package io.github.stoneream.dachshund.usecase.spotify.user_new_release_notification_delivery_queue

import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime

import scala.concurrent.duration.FiniteDuration

final case class UserNewReleaseNotificationDeliveryQueueUseCaseInput(
    now: BusinessDateTime,
    batchSize: Int,
    processingLease: FiniteDuration
) {
  require(batchSize > 0, "batchSize は正の値である必要があります")
  require(processingLease.length > 0, "processingLease は正の値である必要があります")
}
