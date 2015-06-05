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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Utils {

  public static final String SIMPLE_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSX";

  static String getLocalName(String fullResourceUrl) {
    if (fullResourceUrl == null) {
      return null;
    }

    String[] urlParts = fullResourceUrl.split("/");

    return urlParts[urlParts.length - 1];
  }

  static String getProject(String fullResourceUrl) {
    if (fullResourceUrl == null) {
      return null;
    }

    String[] urlParts = fullResourceUrl.split("/");

    // Resource urls look like so: https://www.googleapis.com/compute/v1/projects/rhel-cloud/global/images/rhel-6-v20150526
    if (urlParts.length < 10) {
      throw new IllegalArgumentException("Malformed resource url '" + fullResourceUrl + "'.");
    } else {
      return urlParts[urlParts.length - 4];
    }
  }

  static Date getDateFromTimestamp(String timestamp) throws ParseException {
    if (timestamp != null && !timestamp.isEmpty()) {
      return new SimpleDateFormat(SIMPLE_DATE_FORMAT).parse(timestamp);
    }

    return null;
  }

  static String buildDiskTypeUrl(String projectId, String zone, String dataDiskType) {
    String diskTypeUrl = "https://www.googleapis.com/compute/v1/projects/" + projectId +
        "/zones/" + zone + "/diskTypes/";

    if (dataDiskType.equals("LocalSSD")) {
      diskTypeUrl += "local-ssd";
    } else if (dataDiskType.equals("SSD")) {
      diskTypeUrl += "pd-ssd";
    } else {
      // The value will already have been checked by the validator.
      // Assume 'Standard'.
      diskTypeUrl += "pd-standard";
    }

    return diskTypeUrl;
  }
}
