package aia.stream

import java.nio.file.StandardOpenOption._

import aia.stream.util.FileArg
import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{ FileIO, Flow, Framing, Source }
import akka.stream.{ ActorMaterializer, IOResult }
import akka.util.ByteString
import com.typesafe.config.ConfigFactory

import scala.concurrent.{ ExecutionContextExecutor, Future }

object StreamingCopyApp extends App {
  val config = ConfigFactory.load()
  val maxLine = config.getInt("log-stream-processor.max-line")

  if (args.length != 2) {
    System.err.println("Provide args: input-file output-file")
    System.exit(1)
  }

  println(args)

  val inputFile = FileArg.shellExpanded(args(0))
  val outputFile = FileArg.shellExpanded(args(1))
  val source: Source[ByteString, Future[IOResult]] = FileIO.fromPath(inputFile)
  val sink = FileIO.toPath(outputFile, Set(CREATE, WRITE, APPEND))
  val frame: Flow[ByteString, String, NotUsed] = Framing.delimiter(ByteString("\n"), maxLine).map(_.decodeString("UTF8"))
  val serializable = Flow[String].map(event => ByteString(event))

  val runnableGraph = source.via(frame).via(serializable).to(sink)

  implicit val system: ActorSystem = ActorSystem()
  implicit val ec: ExecutionContextExecutor = system.dispatcher
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  val run: Future[IOResult] = runnableGraph.run()

  run.foreach { result =>
    println(s"${result.status}, ${result.count} bytes read.")
    system.terminate()
  }

}
