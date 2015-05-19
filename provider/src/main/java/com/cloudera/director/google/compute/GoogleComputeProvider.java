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

import static com.cloudera.director.spi.v1.compute.ComputeInstanceTemplate.ComputeInstanceTemplateConfigurationPropertyToken.SSH_OPENSSH_PUBLIC_KEY;
import static com.cloudera.director.spi.v1.compute.ComputeInstanceTemplate.ComputeInstanceTemplateConfigurationPropertyToken.SSH_USERNAME;

import com.cloudera.director.google.internal.GoogleCredentials;
import com.cloudera.director.spi.v1.compute.ComputeInstanceTemplate;
import com.cloudera.director.spi.v1.compute.util.AbstractComputeInstance;
import com.cloudera.director.spi.v1.compute.util.AbstractComputeProvider;
import com.cloudera.director.spi.v1.model.ConfigurationProperty;
import com.cloudera.director.spi.v1.model.Configured;
import com.cloudera.director.spi.v1.model.InstanceState;
import com.cloudera.director.spi.v1.model.InstanceStatus;
import com.cloudera.director.spi.v1.model.InstanceTemplate;
import com.cloudera.director.spi.v1.model.LocalizationContext;
import com.cloudera.director.spi.v1.model.Resource;
import com.cloudera.director.spi.v1.model.exception.InvalidCredentialsException;
import com.cloudera.director.spi.v1.model.exception.TransientProviderException;
import com.cloudera.director.spi.v1.model.util.SimpleInstanceState;
import com.cloudera.director.spi.v1.model.util.SimpleResourceTemplate;
import com.cloudera.director.spi.v1.provider.ResourceProviderMetadata;
import com.cloudera.director.spi.v1.provider.util.SimpleResourceProviderMetadata;
import com.cloudera.director.spi.v1.util.ConfigurationPropertiesUtil;
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
import com.typesafe.config.Config;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class GoogleComputeProvider
    extends AbstractComputeProvider<GoogleComputeInstance, GoogleComputeInstanceTemplate> {

  private static final Logger LOG = Logger.getLogger(GoogleComputeProvider.class.getName());
  private static final int MAX_LOCAL_SSD_COUNT = 4;

  protected static final List<ConfigurationProperty> CONFIGURATION_PROPERTIES =
      ConfigurationPropertiesUtil.asConfigurationPropertyList(
          GoogleComputeProviderConfigurationProperty.values());

  public static final String ID = "compute";

  public static final ResourceProviderMetadata METADATA = SimpleResourceProviderMetadata.builder()
      .id(ID)
      .name("Google Compute Provider")
      .description("Provisions VM's on Google Compute Engine")
      .providerConfigurationProperties(CONFIGURATION_PROPERTIES)
      .resourceTemplateConfigurationProperties(
          GoogleComputeInstanceTemplate.getConfigurationProperties())
      .providerClass(GoogleComputeProvider.class)
      .build();

  private GoogleCredentials credentials;
  private Config googleConfig;

  public GoogleComputeProvider(Configured configuration, GoogleCredentials credentials,
      Config googleConfig, LocalizationContext cloudLocalizationContext) {
    super(configuration, METADATA, cloudLocalizationContext);

    this.credentials = credentials;
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
      }
    } catch (IOException e) {
      throw new TransientProviderException(e);
    }
  }

  @Override
  public ResourceProviderMetadata getProviderMetadata() {
    return METADATA;
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

    LocalizationContext providerLocalizationContext = getLocalizationContext();
    LocalizationContext templateLocalizationContext =
        SimpleResourceTemplate.getTemplateLocalizationContext(providerLocalizationContext);

    for (String instanceId : instanceIds) {
      Compute compute = credentials.getCompute();
      String projectId = credentials.getProjectId();
      String zone = template.getConfigurationValue(
          GoogleComputeInstanceTemplateConfigurationProperty.ZONE, templateLocalizationContext);
      String decoratedInstanceName = decorateInstanceName(template, instanceId, templateLocalizationContext);

      // Resolve the source image.
      String imageAlias = template.getConfigurationValue(
          GoogleComputeInstanceTemplateConfigurationProperty.IMAGE,
          templateLocalizationContext);
      String sourceImageUrl = googleConfig.getString("google.compute.imageAliases." + imageAlias);

      if (sourceImageUrl == null) {
        throw new IllegalArgumentException("Image for alias '" + imageAlias + "' not found.");
      }

      // Compose attached disks.
      List<AttachedDisk> attachedDiskList = new ArrayList<AttachedDisk>();

      // Compose the boot disk.
      long bootDiskSizeGb = Long.parseLong(template.getConfigurationValue(
          GoogleComputeInstanceTemplateConfigurationProperty.BOOTDISKSIZEGB,
          templateLocalizationContext));
      AttachedDiskInitializeParams bootDiskInitializeParams = new AttachedDiskInitializeParams();
      bootDiskInitializeParams.setSourceImage(sourceImageUrl);
      bootDiskInitializeParams.setDiskSizeGb(bootDiskSizeGb);
      AttachedDisk bootDisk = new AttachedDisk();
      bootDisk.setBoot(true);
      bootDisk.setAutoDelete(true);
      bootDisk.setInitializeParams(bootDiskInitializeParams);
      attachedDiskList.add(bootDisk);

      // Attach data disks.
      int dataDiskCount = Integer.parseInt(template.getConfigurationValue(
          GoogleComputeInstanceTemplateConfigurationProperty.DATADISKCOUNT,
          templateLocalizationContext));
      String dataDiskType = template.getConfigurationValue(
          GoogleComputeInstanceTemplateConfigurationProperty.DATADISKTYPE,
          templateLocalizationContext);
      String dataDiskTypeUrl = getDiskTypeURL(projectId, zone, dataDiskType);
      boolean dataDisksAreLocalSSD = dataDiskType.equals("LocalSSD");
      long dataDiskSizeGb = Long.parseLong(template.getConfigurationValue(
          GoogleComputeInstanceTemplateConfigurationProperty.DATADISKSIZEGB,
          templateLocalizationContext));
      String localSSDInterfaceType = template.getConfigurationValue(
          GoogleComputeInstanceTemplateConfigurationProperty.LOCALSSDINTERFACETYPE,
          templateLocalizationContext);

      if (dataDiskCount < 0) {
        throw new IllegalArgumentException("Invalid number of data disks specified: '" + dataDiskCount + "'. " +
            "Number of data disks must not be negative.");
      } else if (dataDisksAreLocalSSD && dataDiskCount > MAX_LOCAL_SSD_COUNT) {
        throw new IllegalArgumentException("Invalid number of local SSD drives specified: '" + dataDiskCount + "'. " +
            "Number of local SSD drives must be between 0 and 4 inclusive.");
      }

      // Use this list to collect the names of operations that must complete prior to provisioning the instance.
      List<String> pendingOperationNames = new ArrayList<String>();

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

            pendingOperationNames.add(diskCreationOperation.getName());
          } catch (GoogleJsonResponseException e) {
            if (e.getStatusCode() == 409) {
              LOG.info("Disk '" + persistentDisk.getName() + "' already exists.");
            } else {
              throw new RuntimeException(e);
            }
          } catch (IOException e) {
            throw new RuntimeException(e);
          }

          String persistentDiskUrl = "https://www.googleapis.com/compute/v1/projects/" + projectId +
              "/zones/" + zone + "/disks/" + persistentDisk.getName();
          attachedDisk.setType("PERSISTENT");
          attachedDisk.setSource(persistentDiskUrl);
        }

        attachedDisk.setAutoDelete(true);
        attachedDiskList.add(attachedDisk);
      }

      // Compose the network url.
      String networkName = template.getConfigurationValue(
          GoogleComputeInstanceTemplateConfigurationProperty.NETWORKNAME,
          templateLocalizationContext);
      String networkUrl = "https://www.googleapis.com/compute/v1/projects/" + projectId +
          "/global/networks/" + networkName;

      // Compose the network interface.
      String accessConfigName = "External NAT";
      final String accessConfigType = "ONE_TO_ONE_NAT";
      AccessConfig accessConfig = new AccessConfig();
      accessConfig.setName(accessConfigName);
      accessConfig.setType(accessConfigType);
      NetworkInterface networkInterface = new NetworkInterface();
      networkInterface.setNetwork(networkUrl);
      networkInterface.setAccessConfigs(Arrays.asList(new AccessConfig[]{accessConfig}));

      // Compose the machine type url.
      String machineTypeName = template.getConfigurationValue(
          GoogleComputeInstanceTemplateConfigurationProperty.TYPE,
          templateLocalizationContext);
      String machineTypeUrl = "https://www.googleapis.com/compute/v1/projects/" + projectId +
          "/zones/" + zone +
          "/machineTypes/" + machineTypeName;

      // Compose the instance metadata containing the SSH public key, user name and tags.
      String sshUserName = template.getConfigurationValue(SSH_USERNAME,
          templateLocalizationContext);
      String sshPublicKey = template.getConfigurationValue(SSH_OPENSSH_PUBLIC_KEY,
          templateLocalizationContext);
      String sshKeysValue = sshUserName + ":" + sshPublicKey;
      List<Metadata.Items> metadataItemsList = new ArrayList<Metadata.Items>();
      metadataItemsList.add(new Metadata.Items().setKey("sshKeys").setValue(sshKeysValue));

      for (Map.Entry<String, String> tag : template.getTags().entrySet()) {
        metadataItemsList.add(new Metadata.Items().setKey(tag.getKey()).setValue(tag.getValue()));
      }

      Metadata metadata = new Metadata().setItems(metadataItemsList);

      // Compose the instance.
      Instance instance = new Instance();
      instance.setMetadata(metadata);
      instance.setName(decoratedInstanceName);
      instance.setMachineType(machineTypeUrl);
      instance.setDisks(attachedDiskList);
      instance.setNetworkInterfaces(Arrays.asList(new NetworkInterface[]{networkInterface}));

      // Wait for pending operations to complete before provisioning the instance.
      pollPendingOperations(projectId, zone, pendingOperationNames, compute);

      try {
        compute.instances().insert(projectId, zone, instance).execute();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  @Override
  public Collection<GoogleComputeInstance> find(GoogleComputeInstanceTemplate template, Collection<String> instanceIds)
      throws InterruptedException {
    LocalizationContext providerLocalizationContext = getLocalizationContext();
    LocalizationContext templateLocalizationContext =
        SimpleResourceTemplate.getTemplateLocalizationContext(providerLocalizationContext);

    List<GoogleComputeInstance> result = new ArrayList<GoogleComputeInstance>();
    for (String currentId : instanceIds) {
      Compute compute = credentials.getCompute();
      String projectId = credentials.getProjectId();
      String zone = template.getConfigurationValue(
          GoogleComputeInstanceTemplateConfigurationProperty.ZONE, templateLocalizationContext);
      String decoratedInstanceName = decorateInstanceName(template, currentId, templateLocalizationContext);

      try {
        Instance instance = compute.instances().get(projectId, zone, decoratedInstanceName).execute();

        result.add(new GoogleComputeInstance(template, currentId, getInetAddressFromInstance(instance)));
      } catch (GoogleJsonResponseException e) {
        if (e.getStatusCode() == 404) {
          LOG.info("Instance '" + decoratedInstanceName + "' not found.");
        } else {
          throw new RuntimeException(e);
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    return result;
  }

  private static InetAddress getInetAddressFromInstance(Instance instance) throws UnknownHostException {
    List<NetworkInterface> networkInterfaceList = instance.getNetworkInterfaces();

    if (networkInterfaceList == null || networkInterfaceList.size() == 0) {
      LOG.info("No network interfaces found for instance '" + instance.getName() + "'.");

      return null;
    } else {
      return InetAddress.getByName(instance.getNetworkInterfaces().get(0).getNetworkIP());
    }
  }

  @Override
  public Map<String, InstanceState> getInstanceState(GoogleComputeInstanceTemplate template,
      Collection<String> instanceIds) {
    LocalizationContext providerLocalizationContext = getLocalizationContext();
    LocalizationContext templateLocalizationContext =
        SimpleResourceTemplate.getTemplateLocalizationContext(providerLocalizationContext);

    Map<String, InstanceState> result = new HashMap<String, InstanceState>();
    for (String currentId : instanceIds) {
      Compute compute = credentials.getCompute();
      String projectId = credentials.getProjectId();

      String zone = template.getConfigurationValue(
          GoogleComputeInstanceTemplateConfigurationProperty.ZONE, templateLocalizationContext);
      String decoratedInstanceName = decorateInstanceName(template, currentId, templateLocalizationContext);

      try {
        // TODO(duftler): Might want to store the entire instance representation in the InstanceState object.
        Instance instance = compute.instances().get(projectId, zone, decoratedInstanceName).execute();
        InstanceStatus instanceStatus = convertGCEInstanceStatusToDirectorInstanceStatus(instance.getStatus());

        result.put(currentId, new SimpleInstanceState(instanceStatus));
      } catch (GoogleJsonResponseException e) {
        if (e.getStatusCode() == 404) {
          LOG.info("Instance '" + decoratedInstanceName + "' not found.");

          result.put(currentId, new SimpleInstanceState(InstanceStatus.UNKNOWN));
        } else {
          throw new RuntimeException(e);
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    return result;
  }

  @Override
  public void delete(GoogleComputeInstanceTemplate template,
      Collection<String> instanceIds) throws InterruptedException {
    LocalizationContext providerLocalizationContext = getLocalizationContext();
    LocalizationContext templateLocalizationContext =
        SimpleResourceTemplate.getTemplateLocalizationContext(providerLocalizationContext);

    for (String currentId : instanceIds) {
      Compute compute = credentials.getCompute();
      String projectId = credentials.getProjectId();
      String zone = template.getConfigurationValue(
          GoogleComputeInstanceTemplateConfigurationProperty.ZONE, templateLocalizationContext);
      String decoratedInstanceName = decorateInstanceName(template, currentId, templateLocalizationContext);

      try {
        compute.instances().delete(projectId, zone, decoratedInstanceName).execute();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private static String decorateInstanceName(GoogleComputeInstanceTemplate template, String currentId,
      LocalizationContext templateLocalizationContext) {
    return template.getConfigurationValue(
        InstanceTemplate.InstanceTemplateConfigurationPropertyToken.INSTANCE_NAME_PREFIX,
        templateLocalizationContext) + "-" + currentId;
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

  private static String getDiskTypeURL(String projectId, String zone, String dataDiskType) {
    String diskTypeUrl = "https://www.googleapis.com/compute/v1/projects/" + projectId +
        "/zones/" + zone + "/diskTypes/";

    if (dataDiskType.equals("LocalSSD")) {
      diskTypeUrl += "local-ssd";
    } else if (dataDiskType.equals("SSD")) {
      diskTypeUrl += "pd-ssd";
    } else if (dataDiskType.equals("Standard")) {
      diskTypeUrl += "pd-standard";
    } else {
      throw new IllegalArgumentException("Invalid data disk type: '" + dataDiskType + "'.");
    }

    return diskTypeUrl;
  }

  // Poll until 0 pending operations remain.
  private static void pollPendingOperations(
      String projectId, String zone, List<String> pendingOperationNames, Compute compute) throws InterruptedException {
    int totalTimePollingSeconds = 0;
    int pollingTimeoutSeconds = 60;

    // Fibonacci backoff in seconds.
    int pollInterval = 1;
    int pollIncrement = 0;

    while (pendingOperationNames.size() > 0) {
      if (totalTimePollingSeconds > pollingTimeoutSeconds) {
        throw new IllegalArgumentException("Exceeded timeout of '" + pollingTimeoutSeconds + "' seconds while " +
            "polling for pending operations to complete: " + pendingOperationNames);
      }

      Thread.currentThread().sleep(pollInterval * 1000);

      totalTimePollingSeconds += pollInterval;

      List<String> completedOperationNames = new ArrayList<String>();

      for (String pendingOperationName : pendingOperationNames) {
        try {
          Operation pendingOperation = compute.zoneOperations().get(projectId, zone, pendingOperationName).execute();

          if (pendingOperation.getStatus().equals("DONE")) {
            completedOperationNames.add(pendingOperation.getName());
          }
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }

      pendingOperationNames.removeAll(completedOperationNames);

      // Update polling interval.
      int oldIncrement = pollIncrement;
      pollIncrement = pollInterval;
      pollInterval += oldIncrement;
    }
  }
}
