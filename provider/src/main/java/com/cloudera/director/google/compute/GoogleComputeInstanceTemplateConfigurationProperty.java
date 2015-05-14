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

import com.cloudera.director.spi.v1.model.ConfigurationProperty;
import com.cloudera.director.spi.v1.model.ConfigurationPropertyToken;
import com.cloudera.director.spi.v1.model.util.SimpleConfigurationPropertyBuilder;

public enum GoogleComputeInstanceTemplateConfigurationProperty implements ConfigurationPropertyToken {

  NETWORKNAME(new SimpleConfigurationPropertyBuilder()
      .configKey("networkName")
      .name("Network Name")
      .defaultDescription("Network identifier")
      .defaultValue("default")
      .required(false)
      .build()),

  ZONE(new SimpleConfigurationPropertyBuilder()
      .configKey("zone")
      .name("Zone")
      .defaultDescription("Zone to target for deployment")
      .required(true)
      .build()),

  BOOTDISKSIZEGB(new SimpleConfigurationPropertyBuilder()
      .configKey("bootDiskSizeGb")
      .name("Boot Disk Size (GB)")
      .defaultDescription("Size of boot disk in GB")
      .defaultValue("60")
      .required(false)
      .build()),

  LOCALSSDCOUNT(new SimpleConfigurationPropertyBuilder()
      .configKey("localSSDCount")
      .name("Local SSD Count")
      .defaultDescription("Number of local SSD drives to create")
      .defaultValue("2")
      .required(false)
      .build()),

  LOCALSSDINTERFACETYPE(new SimpleConfigurationPropertyBuilder()
      .configKey("localSSDInterfaceType")
      .name("Local SSD Interface Type")
      .defaultDescription("Local SSD interface type (SCSI or NVME)")
      .defaultValue("SCSI")
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
