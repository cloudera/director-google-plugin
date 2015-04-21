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

import com.cloudera.director.google.internal.GoogleCredentials;
import com.cloudera.director.spi.v1.model.Configured;
import com.cloudera.director.spi.v1.provider.CloudProvider;
import com.cloudera.director.spi.v1.provider.CredentialsProvider;
import com.cloudera.director.spi.v1.provider.util.AbstractLauncher;
import com.google.api.services.compute.Compute;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigParseOptions;
import com.typesafe.config.ConfigSyntax;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

public class GoogleLauncher extends AbstractLauncher {

  private static final String GOOGLE_CONFIG_FILENAME = "google.conf";

  private Config googleConfig = null;

  public GoogleLauncher() {
    super(Collections.singletonList(GoogleCloudProvider.METADATA));
  }

  /**
   * The config is loaded from a google.conf file on the classpath. If a google.conf file also exists in the
   * configuration directory, its values will override the values defined in the google.conf file on the
   * classpath.
   */
  @Override
  public void initialize(File configurationDirectory) {
    try {
      googleConfig = parseConfigFromClasspath(GOOGLE_CONFIG_FILENAME);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    // Check if an additional google.conf file exists in the configuration directory.
    File configFile = new File(configurationDirectory, GOOGLE_CONFIG_FILENAME);

    if (configFile.canRead()) {
      try {
        Config configFromFile = parseConfigFromFile(configFile);

        // Merge the two configurations, with values in configFromFile overriding values in googleConfig.
        googleConfig = configFromFile.withFallback(googleConfig);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

  }

  /**
   * Parses the specified configuration file from the classpath.
   *
   * @param configPath the path to the configuration file
   * @return the parsed configuration
   */
  private static Config parseConfigFromClasspath(String configPath) {
    ConfigParseOptions options = ConfigParseOptions.defaults()
            .setSyntax(ConfigSyntax.CONF)
            .setAllowMissing(false);

    return ConfigFactory.parseResourcesAnySyntax(configPath, options);
  }

  /**
   * Parses the specified configuration file.
   *
   * @param configFile the configuration file
   * @return the parsed configuration
   */
  private static Config parseConfigFromFile(File configFile) {
    ConfigParseOptions options = ConfigParseOptions.defaults()
            .setSyntax(ConfigSyntax.CONF)
            .setAllowMissing(false);

    return ConfigFactory.parseFileAnySyntax(configFile, options);
  }

  @Override
  public CloudProvider createCloudProvider(String cloudProviderId, Configured configuration) {

    if (!GoogleCloudProvider.ID.equals(cloudProviderId)) {
      throw new IllegalArgumentException("Cloud provider not found: " + cloudProviderId);
    }

    // At this point the configuration object will already contain
    // the required data for authentication.

    CredentialsProvider<GoogleCredentials> provider = new GoogleCredentialsProvider();
    GoogleCredentials credentials = provider.createCredentials(configuration);
    Compute compute = credentials.getCompute();

    if (compute == null) {
      throw new IllegalArgumentException("Invalid cloud provider credentials.");
    } else {
      String projectId = credentials.getProjectId();

      try {
        // Attempt GCP api call to verify credentials.
        compute.regions().list(projectId).execute();
      } catch (IOException e) {
        throw new IllegalArgumentException(
                "Invalid cloud provider credentials for project '" + projectId + "'.", e);
      }
    }

    return new GoogleCloudProvider(credentials, googleConfig);
  }
}
