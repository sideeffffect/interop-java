package zio
package interop

import _root_.java.net.InetSocketAddress
import _root_.java.nio.ByteBuffer
import _root_.java.nio.channels.{ AsynchronousServerSocketChannel, AsynchronousSocketChannel }
import _root_.java.util.concurrent.{ CompletableFuture, CompletionStage, Future }
import zio.interop.javaz._
import zio.test.Assertion._
import zio.test._

object JavaSpec {
  def spec = suite("JavaSpec")(
    suite("`Task.fromFutureJava` must")(
      testM("be lazy on the `Future` parameter") {
        var evaluated         = false
        def ftr: Future[Unit] = CompletableFuture.supplyAsync(() => evaluated = true)
        assertM(ZIO.fromFutureJava(UIO.effectTotal(ftr)).as(evaluated).run, succeeds(isFalse))
      },
      testM("catch exceptions thrown by lazy block") {
        val ex                          = new Exception("no future for you!")
        val noFuture: UIO[Future[Unit]] = UIO.effectTotal(throw ex)
        assertM(ZIO.fromFutureJava(noFuture).run, equalTo[Exit[Throwable, Unit]](Exit.die(ex)))
      },
      testM("return an `IO` that fails if `Future` fails (failedFuture)") {
        val ex                         = new Exception("no value for you!")
        val noValue: UIO[Future[Unit]] = UIO.effectTotal(CompletableFuture_.failedFuture(ex))
        assertM(ZIO.fromFutureJava(noValue).run, fails[Throwable](equalTo(ex)))
      },
      testM("return an `IO` that fails if `Future` fails (supplyAsync)") {
        val ex                         = new Exception("no value for you!")
        val noValue: UIO[Future[Unit]] = UIO.effectTotal(CompletableFuture.supplyAsync(() => throw ex))
        assertM(ZIO.fromFutureJava(noValue).run, fails[Throwable](equalTo(ex)))
      },
      testM("return an `IO` that produces the value from `Future`") {
        val someValue: UIO[Future[Int]] = UIO.effectTotal(CompletableFuture.completedFuture(42))
        assertM(ZIO.fromFutureJava(someValue).run, succeeds(equalTo(42)))
      },
      testM("handle null produced by the completed `Future`") {
        val someValue: UIO[Future[String]] = UIO.effectTotal(CompletableFuture.completedFuture[String](null))
        assertM(ZIO.fromFutureJava(someValue).run, succeeds(equalTo[String](null)))
      }
    ),
    suite("`Task.fromCompletionStage` must")(
      testM("be lazy on the `Future` parameter") {
        var evaluated                 = false
        def cs: CompletionStage[Unit] = CompletableFuture.supplyAsync(() => evaluated = true)
        assertM(ZIO.fromCompletionStage(UIO.effectTotal(cs)).as(evaluated).run, succeeds(isFalse))
      },
      testM("catch exceptions thrown by lazy block") {
        val ex                                   = new Exception("no future for you!")
        val noFuture: UIO[CompletionStage[Unit]] = UIO.effectTotal(throw ex)
        assertM(ZIO.fromCompletionStage(noFuture).run, equalTo[Exit[Throwable, Unit]](Exit.die(ex)))
      },
      testM("return an `IO` that fails if `Future` fails (failedFuture)") {
        val ex                                  = new Exception("no value for you!")
        val noValue: UIO[CompletionStage[Unit]] = UIO.effectTotal(CompletableFuture_.failedFuture(ex))
        assertM(ZIO.fromCompletionStage(noValue).run, fails[Throwable](equalTo(ex)))
      },
      testM("return an `IO` that fails if `Future` fails (supplyAsync)") {
        val ex                                  = new Exception("no value for you!")
        val noValue: UIO[CompletionStage[Unit]] = UIO.effectTotal(CompletableFuture.supplyAsync(() => throw ex))
        assertM(ZIO.fromCompletionStage(noValue).run, fails[Throwable](equalTo(ex)))
      },
      testM("return an `IO` that produces the value from `Future`") {
        val someValue: UIO[CompletionStage[Int]] = UIO.effectTotal(CompletableFuture.completedFuture(42))
        assertM(ZIO.fromCompletionStage(someValue).run, succeeds(equalTo(42)))
      },
      testM("handle null produced by the completed `Future`") {
        val someValue: UIO[CompletionStage[String]] =
          UIO.effectTotal(CompletableFuture.completedFuture[String](null))
        assertM(ZIO.fromCompletionStage(someValue).run, succeeds(equalTo[String](null)))
      }
    ),
    suite("`Task.toCompletableFuture` must")(
      testM("produce always a successful `IO` of `Future`") {
        val failedIO = IO.fail[Throwable](new Exception("IOs also can fail"))
        assertM(failedIO.toCompletableFuture, isSubtype[CompletableFuture[Unit]](anything))
      },
      test("be polymorphic in error type") {
        val unitIO: Task[Unit]                          = Task.unit
        val polyIO: IO[String, CompletableFuture[Unit]] = unitIO.toCompletableFuture
        assert(polyIO, anything)
      },
      testM("return a `CompletableFuture` that fails if `IO` fails") {
        val ex                       = new Exception("IOs also can fail")
        val failedIO: Task[Unit]     = IO.fail[Throwable](ex)
        val failedFuture: Task[Unit] = failedIO.toCompletableFuture.flatMap(f => Task(f.get()))
        assertM(
          failedFuture.run,
          fails[Throwable](hasField("message", _.getMessage, equalTo("java.lang.Exception: IOs also can fail")))
        )
      },
      testM("return a `CompletableFuture` that produces the value from `IO`") {
        val someIO = Task.succeed[Int](42)
        assertM(someIO.toCompletableFuture.map(_.get()), equalTo(42))
      }
    ),
    suite("`Task.toCompletableFutureE` must")(
      testM("convert error of type `E` to `Throwable`") {
        val failedIO: IO[String, Unit] = IO.fail[String]("IOs also can fail")
        val failedFuture: Task[Unit] =
          failedIO.toCompletableFutureWith(new Exception(_)).flatMap(f => Task(f.get()))
        assertM(
          failedFuture.run,
          fails[Throwable](hasField("message", _.getMessage, equalTo("java.lang.Exception: IOs also can fail")))
        )
      }
    ),
    suite("`Fiber.fromCompletionStage`")(
      test("be lazy on the `Future` parameter") {
        var evaluated                  = false
        def ftr: CompletionStage[Unit] = CompletableFuture.supplyAsync(() => evaluated = true)
        Fiber.fromCompletionStage(ftr)
        assert(evaluated, isFalse)
      },
      testM("catch exceptions thrown by lazy block") {
        val ex                              = new Exception("no future for you!")
        def noFuture: CompletionStage[Unit] = throw ex
        assertM(Fiber.fromCompletionStage(noFuture).join.run, equalTo[Exit[Throwable, Unit]](Exit.die(ex)))
      },
      testM("return an `IO` that fails if `Future` fails (failedFuture)") {
        val ex                             = new Exception("no value for you!")
        def noValue: CompletionStage[Unit] = CompletableFuture_.failedFuture(ex)
        assertM(Fiber.fromCompletionStage(noValue).join.run, fails[Throwable](equalTo(ex)))
      },
      testM("return an `IO` that fails if `Future` fails (supplyAsync)") {
        val ex                             = new Exception("no value for you!")
        def noValue: CompletionStage[Unit] = CompletableFuture.supplyAsync(() => throw ex)
        assertM(Fiber.fromCompletionStage(noValue).join.run, fails[Throwable](equalTo(ex)))
      },
      testM("return an `IO` that produces the value from `Future`") {
        def someValue: CompletionStage[Int] = CompletableFuture.completedFuture(42)
        assertM(Fiber.fromCompletionStage(someValue).join.run, succeeds(equalTo(42)))
      }
    ),
    suite("`Fiber.fromFutureJava` must")(
      test("be lazy on the `Future` parameter") {
        var evaluated         = false
        def ftr: Future[Unit] = CompletableFuture.supplyAsync(() => evaluated = true)
        Fiber.fromFutureJava(ftr)
        assert(evaluated, isFalse)
      },
      testM("catch exceptions thrown by lazy block") {
        val ex                     = new Exception("no future for you!")
        def noFuture: Future[Unit] = throw ex
        assertM(Fiber.fromFutureJava(noFuture).join.run, equalTo[Exit[Throwable, Unit]](Exit.die(ex)))
      },
      testM("return an `IO` that fails if `Future` fails (failedFuture)") {
        val ex                    = new Exception("no value for you!")
        def noValue: Future[Unit] = CompletableFuture_.failedFuture(ex)
        assertM(Fiber.fromFutureJava(noValue).join.run, fails[Throwable](equalTo(ex)))
      },
      testM("return an `IO` that fails if `Future` fails (failedFuture)") {
        val ex                    = new Exception("no value for you!")
        def noValue: Future[Unit] = CompletableFuture.supplyAsync(() => throw ex)
        assertM(Fiber.fromFutureJava(noValue).join.run, fails[Throwable](equalTo(ex)))
      },
      testM("return an `IO` that produces the value from `Future`") {
        def someValue: Future[Int] = CompletableFuture.completedFuture(42)
        assertM(Fiber.fromFutureJava(someValue).join.run, succeeds(equalTo(42)))
      }
    ),
    suite("`Task.withCompletionHandler` must")(
      testM("write and read to and from AsynchronousSocketChannel") {
        val list: List[Byte] = List(13)
        val address          = new InetSocketAddress(54321)
        val server           = AsynchronousServerSocketChannel.open().bind(address)
        val client           = AsynchronousSocketChannel.open()

        val taskServer = for {
          c <- ZIO.withCompletionHandler[AsynchronousSocketChannel](server.accept((), _))
          w <- ZIO.withCompletionHandler[Integer](c.write(ByteBuffer.wrap(list.toArray), (), _))
        } yield w

        val taskClient = for {
          _      <- ZIO.withCompletionHandler[Void](client.connect(address, (), _))
          buffer = ByteBuffer.allocate(1)
          r      <- ZIO.withCompletionHandler[Integer](client.read(buffer, (), _))
        } yield (r, buffer.array.toList)

        val task = for {
          fiberServer  <- taskServer.fork
          fiberClient  <- taskClient.fork
          resultServer <- fiberServer.join
          resultClient <- fiberClient.join
        } yield (resultServer, resultClient)

        assertM(task.run, succeeds[(Integer, (Integer, List[Byte]))](equalTo((1, (1, list)))))
      }
    )
  )
}

object JavaSpecMain extends DefaultRunnableSpec(JavaSpec.spec)
