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

public enum GoogleComputeInstanceTemplateConfigurationProperty implements ConfigurationPropertyToken {

  IMAGE(new SimpleConfigurationPropertyBuilder()
      .configKey(ComputeInstanceTemplateConfigurationPropertyToken.IMAGE.unwrap().getConfigKey())
      .name("Image Alias")
      .addValidValues("centos6", "rhel6")
      .defaultDescription("The image alias from plugin configuration.")
      .defaultErrorMessage("Image alias is mandatory")
      .widget(ConfigurationProperty.Widget.OPENLIST)
      .required(true)
      .build()),

  TYPE(new SimpleConfigurationPropertyBuilder()
      .configKey(ComputeInstanceTemplateConfigurationPropertyToken.TYPE.unwrap().getConfigKey())
      .name("Machine Type")
      .addValidValues("f1-micro", "g1-small",
          "n1-standard-1", "n1-standard-2", "n1-standard-4", "n1-standard-8", "n1-standard-16", "n1-standard-32",
          "n1-highcpu-2", "n1-highcpu-4", "n1-highcpu-8", "n1-highcpu-16", "n1-highcpu-32",
          "n1-highmem-2", "n1-highmem-4", "n1-highmem-8", "n1-highmem-16", "n1-highmem-32")
      .defaultDescription("The machine type.")
      .defaultErrorMessage("Machine type is mandatory")
      .widget(ConfigurationProperty.Widget.OPENLIST)
      .required(true)
      .build()),

  NETWORKNAME(new SimpleConfigurationPropertyBuilder()
      .configKey("networkName")
      .name("Network Name")
      .defaultDescription("Network identifier.")
      .defaultValue("default")
      .required(false)
      .build()),

  ZONE(new SimpleConfigurationPropertyBuilder()
      .configKey("zone")
      .name("Zone")
      .defaultDescription("Zone to target for deployment.")
      .widget(ConfigurationProperty.Widget.OPENLIST)
      .required(true)
      .build()),

  BOOTDISKSIZEGB(new SimpleConfigurationPropertyBuilder()
      .configKey("bootDiskSizeGb")
      .name("Boot Disk Size (GB)")
      .defaultDescription("Size of boot disk in GB.")
      .defaultValue("60")
      .type(Property.Type.INTEGER)
      .widget(ConfigurationProperty.Widget.NUMBER)
      .required(false)
      .build()),

  DATADISKCOUNT(new SimpleConfigurationPropertyBuilder()
      .configKey("dataDiskCount")
      .name("Data Disk Count")
      .defaultDescription("Number of data disks to create.")
      .defaultValue("2")
      .type(Property.Type.INTEGER)
      .widget(ConfigurationProperty.Widget.NUMBER)
      .required(false)
      .build()),

  DATADISKTYPE(new SimpleConfigurationPropertyBuilder()
      .configKey("dataDiskType")
      .name("Data Disk Type")
      .addValidValues("LocalSSD", "SSD", "Standard")
      .defaultDescription("Type of data disks to create (LocalSSD, SSD, Standard).")
      .defaultValue("LocalSSD")
      .widget(ConfigurationProperty.Widget.LIST)
      .required(false)
      .build()),

  // This property is ignored when dataDiskType == 'LocalSSD'.
  DATADISKSIZEGB(new SimpleConfigurationPropertyBuilder()
      .configKey("dataDiskSizeGb")
      .name("Data Disk Size")
      .defaultDescription("Size of data disks in GB.")
      .defaultValue("375")
      .type(Property.Type.INTEGER)
      .widget(ConfigurationProperty.Widget.NUMBER)
      .required(false)
      .build()),

  // This property is ignored when dataDiskType != 'LocalSSD'.
  LOCALSSDINTERFACETYPE(new SimpleConfigurationPropertyBuilder()
      .configKey("localSSDInterfaceType")
      .name("Local SSD Interface Type")
      .addValidValues("SCSI", "NVME")
      .defaultDescription("Local SSD interface type (SCSI or NVME).")
      .defaultValue("SCSI")
      .widget(ConfigurationProperty.Widget.LIST)
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
