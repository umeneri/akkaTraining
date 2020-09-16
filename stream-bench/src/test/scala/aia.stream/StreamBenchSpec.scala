package aia.stream

import java.io.File

import akka.actor._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.testkit._
import org.scalatest.{MustMatchers, WordSpecLike}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContextExecutor}

class StreamBenchSpec extends TestKit(ActorSystem("test-filter"))
  with WordSpecLike
  with MustMatchers
  with StopSystemAfterAll {

  val dest = "out.json"

  override protected def beforeAll(): Unit = {
    val file = new File(dest)
    file.delete()
  }



  "it" must {
    "stream" in {
      implicit val system: ActorSystem = ActorSystem()
      implicit val ec: ExecutionContextExecutor = system.dispatcher
      implicit val materializer: ActorMaterializer = ActorMaterializer()

      def spin(value: Int): Int = {
        val start = System.currentTimeMillis()
        while ((System.currentTimeMillis() - start) < 10) {}
        value
      }

      val r = Source(1 to 10).map(spin).map(spin).runWith(Sink.ignore)
      Await.result(r, Duration.Inf)

    }
  }
}

