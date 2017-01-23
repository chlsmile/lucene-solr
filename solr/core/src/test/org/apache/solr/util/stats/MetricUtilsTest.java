/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.solr.util.stats;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Snapshot;
import com.codahale.metrics.Timer;
import org.apache.solr.SolrTestCaseJ4;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.metrics.AggregateMetric;
import org.junit.Test;

public class MetricUtilsTest extends SolrTestCaseJ4 {

  @Test
  public void testSolrTimerGetSnapshot() {
    // create a timer with up to 100 data points
    final Timer timer = new Timer();
    final int iterations = random().nextInt(100);
    for (int i = 0; i < iterations; ++i) {
      timer.update(Math.abs(random().nextInt()) + 1, TimeUnit.NANOSECONDS);
    }
    // obtain timer metrics
    NamedList lst = new NamedList(MetricUtils.timerToMap(timer, false));
    // check that expected metrics were obtained
    assertEquals(14, lst.size());
    final Snapshot snapshot = timer.getSnapshot();
    // cannot test avgRequestsPerMinute directly because mean rate changes as time increases!
    // assertEquals(lst.get("avgRequestsPerSecond"), timer.getMeanRate());
    assertEquals(timer.getFiveMinuteRate(), lst.get("5minRate"));
    assertEquals(timer.getFifteenMinuteRate(), lst.get("15minRate"));
    assertEquals(MetricUtils.nsToMs(snapshot.getMean()), lst.get("mean_ms"));
    assertEquals(MetricUtils.nsToMs(snapshot.getMedian()), lst.get("median_ms"));
    assertEquals(MetricUtils.nsToMs(snapshot.get75thPercentile()), lst.get("p75_ms"));
    assertEquals(MetricUtils.nsToMs(snapshot.get95thPercentile()), lst.get("p95_ms"));
    assertEquals(MetricUtils.nsToMs(snapshot.get99thPercentile()), lst.get("p99_ms"));
    assertEquals(MetricUtils.nsToMs(snapshot.get999thPercentile()), lst.get("p999_ms"));
  }

  @Test
  public void testMetrics() throws Exception {
    MetricRegistry registry = new MetricRegistry();
    Counter counter = registry.counter("counter");
    counter.inc();
    Timer timer = registry.timer("timer");
    Timer.Context ctx = timer.time();
    Thread.sleep(100);
    ctx.stop();
    Meter meter = registry.meter("meter");
    meter.mark();
    Histogram histogram = registry.histogram("histogram");
    histogram.update(10);
    AggregateMetric am = new AggregateMetric();
    registry.register("aggregate", am);
    am.set("foo", 10);
    am.set("bar", 1);
    am.set("bar", 2);
    MetricUtils.toNamedMaps(registry, Collections.singletonList(MetricFilter.ALL), MetricFilter.ALL,
        false, (k, v) -> {
      if (k.startsWith("counter")) {
        assertEquals(1L, v.get("count"));
      } else if (k.startsWith("timer")) {
        assertEquals(1L, v.get("count"));
        assertTrue(((Number)v.get("min_ms")).intValue() > 100);
      } else if (k.startsWith("meter")) {
        assertEquals(1L, v.get("count"));
      } else if (k.startsWith("histogram")) {
        assertEquals(1L, v.get("count"));
      } else if (k.startsWith("aggregate")) {
        assertEquals(2, v.get("count"));
        Map<String, Object> values = (Map<String, Object>)v.get("values");
        assertNotNull(values);
        assertEquals(2, values.size());
        Map<String, Object> update = (Map<String, Object>)values.get("foo");
        assertEquals(10, update.get("value"));
        assertEquals(1, update.get("updateCount"));
        update = (Map<String, Object>)values.get("bar");
        assertEquals(2, update.get("value"));
        assertEquals(2, update.get("updateCount"));
      }
    });
  }

}
