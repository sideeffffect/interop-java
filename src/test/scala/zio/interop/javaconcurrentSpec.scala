package zio
package interop

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.{ AsynchronousServerSocketChannel, AsynchronousSocketChannel }
import java.util.concurrent.{ CompletableFuture, CompletionStage, Future }

import org.specs2.concurrent.ExecutionEnv
import zio.Cause.{ die, fail }
import zio.interop.javaconcurrent._

class javaconcurrentSpec(implicit ee: ExecutionEnv) extends TestRuntime {

  def is = s2"""
  `Task.fromFutureJava` must
    be lazy on the `Future` parameter                    $lazyOnParamRef
    catch exceptions thrown by lazy block                $catchBlockException
    return an `IO` that fails if `Future` fails          $propagateExceptionFromFuture
    return an `IO` that produces the value from `Future` $produceValueFromFuture
    handle null produced by the completed `Future`       $handleNullFromFuture
  `Task.fromCompletionStage` must
    be lazy on the `Future` parameter                    $lazyOnParamRefCs
    catch exceptions thrown by lazy block                $catchBlockExceptionCs
    return an `IO` that fails if `Future` fails          $propagateExceptionFromCs
    return an `IO` that produces the value from `Future` $produceValueFromCs
    handle null produced by the completed `Future`       $handleNullFromCs
  `Task.toCompletableFuture` must
    produce always a successful `IO` of `Future`         $toCompletableFutureAlwaysSucceeds
    be polymorphic in error type                         $toCompletableFuturePoly
    return a `CompletableFuture` that fails if `IO` fails           $toCompletableFutureFailed
    return a `CompletableFuture` that produces the value from `IO`  $toCompletableFutureValue
  `Task.toCompletableFutureE` must
    convert error of type `E` to `Throwable`             $toCompletableFutureE
  `Fiber.fromCompletionStage` must
    be lazy on the `Future` parameter                    $lazyOnParamRefFiberCs
    catch exceptions thrown by lazy block                $catchBlockExceptionFiberCs
    return an `IO` that fails if `Future` fails          $propagateExceptionFromFutureFiberCs
    return an `IO` that produces the value from `Future` $produceValueFromFutureFiberCs
  `Fiber.fromFutureJava` must
    be lazy on the `Future` parameter                    $lazyOnParamRefFiberFuture
    catch exceptions thrown by lazy block                $catchBlockExceptionFiberFuture
    return an `IO` that fails if `Future` fails          $propagateExceptionFromFutureFiberFuture
    return an `IO` that produces the value from `Future` $produceValueFromFutureFiberFuture
  `Task.withCompletionHandler` must
    write and read to and from AsynchronousSocketChannel $withCompletionHandlerSocketChannels
  """

  def lazyOnParamRef = {
    var evaluated         = false
    def ftr: Future[Unit] = CompletableFuture.supplyAsync(() => evaluated = true)
    Task.fromFutureJava(UIO.succeedLazy(ftr))
    evaluated must beFalse
  }

  def catchBlockException = {
    val ex                          = new Exception("no future for you!")
    val noFuture: UIO[Future[Unit]] = UIO.succeedLazy(throw ex)
    unsafeRunSync(Task.fromFutureJava(noFuture)) must_=== Exit.Failure(die(ex))
  }

  def propagateExceptionFromFuture = {
    val ex                         = new Exception("no value for you!")
    val noValue: UIO[Future[Unit]] = UIO.succeedLazy(CompletableFuture.supplyAsync(() => throw ex))
    unsafeRunSync(Task.fromFutureJava(noValue)) must_=== Exit.Failure(fail(ex))
  }

  def produceValueFromFuture = {
    val someValue: UIO[Future[Int]] = UIO.succeedLazy(CompletableFuture.completedFuture(42))
    unsafeRun(Task.fromFutureJava(someValue)) must_=== 42
  }

  def handleNullFromFuture = {
    val someValue: UIO[Future[String]] = UIO.succeedLazy(CompletableFuture.completedFuture[String](null))
    unsafeRun(Task.fromFutureJava[String](someValue)) must_=== null
  }

  def lazyOnParamRefCs = {
    var evaluated                 = false
    def cs: CompletionStage[Unit] = CompletableFuture.supplyAsync(() => evaluated = true)
    Task.fromCompletionStage(UIO.succeedLazy(cs))
    evaluated must beFalse
  }

  def catchBlockExceptionCs = {
    val ex                                   = new Exception("no future for you!")
    val noFuture: UIO[CompletionStage[Unit]] = UIO.succeedLazy(throw ex)
    unsafeRunSync(Task.fromCompletionStage(noFuture)) must_=== Exit.Failure(die(ex))
  }

  def propagateExceptionFromCs = {
    val ex                                  = new Exception("no value for you!")
    val noValue: UIO[CompletionStage[Unit]] = UIO.succeedLazy(CompletableFuture.supplyAsync(() => throw ex))
    unsafeRunSync(Task.fromCompletionStage(noValue)) must_=== Exit.Failure(fail(ex))
  }

  def produceValueFromCs = {
    val someValue: UIO[CompletionStage[Int]] = UIO.succeedLazy(CompletableFuture.completedFuture(42))
    unsafeRun(Task.fromCompletionStage(someValue)) must_=== 42
  }

  def handleNullFromCs = {
    val someValue: UIO[CompletionStage[String]] = UIO.succeedLazy(CompletableFuture.completedFuture[String](null))
    unsafeRun(Task.fromCompletionStage[String](someValue)) must_=== null
  }

  def toCompletableFutureAlwaysSucceeds = {
    val failedIO = IO.fail[Throwable](new Exception("IOs also can fail"))
    unsafeRun(failedIO.toCompletableFuture) must beAnInstanceOf[CompletableFuture[Unit]]
  }

  def toCompletableFuturePoly = {
    val unitIO: Task[Unit]                          = Task.unit
    val polyIO: IO[String, CompletableFuture[Unit]] = unitIO.toCompletableFuture
    val _                                           = polyIO // avoid warning
    ok
  }

  def toCompletableFutureFailed = {
    val failedIO: Task[Unit] = IO.fail[Throwable](new Exception("IOs also can fail"))
    unsafeRun(failedIO.toCompletableFuture).get() must throwA[Exception](message = "IOs also can fail")
  }

  def toCompletableFutureValue = {
    val someIO = Task.succeed[Int](42)
    unsafeRun(someIO.toCompletableFuture).get() must beEqualTo(42)
  }

  def toCompletableFutureE = {
    val failedIO: IO[String, Unit] = IO.fail[String]("IOs also can fail")
    unsafeRun(failedIO.toCompletableFutureWith(new Exception(_))).get() must throwA[Exception](
      message = "IOs also can fail"
    )
  }

  def lazyOnParamRefFiberCs = {
    var evaluated                  = false
    def ftr: CompletionStage[Unit] = CompletableFuture.supplyAsync(() => evaluated = true)
    Fiber.fromCompletionStage(ftr)
    evaluated must beFalse
  }

  def catchBlockExceptionFiberCs = {
    val ex                              = new Exception("no future for you!")
    def noFuture: CompletionStage[Unit] = throw ex
    unsafeRunSync(Fiber.fromCompletionStage(noFuture).join) must_=== Exit.Failure(die(ex))
  }

  def propagateExceptionFromFutureFiberCs = {
    val ex                             = new Exception("no value for you!")
    def noValue: CompletionStage[Unit] = CompletableFuture.supplyAsync(() => throw ex)
    unsafeRunSync(Fiber.fromCompletionStage(noValue).join) must_=== Exit.Failure(fail(ex))
  }

  def produceValueFromFutureFiberCs = {
    def someValue: CompletionStage[Int] = CompletableFuture.completedFuture(42)
    unsafeRun(Fiber.fromCompletionStage(someValue).join) must_=== 42
  }

  def lazyOnParamRefFiberFuture = {
    var evaluated         = false
    def ftr: Future[Unit] = CompletableFuture.supplyAsync(() => evaluated = true)
    Fiber.fromFutureJava(ftr)
    evaluated must beFalse
  }

  def catchBlockExceptionFiberFuture = {
    val ex                     = new Exception("no future for you!")
    def noFuture: Future[Unit] = throw ex
    unsafeRunSync(Fiber.fromFutureJava(noFuture).join) must_=== Exit.Failure(die(ex))
  }

  def propagateExceptionFromFutureFiberFuture = {
    val ex                    = new Exception("no value for you!")
    def noValue: Future[Unit] = CompletableFuture.supplyAsync(() => throw ex)
    unsafeRunSync(Fiber.fromFutureJava(noValue).join) must_=== Exit.Failure(fail(ex))
  }

  def produceValueFromFutureFiberFuture = {
    def someValue: Future[Int] = CompletableFuture.completedFuture(42)
    unsafeRun(Fiber.fromFutureJava(someValue).join) must_=== 42
  }

  def withCompletionHandlerSocketChannels = {
    val list: List[Byte] = List(13)
    val address          = new InetSocketAddress(54321)
    val server           = AsynchronousServerSocketChannel.open().bind(address)
    val client           = AsynchronousSocketChannel.open()

    val taskServer = for {
      c <- Task.withCompletionHandler[AsynchronousSocketChannel](server.accept((), _))
      w <- Task.withCompletionHandler[Integer](c.write(ByteBuffer.wrap(list.toArray), (), _))
    } yield w

    val taskClient = for {
      _      <- Task.withCompletionHandler[Void](client.connect(address, (), _))
      buffer = ByteBuffer.allocate(1)
      r      <- Task.withCompletionHandler[Integer](client.read(buffer, (), _))
    } yield (r, buffer.array.toList)

    val task = for {
      fiberServer  <- taskServer.fork
      fiberClient  <- taskClient.fork
      resultServer <- fiberServer.join
      resultClient <- fiberClient.join
    } yield (resultServer, resultClient)

    val actual = unsafeRun(task)
    actual must_=== ((1, (1, list)))
  }
}
