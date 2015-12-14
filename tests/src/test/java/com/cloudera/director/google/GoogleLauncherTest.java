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
import static junit.framework.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import com.cloudera.director.spi.v1.model.ConfigurationProperty;
import com.cloudera.director.spi.v1.model.util.SimpleConfiguration;
import com.cloudera.director.spi.v1.provider.CloudProvider;
import com.cloudera.director.spi.v1.provider.CloudProviderMetadata;
import com.cloudera.director.spi.v1.provider.Launcher;

import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Performs 'live' test of {@link GoogleLauncher}.
 *
 * This system property is required: GCP_PROJECT_ID.
 * This system property is optional: JSON_KEY_PATH.
 *
 * If JSON_KEY_PATH is not specified, Application Default Credentials will be used.
 *
 * @see <a href="https://developers.google.com/identity/protocols/application-default-credentials"</a>
 */
public class GoogleLauncherTest {

  private static TestFixture testFixture;

  @BeforeClass
  public static void beforeClass() throws IOException {
    Assume.assumeFalse(System.getProperty("GCP_PROJECT_ID", "").isEmpty());

    testFixture = TestFixture.newTestFixture(false);
  }

  @Rule
  public TemporaryFolder TEMPORARY_FOLDER = new TemporaryFolder();

  @Test
  public void testLauncher() throws IOException {

    Launcher launcher = new GoogleLauncher();
    launcher.initialize(TEMPORARY_FOLDER.getRoot(), null);

    assertEquals(1, launcher.getCloudProviderMetadata().size());
    CloudProviderMetadata metadata = launcher.getCloudProviderMetadata().get(0);

    assertEquals(GoogleCloudProvider.ID, metadata.getId());

    List<ConfigurationProperty> providerConfigurationProperties = metadata.getProviderConfigurationProperties();
    assertEquals(0, providerConfigurationProperties.size());

    List<ConfigurationProperty> credentialsConfigurationProperties =
        metadata.getCredentialsProviderMetadata().getCredentialsConfigurationProperties();
    assertEquals(2, credentialsConfigurationProperties.size());
    assertTrue(credentialsConfigurationProperties.contains(PROJECT_ID.unwrap()));
    assertTrue(credentialsConfigurationProperties.contains(JSON_KEY.unwrap()));

    // In order to create a cloud provider we need to configure credentials
    // (we expect them to be eagerly validated on cloud provider creation).
    Map<String, String> environmentConfig = new HashMap<String, String>();
    environmentConfig.put(PROJECT_ID.unwrap().getConfigKey(), testFixture.getProjectId());
    environmentConfig.put(JSON_KEY.unwrap().getConfigKey(), testFixture.getJsonKey());

    CloudProvider cloudProvider = launcher.createCloudProvider(
        GoogleCloudProvider.ID,
        new SimpleConfiguration(environmentConfig),
        Locale.getDefault());
    assertEquals(GoogleCloudProvider.class, cloudProvider.getClass());

    CloudProvider cloudProvider2 = launcher.createCloudProvider(
        GoogleCloudProvider.ID,
        new SimpleConfiguration(environmentConfig),
        Locale.getDefault());
    assertNotSame(cloudProvider, cloudProvider2);
  }

  @Test
  public void testLauncherConfig() throws IOException {
    GoogleLauncher launcher = new GoogleLauncher();
    File configDir = TEMPORARY_FOLDER.getRoot();
    File configFile = new File(configDir, Configurations.GOOGLE_CONFIG_FILENAME);
    PrintWriter printWriter = new PrintWriter(new BufferedWriter(new OutputStreamWriter(
        new FileOutputStream(configFile), "UTF-8")));

    printWriter.println("google {");
    printWriter.println("  compute {");
    printWriter.println("    imageAliases {");
    printWriter.println("      rhel6 = \"https://www.googleapis.com/compute/v1/projects/rhel-cloud/global/images/rhel-6-v20150430\",");
    printWriter.println("      ubuntu = \"https://www.googleapis.com/compute/v1/projects/ubuntu-os-cloud/global/images/ubuntu-1404-trusty-v20150128\"");
    printWriter.println("    }");
    printWriter.println("  }");
    printWriter.println("}");
    printWriter.close();

    launcher.initialize(configDir, null);

    // Verify that base config is reflected.
    assertEquals("https://www.googleapis.com/compute/v1/projects/centos-cloud/global/images/centos-6-v20160119",
        launcher.googleConfig.getString(Configurations.IMAGE_ALIASES_SECTION + "centos6"));

    // Verify that overridden config is reflected.
    assertEquals("https://www.googleapis.com/compute/v1/projects/rhel-cloud/global/images/rhel-6-v20150430",
        launcher.googleConfig.getString(Configurations.IMAGE_ALIASES_SECTION + "rhel6"));

    // Verify that new config is reflected.
    assertEquals("https://www.googleapis.com/compute/v1/projects/ubuntu-os-cloud/global/images/ubuntu-1404-trusty-v20150128",
        launcher.googleConfig.getString(Configurations.IMAGE_ALIASES_SECTION + "ubuntu"));
  }
}
