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

import static junit.framework.Assert.fail;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

import java.net.MalformedURLException;

/**
 * Tests {@link Urls}.
 */
public class UrlsTest {

  @Test
  public void testGetLocalName() {
    String someUrl = "https://www.googleapis.com/compute/v1/projects/some-project/zones/us-central1-a/disks/director-b54b77f3-913b-4052-8cc3-5fbc19928c9f";

    assertThat(Urls.getLocalName(someUrl)).isEqualTo("director-b54b77f3-913b-4052-8cc3-5fbc19928c9f");
  }

  @Test
  public void testGetLocalName_AlreadyLocal() {
    try {
      Urls.getLocalName("director-b54b77f3-913b-4052-8cc3-5fbc19928c9f");

      fail("An exception should have been thrown when we attempted to parse a malformed url.");
    } catch (IllegalArgumentException e) {
      assertThat(e.getCause()).isInstanceOf(MalformedURLException.class);
    }
  }

  @Test
  public void testGetLocalName_Null() {
    assertThat(Urls.getLocalName(null)).isEqualTo(null);
  }

  @Test
  public void testGetLocalName_EmptyString() {
    assertThat(Urls.getLocalName("")).isEqualTo(null);
  }

  @Test
  public void testGetProject() {
    String someUrl = "https://www.googleapis.com/compute/v1/projects/rhel-cloud/global/images/rhel-6-v20150526";

    assertThat(Urls.getProject(someUrl)).isEqualTo("rhel-cloud");
  }

  @Test
  public void testGetProject_AlreadyLocal() {
    try {
      Urls.getProject("rhel-cloud");

      fail("An exception should have been thrown when we attempted to parse a malformed url.");
    } catch (IllegalArgumentException e) {
      assertThat(e.getCause()).isInstanceOf(MalformedURLException.class);
    }
  }

  @Test
  public void testGetProject_TooFewPathParts() {
    String someUrl = "https://www.googleapis.com/compute/v1/projects/rhel-cloud/global/images";

    try {
      Urls.getProject(someUrl);

      fail("An exception should have been thrown when we attempted to parse a malformed url.");
    } catch (IllegalArgumentException e) {
      assertThat(e.getMessage()).isEqualTo(String.format(Urls.MALFORMED_RESOURCE_URL_MSG, someUrl));
    }

    someUrl = "https://www.googleapis.com/compute/v1/projects/rhel-cloud/images/rhel-6-v20150526";

    try {
      Urls.getProject(someUrl);

      fail("An exception should have been thrown when we attempted to parse a malformed url.");
    } catch (IllegalArgumentException e) {
      assertThat(e.getMessage()).isEqualTo(String.format(Urls.MALFORMED_RESOURCE_URL_MSG, someUrl));
    }
  }

  @Test
  public void testGetProject_Null() {
    assertThat(Urls.getProject(null)).isEqualTo(null);
  }

  @Test
  public void testGetProject_EmptyString() {
    assertThat(Urls.getProject("")).isEqualTo(null);
  }

  @Test
  public void testBuildZonalUrl() {
    assertThat(com.cloudera.director.google.compute.util.Urls.buildZonalUrl("some-project", "us-central1-f")).endsWith("/some-project/zones/us-central1-f");

    assertThat(com.cloudera.director.google.compute.util.Urls.buildZonalUrl("some-project", "us-central1-f", "some", "resource"))
        .endsWith("/some-project/zones/us-central1-f/some/resource");
  }

  @Test
  public void testBuildRegionalUrl() {
    assertThat(com.cloudera.director.google.compute.util.Urls.buildRegionalUrl("some-project", "us-central1")).endsWith("/some-project/regions/us-central1");

    assertThat(com.cloudera.director.google.compute.util.Urls.buildRegionalUrl("some-project", "us-central1", "some", "resource"))
        .endsWith("/some-project/regions/us-central1/some/resource");
  }

  @Test
  public void testBuildGlobalUrl() {
    assertThat(com.cloudera.director.google.compute.util.Urls.buildGlobalUrl("some-project")).endsWith("/some-project/global");

    assertThat(com.cloudera.director.google.compute.util.Urls.buildGlobalUrl("some-project", "some", "resource"))
        .endsWith("/some-project/global/some/resource");
  }
}
