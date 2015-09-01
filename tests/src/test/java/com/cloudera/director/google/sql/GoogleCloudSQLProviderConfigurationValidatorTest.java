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
import static com.cloudera.director.google.sql.GoogleCloudSQLProviderConfigurationValidator.REGION_NOT_FOUND_MSG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudera.director.google.TestUtils;
import com.cloudera.director.google.internal.GoogleCredentials;
import com.cloudera.director.google.shaded.com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.cloudera.director.google.shaded.com.google.api.client.googleapis.testing.json.GoogleJsonResponseExceptionFactoryTesting;
import com.cloudera.director.google.shaded.com.google.api.client.testing.json.MockJsonFactory;
import com.cloudera.director.google.shaded.com.google.api.services.sqladmin.SQLAdmin;
import com.cloudera.director.google.shaded.com.google.api.services.sqladmin.model.Tier;
import com.cloudera.director.google.shaded.com.google.api.services.sqladmin.model.TiersListResponse;
import com.cloudera.director.spi.v1.model.ConfigurationPropertyToken;
import com.cloudera.director.spi.v1.model.Configured;
import com.cloudera.director.spi.v1.model.LocalizationContext;
import com.cloudera.director.spi.v1.model.exception.PluginExceptionCondition;
import com.cloudera.director.spi.v1.model.exception.PluginExceptionConditionAccumulator;
import com.cloudera.director.spi.v1.model.util.DefaultLocalizationContext;
import com.cloudera.director.spi.v1.model.util.SimpleConfiguration;
import com.google.common.base.Optional;
import org.assertj.core.util.Maps;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;

/**
 * Tests {@link GoogleCloudSQLProviderConfigurationValidator}.
 */
public class GoogleCloudSQLProviderConfigurationValidatorTest {

  private static final String PROJECT_ID = "some-project";
  private static final String REGION_NAME = "us-central";

  private GoogleCloudSQLProvider sqlAdminProvider;
  private GoogleCredentials credentials;
  private SQLAdmin sqlAdmin;
  private GoogleCloudSQLProviderConfigurationValidator validator;
  private PluginExceptionConditionAccumulator accumulator;
  private LocalizationContext localizationContext = new DefaultLocalizationContext(Locale.getDefault(), "");

  @Before
  public void setUp() throws IOException {
    sqlAdminProvider = mock(GoogleCloudSQLProvider.class);
    credentials = mock(GoogleCredentials.class);
    sqlAdmin = mock(SQLAdmin.class);
    validator = new GoogleCloudSQLProviderConfigurationValidator(credentials);
    accumulator = new PluginExceptionConditionAccumulator();

    when(sqlAdminProvider.getCredentials()).thenReturn(credentials);
    when(sqlAdminProvider.getGoogleConfig()).thenReturn(TestUtils.buildGoogleConfig());
    when(sqlAdminProvider.getConfigurationValue(eq(REGION_SQL), any(LocalizationContext.class))).thenReturn(REGION_NAME);
    when(credentials.getSQLAdmin()).thenReturn(sqlAdmin);
    when(credentials.getProjectId()).thenReturn(PROJECT_ID);
  }

  private TiersListResponse mockSQLAdminToTiersListResponse() throws IOException {
    SQLAdmin.Tiers sqlAdminTiers = mock(SQLAdmin.Tiers.class);
    SQLAdmin.Tiers.List sqlAdminList = mock(SQLAdmin.Tiers.List.class);
    TiersListResponse tiersListResponse = new TiersListResponse();

    when(sqlAdmin.tiers()).thenReturn(sqlAdminTiers);
    when(sqlAdminTiers.list(PROJECT_ID)).thenReturn(sqlAdminList);
    when(sqlAdminList.execute()).thenReturn(tiersListResponse);

    return tiersListResponse;
  }

  @Test
  public void testCheckRegion() throws IOException {
    TiersListResponse tiersListResponse = mockSQLAdminToTiersListResponse();

    Tier tier = new Tier();
    tier.setRegion(Arrays.asList(REGION_NAME));
    tiersListResponse.setItems(Arrays.asList(tier));

    checkRegion(REGION_NAME);
    verifyClean();
  }

  @Test
  public void testCheckRegion_NotFound() throws IOException {
    TiersListResponse tiersListResponse = mockSQLAdminToTiersListResponse();
    tiersListResponse.setItems(null);

    checkRegion(REGION_NAME);
    verifySingleError(REGION_SQL, REGION_NOT_FOUND_MSG, REGION_NAME, PROJECT_ID);
  }

  /**
   * Invokes checkRegion with the specified configuration.
   *
   * @param region the region name
   */
  protected void checkRegion(String region) {
    Map<String, String> configMap = Maps.newHashMap();
    configMap.put(REGION_SQL.unwrap().getConfigKey(), region);
    Configured configuration = new SimpleConfiguration(configMap);
    validator.checkRegion(configuration, accumulator, localizationContext);
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
  private void verifySingleError(ConfigurationPropertyToken token, Optional<String> errorMsgFormat, Object... args) {
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
  private void verifySingleErrorCondition(PluginExceptionCondition condition, Optional<String> errorMsgFormat,
      Object... args) {
    assertThat(condition.isError()).isTrue();
    if (errorMsgFormat.isPresent()) {
      assertThat(condition.getMessage()).isEqualTo(String.format(errorMsgFormat.get(), args));
    }
  }
}