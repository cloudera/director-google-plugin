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

import com.cloudera.director.google.util.Dates;
import com.cloudera.director.google.util.Urls;
import com.cloudera.director.spi.v1.compute.util.AbstractComputeInstance;
import com.cloudera.director.spi.v1.model.DisplayProperty;
import com.cloudera.director.spi.v1.model.DisplayPropertyToken;
import com.cloudera.director.spi.v1.model.util.SimpleDisplayPropertyBuilder;
import com.cloudera.director.spi.v1.util.DisplayPropertiesUtil;
import com.google.api.services.compute.model.AccessConfig;
import com.google.api.services.compute.model.Disk;
import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.NetworkInterface;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Google Compute instance.
 */
public class GoogleComputeInstance
    extends AbstractComputeInstance<GoogleComputeInstanceTemplate, Instance> {

  private static final Logger LOG = LoggerFactory.getLogger(GoogleComputeInstance.class);

  /**
   * The list of display properties (including inherited properties).
   */
  private static final List<DisplayProperty> DISPLAY_PROPERTIES =
      DisplayPropertiesUtil.asDisplayPropertyList(GoogleComputeInstanceDisplayPropertyToken.values());

  /**
   * Returns the list of display properties for a Google instance, including inherited properties.
   *
   * @return the list of display properties for a Google instance, including inherited properties
   */
  public static List<DisplayProperty> getDisplayProperties() {
    return DISPLAY_PROPERTIES;
  }

  /**
   * Google compute instance display properties.
   */
  public enum GoogleComputeInstanceDisplayPropertyToken implements DisplayPropertyToken {

    IMAGE_ID(new SimpleDisplayPropertyBuilder()
        .displayKey("imageId")
        .name("Image ID")
        .defaultDescription("The ID of the image used to launch the instance.")
        .sensitive(false)
        .build()) {
      @Override
      protected String getPropertyValue(Instance instance, Disk bootDisk) {
        return Urls.getLocalName(bootDisk.getSourceImage());
      }
    },

    /**
     * The ID of the instance.
     */
    INSTANCE_ID(new SimpleDisplayPropertyBuilder()
        .displayKey("instanceId")
        .name("Instance ID")
        .defaultDescription("The ID of the instance.")
        .sensitive(false)
        .build()) {
      @Override
      protected String getPropertyValue(Instance instance, Disk bootDisk) {
        return instance.getName();
      }
    },

    /**
     * The instance type.
     */
    INSTANCE_TYPE(new SimpleDisplayPropertyBuilder()
        .displayKey("instanceType")
        .name("Machine type")
        .defaultDescription("The instance type.")
        .sensitive(false)
        .build()) {
      @Override
      protected String getPropertyValue(Instance instance, Disk bootDisk) {
        return Urls.getLocalName(instance.getMachineType());
      }
    },

    /**
     * The time the instance was launched.
     */
    LAUNCH_TIME(new SimpleDisplayPropertyBuilder()
        .displayKey("launchTime")
        .name("Launch time")
        .defaultDescription("The time the instance was launched.")
        .sensitive(false)
        .build()) {
      @Override
      protected String getPropertyValue(Instance instance, Disk bootDisk) {
        String creationTimestampStr = null;

        try {
          creationTimestampStr = instance.getCreationTimestamp();

          Date creationTimestamp = null;

          if (creationTimestampStr != null) {
            creationTimestamp = Dates.getDateFromTimestamp(creationTimestampStr);
          }

          if (creationTimestamp != null) {
            // TODO(duftler): Use appropriate date formatting.
            return creationTimestamp.toString();
          }
        } catch (IllegalArgumentException e) {
          LOG.info("Problem parsing creation timestamp '{}' of instance '{}': {}",
              creationTimestampStr, instance.getName(), e.getMessage());
        }

        return null;
      }
    },

    /**
     * The private IP address assigned to the instance.
     */
    PRIVATE_IP_ADDRESS(new SimpleDisplayPropertyBuilder()
        .displayKey("privateIpAddress")
        .name("Internal IP")
        .defaultDescription("The private IP address assigned to the instance.")
        .sensitive(false)
        .build()) {
      @Override
      protected String getPropertyValue(Instance instance, Disk bootDisk) {
        List<NetworkInterface> networkInterfaceList = instance.getNetworkInterfaces();

        if (networkInterfaceList != null && networkInterfaceList.size() > 0) {
          return networkInterfaceList.get(0).getNetworkIP();
        }

        return null;
      }
    },

    /**
     * The public IP address assigned to the instance.
     */
    PUBLIC_IP_ADDRESS(new SimpleDisplayPropertyBuilder()
        .displayKey("publicIpAddress")
        .name("External IP")
        .defaultDescription("The public IP address assigned to the instance.")
        .sensitive(false)
        .build()) {
      @Override
      protected String getPropertyValue(Instance instance, Disk bootDisk) {
        List<NetworkInterface> networkInterfaceList = instance.getNetworkInterfaces();

        if (networkInterfaceList != null && networkInterfaceList.size() > 0) {
          List<AccessConfig> accessConfigList = networkInterfaceList.get(0).getAccessConfigs();

          if (accessConfigList != null && accessConfigList.size() > 0) {
            return accessConfigList.get(0).getNatIP();
          }
        }

        return null;
      }
    };

    /**
     * The display property.
     */
    private final DisplayProperty displayProperty;

    /**
     * Creates a Google instance display property token with the specified parameters.
     *
     * @param displayProperty the display property
     */
    GoogleComputeInstanceDisplayPropertyToken(DisplayProperty displayProperty) {
      this.displayProperty = displayProperty;
    }

    /**
     * Returns the value of the property from the specified instance.
     *
     * @param instance the instance
     * @return the value of the property from the specified instance
     */
    protected abstract String getPropertyValue(Instance instance, Disk bootDisk);

    @Override
    public DisplayProperty unwrap() {
      return displayProperty;
    }
  }

  public static final Type TYPE = new ResourceType("GoogleComputeInstance");

  private Disk bootDisk = null;

  /**
   * Creates a Google compute instance with the specified parameters.
   *
   * @param template        the template from which the instance was created
   * @param instanceId      the instance identifier
   * @param instanceDetails the provider-specific instance details
   * @throws IllegalArgumentException if the instance does not have a valid private IP address
   */
  protected GoogleComputeInstance(GoogleComputeInstanceTemplate template,
      String instanceId, Instance instanceDetails, Disk bootDisk) {
    super(template, instanceId, getPrivateIpAddress(instanceDetails), null, instanceDetails);

    this.bootDisk = bootDisk;
  }

  /**
   * Returns the private IP address of the specified Google instance.
   *
   * @param instance the instance
   * @return the private IP address of the specified Google instance
   * @throws IllegalArgumentException if the instance does not have a valid private IP address
   */
  private static InetAddress getPrivateIpAddress(Instance instance) {
    Preconditions.checkNotNull(instance, "instance is null");

    List<NetworkInterface> networkInterfaceList = instance.getNetworkInterfaces();

    if (networkInterfaceList == null || networkInterfaceList.isEmpty()) {
      throw new IllegalArgumentException("No network interfaces found for instance '" + instance.getName() + "'.");
    } else {
      try {
        return InetAddress.getByName(networkInterfaceList.get(0).getNetworkIP());
      } catch (UnknownHostException e) {
        throw new IllegalArgumentException("Invalid private IP address", e);
      }
    }
  }

  @Override
  public Type getType() {
    return TYPE;
  }

  public Disk getBootDisk() {
    return bootDisk;
  }

  @Override
  public Map<String, String> getProperties() {
    Map<String, String> properties = Maps.newHashMap();
    Instance instance = unwrap();

    if (instance != null) {
      for (GoogleComputeInstanceDisplayPropertyToken propertyToken : GoogleComputeInstanceDisplayPropertyToken.values()) {
        properties.put(propertyToken.unwrap().getDisplayKey(), propertyToken.getPropertyValue(instance, bootDisk));
      }
    }

    return properties;
  }

  /**
   * Sets the Google instance.
   *
   * @param instance the Google instance
   * @throws IllegalArgumentException if the instance does not have a valid private IP address
   */
  protected void setInstance(Instance instance) {
    super.setDetails(instance);

    InetAddress privateIpAddress = getPrivateIpAddress(instance);
    setPrivateIpAddress(privateIpAddress);
  }
}
