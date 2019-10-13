package trend

import aia.stream.StopSystemAfterAll
import akka.NotUsed
import akka.actor.{ Actor, ActorRef, ActorSystem, Props }
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse }
import akka.stream.scaladsl.{ Flow, Keep, Sink, Source }
import akka.stream.{ ActorMaterializer, OverflowStrategy }
import akka.testkit.TestKit
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{ MustMatchers, WordSpecLike }
import trend.GithubActor.{ Message, Tick }

import scala.concurrent.Future
import scala.sys.process._


class GithubActorTest extends TestKit(ActorSystem("testsystem"))
  with WordSpecLike
  with MustMatchers
  with ScalaFutures
  with StopSystemAfterAll {

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
      val bufferSize = 10

      val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
        Http().outgoingConnection("example.com")
      val httpRequestFlow = Flow[Int].map(i => HttpRequest(uri = "https://example.com"))

      val source = Source.actorRef[Int](bufferSize, OverflowStrategy.fail)
      val ref = source.via(httpRequestFlow).via(connectionFlow)
        .toMat(Sink.foreach(x => println(s"completed $x")))(Keep.left)
        .run

      ref ! 1
      ref ! 2
      ref ! 3
      ref ! akka.actor.Status.Success("done")
      expectNoMsg
    }
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
      val cmd = "curl -s -H 'Authorization: token 883e9ad4a9f842a84fdefd7f1362f4382c5cd35d' 'https://api.github.com/search/repositories?q=language:scala+stars:>=10+created:>2015-10-12'"
      val out = cmd.!
      println(out)

      receiver ! out
  }
}
