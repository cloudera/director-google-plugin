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

import static com.cloudera.director.spi.v1.database.DatabaseServerInstanceTemplate.DatabaseServerInstanceTemplateConfigurationPropertyToken.ADMIN_PASSWORD;
import static com.cloudera.director.spi.v1.database.DatabaseServerInstanceTemplate.DatabaseServerInstanceTemplateConfigurationPropertyToken.ADMIN_USERNAME;
import static com.cloudera.director.spi.v1.database.DatabaseServerInstanceTemplate.DatabaseServerInstanceTemplateConfigurationPropertyToken.TYPE;

import com.cloudera.director.spi.v1.model.ConfigurationProperty;
import com.cloudera.director.spi.v1.model.ConfigurationPropertyToken;
import com.cloudera.director.spi.v1.model.util.SimpleConfigurationPropertyBuilder;

/**
 * Google Cloud SQL instance template configuration properties.
 */
public enum GoogleCloudSQLInstanceTemplateConfigurationProperty implements ConfigurationPropertyToken {

  TIER(new SimpleConfigurationPropertyBuilder()
      .configKey("tier")
      .name("Tier")
      .defaultValue("D1")
      .defaultDescription("The tier of your database. This affects performance and how much you will be charged.")
      .widget(ConfigurationProperty.Widget.OPENLIST)
      .addValidValues(
          "D0",
          "D1",
          "D2",
          "D4",
          "D8",
          "D16",
          "D32")
      .required(true)
      .build()),

  /**
   * The name of the master user for the client DB instance.
   */
  MASTER_USERNAME(new SimpleConfigurationPropertyBuilder()
      .configKey(ADMIN_USERNAME.unwrap().getConfigKey())
      .name("Master username")
      .defaultDescription("The name of the master user for the client DB instance. Username may contain up to 16 characters.")
      .build()),

  /**
   * The password for the master database user.
   */
  MASTER_USER_PASSWORD(new SimpleConfigurationPropertyBuilder()
      .configKey(ADMIN_PASSWORD.unwrap().getConfigKey())
      .name("Master user password")
      .widget(ConfigurationProperty.Widget.PASSWORD)
      .sensitive(true)
      .defaultDescription("The password for the master database user. Password may contain up to 16 characters.")
      .build()),

  PREFERRED_LOCATION(new SimpleConfigurationPropertyBuilder()
      .configKey("preferredLocation")
      .name("Preferred Location Zone")
      .defaultDescription("You can use this setting to store your data close to Compute Engine hosted within a particular region. This will reduce latency and improve availability for services in the preferred location you choose. You can store your data in a Compute Engine zone.")
      .widget(ConfigurationProperty.Widget.OPENLIST)
      .required(false)
      .build()),

  /**
   * The name of the database engine to be used for this instance.
   */
  ENGINE(new SimpleConfigurationPropertyBuilder()
      .configKey(TYPE.unwrap().getConfigKey())
      .name("DB engine")
      .required(true)
      .defaultDescription("The name of the database engine to be used for this instance.")
      .widget(ConfigurationProperty.Widget.LIST)
      .addValidValues("MYSQL")
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
  GoogleCloudSQLInstanceTemplateConfigurationProperty(ConfigurationProperty configurationProperty) {
      this.configurationProperty = configurationProperty;
  }

  @Override
  public ConfigurationProperty unwrap() {
    return configurationProperty;
  }
}
