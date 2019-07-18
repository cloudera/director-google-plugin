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

import com.cloudera.director.spi.v1.compute.ComputeInstanceTemplate.ComputeInstanceTemplateConfigurationPropertyToken;
import com.cloudera.director.spi.v1.model.ConfigurationProperty;
import com.cloudera.director.spi.v1.model.ConfigurationPropertyToken;
import com.cloudera.director.spi.v1.model.Property;
import com.cloudera.director.spi.v1.model.util.SimpleConfigurationPropertyBuilder;

/**
 * Google Compute Engine instance template configuration properties.
 */
public enum GoogleComputeInstanceTemplateConfigurationProperty implements ConfigurationPropertyToken {

  IMAGE(new SimpleConfigurationPropertyBuilder()
      .configKey(ComputeInstanceTemplateConfigurationPropertyToken.IMAGE.unwrap().getConfigKey())
      .name("Image Alias or URL")
      .addValidValues("centos6", "rhel6")
      .defaultDescription("The image alias from plugin configuration or a full image URL.")
      .defaultErrorMessage("Image alias or URL is mandatory")
      .widget(ConfigurationProperty.Widget.OPENLIST)
      .required(true)
      .build()),

  TYPE(new SimpleConfigurationPropertyBuilder()
      .configKey(ComputeInstanceTemplateConfigurationPropertyToken.TYPE.unwrap().getConfigKey())
      .name("Machine Type")
      .addValidValues(
          "f1-micro", "g1-small",
          "n1-standard-1", "n1-standard-2", "n1-standard-4", "n1-standard-8", "n1-standard-16", "n1-standard-32",
          "n1-highcpu-2", "n1-highcpu-4", "n1-highcpu-8", "n1-highcpu-16", "n1-highcpu-32",
          "n1-highmem-2", "n1-highmem-4", "n1-highmem-8", "n1-highmem-16", "n1-highmem-32")
      .defaultDescription(
          "The machine type.<br />" +
          "<a target='_blank' href='https://cloud.google.com/compute/docs/machine-types'>More Information</a>")
      .defaultErrorMessage("Machine type is mandatory")
      .widget(ConfigurationProperty.Widget.OPENLIST)
      .required(true)
      .build()),

  NETWORK_NAME(new SimpleConfigurationPropertyBuilder()
      .configKey("networkName")
      .name("Network Name")
      .defaultDescription(
          "The network identifier.<br />" +
          "<a target='_blank' href='https://cloud.google.com/compute/docs/networking#networks'>More Information</a>")
      .defaultValue("default")
      .required(false)
      .build()),

  NETWORK_PROJECT(new SimpleConfigurationPropertyBuilder()
      .configKey("networkProject")
      .name("Network Project")
      .defaultDescription(
          "The project the network belongs to.<br />" +
              "<a target='_blank' href='https://cloud.google.com/compute/docs/networking#networks'>More Information</a>")
      .defaultValue(null)
      .required(false)
      .build()),

  ASSIGN_EXTERNAL_IPS(new SimpleConfigurationPropertyBuilder()
      .configKey("assignExternalIPs")
      .name("Assign External IPs")
      .defaultDescription("Assign external IP addresses to created instances.  Cloudera Altus Director may " +
          "require egress for package installation, but this can be provided with a Cloud NAT.")
      .defaultValue("true")
      .required(false)
      .build()),

  INSTANCE_TAGS(new SimpleConfigurationPropertyBuilder()
      .configKey("instanceTags")
      .name("Tags")
      .defaultDescription("Instance tags")
      .defaultValue(null)
      .required(false)
      .build()),

  SUBNETWORK_NAME(new SimpleConfigurationPropertyBuilder()
      .configKey("subnetworkName")
      .name("Subnetwork Name")
      .defaultDescription("The subnetwork identifier.<br />" +
              "<a target='_blank' href='https://cloud.google.com/compute/docs/networking#networks'>More Information</a>")
      .defaultValue(null)
      .required(false)
      .build()),

  ZONE(new SimpleConfigurationPropertyBuilder()
      .configKey("zone")
      .name("Zone")
      .defaultDescription(
          "The zone to target for deployment. " +
          "The zone you specify must be contained within the region you selected.<br />" +
          "<a target='_blank' href='https://cloud.google.com/compute/docs/zones'>More Information</a>")
      .defaultErrorMessage("Zone is mandatory")
      .widget(ConfigurationProperty.Widget.OPENLIST)
      .required(true)
      .build()),

  BOOT_DISK_TYPE(new SimpleConfigurationPropertyBuilder()
      .configKey("bootDiskType")
      .name("Boot Disk Type")
      .addValidValues("SSD", "Standard")
      .defaultDescription("The type of boot disk to create (SSD, Standard).<br />" +
          "<a target='_blank' href='https://cloud.google.com/compute/docs/disks/'>More Information</a>")
      .defaultValue("SSD")
      .widget(ConfigurationProperty.Widget.LIST)
      .required(false)
      .build()),

  BOOT_DISK_SIZE_GB(new SimpleConfigurationPropertyBuilder()
      .configKey("bootDiskSizeGb")
      .name("Boot Disk Size (GB)")
      .defaultDescription("The size of the boot disk in GB.")
      .defaultValue("60")
      .type(Property.Type.INTEGER)
      .widget(ConfigurationProperty.Widget.NUMBER)
      .required(false)
      .build()),

  DATA_DISK_COUNT(new SimpleConfigurationPropertyBuilder()
      .configKey("dataDiskCount")
      .name("Data Disk Count")
      .defaultDescription("The number of data disks to create.")
      .defaultValue("2")
      .type(Property.Type.INTEGER)
      .widget(ConfigurationProperty.Widget.NUMBER)
      .required(false)
      .build()),

  DATA_DISK_TYPE(new SimpleConfigurationPropertyBuilder()
      .configKey("dataDiskType")
      .name("Data Disk Type")
      .addValidValues("LocalSSD", "SSD", "Standard")
      .defaultDescription(
          "The type of data disks to create (LocalSSD, SSD, Standard).<br />" +
          "<a target='_blank' href='https://cloud.google.com/compute/docs/disks/'>More Information</a>")
      .defaultValue("LocalSSD")
      .widget(ConfigurationProperty.Widget.LIST)
      .required(false)
      .build()),

  // This property is ignored when dataDiskType == 'LocalSSD'.
  DATA_DISK_SIZE_GB(new SimpleConfigurationPropertyBuilder()
      .configKey("dataDiskSizeGb")
      .name("Data Disk Size")
      .defaultDescription(
          "The size of the data disks in GB. If you've selected LocalSSD data disks, must be exactly " +
          GoogleComputeInstanceTemplateConfigurationValidator.EXACT_LOCAL_SSD_DATA_DISK_SIZE_GB + ".")
      .defaultValue("375")
      .type(Property.Type.INTEGER)
      .widget(ConfigurationProperty.Widget.NUMBER)
      .required(false)
      .build()),

  // This property is ignored when dataDiskType != 'LocalSSD'.
  LOCAL_SSD_INTERFACE_TYPE(new SimpleConfigurationPropertyBuilder()
      .configKey("localSSDInterfaceType")
      .name("Local SSD Interface Type")
      .addValidValues("SCSI", "NVME")
      .defaultDescription(
          "The Local SSD interface type (SCSI or NVME).<br />" +
          "<a target='_blank' href='https://cloud.google.com/compute/docs/disks/local-ssd#performance'>More Information</a>")
      .defaultValue("SCSI")
      .widget(ConfigurationProperty.Widget.LIST)
      .required(false)
      .build()),

  USE_PREEMPTIBLE_INSTANCES(new SimpleConfigurationPropertyBuilder()
      .configKey("usePreemptibleInstances")
      .name("Use Preemptible Instances")
      .defaultDescription(
          "Whether to use preemptible virtual machine (VM) instances. " +
              "Since preemptible instances can be terminated unexpectedly, " +
              "they should be used only for workers, and not for nodes that must be reliable, " +
              "such as masters and data nodes.<br />" +
              "<a target='_blank' href='https://cloud.google.com/compute/docs/instances/preemptible/'>More Information</a>")
      .defaultValue("false")
      .type(Property.Type.BOOLEAN)
      .widget(ConfigurationProperty.Widget.CHECKBOX)
      .required(false)
      .build());

  /**
   * The configuration property.
   */
  private final ConfigurationProperty configurationProperty;

  /**
   * Creates a configuration property token with the specified parameters.
   *
   * @param configurationProperty the configuration property
   */
  private GoogleComputeInstanceTemplateConfigurationProperty(ConfigurationProperty configurationProperty) {
    this.configurationProperty = configurationProperty;
  }

  @Override
  public ConfigurationProperty unwrap() {
    return configurationProperty;
  }
}
