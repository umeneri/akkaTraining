package aia.testdriven

import akka.actor.{Actor, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.WordSpecLike

class EchoActorTest extends TestKit(ActorSystem("testsystem"))
with WordSpecLike
with ImplicitSender
with StopSystemAfterAll {
  "Reply with the same message it receives without ask" in {
    val echo = system.actorOf(Props[EchoActor], "echo2")
    echo ! "some message"
    expectMsg("some message")
  }
}

class EchoActor extends Actor {
  override def receive: Receive = {
    case msg =>
      sender() ! msg
  }
}
