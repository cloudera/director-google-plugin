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
import static com.cloudera.director.google.compute.GoogleComputeProviderConfigurationProperty.REGION;
import static com.cloudera.director.spi.v1.model.util.Validations.addError;

import com.cloudera.director.google.Configurations;
import com.cloudera.director.google.internal.GoogleCredentials;
import com.cloudera.director.spi.v1.model.ConfigurationValidator;
import com.cloudera.director.spi.v1.model.Configured;
import com.cloudera.director.spi.v1.model.LocalizationContext;
import com.cloudera.director.spi.v1.model.exception.PluginExceptionCondition;
import com.cloudera.director.spi.v1.model.exception.PluginExceptionConditionAccumulator;
import com.cloudera.director.spi.v1.model.exception.TransientProviderException;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.Zone;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

/**
 * Validates Google compute instance template configuration.
 */
public class GoogleComputeInstanceTemplateConfigurationValidator implements ConfigurationValidator {

  private static final Logger LOG =
      LoggerFactory.getLogger(GoogleComputeInstanceTemplateConfigurationValidator.class);

  @VisibleForTesting
  static final int MIN_BOOT_DISK_SIZE_GB = 10;
  @VisibleForTesting
  static final int MIN_DATA_DISK_SIZE_GB = 10;
  static final int EXACT_LOCAL_SSD_DATA_DISK_SIZE_GB = 375;
  @VisibleForTesting
  static final int MIN_LOCAL_SSD_COUNT = 0;
  @VisibleForTesting
  static final int MAX_LOCAL_SSD_COUNT = 4;

  @VisibleForTesting
  static final String ZONE_NOT_FOUND_MSG = "Zone '%s' not found for project '%s'.";
  @VisibleForTesting
  static final String ZONE_NOT_FOUND_IN_REGION_MSG = "Zone '%s' not found in region '%s' for project '%s'.";

  @VisibleForTesting
  static final String MAPPING_FOR_IMAGE_ALIAS_NOT_FOUND = "Mapping for image alias '%s' not found.";
  @VisibleForTesting
  static final String IMAGE_NOT_FOUND_MSG = "Image '%s' not found for project '%s'.";

  @VisibleForTesting
  static final String INVALID_BOOT_DISK_SIZE_FORMAT_MSG = "Boot disk size must be an integer: '%s'.";
  @VisibleForTesting
  static final String INVALID_BOOT_DISK_SIZE_MSG =
      "Boot disk size must be at least '%dGB'. Current configuration: '%dGB'.";

  @VisibleForTesting
  static final String INVALID_DATA_DISK_COUNT_FORMAT_MSG = "Data disk count must be an integer: '%s'.";
  @VisibleForTesting
  static final String INVALID_DATA_DISK_COUNT_NEGATIVE_MSG =
      "Data disk count must be non-negative. Current configuration: '%d'.";
  @VisibleForTesting
  static final String INVALID_LOCAL_SSD_DATA_DISK_COUNT_MSG =
      "Data disk count when using local SSD drives must be between '%d' and '%d', inclusive. " +
      "Current configuration: '%d'.";

  @VisibleForTesting
  static final String INVALID_DATA_DISK_SIZE_FORMAT_MSG = "Data disk size must be an integer: '%s'.";
  @VisibleForTesting
  static final String INVALID_DATA_DISK_SIZE_MSG =
      "Data disk size must be at least '%dGB'. Current configuration: '%dGB'.";
  @VisibleForTesting
  static final String INVALID_LOCAL_SSD_DATA_DISK_SIZE_MSG =
      "Data disk size when using local SSD drives must be exactly '%dGB'. Current configuration: '%dGB'.";

  @VisibleForTesting
  static final List<String> DATA_DISK_TYPES = ImmutableList.of("LocalSSD", "SSD", "Standard");
  static final String INVALID_DATA_DISK_TYPE_MSG =
      "Invalid data disk type '%s'. Available options: %s";

  @VisibleForTesting
  static final String MACHINE_TYPE_NOT_FOUND_IN_ZONE_MSG =
      "Machine type '%s' not found in zone '%s' for project '%s'.";

  @VisibleForTesting
  static final String NETWORK_NOT_FOUND_MSG = "Network '%s' not found for project '%s'.";

  /**
   * The Google compute provider.
   */
  private final GoogleComputeProvider provider;

  /**
   * Creates a Google compute instance template configuration validator with the specified
   * parameters.
   *
   * @param provider the Google compute provider
   */
  public GoogleComputeInstanceTemplateConfigurationValidator(GoogleComputeProvider provider) {
    this.provider = Preconditions.checkNotNull(provider, "provider");
  }

  @Override
  public void validate(String name, Configured configuration,
      PluginExceptionConditionAccumulator accumulator, LocalizationContext localizationContext) {

    checkZone(configuration, accumulator, localizationContext);
    checkImage(configuration, accumulator, localizationContext);
    checkBootDiskSize(configuration, accumulator, localizationContext);
    checkDataDiskCount(configuration, accumulator, localizationContext);
    checkDataDiskType(configuration, accumulator, localizationContext);
    checkDataDiskSize(configuration, accumulator, localizationContext);
    checkMachineType(configuration, accumulator, localizationContext);
    checkNetwork(configuration, accumulator, localizationContext);
  }

  /**
   * Validates the configured zone.
   *
   * @param configuration       the configuration to be validated
   * @param accumulator         the exception condition accumulator
   * @param localizationContext the localization context
   */
  void checkZone(Configured configuration,
      PluginExceptionConditionAccumulator accumulator,
      LocalizationContext localizationContext) {

    String zoneName = configuration.getConfigurationValue(ZONE, localizationContext);

    if (zoneName != null) {
      LOG.info(">> Querying zone '{}'", zoneName);

      GoogleCredentials credentials = provider.getCredentials();
      Compute compute = credentials.getCompute();
      String projectId = credentials.getProjectId();
      String regionName = provider.getConfigurationValue(REGION, localizationContext);

      try {
        Zone zone = compute.zones().get(projectId, zoneName).execute();

        if (!Utils.getLocalName(zone.getRegion()).equals(regionName)) {
          addError(accumulator, ZONE, localizationContext, null, ZONE_NOT_FOUND_IN_REGION_MSG,
              zoneName, regionName, projectId);
        }
      } catch (GoogleJsonResponseException e) {
        if (e.getStatusCode() == 404) {
          addError(accumulator, ZONE, localizationContext, null, ZONE_NOT_FOUND_MSG,
              zoneName, projectId);
        } else {
          throw new TransientProviderException(e);
        }
      } catch (IOException e) {
        throw new TransientProviderException(e);
      }
    }
  }

  /**
   * Validates the configured image.
   *
   * @param configuration       the configuration to be validated
   * @param accumulator         the exception condition accumulator
   * @param localizationContext the localization context
   */
  void checkImage(Configured configuration,
      PluginExceptionConditionAccumulator accumulator,
      LocalizationContext localizationContext) {

    String imageAlias = configuration.getConfigurationValue(IMAGE, localizationContext);

    if (imageAlias != null) {
      LOG.info(">> Querying image '{}'", imageAlias);

      Config googleConfig = provider.getGoogleConfig();
      String sourceImageUrl = null;

      try {
        sourceImageUrl = googleConfig.getString(Configurations.IMAGE_ALIASES_SECTION + imageAlias);
      } catch (ConfigException e) {
        // We don't need to propagate this message since we check sourceImageUrl directly below.
        LOG.info(e.getMessage());
      }

      if (sourceImageUrl != null && !sourceImageUrl.isEmpty()) {
        GoogleCredentials credentials = provider.getCredentials();
        Compute compute = credentials.getCompute();
        String projectId = Utils.getProject(sourceImageUrl);
        String imageLocalName = Utils.getLocalName(sourceImageUrl);

        try {
          compute.images().get(projectId, imageLocalName).execute();
        } catch (GoogleJsonResponseException e) {
          if (e.getStatusCode() == 404) {
            addError(accumulator, IMAGE, localizationContext, null, IMAGE_NOT_FOUND_MSG, imageLocalName, projectId);
          } else {
            throw new TransientProviderException(e);
          }
        } catch (IOException e) {
          throw new TransientProviderException(e);
        }
      } else {
        addError(accumulator, IMAGE, localizationContext, null, MAPPING_FOR_IMAGE_ALIAS_NOT_FOUND, imageAlias);
      }
    }
  }

  /**
   * Validates the configured boot disk size.
   *
   * @param configuration       the configuration to be validated
   * @param accumulator         the exception condition accumulator
   * @param localizationContext the localization context
   */
  void checkBootDiskSize(Configured configuration,
      PluginExceptionConditionAccumulator accumulator,
      LocalizationContext localizationContext) {

    String bootDiskSizeGBString = configuration.getConfigurationValue(BOOT_DISK_SIZE_GB, localizationContext);

    if (bootDiskSizeGBString != null) {
      try {
        int bootDiskSizeGB = Integer.parseInt(bootDiskSizeGBString);

        if (bootDiskSizeGB < MIN_BOOT_DISK_SIZE_GB) {
          addError(accumulator, BOOT_DISK_SIZE_GB, localizationContext, null, INVALID_BOOT_DISK_SIZE_MSG,
              MIN_BOOT_DISK_SIZE_GB, bootDiskSizeGB);
        }
      } catch (NumberFormatException e) {
        addError(accumulator, BOOT_DISK_SIZE_GB, localizationContext, null, INVALID_BOOT_DISK_SIZE_FORMAT_MSG,
            bootDiskSizeGBString);
      }
    }
  }

  /**
   * Validates the configured number of data disks.
   *
   * @param configuration       the configuration to be validated
   * @param accumulator         the exception condition accumulator
   * @param localizationContext the localization context
   */
  void checkDataDiskCount(Configured configuration,
      PluginExceptionConditionAccumulator accumulator,
      LocalizationContext localizationContext) {

    String dataDiskCountString = configuration.getConfigurationValue(DATA_DISK_COUNT, localizationContext);

    if (dataDiskCountString != null) {
      try {
        int dataDiskCount = Integer.parseInt(dataDiskCountString);
        String dataDiskType = configuration.getConfigurationValue(DATA_DISK_TYPE, localizationContext);
        boolean dataDisksAreLocalSSD = dataDiskType.equals("LocalSSD");

        if (dataDisksAreLocalSSD) {
          if (dataDiskCount < MIN_LOCAL_SSD_COUNT || dataDiskCount > MAX_LOCAL_SSD_COUNT) {
            addError(accumulator, DATA_DISK_COUNT, localizationContext, null, INVALID_LOCAL_SSD_DATA_DISK_COUNT_MSG,
                MIN_LOCAL_SSD_COUNT, MAX_LOCAL_SSD_COUNT, dataDiskCount);
          }
        } else if (dataDiskCount < 0) {
          addError(accumulator, DATA_DISK_COUNT, localizationContext, null, INVALID_DATA_DISK_COUNT_NEGATIVE_MSG,
              dataDiskCount);
        }
      } catch (NumberFormatException e) {
        addError(accumulator, DATA_DISK_COUNT, localizationContext, null, INVALID_DATA_DISK_COUNT_FORMAT_MSG,
            dataDiskCountString);
      }
    }
  }

  /**
   * Validates the configured data disk type.
   *
   * @param configuration       the configuration to be validated
   * @param accumulator         the exception condition accumulator
   * @param localizationContext the localization context
   */
  void checkDataDiskType(Configured configuration,
      PluginExceptionConditionAccumulator accumulator,
      LocalizationContext localizationContext) {

    String dataDiskType = configuration.getConfigurationValue(DATA_DISK_TYPE, localizationContext);

    if (dataDiskType != null && !DATA_DISK_TYPES.contains(dataDiskType)) {
      addError(accumulator, DATA_DISK_TYPE, localizationContext, null, INVALID_DATA_DISK_TYPE_MSG,
          new Object[]{dataDiskType, Joiner.on(", ").join(DATA_DISK_TYPES)});
    }
  }

  /**
   * Validates the configured data disk size.
   *
   * @param configuration       the configuration to be validated
   * @param accumulator         the exception condition accumulator
   * @param localizationContext the localization context
   */
  void checkDataDiskSize(Configured configuration,
      PluginExceptionConditionAccumulator accumulator,
      LocalizationContext localizationContext) {

    String dataDiskSizeGBString = configuration.getConfigurationValue(DATA_DISK_SIZE_GB, localizationContext);

    if (dataDiskSizeGBString != null) {
      try {
        int dataDiskSizeGB = Integer.parseInt(dataDiskSizeGBString);
        String dataDiskType = configuration.getConfigurationValue(DATA_DISK_TYPE, localizationContext);
        boolean dataDisksAreLocalSSD = dataDiskType.equals("LocalSSD");

        if (dataDisksAreLocalSSD) {
          if (dataDiskSizeGB != EXACT_LOCAL_SSD_DATA_DISK_SIZE_GB) {
            addError(accumulator, DATA_DISK_SIZE_GB, localizationContext, null, INVALID_LOCAL_SSD_DATA_DISK_SIZE_MSG,
                EXACT_LOCAL_SSD_DATA_DISK_SIZE_GB, dataDiskSizeGB);
          }
        } else if (dataDiskSizeGB < MIN_DATA_DISK_SIZE_GB) {
          addError(accumulator, DATA_DISK_SIZE_GB, localizationContext, null, INVALID_DATA_DISK_SIZE_MSG,
              MIN_DATA_DISK_SIZE_GB, dataDiskSizeGB);
        }
      } catch (NumberFormatException e) {
        addError(accumulator, DATA_DISK_SIZE_GB, localizationContext, null, INVALID_DATA_DISK_SIZE_FORMAT_MSG,
            dataDiskSizeGBString);
      }
    }
  }

  /**
   * Validates the configured machine type.
   *
   * @param configuration       the configuration to be validated
   * @param accumulator         the exception condition accumulator
   * @param localizationContext the localization context
   */
  void checkMachineType(Configured configuration,
      PluginExceptionConditionAccumulator accumulator,
      LocalizationContext localizationContext) {

    String type = configuration.getConfigurationValue(TYPE, localizationContext);

    // Machine types are a zonal resource. Only makes sense to check it if the zone itself is valid.
    Collection<PluginExceptionCondition> zoneErrors =
        accumulator.getConditionsByKey().get(ZONE.unwrap().getConfigKey());

    if (zoneErrors != null && zoneErrors.size() > 0) {
      LOG.info("Machine type '{}' not being checked since zone was not found.", type);

      return;
    }

    if (type != null) {
      LOG.info(">> Querying machine type '{}'", type);

      GoogleCredentials credentials = provider.getCredentials();
      Compute compute = credentials.getCompute();
      String projectId = credentials.getProjectId();
      String zoneName = configuration.getConfigurationValue(ZONE, localizationContext);

      try {
        compute.machineTypes().get(projectId, zoneName, type).execute();
      } catch (GoogleJsonResponseException e) {
        if (e.getStatusCode() == 404) {
          addError(accumulator, TYPE, localizationContext, null, MACHINE_TYPE_NOT_FOUND_IN_ZONE_MSG,
              type, zoneName, projectId);
        } else {
          throw new TransientProviderException(e);
        }
      } catch (IOException e) {
        throw new TransientProviderException(e);
      }
    }
  }

  /**
   * Validates the configured network.
   *
   * @param configuration       the configuration to be validated
   * @param accumulator         the exception condition accumulator
   * @param localizationContext the localization context
   */
  void checkNetwork(Configured configuration,
      PluginExceptionConditionAccumulator accumulator,
      LocalizationContext localizationContext) {

    String networkName = configuration.getConfigurationValue(NETWORK_NAME, localizationContext);

    if (networkName != null) {
      LOG.info(">> Querying network '{}'", networkName);

      GoogleCredentials credentials = provider.getCredentials();
      Compute compute = credentials.getCompute();
      String projectId = credentials.getProjectId();

      try {
        compute.networks().get(projectId, networkName).execute();
      } catch (GoogleJsonResponseException e) {
        if (e.getStatusCode() == 404) {
          addError(accumulator, TYPE, localizationContext, null, NETWORK_NOT_FOUND_MSG, networkName, projectId);
        } else {
          throw new TransientProviderException(e);
        }
      } catch (IOException e) {
        throw new TransientProviderException(e);
      }
    }
  }
}