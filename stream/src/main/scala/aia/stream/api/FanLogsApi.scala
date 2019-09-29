package aia.stream.api

import java.nio.file.{ Files, Path }
import java.nio.file.StandardOpenOption.{ APPEND, CREATE, WRITE }

import aia.stream.models.{ Critical, Error, Event, LogReceipt, Ok, ParseError, State, Warning }
import aia.stream.processer.LogEntityMarshaller.LEM
import aia.stream.processer.{ EventMarshalling, EventUnmarshaller, LogEntityMarshaller, LogJson }
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.stream.scaladsl.{ Broadcast, FileIO, Flow, GraphDSL, Keep, Merge, Sink, Source }
import akka.stream._
import akka.util.ByteString
import akka.{ Done, NotUsed }

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }


class FanLogsApi(
  val logsDir: Path, 
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

  def routes: Route = postRoute ~ getLogNotOkRoute ~ getRoute ~ deleteRoute()

  implicit val unmarshaller: Unmarshaller[HttpEntity, Source[Event, _]] = EventUnmarshaller.create(maxLine, maxJsObject)

  def postRoute: Route =
    pathPrefix("logs" / Segment) { logId =>
      pathEndOrSingleSlash {
        post {
          entity(as[Source[Event, _]]) { src =>
            onComplete(
              src.via(processStates(logId))
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

  val getLogNotOkRoute: Route = {
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

  def getRoute: Route =
    pathPrefix("logs" / Segment) { logId =>
      pathEndOrSingleSlash {
        get {
          if(Files.exists(logFile(logId))) {
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
          if(Files.deleteIfExists(logFile(logId))) complete(StatusCodes.OK)
          else complete(StatusCodes.InternalServerError)
        }
      }
    }
}
