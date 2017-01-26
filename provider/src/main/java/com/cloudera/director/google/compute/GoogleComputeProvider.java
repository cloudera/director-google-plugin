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
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationProperty.BOOT_DISK_TYPE;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationProperty.DATA_DISK_COUNT;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationProperty.DATA_DISK_SIZE_GB;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationProperty.DATA_DISK_TYPE;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationProperty.IMAGE;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationProperty.LOCAL_SSD_INTERFACE_TYPE;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationProperty.NETWORK_NAME;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationProperty.TYPE;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationProperty.USE_PREEMPTIBLE_INSTANCES;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationProperty.ZONE;
import static com.cloudera.director.spi.v1.compute.ComputeInstanceTemplate.ComputeInstanceTemplateConfigurationPropertyToken.SSH_OPENSSH_PUBLIC_KEY;
import static com.cloudera.director.spi.v1.compute.ComputeInstanceTemplate.ComputeInstanceTemplateConfigurationPropertyToken.SSH_USERNAME;
import static com.cloudera.director.spi.v1.model.InstanceTemplate.InstanceTemplateConfigurationPropertyToken.INSTANCE_NAME_PREFIX;

import com.cloudera.director.google.Configurations;
import com.cloudera.director.google.compute.util.ComputeUrls;
import com.cloudera.director.google.util.Names;
import com.cloudera.director.google.util.Urls;
import com.cloudera.director.google.internal.GoogleCredentials;
import com.cloudera.director.spi.v1.compute.util.AbstractComputeInstance;
import com.cloudera.director.spi.v1.compute.util.AbstractComputeProvider;
import com.cloudera.director.spi.v1.model.ConfigurationProperty;
import com.cloudera.director.spi.v1.model.ConfigurationValidator;
import com.cloudera.director.spi.v1.model.Configured;
import com.cloudera.director.spi.v1.model.InstanceState;
import com.cloudera.director.spi.v1.model.InstanceStatus;
import com.cloudera.director.spi.v1.model.LocalizationContext;
import com.cloudera.director.spi.v1.model.Resource;
import com.cloudera.director.spi.v1.model.exception.InvalidCredentialsException;
import com.cloudera.director.spi.v1.model.exception.PluginExceptionCondition;
import com.cloudera.director.spi.v1.model.exception.PluginExceptionConditionAccumulator;
import com.cloudera.director.spi.v1.model.exception.PluginExceptionDetails;
import com.cloudera.director.spi.v1.model.exception.TransientProviderException;
import com.cloudera.director.spi.v1.model.exception.UnrecoverableProviderException;
import com.cloudera.director.spi.v1.model.util.CompositeConfigurationValidator;
import com.cloudera.director.spi.v1.model.util.SimpleInstanceState;
import com.cloudera.director.spi.v1.model.util.SimpleResourceTemplate;
import com.cloudera.director.spi.v1.provider.ResourceProviderMetadata;
import com.cloudera.director.spi.v1.provider.util.SimpleResourceProviderMetadata;
import com.cloudera.director.spi.v1.util.ConfigurationPropertiesUtil;
import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.AccessConfig;
import com.google.api.services.compute.model.AttachedDisk;
import com.google.api.services.compute.model.AttachedDiskInitializeParams;
import com.google.api.services.compute.model.Disk;
import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.Metadata;
import com.google.api.services.compute.model.NetworkInterface;
import com.google.api.services.compute.model.Operation;
import com.google.api.services.compute.model.Scheduling;
import com.google.api.services.compute.model.Tags;
import com.google.common.collect.Lists;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Compute provider of Google Compute instances.
 */
public class GoogleComputeProvider
    extends AbstractComputeProvider<GoogleComputeInstance, GoogleComputeInstanceTemplate> {

  private static final Logger LOG = LoggerFactory.getLogger(GoogleComputeProvider.class);

  private static final List<String> DONE_STATE = Arrays.asList("DONE");
  private static final List<String> RUNNING_OR_DONE_STATES = Arrays.asList("RUNNING", "DONE");

  protected static final List<ConfigurationProperty> CONFIGURATION_PROPERTIES =
      ConfigurationPropertiesUtil.asConfigurationPropertyList(
          GoogleComputeProviderConfigurationProperty.values());

  /**
   * The resource provider ID.
   */
  public static final String ID = GoogleComputeProvider.class.getCanonicalName();

  public static final ResourceProviderMetadata METADATA = SimpleResourceProviderMetadata.builder()
      .id(ID)
      .name("Google Compute Engine")
      .description("Google Compute Engine provider")
      .providerClass(GoogleComputeProvider.class)
      .providerConfigurationProperties(CONFIGURATION_PROPERTIES)
      .resourceTemplateConfigurationProperties(
          GoogleComputeInstanceTemplate.getConfigurationProperties())
      .resourceDisplayProperties(GoogleComputeInstance.getDisplayProperties())
      .build();

  private GoogleCredentials credentials;
  private Config applicationProperties;
  private Config googleConfig;

  private final ConfigurationValidator resourceTemplateConfigurationValidator;

  public GoogleComputeProvider(Configured configuration, GoogleCredentials credentials,
      Config applicationProperties, Config googleConfig, LocalizationContext cloudLocalizationContext) {
    super(configuration, METADATA, cloudLocalizationContext);

    this.credentials = credentials;
    this.applicationProperties = applicationProperties;
    this.googleConfig = googleConfig;

    Compute compute = credentials.getCompute();
    String projectId = credentials.getProjectId();

    // Throws GoogleJsonResponseException if no zones can be located.

    try {
      compute.zones().list(projectId).execute();

    } catch (GoogleJsonResponseException e) {
      if (e.getStatusCode() == 404) {
        throw new InvalidCredentialsException(
            "Unable to list zones in project: " + projectId, e);
      } else {
        throw new TransientProviderException(e);
      }
    } catch (IOException e) {
      throw new TransientProviderException(e);
    }

    this.resourceTemplateConfigurationValidator =
        new CompositeConfigurationValidator(METADATA.getResourceTemplateConfigurationValidator(),
            new GoogleComputeInstanceTemplateConfigurationValidator(this));
  }

  @Override
  public ResourceProviderMetadata getProviderMetadata() {
    return METADATA;
  }

  @Override
  public ConfigurationValidator getResourceTemplateConfigurationValidator() {
    return resourceTemplateConfigurationValidator;
  }

  @Override
  public Resource.Type getResourceType() {
    return AbstractComputeInstance.TYPE;
  }

  @Override
  public GoogleComputeInstanceTemplate createResourceTemplate(
      String name, Configured configuration, Map<String, String> tags) {
    return new GoogleComputeInstanceTemplate(name, configuration, tags, getLocalizationContext());
  }

  @Override
  public void allocate(GoogleComputeInstanceTemplate template,
      Collection<String> instanceIds, int minCount) throws InterruptedException {

    PluginExceptionConditionAccumulator accumulator = new PluginExceptionConditionAccumulator();

    LocalizationContext providerLocalizationContext = getLocalizationContext();
    LocalizationContext templateLocalizationContext =
        SimpleResourceTemplate.getTemplateLocalizationContext(providerLocalizationContext);

    Compute compute = credentials.getCompute();
    String projectId = credentials.getProjectId();

    // Use this list to collect successful disk creation operations in case we need to tear everything down.
    List<Operation> successfulDiskCreationOperations = new ArrayList<Operation>();

    // Use this list to collect the operations that must reach a RUNNING or DONE state prior to allocate() returning.
    List<Operation> vmCreationOperations = new ArrayList<Operation>();

    int preExistingVmCount = 0;
    for (String instanceId : instanceIds) {
      String zone = template.getConfigurationValue(ZONE, templateLocalizationContext);
      String decoratedInstanceName = decorateInstanceName(template, instanceId, templateLocalizationContext);

      // Resolve the source image.
      String imageAliasOrUrl = template.getConfigurationValue(IMAGE, templateLocalizationContext);
      String sourceImageUrl = null;

      // This property has already been validated. It will contain either an alias or a full image url.
      try {
        sourceImageUrl = googleConfig.getString(Configurations.IMAGE_ALIASES_SECTION + imageAliasOrUrl);
      } catch (ConfigException e) {
        sourceImageUrl = imageAliasOrUrl;
      }

      // Compose attached disks.
      List<AttachedDisk> attachedDiskList = new ArrayList<AttachedDisk>();

      // Compose the boot disk.
      String bootDiskType = template.getConfigurationValue(
          BOOT_DISK_TYPE,
          templateLocalizationContext);
      String bootDiskTypeUrl = ComputeUrls.buildDiskTypeUrl(projectId, zone, bootDiskType);
      long bootDiskSizeGb = Long.parseLong(template.getConfigurationValue(
          BOOT_DISK_SIZE_GB,
          templateLocalizationContext));
      AttachedDiskInitializeParams bootDiskInitializeParams = new AttachedDiskInitializeParams();
      bootDiskInitializeParams.setSourceImage(sourceImageUrl);
      bootDiskInitializeParams.setDiskType(bootDiskTypeUrl);
      bootDiskInitializeParams.setDiskSizeGb(bootDiskSizeGb);
      AttachedDisk bootDisk = new AttachedDisk();
      bootDisk.setBoot(true);
      bootDisk.setAutoDelete(true);
      bootDisk.setInitializeParams(bootDiskInitializeParams);
      attachedDiskList.add(bootDisk);

      // Attach data disks.
      int dataDiskCount = Integer.parseInt(template.getConfigurationValue(
          DATA_DISK_COUNT,
          templateLocalizationContext));
      String dataDiskType = template.getConfigurationValue(
          DATA_DISK_TYPE,
          templateLocalizationContext);
      String dataDiskTypeUrl = ComputeUrls.buildDiskTypeUrl(projectId, zone, dataDiskType);
      boolean dataDisksAreLocalSSD = dataDiskType.equals("LocalSSD");
      long dataDiskSizeGb = Long.parseLong(template.getConfigurationValue(
          DATA_DISK_SIZE_GB,
          templateLocalizationContext));
      String localSSDInterfaceType = template.getConfigurationValue(
          LOCAL_SSD_INTERFACE_TYPE,
          templateLocalizationContext);

      // Use this list to collect the operations that must reach a DONE state prior to provisioning the instance.
      List<Operation> diskCreationOperations = new ArrayList<Operation>();

      int preExistingPersistentDiskCount = 0;

      for (int i = 0; i < dataDiskCount; i++) {
        AttachedDisk attachedDisk = new AttachedDisk();

        if (dataDisksAreLocalSSD) {
          AttachedDiskInitializeParams attachedDiskInitializeParams = new AttachedDiskInitializeParams();
          attachedDiskInitializeParams.setDiskType(dataDiskTypeUrl);

          attachedDisk.setType("SCRATCH");
          attachedDisk.setInterface(localSSDInterfaceType);
          attachedDisk.setInitializeParams(attachedDiskInitializeParams);
        } else {
          // Data disks other than LocalSSD must first be provisioned before they can be attached.
          Disk persistentDisk = new Disk();
          persistentDisk.setName(decoratedInstanceName + "-pd-" + i);
          persistentDisk.setType(dataDiskTypeUrl);
          persistentDisk.setSizeGb(dataDiskSizeGb);

          try {
            // This is an async operation. We must poll until it completes to confirm the disk exists.
            Operation diskCreationOperation = compute.disks().insert(projectId, zone, persistentDisk).execute();

            diskCreationOperations.add(diskCreationOperation);
          } catch (GoogleJsonResponseException e) {
            if (e.getStatusCode() == 409) {
              LOG.info("Disk '{}' already exists.", persistentDisk.getName());

              preExistingPersistentDiskCount++;
            } else {
              accumulator.addError(null, e.getMessage());
            }
          } catch (IOException e) {
            accumulator.addError(null, e.getMessage());
          }

          String persistentDiskUrl = ComputeUrls.buildDiskUrl(projectId, zone, persistentDisk.getName());
          attachedDisk.setType("PERSISTENT");
          attachedDisk.setSource(persistentDiskUrl);
        }

        attachedDisk.setAutoDelete(true);
        attachedDiskList.add(attachedDisk);
      }

      // Compose the network url.
      String networkName = template.getConfigurationValue(NETWORK_NAME, templateLocalizationContext);
      String networkUrl = ComputeUrls.buildNetworkUrl(projectId, networkName);

      // Compose the network interface.
      String accessConfigName = "External NAT";
      final String accessConfigType = "ONE_TO_ONE_NAT";
      AccessConfig accessConfig = new AccessConfig();
      accessConfig.setName(accessConfigName);
      accessConfig.setType(accessConfigType);
      NetworkInterface networkInterface = new NetworkInterface();
      networkInterface.setNetwork(networkUrl);
      networkInterface.setAccessConfigs(Arrays.asList(accessConfig));

      // Compose the machine type url.
      String machineTypeName = template.getConfigurationValue(TYPE, templateLocalizationContext);
      String machineTypeUrl = ComputeUrls.buildMachineTypeUrl(projectId, zone, machineTypeName);

      // Compose the instance metadata containing the SSH public key, user name and tags.
      List<Metadata.Items> metadataItemsList = new ArrayList<Metadata.Items>();

      String sshUserName = template.getConfigurationValue(SSH_USERNAME, templateLocalizationContext);
      String sshPublicKey = template.getConfigurationValue(SSH_OPENSSH_PUBLIC_KEY, templateLocalizationContext);

      if (sshUserName != null && !sshUserName.isEmpty() && sshPublicKey != null && !sshPublicKey.isEmpty()) {
        String sshKeysValue = sshUserName + ":" + sshPublicKey;

        metadataItemsList.add(new Metadata.Items().setKey("sshKeys").setValue(sshKeysValue));
      } else {
        LOG.info(
            "SSH credentials not set on instance '{}'. " +
            "More information on configuring SSH keys can be found here: " +
            "https://cloud.google.com/compute/docs/console#sshkeys",
            decoratedInstanceName);
      }

      for (Map.Entry<String, String> tag : template.getTags().entrySet()) {
        metadataItemsList.add(new Metadata.Items().setKey(tag.getKey()).setValue(tag.getValue()));
      }

      Metadata metadata = new Metadata().setItems(metadataItemsList);

      boolean usePreemptibleInstances =
          Boolean.parseBoolean(template.getConfigurationValue(USE_PREEMPTIBLE_INSTANCES, templateLocalizationContext));
      Scheduling scheduling = new Scheduling();
      scheduling.setPreemptible(usePreemptibleInstances);

      // Compose the instance.
      Instance instance = new Instance();
      instance.setMetadata(metadata);
      instance.setName(decoratedInstanceName);
      instance.setMachineType(machineTypeUrl);
      instance.setDisks(attachedDiskList);
      instance.setNetworkInterfaces(Arrays.asList(networkInterface));
      instance.setScheduling(scheduling);

      // Compose the tags for the instance, including a tag identifying the plugin and version used to create it.
      // This is not the same as the template 'tags' which are propagated as instance metadata.
      Tags tags = new Tags();
      String applicationNameVersionTag = Names.buildApplicationNameVersionTag(applicationProperties);
      // Massage it into a form acceptable for use as a tag (only allows lowercase letters, numbers and hyphens).
      applicationNameVersionTag = applicationNameVersionTag.toLowerCase().replaceAll("\\.|/", "-");
      tags.setItems(Lists.newArrayList(applicationNameVersionTag));
      instance.setTags(tags);

      // Wait for operations to reach DONE state before provisioning the instance.
      List<Operation> successfulOperations = pollPendingOperations(projectId, diskCreationOperations, DONE_STATE,
          compute, googleConfig, accumulator);

      // We need to ensure that any data disks that were successfully created are deleted in case of teardown.
      for (Operation successfulOperation : successfulOperations) {
        successfulDiskCreationOperations.add(successfulOperation);
      }

      if (dataDisksAreLocalSSD || preExistingPersistentDiskCount + successfulOperations.size() == dataDiskCount) {
        try {
          Operation vmCreationOperation = compute.instances().insert(projectId, zone, instance).execute();

          vmCreationOperations.add(vmCreationOperation);
        }
        catch (GoogleJsonResponseException e) {
          if (hasError(e, 409, "alreadyExists")) {
            preExistingVmCount++;
          }
          accumulator.addError(null, e.getMessage());
        }
        catch (IOException e) {
          accumulator.addError(null, e.getMessage());
        }
      }
    }

    // Wait for operations to reach DONE state before returning.
    // This is the status of the Operations we're referring to, not of the Instances.
    List<Operation> successfulOperations = pollPendingOperations(projectId, vmCreationOperations, DONE_STATE,
        compute, googleConfig, accumulator);

    int successfulOperationCount = successfulOperations.size();

    // Requested instances that already exist are counted as a successful creation
    successfulOperationCount += preExistingVmCount;

    if (successfulOperationCount < minCount) {
      LOG.info("Provisioned {} instances out of {}. minCount is {}. Tearing down provisioned instances.",
          successfulOperationCount, instanceIds.size(), minCount);

      tearDownResources(projectId, vmCreationOperations, successfulDiskCreationOperations, compute, accumulator);

      PluginExceptionDetails pluginExceptionDetails = new PluginExceptionDetails(accumulator.getConditionsByKey());
      throw new UnrecoverableProviderException("Problem allocating instances.", pluginExceptionDetails);
    } else if (successfulOperationCount < instanceIds.size()) {
      LOG.info("Provisioned {} instances out of {}. minCount is {}.",
          successfulOperationCount, instanceIds.size(), minCount);

      // Even through we are not throwing an exception, we still want to log the errors.
      if (accumulator.hasError()) {
        Map<String, Collection<PluginExceptionCondition>> conditionsByKeyMap = accumulator.getConditionsByKey();

        for (Map.Entry<String, Collection<PluginExceptionCondition>> keyToCondition : conditionsByKeyMap.entrySet()) {
          String key = keyToCondition.getKey();

          if (key != null) {
            for (PluginExceptionCondition condition : keyToCondition.getValue()) {
              LOG.info("({}) {}: {}", condition.getType(), key, condition.getMessage());
            }
          } else {
            for (PluginExceptionCondition condition : keyToCondition.getValue()) {
              LOG.info("({}) {}", condition.getType(), condition.getMessage());
            }
          }
        }
      }
    }
  }

  // Check if GoogleJsonResponseException has both the specified status code and error reason
  private boolean hasError(GoogleJsonResponseException ex, int code, String reason) {
    if (ex.getStatusCode() != code) {
      return false;
    }
    List<GoogleJsonError.ErrorInfo> errors = ex.getDetails().getErrors();
    for (GoogleJsonError.ErrorInfo error : errors) {
      if (error.getReason().equals(reason)) {
        return true;
      }
    }
    return false;
  }

  // Delete all persistent disks and instances.
  private void tearDownResources(String projectId, List<Operation> vmCreationOperations,
      List<Operation> diskCreationOperations, Compute compute,
      PluginExceptionConditionAccumulator accumulator) throws InterruptedException {

    // Use this map to allow for pruning the set of persistent disks that must be deleted.
    // Disks already attached to an instance will be automatically deleted when the instance is deleted.
    // So we don't need to delete it explicitly.
    Map<String, Operation> diskNameToCreationOperationMap = new HashMap<String, Operation>();

    // Create a mapping from full resource url to the operation for each disk created.
    for (Operation diskCreationOperation : diskCreationOperations) {
      diskNameToCreationOperationMap.put(diskCreationOperation.getTargetLink(), diskCreationOperation);
    }

    // Were any persistent disks created at all?
    boolean persistentDisksMustBeDeleted = diskNameToCreationOperationMap.size() > 0;

    // Use this list to keep track of all disk and instance deletion operations.
    List<Operation> tearDownOperations = new ArrayList<Operation>();

    // Iterate over each instance creation operation.
    for (Operation vmCreationOperation : vmCreationOperations) {
      String zone = Urls.getLocalName(vmCreationOperation.getZone());
      String instanceName = Urls.getLocalName(vmCreationOperation.getTargetLink());

      try {
        // If any persistent disks were created, retrieve each instance representation and remove its attached disks
        // from the set of disks that must be explicitly deleted.
        if (persistentDisksMustBeDeleted) {
          Instance instance = compute.instances().get(projectId, zone, instanceName).execute();

          for (AttachedDisk attachedDisk : instance.getDisks()) {
            diskNameToCreationOperationMap.remove(attachedDisk.getSource());
          }
        }

        Operation tearDownOperation = compute.instances().delete(projectId, zone, instanceName).execute();

        tearDownOperations.add(tearDownOperation);
      } catch (GoogleJsonResponseException e) {
        if (e.getStatusCode() == 404) {
          // Since we try to tear down all instances, and some may not have been successfully provisioned in the first
          // place, we don't need to propagate this.
        } else {
          accumulator.addError(null, e.getMessage());
        }
      } catch (IOException e) {
        accumulator.addError(null, e.getMessage());
      }
    }

    // Delete each persistent disk that is not attached to an instance.
    for (Operation diskCreationOperation : diskNameToCreationOperationMap.values()) {
      String zone = Urls.getLocalName(diskCreationOperation.getZone());
      String diskName = Urls.getLocalName(diskCreationOperation.getTargetLink());

      try {
        Operation tearDownOperation = compute.disks().delete(projectId, zone, diskName).execute();

        tearDownOperations.add(tearDownOperation);
      } catch (GoogleJsonResponseException e) {
        if (e.getStatusCode() == 404) {
          // Ignore this.
        } else {
          accumulator.addError(null, e.getMessage());
        }
      } catch (IOException e) {
        accumulator.addError(null, e.getMessage());
      }
    }

    List<Operation> successfulTearDownOperations = pollPendingOperations(projectId, tearDownOperations, DONE_STATE,
        compute, googleConfig, accumulator);
    int tearDownOperationCount = tearDownOperations.size();
    int successfulTearDownOperationCount = successfulTearDownOperations.size();

    if (successfulTearDownOperationCount < tearDownOperationCount) {
      accumulator.addError(null, successfulTearDownOperationCount + " of the " + tearDownOperationCount +
          " tear down operations completed successfully.");
    }
  }

  @Override
  public Collection<GoogleComputeInstance> find(GoogleComputeInstanceTemplate template, Collection<String> instanceIds)
      throws InterruptedException {
    LocalizationContext providerLocalizationContext = getLocalizationContext();
    LocalizationContext templateLocalizationContext =
        SimpleResourceTemplate.getTemplateLocalizationContext(providerLocalizationContext);

    List<GoogleComputeInstance> result = new ArrayList<GoogleComputeInstance>();

    // If the prefix is not valid, there is no way the instances could have been created in the first place.
    if (!isPrefixValid(template, templateLocalizationContext)) {
      return result;
    }

    for (String currentId : instanceIds) {
      Compute compute = credentials.getCompute();
      String projectId = credentials.getProjectId();
      String zone = template.getConfigurationValue(ZONE, templateLocalizationContext);
      String decoratedInstanceName = decorateInstanceName(template, currentId, templateLocalizationContext);

      try {
        Instance instance = compute.instances().get(projectId, zone, decoratedInstanceName).execute();
        Disk bootDisk = getBootDisk(projectId, zone, instance, compute);

        if (bootDisk == null) {
          throw new IllegalArgumentException("Boot disk not found for instance '" + instance.getName() + "'.");
        }

        result.add(new GoogleComputeInstance(template, currentId, instance, bootDisk));
      } catch (GoogleJsonResponseException e) {
        if (e.getStatusCode() == 404) {
          LOG.info("Instance '{}' not found.", decoratedInstanceName);
        } else {
          throw new RuntimeException(e);
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    return result;
  }

  private Disk getBootDisk(String projectId, String zone, Instance instance, Compute compute) {
    List<AttachedDisk> attachedDisks = instance.getDisks();
    AttachedDisk attachedBootDisk = null;
    if (attachedDisks != null) {
      for (AttachedDisk attachedDisk : attachedDisks) {
        if (attachedDisk.getBoot()) {
          attachedBootDisk = attachedDisk;
        }
      }
    }

    Disk bootDisk = null;

    if (attachedBootDisk != null) {
      String bootDiskName = Urls.getLocalName(attachedBootDisk.getSource());

      try {
        bootDisk = compute.disks().get(projectId, zone, bootDiskName).execute();
      } catch (GoogleJsonResponseException e) {
        if (e.getStatusCode() == 404) {
          LOG.info("Boot disk '{}' not found for instance '{}'.", bootDiskName, instance.getName());
        } else {
          throw new RuntimeException(e);
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    return bootDisk;
  }

  @Override
  public Map<String, InstanceState> getInstanceState(GoogleComputeInstanceTemplate template,
      Collection<String> instanceIds) {
    LocalizationContext providerLocalizationContext = getLocalizationContext();
    LocalizationContext templateLocalizationContext =
        SimpleResourceTemplate.getTemplateLocalizationContext(providerLocalizationContext);

    Map<String, InstanceState> result = new HashMap<String, InstanceState>();

    // If the prefix is not valid, there is no way the instances could have been created in the first place.
    if (!isPrefixValid(template, templateLocalizationContext)) {
      for (String currentId : instanceIds) {
        result.put(currentId, new SimpleInstanceState(InstanceStatus.UNKNOWN));
      }
    } else {
      for (String currentId : instanceIds) {
        Compute compute = credentials.getCompute();
        String projectId = credentials.getProjectId();

        String zone = template.getConfigurationValue(ZONE, templateLocalizationContext);
        String decoratedInstanceName = decorateInstanceName(template, currentId, templateLocalizationContext);

        try {
          // TODO(duftler): Might want to store the entire instance representation in the InstanceState object.
          Instance instance = compute.instances().get(projectId, zone, decoratedInstanceName).execute();
          InstanceStatus instanceStatus = convertGCEInstanceStatusToDirectorInstanceStatus(instance.getStatus());

          result.put(currentId, new SimpleInstanceState(instanceStatus));
        } catch (GoogleJsonResponseException e) {
          if (e.getStatusCode() == 404) {
            LOG.info("Instance '{}' not found.", decoratedInstanceName);

            result.put(currentId, new SimpleInstanceState(InstanceStatus.UNKNOWN));
          } else {
            throw new RuntimeException(e);
          }
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    }
    return result;
  }

  @Override
  public void delete(GoogleComputeInstanceTemplate template,
      Collection<String> instanceIds) throws InterruptedException {

    PluginExceptionConditionAccumulator accumulator = new PluginExceptionConditionAccumulator();

    LocalizationContext providerLocalizationContext = getLocalizationContext();
    LocalizationContext templateLocalizationContext =
        SimpleResourceTemplate.getTemplateLocalizationContext(providerLocalizationContext);

    // If the prefix is not valid, there is no way the instances could have been created in the first place.
    // So we shouldn't attempt to delete them, but we also shouldn't report an error.
    if (!isPrefixValid(template, templateLocalizationContext)) {
      return;
    }

    Compute compute = credentials.getCompute();
    String projectId = credentials.getProjectId();

    // Use this list to collect the operations that must reach a RUNNING or DONE state prior to delete() returning.
    List<Operation> vmDeletionOperations = new ArrayList<Operation>();

    for (String currentId : instanceIds) {
      String zone = template.getConfigurationValue(ZONE, templateLocalizationContext);
      String decoratedInstanceName = decorateInstanceName(template, currentId, templateLocalizationContext);

      try {
        Operation vmDeletionOperation = compute.instances().delete(projectId, zone, decoratedInstanceName).execute();

        vmDeletionOperations.add(vmDeletionOperation);
      } catch (GoogleJsonResponseException e) {
        if (e.getStatusCode() == 404) {
          LOG.info("Attempted to delete instance '{}', but it does not exist.", decoratedInstanceName);
        } else {
          accumulator.addError(null, e.getMessage());
        }
      } catch (IOException e) {
        accumulator.addError(null, e.getMessage());
      }
    }

    // Wait for operations to reach RUNNING or DONE state before returning.
    // Quotas are verified prior to reaching the RUNNING state.
    // This is the status of the Operations we're referring to, not of the Instances.
    pollPendingOperations(projectId, vmDeletionOperations, RUNNING_OR_DONE_STATES, compute, googleConfig,
        accumulator);

    if (accumulator.hasError()) {
      PluginExceptionDetails pluginExceptionDetails = new PluginExceptionDetails(accumulator.getConditionsByKey());
      throw new UnrecoverableProviderException("Problem deleting instances.", pluginExceptionDetails);
    }
  }

  public GoogleCredentials getCredentials() {
    return credentials;
  }

  public Config getGoogleConfig() {
    return googleConfig;
  }

  private static String decorateInstanceName(GoogleComputeInstanceTemplate template, String currentId,
      LocalizationContext templateLocalizationContext) {
    return template.getConfigurationValue(INSTANCE_NAME_PREFIX, templateLocalizationContext) + "-" + currentId;
  }

  private static InstanceStatus convertGCEInstanceStatusToDirectorInstanceStatus(String gceInstanceStatus) {
    if (gceInstanceStatus.equals("PROVISIONING") || gceInstanceStatus.equals("STAGING")) {
      return InstanceStatus.PENDING;
    } else if (gceInstanceStatus.equals("RUNNING")) {
      return InstanceStatus.RUNNING;
    } else if (gceInstanceStatus.equals("STOPPING")) {
      return InstanceStatus.STOPPING;
    } else if (gceInstanceStatus.equals("TERMINATED")) {
      return InstanceStatus.STOPPED;
    } else {
      return InstanceStatus.UNKNOWN;
    }
  }

  // Poll until 0 operations remain in the passed pendingOperations list.
  // An operation is removed from the list once it reaches one of the states in acceptableStates.
  // The list is cloned and not directly modified.
  // All arguments are required and must be non-null.
  // Returns the number of operations that reached one of the acceptable states within the timeout period.
  private static List<Operation> pollPendingOperations(String projectId, List<Operation> origPendingOperations,
      List<String> acceptableStates, Compute compute, Config googleConfig,
      PluginExceptionConditionAccumulator accumulator)
          throws InterruptedException {
    // Clone the list so we can prune it without modifying the original.
    List<Operation> pendingOperations = new ArrayList<Operation>(origPendingOperations);

    int totalTimePollingSeconds = 0;
    int pollingTimeoutSeconds = googleConfig.getInt(Configurations.COMPUTE_POLLING_TIMEOUT_KEY);
    int maxPollingIntervalSeconds = googleConfig.getInt(Configurations.COMPUTE_MAX_POLLING_INTERVAL_KEY);
    boolean timeoutExceeded = false;

    // Fibonacci backoff in seconds, up to maxPollingIntervalSeconds interval.
    int pollInterval = 1;
    int pollIncrement = 0;

    // Use this list to keep track of each operation that reached one of the acceptable states.
    List<Operation> successfulOperations = new ArrayList<Operation>();

    while (pendingOperations.size() > 0 && !timeoutExceeded) {
      Thread.sleep(pollInterval * 1000);

      totalTimePollingSeconds += pollInterval;

      List<Operation> completedOperations = new ArrayList<Operation>();

      for (Operation pendingOperation : pendingOperations) {
        try {
          String zone = Urls.getLocalName(pendingOperation.getZone());
          String pendingOperationName = pendingOperation.getName();
          Operation subjectOperation = compute.zoneOperations().get(projectId, zone, pendingOperationName).execute();
          Operation.Error error = subjectOperation.getError();
          boolean isActualError = false;

          if (error != null) {
            List<Operation.Error.Errors> errorsList = error.getErrors();

            if (errorsList != null) {
              for (Operation.Error.Errors errors : errorsList) {
                String operationType = subjectOperation.getOperationType();
                String errorCode = errors.getCode();

                // We want insertion and deletion operations to be idempotent.
                if (operationType.equals("insert") && errorCode.equals("RESOURCE_ALREADY_EXISTS")) {
                  LOG.info("Attempted to create resource '{}', but it already exists.",
                      Urls.getLocalName(subjectOperation.getTargetLink()));
                } else if (operationType.equals("delete") && errorCode.equals("RESOURCE_NOT_FOUND")) {
                  LOG.info("Attempted to delete resource '{}', but it does not exist.",
                      Urls.getLocalName(subjectOperation.getTargetLink()));
                } else if (operationType.equals("delete") && errorCode.equals("RESOURCE_NOT_READY")) {
                  LOG.info("Attempted to delete resource '{}', but it is not ready.",
                      Urls.getLocalName(subjectOperation.getTargetLink()));
                } else {
                  accumulator.addError(null, errors.getMessage());
                  isActualError = true;
                }
              }
            }
          }

          if (acceptableStates.contains(subjectOperation.getStatus())) {
            completedOperations.add(pendingOperation);

            if (!isActualError) {
              successfulOperations.add(pendingOperation);
            }
          }
        } catch (IOException e) {
          accumulator.addError(null, e.getMessage());
        }
      }

      // Remove all operations that reached an acceptable state.
      pendingOperations.removeAll(completedOperations);

      if (pendingOperations.size() > 0 && totalTimePollingSeconds > pollingTimeoutSeconds) {
        List<String> pendingOperationNames = new ArrayList<String>();

        for (Operation pendingOperation : pendingOperations) {
          pendingOperationNames.add(pendingOperation.getName());
        }

        accumulator.addError(null, "Exceeded timeout of '" + pollingTimeoutSeconds + "' seconds while " +
            "polling for pending operations to complete: " + pendingOperationNames);

        timeoutExceeded = true;
      } else {
        // Update polling interval.
        int oldIncrement = pollIncrement;
        pollIncrement = pollInterval;
        pollInterval += oldIncrement;
        pollInterval = Math.min(pollInterval, maxPollingIntervalSeconds);
      }
    }

    return successfulOperations;
  }

  private boolean isPrefixValid(GoogleComputeInstanceTemplate template,
      LocalizationContext templateLocalizationContext) {
    PluginExceptionConditionAccumulator accumulator = new PluginExceptionConditionAccumulator();

    GoogleComputeInstanceTemplateConfigurationValidator.checkPrefix(template, accumulator, templateLocalizationContext);

    boolean isValid = accumulator.getConditionsByKey().isEmpty();

    if (!isValid) {
      LOG.info("Instance name prefix '{}' is invalid.",
          template.getConfigurationValue(INSTANCE_NAME_PREFIX, templateLocalizationContext));
    }

    return isValid;
  }

}
