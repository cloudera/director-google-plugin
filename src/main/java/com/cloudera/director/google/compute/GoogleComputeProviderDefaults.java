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

package com.cloudera.director.google.compute;

import java.util.HashMap;
import java.util.Map;

// TODO(duftler): Externalize these defaults.
// https://github.com/cloudera/director-google-plugin/issues/2
public class GoogleComputeProviderDefaults {
  public static Map<String, String> IMAGE_ALIAS_TO_RESOURCE_MAP = new HashMap<String, String>();

  static {
    // TODO(duftler): Populate this map with appropriate choices.
    IMAGE_ALIAS_TO_RESOURCE_MAP.put(
        "ubuntu",
        "https://www.googleapis.com/compute/v1/projects/ubuntu-os-cloud/global/images/ubuntu-1404-trusty-v20150316");
  }
}
