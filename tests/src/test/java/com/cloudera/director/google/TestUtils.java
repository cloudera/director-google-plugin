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

package com.cloudera.director.google;

import static org.junit.Assert.fail;

import com.cloudera.director.google.shaded.com.typesafe.config.Config;
import com.cloudera.director.google.shaded.com.typesafe.config.ConfigFactory;
import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

public class TestUtils {

  public static String readFile(String path, Charset encoding) throws IOException {
    return Files.toString(new File(path), encoding);
  }

  public static String readRequiredSystemProperty(String systemPropertyKey) {
    String systemPropertyValue = System.getProperty(systemPropertyKey, "");

    if (systemPropertyValue.isEmpty()) {
      fail("System property '" + systemPropertyKey + "' is required.");
    }

    return systemPropertyValue;
  }

  public static Config buildApplicationPropertiesConfig() throws IOException {
    Map<String, String> applicationProperties = new HashMap<String, String>();
    applicationProperties.put("application.name", "Cloudera-Director-Google-Plugin");
    applicationProperties.put("application.version", "1.0.0-SNAPSHOT");

    return ConfigFactory.parseMap(applicationProperties);
  }

  public static Config buildGoogleConfig() throws IOException {
    Map<String, String> googleConfig = new HashMap<String, String>();
    googleConfig.put(Configurations.IMAGE_ALIASES_SECTION + "centos6",
        "https://www.googleapis.com/compute/v1/projects/centos-cloud/global/images/centos-6-v20150526");
    googleConfig.put(Configurations.IMAGE_ALIASES_SECTION + "rhel6",
        "https://www.googleapis.com/compute/v1/projects/rhel-cloud/global/images/rhel-6-v20150526");

    return ConfigFactory.parseMap(googleConfig);
  }

  public static String buildImageUrl(String projectId, String image) {
    return "https://www.googleapis.com/compute/v1/projects/" + projectId +
        "/global/images/" + image;
  }

  public static String buildInstanceUrl(String projectId, String zone, String instanceName) {
    return "https://www.googleapis.com/compute/v1/projects/" + projectId +
        "/zones/" + zone + "/instances/" + instanceName;
  }

  public static String buildDiskUrl(String projectId, String zone, String diskName) {
    return "https://www.googleapis.com/compute/v1/projects/" + projectId +
        "/zones/" + zone + "/disks/" + diskName;
  }
}
