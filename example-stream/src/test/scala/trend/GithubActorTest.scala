package trend

import aia.stream.StopSystemAfterAll
import akka.NotUsed
import akka.actor.{ Actor, ActorRef, ActorSystem, Props }
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.{ Authorization, GenericHttpCredentials }
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse, Uri }
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.scaladsl.{ Flow, Keep, Sink, Source }
import akka.stream.{ ActorMaterializer, OverflowStrategy }
import akka.testkit.TestKit
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Json
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{ MustMatchers, WordSpecLike }
import trend.GithubActor.{ Message, Tick }

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContextExecutor, Future }
import scala.sys.process._


class GithubActorTest extends TestKit(ActorSystem("testsystem"))
  with WordSpecLike
  with MustMatchers
  with ScalaFutures
  with FailFastCirceSupport
  with StopSystemAfterAll {

  implicit val ec: ExecutionContextExecutor = system.dispatcher

  "A Github Actor" must {
    "get github repo" in {
      import GithubActor._

      val actor = system.actorOf(GithubActor.props(testActor), "github")
      actor.tell(Tick(), testActor)
      expectMsgType[String]
    }

    "run flow test" in {
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

    "srouce actor and http request test" in {
      implicit val materializer: ActorMaterializer = ActorMaterializer()
      val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
        Http().outgoingConnectionHttps(GithubRequest.host)

      val httpRequestFlow = Flow[Int].map(i => {
        GithubRequest.search()
      })

      val bufferSize = 10
      val source = Source.actorRef[Int](bufferSize, OverflowStrategy.fail)
      val ref = source
        .via(httpRequestFlow)
        .via(connectionFlow)
        .toMat(Sink.foreach(httpResponse => {
          val eventualString = Unmarshal(httpResponse.entity).to[Json]
          eventualString.map { j =>
            println("a")
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

  def search(): HttpRequest = {
    val path = "search/repositories"
    val queryString = "language:scala+stars:>=10+created:>2015-10-12"
    val token = sys.env.getOrElse("GITHUB_TOKEN", "")

    HttpRequest(uri = Uri(s"https://$host/$path").withRawQueryString(s"q=$queryString"))
      .withHeaders(Authorization(GenericHttpCredentials("token", token)))
  }
}

object GithubActor {
  def props(reciever: ActorRef): Props = Props(new GithubActor(reciever))

  case class Tick()

  case class Message(text: String)

}

class GithubActor(receiver: ActorRef) extends Actor {

  override def receive: Receive = {
    case _: Tick =>
      val token = sys.env.getOrElse("GITHUB_TOKEN", "")
      val cmd = s"curl -s -H 'Authorization: token $token' 'https://api.github.com/search/repositories?q=language:scala+stars:>=10+created:>2015-10-12'"
      val out = cmd.!
      println(out)

      receiver ! out
  }
}
