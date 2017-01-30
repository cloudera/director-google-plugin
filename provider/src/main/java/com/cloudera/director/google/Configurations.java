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

/**
 * Constants for important properties and sections in the configuration file.
 *
 * @see <a href="https://github.com/typesafehub/config" />
 */
public class Configurations {

  private Configurations() {
  }

  /**
   * The configuration file name.
   */
  public static final String GOOGLE_CONFIG_FILENAME = "google.conf";

  /**
   * The package.
   */
  public static final String GOOGLE_PLUGIN_PACKAGE = "/com/cloudera/director/google/";

  /**
   * The configuration file name including package qualification.
   */
  public static final String GOOGLE_CONFIG_QUALIFIED_FILENAME = GOOGLE_PLUGIN_PACKAGE + GOOGLE_CONFIG_FILENAME;

  /**
   * The application properties file name including package qualification.
   */
  public static final String APPLICATION_PROPERTIES_FILENAME = GOOGLE_PLUGIN_PACKAGE + "application.properties";

  /**
   * The HOCON path prefix for image aliases configuration.
   */
  public static final String IMAGE_ALIASES_SECTION = "google.compute.imageAliases.";

  /**
   * The HOCON key for the polling timeout, in seconds, for pending compute operations.
   */
  public static final String COMPUTE_POLLING_TIMEOUT_KEY = "google.compute.pollingTimeoutSeconds";

  /**
   * The HOCON key for the maximum polling interval, in seconds, for pending compute operations.
   */
  public static final String COMPUTE_MAX_POLLING_INTERVAL_KEY = "google.compute.maxPollingIntervalSeconds";
}
