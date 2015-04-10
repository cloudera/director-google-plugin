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

import com.cloudera.director.google.internal.GoogleCredentials;
import com.cloudera.director.spi.v1.compute.ComputeInstanceTemplate;
import com.cloudera.director.spi.v1.compute.util.AbstractComputeInstance;
import com.cloudera.director.spi.v1.compute.util.AbstractComputeProvider;
import com.cloudera.director.spi.v1.model.*;
import com.cloudera.director.spi.v1.model.InstanceTemplate;
import com.cloudera.director.spi.v1.model.util.SimpleInstanceState;
import com.cloudera.director.spi.v1.provider.ResourceProviderMetadata;
import com.cloudera.director.spi.v1.provider.util.SimpleResourceProviderMetadata;
import com.cloudera.director.spi.v1.util.ConfigurationPropertiesUtil;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.*;
import com.google.api.services.compute.model.Instance;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.logging.Logger;

public class GoogleComputeProvider
        extends AbstractComputeProvider<GoogleComputeInstance, GoogleComputeInstanceTemplate> {

  private static final Logger LOG = Logger.getLogger(GoogleComputeProvider.class.getName());

  protected static final List<ConfigurationProperty> CONFIGURATION_PROPERTIES =
      ConfigurationPropertiesUtil.asList(GoogleComputeProviderConfigurationProperty.values());

  public static final String ID = "compute";

  public static final ResourceProviderMetadata METADATA = SimpleResourceProviderMetadata.builder()
      .id(ID)
      .name("Google Compute Provider")
      .description("Provisions VM's on Google Compute Engine")
      .providerConfigurationProperties(CONFIGURATION_PROPERTIES)
      .resourceTemplateConfigurationProperties(GoogleComputeInstanceTemplate.getConfigurationProperties())
      .build();

  private GoogleCredentials credentials;

  public GoogleComputeProvider(Configured configuration, GoogleCredentials credentials) {
    super(configuration);

    this.credentials = credentials;

    Compute compute = credentials.getCompute();
    String projectId = credentials.getProjectId();
    String zone = getConfigurationValue(GoogleComputeInstanceTemplateConfigurationProperty.ZONE);

    // Throws GoogleJsonResponseException if the zone cannot be located.
    // Note: Not all projects have access to the same zones.
    try {
      compute.zones().get(projectId, zone).execute();
    } catch (GoogleJsonResponseException e) {
      if (e.getStatusCode() == 404) {
        throw new IllegalArgumentException("Zone '" + zone + "' not found.");
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
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
    return new GoogleComputeInstanceTemplate(name, configuration, tags);
  }

  @Override
  public Collection<GoogleComputeInstance> allocate(GoogleComputeInstanceTemplate template,
      Collection<String> instanceIds, int minCount) throws InterruptedException {
    List<GoogleComputeInstance> result = new ArrayList<GoogleComputeInstance>();

    for (String instanceId : instanceIds) {
      Compute compute = credentials.getCompute();
      String projectId = credentials.getProjectId();
      String zone = getConfigurationValue(GoogleComputeInstanceTemplateConfigurationProperty.ZONE);
      String decoratedInstanceName = decorateInstanceName(template, instanceId);

      // Resolve the source image.
      String imageAlias = template.getConfigurationValue(
              ComputeInstanceTemplate.ComputeInstanceTemplateConfigurationProperty.IMAGE);
      String sourceImageUrl = GoogleComputeProviderDefaults.IMAGE_ALIAS_TO_RESOURCE_MAP.get(imageAlias);

      if (sourceImageUrl == null) {
        throw new IllegalArgumentException("Image for alias '" + imageAlias + "' not found.");
      }

      // Compose the boot disk.
      // TODO(duftler): Add support for attaching local ssd drives.
      // https://github.com/cloudera/director-google-plugin/issues/3
      AttachedDiskInitializeParams attachedDiskInitializeParams = new AttachedDiskInitializeParams();
      attachedDiskInitializeParams.setSourceImage(sourceImageUrl);
      AttachedDisk bootDisk = new AttachedDisk();
      bootDisk.setBoot(true);
      bootDisk.setAutoDelete(true);
      bootDisk.setInitializeParams(attachedDiskInitializeParams);

      // Compose the network url.
      String networkName = template.getConfigurationValue(
              GoogleComputeInstanceTemplateConfigurationProperty.NETWORKNAME);
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
              ComputeInstanceTemplate.ComputeInstanceTemplateConfigurationProperty.TYPE);
      String machineTypeUrl = "https://www.googleapis.com/compute/v1/projects/" + projectId +
                              "/zones/" + zone +
                              "/machineTypes/" + machineTypeName;

      // Compose the instance.
      Instance instance = new Instance();
      instance.setName(decoratedInstanceName);
      instance.setMachineType(machineTypeUrl);
      instance.setDisks(Arrays.asList(new AttachedDisk[]{bootDisk}));
      instance.setNetworkInterfaces(Arrays.asList(new NetworkInterface[]{networkInterface}));

      try {
        compute.instances().insert(projectId, zone, instance).execute();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }

      result.add(new GoogleComputeInstance(template, instanceId, null));
    }

    return result;
  }

  @Override
  public Collection<GoogleComputeInstance> find(GoogleComputeInstanceTemplate template, Collection<String> instanceIds)
      throws InterruptedException {
    List<GoogleComputeInstance> result = new ArrayList<GoogleComputeInstance>();
    for (String currentId : instanceIds) {
      Compute compute = credentials.getCompute();
      String projectId = credentials.getProjectId();
      String zone = getConfigurationValue(GoogleComputeInstanceTemplateConfigurationProperty.ZONE);
      String decoratedInstanceName = decorateInstanceName(template, currentId);

      try {
        Instance instance = compute.instances().get(projectId, zone, decoratedInstanceName).execute();

        result.add(new GoogleComputeInstance(template, instance.getName(), getInetAddressFromInstance(instance)));
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
    Map<String, InstanceState> result = new HashMap<String, InstanceState>();
    for (String currentId : instanceIds) {
      Compute compute = credentials.getCompute();
      String projectId = credentials.getProjectId();
      String zone = getConfigurationValue(GoogleComputeInstanceTemplateConfigurationProperty.ZONE);
      String decoratedInstanceName = decorateInstanceName(template, currentId);

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
    for (String currentId : instanceIds) {
      Compute compute = credentials.getCompute();
      String projectId = credentials.getProjectId();
      String zone = getConfigurationValue(GoogleComputeInstanceTemplateConfigurationProperty.ZONE);
      String decoratedInstanceName = decorateInstanceName(template, currentId);

      try {
        compute.instances().delete(projectId, zone, decoratedInstanceName).execute();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private static String decorateInstanceName(GoogleComputeInstanceTemplate template, String currentId) {
    return template.getConfigurationValue(
            InstanceTemplate.InstanceTemplateConfigurationProperty.INSTANCE_NAME_PREFIX) + "-" + currentId;
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
}
