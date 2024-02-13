package io.github.tassiLuca

import gears.async.AsyncOperations.sleep
import gears.async.default.given
import gears.async.{Async, Future}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

import scala.language.postfixOps

class CancellationTest extends AnyFunSpec with Matchers {

  describe("Structured concurrency") {
    it("ensure all nested computations are contained within the lifetime of the enclosing one") {
      Async.blocking:
        val before = System.currentTimeMillis()
        val f = Future:
          val f1 = Future { "hello" }
          val f2 = Future { sleep(2_000); "gears!" }
          f1.await + " " + f2.await
        f.await shouldBe "hello gears!"
        val now = System.currentTimeMillis()
        now - before should be > 2_000L
    }

    describe("in case of failures") {
      it("if the first nested computation we wait fails with an exception the other is cancelled") {
        Async.blocking:
          var stillAlive = false
          val before = System.currentTimeMillis()
          val f = Future:
            val f1 = Future { throw Error(); "hello" }
            val f2 = Future { sleep(2_000); stillAlive = true }
            f1.await + " " + f2.await // fortunate case in which the one which fails is the one we wait for
          f.awaitResult.isFailure shouldBe true
          val now = System.currentTimeMillis()
          now - before should be < 2_000L
          sleep(3_000)
          stillAlive shouldBe false
      }

      it("if a nested computation fails while we are waiting for another, the enclosing future is not cancelled") {
        Async.blocking:
          var stillAlive = false
          val before = System.currentTimeMillis()
          val f = Future:
            val f1 = Future { throw Error(); "gears!" }
            val f2 = Future { sleep(2_000); stillAlive = true; "hello" }
            f2.await + " " + f1.await
          f.awaitResult.isFailure shouldBe true
          val now = System.currentTimeMillis()
          now - before should be > 2_000L
          stillAlive shouldBe true
      }

      it("but we can achieve cancellation using combinators") {
        Async.blocking:
          var stillAlive = false
          val before = System.currentTimeMillis()
          val f = Future:
            val f1 = Future { throw Error(); "gears!" }
            val f2 = Future { sleep(2_000); stillAlive = true; "hello" }
            f2.zip(f1).await
          f.awaitResult
          f.awaitResult.isFailure shouldBe true
          val now = System.currentTimeMillis()
          now - before should be < 2_000L
          sleep(3_000)
          stillAlive shouldBe false
      }
    }

    describe("allows racing futures cancelling the slower one when one succeeds") {
      Async.blocking:
        var stillAlive = false
        val before = System.currentTimeMillis()
        val f = Future:
          val f1 = Future { sleep(1_000); "faster won" }
          val f2 = Future { sleep(2_000); stillAlive = true }
          f1.altWithCancel(f2).await
        val result = f.awaitResult
        val now = System.currentTimeMillis()
        now - before should (be > 1_000L and be < 5_000L)
        result.isSuccess shouldBe true
        result.get shouldBe "faster won"
        sleep(3_000)
        stillAlive shouldBe false
    }
  }

//// "Weird" behaviour
//  "test" should "work" in {
//    Async.blocking:
//      @volatile var end = false
//      val timer = Timer(2 seconds)
//      Future {
//        timer.run()
//      }
//      val f = Future:
//        val tf = Future {
//          timer.src.awaitResult; end = true
//        }
//        val tr = Task {
//          if end then Failure(Error()) else println("hello")
//        }.schedule(RepeatUntilFailure()).run
//        tf.altWithCancel(tr).awaitResult
//      println(f.awaitResult)
//  }
//
//  "test" should "not work" in {
//    Async.blocking:
//      val timer = Timer(2 seconds)
//      Future {
//        timer.run()
//      }
//      val f = Future:
//        val tf = Future {
//          timer.src.awaitResult
//        }
//        val tr = Task {
//          println("hello")
//        }.schedule(RepeatUntilFailure).run // non c'è chiamata bloccante, se ci fosse andrebbe bene
//        tf.altWithCancel(tr).awaitResult
//        tr.cancel()
//      println(f.awaitResult)
//  }

//  object TestCancellation3:
//
//    class Producer3(using Async):
//      val channel = UnboundedChannel[Int]()
//
//      def run(): Future[Unit] = Task {
//        channel.send(Random.nextInt())
//      }.schedule(Every(1_000)).run
//
//      def cancel(): Unit = Async.current.group.cancel()
//
//    @main def testCancellation(): Unit =
//      Async.blocking:
//        val p = Producer3()
//        val f1 = p.run()
//        val f2 = Task {
//          println(s"${p.channel.read()}!")
//        }.schedule(Every(1_000)).run
//        Thread.sleep(10_000)
//        p.cancel()
//        p.run().awaitResult
}
