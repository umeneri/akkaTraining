package aia.stream.integration

import akka.actor._
import akka.http.scaladsl.marshallers.xml.ScalaXmlSupport._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.{ ExecutionContext, ExecutionContextExecutor }
import scala.xml.NodeSeq

class OrderServiceApi(
  system: ActorSystem, 
  timeout: Timeout, 
  val processOrders: ActorRef
) extends OrderService {
  implicit val requestTimeout: Timeout = timeout
  implicit def executionContext: ExecutionContextExecutor = system.dispatcher
}

trait OrderService {
  import Orders._
  import ProcessOrders._

  val processOrders: ActorRef

  implicit def executionContext: ExecutionContext

  implicit def requestTimeout: Timeout

  val routes: Route = getOrder ~ postOrders

  def getOrder: Route = get {
    pathPrefix("orders" / IntNumber) { id =>
      onSuccess(processOrders.ask(OrderId(id))) {
        case result: TrackingOrder =>
          complete(
            <statusResponse>
              <id>{ result.id }</id>
              <status>{ result.status }</status>
            </statusResponse>
          )
        
        case _: NoSuchOrder =>
          complete(StatusCodes.NotFound)
      }
    }
  }

  def postOrders: Route = post {
    path("orders") {
      entity(as[NodeSeq]) { xml =>
        val order = toOrder(xml)
        onSuccess(processOrders.ask(order)) {
          case result: TrackingOrder =>
            complete(
              <confirm>
                <id>{ result.id }</id>
                <status>{ result.status }</status>
              </confirm>
            )
        
          case _ =>
            complete(StatusCodes.BadRequest)
        }
      }
    }
  }  

  def toOrder(xml: NodeSeq): Order = {
    val order = xml \\ "order"
    val customer = (order \\ "customerId").text
    val productId = (order \\ "productId").text
    val number = (order \\ "number").text.toInt
    Order(customer, productId, number)
  }
}
