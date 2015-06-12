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

package com.cloudera.director.google.compute;

import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationProperty.IMAGE;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationProperty.LOCAL_SSD_INTERFACE_TYPE;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationProperty.NETWORK_NAME;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationProperty.TYPE;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationProperty.ZONE;
import static com.cloudera.director.google.compute.GoogleComputeProviderConfigurationProperty.REGION;
import static com.cloudera.director.spi.v1.compute.ComputeInstanceTemplate.ComputeInstanceTemplateConfigurationPropertyToken.SSH_OPENSSH_PUBLIC_KEY;
import static com.cloudera.director.spi.v1.compute.ComputeInstanceTemplate.ComputeInstanceTemplateConfigurationPropertyToken.SSH_PORT;
import static com.cloudera.director.spi.v1.compute.ComputeInstanceTemplate.ComputeInstanceTemplateConfigurationPropertyToken.SSH_USERNAME;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;

import com.cloudera.director.google.TestUtils;
import com.cloudera.director.google.internal.GoogleCredentials;
import com.cloudera.director.google.shaded.com.typesafe.config.Config;
import com.cloudera.director.spi.v1.compute.ComputeInstance;
import com.cloudera.director.spi.v1.model.Configured;
import com.cloudera.director.spi.v1.model.ConfigurationProperty;
import com.cloudera.director.spi.v1.model.ConfigurationValidator;
import com.cloudera.director.spi.v1.model.InstanceState;
import com.cloudera.director.spi.v1.model.InstanceStatus;
import com.cloudera.director.spi.v1.model.exception.PluginExceptionConditionAccumulator;
import com.cloudera.director.spi.v1.model.exception.PluginExceptionDetails;
import com.cloudera.director.spi.v1.model.exception.UnrecoverableProviderException;
import com.cloudera.director.spi.v1.model.util.DefaultLocalizationContext;
import com.cloudera.director.spi.v1.model.util.SimpleConfiguration;
import com.cloudera.director.spi.v1.provider.ResourceProviderMetadata;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Performs 'live' tests of the full cycle of {@link GoogleComputeProvider}: allocate, getInstanceState, find, delete.
 *
 * These three system properties are required: GCP_PROJECT_ID, SSH_PUBLIC_KEY_PATH, SSH_USER_NAME.
 * These two system properties are optional: JSON_KEY_PATH, HALT_AFTER_ALLOCATION.
 *
 * If JSON_KEY_PATH is not specified, Application Default Credentials will be used.
 *
 * @see <a href="https://developers.google.com/identity/protocols/application-default-credentials"</a>
 */
@RunWith(Parameterized.class)
public class GoogleComputeProviderFullCycleTest {

  private static final Logger LOG = Logger.getLogger(GoogleComputeProviderFullCycleTest.class.getName());

  private static final DefaultLocalizationContext DEFAULT_LOCALIZATION_CONTEXT =
      new DefaultLocalizationContext(Locale.getDefault(), "");

  private static final int POLLING_INTERVAL_SECONDS = 5;

  private static String PROJECT_ID;
  private static String JSON_KEY;
  private static String SSH_PUBLIC_KEY;
  private static String USER_NAME;
  private static boolean HALT_AFTER_ALLOCATION;

  @BeforeClass
  public static void beforeClass() throws IOException {
    PROJECT_ID = TestUtils.readRequiredSystemProperty("GCP_PROJECT_ID");
    JSON_KEY = TestUtils.readFileIfSpecified(System.getProperty("JSON_KEY_PATH", ""));
    SSH_PUBLIC_KEY = TestUtils.readFile(TestUtils.readRequiredSystemProperty("SSH_PUBLIC_KEY_PATH"),
        Charset.defaultCharset());
    USER_NAME = TestUtils.readRequiredSystemProperty("SSH_USER_NAME");
    HALT_AFTER_ALLOCATION = Boolean.parseBoolean(System.getProperty("HALT_AFTER_ALLOCATION", "false"));
  }

  private String localSSDInterfaceType;
  private String image;

  public GoogleComputeProviderFullCycleTest(String localSSDInterfaceType, String image) {
    this.localSSDInterfaceType = localSSDInterfaceType;
    this.image = image;
  }

  @Parameterized.Parameters(name = "{index}: localSSDInterfaceType={0}, image={1}")
  public static Iterable<Object[]> data1() {
    return Arrays.asList(new Object[][]{
        {"SCSI", "centos6"},
        {"SCSI", "rhel6"}
    });
  }

  @Test
  public void testFullCycle() throws InterruptedException, IOException {

    // Retrieve and list out the provider configuration properties for Google compute provider.
    ResourceProviderMetadata computeMetadata = GoogleComputeProvider.METADATA;
    LOG.info("Configurations required for 'compute' resource provider:");
    for (ConfigurationProperty property :
        computeMetadata.getProviderConfigurationProperties()) {
      LOG.info(property.getName(DEFAULT_LOCALIZATION_CONTEXT));
    }

    // Prepare configuration for Google compute provider.
    Map<String, String> computeConfig = new HashMap<String, String>();
    computeConfig.put(REGION.unwrap().getConfigKey(), "us-central1");
    Configured resourceProviderConfiguration = new SimpleConfiguration(computeConfig);

    // Create Google credentials for use by both the validator and the provider.
    Config applicationPropertiesConfig = TestUtils.buildApplicationPropertiesConfig();
    GoogleCredentials credentials = new GoogleCredentials(applicationPropertiesConfig, PROJECT_ID, JSON_KEY);

    // Validate the Google compute provider configuration.
    LOG.info("About to validate the resource provider configuration...");
    ConfigurationValidator resourceProviderValidator = new GoogleComputeProviderConfigurationValidator(credentials);
    PluginExceptionConditionAccumulator accumulator = new PluginExceptionConditionAccumulator();
    resourceProviderValidator.validate("resource provider configuration", resourceProviderConfiguration, accumulator,
        DEFAULT_LOCALIZATION_CONTEXT);
    assertFalse(accumulator.getConditionsByKey().toString(), accumulator.hasError());

    // Create the Google compute provider.
    GoogleComputeProvider compute = new GoogleComputeProvider(resourceProviderConfiguration, credentials,
        applicationPropertiesConfig, TestUtils.buildGoogleConfig(), DEFAULT_LOCALIZATION_CONTEXT);

    // Retrieve and list out the resource template configuration properties for Google compute provider.
    LOG.info("Configurations required for template:");
    for (ConfigurationProperty property :
        computeMetadata.getResourceTemplateConfigurationProperties()) {
      LOG.info(property.getName(DEFAULT_LOCALIZATION_CONTEXT));
    }

    // Prepare configuration for resource template.
    Map<String, String> templateConfig = new HashMap<String, String>();
    templateConfig.put(IMAGE.unwrap().getConfigKey(), image);
    templateConfig.put(TYPE.unwrap().getConfigKey(), "n1-standard-1");
    templateConfig.put(NETWORK_NAME.unwrap().getConfigKey(), "default");
    templateConfig.put(ZONE.unwrap().getConfigKey(), "us-central1-f");
    templateConfig.put(LOCAL_SSD_INTERFACE_TYPE.unwrap().getConfigKey(), localSSDInterfaceType);
    templateConfig.put(SSH_OPENSSH_PUBLIC_KEY.unwrap().getConfigKey(), SSH_PUBLIC_KEY);
    templateConfig.put(SSH_USERNAME.unwrap().getConfigKey(), USER_NAME);
    templateConfig.put(SSH_PORT.unwrap().getConfigKey(), "22");

    Map<String, String> tags = new HashMap<String, String>();
    tags.put("test-tag-1", "some-value-1");
    tags.put("test-tag-2", "some-value-2");

    Configured templateConfiguration = new SimpleConfiguration(templateConfig);

    // Validate the template configuration.
    LOG.info("About to validate the template configuration...");
    ConfigurationValidator templateConfigurationValidator =
        compute.getResourceTemplateConfigurationValidator();
    accumulator = new PluginExceptionConditionAccumulator();
    templateConfigurationValidator.validate("instance resource template", templateConfiguration, accumulator,
        DEFAULT_LOCALIZATION_CONTEXT);
    assertFalse(accumulator.getConditionsByKey().toString(), accumulator.hasError());

    // Create the resource template.
    GoogleComputeInstanceTemplate template =
        compute.createResourceTemplate("template-1", templateConfiguration, tags);
    assertNotNull(template);

    // Use the template to provision one resource.
    LOG.info("About to provision an instance...");
    List<String> instanceIds = Arrays.asList(UUID.randomUUID().toString());

    try {
      compute.allocate(template, instanceIds, 1);
    } catch (UnrecoverableProviderException e) {
      PluginExceptionDetails details = e.getDetails();

      if (details != null) {
        LOG.info("Caught on allocate(): " + details.getConditionsByKey());
      }

      throw e;
    }

    // Run a find by ID.
    LOG.info("About to lookup an instance...");
    Collection<GoogleComputeInstance> instances = compute.find(template, instanceIds);
    assertEquals(1, instances.size());

    for (ComputeInstance foundInstance : instances) {
      LOG.info("Found instance '" + foundInstance.getId() + "' with private ip " +
          foundInstance.getPrivateIpAddress() + ".");
    }

    // Verify the id of the returned instance.
    ComputeInstance instance = instances.iterator().next();
    assertEquals(instanceIds.get(0), instance.getId());

    // Use the template to request creation of the same resource again.
    LOG.info("About to provision the same instance again...");
    compute.allocate(template, instanceIds, 1);
    instances = compute.find(template, instanceIds);
    assertEquals(1, instances.size());

    // Verify the id of the returned instance.
    instance = instances.iterator().next();
    assertEquals(instanceIds.get(0), instance.getId());

    // Query the instance state until the instance status is RUNNING.
    pollInstanceState(compute, template, instanceIds, InstanceStatus.RUNNING);

    // List all display properties.
    LOG.info("Display properties:");

    Map<String, String> displayPropertyMap = instance.getProperties();

    for (Map.Entry<String, String> keyValuePair : displayPropertyMap.entrySet()) {
      LOG.info("  " + keyValuePair.getKey() + " -> " + keyValuePair.getValue());
    }

    if (HALT_AFTER_ALLOCATION) {
      LOG.info("HALT_AFTER_ALLOCATION flag is set.");

      return;
    }

    // Delete the resources.
    LOG.info("About to delete an instance...");
    compute.delete(template, instanceIds);

    // Query the instance state again until the instance status is UNKNOWN.
    pollInstanceState(compute, template, instanceIds, InstanceStatus.UNKNOWN);

    // Verify that the instance has been deleted.
    instances = compute.find(template, instanceIds);
    assertEquals(0, instances.size());
  }

  private static void pollInstanceState(
      GoogleComputeProvider compute,
      GoogleComputeInstanceTemplate template,
      List<String> instanceIds,
      InstanceStatus desiredStatus) throws InterruptedException {

    // Query the instance state until the instance status matches desiredStatus.
    LOG.info("About to query instance state until " + desiredStatus + "...");

    Map<String, InstanceState> idToInstanceStateMap = compute.getInstanceState(template, instanceIds);

    assertEquals(1, idToInstanceStateMap.size());

    for (Map.Entry<String, InstanceState> entry : idToInstanceStateMap.entrySet()) {
      LOG.info(entry.getKey() + " -> " + entry.getValue().getInstanceStateDescription(DEFAULT_LOCALIZATION_CONTEXT));
    }

    while (idToInstanceStateMap.size() == 1
        && idToInstanceStateMap.values().toArray(new InstanceState[1])[0].getInstanceStatus() != desiredStatus) {
      Thread.sleep(POLLING_INTERVAL_SECONDS * 1000);

      LOG.info("Polling...");

      idToInstanceStateMap = compute.getInstanceState(template, instanceIds);

      for (Map.Entry<String, InstanceState> entry : idToInstanceStateMap.entrySet()) {
        LOG.info(entry.getKey() + " -> " + entry.getValue().getInstanceStateDescription(DEFAULT_LOCALIZATION_CONTEXT));
      }
    }
  }
}
