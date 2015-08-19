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

package com.cloudera.director.google.sql;

import com.cloudera.director.spi.v1.database.util.AbstractDatabaseServerInstance;
import com.cloudera.director.spi.v1.model.DisplayProperty;
import com.cloudera.director.spi.v1.model.DisplayPropertyToken;
import com.cloudera.director.spi.v1.model.util.SimpleDisplayPropertyBuilder;
import com.cloudera.director.spi.v1.util.DisplayPropertiesUtil;
import com.google.api.services.sqladmin.model.DatabaseInstance;
import com.google.api.services.sqladmin.model.IpMapping;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;

public class GoogleCloudSQLInstance
    extends AbstractDatabaseServerInstance<GoogleCloudSQLInstanceTemplate, DatabaseInstance> {

  private static final Logger LOG = LoggerFactory.getLogger(GoogleCloudSQLInstance.class);

  /**
   * The list of display properties (including inherited properties).
   */
  private static final List<DisplayProperty> DISPLAY_PROPERTIES =
      DisplayPropertiesUtil.asDisplayPropertyList(GoogleSQLInstanceDisplayPropertyToken.values());

  /**
   * Returns the list of display properties for a Google Cloud SQL instance, including inherited properties.
   */
  public static List<DisplayProperty> getDisplayProperties() {
    return DISPLAY_PROPERTIES;
  }

  /**
   * Google Cloud SQL instance display properties.
   */
  public static enum GoogleSQLInstanceDisplayPropertyToken implements DisplayPropertyToken {

    //TODO(kl3n1nz) Add other display properties.
    /**
     * The display property.
     */
    DATABASE_INSTANCE_ID(new SimpleDisplayPropertyBuilder()
        .displayKey("DatabaseInstanceId")
        .defaultDescription("The ID of the database instance.")
        .sensitive(false)
        .build()) {
      @Override
      protected String getPropertyValue(DatabaseInstance instance) {
        return instance.getName();
      }
    };

    private final DisplayProperty displayProperty;

    /**
     * Creates a Google instance display property token with the specified parameters.
     *
     * @param displayProperty the display property
     */
    private GoogleSQLInstanceDisplayPropertyToken(DisplayProperty displayProperty) {
      this.displayProperty = displayProperty;
    }

    /**
     * Returns the value of the property from the specified instance.
     *
     * @param instance the instance
     * @return the value of the property from the specified instance
     */
    protected abstract String getPropertyValue(DatabaseInstance instance);

    @Override
    public DisplayProperty unwrap() {
      return displayProperty;
    }
  }

  public static final Type TYPE = new ResourceType("GoogleCloudSQLInstance");

  /**
   * Creates a Google Cloud SQL instance with the specified parameters.
   *
   * @param template        the template from which the instance was created
   * @param instanceId      the instance identifier
   * @param instanceDetails the provider-specific instance details
   * @throws IllegalArgumentException if the instance does not have a valid private IP address
   */
  protected GoogleCloudSQLInstance(GoogleCloudSQLInstanceTemplate template, String instanceId,
      DatabaseInstance instanceDetails) {
    super(template, instanceId, getPrivateIpAddress(instanceDetails), null, instanceDetails);
  }

  /**
   * Returns the private IP address of the specified Google instance.
   *
   * @param instance the instance
   * @return the private IP address of the specified Google instance
   * @throws IllegalArgumentException if the instance does not have a valid private IP address
   */
  private static InetAddress getPrivateIpAddress(DatabaseInstance instance) {
    Preconditions.checkNotNull(instance, "instance is null");

    List<IpMapping> ipMappingList = instance.getIpAddresses();

    if (ipMappingList == null || ipMappingList.size() == 0) {
      throw new IllegalArgumentException("No network interfaces found for database instance '" + instance.getName() + "'.");
    } else {
      try {
        return InetAddress.getByName(ipMappingList.get(0).getIpAddress());
      } catch (UnknownHostException e) {
        throw new IllegalArgumentException("Invalid IPv4 address", e);
      }
    }
  }

  @Override
  public Type getType() {
    return TYPE;
  }

  @Override
  public Map<String, String> getProperties() {
    Map<String, String> properties = Maps.newHashMap();
    DatabaseInstance instance = unwrap();

    if (instance != null) {
      for (GoogleSQLInstanceDisplayPropertyToken propertyToken : GoogleSQLInstanceDisplayPropertyToken.values()) {
        properties.put(propertyToken.unwrap().getDisplayKey(), propertyToken.getPropertyValue(instance));
      }
    }

    return properties;
  }
}
