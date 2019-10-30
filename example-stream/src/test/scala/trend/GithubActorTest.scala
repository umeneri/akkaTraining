package trend

import aia.stream.StopSystemAfterAll
import akka.actor.{ Actor, ActorRef, ActorSystem, Props }
import akka.testkit.TestKit
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{ MustMatchers, WordSpecLike }
import trend.GithubActor.Tick

import scala.concurrent.ExecutionContextExecutor
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
//      val cmd = s"curl -s -H 'Authorization: token $token' 'https://api.github.com/search/repositories?q=language:scala+stars:>=10+created:>2015-10-12'"
      val cmd = "ls"
      val out = cmd.!!
      println(out)

      receiver ! out
  }
}
