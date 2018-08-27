/* =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

package kamon.prometheus

import java.lang.StringBuilder
import java.text.{DecimalFormatSymbols, DecimalFormat}
import java.util.Locale

import kamon.metric._
import kamon.metric.MeasurementUnit.none
import kamon.metric.MeasurementUnit.Dimension._

class ScrapeDataBuilder(prometheusConfig: PrometheusReporter.Configuration, environmentTags: Map[String, String] = Map.empty) {
  private val builder = new StringBuilder()
  private val decimalFormatSymbols = DecimalFormatSymbols.getInstance(Locale.ROOT)
  private val numberFormat = new DecimalFormat("#0.0########", decimalFormatSymbols)
  private val quantiles = List(0.01, 0.05, 0.5, 0.9, 0.95, 0.99, 0.999)

  import builder.append

  def build(): String =
    builder
      .toString
      .replace("service_http_server_nanoseconds_count{", "service_http_server{")
      .replace("service_http_server_nanoseconds{", "service_trace_elapsed_time_nanoseconds{")

  def appendCounters(counters: Seq[MetricValue]): ScrapeDataBuilder = {
    counters.groupBy(_.name).foreach(appendValueMetric("counter", alwaysIncreasing = true))
    this
  }

  def appendGauges(gauges: Seq[MetricValue]): ScrapeDataBuilder = {
    gauges.groupBy(_.name).foreach(appendValueMetric("gauge", alwaysIncreasing = false))
    this
  }

  def appendHistograms(histograms: Seq[MetricDistribution]): ScrapeDataBuilder = {
    histograms.groupBy(_.name).foreach(appendDistributionMetric)
    this
  }

  private def appendValueMetric(metricType: String, alwaysIncreasing: Boolean)(group: (String, Seq[MetricValue])): Unit = {
    val (metricName, snapshots) = group
    val unit = snapshots.headOption.map(_.unit).getOrElse(none)
    val tags = snapshots.headOption.map(_.tags).getOrElse(Map.empty)
    val normalizedMetricName = normalizeMetricName(metricName, unit, tags) + {
      if (alwaysIncreasing) "" else "_gauge_max"
    }

    append("# TYPE ").append(normalizedMetricName).append(" ").append(metricType).append("\n")

    snapshots.foreach(metric ⇒ {
      append(normalizedMetricName)
      appendTags(metric.tags)
      append(" ")
      append(format(metric.value))
      append("\n")
    })
  }

  private def appendDistributionMetric(group: (String, Seq[MetricDistribution])): Unit = {
    val (metricName, snapshots) = group
    val unit = snapshots.headOption.map(_.unit).getOrElse(none)
    val tags = snapshots.headOption.map(_.tags).getOrElse(Map.empty)
    val normalizedMetricName = normalizeMetricName(metricName, unit, tags)

    append("# TYPE ").append(normalizedMetricName).append(" histogram").append("\n")

    snapshots.foreach(metric ⇒ {
      if (metric.distribution.count > 0) {
        val buckets = quantiles.map(q ⇒ (q, metric.distribution.percentile(q * 100).value)).toMap
        appendHistogramBuckets(normalizedMetricName, metric.tags, metric, buckets)

        val count = format(metric.distribution.count)
        val sum = format(metric.distribution.sum)
        appendTimeSerieValue(normalizedMetricName, metric.tags, count, "_count")
        appendTimeSerieValue(normalizedMetricName, metric.tags, sum, "_sum")
      }
    })
  }

  private def appendTimeSerieValue(name: String, tags: Map[String, String], value: String, suffix: String = ""): Unit = {
    append(name)
    append(suffix)
    appendTags(tags)
    append(" ")
    append(value)
    append("\n")
  }

  private def appendHistogramBuckets(name: String, tags: Map[String, String], metric: MetricDistribution, buckets: Map[Double, Long]): Unit = {
//    val distributionBuckets = metric.distribution.bucketsIterator
//    var currentDistributionBucket = distributionBuckets.next()
//    var currentDistributionBucketValue = currentDistributionBucket.value
//    var inBucketCount = 0L
//    var leftOver = currentDistributionBucket.frequency

    buckets.foreach {
      case (q, c) ⇒
        val bucketTags = tags + ("quantile" → String.valueOf(q))

        //        if (currentDistributionBucketValue <= c) {
        //          inBucketCount += leftOver
        //          leftOver = 0
        //
        //          while (distributionBuckets.hasNext && currentDistributionBucketValue <= c) {
        //            currentDistributionBucket = distributionBuckets.next()
        //            currentDistributionBucketValue = currentDistributionBucket.value
        //
        //            if (currentDistributionBucketValue <= c) {
        //              inBucketCount += currentDistributionBucket.frequency
        //            } else
        //              leftOver = currentDistributionBucket.frequency
        //          }
        //        }

        appendTimeSerieValue(name, bucketTags, format(c))
    }

//    while (distributionBuckets.hasNext) {
//      leftOver += distributionBuckets.next().frequency
//    }

//    appendTimeSerieValue(name, tags + ("quantile" → "+Inf"), format(leftOver + inBucketCount))
  }

  private def appendTags(tags: Map[String, String]): Unit = {
    val allTags = (tags ++ environmentTags).map {
      case ("operation", v)        ⇒ ("trace-name", v)
      case ("http.status_code", v) ⇒ ("status", v)
      case (k, v)                  ⇒ (k, v)
    }

    if (allTags.nonEmpty) append("{")

    val tagIterator = allTags.iterator
    var tagCount = 0

    while (tagIterator.hasNext) {
      val (key, value) = tagIterator.next()
      if (tagCount > 0) append(",")
      append(normalizeLabelName(key)).append("=\"").append(value).append('"')
      tagCount += 1
    }

    if (allTags.nonEmpty) append("}")
  }

  private def normalizeMetricName(metricName: String, unit: MeasurementUnit, tags: Map[String, String]): String = {
    val blazeMetricName =
      if (metricName.contains("akka.http.server"))
        metricName
          .replace("akka.http.server", "http_server") //todo what about spray servers?
      else if (metricName.contains("span.processing-time") && tags.get("span.kind").contains("server"))
        "http_server"
      else metricName

    val normalizedMetricName = "service_" + blazeMetricName.map(charOrUnderscore)

    val magnitude = unit.magnitude.name match {
      case "byte" ⇒ "bytes"
      case other  ⇒ other
    }

    unit.dimension match {
      case Time | Information ⇒ normalizedMetricName + "_" + magnitude
      case _                  ⇒ normalizedMetricName
    }
  }

  private def normalizeLabelName(label: String): String =
    label.map(charOrUnderscore)

  private def charOrUnderscore(char: Char): Char =
    if (char.isLetterOrDigit || char == '_') char else '_'

  private def format(value: Double): String =
    numberFormat.format(value)

}
