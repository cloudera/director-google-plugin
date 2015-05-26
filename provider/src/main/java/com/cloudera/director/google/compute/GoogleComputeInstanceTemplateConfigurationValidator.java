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

import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationProperty.ZONE;
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
import com.google.api.services.compute.model.Zone;
import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Validates Google compute instance template configuration.
 */
public class GoogleComputeInstanceTemplateConfigurationValidator implements ConfigurationValidator {

  private static final Logger LOG =
          LoggerFactory.getLogger(GoogleComputeInstanceTemplateConfigurationValidator.class);

  private static final String ZONE_NOT_FOUND_MSG = "Zone '%s' not found for project '%s'.";
  private static final String ZONE_NOT_FOUND_IN_REGION_MSG = "Zone '%s' not found in region '%s' for project '%s'.";

  /**
   * The Google compute provider.
   */
  private final GoogleComputeProvider provider;

  /**
   * Creates a Google compute instance template configuration validator with the specified
   * parameters.
   *
   * @param provider the Google compute provider
   */
  public GoogleComputeInstanceTemplateConfigurationValidator(GoogleComputeProvider provider) {
    this.provider = Preconditions.checkNotNull(provider, "provider");
  }

  @Override
  public void validate(String name, Configured configuration,
      PluginExceptionConditionAccumulator accumulator, LocalizationContext localizationContext) {
    checkZone(configuration, accumulator, localizationContext);
  }

  /**
   * Validates the configured zone.
   *
   * @param configuration       the configuration to be validated
   * @param accumulator         the exception condition accumulator
   * @param localizationContext the localization context
   */
  void checkZone(Configured configuration,
      PluginExceptionConditionAccumulator accumulator,
      LocalizationContext localizationContext) {

    String zoneName = configuration.getConfigurationValue(ZONE, localizationContext);

    if (zoneName != null) {
      LOG.info(">> Describing zone '{}'", zoneName);

      GoogleCredentials credentials = provider.getCredentials();
      Compute compute = credentials.getCompute();
      String projectId = credentials.getProjectId();
      String regionName = provider.getConfigurationValue(REGION, localizationContext);

      try {
        Zone zone = compute.zones().get(projectId, zoneName).execute();

        if (!Utils.getLocalName(zone.getRegion()).equals(regionName)) {
          addError(accumulator, ZONE, localizationContext, null, ZONE_NOT_FOUND_IN_REGION_MSG,
              new Object[]{zoneName, regionName, projectId});
        }
      } catch (GoogleJsonResponseException e) {
        if (e.getStatusCode() == 404) {
          addError(accumulator, ZONE, localizationContext, null, ZONE_NOT_FOUND_MSG,
              new Object[]{zoneName, regionName, projectId});
        } else {
          throw new TransientProviderException(e);
        }
      } catch (IOException e) {
        throw new TransientProviderException(e);
      }
    }
  }
}