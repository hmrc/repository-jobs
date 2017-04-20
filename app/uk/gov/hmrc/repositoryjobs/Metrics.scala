package uk.gov.hmrc.repositoryjobs


import com.kenshoo.play.metrics.{Metrics, MetricsImpl}
import play.api.Play


trait DefaultMetricsRegistry {
  private val metrics: Metrics = Play.current.injector.instanceOf[Metrics]
  val defaultMetricsRegistry = metrics.defaultRegistry
}
