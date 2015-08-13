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

import static com.cloudera.director.google.compute.GoogleComputeProviderConfigurationProperty.REGION;
import static com.cloudera.director.spi.v1.model.util.Validations.addError;

import com.cloudera.director.google.internal.GoogleCredentials;
import com.cloudera.director.spi.v1.model.ConfigurationValidator;
import com.cloudera.director.spi.v1.model.Configured;
import com.cloudera.director.spi.v1.model.LocalizationContext;
import com.cloudera.director.spi.v1.model.exception.PluginExceptionConditionAccumulator;
import com.cloudera.director.spi.v1.model.exception.TransientProviderException;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.compute.Compute;
import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Validates Google compute provider configuration.
 */
public class GoogleComputeProviderConfigurationValidator implements ConfigurationValidator {

  private static final Logger LOG =
      LoggerFactory.getLogger(GoogleComputeProviderConfigurationValidator.class);

  @VisibleForTesting
  static final String REGION_NOT_FOUND_MSG = "Region '%s' not found for project '%s'.";

  private GoogleCredentials credentials;

  /**
   * Creates a Google compute provider configuration validator with the specified parameters.
   */
  public GoogleComputeProviderConfigurationValidator(GoogleCredentials credentials) {
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

    String regionName = configuration.getConfigurationValue(REGION, localizationContext);

    LOG.info(">> Querying region '{}'", regionName);

    Compute compute = credentials.getCompute();
    String projectId = credentials.getProjectId();

    try {
      compute.regions().get(projectId, regionName).execute();
    } catch (GoogleJsonResponseException e) {
      if (e.getStatusCode() == 404) {
        addError(accumulator, REGION, localizationContext, null, REGION_NOT_FOUND_MSG, regionName, projectId);
      } else {
        throw new TransientProviderException(e);
      }
    } catch (IOException e) {
      throw new TransientProviderException(e);
    }
  }
}
