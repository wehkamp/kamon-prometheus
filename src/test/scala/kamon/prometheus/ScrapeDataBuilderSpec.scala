package kamon.prometheus

import com.typesafe.config.ConfigFactory
import kamon.metric.{DynamicRange, HdrHistogram, MetricDistribution, MetricValue}
import kamon.metric.MeasurementUnit
import org.scalatest.{Matchers, WordSpec}
import kamon.metric.MeasurementUnit.{information, none, time}

class ScrapeDataBuilderSpec extends WordSpec with Matchers {

  "the scrape data builder formatter" should {

    //    "asdsad" in {
    //      println("====================")
    //      println(builder().appendHistograms(Seq(
    //        constantDistribution("test", Map.empty, time.nanoseconds, 0, 100)))
    //        .build())
    //      println("====================")
    //    }
    "append units to the metric names when reporting values in the time dimension" in {
      val counterOne = MetricValue("counter-one", Map.empty, time.seconds, 10)
      val gaugeOne = MetricValue("gauge-one", Map.empty, time.seconds, 20)

      builder()
        .appendCounters(Seq(counterOne))
        .appendGauges(Seq(gaugeOne))
        .build() should include {
          """
                |# TYPE service_counter_one_seconds counter
                |service_counter_one_seconds 10.0
                |# TYPE service_gauge_one_seconds_gauge_max gauge
                |service_gauge_one_seconds_gauge_max 20.0
              """.stripMargin.trim()
        }
    }

    "append units to the metric names when reporting values in the information dimension" in {
      val counterOne = MetricValue("counter-one", Map.empty, information.bytes, 10)
      val gaugeOne = MetricValue("gauge-one", Map.empty, information.bytes, 20)

      builder()
        .appendCounters(Seq(counterOne))
        .appendGauges(Seq(gaugeOne))
        .build() should include {
          """
              |# TYPE service_counter_one_bytes counter
              |service_counter_one_bytes 10.0
              |# TYPE service_gauge_one_bytes_gauge_max gauge
              |service_gauge_one_bytes_gauge_max 20.0
            """.stripMargin.trim()
        }
    }

    "normalize tag names" in {
      val counterOne = MetricValue("counter-one", Map("tag.with.dots" → "value"), time.seconds, 10)
      val gaugeOne = MetricValue("gauge-one", Map.empty, time.seconds, 20)

      builder()
        .appendCounters(Seq(counterOne))
        .appendGauges(Seq(gaugeOne))
        .build() should include {
          """
              |# TYPE service_counter_one_seconds counter
              |service_counter_one_seconds{tag_with_dots="value"} 10.0
              |# TYPE service_gauge_one_seconds_gauge_max gauge
              |service_gauge_one_seconds_gauge_max 20.0
            """.stripMargin.trim()
        }
    }

    "append counters and group them together by metric name" in {
      val counterOne = MetricValue("counter-one", Map.empty, none, 10)
      val counterTwo = MetricValue("counter-two", Map.empty, none, 20)
      val counterOneWithTag = MetricValue("counter-one", Map("t" → "v"), none, 30)

      builder().appendCounters(Seq(counterOne, counterTwo, counterOneWithTag)).build() should include {
        """
              |# TYPE service_counter_one counter
              |service_counter_one 10.0
              |service_counter_one{t="v"} 30.0
              |# TYPE service_counter_two counter
              |service_counter_two 20.0
            """.stripMargin.trim()
      }

    }

    "append gauges and group them together by metric name" in {
      val gaugeOne = MetricValue("gauge-one", Map.empty, none, 10)
      val gaugeTwo = MetricValue("gauge-two", Map.empty, none, 20)
      val gaugeTwoWithTags = MetricValue("gauge-two", Map("t" → "v", "t2" → "v2"), none, 30)

      builder().appendGauges(Seq(gaugeOne, gaugeTwo, gaugeTwoWithTags)).build() should include {
        """
              |# TYPE service_gauge_one_gauge_max gauge
              |service_gauge_one_gauge_max 10.0
              |# TYPE service_gauge_two_gauge_max gauge
              |service_gauge_two_gauge_max 20.0
              |service_gauge_two_gauge_max{t="v",t2="v2"} 30.0
            """.stripMargin.trim()
      }
    }

    "read custom bucket configurations" in {
      val config = PrometheusReporter.Configuration.readConfiguration(ConfigFactory.parseString(
        """
              |kamon.prometheus.buckets.custom {
              |  singleword = [1, 2, 3]
              |  "with.several.dots" = [3, 2, 1]
              |}
            """.stripMargin
      ).withFallback(ConfigFactory.defaultReference()))

      config.customBuckets should contain allOf (
        ("singleword" → Seq(1D, 2D, 3D)),
        ("with.several.dots" → Seq(3D, 2D, 1D))
      )

    }

    //    "override histogram buckets with custom configuration" in {
    //      val customBucketsHistogram = constantDistribution("histogram.custom-buckets", Map.empty, none, 1, 10)
    //
    //      builder(customBuckets = Map("histogram.custom-buckets" → Seq(1D, 2D, 4D))).appendHistograms(Seq(customBucketsHistogram)).build() should include {
    //        """
    //              |# TYPE service_histogram_custom_buckets histogram
    //              |service_histogram_custom_buckets{quantile="1.0"} 1.0
    //              |service_histogram_custom_buckets{quantile="2.0"} 2.0
    //              |service_histogram_custom_buckets{quantile="4.0"} 4.0
    //              |service_histogram_custom_buckets{quantile="+Inf"} 10.0
    //              |service_histogram_custom_buckets_count 10.0
    //              |service_histogram_custom_buckets_sum 55.0
    //            """.stripMargin.trim()
    //      }
    //    }

    "append histograms grouped together by metric name and with all their derived time series " in {
      val histogramOne = constantDistribution("histogram-one", Map.empty, none, 1, 10)
      val histogramTwo = constantDistribution("histogram-two", Map.empty, none, 1, 20)
      val histogramThree = constantDistribution("histogram-three", Map.empty, none, 5, 10)
      val histogramWithZero = constantDistribution("histogram-with-zero", Map.empty, none, 0, 10)

      builder(buckets = Seq(15D)).appendHistograms(Seq(histogramOne)).build() should include {
        """
          |# TYPE service_histogram_one histogram
          |service_histogram_one{quantile="0.999"} 10.0
          |service_histogram_one{quantile="0.99"} 10.0
          |service_histogram_one{quantile="0.05"} 10.0
          |service_histogram_one{quantile="0.01"} 10.0
          |service_histogram_one{quantile="0.5"} 10.0
          |service_histogram_one{quantile="0.9"} 10.0
          |service_histogram_one{quantile="0.95"} 10.0
          |service_histogram_one{quantile="+Inf"} 10.0
          |service_histogram_one_count 10.0
          |service_histogram_one_sum 55.0
            """.stripMargin.trim()
      }

      builder(buckets = Seq(5D, 10D, 15D, 20D)).appendHistograms(Seq(histogramTwo)).build() should include {
        """
          |# TYPE service_histogram_two histogram
          |service_histogram_two{quantile="0.999"} 20.0
          |service_histogram_two{quantile="0.99"} 20.0
          |service_histogram_two{quantile="0.05"} 20.0
          |service_histogram_two{quantile="0.01"} 20.0
          |service_histogram_two{quantile="0.5"} 20.0
          |service_histogram_two{quantile="0.9"} 20.0
          |service_histogram_two{quantile="0.95"} 20.0
          |service_histogram_two{quantile="+Inf"} 20.0
          |service_histogram_two_count 20.0
          |service_histogram_two_sum 210.0
            """.stripMargin.trim()
      }

      builder(buckets = Seq(3D)).appendHistograms(Seq(histogramThree)).build() should include {
        """
          |# TYPE service_histogram_three histogram
          |service_histogram_three{quantile="0.999"} 2.0
          |service_histogram_three{quantile="0.99"} 2.0
          |service_histogram_three{quantile="0.05"} 2.0
          |service_histogram_three{quantile="0.01"} 2.0
          |service_histogram_three{quantile="0.5"} 2.0
          |service_histogram_three{quantile="0.9"} 2.0
          |service_histogram_three{quantile="0.95"} 2.0
          |service_histogram_three{quantile="+Inf"} 6.0
          |service_histogram_three_count 6.0
          |service_histogram_three_sum 45.0
            """.stripMargin.trim()
      }

      builder(buckets = Seq(3D, 50D)).appendHistograms(Seq(histogramThree)).build() should include {
        """
          |# TYPE service_histogram_three histogram
          |service_histogram_three{quantile="0.999"} 2.0
          |service_histogram_three{quantile="0.99"} 2.0
          |service_histogram_three{quantile="0.05"} 2.0
          |service_histogram_three{quantile="0.01"} 2.0
          |service_histogram_three{quantile="0.5"} 2.0
          |service_histogram_three{quantile="0.9"} 2.0
          |service_histogram_three{quantile="0.95"} 2.0
          |service_histogram_three{quantile="+Inf"} 6.0
          |service_histogram_three_count 6.0
          |service_histogram_three_sum 45.0
            """.stripMargin.trim()
      }

      builder(buckets = Seq(3D, 50D, 60D, 70D)).appendHistograms(Seq(histogramThree)).build() should include {
        """
          |# TYPE service_histogram_three histogram
          |service_histogram_three{quantile="0.999"} 2.0
          |service_histogram_three{quantile="0.99"} 2.0
          |service_histogram_three{quantile="0.05"} 2.0
          |service_histogram_three{quantile="0.01"} 2.0
          |service_histogram_three{quantile="0.5"} 2.0
          |service_histogram_three{quantile="0.9"} 2.0
          |service_histogram_three{quantile="0.95"} 2.0
          |service_histogram_three{quantile="+Inf"} 6.0
          |service_histogram_three_count 6.0
          |service_histogram_three_sum 45.0
            """.stripMargin.trim()
      }

      builder(buckets = Seq(7D)).appendHistograms(Seq(histogramThree)).build() should include {
        """
          |# TYPE service_histogram_three histogram
          |service_histogram_three{quantile="0.999"} 2.0
          |service_histogram_three{quantile="0.99"} 2.0
          |service_histogram_three{quantile="0.05"} 2.0
          |service_histogram_three{quantile="0.01"} 2.0
          |service_histogram_three{quantile="0.5"} 2.0
          |service_histogram_three{quantile="0.9"} 2.0
          |service_histogram_three{quantile="0.95"} 2.0
          |service_histogram_three{quantile="+Inf"} 6.0
          |service_histogram_three_count 6.0
          |service_histogram_three_sum 45.0
            """.stripMargin.trim()
      }

      builder(buckets = Seq(0.005D, 0.05D, 0.5D, 1D, 2D, 2.1D, 2.2D, 2.3D, 10D)).appendHistograms(Seq(histogramWithZero)).build() should include {
        """
          |# TYPE service_histogram_with_zero histogram
          |service_histogram_with_zero{quantile="0.999"} 11.0
          |service_histogram_with_zero{quantile="0.99"} 11.0
          |service_histogram_with_zero{quantile="0.05"} 11.0
          |service_histogram_with_zero{quantile="0.01"} 11.0
          |service_histogram_with_zero{quantile="0.5"} 11.0
          |service_histogram_with_zero{quantile="0.9"} 11.0
          |service_histogram_with_zero{quantile="0.95"} 11.0
          |service_histogram_with_zero{quantile="+Inf"} 11.0
          |service_histogram_with_zero_count 11.0
          |service_histogram_with_zero_sum 55.0
            """.stripMargin.trim()
      }
    }

    "include global custom tags from the Kamon.environment.tags" in {
      val counterOne = MetricValue("counter-one", Map("tag.with.dots" → "value"), time.seconds, 10)
      val gaugeOne = MetricValue("gauge-one", Map.empty, time.seconds, 20)

      builder(environmentTags = Map("env_key" → "env_value"))
        .appendCounters(Seq(counterOne))
        .appendGauges(Seq(gaugeOne))
        .build() should include {
          """
              |# TYPE service_counter_one_seconds counter
              |service_counter_one_seconds{tag_with_dots="value",env_key="env_value"} 10.0
              |# TYPE service_gauge_one_seconds_gauge_max gauge
              |service_gauge_one_seconds_gauge_max{env_key="env_value"} 20.0
            """.stripMargin.trim()
        }
    }

    "let environment tags have precedence over custom tags" in {
      val metric = MetricValue("some-metric", Map("custom.tag" → "custom-value"), time.seconds, 10)

      builder(environmentTags = Map("custom.tag" → "environment-value"))
        .appendCounters(Seq(metric))
        .appendGauges(Seq(metric))
        .build() should include {
          """
              |# TYPE service_some_metric_seconds counter
              |service_some_metric_seconds{custom_tag="environment-value"} 10.0
              |# TYPE service_some_metric_seconds_gauge_max gauge
              |service_some_metric_seconds_gauge_max{custom_tag="environment-value"} 10.0
            """.stripMargin.trim()
        }
    }
  }

  private def builder(buckets: Seq[java.lang.Double] = Seq(5D, 7D, 8D, 9D, 10D, 11D, 12D), customBuckets: Map[String, Seq[java.lang.Double]] = Map("histogram.custom-buckets" → Seq(1D, 3D)), environmentTags: Map[String, String] = Map.empty) = new ScrapeDataBuilder(
    PrometheusReporter.Configuration(false, "localhost", 1, buckets, buckets, buckets, customBuckets, false), environmentTags
  )

  private def constantDistribution(name: String, tags: Map[String, String], unit: MeasurementUnit, lower: Int, upper: Int): MetricDistribution = {
    val histogram = new HdrHistogram(name, tags, unit, DynamicRange.Default)
    for (value ← lower to upper) {
      histogram.record(value, 1)
    }

    histogram.snapshot(resetState = true)
  }
}
