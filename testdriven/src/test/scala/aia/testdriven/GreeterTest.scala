package aia.testdriven

import akka.actor.{ActorSystem, Props, UnhandledMessage}
import akka.testkit.{CallingThreadDispatcher, EventFilter, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalatest.WordSpecLike
import GreeterTest._

class GreeterTest extends TestKit(testSystem)
  with WordSpecLike
  with StopSystemAfterAll {
  "The aia.testdriven.Greeter" must {
    "say Hello World! when a aia.testdriven.Greeting(\"World\") is sent to it" in {
      val props = Greeter.props(Some(testActor))
      val greeter = system.actorOf(props)

      greeter ! Greeting("World")
      expectMsg("Hello World!")
    }

    "say something else and see what happens" in {
      val props = Greeter.props(Some(testActor))
      val greeter = system.actorOf(props)

      system.eventStream.subscribe(testActor, classOf[UnhandledMessage])

      greeter ! "hoge"

      expectMsg(UnhandledMessage("hoge", system.deadLetters, greeter))
    }
  }
}

object GreeterTest {
  val testSystem: ActorSystem = {
    val config = ConfigFactory.parseString(
      """
        |akka.loggers = [akka.testkit.TestEventListener]
      """.stripMargin
    )
    ActorSystem("testsystem", config)
  }
}
