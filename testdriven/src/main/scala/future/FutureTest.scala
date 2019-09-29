package future

import scala.util.{ Failure, Random, Success }

object FutureTest extends App {
  import scala.concurrent._
  import ExecutionContext.Implicits.global

  val futureFail = Future {
    val i = Random.nextInt(2)
    println(i)

    if (i == 0) {
      1
    } else {
      throw new Exception("error!")
    }
  }

  Thread.sleep(1000L)

  Future.firstCompletedOf(List())
  val f = for {
    a <- futureFail
    b <- Future.successful(a * 2)
  } yield {
    b
  }

  f.map {
    println(_) // 例外時は実行されない
  }

//  futureFail.onComplete {
//    case Success(value) => println(value)
//    case Failure(exception) => println(exception)
//  }

  Thread.sleep(1000L)
}
