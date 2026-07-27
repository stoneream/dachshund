package io.github.stoneream.dachshund.usecase.job_status.context

final case class JobStatusJobOption(
    name: String,
    title: String,
    path: String
)

object JobStatusJobOption {
  def fromJob(job: JobStatusJob): JobStatusJobOption =
    JobStatusJobOption(
      name = job.name,
      title = job.title,
      path = s"/job/status/${job.name}"
    )
}
