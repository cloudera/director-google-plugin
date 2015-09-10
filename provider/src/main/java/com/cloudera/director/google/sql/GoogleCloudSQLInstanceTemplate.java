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

import com.cloudera.director.spi.v1.database.DatabaseServerInstanceTemplate;
import com.cloudera.director.spi.v1.model.ConfigurationProperty;
import com.cloudera.director.spi.v1.model.Configured;
import com.cloudera.director.spi.v1.model.LocalizationContext;
import com.cloudera.director.spi.v1.util.ConfigurationPropertiesUtil;

import java.util.List;
import java.util.Map;

public class GoogleCloudSQLInstanceTemplate extends DatabaseServerInstanceTemplate {

  /**
   * The list of configuration properties (including inherited properties).
   */
  private static final List<ConfigurationProperty> CONFIGURATION_PROPERTIES =
      ConfigurationPropertiesUtil.merge(
          DatabaseServerInstanceTemplate.getConfigurationProperties(),
          ConfigurationPropertiesUtil.asConfigurationPropertyList(
              GoogleCloudSQLInstanceTemplateConfigurationProperty.values())
      );

  /**
   * Returns the list of configuration properties for creating credentials.
   *
   * @return the list of configuration properties for creating credentials
   */
  public static List<ConfigurationProperty> getConfigurationProperties() {
    return CONFIGURATION_PROPERTIES;
  }

  /**
   * Creates a Google Cloud SQL instance template with the specified parameters.
   *
   * @param name                        the name of the template
   * @param configuration               the source of configuration
   * @param tags                        the map of tags to be applied to resources created from the template
   * @param providerLocalizationContext the parent provider localization context
   */
  public GoogleCloudSQLInstanceTemplate(String name, Configured configuration, Map<String, String> tags,
      LocalizationContext providerLocalizationContext) {
    super(name, configuration, tags, providerLocalizationContext);
  }
}
