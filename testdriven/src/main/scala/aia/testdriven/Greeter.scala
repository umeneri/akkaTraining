package aia.testdriven

import akka.actor.{Actor, ActorLogging, ActorRef, Props}

case class Greeting(message: String)

object Greeter {
  def props(listener: Option[ActorRef] = None) =
    Props(new Greeter(listener))
}

class Greeter(listener: Option[ActorRef]) extends Actor with ActorLogging {
  override def receive: Receive = {
    case Greeting(who: String) =>
      val message = s"Hello $who!"
      log.info(message)
      listener.foreach(_ ! message)
  }
}
