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

import static com.cloudera.director.google.sql.GoogleSQLInstanceTemplateConfigurationProperty.TIER;
import static com.cloudera.director.spi.v1.model.InstanceTemplate.InstanceTemplateConfigurationPropertyToken.INSTANCE_NAME_PREFIX;

import static com.cloudera.director.spi.v1.model.util.Validations.addError;

import com.cloudera.director.google.Configurations;
import com.cloudera.director.google.internal.GoogleCredentials;
import com.cloudera.director.spi.v1.model.ConfigurationValidator;
import com.cloudera.director.spi.v1.model.Configured;
import com.cloudera.director.spi.v1.model.LocalizationContext;
import com.cloudera.director.spi.v1.model.exception.PluginExceptionCondition;
import com.cloudera.director.spi.v1.model.exception.PluginExceptionConditionAccumulator;
import com.cloudera.director.spi.v1.model.exception.TransientProviderException;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.sqladmin.SQLAdmin;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

public class GoogleSQLInstanceTemplateConfigurationValidator implements ConfigurationValidator  {
  private static final Logger LOG =
      LoggerFactory.getLogger(GoogleSQLInstanceTemplateConfigurationValidator.class);

  @VisibleForTesting
  static final String INVALID_TIER_MSG = "Tier '%s' not found.";
  @VisibleForTesting
  static final List<String> TIERS = ImmutableList.of("D0", "D1", "D2", "D4", "D8", "D16", "D32");
  @VisibleForTesting
  static final String PREFIX_MISSING_MSG = "Instance name prefix must be provided.";
  @VisibleForTesting
  static final String INVALID_PREFIX_LENGTH_MSG = "Instance name prefix must be between 1 and 26 characters.";
  @VisibleForTesting
  static final String INVALID_PREFIX_MSG = "Instance name prefix must follow this pattern: " +
      "The first character must be a lowercase letter, and all following characters must be a dash, lowercase " +
      "letter, or digit.";

  /**
   * The Google SQL provider.
   */
  private final GoogleSQLProvider provider;

  /**
   * The pattern to which instance name prefixes must conform. The pattern is the same as that for instance names in
   * general, except that we allow a trailing dash to be used. This is allowed since we always append a dash and the
   * specified instance id to the prefix.
   *
   *  @see <a href="https://developers.google.com/resources/api-libraries/documentation/compute/v1/java/latest/com/google/api/services/compute/model/Instance.html#setName(java.lang.String)" />
   *  TODO fix link
   */
  private final static Pattern instanceNamePrefixPattern = Pattern.compile("[a-z][-a-z0-9]*");

  /**
   * Creates a Google SQL instance template configuration validator with the specified
   * parameters.
   *
   * @param provider the Google SQL provider
   */
  public GoogleSQLInstanceTemplateConfigurationValidator(GoogleSQLProvider provider) {
    this.provider = Preconditions.checkNotNull(provider, "provider");
  }

  @Override
  public void validate(String name, Configured configuration,
      PluginExceptionConditionAccumulator accumulator, LocalizationContext localizationContext) {

    checkTier(configuration, accumulator, localizationContext);
    checkPrefix(configuration, accumulator, localizationContext);
  }

  /**
   * Validates the configured tier type.
   *
   * @param configuration       the configuration to be validated
   * @param accumulator         the exception condition accumulator
   * @param localizationContext the localization context
   */
  void checkTier(Configured configuration,
      PluginExceptionConditionAccumulator accumulator,
      LocalizationContext localizationContext) {

    String tier = configuration.getConfigurationValue(TIER, localizationContext);

    // TODO Might want to make an API call instead.
    // SQL API currently is supporting only list() method.
    if (tier != null && !TIERS.contains(tier)) {
      addError(accumulator, TIER, localizationContext, null, INVALID_TIER_MSG, tier);
    }
  }

  /**
   * Validates the configured prefix.
   *
   * @param configuration       the configuration to be validated
   * @param accumulator         the exception condition accumulator
   * @param localizationContext the localization context
   */
  static void checkPrefix(Configured configuration,
      PluginExceptionConditionAccumulator accumulator,
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
}
