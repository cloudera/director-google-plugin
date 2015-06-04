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

import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationProperty.DATADISKCOUNT;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationProperty.DATADISKSIZEGB;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationProperty.DATADISKTYPE;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationProperty.IMAGE;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationProperty.NETWORKNAME;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationProperty.TYPE;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationProperty.ZONE;
import static com.cloudera.director.google.compute.GoogleComputeProviderConfigurationProperty.REGION;
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
import com.cloudera.director.google.shaded.com.google.api.services.compute.Compute;
import com.cloudera.director.google.shaded.com.google.api.services.compute.model.AttachedDisk;
import com.cloudera.director.google.shaded.com.google.api.services.compute.model.AttachedDiskInitializeParams;
import com.cloudera.director.google.shaded.com.google.api.services.compute.model.Disk;
import com.cloudera.director.google.shaded.com.google.api.services.compute.model.Instance;
import com.cloudera.director.google.shaded.com.google.api.services.compute.model.Operation;
import com.cloudera.director.spi.v1.model.Configured;
import com.cloudera.director.spi.v1.model.InstanceTemplate;
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
  private static final String NETWORK_NAME = "some-network";

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
        TestUtils.buildGoogleConfig(), DEFAULT_LOCALIZATION_CONTEXT);
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

  @Test
  public void testAllocate_LocalSSD() throws InterruptedException, IOException {
    // Prepare configuration for resource template.
    Map<String, String> templateConfig = new HashMap<String, String>();
    templateConfig.put(IMAGE.unwrap().getConfigKey(), IMAGE_ALIAS_CENTOS);
    templateConfig.put(TYPE.unwrap().getConfigKey(), MACHINE_TYPE_NAME);
    templateConfig.put(NETWORKNAME.unwrap().getConfigKey(), NETWORK_NAME);
    templateConfig.put(ZONE.unwrap().getConfigKey(), ZONE_NAME);

    // Create the resource template.
    GoogleComputeInstanceTemplate template = computeProvider.createResourceTemplate("template-1",
        new SimpleConfiguration(templateConfig), new HashMap<String, String>());

    // Configure stub for successful instance insertion operation.
    String instanceName = UUID.randomUUID().toString();
    String instanceUrl = Utils.buildInstanceUrl(PROJECT_ID, ZONE_NAME, instanceName);
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
    assertThat(insertedInstance.getName()).isEqualTo(
        InstanceTemplate.InstanceTemplateConfigurationPropertyToken.INSTANCE_NAME_PREFIX.unwrap().getDefaultValue() +
        "-" + instanceName);
    assertThat(insertedInstance.getMetadata().getItems()).isEqualTo(Lists.newArrayList());

    List<AttachedDisk> attachedDiskList = insertedInstance.getDisks();
    assertThat(attachedDiskList.size()).isEqualTo(3);

    // Verify boot disk.
    verifyAttachedDiskAttributes(attachedDiskList.get(0), true, true, null, 60L,
        Utils.buildImageUrl(IMAGE_PROJECT_ID, IMAGE_NAME), null, null, null);

    // Verify data disk.
    verifyAttachedDiskAttributes(attachedDiskList.get(1), null, true,
        Utils.buildDiskTypeUrl(PROJECT_ID, ZONE_NAME, "LocalSSD"), null, null, "SCSI", "SCRATCH", null);

    // Verify data disk.
    verifyAttachedDiskAttributes(attachedDiskList.get(2), null, true,
        Utils.buildDiskTypeUrl(PROJECT_ID, ZONE_NAME, "LocalSSD"), null, null, "SCSI", "SCRATCH", null);
  }

  @Test
  public void testAllocate_LocalSSD_CreationFails_BelowMinCount() throws InterruptedException, IOException {
    // Prepare configuration for resource template.
    Map<String, String> templateConfig = new HashMap<String, String>();
    templateConfig.put(IMAGE.unwrap().getConfigKey(), IMAGE_ALIAS_CENTOS);
    templateConfig.put(TYPE.unwrap().getConfigKey(), MACHINE_TYPE_NAME);
    templateConfig.put(NETWORKNAME.unwrap().getConfigKey(), NETWORK_NAME);
    templateConfig.put(ZONE.unwrap().getConfigKey(), ZONE_NAME);

    // Create the resource template.
    GoogleComputeInstanceTemplate template = computeProvider.createResourceTemplate("template-1",
        new SimpleConfiguration(templateConfig), new HashMap<String, String>());

    // Configure stub for successful instance insertion operation.
    String instanceName1 = UUID.randomUUID().toString();
    String instanceUrl1 = Utils.buildInstanceUrl(PROJECT_ID, ZONE_NAME, instanceName1);
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
    String instanceUrl2 = Utils.buildInstanceUrl(PROJECT_ID, ZONE_NAME, instanceName2);
    Operation vmCreationOperation2 = buildOperation(ZONE_NAME, UUID.randomUUID().toString(), instanceUrl2, "PENDING");
    ongoingInsertionStub.thenReturn(vmCreationOperation2);
    Compute.ZoneOperations.Get computeZoneOperationsGet2 = mock(Compute.ZoneOperations.Get.class);
    when(computeZoneOperations.get(PROJECT_ID, ZONE_NAME,
        vmCreationOperation2.getName())).thenReturn(computeZoneOperationsGet2);
    when(computeZoneOperationsGet2.execute()).then(
        new OperationAnswer(vmCreationOperation2, new String[]{"PENDING", "RUNNING", "DONE"},
            "SOME_ERROR_CODE", "Some error message..."));

    // Configure stub for successful instance deletion operation.
    Compute.Instances.Delete computeInstancesDelete1 = mockComputeInstancesDelete(computeInstances, instanceName1);
    Operation vmDeletionOperation1 = buildOperation(ZONE_NAME, UUID.randomUUID().toString(), instanceUrl1, "PENDING");
    when(computeInstancesDelete1.execute()).thenReturn(vmDeletionOperation1);
    Compute.ZoneOperations.Get computeZoneOperationsGet3 = mock(Compute.ZoneOperations.Get.class);
    when(computeZoneOperations.get(PROJECT_ID, ZONE_NAME,
        vmDeletionOperation1.getName())).thenReturn(computeZoneOperationsGet3);
    when(computeZoneOperationsGet3.execute()).then(
        new OperationAnswer(vmDeletionOperation1, new String[]{"PENDING", "RUNNING", "DONE"}));

    // Configure stub for successful instance deletion operation.
    Compute.Instances.Delete computeInstancesDelete2 = mockComputeInstancesDelete(computeInstances, instanceName2);
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
    assertThat(insertedInstance1.getName()).isEqualTo(
        InstanceTemplate.InstanceTemplateConfigurationPropertyToken.INSTANCE_NAME_PREFIX.unwrap().getDefaultValue() +
        "-" + instanceName1);
    assertThat(insertedInstance1.getMetadata().getItems()).isEqualTo(Lists.newArrayList());

    List<AttachedDisk> attachedDiskList1 = insertedInstance1.getDisks();
    assertThat(attachedDiskList1.size()).isEqualTo(3);

    // Verify boot disk.
    verifyAttachedDiskAttributes(attachedDiskList1.get(0), true, true, null, 60L,
        Utils.buildImageUrl(IMAGE_PROJECT_ID, IMAGE_NAME), null, null, null);

    // Verify data disk.
    verifyAttachedDiskAttributes(attachedDiskList1.get(1), null, true,
        Utils.buildDiskTypeUrl(PROJECT_ID, ZONE_NAME, "LocalSSD"), null, null, "SCSI", "SCRATCH", null);

    // Verify data disk.
    verifyAttachedDiskAttributes(attachedDiskList1.get(2), null, true,
        Utils.buildDiskTypeUrl(PROJECT_ID, ZONE_NAME, "LocalSSD"), null, null, "SCSI", "SCRATCH", null);

    // Verify second instance insertion call was made.
    Instance insertedInstance2 = insertedInstanceList.get(1);

    // Verify second instance name and metadata.
    assertThat(insertedInstance2.getName()).isEqualTo(
        InstanceTemplate.InstanceTemplateConfigurationPropertyToken.INSTANCE_NAME_PREFIX.unwrap().getDefaultValue() +
        "-" + instanceName2);
    assertThat(insertedInstance2.getMetadata().getItems()).isEqualTo(Lists.newArrayList());

    List<AttachedDisk> attachedDiskList2 = insertedInstance2.getDisks();
    assertThat(attachedDiskList2.size()).isEqualTo(3);

    // Verify boot disk.
    verifyAttachedDiskAttributes(attachedDiskList2.get(0), true, true, null, 60L,
        Utils.buildImageUrl(IMAGE_PROJECT_ID, IMAGE_NAME), null, null, null);

    // Verify data disk.
    verifyAttachedDiskAttributes(attachedDiskList2.get(1), null, true,
        Utils.buildDiskTypeUrl(PROJECT_ID, ZONE_NAME, "LocalSSD"), null, null, "SCSI", "SCRATCH", null);

    // Verify data disk.
    verifyAttachedDiskAttributes(attachedDiskList2.get(2), null, true,
        Utils.buildDiskTypeUrl(PROJECT_ID, ZONE_NAME, "LocalSSD"), null, null, "SCSI", "SCRATCH", null);

    // Verify first instance deletion call was made.
    ArgumentCaptor<Instance> deleteArgumentCaptor = ArgumentCaptor.forClass(Instance.class);
    verify(computeInstances, times(2)).insert(eq(PROJECT_ID), eq(ZONE_NAME), deleteArgumentCaptor.capture());
    List<Instance> deletedInstanceList = deleteArgumentCaptor.getAllValues();
    Instance deletedInstance1 = deletedInstanceList.get(0);

    // Verify first instance deletion call instance name and metadata.
    assertThat(deletedInstance1.getName()).isEqualTo(
        InstanceTemplate.InstanceTemplateConfigurationPropertyToken.INSTANCE_NAME_PREFIX.unwrap().getDefaultValue() +
        "-" + instanceName1);

    // Verify second instance deletion call was made.
    Instance deletedInstance2 = deletedInstanceList.get(1);

    // Verify second instance deletion call instance name and metadata.
    assertThat(deletedInstance2.getName()).isEqualTo(
        InstanceTemplate.InstanceTemplateConfigurationPropertyToken.INSTANCE_NAME_PREFIX.unwrap().getDefaultValue() +
        "-" + instanceName2);
  }

  @Test
  public void testAllocate_LocalSSD_CreationFails_ReachesMinCount() throws InterruptedException, IOException {
    // Prepare configuration for resource template.
    Map<String, String> templateConfig = new HashMap<String, String>();
    templateConfig.put(IMAGE.unwrap().getConfigKey(), IMAGE_ALIAS_CENTOS);
    templateConfig.put(TYPE.unwrap().getConfigKey(), MACHINE_TYPE_NAME);
    templateConfig.put(NETWORKNAME.unwrap().getConfigKey(), NETWORK_NAME);
    templateConfig.put(ZONE.unwrap().getConfigKey(), ZONE_NAME);

    // Create the resource template.
    GoogleComputeInstanceTemplate template = computeProvider.createResourceTemplate("template-1",
        new SimpleConfiguration(templateConfig), new HashMap<String, String>());

    // Configure stub for successful instance insertion operation.
    String instanceName1 = UUID.randomUUID().toString();
    String instanceUrl1 = Utils.buildInstanceUrl(PROJECT_ID, ZONE_NAME, instanceName1);
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
    String instanceUrl2 = Utils.buildInstanceUrl(PROJECT_ID, ZONE_NAME, instanceName2);
    Operation vmCreationOperation2 = buildOperation(ZONE_NAME, UUID.randomUUID().toString(), instanceUrl2, "PENDING");
    ongoingInsertionStub.thenReturn(vmCreationOperation2);
    Compute.ZoneOperations.Get computeZoneOperationsGet2 = mock(Compute.ZoneOperations.Get.class);
    when(computeZoneOperations.get(PROJECT_ID, ZONE_NAME,
        vmCreationOperation2.getName())).thenReturn(computeZoneOperationsGet2);
    when(computeZoneOperationsGet2.execute()).then(
        new OperationAnswer(vmCreationOperation2, new String[]{"PENDING", "RUNNING", "DONE"}));

    computeProvider.allocate(template, Lists.newArrayList(instanceName1, instanceName2), 2);

    // Verify first instance insertion call was made.
    ArgumentCaptor<Instance> insertArgumentCaptor = ArgumentCaptor.forClass(Instance.class);
    verify(computeInstances, times(2)).insert(eq(PROJECT_ID), eq(ZONE_NAME), insertArgumentCaptor.capture());
    List<Instance> insertedInstanceList = insertArgumentCaptor.getAllValues();
    Instance insertedInstance1 = insertedInstanceList.get(0);

    // Verify first instance name and metadata.
    assertThat(insertedInstance1.getName()).isEqualTo(
        InstanceTemplate.InstanceTemplateConfigurationPropertyToken.INSTANCE_NAME_PREFIX.unwrap().getDefaultValue() +
        "-" + instanceName1);
    assertThat(insertedInstance1.getMetadata().getItems()).isEqualTo(Lists.newArrayList());

    List<AttachedDisk> attachedDiskList1 = insertedInstance1.getDisks();
    assertThat(attachedDiskList1.size()).isEqualTo(3);

    // Verify boot disk.
    verifyAttachedDiskAttributes(attachedDiskList1.get(0), true, true, null, 60L,
        Utils.buildImageUrl(IMAGE_PROJECT_ID, IMAGE_NAME), null, null, null);

    // Verify data disk.
    verifyAttachedDiskAttributes(attachedDiskList1.get(1), null, true,
        Utils.buildDiskTypeUrl(PROJECT_ID, ZONE_NAME, "LocalSSD"), null, null, "SCSI", "SCRATCH", null);

    // Verify data disk.
    verifyAttachedDiskAttributes(attachedDiskList1.get(2), null, true,
        Utils.buildDiskTypeUrl(PROJECT_ID, ZONE_NAME, "LocalSSD"), null, null, "SCSI", "SCRATCH", null);

    // Verify second instance insertion call was made.
    Instance insertedInstance2 = insertedInstanceList.get(1);

    // Verify second instance name and metadata.
    assertThat(insertedInstance2.getName()).isEqualTo(
        InstanceTemplate.InstanceTemplateConfigurationPropertyToken.INSTANCE_NAME_PREFIX.unwrap().getDefaultValue() +
        "-" + instanceName2);
    assertThat(insertedInstance2.getMetadata().getItems()).isEqualTo(Lists.newArrayList());

    List<AttachedDisk> attachedDiskList2 = insertedInstance2.getDisks();
    assertThat(attachedDiskList2.size()).isEqualTo(3);

    // Verify boot disk.
    verifyAttachedDiskAttributes(attachedDiskList2.get(0), true, true, null, 60L,
        Utils.buildImageUrl(IMAGE_PROJECT_ID, IMAGE_NAME), null, null, null);

    // Verify data disk.
    verifyAttachedDiskAttributes(attachedDiskList2.get(1), null, true,
        Utils.buildDiskTypeUrl(PROJECT_ID, ZONE_NAME, "LocalSSD"), null, null, "SCSI", "SCRATCH", null);

    // Verify data disk.
    verifyAttachedDiskAttributes(attachedDiskList2.get(2), null, true,
        Utils.buildDiskTypeUrl(PROJECT_ID, ZONE_NAME, "LocalSSD"), null, null, "SCSI", "SCRATCH", null);
  }

  @Test
  public void testAllocate_Standard() throws InterruptedException, IOException {
    // Prepare configuration for resource template.
    Map<String, String> templateConfig = new HashMap<String, String>();
    templateConfig.put(IMAGE.unwrap().getConfigKey(), IMAGE_ALIAS_CENTOS);
    templateConfig.put(TYPE.unwrap().getConfigKey(), MACHINE_TYPE_NAME);
    templateConfig.put(NETWORKNAME.unwrap().getConfigKey(), NETWORK_NAME);
    templateConfig.put(ZONE.unwrap().getConfigKey(), ZONE_NAME);
    templateConfig.put(DATADISKCOUNT.unwrap().getConfigKey(), "1");
    templateConfig.put(DATADISKTYPE.unwrap().getConfigKey(), "Standard");
    templateConfig.put(DATADISKSIZEGB.unwrap().getConfigKey(), "250");

    // Create the resource template.
    GoogleComputeInstanceTemplate template = computeProvider.createResourceTemplate("template-1",
        new SimpleConfiguration(templateConfig), new HashMap<String, String>());
    String instanceName = UUID.randomUUID().toString();
    String instanceUrl = Utils.buildInstanceUrl(PROJECT_ID, ZONE_NAME, instanceName);

    // Configure stub for successful disk insertion operation.
    String diskName =
        InstanceTemplate.InstanceTemplateConfigurationPropertyToken.INSTANCE_NAME_PREFIX.unwrap().getDefaultValue() +
        "-" + instanceName + "-pd-0";
    String diskUrl = Utils.buildDiskUrl(PROJECT_ID, ZONE_NAME, diskName);
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

    // Verify disk insertion call was made.
    ArgumentCaptor<Disk> argumentCaptor1 = ArgumentCaptor.forClass(Disk.class);
    verify(computeDisks).insert(eq(PROJECT_ID), eq(ZONE_NAME), argumentCaptor1.capture());
    Disk insertedDisk = argumentCaptor1.getValue();

    // Verify disk name, size and type.
    assertThat(insertedDisk.getName()).isEqualTo(diskName);
    assertThat(insertedDisk.getSizeGb()).isEqualTo(250);
    assertThat(insertedDisk.getType()).isEqualTo(Utils.buildDiskTypeUrl(PROJECT_ID, ZONE_NAME, "pd-standard"));

    // Verify instance insertion call was made.
    ArgumentCaptor<Instance> argumentCaptor2 = ArgumentCaptor.forClass(Instance.class);
    verify(computeInstances).insert(eq(PROJECT_ID), eq(ZONE_NAME), argumentCaptor2.capture());
    Instance insertedInstance = argumentCaptor2.getValue();

    // Verify instance name and metadata.
    assertThat(insertedInstance.getName()).isEqualTo(
        InstanceTemplate.InstanceTemplateConfigurationPropertyToken.INSTANCE_NAME_PREFIX.unwrap().getDefaultValue() +
        "-" + instanceName);
    assertThat(insertedInstance.getMetadata().getItems()).isEqualTo(Lists.newArrayList());

    List<AttachedDisk> attachedDiskList = insertedInstance.getDisks();
    assertThat(attachedDiskList.size()).isEqualTo(2);

    // Verify boot disk.
    verifyAttachedDiskAttributes(attachedDiskList.get(0), true, true, null, 60L,
        Utils.buildImageUrl(IMAGE_PROJECT_ID, IMAGE_NAME), null, null, null);

    // Verify data disk.
    verifyAttachedDiskAttributes(attachedDiskList.get(1), null, true, null, null,
        null, null, "PERSISTENT", diskUrl);
  }

  private static Operation buildOperation(String zone, String operationName, String targetLinkUrl, String status) {
    Operation operation = new Operation();

    operation.setZone(zone);
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
