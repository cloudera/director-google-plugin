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

package com.cloudera.director.google.internal;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.ComputeScopes;

import java.io.ByteArrayInputStream;
import java.util.Collections;

public class GoogleCredentials {

  private final static String APPLICATION_NAME = "Cloudera";

  private final String projectId;
  private final String jsonKey;
  private final Compute compute;

  public GoogleCredentials(String projectId, String jsonKey) {
    this.projectId = projectId;
    this.jsonKey = jsonKey;
    this.compute = buildCompute(jsonKey);
  }

  private static Compute buildCompute(String jsonKey) {
    try {
      JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
      HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();

      GoogleCredential credential = GoogleCredential.fromStream(
          new ByteArrayInputStream(jsonKey.getBytes()), httpTransport, JSON_FACTORY)
          .createScoped(Collections.singleton(ComputeScopes.COMPUTE));

      return new Compute.Builder(httpTransport,
          JSON_FACTORY,
          null)
          .setApplicationName(APPLICATION_NAME)
          .setHttpRequestInitializer(credential)
          .build();

    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public String getProjectId() {
    return projectId;
  }

  public String getJsonKey() {
    return jsonKey;
  }

  public Compute getCompute() {
    return compute;
  }

  public boolean match(String projectId, String jsonKey) {
    return projectId.equals(this.projectId) &&
        jsonKey.equals(this.jsonKey);
  }
}
