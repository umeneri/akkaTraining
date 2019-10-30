package aia.stream.api

import java.io.File
import java.nio.file.{ Files, Paths }

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.mockito.{ ArgumentMatchersSugar, MockitoSugar }
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpec }

class FanLogsApiSpec extends WordSpec
  with Matchers
  with ScalaFutures
  with ScalatestRouteTest
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

  "FanLogsApi postRoute" should {
    "post entity" in {
      val fanLogsApi = new FanLogsApi(path, 100, 10000)
      lazy val routes: Route = fanLogsApi.postRoute
      val entityString =
        """my-host-1  | web-app | ok       | 2015-08-12T12:12:00.127Z | 5 tickets sold to RHCP.|
          |my-host-2  | web-app | ok       | 2015-08-12T12:12:01.127Z | 3 tickets sold to RHCP.|
          |my-host-3  | web-app | ok       | 2015-08-12T12:12:02.127Z | 1 tickets sold to RHCP.|
          |my-host-3  | web-app | error    | 2015-08-12T12:12:03.127Z | exception occurred...|
          |my-host-4  | web-app | error    | 2015-08-12T12:12:03.127Z | exception occurred...|
          |""".stripMargin
      val entity = HttpEntity(entityString).withContentType(ContentTypes.`text/plain(UTF-8)`)
      val request = Post("/logs/1").withEntity(entity)

      request ~> routes ~> check {
        status should ===(StatusCodes.OK)
        entityAs[String] should ===("""{"logId":"1","written":637}""")

        val testTxtSource = scala.io.Source.fromFile(s"${path}/1")
        val str = testTxtSource.mkString
        testTxtSource.close()

        val expected = """{"state":"ok","description":"5 tickets sold to RHCP.","host":"my-host-1","service":"web-app","time":"2015-08-12T12:12:00.127Z"}{"state":"ok","description":"3 tickets sold to RHCP.","host":"my-host-2","service":"web-app","time":"2015-08-12T12:12:01.127Z"}{"state":"ok","description":"1 tickets sold to RHCP.","host":"my-host-3","service":"web-app","time":"2015-08-12T12:12:02.127Z"}{"state":"error","description":"exception occurred...","host":"my-host-3","service":"web-app","time":"2015-08-12T12:12:03.127Z"}{"state":"error","description":"exception occurred...","host":"my-host-4","service":"web-app","time":"2015-08-12T12:12:03.127Z"}"""
        str should ===(expected)
      }
    }

    "merge stream" in {
      val fanLogsApi = new FanLogsApi(path, 100, 10000)
      lazy val routes: Route = fanLogsApi.getLogNotOkRoute
      val request = Get("/logs/1")

      request ~> routes ~> check {
//        status should ===(StatusCodes.OK)
//        entityAs[String] should ===("""{"logId":"1","written":637}""")
//
//        val testTxtSource = scala.io.Source.fromFile(s"${path}/1")
//        val str = testTxtSource.mkString
//        testTxtSource.close()
//
//        val expected = """{"state":"ok","description":"5 tickets sold to RHCP.","host":"my-host-1","service":"web-app","time":"2015-08-12T12:12:00.127Z"}{"state":"ok","description":"3 tickets sold to RHCP.","host":"my-host-2","service":"web-app","time":"2015-08-12T12:12:01.127Z"}{"state":"ok","description":"1 tickets sold to RHCP.","host":"my-host-3","service":"web-app","time":"2015-08-12T12:12:02.127Z"}{"state":"error","description":"exception occurred...","host":"my-host-3","service":"web-app","time":"2015-08-12T12:12:03.127Z"}{"state":"error","description":"exception occurred...","host":"my-host-4","service":"web-app","time":"2015-08-12T12:12:03.127Z"}"""
//        str should ===(expected)
      }
    }
  }
}
