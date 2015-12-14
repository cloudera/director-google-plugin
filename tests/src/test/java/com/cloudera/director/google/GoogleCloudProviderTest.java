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
import static com.cloudera.director.google.GoogleLauncher.DEFAULT_PLUGIN_LOCALIZATION_CONTEXT;
import static junit.framework.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.cloudera.director.google.compute.GoogleComputeProvider;
import com.cloudera.director.google.internal.GoogleCredentials;
import com.cloudera.director.google.sql.GoogleCloudSQLProvider;
import com.cloudera.director.google.util.Names;
import com.cloudera.director.google.shaded.com.typesafe.config.Config;
import com.cloudera.director.spi.v1.model.ConfigurationProperty;
import com.cloudera.director.spi.v1.model.LocalizationContext;
import com.cloudera.director.spi.v1.model.util.SimpleConfiguration;
import com.cloudera.director.spi.v1.provider.CloudProviderMetadata;
import com.cloudera.director.spi.v1.provider.CredentialsProviderMetadata;
import com.cloudera.director.spi.v1.provider.ResourceProvider;
import com.cloudera.director.spi.v1.provider.ResourceProviderMetadata;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Performs 'live' test of {@link GoogleCloudProvider}.
 *
 * This system property is required: GCP_PROJECT_ID.
 * This system property is optional: JSON_KEY_PATH.
 *
 * If JSON_KEY_PATH is not specified, Application Default Credentials will be used.
 *
 * @see <a href="https://developers.google.com/identity/protocols/application-default-credentials"</a>
 */
public class GoogleCloudProviderTest {

  private static TestFixture testFixture;

  @BeforeClass
  public static void beforeClass() throws IOException {
    Assume.assumeFalse(System.getProperty("GCP_PROJECT_ID", "").isEmpty());

    testFixture = TestFixture.newTestFixture(false);
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
    assertTrue(credentialsConfigurationProperties.contains(PROJECT_ID.unwrap()));
    assertTrue(credentialsConfigurationProperties.contains(JSON_KEY.unwrap()));

    Config applicationPropertiesConfig = TestUtils.buildApplicationPropertiesConfig();
    GoogleCredentialsProvider googleCredentialsProvider = new GoogleCredentialsProvider(applicationPropertiesConfig);
    assertNotNull(googleCredentialsProvider);

    // In order to create a cloud provider we need to configure credentials
    // (we expect them to be eagerly validated on cloud provider creation).
    Map<String, String> environmentConfig = new HashMap<String, String>();
    environmentConfig.put(PROJECT_ID.unwrap().getConfigKey(), testFixture.getProjectId());
    environmentConfig.put(JSON_KEY.unwrap().getConfigKey(), testFixture.getJsonKey());

    LocalizationContext cloudLocalizationContext =
        GoogleCloudProvider.METADATA.getLocalizationContext(DEFAULT_PLUGIN_LOCALIZATION_CONTEXT);

    GoogleCredentials googleCredentials = googleCredentialsProvider.createCredentials(
        new SimpleConfiguration(environmentConfig), cloudLocalizationContext);
    assertNotNull(googleCredentials);

    // Verify the user agent header string.
    assertEquals(
        Names.buildApplicationNameVersionTag(applicationPropertiesConfig),
        googleCredentials.getCompute().getApplicationName());

    Config googleConfig = TestUtils.buildGoogleConfig();

    GoogleCloudProvider googleProvider = new GoogleCloudProvider(googleCredentials, applicationPropertiesConfig,
        googleConfig, cloudLocalizationContext);
    assertNotNull(googleProvider);
    assertSame(googleProviderMetadata, googleProvider.getProviderMetadata());

    ResourceProviderMetadata computeResourceProviderMetadata = null;
    ResourceProviderMetadata sqlResourceProviderMetadata = null;
    List<ResourceProviderMetadata> resourceProviderMetadatas = googleProviderMetadata.getResourceProviderMetadata();

    for (ResourceProviderMetadata resourceProviderMetadata : resourceProviderMetadatas) {
      String resourceProviderId = resourceProviderMetadata.getId();

      if (GoogleComputeProvider.ID.equals(resourceProviderId)) {
        computeResourceProviderMetadata = resourceProviderMetadata;
      } else if (GoogleCloudSQLProvider.ID.equals(resourceProviderId)) {
        sqlResourceProviderMetadata = resourceProviderMetadata;
      } else {
        throw new IllegalArgumentException("Unexpected resource provider: " + resourceProviderId);
      }
    }
    assertNotNull(computeResourceProviderMetadata);
    if (GoogleCloudProvider.featureFlag) {
      assertNotNull(sqlResourceProviderMetadata);
    }

    ResourceProvider<?, ?> computeResourceProvider =
        googleProvider.createResourceProvider(GoogleComputeProvider.ID,
            new SimpleConfiguration(Collections.<String, String>emptyMap()));
    Assert.assertEquals(GoogleComputeProvider.class, computeResourceProvider.getClass());

    ResourceProvider<?, ?> sqlResourceProvider =
        googleProvider.createResourceProvider(GoogleCloudSQLProvider.ID, new SimpleConfiguration(Collections.<String, String>emptyMap()));
    Assert.assertEquals(GoogleCloudSQLProvider.class, sqlResourceProvider.getClass());
  }
}
