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

import static com.cloudera.director.google.sql.GoogleCloudSQLInstanceTemplateConfigurationProperty.MASTER_USER_PASSWORD;
import static com.cloudera.director.google.sql.GoogleCloudSQLInstanceTemplateConfigurationProperty.MASTER_USERNAME;
import static com.cloudera.director.google.sql.GoogleCloudSQLInstanceTemplateConfigurationProperty.PREFERRED_LOCATION;
import static com.cloudera.director.google.sql.GoogleCloudSQLInstanceTemplateConfigurationProperty.TIER;
import static com.cloudera.director.google.sql.GoogleCloudSQLProviderConfigurationProperty.REGION_SQL;
import static com.cloudera.director.spi.v1.model.InstanceTemplate.InstanceTemplateConfigurationPropertyToken.INSTANCE_NAME_PREFIX;
import static com.cloudera.director.spi.v1.model.util.Validations.addError;

import com.cloudera.director.google.util.Urls;
import com.cloudera.director.google.internal.GoogleCredentials;
import com.cloudera.director.spi.v1.model.ConfigurationValidator;
import com.cloudera.director.spi.v1.model.Configured;
import com.cloudera.director.spi.v1.model.LocalizationContext;
import com.cloudera.director.spi.v1.model.exception.PluginExceptionConditionAccumulator;
import com.cloudera.director.spi.v1.model.exception.PluginExceptionDetails;
import com.cloudera.director.spi.v1.model.exception.TransientProviderException;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.Zone;
import com.google.api.services.sqladmin.model.Tier;
import com.google.api.services.sqladmin.SQLAdmin;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

public class GoogleCloudSQLInstanceTemplateConfigurationValidator implements ConfigurationValidator  {
  private static final Logger LOG =
      LoggerFactory.getLogger(GoogleCloudSQLInstanceTemplateConfigurationValidator.class);

  @VisibleForTesting
  static final String INVALID_TIER_MSG = "Database instance tier '%s' not found.";
  @VisibleForTesting
  static final String PASSWORD_MISSING_MSG = "Database instance user password must be provided.";
  @VisibleForTesting
  static final String PREFIX_MISSING_MSG = "Database instance name prefix must be provided.";
  @VisibleForTesting
  static final String INVALID_PASSWORD_LENGTH_MSG = "Database instance user password must be between 1 and 16 characters.";
  @VisibleForTesting
  static final String INVALID_PREFIX_LENGTH_MSG = "Database instance name prefix must be between 1 and 26 characters.";
  @VisibleForTesting
  static final String INVALID_USERNAME_LENGTH_MSG = "Database instance username must be between 1 and 16 characters.";
  @VisibleForTesting
  static final String INVALID_PREFIX_MSG = "Database instance name prefix must follow this pattern: " +
      "The first character must be a lowercase letter, and all following characters must be a dash, lowercase " +
      "letter, or digit.";
  @VisibleForTesting
  static final String PREFERRED_LOCATION_NOT_FOUND_MSG = "Preferred location '%s' not found for project '%s'.";
  @VisibleForTesting
  static final String PREFERRED_LOCATION_NOT_FOUND_IN_REGION_MSG = "Preferred location '%s' not found in region '%s' for project '%s'.";
  @VisibleForTesting
  static final String USERNAME_MISSING_MSG = "Database instance username must be provided.";

  /**
   * The Google Cloud SQL provider.
   */
  private final GoogleCloudSQLProvider provider;

  /**
   * The pattern to which instance name prefixes must conform. The pattern is the same as that for instance names in
   * general, except that we allow a trailing dash to be used. This is allowed since we always append a dash and the
   * specified instance id to the prefix.
   *
   *  @see <a href="https://developers.google.com/resources/api-libraries/documentation/sqladmin/v1beta4/java/latest/com/google/api/services/sqladmin/model/DatabaseInstance.html#setName(java.lang.String)" />
   */
  private final static Pattern instanceNamePrefixPattern = Pattern.compile("[a-z][-a-z0-9]*");

  /**
   * Creates a Google Cloud SQL instance template configuration validator with the specified parameters.
   *
   * @param provider the Google Cloud SQL provider
   */
  public GoogleCloudSQLInstanceTemplateConfigurationValidator(GoogleCloudSQLProvider provider) {
    this.provider = Preconditions.checkNotNull(provider, "provider");
  }

  @Override
  public void validate(String name, Configured configuration,
      PluginExceptionConditionAccumulator accumulator, LocalizationContext localizationContext) {

    checkTier(configuration, accumulator, localizationContext);
    checkPreferredLocation(configuration, accumulator, localizationContext);
    checkPrefix(configuration, accumulator, localizationContext);
    checkUsername(configuration, accumulator, localizationContext);
    checkPassword(configuration, accumulator, localizationContext);
  }

  /**
   * Validates the configured tier type.
   *
   * @param configuration       the configuration to be validated
   * @param accumulator         the exception condition accumulator
   * @param localizationContext the localization context
   */
  void checkTier(Configured configuration, PluginExceptionConditionAccumulator accumulator,
      LocalizationContext localizationContext) {

    GoogleCredentials credentials = provider.getCredentials();
    SQLAdmin sqlAdmin = credentials.getSQLAdmin();
    String projectId = credentials.getProjectId();
    String tierName = configuration.getConfigurationValue(TIER, localizationContext);

    try {
      List<Tier> tierList = sqlAdmin.tiers().list(projectId).execute().getItems();

      if (tierList != null) {
        for (Tier tier : tierList) {
          if (tier.getTier().equals(tierName)) {
            return;
          }
        }
      }
      addError(accumulator, TIER, localizationContext, null, INVALID_TIER_MSG, tierName);
    } catch (IOException e) {
      throw new TransientProviderException(e);
    }
  }

  /**
   * Validates the configured password.
   *
   * @param configuration       the configuration to be validated
   * @param accumulator         the exception condition accumulator
   * @param localizationContext the localization context
   */
  static void checkPassword(Configured configuration, PluginExceptionConditionAccumulator accumulator,
      LocalizationContext localizationContext) {

    String instancePassword = configuration.getConfigurationValue(MASTER_USER_PASSWORD, localizationContext);

    LOG.info(">> Validating password");

    if (instancePassword == null) {
      addError(accumulator, instancePassword, localizationContext, null, PASSWORD_MISSING_MSG);
    } else {
      int length = instancePassword.length();

      if (length < 1 || length > 16) {
        addError(accumulator, MASTER_USER_PASSWORD, localizationContext, null, INVALID_PASSWORD_LENGTH_MSG);
      }
    }
  }

  /**
   * Validates the preferred location.
   *
   * @param configuration       the configuration to be validated
   * @param accumulator         the exception condition accumulator
   * @param localizationContext the localization context
   */
  void checkPreferredLocation(Configured configuration, PluginExceptionConditionAccumulator accumulator,
      LocalizationContext localizationContext) {

    String preferredLocation = configuration.getConfigurationValue(PREFERRED_LOCATION, localizationContext);

    if (preferredLocation != null && !preferredLocation.isEmpty()) {
      LOG.info(">> Querying zone '{}'", preferredLocation);

      GoogleCredentials credentials = provider.getCredentials();
      Compute compute = credentials.getCompute();
      String projectId = credentials.getProjectId();
      String regionName = provider.getConfigurationValue(REGION_SQL, localizationContext);

      // US region is called differently in GCE and Google Cloud SQL.
      String computeRegionName = regionName;
      if (computeRegionName.equals("us-central")) {
        computeRegionName = "us-central1";
      }

      try {
        Zone zone = compute.zones().get(projectId, preferredLocation).execute();

        if (!Urls.getLocalName(zone.getRegion()).equals(computeRegionName)) {
          addError(accumulator, PREFERRED_LOCATION, localizationContext, null, PREFERRED_LOCATION_NOT_FOUND_IN_REGION_MSG,
              preferredLocation, regionName, projectId);
        }
      } catch (GoogleJsonResponseException e) {
        if (e.getStatusCode() == 404) {
          addError(accumulator, PREFERRED_LOCATION, localizationContext, null, PREFERRED_LOCATION_NOT_FOUND_MSG,
              preferredLocation, projectId);
        } else {
          throw new TransientProviderException(e);
        }
      } catch (IOException e) {
        throw new TransientProviderException(e);
      }
    }
  }

  /**
   * Validates the configured prefix.
   *
   * @param configuration       the configuration to be validated
   * @param accumulator         the exception condition accumulator
   * @param localizationContext the localization context
   */
  static void checkPrefix(Configured configuration, PluginExceptionConditionAccumulator accumulator,
      LocalizationContext localizationContext) {

    String instanceNamePrefix = configuration.getConfigurationValue(INSTANCE_NAME_PREFIX, localizationContext);

    LOG.info(">> Validating prefix '{}'", instanceNamePrefix);

    if (instanceNamePrefix == null) {
      addError(accumulator, INSTANCE_NAME_PREFIX, localizationContext, null, PREFIX_MISSING_MSG);
    } else {
      int length = instanceNamePrefix.length();

      if (length < 1 || length > 26) {
        addError(accumulator, INSTANCE_NAME_PREFIX, localizationContext, null, INVALID_PREFIX_LENGTH_MSG);
      } else if (!instanceNamePrefixPattern.matcher(instanceNamePrefix).matches()) {
        addError(accumulator, INSTANCE_NAME_PREFIX, localizationContext, null, INVALID_PREFIX_MSG);
      }
    }
  }

  /**
   * Validates the configured username.
   *
   * @param configuration       the configuration to be validated
   * @param accumulator         the exception condition accumulator
   * @param localizationContext the localization context
   */
  static void checkUsername(Configured configuration, PluginExceptionConditionAccumulator accumulator,
      LocalizationContext localizationContext) {

    String instanceUsername = configuration.getConfigurationValue(MASTER_USERNAME, localizationContext);

    LOG.info(">> Validating username '{}'", instanceUsername);

    if (instanceUsername == null) {
      addError(accumulator, MASTER_USERNAME, localizationContext, null, USERNAME_MISSING_MSG);
    } else {
      int length = instanceUsername.length();

      if (length < 1 || length > 16) {
        addError(accumulator, INSTANCE_NAME_PREFIX, localizationContext, null, INVALID_USERNAME_LENGTH_MSG);
      }
    }
  }
}
