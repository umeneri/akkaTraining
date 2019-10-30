package trend

import java.nio.file.{ Path, Paths }

import aia.stream.StopSystemAfterAll
import akka.actor.{ ActorRef, ActorSystem }
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.{ Authorization, GenericHttpCredentials }
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse, Uri }
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.scaladsl.{ FileIO, Flow, Keep, Sink, Source }
import akka.stream.{ ActorMaterializer, IOResult, OverflowStrategy }
import akka.testkit.TestKit
import akka.util.ByteString
import akka.{ Done, NotUsed }
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Json
import io.circe.parser._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{ MustMatchers, WordSpecLike }

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContextExecutor, Future }
import scala.util.Success

class FlowTest extends TestKit(ActorSystem("testsystem"))
  with WordSpecLike
  with MustMatchers
  with ScalaFutures
  with FailFastCirceSupport
  with StopSystemAfterAll {

  implicit val ec: ExecutionContextExecutor = system.dispatcher

  "Akka Stream" must {
    "run flow test" in {
      case class Message(text: String)

      implicit val materializer: ActorMaterializer = ActorMaterializer()

      val flow: Flow[Message, String, NotUsed] = Flow[Message]
        .map(_.text.toUpperCase)
        .map { elem =>
          println(elem)
          elem
        }

      val source = Source[Message](List(Message("1")))
      val graph = source.via(flow).runWith(Sink.seq)
      graph.futureValue must be(Seq("1"))
    }

    "srouce actor test" in {
      implicit val materializer: ActorMaterializer = ActorMaterializer()
      val bufferSize = 10


      val ref: ActorRef = Source
        .actorRef[Int](bufferSize, OverflowStrategy.fail) // note: backpressure is not supported
        .map(x => x * x)
        .toMat(Sink.foreach(x => println(s"completed $x")))(Keep.left)
        .run()

      ref ! 1
      ref ! 2
      ref ! 3
      ref ! akka.actor.Status.Success("done")
    }

    "file source test" in {
      implicit val materializer: ActorMaterializer = ActorMaterializer()
      val inputFile: Path = Paths.get(s"example-stream/logs/github_scala_mini.json")
      val fileSource = FileIO.fromPath(inputFile)
      val repositoryFrame = Flow[ByteString].map { b =>
        b.decodeString("UTF8")
      }.map { jsonString =>
        parse(jsonString) fold( { e =>
          println(e)
          Json.Null
        }, { json =>
          println(json)
          json
        })
      }

      val fileGraph = fileSource
        .via(repositoryFrame)
        .toMat(Sink.foreach(_ => {
          // println(json)
        }))(Keep.left)
        .run

      fileGraph.futureValue must be(IOResult(5828, Success(Done)))
    }

    "srouce actor and http request test" in {
      implicit val materializer: ActorMaterializer = ActorMaterializer()
      val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
        Http().outgoingConnectionHttps(GithubRequest.host)

      val httpRequestFlow = Flow[Int].map(i => {
        GithubRequest.getSearchRequest
      })

      val bufferSize = 10
      val source = Source.actorRef[Int](bufferSize, OverflowStrategy.fail)
      val ref = source
        .via(httpRequestFlow)
        .via(connectionFlow)
        .toMat(Sink.foreach(httpResponse => {
          val eventualString = Unmarshal(httpResponse.entity).to[Json]
          eventualString.map { j =>
            println(j)
          }
        }))(Keep.left)
        .run

      ref ! 1
      ref ! akka.actor.Status.Success("done")
      expectNoMessage(3.seconds)
    }
  }
}

object GithubRequest {
  val host = "api.github.com"

  def getSearchRequest: HttpRequest = {
    val path = "search/repositories"
    val queryString = "language:scala+stars:>=10+created:>2015-10-12"
    val token = sys.env.getOrElse("GITHUB_TOKEN", "")
    val uri = Uri(s"https://$host/$path").withRawQueryString(s"q=$queryString")

    HttpRequest(uri = uri).withHeaders(Authorization(GenericHttpCredentials("token", token)))
  }
}
