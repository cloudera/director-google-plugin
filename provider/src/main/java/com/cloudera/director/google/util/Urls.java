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

package com.cloudera.director.google.util;

import com.google.api.client.http.GenericUrl;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import java.util.List;

public final class Urls {

  @VisibleForTesting
  static final String MALFORMED_RESOURCE_URL_MSG = "Malformed resource url '%s'.";

  private Urls() {}

  public static String getLocalName(String fullResourceUrl) {
    if (fullResourceUrl == null || fullResourceUrl.isEmpty()) {
      return null;
    }

    GenericUrl url = new GenericUrl(fullResourceUrl);

    return Iterables.getLast(url.getPathParts());
  }

  public static String getProject(String fullResourceUrl) {
    if (fullResourceUrl == null || fullResourceUrl.isEmpty()) {
      return null;
    }

    GenericUrl url = new GenericUrl(fullResourceUrl);
    String[] urlParts = Iterables.toArray(url.getPathParts(), String.class);

    // Resource urls look like so: https://www.googleapis.com/compute/v1/projects/rhel-cloud/global/images/rhel-6-v20150526
    // The path parts begin after the host and include a leading "" path part to force the leading slash.
    if (urlParts.length < 8) {
      throw new IllegalArgumentException(String.format(MALFORMED_RESOURCE_URL_MSG, fullResourceUrl));
    } else {
      return urlParts[urlParts.length - 4];
    }
  }

  public static String buildGenericApisUrl(String domainName, String version, String projectId, String... resourcePathParts) {
    GenericUrl genericUrl = new GenericUrl();
    genericUrl.setScheme("https");
    genericUrl.setHost("www.googleapis.com");

    List<String> pathParts = Lists.newArrayList("", domainName, version);
    pathParts.add("projects");
    pathParts.add(projectId);

    if (resourcePathParts != null) {
      pathParts.addAll(Lists.newArrayList(resourcePathParts));
    }

    genericUrl.setPathParts(pathParts);

    return genericUrl.build();
  }

}
