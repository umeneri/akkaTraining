package aia.testdriven

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.testkit.{TestActorRef, TestKit}
import org.scalatest.{MustMatchers, WordSpecLike}

class SendingActorTest extends TestKit(ActorSystem("testsystem"))
  with WordSpecLike
  with MustMatchers
  with StopSystemAfterAll {

  "A silent actor" must {
    "change internal state when it receives a message, single threaded" in {
      import SilentActor._

      val silentActor = TestActorRef[SilentActor]
      val whisper = "whisper"
      silentActor ! SilentMessage(whisper)
      silentActor.underlyingActor.state must contain(whisper)
    }

    "change state when it receives a message, multi-threaded" in {
      import SilentActor._

      val silentActor = system.actorOf(Props[SilentActor], "s3")
      val whisper = "whisper"
      silentActor ! SilentMessage(whisper + "1")
      silentActor ! SilentMessage(whisper + "2")
      silentActor ! GetState(testActor)
      expectMsg(Vector(whisper + "1", whisper + "2"))
    }
  }
}

object SilentActor {
  case class SilentMessage(data: String)
  case class GetState(receiver: ActorRef)
}

class SilentActor extends Actor {
  import SilentActor._

  var internalState: Vector[String] = Vector[String]()

  override def receive: Receive = {
    case SilentMessage(data: String) =>
      internalState = internalState :+ data
    case GetState(receiver: ActorRef) =>
      receiver ! state
  }

  def state: Vector[String] = internalState
}
