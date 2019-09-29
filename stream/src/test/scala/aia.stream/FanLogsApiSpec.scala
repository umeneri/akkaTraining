package aia.stream

import java.nio.file.Paths

import aia.stream.api.LogsApi
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.mockito.{ ArgumentMatchersSugar, MockitoSugar }
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{ Matchers, WordSpec }

class LogsApiSpec extends WordSpec
  with Matchers
  with ScalaFutures
  with ScalatestRouteTest
  with MockitoSugar
  with ArgumentMatchersSugar {

  "LogsApi getRoute" should {
    "post entity" in {
      val logsApi = new LogsApi(Paths.get("test/tmp"), 100)
      lazy val routes: Route = logsApi.postRoute
      val entity =
        """my-host-1  | web-app | ok       | 2015-08-12T12:12:00.127Z | 5 tickets sold to RHCP.|
          |my-host-2  | web-app | ok       | 2015-08-12T12:12:01.127Z | 3 tickets sold to RHCP.|
          |my-host-3  | web-app | ok       | 2015-08-12T12:12:02.127Z | 1 tickets sold to RHCP.|
          |my-host-3  | web-app | error    | 2015-08-12T12:12:03.127Z | exception occurred...|
          |my-host-4  | web-app | error    | 2015-08-12T12:12:03.127Z | exception occurred...|
          |
          |""".stripMargin
      val request = Post("/logs/1").withEntity(entity)

      request ~> routes ~> check {
        status should ===(StatusCodes.OK)
        entityAs[String] should ===("""{"logId":"1","written":637}""")
      }
    }

    "post entity as json" in {
      val logsApi = new LogsApi(Paths.get("test/tmp"), 100)
      lazy val routes: Route = logsApi.postRoute
      val entityString =
        """[
          |  {
          |    "host": "my-host-1",
          |    "service": "web-app",
          |    "state": "ok",
          |    "time": "2015-08-12T12:12:00.127Z",
          |    "description": "5 tickets sold to RHCP."
          |  },
          |  {
          |    "host": "my-host-2",
          |    "service": "web-app",
          |    "state": "ok",
          |    "time": "2015-08-12T12:12:01.127Z",
          |    "description": "3 tickets sold to RHCP."
          |  }
          |]
          """.stripMargin
      val entity = HttpEntity(entityString).withContentType(ContentTypes.`application/json`)
      val request = Post("/logs/2").withEntity(entity)

      request ~> routes ~> check {
        status should ===(StatusCodes.OK)
        entityAs[String] should ===("""{"logId":"2","written":254}""")
      }
    }

    "get entity" in {
      val logsApi = new LogsApi(Paths.get("test/resources"), 100)
      lazy val routes: Route = logsApi.getRoute
      val request = Get("/logs/1")

      val expected =
        """my-host-1  | web-app | ok       | 2015-08-12T12:12:00.127Z | 5 tickets sold to RHCP.|
          |my-host-2  | web-app | ok       | 2015-08-12T12:12:01.127Z | 3 tickets sold to RHCP.|
          |my-host-3  | web-app | ok       | 2015-08-12T12:12:02.127Z | 1 tickets sold to RHCP.|
          |my-host-3  | web-app | error    | 2015-08-12T12:12:03.127Z | exception occurred...|
          |my-host-4  | web-app | error    | 2015-08-12T12:12:03.127Z | exception occurred...|
          |
          |""".stripMargin

      request ~> routes ~> check {
        status should ===(StatusCodes.OK)
        entityAs[String] should ===(expected)
      }
    }
  }
}
