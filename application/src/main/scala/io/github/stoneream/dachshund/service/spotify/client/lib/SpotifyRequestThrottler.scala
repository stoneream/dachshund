package io.github.stoneream.dachshund.service.spotify.client.lib

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.config.ApplicationConfig

import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.{CompletableFuture, TimeUnit}
import scala.annotation.tailrec
import scala.concurrent.duration.*
import scala.concurrent.{ExecutionContext, Future, Promise}

@Singleton
private[spotify] class SpotifyRequestThrottler @Inject() (
    applicationConfig: ApplicationConfig
) {
  import SpotifyRequestThrottler.*

  private val pacingInterval = applicationConfig.spotify.client.requestPolicy.pacingInterval
  private val state = new AtomicReference(ThrottleState.empty)

  def acquirePermit()(using executionContext: ExecutionContext): Future[Either[Throttled, Unit]] = {
    val decision = modifyState { currentState =>
      val nowNanos = System.nanoTime()
      activeBlock(currentState, nowNanos) match {
        case Some(value) =>
          currentState.copy(blockedUntilNanos = Some(value)) -> Left(toThrottled(value, nowNanos))
        case None =>
          val permitAtNanos = math.max(nowNanos, currentState.nextPermitAtNanos)
          val nextState = ThrottleState(
            nextPermitAtNanos = addNanosSafely(permitAtNanos, pacingInterval.toNanos),
            blockedUntilNanos = None
          )
          nextState -> Right(math.max(0L, permitAtNanos - nowNanos))
      }
    }

    decision match {
      case Left(value) =>
        Future.successful(Left(value))
      case Right(0L) =>
        Future.successful(validatePermit())
      case Right(delayNanos) =>
        delayed(delayNanos).map(_ => validatePermit())
    }
  }

  def registerRateLimit(delay: FiniteDuration): Unit =
    modifyState { currentState =>
      val nowNanos = System.nanoTime()
      val blockedUntilNanos = addNanosSafely(nowNanos, delay.toNanos)
      val nextBlockedUntilNanos = activeBlock(currentState, nowNanos)
        .map(existing => math.max(existing, blockedUntilNanos))
        .orElse(Some(blockedUntilNanos))
      currentState.copy(blockedUntilNanos = nextBlockedUntilNanos) -> ()
    }

  private def validatePermit(): Either[Throttled, Unit] =
    modifyState { currentState =>
      val nowNanos = System.nanoTime()
      val currentBlock = activeBlock(currentState, nowNanos)
      currentState.copy(blockedUntilNanos = currentBlock) ->
        currentBlock.map(toThrottled(_, nowNanos)).toLeft(())
    }

  @tailrec
  private def modifyState[A](
      transition: ThrottleState => (ThrottleState, A)
  ): A = {
    val currentState = state.get()
    val (nextState, result) = transition(currentState)
    if (state.compareAndSet(currentState, nextState)) {
      result
    } else {
      modifyState(transition)
    }
  }

  private def activeBlock(
      currentState: ThrottleState,
      nowNanos: Long
  ): Option[Long] =
    currentState.blockedUntilNanos.filter(_ > nowNanos)

  private def toThrottled(blockedUntilNanos: Long, nowNanos: Long): Throttled =
    Throttled(
      retryAfter = math.max(1L, blockedUntilNanos - nowNanos).nanos
    )

  private def delayed(delayNanos: Long): Future[Unit] = {
    val promise = Promise[Unit]()
    CompletableFuture
      .runAsync(
        () => promise.success(()),
        CompletableFuture.delayedExecutor(delayNanos, TimeUnit.NANOSECONDS)
      )
      .exceptionally { exception =>
        promise.failure(exception)
        null
      }
    promise.future
  }

  private def addNanosSafely(base: Long, addition: Long): Long =
    if (addition <= 0L || base > Long.MaxValue - addition) {
      if (addition <= 0L) base else Long.MaxValue
    } else {
      base + addition
    }
}

private[spotify] object SpotifyRequestThrottler {
  final case class Throttled(
      retryAfter: FiniteDuration
  )

  private final case class ThrottleState(
      nextPermitAtNanos: Long,
      blockedUntilNanos: Option[Long]
  )

  private object ThrottleState {
    val empty: ThrottleState =
      ThrottleState(
        nextPermitAtNanos = 0L,
        blockedUntilNanos = None
      )
  }
}
