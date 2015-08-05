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
import static com.cloudera.director.google.compute.GoogleComputeProviderConfigurationValidator.REGION_NOT_FOUND_MSG;
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
import com.cloudera.director.google.shaded.com.google.api.services.compute.Compute;
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
import java.util.Collection;
import java.util.Locale;
import java.util.Map;

/**
 * Tests {@link com.cloudera.director.google.compute.GoogleComputeProviderConfigurationValidator}.
 */
public class GoogleComputeProviderConfigurationValidatorTest {

  private static final String PROJECT_ID = "some-project";
  private static final String REGION_NAME = "us-central1";

  private GoogleComputeProvider computeProvider;
  private GoogleCredentials credentials;
  private Compute compute;
  private GoogleComputeProviderConfigurationValidator validator;
  private PluginExceptionConditionAccumulator accumulator;
  private LocalizationContext localizationContext = new DefaultLocalizationContext(Locale.getDefault(), "");

  @Before
  public void setUp() throws IOException {
    computeProvider = mock(GoogleComputeProvider.class);
    credentials = mock(GoogleCredentials.class);
    compute = mock(Compute.class);
    validator = new GoogleComputeProviderConfigurationValidator(credentials);
    accumulator = new PluginExceptionConditionAccumulator();

    when(computeProvider.getCredentials()).thenReturn(credentials);
    when(computeProvider.getGoogleConfig()).thenReturn(TestUtils.buildGoogleConfig());
    when(computeProvider.getConfigurationValue(eq(REGION), any(LocalizationContext.class))).thenReturn(REGION_NAME);
    when(credentials.getCompute()).thenReturn(compute);
    when(credentials.getProjectId()).thenReturn(PROJECT_ID);
  }

  private Compute.Regions.Get mockComputeToRegion() throws IOException {
    Compute.Regions computeRegions = mock(Compute.Regions.class);
    Compute.Regions.Get computeRegionsGet = mock(Compute.Regions.Get.class);

    when(compute.regions()).thenReturn(computeRegions);
    when(computeRegions.get(PROJECT_ID, REGION_NAME)).thenReturn(computeRegionsGet);

    return computeRegionsGet;
  }

  @Test
  public void testCheckRegion() throws IOException {
    Compute.Regions.Get computeRegionsGet = mockComputeToRegion();
    when(computeRegionsGet.execute()).thenReturn(null);

    checkRegion(REGION_NAME);
    verify(computeRegionsGet).execute();
    verifyClean();
  }

  @Test
  public void testCheckRegion_NotFound() throws IOException {
    Compute.Regions.Get computeRegionsGet = mockComputeToRegion();

    GoogleJsonResponseException exception =
        GoogleJsonResponseExceptionFactoryTesting.newMock(new MockJsonFactory(), 404, "not found");
    when(computeRegionsGet.execute()).thenThrow(exception);

    checkRegion(REGION_NAME);
    verify(computeRegionsGet).execute();
    verifySingleError(REGION, REGION_NOT_FOUND_MSG, REGION_NAME, PROJECT_ID);
  }

  /**
   * Invokes checkRegion with the specified configuration.
   *
   * @param region the region name
   */
  protected void checkRegion(String region) {
    Map<String, String> configMap = Maps.newHashMap();
    configMap.put(REGION.unwrap().getConfigKey(), region);
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
