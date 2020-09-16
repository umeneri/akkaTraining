package aia.stream

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}

import scala.concurrent.{Await, ExecutionContextExecutor, Future, Promise}
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.duration._
import scala.util.Random

object Bench extends App {
  implicit val system: ActorSystem = ActorSystem()
  implicit val ec: ExecutionContextExecutor = system.dispatcher
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  def spin(value: Int): Int = {
    val start = System.currentTimeMillis()
    while ((System.currentTimeMillis() - start) < 10) {}
    value
  }

  def nonBlocking(value: Int): Future[Int] = {
    val promise = Promise[Int]
    val t = FiniteDuration(Random.nextInt(101), MILLISECONDS)
    system.scheduler.scheduleOnce(t) {
      promise.success(value)
    }

    promise.future
  }

  val start = System.currentTimeMillis()

//  # 1
//  val r = Source(1 to 100)
//    .mapAsync(4)(x => Future(spin(x)))
//    .mapAsync(4)(x => Future(spin(x)))
//    .runWith(Sink.ignore)
//  Await.result(r, Duration.Inf)

  // #2
    val r = Source(1 to 100)
      .mapAsync(4)(x => nonBlocking(x))
      .mapAsync(4)(x => nonBlocking(x))
      .runWith(Sink.ignore)
    Await.result(r, Duration.Inf)


  val elapsed = System.currentTimeMillis() - start
  println(elapsed)


  system.terminate()

}
