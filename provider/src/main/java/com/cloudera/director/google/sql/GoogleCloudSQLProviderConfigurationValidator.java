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

import static com.cloudera.director.google.sql.GoogleCloudSQLProviderConfigurationProperty.REGION_SQL;
import static com.cloudera.director.spi.v1.model.util.Validations.addError;

import com.cloudera.director.google.internal.GoogleCredentials;
import com.cloudera.director.spi.v1.model.ConfigurationValidator;
import com.cloudera.director.spi.v1.model.Configured;
import com.cloudera.director.spi.v1.model.LocalizationContext;
import com.cloudera.director.spi.v1.model.exception.PluginExceptionConditionAccumulator;
import com.cloudera.director.spi.v1.model.exception.TransientProviderException;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.compute.Compute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Validates Google Cloud SQL provider configuration.
 */
public class GoogleCloudSQLProviderConfigurationValidator implements ConfigurationValidator {

  private static final Logger LOG =
      LoggerFactory.getLogger(GoogleCloudSQLProviderConfigurationValidator.class);

  private static final String REGION_NOT_FOUND_MSG = "Region '%s' not found for project '%s'.";
  private static final String REGION_SUGGESTION = "You should probably use '%s' instead.";

  private GoogleCredentials credentials;

  /**
   * Creates a Google Cloud SQL provider configuration validator with the specified parameters.
   */
  public GoogleCloudSQLProviderConfigurationValidator(GoogleCredentials credentials) {
    this.credentials = credentials;
  }

  @Override
  public void validate(String name, Configured configuration,
      PluginExceptionConditionAccumulator accumulator, LocalizationContext localizationContext) {

    checkRegion(configuration, accumulator, localizationContext);
  }

  /**
   * Validates the configured region.
   *
   * @param configuration       the configuration to be validated
   * @param accumulator         the exception condition accumulator
   * @param localizationContext the localization context
   */
  void checkRegion(Configured configuration,
      PluginExceptionConditionAccumulator accumulator,
      LocalizationContext localizationContext) {
    String regionName = configuration.getConfigurationValue(REGION_SQL, localizationContext);

    Compute compute = credentials.getCompute();
    String projectId = credentials.getProjectId();

    LOG.info(">> Querying region '{}'", regionName);

    // Cloud SQL API doesn't provide native region check, so we have to use Compute call to
    // verify it.

    if (regionName.equals("us-central1")) {
      // We must manually handle this case.
      addError(accumulator, REGION_SQL, localizationContext, null, REGION_NOT_FOUND_MSG + " " + REGION_SUGGESTION,
          regionName, projectId, "us-central");
    } else if (regionName.equals("us-central")) {
      regionName = "us-central1";
    }

    try {
      compute.regions().get(projectId, regionName).execute();
    } catch (GoogleJsonResponseException e) {
      if (e.getStatusCode() == 404) {
        addError(accumulator, REGION_SQL, localizationContext, null, REGION_NOT_FOUND_MSG, regionName, projectId);
      } else {
        throw new TransientProviderException(e);
      }
    } catch (IOException e) {
      throw new TransientProviderException(e);
    }
  }
}
