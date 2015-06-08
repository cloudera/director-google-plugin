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

import java.io.IOException;
import java.nio.charset.Charset;

public final class TestFixture {

  private static String PROJECT_ID;
  private static String JSON_KEY;
  private static String SSH_PUBLIC_KEY;
  private static String USER_NAME;
  private static boolean HALT_AFTER_ALLOCATION;

  public String projectId;
  public String jsonKey;
  public String sshPublicKey;
  public String userName;
  public boolean haltAfterAllocation;

  private TestFixture(
      String projectId, String jsonKey, String sshPublicKey, String userName, boolean haltAfterAllocation) {
    this.projectId = projectId;
    this.jsonKey = jsonKey;
    this.sshPublicKey = sshPublicKey;
    this.userName = userName;
    this.haltAfterAllocation = haltAfterAllocation;
  }

  private static void initializeIfNecessary(boolean sshPublicKeyIsRequired, boolean userNameIsRequired) throws IOException {
    if (PROJECT_ID == null) {
      PROJECT_ID = TestUtils.readRequiredSystemProperty("GCP_PROJECT_ID");
      JSON_KEY = TestUtils.readFileIfSpecified(System.getProperty("JSON_KEY_PATH", ""));

      if (sshPublicKeyIsRequired) {
        SSH_PUBLIC_KEY = TestUtils.readFile(TestUtils.readRequiredSystemProperty("SSH_PUBLIC_KEY_PATH"),
            Charset.defaultCharset());
      }

      if (userNameIsRequired) {
        USER_NAME = TestUtils.readRequiredSystemProperty("SSH_USER_NAME");
      }

      HALT_AFTER_ALLOCATION = Boolean.parseBoolean(System.getProperty("HALT_AFTER_ALLOCATION", "false"));
    }
  }

  public static TestFixture newTestFixture(boolean sshPublicKeyIsRequired, boolean userNameIsRequired) throws IOException {
    initializeIfNecessary(sshPublicKeyIsRequired, userNameIsRequired);

    return new TestFixture(PROJECT_ID, JSON_KEY, SSH_PUBLIC_KEY, USER_NAME, HALT_AFTER_ALLOCATION);
  }
}
