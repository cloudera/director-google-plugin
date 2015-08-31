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

package com.cloudera.director.google.sql;

import com.cloudera.director.spi.v1.model.ConfigurationProperty;
import com.cloudera.director.spi.v1.model.ConfigurationPropertyToken;
import com.cloudera.director.spi.v1.model.util.SimpleConfigurationPropertyBuilder;

/**
 * Google Cloud SQL configuration properties.
 */
public enum GoogleCloudSQLProviderConfigurationProperty implements ConfigurationPropertyToken {

  REGION_SQL(new SimpleConfigurationPropertyBuilder()
      .configKey("region_sql")
      .name("Region")
      .defaultValue("us-central")
      .defaultDescription(
          "Region to target for deployment.<br />" +
          "<a target='_blank' href='https://cloud.google.com/compute/docs/zones'>More Information</a>")
      .widget(ConfigurationProperty.Widget.OPENLIST)
      .addValidValues(
          "us-central",
          "asia-east1",
          "europe-west1")
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
  private GoogleCloudSQLProviderConfigurationProperty(ConfigurationProperty configurationProperty) {
    this.configurationProperty = configurationProperty;
  }

  @Override
  public ConfigurationProperty unwrap() {
      return configurationProperty;
  }
}

