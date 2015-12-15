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

import org.apache.commons.codec.binary.Base64;

import java.io.IOException;
import java.nio.charset.Charset;

public final class TestFixture {

  private String projectId;
  private String jsonKey;
  private String sshPublicKey;
  private String userName;
  private boolean haltAfterAllocation;

  private TestFixture(
      String projectId, String jsonKey, String sshPublicKey, String userName, boolean haltAfterAllocation) {
    this.projectId = projectId;
    this.jsonKey = jsonKey;
    this.sshPublicKey = sshPublicKey;
    this.userName = userName;
    this.haltAfterAllocation = haltAfterAllocation;
  }

  public static TestFixture newTestFixture(boolean sshPublicKeyAndUserNameAreRequired) throws IOException {
    String projectId = TestUtils.readRequiredSystemProperty("GCP_PROJECT_ID");
    String jsonKey = TestUtils.readFileIfSpecified(System.getProperty("JSON_KEY_PATH", ""));

    // If the path to a json key file was not provided, check if the key was passed explicitly.
    if (jsonKey == null) {
      String jsonKeyInline = System.getProperty("JSON_KEY_INLINE", "");

      if (!jsonKeyInline.isEmpty()) {
        // If so, we must base64-decode it.
        jsonKey = new String(Base64.decodeBase64(jsonKeyInline));
      }
    }

    String sshPublicKey = null;
    String userName = null;

    if (sshPublicKeyAndUserNameAreRequired) {
      sshPublicKey = TestUtils.readFile(TestUtils.readRequiredSystemProperty("SSH_PUBLIC_KEY_PATH"),
          Charset.defaultCharset());
      userName = TestUtils.readRequiredSystemProperty("SSH_USER_NAME");
    }

    boolean haltAfterAllocation = Boolean.parseBoolean(System.getProperty("HALT_AFTER_ALLOCATION", "false"));

    return new TestFixture(projectId, jsonKey, sshPublicKey, userName, haltAfterAllocation);
  }

  public String getProjectId() {
    return projectId;
  }

  public String getJsonKey() {
    return jsonKey;
  }

  public String getSshPublicKey() {
    return sshPublicKey;
  }

  public String getUserName() {
    return userName;
  }

  public boolean getHaltAfterAllocation() {
    return haltAfterAllocation;
  }
}
