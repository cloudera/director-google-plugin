/*
 * Copyright (c) 2015 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.cloudera.director.google.util;

import static junit.framework.Assert.fail;
import static org.assertj.core.api.Assertions.assertThat;

import com.cloudera.director.google.Configurations;
import com.cloudera.director.google.GoogleLauncher;
import com.cloudera.director.google.shaded.com.typesafe.config.Config;
import com.cloudera.director.google.TestUtils;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;

/**
 * Tests {@link Names}.
 */
public class NamesTest {

  @Test
  public void convertCloudSQLRegionToComputeRegion() throws IOException {
    Config googleConfig = TestUtils.buildGoogleConfig();
    assertThat(Names.convertCloudSQLRegionToComputeRegion("us-central", googleConfig)).isEqualTo("us-central1");
    assertThat(Names.convertCloudSQLRegionToComputeRegion("europe-west1", googleConfig)).isEqualTo("europe-west1");
    assertThat(Names.convertCloudSQLRegionToComputeRegion("asia-east1", googleConfig)).isEqualTo("asia-east1");
  }

}
