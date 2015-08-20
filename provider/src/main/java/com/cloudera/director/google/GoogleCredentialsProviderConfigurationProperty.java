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

import com.cloudera.director.spi.v1.model.ConfigurationProperty;
import com.cloudera.director.spi.v1.model.ConfigurationPropertyToken;
import com.cloudera.director.spi.v1.model.util.SimpleConfigurationPropertyBuilder;

/**
 * An enum of properties required for building credentials.
 */
public enum GoogleCredentialsProviderConfigurationProperty implements ConfigurationPropertyToken {

  PROJECT_ID(new SimpleConfigurationPropertyBuilder()
      .configKey("projectId")
      .name("Project ID")
      .defaultDescription("Google Cloud Project ID.")
      .defaultErrorMessage("Project ID is mandatory")
      .required(true)
      .build()),

  JSON_KEY(new SimpleConfigurationPropertyBuilder()
      .configKey("jsonKey")
      .name("Client ID JSON Key")
      .defaultDescription(
          "Google Cloud service account JSON key.<br />" +
          "Leave unset to get Google credentials from the environment.<br />" +
          "<a target='_blank' href='https://cloud.google.com/compute/docs/authentication#general'>More Information</a>")
      .widget(ConfigurationProperty.Widget.FILE)
      .sensitive(true)
      .build());

  /**
   * The configuration property.
   */
  private final ConfigurationProperty configurationProperty;

  /**
   * Creates a configuration property token with the specified parameters.
   *
   * @param configurationProperty the configuration property
   */
  GoogleCredentialsProviderConfigurationProperty(
      ConfigurationProperty configurationProperty) {
    this.configurationProperty = configurationProperty;
  }

  @Override
  public ConfigurationProperty unwrap() {
    return configurationProperty;
  }
}
