package aia.stream.processer

import aia.stream.models.Metric
import spray.json.{ DefaultJsonProtocol, RootJsonFormat }

trait MetricMarshalling extends EventMarshalling with DefaultJsonProtocol {
  implicit val metric: RootJsonFormat[Metric] = jsonFormat5(Metric)
}
