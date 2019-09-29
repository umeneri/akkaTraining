package aia.stream

import aia.stream.models.Notification
import spray.json._

trait NotificationMarshalling extends EventMarshalling with DefaultJsonProtocol {
  implicit val summary: RootJsonFormat[Notification] = jsonFormat1(Notification)
}
