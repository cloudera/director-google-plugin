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

import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationProperty.BOOT_DISK_SIZE_GB;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationProperty.DATA_DISK_COUNT;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationProperty.DATA_DISK_SIZE_GB;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationProperty.DATA_DISK_TYPE;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationProperty.IMAGE;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationProperty.NETWORK_NAME;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationProperty.TYPE;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationProperty.ZONE;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationValidator.DATA_DISK_TYPES;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationValidator.EXACT_LOCAL_SSD_DATA_DISK_SIZE_GB;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationValidator.IMAGE_NOT_FOUND_MSG;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationValidator.INVALID_BOOT_DISK_SIZE_FORMAT_MSG;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationValidator.INVALID_BOOT_DISK_SIZE_MSG;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationValidator.INVALID_DATA_DISK_COUNT_FORMAT_MSG;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationValidator.INVALID_DATA_DISK_COUNT_NEGATIVE_MSG;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationValidator.INVALID_DATA_DISK_SIZE_FORMAT_MSG;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationValidator.INVALID_DATA_DISK_SIZE_MSG;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationValidator.INVALID_DATA_DISK_TYPE_MSG;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationValidator.INVALID_LOCAL_SSD_DATA_DISK_COUNT_MSG;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationValidator.INVALID_LOCAL_SSD_DATA_DISK_SIZE_MSG;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationValidator.INVALID_PREFIX_LENGTH_MSG;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationValidator.INVALID_PREFIX_MSG;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationValidator.MACHINE_TYPE_NOT_FOUND_IN_ZONE_MSG;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationValidator.MAPPING_FOR_IMAGE_ALIAS_NOT_FOUND;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationValidator.MAX_LOCAL_SSD_COUNT;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationValidator.MIN_BOOT_DISK_SIZE_GB;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationValidator.MIN_DATA_DISK_SIZE_GB;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationValidator.MIN_LOCAL_SSD_COUNT;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationValidator.NETWORK_NOT_FOUND_MSG;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationValidator.PREFIX_MISSING_MSG;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationValidator.ZONE_NOT_FOUND_IN_REGION_MSG;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationValidator.ZONE_NOT_FOUND_MSG;
import static com.cloudera.director.google.compute.GoogleComputeProviderConfigurationProperty.REGION;
import static com.cloudera.director.spi.v1.model.InstanceTemplate.InstanceTemplateConfigurationPropertyToken.INSTANCE_NAME_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudera.director.google.TestUtils;
import com.cloudera.director.google.internal.GoogleCredentials;
import com.cloudera.director.google.shaded.com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.cloudera.director.google.shaded.com.google.api.client.googleapis.testing.json.GoogleJsonResponseExceptionFactoryTesting;
import com.cloudera.director.google.shaded.com.google.api.client.testing.json.MockJsonFactory;
import com.cloudera.director.google.shaded.com.google.api.services.compute.Compute;
import com.cloudera.director.google.shaded.com.google.api.services.compute.model.Zone;
import com.cloudera.director.spi.v1.model.ConfigurationPropertyToken;
import com.cloudera.director.spi.v1.model.Configured;
import com.cloudera.director.spi.v1.model.LocalizationContext;
import com.cloudera.director.spi.v1.model.exception.PluginExceptionCondition;
import com.cloudera.director.spi.v1.model.exception.PluginExceptionConditionAccumulator;
import com.cloudera.director.spi.v1.model.util.DefaultLocalizationContext;
import com.cloudera.director.spi.v1.model.util.SimpleConfiguration;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import org.assertj.core.util.Maps;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;

/**
 * Tests {@link GoogleComputeInstanceTemplateConfigurationValidator}.
 */
public class GoogleComputeInstanceTemplateConfigurationValidatorTest {

  private static final String PROJECT_ID = "some-project";
  private static final String REGION_NAME_1 = "us-central1";
  private static final String REGION_NAME_2 = "europe-west1";
  private static final String REGION_URL_1 = com.cloudera.director.google.compute.util.Urls.buildRegionalUrl(PROJECT_ID, REGION_NAME_1);
  private static final String REGION_URL_2 = com.cloudera.director.google.compute.util.Urls.buildRegionalUrl(PROJECT_ID, REGION_NAME_2);
  private static final String ZONE_NAME = "us-central1-a";
  private static final String IMAGE_ALIAS_CENTOS = "centos6";
  private static final String IMAGE_ALIAS_UBUNTU = "ubuntu";
  private static final String IMAGE_PROJECT_ID = "centos-cloud";
  private static final String IMAGE_NAME = "centos-6-v20150526";
  private static final String MACHINE_TYPE_NAME = "n1-standard-1";
  private static final String NETWORK_NAME_VALUE = "some-network";
  private static final String BOOT_DISK_SIZE = "60";
  private static final String BOOT_DISK_SIZE_MALFORMED = "sixty";
  private static final String BOOT_DISK_SIZE_TOO_SMALL = "5";
  private static final String DATA_DISK_COUNT_VALUE = "2";
  private static final String DATA_DISK_COUNT_TOO_FEW = "-1";
  private static final String DATA_DISK_COUNT_LOCAL_SSD_TOO_MANY = "5";
  private static final String DATA_DISK_COUNT_MALFORMED = "three";
  private static final String DATA_DISK_TYPE_LOCAL_SSD = "LocalSSD";
  private static final String DATA_DISK_TYPE_SSD = "SSD";
  private static final String DATA_DISK_TYPE_STANDARD = "Standard";
  private static final String DATA_DISK_TYPE_WRONG = "SomethingElse";
  private static final String DATA_DISK_SIZE = "250";
  private static final String DATA_DISK_SIZE_TOO_SMALL = "8";
  private static final String DATA_DISK_SIZE_MALFORMED = "ninety";
  private static final String DATA_DISK_SIZE_LOCAL_SSD = "375";

  private GoogleComputeProvider computeProvider;
  private GoogleCredentials credentials;
  private Compute compute;
  private GoogleComputeInstanceTemplateConfigurationValidator validator;
  private PluginExceptionConditionAccumulator accumulator;
  private LocalizationContext localizationContext = new DefaultLocalizationContext(Locale.getDefault(), "");

  @Before
  public void setUp() throws IOException {
    computeProvider = mock(GoogleComputeProvider.class);
    credentials = mock(GoogleCredentials.class);
    compute = mock(Compute.class);
    validator = new GoogleComputeInstanceTemplateConfigurationValidator(computeProvider);
    accumulator = new PluginExceptionConditionAccumulator();

    when(computeProvider.getCredentials()).thenReturn(credentials);
    when(computeProvider.getGoogleConfig()).thenReturn(TestUtils.buildGoogleConfig());
    when(computeProvider.getConfigurationValue(eq(REGION), any(LocalizationContext.class))).thenReturn(REGION_NAME_1);
    when(credentials.getCompute()).thenReturn(compute);
    when(credentials.getProjectId()).thenReturn(PROJECT_ID);
  }

  private Compute.Zones.Get mockComputeToZone() throws IOException {
    Compute.Zones computeZones = mock(Compute.Zones.class);
    Compute.Zones.Get computeZonesGet = mock(Compute.Zones.Get.class);

    when(compute.zones()).thenReturn(computeZones);
    when(computeZones.get(PROJECT_ID, ZONE_NAME)).thenReturn(computeZonesGet);

    return computeZonesGet;
  }

  @Test
  public void testCheckZone() throws IOException {
    Zone zone = new Zone();
    zone.setName(ZONE_NAME);
    zone.setRegion(REGION_URL_1);

    Compute.Zones.Get computeZonesGet = mockComputeToZone();
    when(computeZonesGet.execute()).thenReturn(zone);

    checkZone(ZONE_NAME);
    verify(computeZonesGet).execute();
    verifyClean();
  }

  @Test
  public void testCheckZone_WrongRegion() throws IOException {
    Zone zone = new Zone();
    zone.setName(ZONE_NAME);
    zone.setRegion(REGION_URL_2);

    Compute.Zones.Get computeZonesGet = mockComputeToZone();
    when(computeZonesGet.execute()).thenReturn(zone);

    checkZone(ZONE_NAME);
    verify(computeZonesGet).execute();
    verifySingleError(ZONE, ZONE_NOT_FOUND_IN_REGION_MSG, ZONE_NAME, REGION_NAME_1, PROJECT_ID);
  }

  @Test
  public void testCheckZone_NotFound() throws IOException {
    Zone zone = new Zone();
    zone.setName(ZONE_NAME);
    zone.setRegion(REGION_URL_2);

    Compute.Zones.Get computeZonesGet = mockComputeToZone();

    GoogleJsonResponseException exception =
        GoogleJsonResponseExceptionFactoryTesting.newMock(new MockJsonFactory(), 404, "not found");
    when(computeZonesGet.execute()).thenThrow(exception);

    checkZone(ZONE_NAME);
    verify(computeZonesGet).execute();
    verifySingleError(ZONE, ZONE_NOT_FOUND_MSG, ZONE_NAME, PROJECT_ID);
  }

  private Compute.Images.Get mockComputeToImage() throws IOException {
    Compute.Images computeImages = mock(Compute.Images.class);
    Compute.Images.Get computeImagesGet = mock(Compute.Images.Get.class);

    when(compute.images()).thenReturn(computeImages);
    when(computeImages.get(IMAGE_PROJECT_ID, IMAGE_NAME)).thenReturn(computeImagesGet);

    return computeImagesGet;
  }

  @Test
  public void testCheckImage() throws IOException {
    Compute.Images.Get computeImagesGet = mockComputeToImage();

    // We don't need to actually return an image, we just need to not throw a 404.
    when(computeImagesGet.execute()).thenReturn(null);

    checkImage(IMAGE_ALIAS_CENTOS);
    verify(computeImagesGet).execute();
    verifyClean();
  }

  @Test
  public void testCheckImage_NotFound() throws IOException {
    Compute.Images.Get computeImagesGet = mockComputeToImage();

    GoogleJsonResponseException exception =
        GoogleJsonResponseExceptionFactoryTesting.newMock(new MockJsonFactory(), 404, "not found");
    when(computeImagesGet.execute()).thenThrow(exception);

    checkImage(IMAGE_ALIAS_CENTOS);
    verify(computeImagesGet).execute();
    verifySingleError(IMAGE, IMAGE_NOT_FOUND_MSG, IMAGE_NAME, IMAGE_PROJECT_ID);
  }

  @Test
  public void testCheckImage_AliasNotFound() {
    checkImage(IMAGE_ALIAS_UBUNTU);
    verifySingleError(IMAGE, MAPPING_FOR_IMAGE_ALIAS_NOT_FOUND, IMAGE_ALIAS_UBUNTU);
  }

  @Test
  public void testCheckBootDiskSize() {
    checkBootDiskSize(BOOT_DISK_SIZE);
    verifyClean();
  }

  @Test
  public void testCheckBootDiskSize_TooSmall() {
    checkBootDiskSize(BOOT_DISK_SIZE_TOO_SMALL);
    verifySingleError(BOOT_DISK_SIZE_GB, INVALID_BOOT_DISK_SIZE_MSG, MIN_BOOT_DISK_SIZE_GB,
        Integer.parseInt(BOOT_DISK_SIZE_TOO_SMALL));
  }

  @Test
  public void testCheckBootDiskSize_Malformed() {
    checkBootDiskSize(BOOT_DISK_SIZE_MALFORMED);
    verifySingleError(BOOT_DISK_SIZE_GB, INVALID_BOOT_DISK_SIZE_FORMAT_MSG, BOOT_DISK_SIZE_MALFORMED);
  }

  @Test
  public void testCheckDataDiskCount_Standard() {
    checkDataDiskCount(DATA_DISK_COUNT_VALUE, DATA_DISK_TYPE_STANDARD);
    verifyClean();
  }

  @Test
  public void testCheckDataDiskCount_Standard_TooFew() {
    checkDataDiskCount(DATA_DISK_COUNT_TOO_FEW, DATA_DISK_TYPE_STANDARD);
    verifySingleError(DATA_DISK_COUNT, INVALID_DATA_DISK_COUNT_NEGATIVE_MSG, Integer.parseInt(DATA_DISK_COUNT_TOO_FEW));
  }

  @Test
  public void testCheckDataDiskCount_Standard_Malformed() {
    checkDataDiskCount(DATA_DISK_COUNT_MALFORMED, DATA_DISK_TYPE_STANDARD);
    verifySingleError(DATA_DISK_COUNT, INVALID_DATA_DISK_COUNT_FORMAT_MSG, DATA_DISK_COUNT_MALFORMED);
  }

  @Test
  public void testCheckDataDiskCount_SSD() {
    checkDataDiskCount(DATA_DISK_COUNT_VALUE, DATA_DISK_TYPE_SSD);
    verifyClean();
  }

  @Test
  public void testCheckDataDiskCount_SSD_TooFew() {
    checkDataDiskCount(DATA_DISK_COUNT_TOO_FEW, DATA_DISK_TYPE_SSD);
    verifySingleError(DATA_DISK_COUNT, INVALID_DATA_DISK_COUNT_NEGATIVE_MSG, Integer.parseInt(DATA_DISK_COUNT_TOO_FEW));
  }

  @Test
  public void testCheckDataDiskCount_SSD_Malformed() {
    checkDataDiskCount(DATA_DISK_COUNT_MALFORMED, DATA_DISK_TYPE_SSD);
    verifySingleError(DATA_DISK_COUNT, INVALID_DATA_DISK_COUNT_FORMAT_MSG, DATA_DISK_COUNT_MALFORMED);
  }



  @Test
  public void testCheckDataDiskCount_LocalSSD() {
    checkDataDiskCount(DATA_DISK_COUNT_VALUE, DATA_DISK_TYPE_LOCAL_SSD);
    verifyClean();
  }

  @Test
  public void testCheckDataDiskCount_LocalSSD_TooFew() {
    checkDataDiskCount(DATA_DISK_COUNT_TOO_FEW, DATA_DISK_TYPE_LOCAL_SSD);
    verifySingleError(DATA_DISK_COUNT, INVALID_LOCAL_SSD_DATA_DISK_COUNT_MSG,
        MIN_LOCAL_SSD_COUNT, MAX_LOCAL_SSD_COUNT, Integer.parseInt(DATA_DISK_COUNT_TOO_FEW));
  }

  @Test
  public void testCheckDataDiskCount_LocalSSD_TooMany() {
    checkDataDiskCount(DATA_DISK_COUNT_LOCAL_SSD_TOO_MANY, DATA_DISK_TYPE_LOCAL_SSD);
    verifySingleError(DATA_DISK_COUNT, INVALID_LOCAL_SSD_DATA_DISK_COUNT_MSG,
        MIN_LOCAL_SSD_COUNT, MAX_LOCAL_SSD_COUNT, Integer.parseInt(DATA_DISK_COUNT_LOCAL_SSD_TOO_MANY));
  }

  @Test
  public void testCheckDataDiskCount_LocalSSD_Malformed() {
    checkDataDiskCount(DATA_DISK_COUNT_MALFORMED, DATA_DISK_TYPE_LOCAL_SSD);
    verifySingleError(DATA_DISK_COUNT, INVALID_DATA_DISK_COUNT_FORMAT_MSG, DATA_DISK_COUNT_MALFORMED);
  }

  @Test
  public void testCheckDataDiskType() {
    checkDataDiskType(DATA_DISK_TYPE_STANDARD);
    checkDataDiskType(DATA_DISK_TYPE_SSD);
    checkDataDiskType(DATA_DISK_TYPE_LOCAL_SSD);
    verifyClean();
  }

  @Test
  public void testCheckDataDiskType_NotFound() {
    checkDataDiskType(DATA_DISK_TYPE_WRONG);
    verifySingleError(DATA_DISK_TYPE, INVALID_DATA_DISK_TYPE_MSG,
        new Object[]{DATA_DISK_TYPE_WRONG, Joiner.on(", ").join(DATA_DISK_TYPES)});
  }

  @Test
  public void testDataDiskSize_Standard() {
    checkDataDiskSize(DATA_DISK_SIZE, DATA_DISK_TYPE_STANDARD);
    verifyClean();
  }

  @Test
  public void testDataDiskSize_Standard_TooSmall() {
    checkDataDiskSize(DATA_DISK_SIZE_TOO_SMALL, DATA_DISK_TYPE_STANDARD);
    verifySingleError(DATA_DISK_SIZE_GB, INVALID_DATA_DISK_SIZE_MSG, MIN_DATA_DISK_SIZE_GB,
        Integer.parseInt(DATA_DISK_SIZE_TOO_SMALL));
  }

  @Test
  public void testDataDiskSize_Standard_Malformed() {
    checkDataDiskSize(DATA_DISK_SIZE_MALFORMED, DATA_DISK_TYPE_STANDARD);
    verifySingleError(DATA_DISK_SIZE_GB, INVALID_DATA_DISK_SIZE_FORMAT_MSG, DATA_DISK_SIZE_MALFORMED);
  }

  @Test
  public void testDataDiskSize_SSD() {
    checkDataDiskSize(DATA_DISK_SIZE, DATA_DISK_TYPE_SSD);
    verifyClean();
  }

  @Test
  public void testDataDiskSize_SSD_TooSmall() {
    checkDataDiskSize(DATA_DISK_SIZE_TOO_SMALL, DATA_DISK_TYPE_SSD);
    verifySingleError(DATA_DISK_SIZE_GB, INVALID_DATA_DISK_SIZE_MSG, MIN_DATA_DISK_SIZE_GB,
        Integer.parseInt(DATA_DISK_SIZE_TOO_SMALL));
  }

  @Test
  public void testDataDiskSize_SSD_Malformed() {
    checkDataDiskSize(DATA_DISK_SIZE_MALFORMED, DATA_DISK_TYPE_SSD);
    verifySingleError(DATA_DISK_SIZE_GB, INVALID_DATA_DISK_SIZE_FORMAT_MSG, DATA_DISK_SIZE_MALFORMED);
  }

  @Test
  public void testDataDiskSize_LocalSSD() {
    checkDataDiskSize(DATA_DISK_SIZE_LOCAL_SSD, DATA_DISK_TYPE_LOCAL_SSD);
    verifyClean();
  }

  @Test
  public void testDataDiskSize_LocalSSD_WrongSize() {
    checkDataDiskSize(DATA_DISK_SIZE, DATA_DISK_TYPE_LOCAL_SSD);
    verifySingleError(DATA_DISK_SIZE_GB, INVALID_LOCAL_SSD_DATA_DISK_SIZE_MSG, EXACT_LOCAL_SSD_DATA_DISK_SIZE_GB,
        Integer.parseInt(DATA_DISK_SIZE));
  }

  @Test
  public void testDataDiskSize_LocalSSD_Malformed() {
    checkDataDiskSize(DATA_DISK_SIZE_MALFORMED, DATA_DISK_TYPE_LOCAL_SSD);
    verifySingleError(DATA_DISK_SIZE_GB, INVALID_DATA_DISK_SIZE_FORMAT_MSG, DATA_DISK_SIZE_MALFORMED);
  }

  private Compute.MachineTypes.Get mockComputeToMachineType() throws IOException {
    Compute.MachineTypes computeMachineTypes = mock(Compute.MachineTypes.class);
    Compute.MachineTypes.Get computeMachineTypesGet = mock(Compute.MachineTypes.Get.class);

    when(compute.machineTypes()).thenReturn(computeMachineTypes);
    when(computeMachineTypes.get(PROJECT_ID, ZONE_NAME, MACHINE_TYPE_NAME)).thenReturn(computeMachineTypesGet);

    return computeMachineTypesGet;
  }

  @Test
  public void testCheckMachineType() throws IOException {
    Compute.MachineTypes.Get computeMachineTypesGet = mockComputeToMachineType();

    // We don't need to actually return a machine type, we just need to not throw a 404.
    when(computeMachineTypesGet.execute()).thenReturn(null);

    checkMachineType(ZONE_NAME, MACHINE_TYPE_NAME);
    verify(computeMachineTypesGet).execute();
    verifyClean();
  }

  @Test
  public void testCheckMachineType_NotFound() throws IOException {
    Compute.MachineTypes.Get computeMachineTypesGet = mockComputeToMachineType();

    GoogleJsonResponseException exception =
        GoogleJsonResponseExceptionFactoryTesting.newMock(new MockJsonFactory(), 404, "not found");
    when(computeMachineTypesGet.execute()).thenThrow(exception);

    checkMachineType(ZONE_NAME, MACHINE_TYPE_NAME);
    verify(computeMachineTypesGet).execute();
    verifySingleError(TYPE, MACHINE_TYPE_NOT_FOUND_IN_ZONE_MSG, MACHINE_TYPE_NAME, ZONE_NAME, PROJECT_ID);
  }

  private Compute.Networks.Get mockComputeToNetwork() throws IOException {
    Compute.Networks computeNetworks = mock(Compute.Networks.class);
    Compute.Networks.Get computeNetworksGet = mock(Compute.Networks.Get.class);

    when(compute.networks()).thenReturn(computeNetworks);
    when(computeNetworks.get(PROJECT_ID, NETWORK_NAME_VALUE)).thenReturn(computeNetworksGet);

    return computeNetworksGet;
  }

  @Test
  public void testCheckNetwork() throws IOException {
    Compute.Networks.Get computeNetworksGet = mockComputeToNetwork();

    // We don't need to actually return a network, we just need to not throw a 404.
    when(computeNetworksGet.execute()).thenReturn(null);

    checkNetwork(NETWORK_NAME_VALUE);
    verify(computeNetworksGet).execute();
    verifyClean();
  }

  @Test
  public void testCheckNetwork_NotFound() throws IOException {
    Compute.Networks.Get computeNetworksGet = mockComputeToNetwork();

    GoogleJsonResponseException exception =
        GoogleJsonResponseExceptionFactoryTesting.newMock(new MockJsonFactory(), 404, "not found");
    when(computeNetworksGet.execute()).thenThrow(exception);

    checkNetwork(NETWORK_NAME_VALUE);
    verify(computeNetworksGet).execute();
    verifySingleError(NETWORK_NAME, NETWORK_NOT_FOUND_MSG, NETWORK_NAME_VALUE, PROJECT_ID);
  }

  @Test
  public void testCheckPrefix() throws IOException {
    checkPrefix("director");
    checkPrefix("some-other-prefix");
    checkPrefix("length-is-eq-26-characters");
    checkPrefix("c");
    checkPrefix("c-d");
    checkPrefix("ends-with-digit-1");
    verifyClean();
  }

  @Test
  public void testCheckPrefix_Missing() throws IOException {
    checkPrefix(null);
    verifySingleError(INSTANCE_NAME_PREFIX, PREFIX_MISSING_MSG);
  }

  @Test
  public void testCheckPrefix_TooShort() throws IOException {
    checkPrefix("");
    verifySingleError(INSTANCE_NAME_PREFIX, INVALID_PREFIX_LENGTH_MSG);
  }

  @Test
  public void testCheckPrefix_TooLong() throws IOException {
    checkPrefix("the-length-eqs-twenty-seven");
    verifySingleError(INSTANCE_NAME_PREFIX, INVALID_PREFIX_LENGTH_MSG);
  }

  @Test
  public void testCheckPrefix_StartsWithUppercaseLetter() throws IOException {
    checkPrefix("Bad-prefix");
    verifySingleError(INSTANCE_NAME_PREFIX, INVALID_PREFIX_MSG);
  }

  @Test
  public void testCheckPrefix_StartsWithDash() throws IOException {
    checkPrefix("-bad-prefix");
    verifySingleError(INSTANCE_NAME_PREFIX, INVALID_PREFIX_MSG);
  }

  @Test
  public void testCheckPrefix_StartsWithDigit() throws IOException {
    checkPrefix("1-bad-prefix");
    verifySingleError(INSTANCE_NAME_PREFIX, INVALID_PREFIX_MSG);
  }

  @Test
  public void testCheckPrefix_ContainsUppercaseLetter() throws IOException {
    checkPrefix("badPrefix");
    verifySingleError(INSTANCE_NAME_PREFIX, INVALID_PREFIX_MSG);
  }

  @Test
  public void testCheckPrefix_ContainsUnderscore() throws IOException {
    checkPrefix("bad_prefix");
    verifySingleError(INSTANCE_NAME_PREFIX, INVALID_PREFIX_MSG);
  }

  /**
   * Invokes checkZone with the specified configuration.
   *
   * @param zone the zone name
   */
  protected void checkZone(String zone) {
    Map<String, String> configMap = Maps.newHashMap();
    configMap.put(ZONE.unwrap().getConfigKey(), zone);
    Configured configuration = new SimpleConfiguration(configMap);
    validator.checkZone(configuration, accumulator, localizationContext);
  }

  /**
   * Invokes checkImage with the specified configuration.
   *
   * @param image the image name
   */
  protected void checkImage(String image) {
    Map<String, String> configMap = Maps.newHashMap();
    configMap.put(IMAGE.unwrap().getConfigKey(), image);
    Configured configuration = new SimpleConfiguration(configMap);
    validator.checkImage(configuration, accumulator, localizationContext);
  }

  /**
   * Invokes checkBootDiskSize with the specified configuration.
   *
   * @param bootDiskSize the boot disk size
   */
  protected void checkBootDiskSize(String bootDiskSize) {
    Map<String, String> configMap = Maps.newHashMap();
    configMap.put(BOOT_DISK_SIZE_GB.unwrap().getConfigKey(), bootDiskSize);
    Configured configuration = new SimpleConfiguration(configMap);
    validator.checkBootDiskSize(configuration, accumulator, localizationContext);
  }

  /**
   * Invokes checkDataDiskCount with the specified configuration.
   *
   * @param dataDiskCount the number of data disks
   * @param dataDiskType the type of data disks
   */
  protected void checkDataDiskCount(String dataDiskCount, String dataDiskType) {
    Map<String, String> configMap = Maps.newHashMap();
    configMap.put(DATA_DISK_COUNT.unwrap().getConfigKey(), dataDiskCount);
    configMap.put(DATA_DISK_TYPE.unwrap().getConfigKey(), dataDiskType);
    Configured configuration = new SimpleConfiguration(configMap);
    validator.checkDataDiskCount(configuration, accumulator, localizationContext);
  }

  /**
   * Invokes checkDataDiskType with the specified configuration.
   *
   * @param dataDiskType the type of data disks
   */
  protected void checkDataDiskType(String dataDiskType) {
    Map<String, String> configMap = Maps.newHashMap();
    configMap.put(DATA_DISK_TYPE.unwrap().getConfigKey(), dataDiskType);
    Configured configuration = new SimpleConfiguration(configMap);
    validator.checkDataDiskType(configuration, accumulator, localizationContext);
  }

  /**
   * Invokes checkDataDiskSize with the specified configuration.
   *
   * @param dataDiskSize the size of the data disks
   * @param dataDiskType the type of data disks
   */
  protected void checkDataDiskSize(String dataDiskSize, String dataDiskType) {
    Map<String, String> configMap = Maps.newHashMap();
    configMap.put(DATA_DISK_SIZE_GB.unwrap().getConfigKey(), dataDiskSize);
    configMap.put(DATA_DISK_TYPE.unwrap().getConfigKey(), dataDiskType);
    Configured configuration = new SimpleConfiguration(configMap);
    validator.checkDataDiskSize(configuration, accumulator, localizationContext);
  }

  /**
   * Invokes checkMachineType with the specified configuration.
   *
   * @param type the machine type name
   */
  protected void checkMachineType(String zone, String type) {
    Map<String, String> configMap = Maps.newHashMap();
    configMap.put(ZONE.unwrap().getConfigKey(), zone);
    configMap.put(TYPE.unwrap().getConfigKey(), type);
    Configured configuration = new SimpleConfiguration(configMap);
    validator.checkMachineType(configuration, accumulator, localizationContext);
  }

  /**
   * Invokes checkNetwork with the specified configuration.
   *
   * @param network the network name
   */
  protected void checkNetwork(String network) {
    Map<String, String> configMap = Maps.newHashMap();
    configMap.put(NETWORK_NAME.unwrap().getConfigKey(), network);
    Configured configuration = new SimpleConfiguration(configMap);
    validator.checkNetwork(configuration, accumulator, localizationContext);
  }

  /**
   * Invokes checkPrefix with the specified configuration.
   *
   * @param prefix the instance name prefix
   */
  protected void checkPrefix(String prefix) {
    Map<String, String> configMap = Maps.newHashMap();
    configMap.put(INSTANCE_NAME_PREFIX.unwrap().getConfigKey(), prefix);
    Configured configuration = new SimpleConfiguration(configMap);
    validator.checkPrefix(configuration, accumulator, localizationContext);
  }

  /**
   * Verifies that the specified plugin exception condition accumulator contains no errors or
   * warnings.
   */
  private void verifyClean() {
    Map<String, Collection<PluginExceptionCondition>> conditionsByKey = accumulator.getConditionsByKey();
    assertThat(conditionsByKey).isEmpty();
  }

  /**
   * Verifies that the specified plugin exception condition accumulator contains exactly
   * one condition, which must be an error associated with the specified property.
   *
   * @param token the configuration property token for the property which should be in error
   */
  private void verifySingleError(ConfigurationPropertyToken token) {
    verifySingleError(token, Optional.<String>absent());
  }

  /**
   * Verifies that the specified plugin exception condition accumulator contains exactly
   * one condition, which must be an error with the specified message and associated with the
   * specified property.
   *
   * @param token    the configuration property token for the property which should be in error
   * @param errorMsg the expected error message
   * @param args     the error message arguments
   */
  private void verifySingleError(ConfigurationPropertyToken token, String errorMsg, Object... args) {
    verifySingleError(token, Optional.of(errorMsg), args);
  }

  /**
   * Verifies that the specified plugin exception condition accumulator contains exactly
   * one condition, which must be an error with the specified message and associated with the
   * specified property.
   *
   * @param token          the configuration property token for the property which should be in error
   * @param errorMsgFormat the expected error message
   * @param args           the error message arguments
   */
  private void verifySingleError(ConfigurationPropertyToken token, Optional<String> errorMsgFormat, Object... args) {
    Map<String, Collection<PluginExceptionCondition>> conditionsByKey = accumulator.getConditionsByKey();
    assertThat(conditionsByKey).hasSize(1);
    String configKey = token.unwrap().getConfigKey();
    assertThat(conditionsByKey.containsKey(configKey)).isTrue();
    Collection<PluginExceptionCondition> keyConditions = conditionsByKey.get(configKey);
    assertThat(keyConditions).hasSize(1);
    PluginExceptionCondition condition = keyConditions.iterator().next();
    verifySingleErrorCondition(condition, errorMsgFormat, args);
  }

  /**
   * Verifies that the specified plugin exception condition is an error with the specified message.
   *
   * @param condition      the plugin exception condition
   * @param errorMsgFormat the expected error message format
   * @param args           the error message arguments
   */
  private void verifySingleErrorCondition(PluginExceptionCondition condition,
      Optional<String> errorMsgFormat, Object... args) {
    assertThat(condition.isError()).isTrue();
    if (errorMsgFormat.isPresent()) {
      assertThat(condition.getMessage()).isEqualTo(String.format(errorMsgFormat.get(), args));
    }
  }
}
