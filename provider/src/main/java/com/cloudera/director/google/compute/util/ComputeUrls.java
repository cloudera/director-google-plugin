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

package com.cloudera.director.google.compute.util;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.cloudera.director.google.util.Urls;

import java.util.List;

public class ComputeUrls {

  private ComputeUrls() {}

  public static String buildDiskTypeUrl(String projectId, String zone, String dataDiskType) {
    String diskTypePath;

    if (dataDiskType.equals("LocalSSD")) {
      diskTypePath = "local-ssd";
    } else if (dataDiskType.equals("SSD")) {
      diskTypePath = "pd-ssd";
    } else {
      // The value will already have been checked by the validator.
      // Assume 'Standard'.
      diskTypePath = "pd-standard";
    }

    return buildZonalUrl(projectId, zone, "diskTypes", diskTypePath);
  }

  public static String buildDiskUrl(String projectId, String zone, String diskName) {
    return buildZonalUrl(projectId, zone, "disks", diskName);
  }

  public static String buildMachineTypeUrl(String projectId, String zone, String machineType) {
    return buildZonalUrl(projectId, zone, "machineTypes", machineType);
  }

  public static String buildNetworkUrl(String projectId, String networkName) {
    return buildGlobalUrl(projectId, "networks", networkName);
  }

  public static String buildSubnetUrl(String projectId, String region, String subnetName){
    return buildRegionalUrl(projectId, region, "subnetworks", subnetName);
  }

  public static String buildZonalUrl(String projectId, String zone, String... resourcePathParts) {
    List<String> pathParts = Lists.newArrayList("zones", zone);

    if (resourcePathParts != null) {
      pathParts.addAll(Lists.newArrayList(resourcePathParts));
    }

    return buildGoogleComputeApisUrl(projectId, Iterables.toArray(pathParts, String.class));
  }

  public static String buildRegionalUrl(String projectId, String region, String... resourcePathParts) {
    List<String> pathParts = Lists.newArrayList("regions", region);

    if (resourcePathParts != null) {
      pathParts.addAll(Lists.newArrayList(resourcePathParts));
    }

    return buildGoogleComputeApisUrl(projectId, Iterables.toArray(pathParts, String.class));
  }

  public static String buildGlobalUrl(String projectId, String... resourcePathParts) {
    List<String> pathParts = Lists.newArrayList("global");

    if (resourcePathParts != null) {
      pathParts.addAll(Lists.newArrayList(resourcePathParts));
    }

    return buildGoogleComputeApisUrl(projectId, Iterables.toArray(pathParts, String.class));
  }

  static String buildGoogleComputeApisUrl(String projectId, String... resourcePathParts) {
    return Urls.buildGenericApisUrl("compute", "v1", projectId, resourcePathParts);
  }
}
