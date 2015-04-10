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

package com.cloudera.director.google;

import com.cloudera.director.google.internal.GoogleCredentials;
import com.cloudera.director.spi.v1.model.Configured;
import com.cloudera.director.spi.v1.provider.CloudProvider;
import com.cloudera.director.spi.v1.provider.CredentialsProvider;
import com.cloudera.director.spi.v1.provider.util.AbstractLauncher;
import com.google.api.services.compute.Compute;

import java.io.IOException;
import java.util.Collections;
import java.util.NoSuchElementException;

public class GoogleLauncher extends AbstractLauncher {

  public GoogleLauncher() {
    super(Collections.singletonList(GoogleCloudProvider.METADATA));
  }

  @Override
  public CloudProvider createCloudProvider(String cloudProviderId, Configured configuration) {

    if (!GoogleCloudProvider.ID.equals(cloudProviderId)) {
      throw new IllegalArgumentException("Cloud provider not found: " + cloudProviderId);
    }

    // At this point the configuration object will already contain
    // the required data for authentication.

    CredentialsProvider<GoogleCredentials> provider = new GoogleCredentialsProvider();
    GoogleCredentials credentials = provider.createCredentials(configuration);
    Compute compute = credentials.getCompute();

    if (compute == null) {
      throw new IllegalArgumentException("Invalid cloud provider credentials.");
    } else {
      String projectId = credentials.getProjectId();

      try {
        // Attempt GCP api call to verify credentials.
        compute.regions().list(projectId).execute();
      } catch (IOException e) {
        throw new IllegalArgumentException(
                "Invalid cloud provider credentials for project '" + projectId + "'.", e);
      }
    }

    return new GoogleCloudProvider(credentials);
  }
}
