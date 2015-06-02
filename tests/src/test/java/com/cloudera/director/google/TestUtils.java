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

import com.cloudera.director.google.shaded.com.typesafe.config.Config;
import com.cloudera.director.google.shaded.com.typesafe.config.ConfigFactory;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class TestUtils {

  public static String readFile(String path, Charset encoding) throws IOException {
    byte[] encoded = Files.readAllBytes(Paths.get(path));

    return new String(encoded, encoding);
  }

  public static String readRequiredSystemProperty(String systemPropertyKey) {
    String systemPropertyValue = System.getProperty(systemPropertyKey, "");

    if (!systemPropertyValue.isEmpty()) {
      return systemPropertyValue;
    } else {
      throw new IllegalArgumentException("System property '" + systemPropertyKey + "' is required.");
    }
  }

  public static Config buildGoogleConfig() throws IOException {
    Map<String, String> googleConfig = new HashMap<String, String>();
    googleConfig.put(Configurations.IMAGE_ALIASES_SECTION + "centos",
            "https://www.googleapis.com/compute/v1/projects/centos-cloud/global/images/centos-6-v20150325");
    googleConfig.put(Configurations.IMAGE_ALIASES_SECTION + "rhel",
            "https://www.googleapis.com/compute/v1/projects/rhel-cloud/global/images/rhel-6-v20150325");

    return ConfigFactory.parseMap(googleConfig);
  }
}
