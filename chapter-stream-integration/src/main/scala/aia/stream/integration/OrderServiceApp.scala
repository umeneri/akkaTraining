package aia.stream.integration

import akka.actor.{ ActorSystem, Props }
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.typesafe.config.{ Config, ConfigFactory }

import scala.concurrent.{ ExecutionContextExecutor, Future }
import scala.util.{ Failure, Success }

object OrderServiceApp extends App
    with RequestTimeout {
  val config = ConfigFactory.load() 
  val host = config.getString("http.host")
  val port = config.getInt("http.port")

  implicit val system: ActorSystem = ActorSystem()
  implicit val ec: ExecutionContextExecutor = system.dispatcher
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  val processOrders = system.actorOf(Props(new ProcessOrders), "process-orders")
  val route = new OrderServiceApi(system, requestTimeout(config), processOrders).routes
  val bindingFuture: Future[Http.ServerBinding] = Http().bindAndHandle(route, host, port)
  val log =  Logging(system.eventStream, "order-service")

  bindingFuture.map { serverBinding =>
    log.info(s"Bound to ${serverBinding.localAddress}")
  }.onComplete {
    case Failure(exception) =>
      log.error(exception, "Failed to bind to {}:{}", host, port)
      system.terminate()
    case Success(value) =>
      log.info(s"success: $value")
  }
}


trait RequestTimeout {
  import scala.concurrent.duration._
  def requestTimeout(config: Config): Timeout = {
    val t = config.getString("akka.http.server.request-timeout")
    val d = Duration(t)
    FiniteDuration(d.length, d.unit)
  }
}
