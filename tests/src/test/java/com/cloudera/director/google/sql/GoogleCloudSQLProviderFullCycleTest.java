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

import static com.cloudera.director.google.sql.GoogleCloudSQLInstanceTemplateConfigurationProperty.ENGINE;
import static com.cloudera.director.google.sql.GoogleCloudSQLInstanceTemplateConfigurationProperty.MASTER_USER_PASSWORD;
import static com.cloudera.director.google.sql.GoogleCloudSQLInstanceTemplateConfigurationProperty.MASTER_USERNAME;
import static com.cloudera.director.google.sql.GoogleCloudSQLInstanceTemplateConfigurationProperty.TIER;
import static com.cloudera.director.google.sql.GoogleCloudSQLProviderConfigurationProperty.REGION_SQL;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;

import com.cloudera.director.google.TestFixture;
import com.cloudera.director.google.TestUtils;
import com.cloudera.director.google.internal.GoogleCredentials;
import com.cloudera.director.google.shaded.com.typesafe.config.Config;
import com.cloudera.director.spi.v1.database.DatabaseServerInstance;
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

/**
 * Performs 'live' tests of the full cycle of {@link GoogleCloudSQLProvider}: allocate, getInstanceState, find, delete.
 *
 * This property is required: GCP_PROJECT_ID.
 * These two system properties are optional: JSON_KEY_PATH, HALT_AFTER_ALLOCATION.
 *
 * If JSON_KEY_PATH is not specified, Application Default Credentials will be used.
 *
 * @see <a href="https://developers.google.com/identity/protocols/application-default-credentials"</a>
 */

public class GoogleCloudSQLProviderFullCycleTest {

  private static final Logger LOG = Logger.getLogger(GoogleCloudSQLProviderFullCycleTest.class.getName());

  private static final DefaultLocalizationContext DEFAULT_LOCALIZATION_CONTEXT =
      new DefaultLocalizationContext(Locale.getDefault(), "");

  private static final int POLLING_INTERVAL_SECONDS = 5;

  private static final String MY_REGION_NAME = "us-central";
  private static final String MY_TIER = "D4";
  private static final String MY_USER_PASSWORD = "admin";
  private static final String MY_USERNAME = "admin";
  private static final String MY_DATABASE_TYPE = "MYSQL";

  private static TestFixture testFixture;

  @BeforeClass
  public static void beforeClass() throws IOException {
      testFixture = TestFixture.newTestFixture(false);
  }

  public GoogleCloudSQLProviderFullCycleTest() {}

  @Test
  public void testFullCycle() throws InterruptedException, IOException {

    // Retrieve and list out the provider configuration properties for Google Cloud SQL provider.
    ResourceProviderMetadata sqlMetadata = GoogleCloudSQLProvider.METADATA;
    LOG.info("Configurations required for 'sql' resource provider:");
    for (ConfigurationProperty property :
        sqlMetadata.getProviderConfigurationProperties()) {
      LOG.info(property.getName(DEFAULT_LOCALIZATION_CONTEXT));
    }

    // Prepare configuration for Google Cloud SQL provider.
    Map<String, String> sqlAdminConfig = new HashMap<String, String>();
    sqlAdminConfig.put(REGION_SQL.unwrap().getConfigKey(), MY_REGION_NAME);
    Configured resourceProviderConfiguration = new SimpleConfiguration(sqlAdminConfig);

    // Create Google credentials for use by both the validator and the provider.
    Config applicationPropertiesConfig = TestUtils.buildApplicationPropertiesConfig();
    GoogleCredentials credentials = new GoogleCredentials(applicationPropertiesConfig, testFixture.getProjectId(),
        testFixture.getJsonKey());

    // Validate the Google Cloud SQL provider configuration.
    LOG.info("About to validate the resource provider configuration...");
    ConfigurationValidator resourceProviderValidator = new GoogleCloudSQLProviderConfigurationValidator(credentials);
    PluginExceptionConditionAccumulator accumulator = new PluginExceptionConditionAccumulator();
    resourceProviderValidator.validate("resource provider configuration", resourceProviderConfiguration, accumulator,
        DEFAULT_LOCALIZATION_CONTEXT);
    assertFalse(accumulator.getConditionsByKey().toString(), accumulator.hasError());

    // Create the Google Cloud SQL provider.
    GoogleCloudSQLProvider sqlAdmin = new GoogleCloudSQLProvider(resourceProviderConfiguration, credentials,
        applicationPropertiesConfig, TestUtils.buildGoogleConfig(), DEFAULT_LOCALIZATION_CONTEXT);

    // Retrieve and list out the resource template configuration properties for Google Cloud SQL provider.
    LOG.info("Configurations required for template:");
    for (ConfigurationProperty property :
        sqlMetadata.getResourceTemplateConfigurationProperties()) {
      LOG.info(property.getName(DEFAULT_LOCALIZATION_CONTEXT));
    }

    // Prepare configuration for resource template.
    Map<String, String> templateConfig = new HashMap<String, String>();
    templateConfig.put(TIER.unwrap().getConfigKey(), MY_TIER);
    templateConfig.put(MASTER_USERNAME.unwrap().getConfigKey(), MY_USERNAME);
    templateConfig.put(MASTER_USER_PASSWORD.unwrap().getConfigKey(), MY_USER_PASSWORD);
    templateConfig.put(ENGINE.unwrap().getConfigKey(), MY_DATABASE_TYPE);

    Map<String, String> tags = new HashMap<String, String>();
    tags.put("test-tag-1", "some-value-1");
    tags.put("test-tag-2", "some-value-2");

    Configured templateConfiguration = new SimpleConfiguration(templateConfig);

    // Validate the template configuration.
    LOG.info("About to validate the template configuration...");
    ConfigurationValidator templateConfigurationValidator =
    sqlAdmin.getResourceTemplateConfigurationValidator();
    accumulator = new PluginExceptionConditionAccumulator();
    templateConfigurationValidator.validate("instance resource template", templateConfiguration, accumulator,
        DEFAULT_LOCALIZATION_CONTEXT);
    assertFalse(accumulator.getConditionsByKey().toString(), accumulator.hasError());

    // Create the resource template.
    GoogleCloudSQLInstanceTemplate template =
        sqlAdmin.createResourceTemplate("template-1", templateConfiguration, tags);
    assertNotNull(template);


    List<String> instanceIds = Arrays.asList(UUID.randomUUID().toString());

    // Verify that instances are not created.
    Collection<GoogleCloudSQLInstance> instances = sqlAdmin.find(template, instanceIds);
    assertEquals(0, instances.size());

    Map<String, InstanceState> instanceStates = sqlAdmin.getInstanceState(template, instanceIds);
    assertEquals(instanceIds.size(), instanceStates.size());

    // Use the template to provision one resource.
    LOG.info("About to provision an instance...");
    try {
        sqlAdmin.allocate(template, instanceIds, 1);
    } catch (UnrecoverableProviderException e) {
      PluginExceptionDetails details = e.getDetails();

      if (details != null) {
        LOG.info("Caught on allocate(): " + details.getConditionsByKey());
      }

      throw e;
    }

    // Run a find by ID.
    LOG.info("About to lookup an instance...");
    instances = sqlAdmin.find(template, instanceIds);
    assertEquals(1, instances.size());

    // Verify that no exception is thrown.
    instanceStates = sqlAdmin.getInstanceState(template, instanceIds);
    assertEquals(instanceIds.size(), instanceStates.size());

    for (DatabaseServerInstance foundInstance : instances) {
      LOG.info("Found instance '" + foundInstance.getId() + "' with private ip " +
        foundInstance.getPrivateIpAddress() + ".");
    }

    // Verify the id of the returned instance.
    DatabaseServerInstance instance = instances.iterator().next();
    assertEquals(instanceIds.get(0), instance.getId());

    // Use the template to request creation of the same resource again.
    LOG.info("About to provision the same instance again...");
    sqlAdmin.allocate(template, instanceIds, 1);
    instances = sqlAdmin.find(template, instanceIds);
    assertEquals(1, instances.size());

    // Verify that no exception is thrown.
    instanceStates = sqlAdmin.getInstanceState(template, instanceIds);
    assertEquals(instanceIds.size(), instanceStates.size());

    // Verify the id of the returned instance.
    instance = instances.iterator().next();
    assertEquals(instanceIds.get(0), instance.getId());

    // Query the instance state until the instance status is RUNNING.
    pollInstanceState(sqlAdmin, template, instanceIds, InstanceStatus.RUNNING);

    // List all display properties.
    LOG.info("Display properties:");

    Map<String, String> displayPropertyMap = instance.getProperties();

    for (Map.Entry<String, String> keyValuePair : displayPropertyMap.entrySet()) {
      LOG.info("  " + keyValuePair.getKey() + " -> " + keyValuePair.getValue());
    }

    if (testFixture.getHaltAfterAllocation()) {
      LOG.info("HALT_AFTER_ALLOCATION flag is set.");

      return;
    }

    // Delete the resources.
    LOG.info("About to delete an instance...");
    sqlAdmin.delete(template, instanceIds);

    // Query the instance state again until the instance status is UNKNOWN.
    pollInstanceState(sqlAdmin, template, instanceIds, InstanceStatus.UNKNOWN);

    // Verify that the instance has been deleted.
    System.out.println("EK:VERIFY THAT THE INSTANCE HAS BEEN DELETED.");
    instances = sqlAdmin.find(template, instanceIds);
    assertEquals(0, instances.size());

    // Verify that no exception is thrown.
    instanceStates = sqlAdmin.getInstanceState(template, instanceIds);
    assertEquals(instanceIds.size(), instanceStates.size());

    LOG.info("About to delete the same instance again...");
    try {
      sqlAdmin.delete(template, instanceIds);
    } catch (UnrecoverableProviderException e) {
      PluginExceptionDetails details = e.getDetails();

      if (details != null) {
        LOG.info("Caught on delete():" + details.getConditionsByKey());
      }

      throw e;
    }
  }

  private static void pollInstanceState(
      GoogleCloudSQLProvider sqlAdmin,
      GoogleCloudSQLInstanceTemplate template,
      List<String> instanceIds,
      InstanceStatus desiredStatus) throws InterruptedException {

    // Query the instance state until the instance status matches desiredStatus.
    LOG.info("About to query instance state until " + desiredStatus + "...");

    Map<String, InstanceState> idToInstanceStateMap = sqlAdmin.getInstanceState(template, instanceIds);

    assertEquals(instanceIds.size(), idToInstanceStateMap.size());

    for (Map.Entry<String, InstanceState> entry : idToInstanceStateMap.entrySet()) {
      LOG.info(entry.getKey() + " -> " + entry.getValue().getInstanceStateDescription(DEFAULT_LOCALIZATION_CONTEXT));
    }

    while (idToInstanceStateMap.size() == 1
        && idToInstanceStateMap.values().toArray(new InstanceState[1])[0].getInstanceStatus() != desiredStatus) {
      Thread.sleep(POLLING_INTERVAL_SECONDS * 1000);

      LOG.info("Polling...");

      idToInstanceStateMap = sqlAdmin.getInstanceState(template, instanceIds);

      for (Map.Entry<String, InstanceState> entry : idToInstanceStateMap.entrySet()) {
        LOG.info(entry.getKey() + " -> " + entry.getValue().getInstanceStateDescription(DEFAULT_LOCALIZATION_CONTEXT));
      }
    }
  }
}

