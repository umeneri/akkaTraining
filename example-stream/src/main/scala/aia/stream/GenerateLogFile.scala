package aia.stream

import java.nio.file.StandardOpenOption._
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import aia.stream.util.FileArg
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import akka.util.ByteString

import scala.concurrent.ExecutionContextExecutor

object GenerateLogFile extends App {
  val filePath = args(0)
  val numberOfLines = args(1).toInt
  val rnd = new java.util.Random()
  val sink = FileIO.toPath(FileArg.shellExpanded(filePath), Set(CREATE, WRITE, APPEND))
  def line(i: Int): String = {
    val host = "my-host"
    val service = "my-service"
    val time = ZonedDateTime.now.format(DateTimeFormatter.ISO_INSTANT)
    val state = if( i % 3 == 0) "warning"
    else if(i % 10 == 0) "error"
    else if(i % 31 == 0) "critical"
    else "ok"
    val description = "Some description of what has happened."
    val tag = "tag"
    val metric = rnd.nextDouble() * 100
    s"$host | $service | $state | $time | $description | $tag | $metric \n"
  }

  val graph = Source.fromIterator{() =>
    Iterator.tabulate(numberOfLines)(line)
  }.map(l=> ByteString(l)).toMat(sink)(Keep.right)

  implicit val system: ActorSystem = ActorSystem()
  implicit val ec: ExecutionContextExecutor = system.dispatcher
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  graph.run().foreach { result =>
    println(s"Wrote ${result.count} bytes to '$filePath'.")
    system.terminate()
  }
}
