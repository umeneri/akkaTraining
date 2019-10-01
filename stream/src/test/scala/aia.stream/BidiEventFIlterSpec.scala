package aia.stream

import java.io.File
import java.nio.file._

import aia.stream.processor.BidiEventFilter
import akka.actor._
import akka.stream._
import akka.testkit._
import org.scalatest.{ MustMatchers, WordSpecLike }

class BidiEventFIlterSpec extends TestKit(ActorSystem("test-filter"))
  with WordSpecLike
  with MustMatchers
  with StopSystemAfterAll {

  val dest = "out.json"

  override protected def beforeAll(): Unit = {
    val file = new File(dest)
    file.delete()
  }


    val lines: String =
    "my-host-1  | web-app | ok       | 2015-08-12T12:12:00.127Z | 5 tickets sold to RHCP.||\n" +
      "my-host-2  | web-app | ok       | 2015-08-12T12:12:01.127Z | 3 tickets sold to RHCP.| | \n" +
      "my-host-3  | web-app | ok       | 2015-08-12T12:12:02.127Z | 1 tickets sold to RHCP.| | \n" +
      "my-host-3  | web-app | error    | 2015-08-12T12:12:03.127Z | exception occurred...| | \n"

  "bidi flow" must {
    "be able to read a log file and parse events" in {
      val path = Files.createTempFile("logs", ".txt")

      val bytes = lines.getBytes("UTF8")
      Files.write(path, bytes, StandardOpenOption.APPEND)

      val args = List("txt", "json", path.toString, dest, "ok")
      BidiEventFilter.run(args)
      Thread.sleep(1000L)

      val src = scala.io.Source.fromFile(dest)
      val contents = src.mkString
      src.close()

      contents mustBe """{"state":"ok","description":"5 tickets sold to RHCP.","host":"my-host-1","service":"web-app","time":"2015-08-12T12:12:00.127Z"}{"state":"ok","description":"3 tickets sold to RHCP.","host":"my-host-2","service":"web-app","time":"2015-08-12T12:12:01.127Z"}{"state":"ok","description":"1 tickets sold to RHCP.","host":"my-host-3","service":"web-app","time":"2015-08-12T12:12:02.127Z"}"""
    }

    "be able to read it's own output" in {
      implicit val materializer: ActorMaterializer = ActorMaterializer()
      val path = Files.createTempFile("logs", ".json")
      val json =
        """
      [
      {
        "host": "my-host-1",
        "service": "web-app",
        "state": "ok",
        "time": "2015-08-12T12:12:00.127Z",
        "description": "5 tickets sold to RHCP."
      },
      {
        "host": "my-host-2",
        "service": "web-app",
        "state": "ok",
        "time": "2015-08-12T12:12:01.127Z",
        "description": "3 tickets sold to RHCP."
      },
      {
        "host": "my-host-3",
        "service": "web-app",
        "state": "ok",
        "time": "2015-08-12T12:12:02.127Z",
        "description": "1 tickets sold to RHCP."
      },
      {
        "host": "my-host-3",
        "service": "web-app",
        "state": "error",
        "time": "2015-08-12T12:12:03.127Z",
        "description": "exception occurred..."
      }
      ]
      """

      val bytes = json.getBytes("UTF8")
      Files.write(path, bytes, StandardOpenOption.APPEND)

      import aia.stream.processor.LogStreamProcessor._
      val source = jsonText(path)

      //      val results = errors(parseJsonEvents(source)).runWith(Sink.seq[Event])
      //      Await.result(results, 10 seconds) must be(
      //        Vector(Event("my-host-3", "web-app", Error, ZonedDateTime.parse("2015-08-12T12:12:03.127Z"), "exception occurred..." ))
      //      )
    }
  }
}

