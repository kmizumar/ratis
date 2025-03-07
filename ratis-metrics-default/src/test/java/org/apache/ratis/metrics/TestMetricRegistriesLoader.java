/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ratis.metrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.Mockito.mock;

import org.apache.ratis.metrics.impl.MetricRegistriesImpl;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

/**
 * Test class for {@link MetricRegistriesLoader}.
 */
public class TestMetricRegistriesLoader {
  @Test
  public void testLoadSingleInstance() {
    MetricRegistries loader = mock(MetricRegistries.class);
    MetricRegistries instance = MetricRegistriesLoader.load(Collections.singletonList(loader));
    assertEquals(loader, instance);
  }

  @Test
  public void testLoadMultipleInstances() {
    MetricRegistries loader1 = mock(MetricRegistries.class);
    MetricRegistries loader2 = mock(MetricRegistries.class);
    MetricRegistries loader3 = mock(MetricRegistries.class);
    MetricRegistries instance = MetricRegistriesLoader.load(Arrays.asList(loader1, loader2, loader3));

    // the load() returns the first instance
    assertEquals(loader1, instance);
    assertNotEquals(loader2, instance);
    assertNotEquals(loader3, instance);
  }

  @Test
  public void testLoadDefault() {
    final MetricRegistries r = MetricRegistriesLoader.load();
    Assert.assertSame(MetricRegistriesImpl.class, r.getClass());
  }

}
