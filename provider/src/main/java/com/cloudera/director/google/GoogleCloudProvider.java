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

import com.cloudera.director.google.compute.GoogleComputeProvider;
import com.cloudera.director.google.compute.GoogleComputeProviderConfigurationValidator;
import com.cloudera.director.google.internal.GoogleCredentials;
import com.cloudera.director.spi.v1.model.ConfigurationProperty;
import com.cloudera.director.spi.v1.model.ConfigurationValidator;
import com.cloudera.director.spi.v1.model.Configured;
import com.cloudera.director.spi.v1.model.LocalizationContext;
import com.cloudera.director.spi.v1.model.util.CompositeConfigurationValidator;
import com.cloudera.director.spi.v1.provider.CloudProviderMetadata;
import com.cloudera.director.spi.v1.provider.ResourceProvider;
import com.cloudera.director.spi.v1.provider.ResourceProviderMetadata;
import com.cloudera.director.spi.v1.provider.util.AbstractCloudProvider;
import com.cloudera.director.spi.v1.provider.util.SimpleCloudProviderMetadataBuilder;
import com.typesafe.config.Config;

import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;

public class GoogleCloudProvider extends AbstractCloudProvider {

  public static final String ID = "google";

  private static final List<ResourceProviderMetadata> RESOURCE_PROVIDER_METADATA =
      Collections.singletonList(GoogleComputeProvider.METADATA);

  private GoogleCredentials credentials;
  private Config googleConfig;

  protected static final CloudProviderMetadata METADATA = new SimpleCloudProviderMetadataBuilder()
      .id(ID)
      .name("Google Cloud Platform")
      .description("A provider implementation that provisions virtual resources on Google Cloud Platform.")
      .configurationProperties(Collections.<ConfigurationProperty>emptyList())
      .credentialsProviderMetadata(GoogleCredentialsProvider.METADATA)
      .resourceProviderMetadata(RESOURCE_PROVIDER_METADATA)
      .build();

  public GoogleCloudProvider(GoogleCredentials credentials, Config googleConfig,
      LocalizationContext rootLocalizationContext) {
    super(METADATA, rootLocalizationContext);
    this.credentials = credentials;
    this.googleConfig = googleConfig;
  }

  @Override
  protected ConfigurationValidator getResourceProviderConfigurationValidator(
      ResourceProviderMetadata resourceProviderMetadata) {
    ConfigurationValidator providerSpecificValidator;
    if (resourceProviderMetadata.getId().equals(GoogleComputeProvider.METADATA.getId())) {
      providerSpecificValidator = new GoogleComputeProviderConfigurationValidator(credentials);
    } else {
      throw new NoSuchElementException("Invalid provider id: " + resourceProviderMetadata.getId());
    }
    return new CompositeConfigurationValidator(METADATA.getProviderConfigurationValidator(),
        providerSpecificValidator);
  }

  @Override
  public ResourceProvider createResourceProvider(String resourceProviderId, Configured configuration) {

    if (GoogleComputeProvider.METADATA.getId().equals(resourceProviderId)) {
      return new GoogleComputeProvider(configuration, credentials, googleConfig,
          getLocalizationContext());
    }

    throw new NoSuchElementException("Invalid provider id: " + resourceProviderId);
  }
}
