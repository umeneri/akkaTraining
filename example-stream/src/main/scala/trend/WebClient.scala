package trend

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._

import scala.concurrent.Future
import scala.util.{ Failure, Success }

object WebClient {
  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()
    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] = Http().outgoingConnection("akka.io")
    val source: Source[HttpRequest, NotUsed] = Source(List(HttpRequest(uri = "https://example.com")))
    val flow: Source[HttpResponse, NotUsed] = source.via(connectionFlow)
    val responseFuture = flow
      .runWith(Sink.head)

    responseFuture.andThen {
      case Success(v) =>
        println("request succeded")
        println(v)
      case Failure(_) => println("request failed")
    }.andThen {
      case _ => system.terminate()
    }

//    val response: HttpResponse = ???
//
//    val bytes = response.entity.dataBytes
  }
}