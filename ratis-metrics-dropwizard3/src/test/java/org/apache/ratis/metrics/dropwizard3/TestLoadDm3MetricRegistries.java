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
package org.apache.ratis.metrics.dropwizard3;

import org.apache.ratis.metrics.MetricRegistries;
import org.apache.ratis.metrics.MetricRegistriesLoader;
import org.junit.Assert;
import org.junit.Test;

/**
 * Test class for {@link MetricRegistriesLoader}.
 */
public class TestLoadDm3MetricRegistries {
  @Test
  public void testLoadDm3() {
    final MetricRegistries r = MetricRegistriesLoader.load();
    Assert.assertSame(Dm3MetricRegistriesImpl.class, r.getClass());
  }
}
