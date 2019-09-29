package aia.stream.processer

import java.time.ZonedDateTime
import java.time.format.{ DateTimeFormatter, DateTimeParseException }

import aia.stream.models.{ Critical, Error, Event, LogReceipt, Ok, ParseError, State, Warning }
import spray.json.{ DefaultJsonProtocol, JsString, JsValue, JsonFormat, deserializationError }

import scala.util.Try

trait EventMarshalling extends DefaultJsonProtocol {
  implicit val dateTimeFormat = new JsonFormat[ZonedDateTime] {
    def write(dateTime: ZonedDateTime) = JsString(dateTime.format(DateTimeFormatter.ISO_INSTANT))
    def read(value: JsValue): ZonedDateTime = value match {
      case JsString(str) =>
        Try {
          ZonedDateTime.parse(str)
        }.recover {
          case _: DateTimeParseException =>
            val msg = s"Could not deserialize $str to ZonedDateTime"
            deserializationError(msg)
        }.get
      case js =>
        val msg = s"Could not deserialize $js to ZonedDateTime."
        deserializationError(msg)
    }
  }

  implicit val stateFormat = new JsonFormat[State] {
    def write(state: State) = JsString(State.norm(state))
    def read(value: JsValue): State = value match {
      case JsString("ok") => Ok
      case JsString("warning") => Warning
      case JsString("error") => Error
      case JsString("critical") => Critical
      case js =>
        val msg = s"Could not deserialize $js to State."
        deserializationError(msg)
    }
  }

  implicit val eventFormat = jsonFormat7(Event)
  implicit val logIdFormat = jsonFormat2(LogReceipt)
  implicit val errorFormat = jsonFormat2(ParseError)
}
