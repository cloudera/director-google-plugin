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

import static com.cloudera.director.google.sql.GoogleCloudSQLProviderConfigurationProperty.REGION_SQL;
import static com.cloudera.director.google.sql.GoogleCloudSQLInstanceTemplateConfigurationProperty.ENGINE;
import static com.cloudera.director.google.sql.GoogleCloudSQLInstanceTemplateConfigurationProperty.MASTER_USER_PASSWORD;
import static com.cloudera.director.google.sql.GoogleCloudSQLInstanceTemplateConfigurationProperty.MASTER_USERNAME;
import static com.cloudera.director.google.sql.GoogleCloudSQLInstanceTemplateConfigurationProperty.TIER;
import static com.cloudera.director.spi.v1.model.InstanceTemplate.InstanceTemplateConfigurationPropertyToken.INSTANCE_NAME_PREFIX;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.fail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudera.director.google.TestUtils;
import com.cloudera.director.google.internal.GoogleCredentials;
import com.cloudera.director.google.shaded.com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.cloudera.director.google.shaded.com.google.api.client.googleapis.testing.json.GoogleJsonResponseExceptionFactoryTesting;
import com.cloudera.director.google.shaded.com.google.api.client.testing.json.MockJsonFactory;
import com.cloudera.director.google.shaded.com.google.api.services.sqladmin.SQLAdmin;
import com.cloudera.director.google.shaded.com.google.api.services.sqladmin.model.DatabaseInstance;
import com.cloudera.director.google.shaded.com.google.api.services.sqladmin.model.Operation;
import com.cloudera.director.google.shaded.com.google.api.services.sqladmin.model.OperationError;
import com.cloudera.director.google.shaded.com.google.api.services.sqladmin.model.OperationErrors;
import com.cloudera.director.spi.v1.model.Configured;
import com.cloudera.director.spi.v1.model.InstanceState;
import com.cloudera.director.spi.v1.model.InstanceStatus;
import com.cloudera.director.spi.v1.model.exception.PluginExceptionCondition;
import com.cloudera.director.spi.v1.model.exception.PluginExceptionDetails;
import com.cloudera.director.spi.v1.model.exception.UnrecoverableProviderException;
import com.cloudera.director.spi.v1.model.util.DefaultLocalizationContext;
import com.cloudera.director.spi.v1.model.util.SimpleConfiguration;
import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.mockito.stubbing.OngoingStubbing;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedSet;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Tests {@link GoogleCloudSQLProvider}.
 */
public class GoogleCloudSQLProviderTest {

  private static final Logger LOG = Logger.getLogger(GoogleCloudSQLProviderTest.class.getName());

  private static final DefaultLocalizationContext DEFAULT_LOCALIZATION_CONTEXT =
      new DefaultLocalizationContext(Locale.getDefault(), "");


  private static final String PROJECT_ID = "some-project";
  private static final String REGION_NAME_1 = "us-central";
  private static final String TIER_NAME = "D2";
  private static final String USER_PASSWORD = "admin";
  private static final String USERNAME = "admin";
  private static final String DATABASE_TYPE = "MYSQL";
  private static final String INVALID_INSTANCE_NAME_PREFIX = "-starts-with-dash";

  private GoogleCloudSQLProvider sqlProvider;
  private GoogleCredentials credentials;
  private SQLAdmin sqlAdmin;
  private GoogleCloudSQLInstanceTemplateConfigurationValidator validator;

  @Before
  public void setUp() throws IOException {
    credentials = mock(GoogleCredentials.class);
    sqlAdmin = mock(SQLAdmin.class);

    when(credentials.getSQLAdmin()).thenReturn(sqlAdmin);
    when(credentials.getProjectId()).thenReturn(PROJECT_ID);

    SQLAdmin.Tiers.List sqlTierList = mockSQLAdminToTiersList();

    // We don't need to actually return an image, we just need to not throw a 404.
    when(sqlTierList.execute()).thenReturn(null);

    // Prepare configuration for Google Cloud SQL provider.
    Map<String, String> sqlAdminConfig = new HashMap<String, String>();
    sqlAdminConfig.put(REGION_SQL.unwrap().getConfigKey(), REGION_NAME_1);
    Configured resourceProviderConfiguration = new SimpleConfiguration(sqlAdminConfig);

    // Create the Google Cloud SQL provider.
    sqlProvider = new GoogleCloudSQLProvider(resourceProviderConfiguration, credentials,
        TestUtils.buildApplicationPropertiesConfig(), TestUtils.buildGoogleConfig(), DEFAULT_LOCALIZATION_CONTEXT);
  }

  private SQLAdmin.Tiers.List mockSQLAdminToTiersList() throws IOException {
    SQLAdmin.Tiers sqlAdminTiers = mock(SQLAdmin.Tiers.class);
    SQLAdmin.Tiers.List sqlAdminTierList = mock(SQLAdmin.Tiers.List.class);

    when(sqlAdmin.tiers()).thenReturn(sqlAdminTiers);
    when(sqlAdminTiers.list(PROJECT_ID)).thenReturn(sqlAdminTierList);

    return sqlAdminTierList;
  }

  private SQLAdmin.Instances mockSQLAdminToInstances() {
    SQLAdmin.Instances sqlAdminInstances = mock(SQLAdmin.Instances.class);

    when(sqlAdmin.instances()).thenReturn(sqlAdminInstances);

    return sqlAdminInstances;
  }

  private SQLAdmin.Instances.Insert mockSQLAdminInstancesInsert(SQLAdmin.Instances sqlAdminInstances) throws IOException {
    SQLAdmin.Instances.Insert sqlAdminInstancesInsert = mock(SQLAdmin.Instances.Insert.class);

    when(sqlAdminInstances.insert(eq(PROJECT_ID), any(DatabaseInstance.class))).thenReturn(sqlAdminInstancesInsert);

    return sqlAdminInstancesInsert;
  }

  private SQLAdmin.Operations mockSQLAdminToOperations() {
    SQLAdmin.Operations sqlAdminOperations = mock(SQLAdmin.Operations.class);

    when(sqlAdmin.operations()).thenReturn(sqlAdminOperations);

    return sqlAdminOperations;
  }

  private SQLAdmin.Instances.Delete mockSQLAdminInstancesDelete(
      SQLAdmin.Instances sqlAdminInstances, String instanceName) throws IOException {
    SQLAdmin.Instances.Delete sqlAdminInstancesDelete = mock(SQLAdmin.Instances.Delete.class);

    when(sqlAdminInstances.delete(eq(PROJECT_ID), eq(instanceName))).thenReturn(sqlAdminInstancesDelete);

    return sqlAdminInstancesDelete;
  }

  private SQLAdmin.Instances.Get mockSQLAdminInstancesGet(
      SQLAdmin.Instances sqlAdminInstances, String instanceName) throws IOException {
    SQLAdmin.Instances.Get sqlAdminInstancesGet = mock(SQLAdmin.Instances.Get.class);

    when(sqlAdminInstances.get(PROJECT_ID, instanceName)).thenReturn(sqlAdminInstancesGet);

    return sqlAdminInstancesGet;
  }

  @Test
  public void testAllocate_Standard() throws InterruptedException, IOException {
    // Prepare configuration for resource template.
    Map<String, String> templateConfig = new HashMap<String, String>();
    templateConfig.put(TIER.unwrap().getConfigKey(), TIER_NAME);
    templateConfig.put(MASTER_USERNAME.unwrap().getConfigKey(), USERNAME);
    templateConfig.put(MASTER_USER_PASSWORD.unwrap().getConfigKey(), USER_PASSWORD);
    templateConfig.put(ENGINE.unwrap().getConfigKey(), DATABASE_TYPE);

    // Create the resource template.
    GoogleCloudSQLInstanceTemplate template = sqlProvider.createResourceTemplate("template-1",
        new SimpleConfiguration(templateConfig), new HashMap<String, String>());

    // Configure stub for successful instance insertion operation.
    String instanceName = UUID.randomUUID().toString();
    String decoratedInstanceName = INSTANCE_NAME_PREFIX.unwrap().getDefaultValue() + "-" + instanceName;
    String instanceUrl = TestUtils.buildDatabaseInstanceUrl(PROJECT_ID, decoratedInstanceName);
    SQLAdmin.Instances sqlAdminInstances = mockSQLAdminToInstances();
    SQLAdmin.Instances.Insert sqlAdminInstancesInsert = mockSQLAdminInstancesInsert(sqlAdminInstances);
    Operation dbCreationOperation = buildInitialOperation("insert", instanceUrl);
    when(sqlAdminInstancesInsert.execute()).thenReturn(dbCreationOperation);
    SQLAdmin.Operations sqlAdminOperations = mockSQLAdminToOperations();
    SQLAdmin.Operations.Get sqlAdminOperationsGet = mock(SQLAdmin.Operations.Get.class);
    when(sqlAdminOperations.get(PROJECT_ID, dbCreationOperation.getName())).thenReturn(sqlAdminOperationsGet);
    when(sqlAdminOperationsGet.execute()).then(
        new OperationAnswer(dbCreationOperation, new String[]{"PENDING", "RUNNING", "DONE"}));

    sqlProvider.allocate(template, Lists.newArrayList(instanceName), 1);

    // Verify instance insertion call was made.
    ArgumentCaptor<DatabaseInstance> argumentCaptor = ArgumentCaptor.forClass(DatabaseInstance.class);
    verify(sqlAdminInstances).insert(eq(PROJECT_ID), argumentCaptor.capture());
    DatabaseInstance insertedInstance = argumentCaptor.getValue();

    // Verify instance name and metadata.
    assertThat(insertedInstance.getName()).isEqualTo(decoratedInstanceName);
    assertEquals(insertedInstance.getRegion(), "us-central");
  }

  @Test
  public void testAllocate_CreationFails_BelowMinCount() throws InterruptedException, IOException {

    // Prepare configuration for resource template.
    Map<String, String> templateConfig = new HashMap<String, String>();
    templateConfig.put(TIER.unwrap().getConfigKey(), TIER_NAME);
    templateConfig.put(MASTER_USERNAME.unwrap().getConfigKey(), USERNAME);
    templateConfig.put(MASTER_USER_PASSWORD.unwrap().getConfigKey(), USER_PASSWORD);
    templateConfig.put(ENGINE.unwrap().getConfigKey(), DATABASE_TYPE);

    // Create the resource template.
    GoogleCloudSQLInstanceTemplate template = sqlProvider.createResourceTemplate("template-1",
        new SimpleConfiguration(templateConfig), new HashMap<String, String>());

    // Configure stub for successful instance insertion operation.
    String instanceName1 = UUID.randomUUID().toString();
    String decoratedInstanceName1 = INSTANCE_NAME_PREFIX.unwrap().getDefaultValue() + "-" + instanceName1;
    String instanceUrl1 = TestUtils.buildDatabaseInstanceUrl(PROJECT_ID, decoratedInstanceName1);
    SQLAdmin.Instances sqlAdminInstances = mockSQLAdminToInstances();
    SQLAdmin.Instances.Insert sqlAdminInstancesInsert = mockSQLAdminInstancesInsert(sqlAdminInstances);
    Operation vmCreationOperation1 = buildInitialOperation("insert", instanceUrl1);
    OngoingStubbing<Operation> ongoingInsertionStub =
        when(sqlAdminInstancesInsert.execute()).thenReturn(vmCreationOperation1);
    SQLAdmin.Operations sqlAdminOperations = mockSQLAdminToOperations();
    SQLAdmin.Operations.Get sqlAdminOperationsGet1 = mock(SQLAdmin.Operations.Get.class);
    when(sqlAdminOperations.get(PROJECT_ID, vmCreationOperation1.getName())).thenReturn(sqlAdminOperationsGet1);
    when(sqlAdminOperationsGet1.execute()).then(
        new OperationAnswer(vmCreationOperation1, new String[]{"PENDING", "RUNNING", "DONE"}));

    // Configure stub for unsuccessful instance insertion operation.
    String instanceName2 = UUID.randomUUID().toString();
    String decoratedInstanceName2 = INSTANCE_NAME_PREFIX.unwrap().getDefaultValue() + "-" + instanceName2;
    String instanceUrl2 = TestUtils.buildDatabaseInstanceUrl(PROJECT_ID, decoratedInstanceName2);
    Operation vmCreationOperation2 = buildInitialOperation("insert", instanceUrl2);
    ongoingInsertionStub.thenReturn(vmCreationOperation2);
    SQLAdmin.Operations.Get sqlAdminOperationsGet2 = mock(SQLAdmin.Operations.Get.class);
    when(sqlAdminOperations.get(PROJECT_ID, vmCreationOperation2.getName())).thenReturn(sqlAdminOperationsGet2);
    when(sqlAdminOperationsGet2.execute()).then(
        new OperationAnswer(vmCreationOperation2, new String[]{"PENDING", "RUNNING", "DONE"},
            "SOME_ERROR_CODE", "Some error message..."));

    // Configure stub for successful instance deletion operation.
    SQLAdmin.Instances.Delete sqlAdminInstancesDelete1 =
        mockSQLAdminInstancesDelete(sqlAdminInstances, decoratedInstanceName1);
    Operation vmDeletionOperation1 = buildInitialOperation("delete", instanceUrl1);
    when(sqlAdminInstancesDelete1.execute()).thenReturn(vmDeletionOperation1);
    SQLAdmin.Operations.Get sqlAdminOperationsGet3 = mock(SQLAdmin.Operations.Get.class);
    when(sqlAdminOperations.get(PROJECT_ID, vmDeletionOperation1.getName())).thenReturn(sqlAdminOperationsGet3);
    when(sqlAdminOperationsGet3.execute()).then(
    new OperationAnswer(vmDeletionOperation1, new String[]{"PENDING", "RUNNING", "DONE"}));

    // Configure stub for successful instance deletion operation.
    SQLAdmin.Instances.Delete sqlAdminInstancesDelete2 =
        mockSQLAdminInstancesDelete(sqlAdminInstances, decoratedInstanceName2);
    Operation vmDeletionOperation2 = buildInitialOperation("delete", instanceUrl2);
    when(sqlAdminInstancesDelete2.execute()).thenReturn(vmDeletionOperation2);
    SQLAdmin.Operations.Get sqlAdminOperationsGet4 = mock(SQLAdmin.Operations.Get.class);
    when(sqlAdminOperations.get(PROJECT_ID, vmDeletionOperation2.getName())).thenReturn(sqlAdminOperationsGet4);
    when(sqlAdminOperationsGet4.execute()).then(
        new OperationAnswer(vmDeletionOperation2, new String[]{"PENDING", "RUNNING", "DONE"}));

    try {
      sqlProvider.allocate(template, Lists.newArrayList(instanceName1, instanceName2), 2);

      fail("An exception should have been thrown when we failed to provision at least minCount instances.");
    } catch (UnrecoverableProviderException e) {
      LOG.info("Caught: " + e.getMessage());

      assertThat(e.getMessage()).isEqualTo("Problem allocating instances.");
      verifySingleError(e.getDetails(), "Some error message...");
    }

    // Verify first instance insertion call was made.
    ArgumentCaptor<DatabaseInstance> insertArgumentCaptor = ArgumentCaptor.forClass(DatabaseInstance.class);
    verify(sqlAdminInstances, times(2)).insert(eq(PROJECT_ID), insertArgumentCaptor.capture());
    List<DatabaseInstance> insertedInstanceList = insertArgumentCaptor.getAllValues();
    DatabaseInstance insertedInstance1 = insertedInstanceList.get(0);

    // Verify first instance name.
    assertThat(insertedInstance1.getName()).isEqualTo(decoratedInstanceName1);

    // Verify second instance insertion call was made.
    DatabaseInstance insertedInstance2 = insertedInstanceList.get(1);

    // Verify second instance name and metadata.
    assertThat(insertedInstance2.getName()).isEqualTo(decoratedInstanceName2);

    // Verify first instance deletion call instance name.
    verify(sqlAdminInstances).delete(eq(PROJECT_ID), eq(decoratedInstanceName1));
  }

  @Test
  public void testAllocate_CreationFails_ReachesMinCount() throws InterruptedException, IOException {

    // Prepare configuration for resource template.
    Map<String, String> templateConfig = new HashMap<String, String>();
    templateConfig.put(TIER.unwrap().getConfigKey(), TIER_NAME);
    templateConfig.put(MASTER_USERNAME.unwrap().getConfigKey(), USERNAME);
    templateConfig.put(MASTER_USER_PASSWORD.unwrap().getConfigKey(), USER_PASSWORD);
    templateConfig.put(ENGINE.unwrap().getConfigKey(), DATABASE_TYPE);

    // Create the resource template.
    GoogleCloudSQLInstanceTemplate template = sqlProvider.createResourceTemplate("template-1",
        new SimpleConfiguration(templateConfig), new HashMap<String, String>());

    // Configure stub for successful instance insertion operation.
    String instanceName1 = UUID.randomUUID().toString();
    String decoratedInstanceName1 = INSTANCE_NAME_PREFIX.unwrap().getDefaultValue() + "-" + instanceName1;
    String instanceUrl1 = TestUtils.buildDatabaseInstanceUrl(PROJECT_ID, decoratedInstanceName1);
    SQLAdmin.Instances sqlAdminInstances = mockSQLAdminToInstances();
    SQLAdmin.Instances.Insert sqlAdminInstancesInsert = mockSQLAdminInstancesInsert(sqlAdminInstances);
    Operation vmCreationOperation1 = buildInitialOperation("insert", instanceUrl1);
    OngoingStubbing<Operation> ongoingInsertionStub =
        when(sqlAdminInstancesInsert.execute()).thenReturn(vmCreationOperation1);
    SQLAdmin.Operations sqlAdminOperations = mockSQLAdminToOperations();
    SQLAdmin.Operations.Get sqlAdminOperationsGet1 = mock(SQLAdmin.Operations.Get.class);
    when(sqlAdminOperations.get(PROJECT_ID, vmCreationOperation1.getName())).thenReturn(sqlAdminOperationsGet1);
    when(sqlAdminOperationsGet1.execute()).then(
        new OperationAnswer(vmCreationOperation1, new String[]{"PENDING", "RUNNING", "DONE"}));

    // Configure stub for unsuccessful instance insertion operation.
    String instanceName2 = UUID.randomUUID().toString();
    String decoratedInstanceName2 = INSTANCE_NAME_PREFIX.unwrap().getDefaultValue() + "-" + instanceName2;
    String instanceUrl2 = TestUtils.buildDatabaseInstanceUrl(PROJECT_ID, decoratedInstanceName2);
    Operation vmCreationOperation2 = buildInitialOperation("insert", instanceUrl2);
    ongoingInsertionStub.thenReturn(vmCreationOperation2);
    SQLAdmin.Operations.Get sqlAdminOperationsGet2 = mock(SQLAdmin.Operations.Get.class);
    when(sqlAdminOperations.get(PROJECT_ID, vmCreationOperation2.getName())).thenReturn(sqlAdminOperationsGet2);
    when(sqlAdminOperationsGet2.execute()).then(
        new OperationAnswer(vmCreationOperation2, new String[]{"PENDING", "RUNNING", "DONE"},
            "SOME_ERROR_CODE", "Some error message..."));

    sqlProvider.allocate(template, Lists.newArrayList(instanceName1, instanceName2), 1);

    // Verify first instance insertion call was made.
    ArgumentCaptor<DatabaseInstance> insertArgumentCaptor = ArgumentCaptor.forClass(DatabaseInstance.class);
    verify(sqlAdminInstances, times(2)).insert(eq(PROJECT_ID), insertArgumentCaptor.capture());
    List<DatabaseInstance> insertedInstanceList = insertArgumentCaptor.getAllValues();
    DatabaseInstance insertedInstance1 = insertedInstanceList.get(0);

    // Verify first instance name.
    assertThat(insertedInstance1.getName()).isEqualTo(decoratedInstanceName1);

    // Verify second instance insertion call was made.
    DatabaseInstance insertedInstance2 = insertedInstanceList.get(1);

    // Verify second instance name.
    assertThat(insertedInstance2.getName()).isEqualTo(decoratedInstanceName2);

    // NPE would be thrown (due to lack of mocks) if the SQL provider attempted actual deletion calls against GCE.
    // If no NPE's are thrown, the test is a success.
  }

  @Test
  public void testAllocate_RESOURCE_ALREADY_EXISTS() throws InterruptedException, IOException {
    // Prepare configuration for resource template.
    Map<String, String> templateConfig = new HashMap<String, String>();
    templateConfig.put(TIER.unwrap().getConfigKey(), TIER_NAME);
    templateConfig.put(MASTER_USERNAME.unwrap().getConfigKey(), USERNAME);
    templateConfig.put(MASTER_USER_PASSWORD.unwrap().getConfigKey(), USER_PASSWORD);
    templateConfig.put(ENGINE.unwrap().getConfigKey(), DATABASE_TYPE);

    // Create the resource template.
    GoogleCloudSQLInstanceTemplate template = sqlProvider.createResourceTemplate("template-1",
    new SimpleConfiguration(templateConfig), new HashMap<String, String>());

    // Configure stub for unsuccessful instance insertion operation of instance that already exists.
    String instanceName = UUID.randomUUID().toString();
    String decoratedInstanceName = INSTANCE_NAME_PREFIX.unwrap().getDefaultValue() + "-" + instanceName;
    String instanceUrl = TestUtils.buildDatabaseInstanceUrl(PROJECT_ID, decoratedInstanceName);
    SQLAdmin.Instances sqlAdminInstances = mockSQLAdminToInstances();
    SQLAdmin.Instances.Insert sqlAdminInstancesInsert = mockSQLAdminInstancesInsert(sqlAdminInstances);
    Operation vmCreationOperation = buildInitialOperation("insert", instanceUrl);
    when(sqlAdminInstancesInsert.execute()).thenReturn(vmCreationOperation);
    SQLAdmin.Operations sqlAdminOperations = mockSQLAdminToOperations();
    SQLAdmin.Operations.Get sqlAdminOperationsGet = mock(SQLAdmin.Operations.Get.class);
    when(sqlAdminOperations.get(PROJECT_ID, vmCreationOperation.getName())).thenReturn(sqlAdminOperationsGet);
    when(sqlAdminOperationsGet.execute()).then(
    new OperationAnswer(vmCreationOperation, new String[]{"PENDING", "RUNNING", "DONE"},
    "RESOURCE_ALREADY_EXISTS", "Some error message..."));

    // An UnrecoverableProviderException would be thrown if the compute provider did not treat an error code of
    // RESOURCE_ALREADY_EXISTS on insertion as acceptable.
    // If no UnrecoverableProviderException is thrown, the test is a success.
    sqlProvider.allocate(template, Lists.newArrayList(instanceName), 1);

    // Verify instance insertion call was made.
    ArgumentCaptor<DatabaseInstance> argumentCaptor = ArgumentCaptor.forClass(DatabaseInstance.class);
    verify(sqlAdminInstances).insert(eq(PROJECT_ID), argumentCaptor.capture());
    DatabaseInstance insertedInstance = argumentCaptor.getValue();

    // Verify instance name and metadata.
    assertThat(insertedInstance.getName()).isEqualTo(decoratedInstanceName);
  }

  @Test
  public void testFind() throws InterruptedException, IOException {
    // Prepare configuration for resource template.
    Map<String, String> templateConfig = new HashMap<String, String>();
    templateConfig.put(TIER.unwrap().getConfigKey(), TIER_NAME);
    templateConfig.put(MASTER_USERNAME.unwrap().getConfigKey(), USERNAME);
    templateConfig.put(MASTER_USER_PASSWORD.unwrap().getConfigKey(), USER_PASSWORD);
    templateConfig.put(ENGINE.unwrap().getConfigKey(), DATABASE_TYPE);

    // Create the resource template.
    GoogleCloudSQLInstanceTemplate template = sqlProvider.createResourceTemplate("template-1",
        new SimpleConfiguration(templateConfig), new HashMap<String, String>());

    String instanceName1 = UUID.randomUUID().toString();
    String decoratedInstanceName1 = INSTANCE_NAME_PREFIX.unwrap().getDefaultValue() + "-" + instanceName1;
    String instanceName2 = UUID.randomUUID().toString();
    String decoratedInstanceName2 = INSTANCE_NAME_PREFIX.unwrap().getDefaultValue() + "-" + instanceName2;
    List<String> instanceIds = Lists.newArrayList(instanceName1, instanceName2);

    // Configure stub for first successful instance retrieval.
    SQLAdmin.Instances sqlAdminInstances = mockSQLAdminToInstances();
    SQLAdmin.Instances.Get sqlAdminInstancesGet1 = mockSQLAdminInstancesGet(sqlAdminInstances, decoratedInstanceName1);
    DatabaseInstance instance1 = new DatabaseInstance();

    when(sqlAdminInstancesGet1.execute()).thenReturn(instance1);

    // Configure stub for second successful instance retrieval.
    SQLAdmin.Instances.Get sqlAdminInstancesGet2 = mockSQLAdminInstancesGet(sqlAdminInstances, decoratedInstanceName2);
    DatabaseInstance instance2 = new DatabaseInstance();

    when(sqlAdminInstancesGet2.execute()).thenReturn(instance2);

    Collection<GoogleCloudSQLInstance> foundInstances = sqlProvider.find(template, instanceIds);

    // Verify that both of the two requested instances were returned.
    assertThat(foundInstances.size()).isEqualTo(2);

    // Verify the properties of the first returned instance.
    Iterator<GoogleCloudSQLInstance> instanceIterator = foundInstances.iterator();
    GoogleCloudSQLInstance foundInstance1 = instanceIterator.next();
    assertThat(foundInstance1.getId()).isEqualTo(instanceName1);

    // Verify the properties of the second returned instance.
    GoogleCloudSQLInstance foundInstance2 = instanceIterator.next();
    assertThat(foundInstance2.getId()).isEqualTo(instanceName2);
  }

  @Test
  public void testFind_PartialSuccess() throws InterruptedException, IOException {
    // Prepare configuration for resource template.
    Map<String, String> templateConfig = new HashMap<String, String>();
    templateConfig.put(MASTER_USERNAME.unwrap().getConfigKey(), USERNAME);
    templateConfig.put(MASTER_USER_PASSWORD.unwrap().getConfigKey(), USER_PASSWORD);
    templateConfig.put(ENGINE.unwrap().getConfigKey(), DATABASE_TYPE);

    // Create the resource template.
    GoogleCloudSQLInstanceTemplate template = sqlProvider.createResourceTemplate("template-1",
    new SimpleConfiguration(templateConfig), new HashMap<String, String>());

    String instanceName1 = UUID.randomUUID().toString();
    String decoratedInstanceName1 = INSTANCE_NAME_PREFIX.unwrap().getDefaultValue() + "-" + instanceName1;
    String instanceName2 = UUID.randomUUID().toString();
    String decoratedInstanceName2 = INSTANCE_NAME_PREFIX.unwrap().getDefaultValue() + "-" + instanceName2;
    List<String> instanceIds = Lists.newArrayList(instanceName1, instanceName2);

    // Configure stub for successful instance retrieval.
    SQLAdmin.Instances sqlAdminInstances = mockSQLAdminToInstances();
    SQLAdmin.Instances.Get sqlAdminInstancesGet1 = mockSQLAdminInstancesGet(sqlAdminInstances, decoratedInstanceName1);
    DatabaseInstance instance1 = new DatabaseInstance();

    when(sqlAdminInstancesGet1.execute()).thenReturn(instance1);

    // Configure stub for unsuccessful instance retrieval (throws 404).
    SQLAdmin.Instances.Get sqlAdminInstancesGet2 = mockSQLAdminInstancesGet(sqlAdminInstances, decoratedInstanceName2);
    GoogleJsonResponseException exception =
        GoogleJsonResponseExceptionFactoryTesting.newMock(new MockJsonFactory(), 404, "not found");
    when(sqlAdminInstancesGet2.execute()).thenThrow(exception);

    Collection<GoogleCloudSQLInstance> foundInstances = sqlProvider.find(template, instanceIds);

    // Verify that exactly one of the two requested instances was returned.
    assertThat(foundInstances.size()).isEqualTo(1);

    // Verify the properties of the returned instance.
    GoogleCloudSQLInstance foundInstance = foundInstances.iterator().next();
    assertThat(foundInstance.getId()).isEqualTo(instanceName1);
  }

  @Test
  public void testFind_InvalidPrefix() throws InterruptedException, IOException {
    // Prepare configuration for resource template.
    Map<String, String> templateConfig = new HashMap<String, String>();
    templateConfig.put(MASTER_USERNAME.unwrap().getConfigKey(), USERNAME);
    templateConfig.put(MASTER_USER_PASSWORD.unwrap().getConfigKey(), USER_PASSWORD);
    templateConfig.put(ENGINE.unwrap().getConfigKey(), DATABASE_TYPE);
    templateConfig.put(INSTANCE_NAME_PREFIX.unwrap().getConfigKey(), INVALID_INSTANCE_NAME_PREFIX);

    // Create the resource template.
    GoogleCloudSQLInstanceTemplate template = sqlProvider.createResourceTemplate("template-1",
        new SimpleConfiguration(templateConfig), new HashMap<String, String>());

    String instanceName1 = UUID.randomUUID().toString();
    String instanceName2 = UUID.randomUUID().toString();
    List<String> instanceIds = Lists.newArrayList(instanceName1, instanceName2);

    // NPE would be thrown (due to lack of mocks) if the sql provider attempted actual calls against GCE.
    // When the instance name prefix is deemed invalid, no calls are attempted against GCE.
    Collection<GoogleCloudSQLInstance> foundInstances = sqlProvider.find(template, instanceIds);

    // Verify that no instances were returned.
    assertThat(foundInstances.size()).isEqualTo(0);
  }

  @Test
  public void testGetInstanceState() throws InterruptedException, IOException {
    // Prepare configuration for resource template.
    Map<String, String> templateConfig = new HashMap<String, String>();
    templateConfig.put(MASTER_USERNAME.unwrap().getConfigKey(), USERNAME);
    templateConfig.put(MASTER_USER_PASSWORD.unwrap().getConfigKey(), USER_PASSWORD);
    templateConfig.put(ENGINE.unwrap().getConfigKey(), DATABASE_TYPE);

    // Create the resource template.
    GoogleCloudSQLInstanceTemplate template = sqlProvider.createResourceTemplate("template-1",
        new SimpleConfiguration(templateConfig), new HashMap<String, String>());

    String instanceName1 = UUID.randomUUID().toString();
    String decoratedInstanceName1 = INSTANCE_NAME_PREFIX.unwrap().getDefaultValue() + "-" + instanceName1;
    String instanceName2 = UUID.randomUUID().toString();
    String decoratedInstanceName2 = INSTANCE_NAME_PREFIX.unwrap().getDefaultValue() + "-" + instanceName2;
    List<String> instanceIds = Lists.newArrayList(instanceName1, instanceName2);

    // Configure stub for first successful instance retrieval.
    SQLAdmin.Instances sqlAdminInstances = mockSQLAdminToInstances();
    SQLAdmin.Instances.Get sqlAdminInstancesGet1 = mockSQLAdminInstancesGet(sqlAdminInstances, decoratedInstanceName1);
    DatabaseInstance instance1 = new DatabaseInstance();
    instance1.setState("PENDING_CREATE");
    when(sqlAdminInstancesGet1.execute()).thenReturn(instance1);

    // Configure stub for second successful instance retrieval.
    SQLAdmin.Instances.Get sqlAdminInstancesGet2 = mockSQLAdminInstancesGet(sqlAdminInstances, decoratedInstanceName2);
    DatabaseInstance instance2 = new DatabaseInstance();
    instance2.setState("RUNNABLE");
    when(sqlAdminInstancesGet2.execute()).thenReturn(instance2);

    Map<String, InstanceState> instanceStates = sqlProvider.getInstanceState(template, instanceIds);

    // Verify that the state of both instances was returned.
    assertThat(instanceStates.size()).isEqualTo(2);

    // Verify the state of the first instance.
    InstanceState instanceState1 = instanceStates.get(instanceName1);
    assertThat(instanceState1.getInstanceStatus()).isEqualTo(InstanceStatus.PENDING);

    // Verify the state of the second instance.
    InstanceState instanceState2 = instanceStates.get(instanceName2);
    assertThat(instanceState2.getInstanceStatus()).isEqualTo(InstanceStatus.RUNNING);
  }

  @Test
  public void testGetInstanceState_PartialSuccess() throws InterruptedException, IOException {
    // Prepare configuration for resource template.
    Map<String, String> templateConfig = new HashMap<String, String>();
    templateConfig.put(MASTER_USERNAME.unwrap().getConfigKey(), USERNAME);
    templateConfig.put(MASTER_USER_PASSWORD.unwrap().getConfigKey(), USER_PASSWORD);
    templateConfig.put(ENGINE.unwrap().getConfigKey(), DATABASE_TYPE);

    // Create the resource template.
    GoogleCloudSQLInstanceTemplate template = sqlProvider.createResourceTemplate("template-1",
        new SimpleConfiguration(templateConfig), new HashMap<String, String>());

    String instanceName1 = UUID.randomUUID().toString();
    String decoratedInstanceName1 = INSTANCE_NAME_PREFIX.unwrap().getDefaultValue() + "-" + instanceName1;
    String instanceName2 = UUID.randomUUID().toString();
    String decoratedInstanceName2 = INSTANCE_NAME_PREFIX.unwrap().getDefaultValue() + "-" + instanceName2;
    List<String> instanceIds = Lists.newArrayList(instanceName1, instanceName2);

    // Configure stub for first successful instance retrieval.
    SQLAdmin.Instances sqlAdminInstances = mockSQLAdminToInstances();
    SQLAdmin.Instances.Get sqlAdminInstancesGet1 = mockSQLAdminInstancesGet(sqlAdminInstances, decoratedInstanceName1);
    DatabaseInstance instance1 = new DatabaseInstance();
    instance1.setState("PENDING_CREATE");
    when(sqlAdminInstancesGet1.execute()).thenReturn(instance1);

    // Configure stub for unsuccessful instance retrieval (throws 404).
    SQLAdmin.Instances.Get sqlAdminInstancesGet2 = mockSQLAdminInstancesGet(sqlAdminInstances, decoratedInstanceName2);
    GoogleJsonResponseException exception =
        GoogleJsonResponseExceptionFactoryTesting.newMock(new MockJsonFactory(), 404, "not found");
    when(sqlAdminInstancesGet2.execute()).thenThrow(exception);

    Map<String, InstanceState> instanceStates = sqlProvider.getInstanceState(template, instanceIds);

    // Verify that the state of both instances was returned.
    assertThat(instanceStates.size()).isEqualTo(2);

    // Verify the state of the first instance.
    InstanceState instanceState1 = instanceStates.get(instanceName1);
    assertThat(instanceState1.getInstanceStatus()).isEqualTo(InstanceStatus.PENDING);

    // Verify the state of the second instance.
    InstanceState instanceState2 = instanceStates.get(instanceName2);
    assertThat(instanceState2.getInstanceStatus()).isEqualTo(InstanceStatus.UNKNOWN);
  }

  @Test
  public void testGetInstanceState_InvalidPrefix() throws InterruptedException, IOException {
    // Prepare configuration for resource template.
    Map<String, String> templateConfig = new HashMap<String, String>();
    templateConfig.put(MASTER_USERNAME.unwrap().getConfigKey(), USERNAME);
    templateConfig.put(MASTER_USER_PASSWORD.unwrap().getConfigKey(), USER_PASSWORD);
    templateConfig.put(ENGINE.unwrap().getConfigKey(), DATABASE_TYPE);
    templateConfig.put(INSTANCE_NAME_PREFIX.unwrap().getConfigKey(), INVALID_INSTANCE_NAME_PREFIX);

    // Create the resource template.
    GoogleCloudSQLInstanceTemplate template = sqlProvider.createResourceTemplate("template-1",
        new SimpleConfiguration(templateConfig), new HashMap<String, String>());

    String instanceName1 = UUID.randomUUID().toString();
    String instanceName2 = UUID.randomUUID().toString();
    List<String> instanceIds = Lists.newArrayList(instanceName1, instanceName2);

    // NPE would be thrown (due to lack of mocks) if the sql provider attempted actual calls against GCE.
    // When the instance name prefix is deemed invalid, no calls are attempted against GCE.
    Map<String, InstanceState> instanceStates = sqlProvider.getInstanceState(template, instanceIds);

    // Verify that the state of both instances was returned.
    assertThat(instanceStates.size()).isEqualTo(2);

    // Verify the state of the first instance.
    InstanceState instanceState1 = instanceStates.get(instanceName1);
    assertThat(instanceState1.getInstanceStatus()).isEqualTo(InstanceStatus.UNKNOWN);

    // Verify the state of the second instance.
    InstanceState instanceState2 = instanceStates.get(instanceName2);
    assertThat(instanceState2.getInstanceStatus()).isEqualTo(InstanceStatus.UNKNOWN);
  }

  @Test
  public void testDelete() throws InterruptedException, IOException {
    // Prepare configuration for resource template.
    Map<String, String> templateConfig = new HashMap<String, String>();
    templateConfig.put(MASTER_USERNAME.unwrap().getConfigKey(), USERNAME);
    templateConfig.put(MASTER_USER_PASSWORD.unwrap().getConfigKey(), USER_PASSWORD);
    templateConfig.put(ENGINE.unwrap().getConfigKey(), DATABASE_TYPE);

    // Create the resource template.
    GoogleCloudSQLInstanceTemplate template = sqlProvider.createResourceTemplate("template-1",
        new SimpleConfiguration(templateConfig), new HashMap<String, String>());

    String instanceName1 = UUID.randomUUID().toString();
    String decoratedInstanceName1 = INSTANCE_NAME_PREFIX.unwrap().getDefaultValue() + "-" + instanceName1;
    String instanceUrl1 = TestUtils.buildDatabaseInstanceUrl(PROJECT_ID, decoratedInstanceName1);
    String instanceName2 = UUID.randomUUID().toString();
    String decoratedInstanceName2 = INSTANCE_NAME_PREFIX.unwrap().getDefaultValue() + "-" + instanceName2;
    String instanceUrl2 = TestUtils.buildDatabaseInstanceUrl(PROJECT_ID, decoratedInstanceName2);
    List<String> instanceIds = Lists.newArrayList(instanceName1, instanceName2);

    // Configure stub for successful instance deletion operation.
    SQLAdmin.Instances sqlAdminInstances = mockSQLAdminToInstances();
    SQLAdmin.Instances.Delete sqlAdminInstancesDelete1 =
        mockSQLAdminInstancesDelete(sqlAdminInstances, decoratedInstanceName1);
    Operation vmDeletionOperation1 = buildInitialOperation("delete", instanceUrl1);
    when(sqlAdminInstancesDelete1.execute()).thenReturn(vmDeletionOperation1);
    SQLAdmin.Operations sqlAdminOperations = mockSQLAdminToOperations();
    SQLAdmin.Operations.Get sqlAdminOperationsGet1 = mock(SQLAdmin.Operations.Get.class);
    when(sqlAdminOperations.get(PROJECT_ID, vmDeletionOperation1.getName())).thenReturn(sqlAdminOperationsGet1);
    when(sqlAdminOperationsGet1.execute()).then(
        new OperationAnswer(vmDeletionOperation1, new String[]{"PENDING", "RUNNING", "DONE"}));

    // Configure stub for successful instance deletion operation.
    SQLAdmin.Instances.Delete sqlAdminInstancesDelete2 =
        mockSQLAdminInstancesDelete(sqlAdminInstances, decoratedInstanceName2);
    Operation vmDeletionOperation2 = buildInitialOperation("delete", instanceUrl2);
    when(sqlAdminInstancesDelete2.execute()).thenReturn(vmDeletionOperation2);
    SQLAdmin.Operations.Get sqlAdminOperationsGet2 = mock(SQLAdmin.Operations.Get.class);
    when(sqlAdminOperations.get(PROJECT_ID, vmDeletionOperation2.getName())).thenReturn(sqlAdminOperationsGet2);
    when(sqlAdminOperationsGet2.execute()).then(
    new OperationAnswer(vmDeletionOperation2, new String[]{"PENDING", "RUNNING", "DONE"}));

    sqlProvider.delete(template, instanceIds);

    // Verify first instance deletion call was made.
    verify(sqlAdminInstances).delete(eq(PROJECT_ID), eq(decoratedInstanceName1));

    // Verify second instance deletion call was made.
    verify(sqlAdminInstances).delete(eq(PROJECT_ID), eq(decoratedInstanceName2));
  }

  @Test
  public void testDelete_PartialSuccess() throws InterruptedException, IOException {
    // Prepare configuration for resource template.
    Map<String, String> templateConfig = new HashMap<String, String>();
    templateConfig.put(MASTER_USERNAME.unwrap().getConfigKey(), USERNAME);
    templateConfig.put(MASTER_USER_PASSWORD.unwrap().getConfigKey(), USER_PASSWORD);
    templateConfig.put(ENGINE.unwrap().getConfigKey(), DATABASE_TYPE);

    // Create the resource template.
    GoogleCloudSQLInstanceTemplate template = sqlProvider.createResourceTemplate("template-1",
    new SimpleConfiguration(templateConfig), new HashMap<String, String>());

    String instanceName1 = UUID.randomUUID().toString();
    String decoratedInstanceName1 = INSTANCE_NAME_PREFIX.unwrap().getDefaultValue() + "-" + instanceName1;
    String instanceUrl1 = TestUtils.buildDatabaseInstanceUrl(PROJECT_ID, decoratedInstanceName1);
    String instanceName2 = UUID.randomUUID().toString();
    String decoratedInstanceName2 = INSTANCE_NAME_PREFIX.unwrap().getDefaultValue() + "-" + instanceName2;
    List<String> instanceIds = Lists.newArrayList(instanceName1, instanceName2);

    // Configure stub for successful instance deletion operation.
    SQLAdmin.Instances sqlAdminInstances = mockSQLAdminToInstances();
    SQLAdmin.Instances.Delete sqlAdminInstancesDelete1 =
        mockSQLAdminInstancesDelete(sqlAdminInstances, decoratedInstanceName1);
    Operation vmDeletionOperation1 = buildInitialOperation("delete", instanceUrl1);
    when(sqlAdminInstancesDelete1.execute()).thenReturn(vmDeletionOperation1);
    SQLAdmin.Operations sqlAdminOperations = mockSQLAdminToOperations();
    SQLAdmin.Operations.Get sqlAdminOperationsGet1 = mock(SQLAdmin.Operations.Get.class);
    when(sqlAdminOperations.get(PROJECT_ID, vmDeletionOperation1.getName())).thenReturn(sqlAdminOperationsGet1);
    when(sqlAdminOperationsGet1.execute()).then(
        new OperationAnswer(vmDeletionOperation1, new String[]{"PENDING", "RUNNING", "DONE"}));

    // Configure stub for unsuccessful instance deletion operation.
    SQLAdmin.Instances.Delete sqlAdminInstancesDelete2 =
        mockSQLAdminInstancesDelete(sqlAdminInstances, decoratedInstanceName2);
    GoogleJsonResponseException exception =
        GoogleJsonResponseExceptionFactoryTesting.newMock(new MockJsonFactory(), 404, "not found");
    when(sqlAdminInstancesDelete2.execute()).thenThrow(exception);

    sqlProvider.delete(template, instanceIds);

    // Verify first instance deletion call was made.
    verify(sqlAdminInstances).delete(eq(PROJECT_ID), eq(decoratedInstanceName1));

    // Verify second instance deletion call was made.
    verify(sqlAdminInstances).delete(eq(PROJECT_ID), eq(decoratedInstanceName2));
  }

  @Test
  public void testDelete_InvalidPrefix() throws InterruptedException, IOException {
    // Prepare configuration for resource template.
    Map<String, String> templateConfig = new HashMap<String, String>();
    templateConfig.put(MASTER_USERNAME.unwrap().getConfigKey(), USERNAME);
    templateConfig.put(MASTER_USER_PASSWORD.unwrap().getConfigKey(), USER_PASSWORD);
    templateConfig.put(ENGINE.unwrap().getConfigKey(), DATABASE_TYPE);
    templateConfig.put(INSTANCE_NAME_PREFIX.unwrap().getConfigKey(), INVALID_INSTANCE_NAME_PREFIX);

    // Create the resource template.
    GoogleCloudSQLInstanceTemplate template = sqlProvider.createResourceTemplate("template-1",
        new SimpleConfiguration(templateConfig), new HashMap<String, String>());

    String instanceName1 = UUID.randomUUID().toString();
    String instanceName2 = UUID.randomUUID().toString();
    List<String> instanceIds = Lists.newArrayList(instanceName1, instanceName2);

    // NPE would be thrown (due to lack of mocks) if the sql provider attempted actual calls against GCE.
    // When the instance name prefix is deemed invalid, no calls are attempted against GCE.
    // If no NPE's are thrown, the test is a success.
    sqlProvider.delete(template, instanceIds);
  }

  @Test
  public void testDelete_RESOURCE_NOT_FOUND() throws InterruptedException, IOException {
    // Prepare configuration for resource template.
    Map<String, String> templateConfig = new HashMap<String, String>();
    templateConfig.put(MASTER_USERNAME.unwrap().getConfigKey(), USERNAME);
    templateConfig.put(MASTER_USER_PASSWORD.unwrap().getConfigKey(), USER_PASSWORD);
    templateConfig.put(ENGINE.unwrap().getConfigKey(), DATABASE_TYPE);

    // Create the resource template.
    GoogleCloudSQLInstanceTemplate template = sqlProvider.createResourceTemplate("template-1",
    new SimpleConfiguration(templateConfig), new HashMap<String, String>());

    String instanceName = UUID.randomUUID().toString();
    String decoratedInstanceName = INSTANCE_NAME_PREFIX.unwrap().getDefaultValue() + "-" + instanceName;
    String instanceUrl = TestUtils.buildDatabaseInstanceUrl(PROJECT_ID, decoratedInstanceName);
    List<String> instanceIds = Lists.newArrayList(instanceName);

    // Configure stub for unsuccessful instance deletion operation of instance that does not exist.
    SQLAdmin.Instances sqlAdminInstances = mockSQLAdminToInstances();
    SQLAdmin.Instances.Delete sqlAdminInstancesDelete =
        mockSQLAdminInstancesDelete(sqlAdminInstances, decoratedInstanceName);
    Operation vmDeletionOperation = buildInitialOperation("delete", instanceUrl);
    when(sqlAdminInstancesDelete.execute()).thenReturn(vmDeletionOperation);
    SQLAdmin.Operations sqlAdminOperations = mockSQLAdminToOperations();
    SQLAdmin.Operations.Get sqlAdminOperationsGet = mock(SQLAdmin.Operations.Get.class);
    when(sqlAdminOperations.get(PROJECT_ID, vmDeletionOperation.getName())).thenReturn(sqlAdminOperationsGet);
    when(sqlAdminOperationsGet.execute()).then(
        new OperationAnswer(vmDeletionOperation, new String[]{"PENDING", "DONE"},
            "RESOURCE_NOT_FOUND", "Some error message..."));

    // An UnrecoverableProviderException would be thrown if the compute provider did not treat an error code of
    // RESOURCE_NOT_FOUND on deletion as acceptable.
    // If no UnrecoverableProviderException is thrown, the test is a success.
    sqlProvider.delete(template, instanceIds);

    // Verify instance deletion call was made.
    verify(sqlAdminInstances).delete(eq(PROJECT_ID), eq(decoratedInstanceName));
  }

  private static Operation buildInitialOperation(String operationType, String targetLinkUrl) {
    return buildOperation(UUID.randomUUID().toString(), operationType, targetLinkUrl, "PENDING");
  }

  private static Operation buildOperation(String operationName, String operationType, String targetLinkUrl,
                                          String status) {
    Operation operation = new Operation();

    operation.setName(operationName);
    operation.setOperationType(operationType);
    operation.setTargetLink(targetLinkUrl);
    operation.setStatus(status);

    return operation;
  }

  /**
   * Used to return operations with a series of statuses in response to the sql provider polling.
   * If both the errorCode and the errorMessage are not null and not empty, an error will be set on the
   * returned operation.
   */
  class OperationAnswer implements Answer<Operation> {
    Operation subjectOperation;
    private Deque<String> statusQueue;
    private String errorCode;
    private String errorMessage;

    public OperationAnswer(Operation subjectOperation, String[] statuses) {
      this.subjectOperation = subjectOperation;
      this.statusQueue = new ArrayDeque<String>();

      for (String status : statuses) {
        statusQueue.add(status);
      }
    }

    public OperationAnswer(Operation subjectOperation, String[] statuses, String errorCode, String errorMessage) {
      this(subjectOperation, statuses);

      this.errorCode = errorCode;
      this.errorMessage = errorMessage;
    }

    @Override
    public Operation answer(InvocationOnMock invocationOnMock) throws Throwable {
      Operation polledOperation = buildOperation(subjectOperation.getName(), subjectOperation.getOperationType(),
          subjectOperation.getTargetLink(), statusQueue.remove());

      if (polledOperation.getStatus().equals("DONE") && errorCode != null) {
        OperationError errors = new OperationError();
        errors.setCode(errorCode);
        errors.setMessage(errorMessage);

        List<OperationError> errorsList = Lists.newArrayList(errors);
        OperationErrors error = new OperationErrors();
        error.setErrors(errorsList);

        polledOperation.setError(error);
      }

      return polledOperation;
    }

  };

  /**
   * Verifies that the specified plugin exception details contain exactly one condition, which must be an
   * error with the specified message.
   *
   * @param pluginExceptionDetails the exception details containins the error conditions
   * @param errorMsg               the expected error message
   */
  private static void verifySingleError(PluginExceptionDetails pluginExceptionDetails, String errorMsg) {
    Map<String, SortedSet<PluginExceptionCondition>> conditionsByKey = pluginExceptionDetails.getConditionsByKey();
    assertThat(conditionsByKey).hasSize(1);
    Collection<PluginExceptionCondition> keyConditions = conditionsByKey.get(null);
    assertThat(keyConditions).hasSize(1);
    PluginExceptionCondition condition = keyConditions.iterator().next();
    assertThat(condition.getMessage()).isEqualTo(errorMsg);
  }
}