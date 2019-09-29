package aia.stream

import aia.stream.models.Metric
import spray.json._

trait MetricMarshalling extends EventMarshalling with DefaultJsonProtocol {
  implicit val metric: RootJsonFormat[Metric] = jsonFormat5(Metric)
}
