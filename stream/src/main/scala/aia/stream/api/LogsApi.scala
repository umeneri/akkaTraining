package aia.stream.api

import java.nio.file.{ Files, Path }

import aia.stream.models.{ Event, LogReceipt, ParseError }
import aia.stream.processer.{ EventMarshalling, EventUnmarshaller, LogStreamProcessor }
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.stream.scaladsl.{ BidiFlow, FileIO, Flow, Framing, Keep, Sink, Source }
import akka.stream.{ ActorMaterializer, IOResult }
import akka.util.ByteString
import akka.{ Done, NotUsed }
import spray.json._

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }


class LogsApi(
  val logsDir: Path, 
  val maxLine: Int
)(
  implicit val executionContext: ExecutionContext, 
  val materializer: ActorMaterializer
) extends EventMarshalling {
  def logFile(id: String): Path = logsDir.resolve(id)

  val inFlow: Flow[ByteString, Event, NotUsed] = Framing.delimiter(ByteString("\n"), maxLine, allowTruncation = true)
    .map(_.decodeString("UTF8"))
    .map(LogStreamProcessor.parseLineEx)
    .collect { case Some(e) => e }

  val outFlow: Flow[Event, ByteString, NotUsed] = Flow[Event].map { event =>
    ByteString(event.toJson.compactPrint)
  }
  val bidiFlow: BidiFlow[ByteString, Event, Event, ByteString, NotUsed] = BidiFlow.fromFlows(inFlow, outFlow)

  import java.nio.file.StandardOpenOption._

  val logToJsonFlow: Flow[ByteString, ByteString, NotUsed] = bidiFlow.join(Flow[Event])

  val maxJsObject = 10000

  implicit val unmarshaller: Unmarshaller[HttpEntity, Source[Event, _]] = EventUnmarshaller.create(maxLine, maxJsObject)

  def logFileSink(logId: String): Sink[ByteString, Future[IOResult]] =
    FileIO.toPath(logFile(logId), Set(CREATE, WRITE, APPEND))
  def logFileSource(logId: String): Source[ByteString, Future[IOResult]] = FileIO.fromPath(logFile(logId))

  def routes: Route = postRoute ~ getRoute ~ deleteRoute()

  def postRoute: Route =
    pathPrefix("logs" / Segment) { logId =>
      pathEndOrSingleSlash {
        post {
          entity(as[Source[Event, _]]) { src =>
            onComplete(
              src.via(outFlow)
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
