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

import static com.cloudera.director.google.GoogleCredentialsProviderConfigurationProperty.JSONKEY;
import static com.cloudera.director.google.GoogleCredentialsProviderConfigurationProperty.PROJECTID;
import static com.cloudera.director.google.GoogleLauncher.DEFAULT_PLUGIN_LOCALIZATION_CONTEXT;
import static junit.framework.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.cloudera.director.google.compute.GoogleComputeProvider;
import com.cloudera.director.google.internal.GoogleCredentials;
import com.cloudera.director.google.shaded.com.typesafe.config.Config;
import com.cloudera.director.google.shaded.com.typesafe.config.ConfigFactory;
import com.cloudera.director.spi.v1.model.ConfigurationProperty;
import com.cloudera.director.spi.v1.model.LocalizationContext;
import com.cloudera.director.spi.v1.model.util.SimpleConfiguration;
import com.cloudera.director.spi.v1.provider.*;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tests {@link GoogleCloudProvider}.
 */
public class GoogleCloudProviderTest {

  private static String PROJECT_ID;
  private static String JSON_KEY;

  @BeforeClass
  public static void beforeClass() throws IOException {
    PROJECT_ID = TestUtils.readRequiredSystemProperty("GCP_PROJECT_ID");
    JSON_KEY = TestUtils.readFile(TestUtils.readRequiredSystemProperty("JSON_KEY_PATH"), Charset.defaultCharset());
  }

  @Rule
  public TemporaryFolder TEMPORARY_FOLDER = new TemporaryFolder();

  @Test
  public void testProvider() throws IOException {

    CloudProviderMetadata googleProviderMetadata = GoogleCloudProvider.METADATA;

    CredentialsProviderMetadata credentialsProviderMetadata =
        googleProviderMetadata.getCredentialsProviderMetadata();
    List<ConfigurationProperty> credentialsConfigurationProperties =
        credentialsProviderMetadata.getCredentialsConfigurationProperties();
    assertEquals(2, credentialsConfigurationProperties.size());
    assertTrue(credentialsConfigurationProperties.contains(PROJECTID.unwrap()));
    assertTrue(credentialsConfigurationProperties.contains(JSONKEY.unwrap()));

    Config applicationPropertiesConfig = TestUtils.buildApplicationPropertiesConfig();
    GoogleCredentialsProvider googleCredentialsProvider = new GoogleCredentialsProvider(applicationPropertiesConfig);
    assertNotNull(googleCredentialsProvider);

    // In order to create a cloud provider we need to configure credentials
    // (we expect them to be eagerly validated on cloud provider creation).
    Map<String, String> environmentConfig = new HashMap<String, String>();
    environmentConfig.put(PROJECTID.unwrap().getConfigKey(), PROJECT_ID);
    environmentConfig.put(JSONKEY.unwrap().getConfigKey(), JSON_KEY);

    LocalizationContext cloudLocalizationContext =
            GoogleCloudProvider.METADATA.getLocalizationContext(DEFAULT_PLUGIN_LOCALIZATION_CONTEXT);

    GoogleCredentials googleCredentials = googleCredentialsProvider.createCredentials(
        new SimpleConfiguration(environmentConfig), cloudLocalizationContext);
    assertNotNull(googleCredentials);

    // Verify the user agent header string.
    assertEquals("Cloudera-Director-Google-Plugin/1.0.0-SNAPSHOT",
            googleCredentials.getCompute().getApplicationName());

    Config googleConfig = TestUtils.buildGoogleConfig();

    GoogleCloudProvider googleProvider = new GoogleCloudProvider(googleCredentials, googleConfig,
        cloudLocalizationContext);
    assertNotNull(googleProvider);
    assertSame(googleProviderMetadata, googleProvider.getProviderMetadata());

    ResourceProviderMetadata computeResourceProviderMetadata = null;
    List<ResourceProviderMetadata> resourceProviderMetadatas = googleProviderMetadata.getResourceProviderMetadata();

    for (ResourceProviderMetadata resourceProviderMetadata : resourceProviderMetadatas) {
      String resourceProviderId = resourceProviderMetadata.getId();

      if (GoogleComputeProvider.ID.equals(resourceProviderId)) {
        computeResourceProviderMetadata = resourceProviderMetadata;
      } else {
        throw new IllegalArgumentException("Unexpected resource provider: " + resourceProviderId);
      }
    }
    assertNotNull(computeResourceProviderMetadata);

    ResourceProvider<?, ?> computeResourceProvider =
        googleProvider.createResourceProvider(GoogleComputeProvider.ID,
            new SimpleConfiguration(Collections.<String, String>emptyMap()));
    Assert.assertEquals(GoogleComputeProvider.class, computeResourceProvider.getClass());
  }
}
