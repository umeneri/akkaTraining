package aia.stream.api

import java.nio.file.StandardOpenOption.{ APPEND, CREATE, WRITE }
import java.nio.file.{ Files, Path }

import aia.stream.models.{ Critical, Error, Event, LogReceipt, Metric, Notification, Ok, ParseError, State, Warning }
import aia.stream.processor.LogEntityMarshaller.LEM
import aia.stream.processor.{ EventMarshalling, EventUnmarshaller, LogEntityMarshaller, LogJson }
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.stream._
import akka.stream.scaladsl.{ Broadcast, FileIO, Flow, GraphDSL, Keep, Merge, MergePreferred, Sink, Source }
import akka.util.ByteString
import akka.{ Done, NotUsed }

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }


class BufferLogsApi(
                       val logsDir: Path,
                       val notificationsDir: Path,
                       val metricsDir: Path,
                       val maxLine: Int,
                       val maxJsObject: Int
                   )(
                       implicit val executionContext: ExecutionContext,
                       val materializer: ActorMaterializer
                   ) extends EventMarshalling {
  type FlowLike = Graph[FlowShape[Event, ByteString], NotUsed]

  def logStateFile(id: String, state: State): Path =
    logsDir.resolve(s"$id-${State.norm(state)}")

  def logFileSink(logId: String, state: State): Sink[ByteString, Future[IOResult]] =
    FileIO.toPath(logStateFile(logId, state), Set(CREATE, WRITE, APPEND))

  def logFileSource(logId: String, state: State): Source[ByteString, Future[IOResult]] =
    FileIO.fromPath(logStateFile(logId, state))

  def logFile(id: String): Path = logsDir.resolve(id)

  def logFileSource(logId: String): Source[ByteString, Future[IOResult]] =
    FileIO.fromPath(logFile(logId))

  def logFileSink(logId: String): Sink[ByteString, Future[IOResult]] =
    FileIO.toPath(logFile(logId), Set(CREATE, WRITE, APPEND))

  def bufferedStats(logId: String): Flow[Event, ByteString, NotUsed] = {
    val notificationSink: Sink[ByteString, Future[IOResult]] =
      FileIO.toPath(notificationsDir.resolve("notifications.json"), Set(CREATE, WRITE, APPEND))

    val metricsSink: Sink[ByteString, Future[IOResult]] =
      FileIO.toPath(metricsDir.resolve("metrics.json"), Set(CREATE, WRITE, APPEND))

    val nrWarnings = 100
    val nrErrors = 10
    val archBufSize = 100000
    val warnBufSize = 100
    val errBufSize = 1000
    val errDuration: FiniteDuration = 10 seconds
    val warnDuration: FiniteDuration = 1 minute

    val toMetric: Flow[Event, Metric, NotUsed] = Flow[Event].collect {
      case Event(_, service, _, time, _, Some(tag), Some(metric)) =>
        Metric(service, time, metric, tag)
    }

    val recordDrift: Flow[Metric, Metric, NotUsed] = Flow[Metric]
      .expand { metric =>
        Iterator.from(0).map(d => metric.copy(drift = d))
      }

    def rollup(nr: Int, duration: FiniteDuration): Flow[Event, Notification, NotUsed] =
      Flow[Event].groupedWithin(nr, duration)
        .map(events => Notification(events.toVector))

    val rollupErr: Flow[Event, Notification, NotUsed] = rollup(nrErrors, errDuration)
    val rollupWarn: Flow[Event, Notification, NotUsed] = rollup(nrWarnings, warnDuration)
    val toNot: Flow[Event, Notification, NotUsed] = Flow[Event].map(e=> Notification(Vector(e)))

    Flow.fromGraph(
      GraphDSL.create() { implicit builder =>
        import GraphDSL.Implicits._
        val archBuf = Flow[Event].buffer(archBufSize, OverflowStrategy.fail)
        val warnBuf = Flow[Event].buffer(warnBufSize, OverflowStrategy.dropHead)
        val errBuf = Flow[Event].buffer(errBufSize, OverflowStrategy.backpressure)
        val metricBuf = Flow[Event].buffer(errBufSize, OverflowStrategy.dropHead)
        val jsFlow: Flow[Event, ByteString, NotUsed] = LogJson.jsonOutFlow
        val notifyOutFlow = LogJson.notifyOutFlow
        val metricOutFlow = LogJson.metricOutFlow

        val ok: Flow[Event, Event, NotUsed] = Flow[Event].filter(_.state == Ok)
        val warning: Flow[Event, Event, NotUsed] = Flow[Event].filter(_.state == Warning)
        val error: Flow[Event, Event, NotUsed] = Flow[Event].filter(_.state == Error)
        val critical: Flow[Event, Event, NotUsed] = Flow[Event].filter(_.state == Critical)

        val bcast = builder.add(Broadcast[Event](5))
        val wbcast = builder.add(Broadcast[Event](2))
        val ebcast = builder.add(Broadcast[Event](2))
        val cbcast = builder.add(Broadcast[Event](2))
        val okcast = builder.add(Broadcast[Event](2))
        val mergeNotify = builder.add(MergePreferred[Notification](2))
        val archive = builder.add(jsFlow)

        bcast ~> archBuf ~> archive.in
        bcast ~> ok ~> okcast
        bcast ~> warning ~> wbcast
        bcast ~> error ~> ebcast
        bcast ~> critical ~> cbcast

        okcast ~> jsFlow ~> logFileSink(logId, Ok)
        okcast ~> metricBuf ~> toMetric ~> recordDrift ~> metricOutFlow ~> metricsSink

        cbcast ~> jsFlow ~> logFileSink(logId, Critical)
        cbcast ~> toNot ~> mergeNotify.preferred

        ebcast ~> jsFlow ~> logFileSink(logId, Error)
        ebcast ~> errBuf ~> rollupErr ~> mergeNotify.in(0)

        wbcast ~> jsFlow ~> logFileSink(logId, Warning)
        wbcast ~> warnBuf ~> rollupWarn ~> mergeNotify.in(1)

        mergeNotify ~> notifyOutFlow ~> notificationSink

        FlowShape(bcast.in, archive.out)
      }
    )
  }

  def processStates(logId: String): FlowLike = {
    Flow.fromGraph(
      GraphDSL.create() { implicit builder =>
        import GraphDSL.Implicits._
        val bcast: UniformFanOutShape[Event, Event] = builder.add(Broadcast[Event](5))
        val jsFlow: Flow[Event, ByteString, NotUsed] = LogJson.jsonOutFlow
        val js: FlowShape[Event, ByteString] = builder.add(jsFlow)
        val ok: Flow[Event, Event, NotUsed] = Flow[Event].filter(_.state == Ok)
        val warning: Flow[Event, Event, NotUsed] = Flow[Event].filter(_.state == Warning)
        val error: Flow[Event, Event, NotUsed] = Flow[Event].filter(_.state == Error)
        val critical: Flow[Event, Event, NotUsed] = Flow[Event].filter(_.state == Critical)

        bcast ~> js.in
        bcast ~> ok ~> jsFlow ~> logFileSink(logId, Ok)
        bcast ~> warning ~> jsFlow ~> logFileSink(logId, Warning)
        bcast ~> error ~> jsFlow ~> logFileSink(logId, Error)
        bcast ~> critical ~> jsFlow ~> logFileSink(logId, Critical)

        FlowShape(bcast.in, js.out)
      }
    )
  }

  def mergeNotOk(logId: String): Source[ByteString, NotUsed] = {
    val warning = logFileSource(logId, Warning).via(LogJson.jsonFramed(maxJsObject))
    val error = logFileSource(logId, Error).via(LogJson.jsonFramed(maxJsObject))
    val critical = logFileSource(logId, Critical).via(LogJson.jsonFramed(maxJsObject))

    Source.fromGraph(
      GraphDSL.create() { implicit builder =>
        import GraphDSL.Implicits._

        val warningShape = builder.add(warning)
        val errorShape = builder.add(error)
        val criticalShape = builder.add(critical)
        val merge = builder.add(Merge[ByteString](3))

        warningShape ~> merge
        criticalShape ~> merge
        errorShape ~> merge
        SourceShape(merge.out)
      }
    )
  }

  def mergeSources[E](source: Vector[Source[E, _]]): Option[Source[E, _]] = {
    if (source.isEmpty) None
    else if (source.size == 1) Some(source(0))
    else {
      val combined = Source.combine(
        source(0),
        source(1),
        source.drop(2): _*
      )(Merge(_))

      Some(combined)
    }
  }

  def routes: Route = postRoute ~ getLogNotOkRoute ~ getRoute ~ deleteRoute() ~ getLogsRoute

  implicit val unmarshaller: Unmarshaller[HttpEntity, Source[Event, _]] = EventUnmarshaller.create(maxLine, maxJsObject)

  def postRoute: Route =
    pathPrefix("logs" / Segment) { logId =>
      pathEndOrSingleSlash {
        post {
          entity(as[Source[Event, _]]) { src =>
            onComplete(
              src.via(bufferedStats(logId))
                .toMat(logFileSink(logId))(Keep.right)
                .run()
            ) {
              case Success(IOResult(count, Success(Done))) =>
                complete((StatusCodes.OK, LogReceipt(logId, count)))
              case Success(IOResult(_, Failure(e))) =>
                complete((
                  StatusCodes.BadRequest,
                  ParseError(logId, e.getMessage)
                ))
              case Failure(e) =>
                complete((
                  StatusCodes.BadRequest,
                  ParseError(logId, e.getMessage)
                ))
            }
          }
        }
      }
    }

  // NOTE: なぜLEMでうまくいくのか？
  implicit val marshaller: LEM = LogEntityMarshaller.create(maxJsObject)

  def getLogNotOkRoute: Route = {
    pathPrefix("logs" / Segment / "not-ok") { logId =>
      pathEndOrSingleSlash {
        get {
          extractRequest { req =>
            complete(Marshal(mergeNotOk(logId)).toResponseFor(req))
          }
        }
      }
    }
  }

  def getFileSources[T](dir: Path): Vector[Source[ByteString, Future[IOResult]]] = {
    val dirStream = Files.newDirectoryStream(dir)
    try {
      import scala.collection.JavaConverters._
      val paths = dirStream.iterator.asScala.toVector
      paths.map(path => FileIO.fromPath(path))
    } finally dirStream.close()
  }

  def getLogsRoute: Route =
    pathPrefix("logs") {
      pathEndOrSingleSlash {
        get {
          extractRequest { req =>
            val sources = getFileSources(logsDir).map { src =>
              src.via(LogJson.jsonFramed(maxJsObject))
            }
            mergeSources(sources) match {
              case Some(src) =>
                complete(Marshal(src).toResponseFor(req))
              case None =>
                complete(StatusCodes.NotFound)
            }
          }
        }
      }
    }


  def getRoute: Route =
    pathPrefix("logs" / Segment) { logId =>
      pathEndOrSingleSlash {
        get {
          if (Files.exists(logFile(logId))) {
            val src = logFileSource(logId)
            complete(
              HttpEntity(ContentTypes.`application/json`, src)
            )
          } else {
            complete(StatusCodes.NotFound)
          }
        }
      }
    }


  def deleteRoute(): Route =
    pathPrefix("logs" / Segment) { logId =>
      pathEndOrSingleSlash {
        delete {
          if (Files.deleteIfExists(logFile(logId))) complete(StatusCodes.OK)
          else complete(StatusCodes.InternalServerError)
        }
      }
    }
}
