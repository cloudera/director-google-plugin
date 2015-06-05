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

import static com.cloudera.director.google.GoogleCredentialsProviderConfigurationProperty.JSON_KEY;
import static com.cloudera.director.google.GoogleCredentialsProviderConfigurationProperty.PROJECT_ID;

import com.cloudera.director.google.internal.GoogleCredentials;
import com.cloudera.director.spi.v1.model.ConfigurationProperty;
import com.cloudera.director.spi.v1.model.Configured;
import com.cloudera.director.spi.v1.model.LocalizationContext;
import com.cloudera.director.spi.v1.provider.CredentialsProvider;
import com.cloudera.director.spi.v1.provider.CredentialsProviderMetadata;
import com.cloudera.director.spi.v1.provider.util.SimpleCredentialsProviderMetadata;
import com.cloudera.director.spi.v1.util.ConfigurationPropertiesUtil;
import com.typesafe.config.Config;

import java.util.List;

/**
 * Convert a configuration to a cloud provider specific credentials object.
 */
public class GoogleCredentialsProvider implements CredentialsProvider<GoogleCredentials> {

  private static final List<ConfigurationProperty> CONFIGURATION_PROPERTIES =
      ConfigurationPropertiesUtil.asConfigurationPropertyList(
          GoogleCredentialsProviderConfigurationProperty.values());

  public static CredentialsProviderMetadata METADATA =
      new SimpleCredentialsProviderMetadata(CONFIGURATION_PROPERTIES);

  private Config applicationProperties;

  public GoogleCredentialsProvider(Config applicationProperties) {
    this.applicationProperties = applicationProperties;
  }

  @Override
  public CredentialsProviderMetadata getMetadata() {
    return METADATA;
  }

  @Override
  public GoogleCredentials createCredentials(Configured configuration,
      LocalizationContext localizationContext) {
    return new GoogleCredentials(
        applicationProperties,
        configuration.getConfigurationValue(PROJECT_ID, localizationContext),
        configuration.getConfigurationValue(JSON_KEY, localizationContext)
    );
  }
}
