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

import com.cloudera.director.google.Configurations;
import com.cloudera.director.google.GoogleLauncher;
import com.typesafe.config.Config;

public class Names {

  public static String buildApplicationNameVersionTag(Config applicationProperties) {
    return applicationProperties.getString("application.name") + "/" +
        applicationProperties.getString("application.version");
  }

  public static String convertCloudSQLRegionToComputeRegion(String cloudSQLRegion, Config googleConfig) {
    return googleConfig.getString(Configurations.CLOUD_SQL_REGIONS_ALIASES_SECTION + cloudSQLRegion);
  }
}
