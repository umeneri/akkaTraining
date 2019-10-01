package aia.stream.processor

import aia.stream.models.Notification
import spray.json.{ DefaultJsonProtocol, RootJsonFormat }

trait NotificationMarshalling extends EventMarshalling with DefaultJsonProtocol {
  implicit val summary: RootJsonFormat[Notification] = jsonFormat1(Notification)
}
