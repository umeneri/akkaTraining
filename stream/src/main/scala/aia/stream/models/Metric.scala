package aia.stream.models

import java.time.ZonedDateTime

case class Metric(
  service: String, 
  time: ZonedDateTime, 
  metric: Double, 
  tag: String, 
  drift: Int = 0
)