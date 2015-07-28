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

import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationProperty.DATA_DISK_COUNT;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationProperty.DATA_DISK_SIZE_GB;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationProperty.DATA_DISK_TYPE;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationProperty.IMAGE;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationProperty.NETWORK_NAME;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationProperty.TYPE;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationProperty.ZONE;
import static com.cloudera.director.google.compute.GoogleComputeProviderConfigurationProperty.REGION;
import static com.cloudera.director.spi.v1.model.InstanceTemplate.InstanceTemplateConfigurationPropertyToken.INSTANCE_NAME_PREFIX;
import static junit.framework.Assert.fail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudera.director.google.TestUtils;
import com.cloudera.director.google.compute.util.Urls;
import com.cloudera.director.google.internal.GoogleCredentials;
import com.cloudera.director.google.shaded.com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.cloudera.director.google.shaded.com.google.api.client.googleapis.testing.json.GoogleJsonResponseExceptionFactoryTesting;
import com.cloudera.director.google.shaded.com.google.api.client.testing.json.MockJsonFactory;
import com.cloudera.director.google.shaded.com.google.api.services.compute.Compute;
import com.cloudera.director.google.shaded.com.google.api.services.compute.model.AttachedDisk;
import com.cloudera.director.google.shaded.com.google.api.services.compute.model.AttachedDiskInitializeParams;
import com.cloudera.director.google.shaded.com.google.api.services.compute.model.Disk;
import com.cloudera.director.google.shaded.com.google.api.services.compute.model.Instance;
import com.cloudera.director.google.shaded.com.google.api.services.compute.model.NetworkInterface;
import com.cloudera.director.google.shaded.com.google.api.services.compute.model.Operation;
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
 * Tests {@link GoogleComputeProvider}.
 */
public class GoogleComputeProviderTest {

  private static final Logger LOG = Logger.getLogger(GoogleComputeProviderTest.class.getName());

  private static final DefaultLocalizationContext DEFAULT_LOCALIZATION_CONTEXT =
      new DefaultLocalizationContext(Locale.getDefault(), "");

  private static final String PROJECT_ID = "some-project";
  private static final String REGION_NAME_1 = "us-central1";
  private static final String ZONE_NAME = "us-central1-a";
  private static final String IMAGE_ALIAS_CENTOS = "rhel6";
  private static final String IMAGE_PROJECT_ID = "rhel-cloud";
  private static final String IMAGE_NAME = "rhel-6-v20150526";
  private static final String MACHINE_TYPE_NAME = "n1-standard-1";
  private static final String NETWORK_NAME_VALUE = "some-network";
  private static final String INVALID_INSTANCE_NAME_PREFIX = "-starts-with-dash";

  private GoogleComputeProvider computeProvider;
  private GoogleCredentials credentials;
  private Compute compute;
  private GoogleComputeInstanceTemplateConfigurationValidator validator;

  @Before
  public void setUp() throws IOException {
    credentials = mock(GoogleCredentials.class);
    compute = mock(Compute.class);

    when(credentials.getCompute()).thenReturn(compute);
    when(credentials.getProjectId()).thenReturn(PROJECT_ID);

    Compute.Zones.List computeZonesList = mockComputeToZonesList();

    // We don't need to actually return an image, we just need to not throw a 404.
    when(computeZonesList.execute()).thenReturn(null);

    // Prepare configuration for Google compute provider.
    Map<String, String> computeConfig = new HashMap<String, String>();
    computeConfig.put(REGION.unwrap().getConfigKey(), REGION_NAME_1);
    Configured resourceProviderConfiguration = new SimpleConfiguration(computeConfig);

    // Create the Google compute provider.
    computeProvider = new GoogleComputeProvider(resourceProviderConfiguration, credentials,
        TestUtils.buildApplicationPropertiesConfig(), TestUtils.buildGoogleConfig(), DEFAULT_LOCALIZATION_CONTEXT);
  }

  private Compute.Zones.List mockComputeToZonesList() throws IOException {
    Compute.Zones computeZones = mock(Compute.Zones.class);
    Compute.Zones.List computeZonesList = mock(Compute.Zones.List.class);

    when(compute.zones()).thenReturn(computeZones);
    when(computeZones.list(PROJECT_ID)).thenReturn(computeZonesList);

    return computeZonesList;
  }

  private Compute.Instances mockComputeToInstances() {
    Compute.Instances computeInstances = mock(Compute.Instances.class);

    when(compute.instances()).thenReturn(computeInstances);

    return computeInstances;
  }

  private Compute.Instances.Insert mockComputeInstancesInsert(Compute.Instances computeInstances) throws IOException {
    Compute.Instances.Insert computeInstancesInsert = mock(Compute.Instances.Insert.class);

    when(computeInstances.insert(
        eq(PROJECT_ID), eq(ZONE_NAME), any(Instance.class))).thenReturn(computeInstancesInsert);

    return computeInstancesInsert;
  }

  private Compute.Instances.Delete mockComputeInstancesDelete(
      Compute.Instances computeInstances, String instanceName) throws IOException {
    Compute.Instances.Delete computeInstancesDelete = mock(Compute.Instances.Delete.class);

    when(computeInstances.delete(eq(PROJECT_ID), eq(ZONE_NAME), eq(instanceName))).thenReturn(computeInstancesDelete);

    return computeInstancesDelete;
  }

  private Compute.Instances.Get mockComputeInstancesGet(
      Compute.Instances computeInstances, String instanceName) throws IOException {
    Compute.Instances.Get computeInstancesGet = mock(Compute.Instances.Get.class);

    when(computeInstances.get(PROJECT_ID, ZONE_NAME, instanceName)).thenReturn(computeInstancesGet);

    return computeInstancesGet;
  }

  private Compute.ZoneOperations mockComputeToZoneOperations() {
    Compute.ZoneOperations computeZoneOperations = mock(Compute.ZoneOperations.class);

    when(compute.zoneOperations()).thenReturn(computeZoneOperations);

    return computeZoneOperations;
  }

  private Compute.Disks mockComputeToDisks() {
    Compute.Disks computeDisks = mock(Compute.Disks.class);

    when(compute.disks()).thenReturn(computeDisks);

    return computeDisks;
  }

  private Compute.Disks.Insert mockComputeDisksInsert(Compute.Disks computeDisks) throws IOException {
    Compute.Disks.Insert computeDisksInsert = mock(Compute.Disks.Insert.class);

    when(computeDisks.insert(
        eq(PROJECT_ID), eq(ZONE_NAME), any(Disk.class))).thenReturn(computeDisksInsert);

    return computeDisksInsert;
  }

  private Compute.Disks.Get mockComputeDisksGet(
      Compute.Disks computeDisks, String diskName) throws IOException {
    Compute.Disks.Get computeDisksGet = mock(Compute.Disks.Get.class);

    when(computeDisks.get(
        eq(PROJECT_ID), eq(ZONE_NAME), eq(diskName))).thenReturn(computeDisksGet);

    return computeDisksGet;
  }

  private Compute.Disks.Delete mockComputeDisksDelete(
      Compute.Disks computeDisks, String diskName) throws IOException {
    Compute.Disks.Delete computeDisksDelete = mock(Compute.Disks.Delete.class);

    when(computeDisks.delete(
        eq(PROJECT_ID), eq(ZONE_NAME), eq(diskName))).thenReturn(computeDisksDelete);

    return computeDisksDelete;
  }

  @Test
  public void testAllocate_LocalSSD() throws InterruptedException, IOException {
    // Prepare configuration for resource template.
    Map<String, String> templateConfig = new HashMap<String, String>();
    templateConfig.put(IMAGE.unwrap().getConfigKey(), IMAGE_ALIAS_CENTOS);
    templateConfig.put(TYPE.unwrap().getConfigKey(), MACHINE_TYPE_NAME);
    templateConfig.put(NETWORK_NAME.unwrap().getConfigKey(), NETWORK_NAME_VALUE);
    templateConfig.put(ZONE.unwrap().getConfigKey(), ZONE_NAME);

    // Create the resource template.
    GoogleComputeInstanceTemplate template = computeProvider.createResourceTemplate("template-1",
        new SimpleConfiguration(templateConfig), new HashMap<String, String>());

    // Configure stub for successful instance insertion operation.
    String instanceName = UUID.randomUUID().toString();
    String decoratedInstanceName = INSTANCE_NAME_PREFIX.unwrap().getDefaultValue() + "-" + instanceName;
    String instanceUrl = TestUtils.buildInstanceUrl(PROJECT_ID, ZONE_NAME, decoratedInstanceName);
    Compute.Instances computeInstances = mockComputeToInstances();
    Compute.Instances.Insert computeInstancesInsert = mockComputeInstancesInsert(computeInstances);
    Operation vmCreationOperation = buildOperation(ZONE_NAME, UUID.randomUUID().toString(), instanceUrl, "PENDING");
    when(computeInstancesInsert.execute()).thenReturn(vmCreationOperation);
    Compute.ZoneOperations computeZoneOperations = mockComputeToZoneOperations();
    Compute.ZoneOperations.Get computeZoneOperationsGet = mock(Compute.ZoneOperations.Get.class);
    when(computeZoneOperations.get(PROJECT_ID, ZONE_NAME,
        vmCreationOperation.getName())).thenReturn(computeZoneOperationsGet);
    when(computeZoneOperationsGet.execute()).then(
        new OperationAnswer(vmCreationOperation, new String[]{"PENDING", "RUNNING", "DONE"}));

    computeProvider.allocate(template, Lists.newArrayList(instanceName), 1);

    // Verify instance insertion call was made.
    ArgumentCaptor<Instance> argumentCaptor = ArgumentCaptor.forClass(Instance.class);
    verify(computeInstances).insert(eq(PROJECT_ID), eq(ZONE_NAME), argumentCaptor.capture());
    Instance insertedInstance = argumentCaptor.getValue();

    // Verify instance name and metadata.
    assertThat(insertedInstance.getName()).isEqualTo(decoratedInstanceName);
    assertThat(insertedInstance.getMetadata().getItems()).isEqualTo(Lists.newArrayList());

    List<AttachedDisk> attachedDiskList = insertedInstance.getDisks();
    assertThat(attachedDiskList.size()).isEqualTo(3);

    // Verify boot disk.
    verifyAttachedDiskAttributes(attachedDiskList.get(0), true, true, null, 60L,
        TestUtils.buildImageUrl(IMAGE_PROJECT_ID, IMAGE_NAME), null, null, null);

    // Verify data disk.
    verifyAttachedDiskAttributes(attachedDiskList.get(1), null, true,
        Urls.buildDiskTypeUrl(PROJECT_ID, ZONE_NAME, "LocalSSD"), null, null, "SCSI", "SCRATCH", null);

    // Verify data disk.
    verifyAttachedDiskAttributes(attachedDiskList.get(2), null, true,
        Urls.buildDiskTypeUrl(PROJECT_ID, ZONE_NAME, "LocalSSD"), null, null, "SCSI", "SCRATCH", null);
  }

  @Test
  public void testAllocate_LocalSSD_CreationFails_BelowMinCount() throws InterruptedException, IOException {
    // Prepare configuration for resource template.
    Map<String, String> templateConfig = new HashMap<String, String>();
    templateConfig.put(IMAGE.unwrap().getConfigKey(), IMAGE_ALIAS_CENTOS);
    templateConfig.put(TYPE.unwrap().getConfigKey(), MACHINE_TYPE_NAME);
    templateConfig.put(NETWORK_NAME.unwrap().getConfigKey(), NETWORK_NAME_VALUE);
    templateConfig.put(ZONE.unwrap().getConfigKey(), ZONE_NAME);

    // Create the resource template.
    GoogleComputeInstanceTemplate template = computeProvider.createResourceTemplate("template-1",
        new SimpleConfiguration(templateConfig), new HashMap<String, String>());

    // Configure stub for successful instance insertion operation.
    String instanceName1 = UUID.randomUUID().toString();
    String decoratedInstanceName1 = INSTANCE_NAME_PREFIX.unwrap().getDefaultValue() + "-" + instanceName1;
    String instanceUrl1 = TestUtils.buildInstanceUrl(PROJECT_ID, ZONE_NAME, decoratedInstanceName1);
    Compute.Instances computeInstances = mockComputeToInstances();
    Compute.Instances.Insert computeInstancesInsert1 = mockComputeInstancesInsert(computeInstances);
    Operation vmCreationOperation1 = buildOperation(ZONE_NAME, UUID.randomUUID().toString(), instanceUrl1, "PENDING");
    OngoingStubbing<Operation> ongoingInsertionStub =
        when(computeInstancesInsert1.execute()).thenReturn(vmCreationOperation1);
    Compute.ZoneOperations computeZoneOperations = mockComputeToZoneOperations();
    Compute.ZoneOperations.Get computeZoneOperationsGet1 = mock(Compute.ZoneOperations.Get.class);
    when(computeZoneOperations.get(PROJECT_ID, ZONE_NAME,
        vmCreationOperation1.getName())).thenReturn(computeZoneOperationsGet1);
    when(computeZoneOperationsGet1.execute()).then(
        new OperationAnswer(vmCreationOperation1, new String[]{"PENDING", "RUNNING", "DONE"}));

    // Configure stub for unsuccessful instance insertion operation.
    String instanceName2 = UUID.randomUUID().toString();
    String decoratedInstanceName2 = INSTANCE_NAME_PREFIX.unwrap().getDefaultValue() + "-" + instanceName2;
    String instanceUrl2 = TestUtils.buildInstanceUrl(PROJECT_ID, ZONE_NAME, decoratedInstanceName2);
    Operation vmCreationOperation2 = buildOperation(ZONE_NAME, UUID.randomUUID().toString(), instanceUrl2, "PENDING");
    ongoingInsertionStub.thenReturn(vmCreationOperation2);
    Compute.ZoneOperations.Get computeZoneOperationsGet2 = mock(Compute.ZoneOperations.Get.class);
    when(computeZoneOperations.get(PROJECT_ID, ZONE_NAME,
        vmCreationOperation2.getName())).thenReturn(computeZoneOperationsGet2);
    when(computeZoneOperationsGet2.execute()).then(
        new OperationAnswer(vmCreationOperation2, new String[]{"PENDING", "RUNNING", "DONE"},
            "SOME_ERROR_CODE", "Some error message..."));

    // Configure stub for successful instance deletion operation.
    Compute.Instances.Delete computeInstancesDelete1 =
        mockComputeInstancesDelete(computeInstances, decoratedInstanceName1);
    Operation vmDeletionOperation1 = buildOperation(ZONE_NAME, UUID.randomUUID().toString(), instanceUrl1, "PENDING");
    when(computeInstancesDelete1.execute()).thenReturn(vmDeletionOperation1);
    Compute.ZoneOperations.Get computeZoneOperationsGet3 = mock(Compute.ZoneOperations.Get.class);
    when(computeZoneOperations.get(PROJECT_ID, ZONE_NAME,
        vmDeletionOperation1.getName())).thenReturn(computeZoneOperationsGet3);
    when(computeZoneOperationsGet3.execute()).then(
        new OperationAnswer(vmDeletionOperation1, new String[]{"PENDING", "RUNNING", "DONE"}));

    // Configure stub for successful instance deletion operation.
    Compute.Instances.Delete computeInstancesDelete2 =
        mockComputeInstancesDelete(computeInstances, decoratedInstanceName2);
    Operation vmDeletionOperation2 = buildOperation(ZONE_NAME, UUID.randomUUID().toString(), instanceUrl2, "PENDING");
    when(computeInstancesDelete2.execute()).thenReturn(vmDeletionOperation2);
    Compute.ZoneOperations.Get computeZoneOperationsGet4 = mock(Compute.ZoneOperations.Get.class);
    when(computeZoneOperations.get(PROJECT_ID, ZONE_NAME,
        vmDeletionOperation2.getName())).thenReturn(computeZoneOperationsGet4);
    when(computeZoneOperationsGet4.execute()).then(
        new OperationAnswer(vmDeletionOperation2, new String[]{"PENDING", "RUNNING", "DONE"}));

    try {
      computeProvider.allocate(template, Lists.newArrayList(instanceName1, instanceName2), 2);

      fail("An exception should have been thrown when we failed to provision at least minCount instances.");
    } catch (UnrecoverableProviderException e) {
      LOG.info("Caught: " + e.getMessage());

      assertThat(e.getMessage()).isEqualTo("Problem allocating instances.");
      verifySingleError(e.getDetails(), "Some error message...");
    }

    // Verify first instance insertion call was made.
    ArgumentCaptor<Instance> insertArgumentCaptor = ArgumentCaptor.forClass(Instance.class);
    verify(computeInstances, times(2)).insert(eq(PROJECT_ID), eq(ZONE_NAME), insertArgumentCaptor.capture());
    List<Instance> insertedInstanceList = insertArgumentCaptor.getAllValues();
    Instance insertedInstance1 = insertedInstanceList.get(0);

    // Verify first instance name and metadata.
    assertThat(insertedInstance1.getName()).isEqualTo(decoratedInstanceName1);
    assertThat(insertedInstance1.getMetadata().getItems()).isEqualTo(Lists.newArrayList());

    List<AttachedDisk> attachedDiskList1 = insertedInstance1.getDisks();
    assertThat(attachedDiskList1.size()).isEqualTo(3);

    // Verify boot disk.
    verifyAttachedDiskAttributes(attachedDiskList1.get(0), true, true, null, 60L,
        TestUtils.buildImageUrl(IMAGE_PROJECT_ID, IMAGE_NAME), null, null, null);

    // Verify data disk.
    verifyAttachedDiskAttributes(attachedDiskList1.get(1), null, true,
        Urls.buildDiskTypeUrl(PROJECT_ID, ZONE_NAME, "LocalSSD"), null, null, "SCSI", "SCRATCH", null);

    // Verify data disk.
    verifyAttachedDiskAttributes(attachedDiskList1.get(2), null, true,
        Urls.buildDiskTypeUrl(PROJECT_ID, ZONE_NAME, "LocalSSD"), null, null, "SCSI", "SCRATCH", null);

    // Verify second instance insertion call was made.
    Instance insertedInstance2 = insertedInstanceList.get(1);

    // Verify second instance name and metadata.
    assertThat(insertedInstance2.getName()).isEqualTo(decoratedInstanceName2);
    assertThat(insertedInstance2.getMetadata().getItems()).isEqualTo(Lists.newArrayList());

    List<AttachedDisk> attachedDiskList2 = insertedInstance2.getDisks();
    assertThat(attachedDiskList2.size()).isEqualTo(3);

    // Verify boot disk.
    verifyAttachedDiskAttributes(attachedDiskList2.get(0), true, true, null, 60L,
        TestUtils.buildImageUrl(IMAGE_PROJECT_ID, IMAGE_NAME), null, null, null);

    // Verify data disk.
    verifyAttachedDiskAttributes(attachedDiskList2.get(1), null, true,
        Urls.buildDiskTypeUrl(PROJECT_ID, ZONE_NAME, "LocalSSD"), null, null, "SCSI", "SCRATCH", null);

    // Verify data disk.
    verifyAttachedDiskAttributes(attachedDiskList2.get(2), null, true,
        Urls.buildDiskTypeUrl(PROJECT_ID, ZONE_NAME, "LocalSSD"), null, null, "SCSI", "SCRATCH", null);

    // Verify first instance deletion call was made.
    verify(computeInstances).delete(eq(PROJECT_ID), eq(ZONE_NAME), eq(decoratedInstanceName1));

    // Verify second instance deletion call was made.
    verify(computeInstances).delete(eq(PROJECT_ID), eq(ZONE_NAME), eq(decoratedInstanceName2));
  }

  @Test
  public void testAllocate_LocalSSD_CreationFails_ReachesMinCount() throws InterruptedException, IOException {
    // Prepare configuration for resource template.
    Map<String, String> templateConfig = new HashMap<String, String>();
    templateConfig.put(IMAGE.unwrap().getConfigKey(), IMAGE_ALIAS_CENTOS);
    templateConfig.put(TYPE.unwrap().getConfigKey(), MACHINE_TYPE_NAME);
    templateConfig.put(NETWORK_NAME.unwrap().getConfigKey(), NETWORK_NAME_VALUE);
    templateConfig.put(ZONE.unwrap().getConfigKey(), ZONE_NAME);

    // Create the resource template.
    GoogleComputeInstanceTemplate template = computeProvider.createResourceTemplate("template-1",
        new SimpleConfiguration(templateConfig), new HashMap<String, String>());

    // Configure stub for successful instance insertion operation.
    String instanceName1 = UUID.randomUUID().toString();
    String decoratedInstanceName1 = INSTANCE_NAME_PREFIX.unwrap().getDefaultValue() + "-" + instanceName1;
    String instanceUrl1 = TestUtils.buildInstanceUrl(PROJECT_ID, ZONE_NAME, decoratedInstanceName1);
    Compute.Instances computeInstances = mockComputeToInstances();
    Compute.Instances.Insert computeInstancesInsert1 = mockComputeInstancesInsert(computeInstances);
    Operation vmCreationOperation1 = buildOperation(ZONE_NAME, UUID.randomUUID().toString(), instanceUrl1, "PENDING");
    OngoingStubbing<Operation> ongoingInsertionStub =
        when(computeInstancesInsert1.execute()).thenReturn(vmCreationOperation1);
    Compute.ZoneOperations computeZoneOperations = mockComputeToZoneOperations();
    Compute.ZoneOperations.Get computeZoneOperationsGet1 = mock(Compute.ZoneOperations.Get.class);
    when(computeZoneOperations.get(PROJECT_ID, ZONE_NAME,
        vmCreationOperation1.getName())).thenReturn(computeZoneOperationsGet1);
    when(computeZoneOperationsGet1.execute()).then(
        new OperationAnswer(vmCreationOperation1, new String[]{"PENDING", "RUNNING", "DONE"}));

    // Configure stub for unsuccessful instance insertion operation.
    String instanceName2 = UUID.randomUUID().toString();
    String decoratedInstanceName2 = INSTANCE_NAME_PREFIX.unwrap().getDefaultValue() + "-" + instanceName2;
    String instanceUrl2 = TestUtils.buildInstanceUrl(PROJECT_ID, ZONE_NAME, decoratedInstanceName2);
    Operation vmCreationOperation2 = buildOperation(ZONE_NAME, UUID.randomUUID().toString(), instanceUrl2, "PENDING");
    ongoingInsertionStub.thenReturn(vmCreationOperation2);
    Compute.ZoneOperations.Get computeZoneOperationsGet2 = mock(Compute.ZoneOperations.Get.class);
    when(computeZoneOperations.get(PROJECT_ID, ZONE_NAME,
        vmCreationOperation2.getName())).thenReturn(computeZoneOperationsGet2);
    when(computeZoneOperationsGet2.execute()).then(
        new OperationAnswer(vmCreationOperation2, new String[]{"PENDING", "RUNNING", "DONE"},
            "SOME_ERROR_CODE", "Some error message..."));

    computeProvider.allocate(template, Lists.newArrayList(instanceName1, instanceName2), 1);

    // Verify first instance insertion call was made.
    ArgumentCaptor<Instance> insertArgumentCaptor = ArgumentCaptor.forClass(Instance.class);
    verify(computeInstances, times(2)).insert(eq(PROJECT_ID), eq(ZONE_NAME), insertArgumentCaptor.capture());
    List<Instance> insertedInstanceList = insertArgumentCaptor.getAllValues();
    Instance insertedInstance1 = insertedInstanceList.get(0);

    // Verify first instance name and metadata.
    assertThat(insertedInstance1.getName()).isEqualTo(decoratedInstanceName1);
    assertThat(insertedInstance1.getMetadata().getItems()).isEqualTo(Lists.newArrayList());

    List<AttachedDisk> attachedDiskList1 = insertedInstance1.getDisks();
    assertThat(attachedDiskList1.size()).isEqualTo(3);

    // Verify boot disk.
    verifyAttachedDiskAttributes(attachedDiskList1.get(0), true, true, null, 60L,
        TestUtils.buildImageUrl(IMAGE_PROJECT_ID, IMAGE_NAME), null, null, null);

    // Verify data disk.
    verifyAttachedDiskAttributes(attachedDiskList1.get(1), null, true,
        Urls.buildDiskTypeUrl(PROJECT_ID, ZONE_NAME, "LocalSSD"), null, null, "SCSI", "SCRATCH", null);

    // Verify data disk.
    verifyAttachedDiskAttributes(attachedDiskList1.get(2), null, true,
        Urls.buildDiskTypeUrl(PROJECT_ID, ZONE_NAME, "LocalSSD"), null, null, "SCSI", "SCRATCH", null);

    // Verify second instance insertion call was made.
    Instance insertedInstance2 = insertedInstanceList.get(1);

    // Verify second instance name and metadata.
    assertThat(insertedInstance2.getName()).isEqualTo(decoratedInstanceName2);
    assertThat(insertedInstance2.getMetadata().getItems()).isEqualTo(Lists.newArrayList());

    List<AttachedDisk> attachedDiskList2 = insertedInstance2.getDisks();
    assertThat(attachedDiskList2.size()).isEqualTo(3);

    // Verify boot disk.
    verifyAttachedDiskAttributes(attachedDiskList2.get(0), true, true, null, 60L,
        TestUtils.buildImageUrl(IMAGE_PROJECT_ID, IMAGE_NAME), null, null, null);

    // Verify data disk.
    verifyAttachedDiskAttributes(attachedDiskList2.get(1), null, true,
        Urls.buildDiskTypeUrl(PROJECT_ID, ZONE_NAME, "LocalSSD"), null, null, "SCSI", "SCRATCH", null);

    // Verify data disk.
    verifyAttachedDiskAttributes(attachedDiskList2.get(2), null, true,
        Urls.buildDiskTypeUrl(PROJECT_ID, ZONE_NAME, "LocalSSD"), null, null, "SCSI", "SCRATCH", null);

    // NPE would be thrown (due to lack of mocks) if the compute provider attempted actual deletion calls against GCE.
    // If no NPE's are thrown, the test is a success.
  }

  @Test
  public void testAllocate_Standard() throws InterruptedException, IOException {
    // Prepare configuration for resource template.
    Map<String, String> templateConfig = new HashMap<String, String>();
    templateConfig.put(IMAGE.unwrap().getConfigKey(), IMAGE_ALIAS_CENTOS);
    templateConfig.put(TYPE.unwrap().getConfigKey(), MACHINE_TYPE_NAME);
    templateConfig.put(NETWORK_NAME.unwrap().getConfigKey(), NETWORK_NAME_VALUE);
    templateConfig.put(ZONE.unwrap().getConfigKey(), ZONE_NAME);
    templateConfig.put(DATA_DISK_COUNT.unwrap().getConfigKey(), "1");
    templateConfig.put(DATA_DISK_TYPE.unwrap().getConfigKey(), "Standard");
    templateConfig.put(DATA_DISK_SIZE_GB.unwrap().getConfigKey(), "250");

    // Create the resource template.
    GoogleComputeInstanceTemplate template = computeProvider.createResourceTemplate("template-1",
        new SimpleConfiguration(templateConfig), new HashMap<String, String>());
    String instanceName = UUID.randomUUID().toString();
    String decoratedInstanceName = INSTANCE_NAME_PREFIX.unwrap().getDefaultValue() + "-" + instanceName;
    String instanceUrl = TestUtils.buildInstanceUrl(PROJECT_ID, ZONE_NAME, decoratedInstanceName);

    // Configure stub for successful disk insertion operation.
    String diskName = INSTANCE_NAME_PREFIX.unwrap().getDefaultValue() + "-" + instanceName + "-pd-0";
    String diskUrl = Urls.buildDiskUrl(PROJECT_ID, ZONE_NAME, diskName);
    Compute.Disks computeDisks = mockComputeToDisks();
    Compute.Disks.Insert computeDisksInsert = mockComputeDisksInsert(computeDisks);
    Operation diskCreationOperation = buildOperation(ZONE_NAME, UUID.randomUUID().toString(), diskUrl, "PENDING");
    when(computeDisksInsert.execute()).thenReturn(diskCreationOperation);
    Compute.ZoneOperations computeZoneOperations = mockComputeToZoneOperations();
    Compute.ZoneOperations.Get computeZoneOperationsGet1 = mock(Compute.ZoneOperations.Get.class);
    when(computeZoneOperations.get(PROJECT_ID, ZONE_NAME,
        diskCreationOperation.getName())).thenReturn(computeZoneOperationsGet1);
    when(computeZoneOperationsGet1.execute()).then(
        new OperationAnswer(diskCreationOperation, new String[]{"PENDING", "RUNNING", "DONE"}));

    // Configure stub for successful instance insertion operation.
    Compute.Instances computeInstances = mockComputeToInstances();
    Compute.Instances.Insert computeInstancesInsert = mockComputeInstancesInsert(computeInstances);
    Operation vmCreationOperation = buildOperation(ZONE_NAME, UUID.randomUUID().toString(), instanceUrl, "PENDING");
    when(computeInstancesInsert.execute()).thenReturn(vmCreationOperation);
    Compute.ZoneOperations.Get computeZoneOperationsGet2 = mock(Compute.ZoneOperations.Get.class);
    when(computeZoneOperations.get(PROJECT_ID, ZONE_NAME,
        vmCreationOperation.getName())).thenReturn(computeZoneOperationsGet2);
    when(computeZoneOperationsGet2.execute()).then(
        new OperationAnswer(vmCreationOperation, new String[]{"PENDING", "RUNNING", "DONE"}));

    computeProvider.allocate(template, Lists.newArrayList(instanceName), 1);

    // Verify persistent disk insertion call was made.
    ArgumentCaptor<Disk> argumentCaptor1 = ArgumentCaptor.forClass(Disk.class);
    verify(computeDisks).insert(eq(PROJECT_ID), eq(ZONE_NAME), argumentCaptor1.capture());
    Disk insertedDisk = argumentCaptor1.getValue();

    // Verify disk name, size and type.
    assertThat(insertedDisk.getName()).isEqualTo(diskName);
    assertThat(insertedDisk.getSizeGb()).isEqualTo(250);
    assertThat(insertedDisk.getType()).isEqualTo(Urls.buildDiskTypeUrl(PROJECT_ID, ZONE_NAME, "Standard"));

    // Verify instance insertion call was made.
    ArgumentCaptor<Instance> argumentCaptor2 = ArgumentCaptor.forClass(Instance.class);
    verify(computeInstances).insert(eq(PROJECT_ID), eq(ZONE_NAME), argumentCaptor2.capture());
    Instance insertedInstance = argumentCaptor2.getValue();

    // Verify instance name and metadata.
    assertThat(insertedInstance.getName()).isEqualTo(decoratedInstanceName);
    assertThat(insertedInstance.getMetadata().getItems()).isEqualTo(Lists.newArrayList());

    List<AttachedDisk> attachedDiskList = insertedInstance.getDisks();
    assertThat(attachedDiskList.size()).isEqualTo(2);

    // Verify boot disk.
    verifyAttachedDiskAttributes(attachedDiskList.get(0), true, true, null, 60L,
        TestUtils.buildImageUrl(IMAGE_PROJECT_ID, IMAGE_NAME), null, null, null);

    // Verify data disk.
    verifyAttachedDiskAttributes(attachedDiskList.get(1), null, true, null, null,
        null, null, "PERSISTENT", diskUrl);
  }

  @Test
  public void testAllocate_SSD_CreationFails_BelowMinCount() throws InterruptedException, IOException {
    // Prepare configuration for resource template.
    Map<String, String> templateConfig = new HashMap<String, String>();
    templateConfig.put(IMAGE.unwrap().getConfigKey(), IMAGE_ALIAS_CENTOS);
    templateConfig.put(TYPE.unwrap().getConfigKey(), MACHINE_TYPE_NAME);
    templateConfig.put(NETWORK_NAME.unwrap().getConfigKey(), NETWORK_NAME_VALUE);
    templateConfig.put(ZONE.unwrap().getConfigKey(), ZONE_NAME);
    templateConfig.put(DATA_DISK_COUNT.unwrap().getConfigKey(), "1");
    templateConfig.put(DATA_DISK_TYPE.unwrap().getConfigKey(), "SSD");
    templateConfig.put(DATA_DISK_SIZE_GB.unwrap().getConfigKey(), "500");

    // Create the resource template.
    GoogleComputeInstanceTemplate template = computeProvider.createResourceTemplate("template-1",
        new SimpleConfiguration(templateConfig), new HashMap<String, String>());
    String instanceName1 = UUID.randomUUID().toString();
    String decoratedInstanceName1 = INSTANCE_NAME_PREFIX.unwrap().getDefaultValue() + "-" + instanceName1;
    String instanceUrl1 = TestUtils.buildInstanceUrl(PROJECT_ID, ZONE_NAME, decoratedInstanceName1);
    String instanceName2 = UUID.randomUUID().toString();
    String decoratedInstanceName2 = INSTANCE_NAME_PREFIX.unwrap().getDefaultValue() + "-" + instanceName2;
    String instanceUrl2 = TestUtils.buildInstanceUrl(PROJECT_ID, ZONE_NAME, decoratedInstanceName2);

    // Configure stub for first successful disk insertion operation.
    String diskName1 = INSTANCE_NAME_PREFIX.unwrap().getDefaultValue() + "-" + instanceName1 + "-pd-0";
    String diskUrl1 = Urls.buildDiskUrl(PROJECT_ID, ZONE_NAME, diskName1);
    Compute.Disks computeDisks = mockComputeToDisks();
    Compute.Disks.Insert computeDisksInsert = mockComputeDisksInsert(computeDisks);
    Operation diskCreationOperation1 = buildOperation(ZONE_NAME, UUID.randomUUID().toString(), diskUrl1, "PENDING");
    OngoingStubbing<Operation> ongoingDiskStub =
        when(computeDisksInsert.execute()).thenReturn(diskCreationOperation1);
    Compute.ZoneOperations computeZoneOperations = mockComputeToZoneOperations();
    Compute.ZoneOperations.Get computeZoneOperationsGet1 = mock(Compute.ZoneOperations.Get.class);
    when(computeZoneOperations.get(PROJECT_ID, ZONE_NAME,
        diskCreationOperation1.getName())).thenReturn(computeZoneOperationsGet1);
    when(computeZoneOperationsGet1.execute()).then(
        new OperationAnswer(diskCreationOperation1, new String[]{"PENDING", "RUNNING", "DONE"}));

    // Configure stub for second successful disk insertion operation.
    String diskName2 = INSTANCE_NAME_PREFIX.unwrap().getDefaultValue() + "-" + instanceName2 + "-pd-0";
    String diskUrl2 = Urls.buildDiskUrl(PROJECT_ID, ZONE_NAME, diskName2);
    Operation diskCreationOperation2 = buildOperation(ZONE_NAME, UUID.randomUUID().toString(), diskUrl2, "PENDING");
    ongoingDiskStub.thenReturn(diskCreationOperation2);
    Compute.ZoneOperations.Get computeZoneOperationsGet2 = mock(Compute.ZoneOperations.Get.class);
    when(computeZoneOperations.get(PROJECT_ID, ZONE_NAME,
        diskCreationOperation2.getName())).thenReturn(computeZoneOperationsGet2);
    when(computeZoneOperationsGet2.execute()).then(
        new OperationAnswer(diskCreationOperation2, new String[]{"PENDING", "RUNNING", "DONE"}));

    // Configure stub for successful instance insertion operation.
    Compute.Instances computeInstances = mockComputeToInstances();
    Compute.Instances.Insert computeInstancesInsert1 = mockComputeInstancesInsert(computeInstances);
    Operation vmCreationOperation1 = buildOperation(ZONE_NAME, UUID.randomUUID().toString(), instanceUrl1, "PENDING");
    OngoingStubbing<Operation> ongoingInsertionStub =
        when(computeInstancesInsert1.execute()).thenReturn(vmCreationOperation1);
    Compute.ZoneOperations.Get computeZoneOperationsGet3 = mock(Compute.ZoneOperations.Get.class);
    when(computeZoneOperations.get(PROJECT_ID, ZONE_NAME,
        vmCreationOperation1.getName())).thenReturn(computeZoneOperationsGet3);
    when(computeZoneOperationsGet3.execute()).then(
        new OperationAnswer(vmCreationOperation1, new String[]{"PENDING", "RUNNING", "DONE"}));

    // Configure stub for unsuccessful instance insertion operation.
    Operation vmCreationOperation2 = buildOperation(ZONE_NAME, UUID.randomUUID().toString(), instanceUrl2, "PENDING");
    ongoingInsertionStub.thenReturn(vmCreationOperation2);
    Compute.ZoneOperations.Get computeZoneOperationsGet4 = mock(Compute.ZoneOperations.Get.class);
    when(computeZoneOperations.get(PROJECT_ID, ZONE_NAME,
        vmCreationOperation2.getName())).thenReturn(computeZoneOperationsGet4);
    when(computeZoneOperationsGet4.execute()).then(
        new OperationAnswer(vmCreationOperation2, new String[]{"PENDING", "RUNNING", "DONE"},
            "SOME_ERROR_CODE", "Some error message..."));

    // Now configure the expected tearDown operations.

    // Configure stub for successful instance retrieval with attached persistent disk.
    Compute.Instances.Get computeInstancesGet1 = mockComputeInstancesGet(computeInstances, decoratedInstanceName1);
    Instance instance1 = new Instance();
    AttachedDisk attachedDisk1 = new AttachedDisk();
    attachedDisk1.setSource(diskUrl1);
    List<AttachedDisk> diskList1 = Lists.newArrayList(attachedDisk1);
    instance1.setDisks(diskList1);
    when(computeInstancesGet1.execute()).thenReturn(instance1);

    // Configure stub for unsuccessful instance retrieval (throws 404).
    Compute.Instances.Get computeInstancesGet2 = mockComputeInstancesGet(computeInstances, decoratedInstanceName2);
    GoogleJsonResponseException exception =
        GoogleJsonResponseExceptionFactoryTesting.newMock(new MockJsonFactory(), 404, "not found");
    when(computeInstancesGet2.execute()).thenThrow(exception);

    // Configure stub for successful instance deletion operation.
    Compute.Instances.Delete computeInstancesDelete1 =
        mockComputeInstancesDelete(computeInstances, decoratedInstanceName1);
    Operation vmDeletionOperation1 = buildOperation(ZONE_NAME, UUID.randomUUID().toString(), instanceUrl1, "PENDING");
    when(computeInstancesDelete1.execute()).thenReturn(vmDeletionOperation1);
    Compute.ZoneOperations.Get computeZoneOperationsGet5 = mock(Compute.ZoneOperations.Get.class);
    when(computeZoneOperations.get(PROJECT_ID, ZONE_NAME,
        vmDeletionOperation1.getName())).thenReturn(computeZoneOperationsGet5);
    when(computeZoneOperationsGet5.execute()).then(
        new OperationAnswer(vmDeletionOperation1, new String[]{"PENDING", "RUNNING", "DONE"}));

    // Configure stub for successful disk deletion operation.
    // The first disk was attached to the first instance, so we rely on auto-delete for that one.
    // The second disk was never attached to an instance since the instance creation failed. So it
    // must be explicitly deleted.
    Compute.Disks.Delete computeDisksDelete = mockComputeDisksDelete(computeDisks, diskName2);
    Operation diskDeletionOperation = buildOperation(ZONE_NAME, UUID.randomUUID().toString(), diskUrl2, "PENDING");
    when(computeDisksDelete.execute()).thenReturn(diskDeletionOperation);
    Compute.ZoneOperations.Get computeZoneOperationsGet7 = mock(Compute.ZoneOperations.Get.class);
    when(computeZoneOperations.get(PROJECT_ID, ZONE_NAME,
        diskDeletionOperation.getName())).thenReturn(computeZoneOperationsGet7);
    when(computeZoneOperationsGet7.execute()).then(
        new OperationAnswer(diskDeletionOperation, new String[]{"PENDING", "RUNNING", "DONE"}));

    try {
      computeProvider.allocate(template, Lists.newArrayList(instanceName1, instanceName2), 2);

      fail("An exception should have been thrown when we failed to provision at least minCount instances.");
    } catch (UnrecoverableProviderException e) {
      LOG.info("Caught: " + e.getMessage());

      assertThat(e.getMessage()).isEqualTo("Problem allocating instances.");
      verifySingleError(e.getDetails(), "Some error message...");
    }

    // Verify persistent disk insertion call was made.
    ArgumentCaptor<Disk> argumentCaptor1 = ArgumentCaptor.forClass(Disk.class);
    verify(computeDisks, times(2)).insert(eq(PROJECT_ID), eq(ZONE_NAME), argumentCaptor1.capture());
    List<Disk> insertedDisks = argumentCaptor1.getAllValues();
    Disk insertedDisk1 = insertedDisks.get(0);

    // Verify first disk name, size and type.
    assertThat(insertedDisk1.getName()).isEqualTo(diskName1);
    assertThat(insertedDisk1.getSizeGb()).isEqualTo(500);
    assertThat(insertedDisk1.getType()).isEqualTo(Urls.buildDiskTypeUrl(PROJECT_ID, ZONE_NAME, "SSD"));

    // Verify second persistent disk insertion call was made.
    Disk insertedDisk2 = insertedDisks.get(1);

    // Verify second disk name, size and type.
    assertThat(insertedDisk2.getName()).isEqualTo(diskName2);
    assertThat(insertedDisk2.getSizeGb()).isEqualTo(500);
    assertThat(insertedDisk2.getType()).isEqualTo(Urls.buildDiskTypeUrl(PROJECT_ID, ZONE_NAME, "SSD"));

    // Verify first instance insertion call was made.
    ArgumentCaptor<Instance> argumentCaptor2 = ArgumentCaptor.forClass(Instance.class);
    verify(computeInstances, times(2)).insert(eq(PROJECT_ID), eq(ZONE_NAME), argumentCaptor2.capture());
    List<Instance> insertedInstanceList = argumentCaptor2.getAllValues();
    Instance insertedInstance1 = insertedInstanceList.get(0);

    // Verify first instance name and metadata.
    assertThat(insertedInstance1.getName()).isEqualTo(decoratedInstanceName1);
    assertThat(insertedInstance1.getMetadata().getItems()).isEqualTo(Lists.newArrayList());

    List<AttachedDisk> attachedDiskList1 = insertedInstance1.getDisks();
    assertThat(attachedDiskList1.size()).isEqualTo(2);

    // Verify boot disk.
    verifyAttachedDiskAttributes(attachedDiskList1.get(0), true, true, null, 60L,
        TestUtils.buildImageUrl(IMAGE_PROJECT_ID, IMAGE_NAME), null, null, null);

    // Verify data disk.
    verifyAttachedDiskAttributes(attachedDiskList1.get(1), null, true, null, null,
        null, null, "PERSISTENT", diskUrl1);

    // Verify second instance insertion call was made.
    Instance insertedInstance2 = insertedInstanceList.get(1);

    // Verify second instance name and metadata.
    assertThat(insertedInstance2.getName()).isEqualTo(decoratedInstanceName2);
    assertThat(insertedInstance2.getMetadata().getItems()).isEqualTo(Lists.newArrayList());

    List<AttachedDisk> attachedDiskList2 = insertedInstance2.getDisks();
    assertThat(attachedDiskList2.size()).isEqualTo(2);

    // Verify boot disk.
    verifyAttachedDiskAttributes(attachedDiskList2.get(0), true, true, null, 60L,
        TestUtils.buildImageUrl(IMAGE_PROJECT_ID, IMAGE_NAME), null, null, null);

    // Verify data disk.
    verifyAttachedDiskAttributes(attachedDiskList2.get(1), null, true, null, null,
        null, null, "PERSISTENT", diskUrl2);

    // Verify disk deletion call was made (of disk two).
    ArgumentCaptor<String> argumentCaptor3 = ArgumentCaptor.forClass(String.class);
    verify(computeDisks).delete(eq(PROJECT_ID), eq(ZONE_NAME), argumentCaptor3.capture());
    String deletedDiskName = argumentCaptor3.getValue();
    assertThat(deletedDiskName).isEqualTo(diskName2);

    // Verify instance deletion call was made (of instance one).
    verify(computeInstances).delete(eq(PROJECT_ID), eq(ZONE_NAME), eq(decoratedInstanceName1));
  }

  @Test
  public void testFind() throws InterruptedException, IOException {
    // Prepare configuration for resource template.
    Map<String, String> templateConfig = new HashMap<String, String>();
    templateConfig.put(ZONE.unwrap().getConfigKey(), ZONE_NAME);

    // Create the resource template.
    GoogleComputeInstanceTemplate template = computeProvider.createResourceTemplate("template-1",
        new SimpleConfiguration(templateConfig), new HashMap<String, String>());

    String instanceName1 = UUID.randomUUID().toString();
    String decoratedInstanceName1 = INSTANCE_NAME_PREFIX.unwrap().getDefaultValue() + "-" + instanceName1;
    String instanceName2 = UUID.randomUUID().toString();
    String decoratedInstanceName2 = INSTANCE_NAME_PREFIX.unwrap().getDefaultValue() + "-" + instanceName2;
    List<String> instanceIds = Lists.newArrayList(instanceName1, instanceName2);

    // Configure stub for first successful instance retrieval.
    Compute.Instances computeInstances = mockComputeToInstances();
    Compute.Instances.Get computeInstancesGet1 = mockComputeInstancesGet(computeInstances, decoratedInstanceName1);
    Instance instance1 = new Instance();

    // Configure boot disk.
    AttachedDisk attachedDisk1 = new AttachedDisk();
    String diskName1 = UUID.randomUUID().toString();
    String diskUrl1 = Urls.buildDiskUrl(PROJECT_ID, ZONE_NAME, diskName1);
    attachedDisk1.setBoot(true);
    attachedDisk1.setSource(diskUrl1);
    List<AttachedDisk> diskList1 = Lists.newArrayList(attachedDisk1);
    instance1.setDisks(diskList1);

    // Configure network interface.
    NetworkInterface networkInterface1 = new NetworkInterface();
    networkInterface1.setNetworkIP("1.2.3.4");
    List<NetworkInterface> networkInterfaceList1 = Lists.newArrayList(networkInterface1);
    instance1.setNetworkInterfaces(networkInterfaceList1);

    when(computeInstancesGet1.execute()).thenReturn(instance1);

    // Configure stub for second successful instance retrieval.
    Compute.Instances.Get computeInstancesGet2 = mockComputeInstancesGet(computeInstances, decoratedInstanceName2);
    Instance instance2 = new Instance();

    // Configure boot disk.
    AttachedDisk attachedDisk2 = new AttachedDisk();
    String diskName2 = UUID.randomUUID().toString();
    String diskUrl2 = Urls.buildDiskUrl(PROJECT_ID, ZONE_NAME, diskName2);
    attachedDisk2.setBoot(true);
    attachedDisk2.setSource(diskUrl2);
    List<AttachedDisk> diskList2 = Lists.newArrayList(attachedDisk2);
    instance2.setDisks(diskList2);

    // Configure network interface.
    NetworkInterface networkInterface2 = new NetworkInterface();
    networkInterface2.setNetworkIP("5.6.7.8");
    List<NetworkInterface> networkInterfaceList2 = Lists.newArrayList(networkInterface2);
    instance2.setNetworkInterfaces(networkInterfaceList2);

    when(computeInstancesGet2.execute()).thenReturn(instance2);

    // Configure stub for first successful boot disk retrieval.
    Compute.Disks computeDisks = mockComputeToDisks();
    Compute.Disks.Get computeDisksGet1 = mockComputeDisksGet(computeDisks, diskName1);
    Disk bootDisk1 = new Disk();
    bootDisk1.setSourceImage(diskUrl1);
    when(computeDisksGet1.execute()).thenReturn(bootDisk1);

    // Configure stub for second successful boot disk retrieval.
    Compute.Disks.Get computeDisksGet2 = mockComputeDisksGet(computeDisks, diskName2);
    Disk bootDisk2 = new Disk();
    bootDisk2.setSourceImage(diskUrl2);
    when(computeDisksGet2.execute()).thenReturn(bootDisk2);

    Collection<GoogleComputeInstance> foundInstances = computeProvider.find(template, instanceIds);

    // Verify that both of the two requested instances were returned.
    assertThat(foundInstances.size()).isEqualTo(2);

    // Verify the properties of the first returned instance.
    Iterator<GoogleComputeInstance> instanceIterator = foundInstances.iterator();
    GoogleComputeInstance foundInstance1 = instanceIterator.next();
    assertThat(foundInstance1.getId()).isEqualTo(instanceName1);
    assertThat(foundInstance1.getBootDisk().getSourceImage()).isEqualTo(diskUrl1);
    assertThat(foundInstance1.getPrivateIpAddress().getHostAddress()).isEqualTo("1.2.3.4");

    // Verify the properties of the second returned instance.
    GoogleComputeInstance foundInstance2 = instanceIterator.next();
    assertThat(foundInstance2.getId()).isEqualTo(instanceName2);
    assertThat(foundInstance2.getBootDisk().getSourceImage()).isEqualTo(diskUrl2);
    assertThat(foundInstance2.getPrivateIpAddress().getHostAddress()).isEqualTo("5.6.7.8");
  }

  @Test
  public void testFind_PartialSuccess() throws InterruptedException, IOException {
    // Prepare configuration for resource template.
    Map<String, String> templateConfig = new HashMap<String, String>();
    templateConfig.put(ZONE.unwrap().getConfigKey(), ZONE_NAME);

    // Create the resource template.
    GoogleComputeInstanceTemplate template = computeProvider.createResourceTemplate("template-1",
        new SimpleConfiguration(templateConfig), new HashMap<String, String>());

    String instanceName1 = UUID.randomUUID().toString();
    String decoratedInstanceName1 = INSTANCE_NAME_PREFIX.unwrap().getDefaultValue() + "-" + instanceName1;
    String instanceName2 = UUID.randomUUID().toString();
    String decoratedInstanceName2 = INSTANCE_NAME_PREFIX.unwrap().getDefaultValue() + "-" + instanceName2;
    List<String> instanceIds = Lists.newArrayList(instanceName1, instanceName2);

    // Configure stub for successful instance retrieval.
    Compute.Instances computeInstances = mockComputeToInstances();
    Compute.Instances.Get computeInstancesGet1 = mockComputeInstancesGet(computeInstances, decoratedInstanceName1);
    Instance instance1 = new Instance();

    // Configure boot disk.
    AttachedDisk attachedDisk1 = new AttachedDisk();
    String diskName = UUID.randomUUID().toString();
    String diskUrl = Urls.buildDiskUrl(PROJECT_ID, ZONE_NAME, diskName);
    attachedDisk1.setBoot(true);
    attachedDisk1.setSource(diskUrl);
    List<AttachedDisk> diskList1 = Lists.newArrayList(attachedDisk1);
    instance1.setDisks(diskList1);

    // Configure network interface.
    NetworkInterface networkInterface = new NetworkInterface();
    networkInterface.setNetworkIP("1.2.3.4");
    List<NetworkInterface> networkInterfaceList = Lists.newArrayList(networkInterface);
    instance1.setNetworkInterfaces(networkInterfaceList);

    when(computeInstancesGet1.execute()).thenReturn(instance1);

    // Configure stub for unsuccessful instance retrieval (throws 404).
    Compute.Instances.Get computeInstancesGet2 = mockComputeInstancesGet(computeInstances, decoratedInstanceName2);
    GoogleJsonResponseException exception =
        GoogleJsonResponseExceptionFactoryTesting.newMock(new MockJsonFactory(), 404, "not found");
    when(computeInstancesGet2.execute()).thenThrow(exception);

    // Configure stub for successful boot disk retrieval.
    Compute.Disks computeDisks = mockComputeToDisks();
    Compute.Disks.Get computeDisksGet = mockComputeDisksGet(computeDisks, diskName);
    Disk bootDisk = new Disk();
    bootDisk.setSourceImage(diskUrl);
    when(computeDisksGet.execute()).thenReturn(bootDisk);

    Collection<GoogleComputeInstance> foundInstances = computeProvider.find(template, instanceIds);

    // Verify that exactly one of the two requested instances was returned.
    assertThat(foundInstances.size()).isEqualTo(1);

    // Verify the properties of the returned instance.
    GoogleComputeInstance foundInstance = foundInstances.iterator().next();
    assertThat(foundInstance.getId()).isEqualTo(instanceName1);
    assertThat(foundInstance.getBootDisk().getSourceImage()).isEqualTo(diskUrl);
    assertThat(foundInstance.getPrivateIpAddress().getHostAddress()).isEqualTo("1.2.3.4");
  }

  @Test
  public void testFind_InvalidPrefix() throws InterruptedException, IOException {
    // Prepare configuration for resource template.
    Map<String, String> templateConfig = new HashMap<String, String>();
    templateConfig.put(ZONE.unwrap().getConfigKey(), ZONE_NAME);
    templateConfig.put(INSTANCE_NAME_PREFIX.unwrap().getConfigKey(), INVALID_INSTANCE_NAME_PREFIX);

    // Create the resource template.
    GoogleComputeInstanceTemplate template = computeProvider.createResourceTemplate("template-1",
            new SimpleConfiguration(templateConfig), new HashMap<String, String>());

    String instanceName1 = UUID.randomUUID().toString();
    String instanceName2 = UUID.randomUUID().toString();
    List<String> instanceIds = Lists.newArrayList(instanceName1, instanceName2);

    // NPE would be thrown (due to lack of mocks) if the compute provider attempted actual calls against GCE.
    // When the instance name prefix is deemed invalid, no calls are attempted against GCE.
    Collection<GoogleComputeInstance> foundInstances = computeProvider.find(template, instanceIds);

    // Verify that no instances were returned.
    assertThat(foundInstances.size()).isEqualTo(0);
  }

  @Test
  public void testGetInstanceState() throws InterruptedException, IOException {
    // Prepare configuration for resource template.
    Map<String, String> templateConfig = new HashMap<String, String>();
    templateConfig.put(ZONE.unwrap().getConfigKey(), ZONE_NAME);

    // Create the resource template.
    GoogleComputeInstanceTemplate template = computeProvider.createResourceTemplate("template-1",
        new SimpleConfiguration(templateConfig), new HashMap<String, String>());

    String instanceName1 = UUID.randomUUID().toString();
    String decoratedInstanceName1 = INSTANCE_NAME_PREFIX.unwrap().getDefaultValue() + "-" + instanceName1;
    String instanceName2 = UUID.randomUUID().toString();
    String decoratedInstanceName2 = INSTANCE_NAME_PREFIX.unwrap().getDefaultValue() + "-" + instanceName2;
    List<String> instanceIds = Lists.newArrayList(instanceName1, instanceName2);

    // Configure stub for first successful instance retrieval.
    Compute.Instances computeInstances = mockComputeToInstances();
    Compute.Instances.Get computeInstancesGet1 = mockComputeInstancesGet(computeInstances, decoratedInstanceName1);
    Instance instance1 = new Instance();
    instance1.setStatus("PROVISIONING");
    when(computeInstancesGet1.execute()).thenReturn(instance1);

    // Configure stub for second successful instance retrieval.
    Compute.Instances.Get computeInstancesGet2 = mockComputeInstancesGet(computeInstances, decoratedInstanceName2);
    Instance instance2 = new Instance();
    instance2.setStatus("RUNNING");
    when(computeInstancesGet2.execute()).thenReturn(instance2);

    Map<String, InstanceState> instanceStates = computeProvider.getInstanceState(template, instanceIds);

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
    templateConfig.put(ZONE.unwrap().getConfigKey(), ZONE_NAME);

    // Create the resource template.
    GoogleComputeInstanceTemplate template = computeProvider.createResourceTemplate("template-1",
        new SimpleConfiguration(templateConfig), new HashMap<String, String>());

    String instanceName1 = UUID.randomUUID().toString();
    String decoratedInstanceName1 = INSTANCE_NAME_PREFIX.unwrap().getDefaultValue() + "-" + instanceName1;
    String instanceName2 = UUID.randomUUID().toString();
    String decoratedInstanceName2 = INSTANCE_NAME_PREFIX.unwrap().getDefaultValue() + "-" + instanceName2;
    List<String> instanceIds = Lists.newArrayList(instanceName1, instanceName2);

    // Configure stub for first successful instance retrieval.
    Compute.Instances computeInstances = mockComputeToInstances();
    Compute.Instances.Get computeInstancesGet1 = mockComputeInstancesGet(computeInstances, decoratedInstanceName1);
    Instance instance1 = new Instance();
    instance1.setStatus("STAGING");
    when(computeInstancesGet1.execute()).thenReturn(instance1);

    // Configure stub for unsuccessful instance retrieval (throws 404).
    Compute.Instances.Get computeInstancesGet2 = mockComputeInstancesGet(computeInstances, decoratedInstanceName2);
    GoogleJsonResponseException exception =
        GoogleJsonResponseExceptionFactoryTesting.newMock(new MockJsonFactory(), 404, "not found");
    when(computeInstancesGet2.execute()).thenThrow(exception);

    Map<String, InstanceState> instanceStates = computeProvider.getInstanceState(template, instanceIds);

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
    templateConfig.put(ZONE.unwrap().getConfigKey(), ZONE_NAME);
    templateConfig.put(INSTANCE_NAME_PREFIX.unwrap().getConfigKey(), INVALID_INSTANCE_NAME_PREFIX);

    // Create the resource template.
    GoogleComputeInstanceTemplate template = computeProvider.createResourceTemplate("template-1",
            new SimpleConfiguration(templateConfig), new HashMap<String, String>());

    String instanceName1 = UUID.randomUUID().toString();
    String instanceName2 = UUID.randomUUID().toString();
    List<String> instanceIds = Lists.newArrayList(instanceName1, instanceName2);

    // NPE would be thrown (due to lack of mocks) if the compute provider attempted actual calls against GCE.
    // When the instance name prefix is deemed invalid, no calls are attempted against GCE.
    Map<String, InstanceState> instanceStates = computeProvider.getInstanceState(template, instanceIds);

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
    templateConfig.put(ZONE.unwrap().getConfigKey(), ZONE_NAME);

    // Create the resource template.
    GoogleComputeInstanceTemplate template = computeProvider.createResourceTemplate("template-1",
        new SimpleConfiguration(templateConfig), new HashMap<String, String>());

    String instanceName1 = UUID.randomUUID().toString();
    String decoratedInstanceName1 = INSTANCE_NAME_PREFIX.unwrap().getDefaultValue() + "-" + instanceName1;
    String instanceUrl1 = TestUtils.buildInstanceUrl(PROJECT_ID, ZONE_NAME, decoratedInstanceName1);
    String instanceName2 = UUID.randomUUID().toString();
    String decoratedInstanceName2 = INSTANCE_NAME_PREFIX.unwrap().getDefaultValue() + "-" + instanceName2;
    String instanceUrl2 = TestUtils.buildInstanceUrl(PROJECT_ID, ZONE_NAME, decoratedInstanceName2);
    List<String> instanceIds = Lists.newArrayList(instanceName1, instanceName2);

    // Configure stub for successful instance deletion operation.
    Compute.Instances computeInstances = mockComputeToInstances();
    Compute.Instances.Delete computeInstancesDelete1 =
        mockComputeInstancesDelete(computeInstances, decoratedInstanceName1);
    Operation vmDeletionOperation1 = buildOperation(ZONE_NAME, UUID.randomUUID().toString(), instanceUrl1, "PENDING");
    when(computeInstancesDelete1.execute()).thenReturn(vmDeletionOperation1);
    Compute.ZoneOperations computeZoneOperations = mockComputeToZoneOperations();
    Compute.ZoneOperations.Get computeZoneOperationsGet1 = mock(Compute.ZoneOperations.Get.class);
    when(computeZoneOperations.get(PROJECT_ID, ZONE_NAME,
        vmDeletionOperation1.getName())).thenReturn(computeZoneOperationsGet1);
    when(computeZoneOperationsGet1.execute()).then(
        new OperationAnswer(vmDeletionOperation1, new String[]{"PENDING", "RUNNING", "DONE"}));

    // Configure stub for successful instance deletion operation.
    Compute.Instances.Delete computeInstancesDelete2 =
        mockComputeInstancesDelete(computeInstances, decoratedInstanceName2);
    Operation vmDeletionOperation2 = buildOperation(ZONE_NAME, UUID.randomUUID().toString(), instanceUrl2, "PENDING");
    when(computeInstancesDelete2.execute()).thenReturn(vmDeletionOperation2);
    Compute.ZoneOperations.Get computeZoneOperationsGet2 = mock(Compute.ZoneOperations.Get.class);
    when(computeZoneOperations.get(PROJECT_ID, ZONE_NAME,
        vmDeletionOperation2.getName())).thenReturn(computeZoneOperationsGet2);
    when(computeZoneOperationsGet2.execute()).then(
        new OperationAnswer(vmDeletionOperation2, new String[]{"PENDING", "RUNNING", "DONE"}));

    computeProvider.delete(template, instanceIds);

    // Verify first instance deletion call was made.
    verify(computeInstances).delete(eq(PROJECT_ID), eq(ZONE_NAME), eq(decoratedInstanceName1));

    // Verify second instance deletion call was made.
    verify(computeInstances).delete(eq(PROJECT_ID), eq(ZONE_NAME), eq(decoratedInstanceName2));
  }

  @Test
  public void testDelete_PartialSuccess() throws InterruptedException, IOException {
    // Prepare configuration for resource template.
    Map<String, String> templateConfig = new HashMap<String, String>();
    templateConfig.put(ZONE.unwrap().getConfigKey(), ZONE_NAME);

    // Create the resource template.
    GoogleComputeInstanceTemplate template = computeProvider.createResourceTemplate("template-1",
        new SimpleConfiguration(templateConfig), new HashMap<String, String>());

    String instanceName1 = UUID.randomUUID().toString();
    String decoratedInstanceName1 = INSTANCE_NAME_PREFIX.unwrap().getDefaultValue() + "-" + instanceName1;
    String instanceUrl1 = TestUtils.buildInstanceUrl(PROJECT_ID, ZONE_NAME, decoratedInstanceName1);
    String instanceName2 = UUID.randomUUID().toString();
    String decoratedInstanceName2 = INSTANCE_NAME_PREFIX.unwrap().getDefaultValue() + "-" + instanceName2;
    List<String> instanceIds = Lists.newArrayList(instanceName1, instanceName2);

    // Configure stub for successful instance deletion operation.
    Compute.Instances computeInstances = mockComputeToInstances();
    Compute.Instances.Delete computeInstancesDelete1 =
        mockComputeInstancesDelete(computeInstances, decoratedInstanceName1);
    Operation vmDeletionOperation1 = buildOperation(ZONE_NAME, UUID.randomUUID().toString(), instanceUrl1, "PENDING");
    when(computeInstancesDelete1.execute()).thenReturn(vmDeletionOperation1);
    Compute.ZoneOperations computeZoneOperations = mockComputeToZoneOperations();
    Compute.ZoneOperations.Get computeZoneOperationsGet1 = mock(Compute.ZoneOperations.Get.class);
    when(computeZoneOperations.get(PROJECT_ID, ZONE_NAME,
        vmDeletionOperation1.getName())).thenReturn(computeZoneOperationsGet1);
    when(computeZoneOperationsGet1.execute()).then(
        new OperationAnswer(vmDeletionOperation1, new String[]{"PENDING", "RUNNING", "DONE"}));

    // Configure stub for unsuccessful instance deletion operation.
    Compute.Instances.Delete computeInstancesDelete2 =
        mockComputeInstancesDelete(computeInstances, decoratedInstanceName2);
    GoogleJsonResponseException exception =
        GoogleJsonResponseExceptionFactoryTesting.newMock(new MockJsonFactory(), 404, "not found");
    when(computeInstancesDelete2.execute()).thenThrow(exception);

    computeProvider.delete(template, instanceIds);

    // Verify first instance deletion call was made.
    verify(computeInstances).delete(eq(PROJECT_ID), eq(ZONE_NAME), eq(decoratedInstanceName1));

    // Verify second instance deletion call was made.
    verify(computeInstances).delete(eq(PROJECT_ID), eq(ZONE_NAME), eq(decoratedInstanceName2));
  }

  @Test
  public void testDelete_InvalidPrefix() throws InterruptedException, IOException {
    // Prepare configuration for resource template.
    Map<String, String> templateConfig = new HashMap<String, String>();
    templateConfig.put(ZONE.unwrap().getConfigKey(), ZONE_NAME);
    templateConfig.put(INSTANCE_NAME_PREFIX.unwrap().getConfigKey(), INVALID_INSTANCE_NAME_PREFIX);

    // Create the resource template.
    GoogleComputeInstanceTemplate template = computeProvider.createResourceTemplate("template-1",
            new SimpleConfiguration(templateConfig), new HashMap<String, String>());

    String instanceName1 = UUID.randomUUID().toString();
    String instanceName2 = UUID.randomUUID().toString();
    List<String> instanceIds = Lists.newArrayList(instanceName1, instanceName2);

    // NPE would be thrown (due to lack of mocks) if the compute provider attempted actual calls against GCE.
    // When the instance name prefix is deemed invalid, no calls are attempted against GCE.
    // If no NPE's are thrown, the test is a success.
    computeProvider.delete(template, instanceIds);
  }

  private static Operation buildOperation(String zone, String operationName, String targetLinkUrl, String status) {
    Operation operation = new Operation();

    operation.setZone(Urls.buildZonalUrl(PROJECT_ID, zone));
    operation.setName(operationName);
    operation.setTargetLink(targetLinkUrl);
    operation.setStatus(status);

    return operation;
  }

  /**
   * Used to return operations with a series of statuses in response to the compute provider polling.
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
      Operation polledOperation = buildOperation(ZONE_NAME, subjectOperation.getName(),
          subjectOperation.getTargetLink(), statusQueue.remove());

      if (polledOperation.getStatus().equals("DONE") && errorCode != null) {
        Operation.Error.Errors errors = new Operation.Error.Errors();
        errors.setCode(errorCode);
        errors.setMessage(errorMessage);

        List<Operation.Error.Errors> errorsList = Lists.newArrayList(errors);
        Operation.Error error = new Operation.Error();
        error.setErrors(errorsList);

        polledOperation.setError(error);
      }

      return polledOperation;
    }
  };

  /**
   * Verifies that the properties of the specified attached disk match the specified arguments.
   *
   * @param attachedDisk          the disk to examine. Must not be null.
   * @param boot                  whether or not this is a boot disk. May be null.
   * @param autoDelete            whether or not this disk will auto-delete. May be null.
   * @param diskType              the type of the disk. May be null.
   * @param diskSizeGb            the size of the disk in GB. May be null.
   * @param sourceImage           the image from which the disk is to be created. May be null.
   * @param localSSDInterfaceType the interface type, if this is a Local SSD disk. May be null.
   * @param scratchOrPersistent   either "SCRATCH" or "PERSISTENT". May be null.
   * @param source                the source disk url if this is a persistent disk. May be null.
   */
  private static void verifyAttachedDiskAttributes(AttachedDisk attachedDisk, Boolean boot, Boolean autoDelete,
      String diskType, Long diskSizeGb, String sourceImage, String localSSDInterfaceType,
      String scratchOrPersistent, String source) {
    assertThat(attachedDisk.getBoot()).isEqualTo(boot);
    assertThat(attachedDisk.getAutoDelete()).isEqualTo(autoDelete);

    AttachedDiskInitializeParams initializeParams = attachedDisk.getInitializeParams();

    if (initializeParams != null) {
      assertThat(initializeParams.getDiskType()).isEqualTo(diskType);
      assertThat(initializeParams.getDiskSizeGb()).isEqualTo(diskSizeGb);
      assertThat(initializeParams.getSourceImage()).isEqualTo(sourceImage);
    }

    assertThat(attachedDisk.getInterface()).isEqualTo(localSSDInterfaceType);
    assertThat(attachedDisk.getType()).isEqualTo(scratchOrPersistent);
    assertThat(attachedDisk.getSource()).isEqualTo(source);
  }

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
