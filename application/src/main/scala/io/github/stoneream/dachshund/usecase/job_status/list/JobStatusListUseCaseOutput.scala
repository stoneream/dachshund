package io.github.stoneream.dachshund.usecase.job_status.list

import io.github.stoneream.dachshund.usecase.job_status.context.{JobStatusJob, JobStatusJobOption}

final case class JobStatusListUseCaseOutput(
    user: JobStatusListUseCaseOutput.ViewUser,
    jobOptions: Seq[JobStatusJobOption]
)

object JobStatusListUseCaseOutput {
  final case class ViewUser(
      displayName: String
  )

  def build(userDisplayName: String): JobStatusListUseCaseOutput =
    JobStatusListUseCaseOutput(
      user = ViewUser(userDisplayName),
      jobOptions = JobStatusJob.All.map(JobStatusJobOption.fromJob)
    )
}
