package aia.stream.api

import java.io.File
import java.nio.file.{ Files, Paths }

import aia.stream.models.Event
import aia.stream.processor.{ EventMarshalling, LogJson }
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.stream.scaladsl.Sink
import org.mockito.{ ArgumentMatchersSugar, MockitoSugar }
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpec }
import spray.json._

import scala.concurrent.Await
import scala.concurrent.duration._

class BufferLogsApiSpec
  extends WordSpec
    with EventMarshalling
    with ScalatestRouteTest
    with Matchers
    with ScalaFutures
    with MockitoSugar
    with ArgumentMatchersSugar
    with BeforeAndAfterAll {
  val path = Files.createTempDirectory(Paths.get("src/test/"), "tmp")

  def delete(file: File) {
    if (file.isDirectory)
      Option(file.listFiles).map(_.toList).getOrElse(Nil).foreach(delete)
    file.delete
  }

  override def beforeAll(): Unit = {
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    val dir = new File(path.toString)
    delete(dir)
    super.afterAll()
  }

  "BufferLogsApi" should {

    "merge stream" in {
      val logsDir = Paths.get("src/test/resources/logs/json/")
      val notificationDir = Paths.get("tmp/notifications")
      val metricsDir = Paths.get("tmp/metrics")
      val BufferLogsApi = new BufferLogsApi(path, notificationDir, metricsDir, 100, 10000)
      val sources = BufferLogsApi.getFileSources(logsDir).map { src =>
        src.via(LogJson.jsonFramed(10000))
      }
      val result = BufferLogsApi.mergeSources(sources)
      val events = result.get.map { byteString =>
        byteString.decodeString("UTF8").parseJson.convertTo[Event].toString
      }.runWith(Sink.seq[String])

      val expected = Vector(
        "Event(my-host-1,web-app,Ok,2015-08-12T12:12:00.127Z,5 tickets sold to RHCP.,None,None)",
        "Event(my-host-2,web-app,Ok,2015-08-12T12:12:01.127Z,3 tickets sold to RHCP.,None,None)",
        "Event(my-host-3,web-app,Error,2015-08-12T12:12:02.127Z,1 tickets sold to RHCP.,None,None)"
      )
      Await.result(events, 10 seconds) shouldBe expected
    }
  }
}
