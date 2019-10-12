package aia.stream.integration

import java.io.File

import akka.actor.ActorSystem
import akka.testkit.TestKit
import org.scalatest.{ BeforeAndAfterAll, MustMatchers, WordSpecLike }

class FileTest extends WordSpecLike with MustMatchers {

  "Consumer" must {
    "pickup xml files" in {
       val f = new File("hoge1")
      println(f.exists())
    }
  }
}
