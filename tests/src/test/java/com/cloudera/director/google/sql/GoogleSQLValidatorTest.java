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

/*
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationProperty.BOOT_DISK_SIZE_GB;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationProperty.DATA_DISK_COUNT;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationProperty.DATA_DISK_SIZE_GB;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationProperty.DATA_DISK_TYPE;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationProperty.IMAGE;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationProperty.NETWORK_NAME;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationProperty.TYPE;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationProperty.ZONE;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationValidator.DATA_DISK_TYPES;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationValidator.EXACT_LOCAL_SSD_DATA_DISK_SIZE_GB;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationValidator.IMAGE_NOT_FOUND_MSG;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationValidator.INVALID_BOOT_DISK_SIZE_FORMAT_MSG;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationValidator.INVALID_BOOT_DISK_SIZE_MSG;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationValidator.INVALID_DATA_DISK_COUNT_FORMAT_MSG;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationValidator.INVALID_DATA_DISK_COUNT_NEGATIVE_MSG;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationValidator.INVALID_DATA_DISK_SIZE_FORMAT_MSG;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationValidator.INVALID_DATA_DISK_SIZE_MSG;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationValidator.INVALID_DATA_DISK_TYPE_MSG;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationValidator.INVALID_LOCAL_SSD_DATA_DISK_COUNT_MSG;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationValidator.INVALID_LOCAL_SSD_DATA_DISK_SIZE_MSG;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationValidator.INVALID_PREFIX_LENGTH_MSG;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationValidator.INVALID_PREFIX_MSG;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationValidator.MACHINE_TYPE_NOT_FOUND_IN_ZONE_MSG;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationValidator.MAPPING_FOR_IMAGE_ALIAS_NOT_FOUND;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationValidator.MAX_LOCAL_SSD_COUNT;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationValidator.MIN_BOOT_DISK_SIZE_GB;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationValidator.MIN_DATA_DISK_SIZE_GB;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationValidator.MIN_LOCAL_SSD_COUNT;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationValidator.NETWORK_NOT_FOUND_MSG;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationValidator.PREFIX_MISSING_MSG;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationValidator.ZONE_NOT_FOUND_IN_REGION_MSG;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationValidator.ZONE_NOT_FOUND_MSG;
import static com.cloudera.director.google.compute.GoogleComputeProviderConfigurationProperty.REGION;
*/
import static com.cloudera.director.google.sql.GoogleSQLInstanceTemplateConfigurationValidator.INVALID_TIER_MSG;
import static com.cloudera.director.google.sql.GoogleSQLInstanceTemplateConfigurationProperty.TIER;
import static com.cloudera.director.google.sql.GoogleSQLProviderConfigurationProperty.REGION_SQL;

import static com.cloudera.director.spi.v1.model.InstanceTemplate.InstanceTemplateConfigurationPropertyToken.INSTANCE_NAME_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudera.director.google.TestUtils;
import com.cloudera.director.google.compute.util.Urls;
import com.cloudera.director.google.internal.GoogleCredentials;
import com.cloudera.director.google.shaded.com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.cloudera.director.google.shaded.com.google.api.client.googleapis.testing.json.GoogleJsonResponseExceptionFactoryTesting;
import com.cloudera.director.google.shaded.com.google.api.client.testing.json.MockJsonFactory;
import com.cloudera.director.google.shaded.com.google.api.services.sqladmin.SQLAdmin;
import com.cloudera.director.spi.v1.model.ConfigurationPropertyToken;
import com.cloudera.director.spi.v1.model.Configured;
import com.cloudera.director.spi.v1.model.LocalizationContext;
import com.cloudera.director.spi.v1.model.exception.PluginExceptionCondition;
import com.cloudera.director.spi.v1.model.exception.PluginExceptionConditionAccumulator;
import com.cloudera.director.spi.v1.model.util.DefaultLocalizationContext;
import com.cloudera.director.spi.v1.model.util.SimpleConfiguration;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import org.assertj.core.util.Maps;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;

/**
 * Tests {@link GoogleSQLInstanceTemplateConfigurationValidator}.
 * Tests {@link GoogleSQLProviderConfigurationValidator}.
 */
public class GoogleSQLValidatorTest {

  private static final String PROJECT_ID = "some-project";
  private static final String REGION_NAME_1 = "us-central";
  private static final String REGION_NAME_2 = "europe-west1";
  private static final String TIER_NAME = "D4";
  private static final String TIER_NAME_WRONG = "D15";


  private GoogleSQLProvider sqlProvider;
  private GoogleCredentials credentials;
  private SQLAdmin sqlAdmin;
  private GoogleSQLInstanceTemplateConfigurationValidator validator;
  private PluginExceptionConditionAccumulator accumulator;
  private LocalizationContext localizationContext = new DefaultLocalizationContext(Locale.getDefault(), "");

  @Before
  public void setUp() throws IOException {
    sqlProvider = mock(GoogleSQLProvider.class);
    credentials = mock(GoogleCredentials.class);
    sqlAdmin = mock(SQLAdmin.class);
    validator = new GoogleSQLInstanceTemplateConfigurationValidator(sqlProvider);
    accumulator = new PluginExceptionConditionAccumulator();

    when(sqlProvider.getCredentials()).thenReturn(credentials);
    when(sqlProvider.getGoogleConfig()).thenReturn(TestUtils.buildGoogleConfig());
    when(sqlProvider.getConfigurationValue(eq(REGION_SQL), any(LocalizationContext.class))).thenReturn(REGION_NAME_1);
    when(credentials.getSQLAdmin()).thenReturn(sqlAdmin);
    when(credentials.getProjectId()).thenReturn(PROJECT_ID);
  }

  @Test
  public void testCheckRegion() throws IOException {
    // TODO
  }

  @Test
  public void testCheckTier() throws IOException {
    checkTier(TIER_NAME);
    verifyClean();
  }

  @Test
  public void testCheckTier_WrongTier() throws IOException {
    checkTier(TIER_NAME_WRONG);
    verifySingleError(TIER, INVALID_TIER_MSG, TIER_NAME_WRONG);
  }

  /**
   * Invokes checkPrefix with the specified configuration.
   *
   * @param tier the instance name prefix
   */
  protected void checkTier(String tier) {
    Map<String, String> configMap = Maps.newHashMap();
    configMap.put(TIER.unwrap().getConfigKey(), tier);
    Configured configuration = new SimpleConfiguration(configMap);
    validator.checkTier(configuration, accumulator, localizationContext);
  }

  /**
   * Invokes checkPrefix with the specified configuration.
   *
   * @param prefix the instance name prefix
   */
  protected void checkPrefix(String prefix) {
    Map<String, String> configMap = Maps.newHashMap();
    configMap.put(INSTANCE_NAME_PREFIX.unwrap().getConfigKey(), prefix);
    Configured configuration = new SimpleConfiguration(configMap);
    validator.checkPrefix(configuration, accumulator, localizationContext);
  }

  /**
   * Verifies that the specified plugin exception condition accumulator contains no errors or
   * warnings.
   */
  private void verifyClean() {
    Map<String, Collection<PluginExceptionCondition>> conditionsByKey = accumulator.getConditionsByKey();
    assertThat(conditionsByKey).isEmpty();
  }

  /**
   * Verifies that the specified plugin exception condition accumulator contains exactly
   * one condition, which must be an error associated with the specified property.
   *
   * @param token the configuration property token for the property which should be in error
   */
  private void verifySingleError(ConfigurationPropertyToken token) {
    verifySingleError(token, Optional.<String>absent());
  }

  /**
   * Verifies that the specified plugin exception condition accumulator contains exactly
   * one condition, which must be an error with the specified message and associated with the
   * specified property.
   *
   * @param token    the configuration property token for the property which should be in error
   * @param errorMsg the expected error message
   * @param args     the error message arguments
   */
  private void verifySingleError(ConfigurationPropertyToken token, String errorMsg, Object... args) {
    verifySingleError(token, Optional.of(errorMsg), args);
  }

  /**
   * Verifies that the specified plugin exception condition accumulator contains exactly
   * one condition, which must be an error with the specified message and associated with the
   * specified property.
   *
   * @param token          the configuration property token for the property which should be in error
   * @param errorMsgFormat the expected error message
   * @param args           the error message arguments
   */
  private void verifySingleError(ConfigurationPropertyToken token, Optional<String> errorMsgFormat,
                                 Object... args) {
    Map<String, Collection<PluginExceptionCondition>> conditionsByKey = accumulator.getConditionsByKey();
    assertThat(conditionsByKey).hasSize(1);
    String configKey = token.unwrap().getConfigKey();
    assertThat(conditionsByKey.containsKey(configKey)).isTrue();
    Collection<PluginExceptionCondition> keyConditions = conditionsByKey.get(configKey);
    assertThat(keyConditions).hasSize(1);
    PluginExceptionCondition condition = keyConditions.iterator().next();
    verifySingleErrorCondition(condition, errorMsgFormat, args);
  }

  /**
   * Verifies that the specified plugin exception condition is an error with the specified message.
   *
   * @param condition      the plugin exception condition
   * @param errorMsgFormat the expected error message format
   * @param args           the error message arguments
   */
  private void verifySingleErrorCondition(PluginExceptionCondition condition,
                                          Optional<String> errorMsgFormat, Object... args) {
    assertThat(condition.isError()).isTrue();
    if (errorMsgFormat.isPresent()) {
      assertThat(condition.getMessage()).isEqualTo(String.format(errorMsgFormat.get(), args));
    }
  }
}
