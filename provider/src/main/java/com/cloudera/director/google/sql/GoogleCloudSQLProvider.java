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

import static com.cloudera.director.google.sql.GoogleCloudSQLInstanceTemplateConfigurationProperty.MASTER_USERNAME;
import static com.cloudera.director.google.sql.GoogleCloudSQLInstanceTemplateConfigurationProperty.MASTER_USER_PASSWORD;
import static com.cloudera.director.google.sql.GoogleCloudSQLInstanceTemplateConfigurationProperty.TIER;
import static com.cloudera.director.google.sql.GoogleCloudSQLProviderConfigurationProperty.REGION_SQL;
import static com.cloudera.director.spi.v1.model.InstanceTemplate.InstanceTemplateConfigurationPropertyToken.INSTANCE_NAME_PREFIX;

import com.cloudera.director.google.util.Urls;
import com.cloudera.director.google.internal.GoogleCredentials;
import com.cloudera.director.spi.v1.database.DatabaseType;
import com.cloudera.director.spi.v1.database.util.AbstractDatabaseServerInstance;
import com.cloudera.director.spi.v1.database.util.AbstractDatabaseServerProvider;
import com.cloudera.director.spi.v1.database.util.SimpleDatabaseServerProviderMetadata;
import com.cloudera.director.spi.v1.model.ConfigurationProperty;
import com.cloudera.director.spi.v1.model.ConfigurationValidator;
import com.cloudera.director.spi.v1.model.Configured;
import com.cloudera.director.spi.v1.model.InstanceState;
import com.cloudera.director.spi.v1.model.InstanceStatus;
import com.cloudera.director.spi.v1.model.LocalizationContext;
import com.cloudera.director.spi.v1.model.Resource;
import com.cloudera.director.spi.v1.model.util.CompositeConfigurationValidator;
import com.cloudera.director.spi.v1.model.exception.InvalidCredentialsException;
import com.cloudera.director.spi.v1.model.exception.PluginExceptionCondition;
import com.cloudera.director.spi.v1.model.exception.PluginExceptionConditionAccumulator;
import com.cloudera.director.spi.v1.model.exception.PluginExceptionDetails;
import com.cloudera.director.spi.v1.model.exception.TransientProviderException;
import com.cloudera.director.spi.v1.model.exception.UnrecoverableProviderException;
import com.cloudera.director.spi.v1.model.util.SimpleInstanceState;
import com.cloudera.director.spi.v1.model.util.SimpleResourceTemplate;
import com.cloudera.director.spi.v1.provider.ResourceProviderMetadata;
import com.cloudera.director.spi.v1.util.ConfigurationPropertiesUtil;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.sqladmin.model.AclEntry;
import com.google.api.services.sqladmin.model.DatabaseInstance;
import com.google.api.services.sqladmin.model.IpConfiguration;
import com.google.api.services.sqladmin.model.Operation;
import com.google.api.services.sqladmin.model.OperationError;
import com.google.api.services.sqladmin.model.OperationErrors;
import com.google.api.services.sqladmin.model.Settings;
import com.google.api.services.sqladmin.model.User;
import com.google.api.services.sqladmin.SQLAdmin;
import com.typesafe.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;

public class GoogleCloudSQLProvider
    extends AbstractDatabaseServerProvider<GoogleCloudSQLInstance, GoogleCloudSQLInstanceTemplate> {

  private static final Logger LOG = LoggerFactory.getLogger(GoogleCloudSQLProvider.class);

  private static final List<String> DONE_STATE = Arrays.asList(new String[]{"DONE"});
  private static final List<String> RUNNING_OR_DONE_STATES = Arrays.asList(new String[]{"RUNNING", "DONE"});

  protected static final List<ConfigurationProperty> CONFIGURATION_PROPERTIES =
      ConfigurationPropertiesUtil.asConfigurationPropertyList(
          GoogleCloudSQLProviderConfigurationProperty.values());

  /**
   * The resource provider ID.
   */
  public static final String ID = GoogleCloudSQLProvider.class.getCanonicalName();

  public static final ResourceProviderMetadata METADATA = SimpleDatabaseServerProviderMetadata
      .databaseServerProviderMetadataBuilder()
      .id(ID)
      .name("Google Cloud SQL")
      .description("Google Cloud SQL provider")
      .providerClass(GoogleCloudSQLProvider.class)
      .providerConfigurationProperties(CONFIGURATION_PROPERTIES)
      .resourceTemplateConfigurationProperties(
          GoogleCloudSQLInstanceTemplate.getConfigurationProperties())
      .supportedDatabaseTypes(new HashSet<DatabaseType>(Arrays.asList(DatabaseType.MYSQL)))
      .resourceDisplayProperties(GoogleCloudSQLInstance.getDisplayProperties())
      .build();

  private GoogleCredentials credentials;
  private Config applicationProperties;
  private Config googleConfig;

  private final ConfigurationValidator resourceTemplateConfigurationValidator;

  public GoogleCloudSQLProvider(Configured configuration, GoogleCredentials credentials,
      Config applicationProperties, Config googleConfig, LocalizationContext cloudLocalizationContext) {
    super(configuration, METADATA, cloudLocalizationContext);

    this.credentials = credentials;
    this.applicationProperties = applicationProperties;
    this.googleConfig = googleConfig;

    SQLAdmin sqlAdmin = credentials.getSQLAdmin();
    String projectId = credentials.getProjectId();

    // Throws GoogleJsonResponseException if no tiers can be located.

    try {
      sqlAdmin.tiers().list(projectId).execute();
    } catch (GoogleJsonResponseException e) {
      if (e.getStatusCode() == 404) {
          throw new InvalidCredentialsException("Unable to list tiers in project: " + projectId, e);
      } else {
          throw new TransientProviderException(e);
      }
    } catch (IOException e) {
      throw new TransientProviderException(e);
    }

    this.resourceTemplateConfigurationValidator =
        new CompositeConfigurationValidator(METADATA.getResourceTemplateConfigurationValidator(),
            new GoogleCloudSQLInstanceTemplateConfigurationValidator(this));
  }

  @Override
  public ResourceProviderMetadata getProviderMetadata() {
    return METADATA;
  }

  @Override
  public ConfigurationValidator getResourceTemplateConfigurationValidator() {
    return resourceTemplateConfigurationValidator;
  }

  @Override
  public Resource.Type getResourceType() {
    return AbstractDatabaseServerInstance.TYPE;
  }

  @Override
  public GoogleCloudSQLInstanceTemplate createResourceTemplate(
      String name, Configured configuration, Map<String, String> tags) {
    return new GoogleCloudSQLInstanceTemplate(name, configuration, tags, getLocalizationContext());
  }

  @Override
  public void allocate(GoogleCloudSQLInstanceTemplate template,
      Collection<String> instanceIds, int minCount) throws InterruptedException {

    PluginExceptionConditionAccumulator accumulator = new PluginExceptionConditionAccumulator();

    LocalizationContext providerLocalizationContext = getLocalizationContext();
    LocalizationContext templateLocalizationContext =
        SimpleResourceTemplate.getTemplateLocalizationContext(providerLocalizationContext);

    SQLAdmin sqlAdmin = credentials.getSQLAdmin();
    String projectId = credentials.getProjectId();

    // Use this list to collect the operations that must reach a RUNNING or DONE state prior to allocate() returning.
    List<Operation> dbCreationOperations = new ArrayList<Operation>();
    List<Operation> userCreationOperations = new ArrayList<Operation>();
    List<String> successfullyCreatedInstancesNames = new ArrayList<String>();

    for (String instanceId : instanceIds) {
      String decoratedInstanceName = decorateInstanceName(template, instanceId, templateLocalizationContext);

      // Compose the settings.
      Settings settings = new Settings();

      String tierName = template.getConfigurationValue(TIER, templateLocalizationContext);
      settings.setTier(tierName);

      IpConfiguration ipConfiguration = new IpConfiguration();
      ipConfiguration.setIpv4Enabled(true);

      // TODO(kl3n1nz) Might want to whitelist only GCE instances.
      AclEntry aclEntry = new AclEntry();
      aclEntry.setValue("0.0.0.0/0");
      aclEntry.setKind("sql#aclEntry");
      aclEntry.setName("world");

      ipConfiguration.setAuthorizedNetworks(Arrays.asList(aclEntry));
      settings.setIpConfiguration(ipConfiguration);

      // Compose the instance.
      DatabaseInstance instance = new DatabaseInstance();
      String regionName = template.getConfigurationValue(REGION_SQL, templateLocalizationContext);

      instance.setName(decoratedInstanceName);
      instance.setRegion(regionName);
      instance.setSettings(settings);


      try {
        // This is an async operation. We must poll until it completes to confirm the disk exists.
        Operation dbCreationOperation = sqlAdmin.instances().insert(projectId, instance).execute();

        dbCreationOperations.add(dbCreationOperation);
      } catch (GoogleJsonResponseException e) {
        if (e.getStatusCode() == 409) {
          LOG.info("Database instance '{}' already exists.", instance.getName());
          successfullyCreatedInstancesNames.add(decoratedInstanceName);
        } else {
          accumulator.addError(null, e.getMessage());
        }
      } catch (IOException e) {
        accumulator.addError(null, e.getMessage());
      }
    }

    // Wait for operations to reach DONE state before returning.
    // This is the status of the Operations we're referring to, not of the Instances.
    successfullyCreatedInstancesNames.addAll(pollPendingOperations(projectId, dbCreationOperations, DONE_STATE,
        sqlAdmin, accumulator));

    for (String instanceName : successfullyCreatedInstancesNames) {
      User user = new User();
      user.setName(template.getConfigurationValue(MASTER_USERNAME, templateLocalizationContext));
      user.setPassword(template.getConfigurationValue(MASTER_USER_PASSWORD, templateLocalizationContext));

      try {
        Operation userCreationOperation = sqlAdmin.users().insert(projectId, instanceName, user).execute();

        userCreationOperations.add(userCreationOperation);
      } catch (IOException e) {
        accumulator.addError(null, e.getMessage());
      }
    }

    int successfulOperationCount =
        pollPendingOperations(projectId, userCreationOperations, DONE_STATE, sqlAdmin, accumulator).size();

    if (successfulOperationCount < minCount) {
      LOG.info("Provisioned {} instances out of {}. minCount is {}. Tearing down provisioned instances.",
          successfulOperationCount, instanceIds.size(), minCount);

      tearDownResources(projectId, dbCreationOperations, sqlAdmin, accumulator);

      PluginExceptionDetails pluginExceptionDetails = new PluginExceptionDetails(accumulator.getConditionsByKey());
      throw new UnrecoverableProviderException("Problem allocating instances.", pluginExceptionDetails);
    } else if (successfulOperationCount < instanceIds.size()) {
      LOG.info("Provisioned {} instances out of {}. minCount is {}.",
          successfulOperationCount, instanceIds.size(), minCount);

      // Even through we are not throwing an exception, we still want to log the errors.
      if (accumulator.hasError()) {
        Map<String, Collection<PluginExceptionCondition>> conditionsByKeyMap = accumulator.getConditionsByKey();

        for (Map.Entry<String, Collection<PluginExceptionCondition>> keyToCondition : conditionsByKeyMap.entrySet()) {
          String key = keyToCondition.getKey();

          if (key != null) {
            for (PluginExceptionCondition condition : keyToCondition.getValue()) {
              LOG.info("({}) {}: {}", condition.getType(), key, condition.getMessage());
            }
          } else {
            for (PluginExceptionCondition condition : keyToCondition.getValue()) {
              LOG.info("({}) {}", condition.getType(), condition.getMessage());
            }
          }
        }
      }
    }
  }

  // Delete all instances.
  private void tearDownResources(String projectId, List<Operation> dbCreationOperations,
      SQLAdmin sqlAdmin, PluginExceptionConditionAccumulator accumulator) throws InterruptedException {

    // Use this list to keep track of database instance deletion operations.
    List<Operation> tearDownOperations = new ArrayList<Operation>();

    // Iterate over each instance creation operation.
    for (Operation dbCreationOperation : dbCreationOperations) {
      String dbName = dbCreationOperation.getTargetId();

      try {
        Operation tearDownOperation = sqlAdmin.instances().delete(projectId, dbName).execute();

        tearDownOperations.add(tearDownOperation);
      } catch (GoogleJsonResponseException e) {
        if (e.getStatusCode() == 404) {
          // Ignore this.
        } else {
          accumulator.addError(null, e.getMessage());
        }
      } catch (IOException e) {
        accumulator.addError(null, e.getMessage());
      }
    }

    int tearDownOperationCount = tearDownOperations.size();
    int successfulTearDownOperationCount =
        pollPendingOperations(projectId, tearDownOperations, DONE_STATE, sqlAdmin, accumulator).size();

    if (successfulTearDownOperationCount < tearDownOperationCount) {
        accumulator.addError(null, successfulTearDownOperationCount + " of the " + tearDownOperationCount +
            " tear down operations completed successfully.");
    }
  }

  @Override
  public Collection<GoogleCloudSQLInstance> find(GoogleCloudSQLInstanceTemplate template, Collection<String> instanceIds)
      throws InterruptedException {
    LocalizationContext providerLocalizationContext = getLocalizationContext();
    LocalizationContext templateLocalizationContext =
        SimpleResourceTemplate.getTemplateLocalizationContext(providerLocalizationContext);

    List<GoogleCloudSQLInstance> result = new ArrayList<GoogleCloudSQLInstance>();

    // If the prefix is not valid, there is no way the instances could have been created in the first place.
    if (!isPrefixValid(template, templateLocalizationContext)) {
      return result;
    }

    for (String currentId : instanceIds) {
      SQLAdmin sqlAdmin = credentials.getSQLAdmin();
      String projectId = credentials.getProjectId();
      String decoratedInstanceName = decorateInstanceName(template, currentId, templateLocalizationContext);

      try {
        DatabaseInstance instance = sqlAdmin.instances().get(projectId, decoratedInstanceName).execute();
        result.add(new GoogleCloudSQLInstance(template, currentId, instance));
      } catch (GoogleJsonResponseException e) {
        // There are two ways of saying that instance doesn't exist. If it was just deleted it will throw 404 Error.
        // If it never existed for some long time it will throw 403 Error.
        if (e.getStatusCode() == 404) {
          LOG.info("Database instance '{}' doesn't exist anymore.", decoratedInstanceName);
        } else if (e.getStatusCode() == 403) {
          LOG.info("Database instance '{}' not found.", decoratedInstanceName);
        } else {
          throw new RuntimeException(e);
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    return result;
  }

  @Override
  public Map<String, InstanceState> getInstanceState(GoogleCloudSQLInstanceTemplate template,
      Collection<String> instanceIds) {
    LocalizationContext providerLocalizationContext = getLocalizationContext();
    LocalizationContext templateLocalizationContext =
        SimpleResourceTemplate.getTemplateLocalizationContext(providerLocalizationContext);

    Map<String, InstanceState> result = new HashMap<String, InstanceState>();

    // If the prefix is not valid, there is no way the instances could have been created in the first place.
    if (!isPrefixValid(template, templateLocalizationContext)) {
      for (String currentId : instanceIds) {
        result.put(currentId, new SimpleInstanceState(InstanceStatus.UNKNOWN));
      }
    } else {
      for (String currentId : instanceIds) {
        SQLAdmin sqlAdmin = credentials.getSQLAdmin();
        String projectId = credentials.getProjectId();

        String decoratedInstanceName = decorateInstanceName(template, currentId, templateLocalizationContext);

        try {
          // TODO(duftler): Might want to store the entire instance representation in the InstanceState object.
          DatabaseInstance instance = sqlAdmin.instances().get(projectId, decoratedInstanceName).execute();
          InstanceStatus instanceStatus = convertSQLInstanceStatusToDirectorInstanceStatus(instance.getState());

          result.put(currentId, new SimpleInstanceState(instanceStatus));
        } catch (GoogleJsonResponseException e) {
          // There are two ways of saying that instance doesn't exist. If it was just deleted it will throw 404 Error.
          // If it never existed for some long time it will throw 403 Error.
          if (e.getStatusCode() == 404) {
            LOG.info("Database instance '{}' doesn't exist anymore.", decoratedInstanceName);

            // Might want to return DELETED instead but in this case a lot of code has to be refactored.
            result.put(currentId, new SimpleInstanceState(InstanceStatus.UNKNOWN));
          } else if (e.getStatusCode() == 403) {
            LOG.info("Database instance '{}' not found.", decoratedInstanceName);

            result.put(currentId, new SimpleInstanceState(InstanceStatus.UNKNOWN));
          } else {
            throw new RuntimeException(e);
          }
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    }
    return result;
  }

  @Override
  public void delete(GoogleCloudSQLInstanceTemplate template,
      Collection<String> instanceIds) throws InterruptedException {

    PluginExceptionConditionAccumulator accumulator = new PluginExceptionConditionAccumulator();

    LocalizationContext providerLocalizationContext = getLocalizationContext();
    LocalizationContext templateLocalizationContext =
        SimpleResourceTemplate.getTemplateLocalizationContext(providerLocalizationContext);

    // If the prefix is not valid, there is no way the instances could have been created in the first place.
    // So we shouldn't attempt to delete them, but we also shouldn't report an error.
    if (!isPrefixValid(template, templateLocalizationContext)) {
      return;
    }

    SQLAdmin sqlAdmin = credentials.getSQLAdmin();
    String projectId = credentials.getProjectId();

    // Use this list to collect the operations that must reach a RUNNING or DONE state prior to delete() returning.
    List<Operation> dbDeletionOperations = new ArrayList<Operation>();

    for (String currentId : instanceIds) {
      String decoratedInstanceName = decorateInstanceName(template, currentId, templateLocalizationContext);

      try {
        Operation dbDeletionOperation = sqlAdmin.instances().delete(projectId, decoratedInstanceName).execute();

        dbDeletionOperations.add(dbDeletionOperation);
      } catch (GoogleJsonResponseException e) {
        if (e.getStatusCode() == 404) {
          LOG.info("Attempted to delete database instance '{}', but it does not exist.", decoratedInstanceName);
        } else if (e.getStatusCode() == 409) {
          LOG.info("Attempted to delete instance '{}', but it's already in the process of being deleted.",
              decoratedInstanceName);
        } else {
          accumulator.addError(null, e.getMessage());
        }
      } catch (IOException e) {
        accumulator.addError(null, e.getMessage());
      }
    }

    // Wait for operations to reach RUNNING or DONE state before returning.
    // Quotas are verified prior to reaching the RUNNING state.
    // This is the status of the Operations we're referring to, not of the Instances.
    pollPendingOperations(projectId, dbDeletionOperations, RUNNING_OR_DONE_STATES, sqlAdmin, accumulator);

    if (accumulator.hasError()) {
      PluginExceptionDetails pluginExceptionDetails = new PluginExceptionDetails(accumulator.getConditionsByKey());
      throw new UnrecoverableProviderException("Problem deleting instances.", pluginExceptionDetails);
    }
  }

  public GoogleCredentials getCredentials() {
    return credentials;
  }

  public Config getGoogleConfig() {
    return googleConfig;
  }

  private static String decorateInstanceName(GoogleCloudSQLInstanceTemplate template, String currentId,
      LocalizationContext templateLocalizationContext) {
    return template.getConfigurationValue(INSTANCE_NAME_PREFIX, templateLocalizationContext) + "-" + currentId;
  }

  private static InstanceStatus convertSQLInstanceStatusToDirectorInstanceStatus(String sqlInstanceStatus) {
    if (sqlInstanceStatus.equals("PENDING_CREATE")) {
      return InstanceStatus.PENDING;
    } else if (sqlInstanceStatus.equals("RUNNABLE")) {
      return InstanceStatus.RUNNING;
    } else if (sqlInstanceStatus.equals("SUSPENDED") || sqlInstanceStatus.equals("MAINTENANCE"))
      return InstanceStatus.STOPPED;
    else if (sqlInstanceStatus.equals("FAILED")) {
      return InstanceStatus.FAILED;
    } else {
      return InstanceStatus.UNKNOWN;
    }
  }

  // Poll until 0 operations remain in the passed pendingOperations list.
  // An operation is removed from the list once it reaches one of the states in acceptableStates.
  // The list is cloned and not directly modified.
  // All arguments are required and must be non-null.
  // Returns the number of operations that reached one of the acceptable states within the timeout period.
  private static List<String> pollPendingOperations(String projectId, List<Operation> origPendingOperations,
      List<String> acceptableStates, SQLAdmin sqlAdmin, PluginExceptionConditionAccumulator accumulator)
          throws InterruptedException {
    // Clone the list so we can prune it without modifying the original.
    List<Operation> pendingOperations = new ArrayList<Operation>(origPendingOperations);

    int totalTimePollingSeconds = 0;
    int pollingTimeoutSeconds = 180;
    int maxPollingIntervalSeconds = 8;
    boolean timeoutExceeded = false;

    // Fibonacci backoff in seconds, up to maxPollingIntervalSeconds interval.
    int pollInterval = 1;
    int pollIncrement = 0;

    // Use this list to keep track of each operation that reached one of the acceptable states.
    List<String> successfulTargets = new ArrayList<String>();

    while (pendingOperations.size() > 0 && !timeoutExceeded) {
      Thread.currentThread().sleep(pollInterval * 1000);

      totalTimePollingSeconds += pollInterval;

      List<Operation> completedOperations = new ArrayList<Operation>();

      for (Operation pendingOperation : pendingOperations) {
        try {
          String pendingOperationName = pendingOperation.getName();
          Operation subjectOperation = sqlAdmin.operations().get(projectId, pendingOperationName).execute();
          OperationErrors errors = subjectOperation.getError();
          boolean isActualError = false;

          if (errors != null) {
            List<OperationError> errorsList = errors.getErrors();

            if (errorsList != null) {
              for (OperationError error : errorsList) {
                accumulator.addError(null, error.getMessage());
                isActualError = true;
              }
            }
          }

          if (acceptableStates.contains(subjectOperation.getStatus())) {
            completedOperations.add(pendingOperation);

            if (!isActualError) {
              successfulTargets.add(pendingOperation.getTargetId());
            }
          }
        } catch (IOException e) {
          accumulator.addError(null, e.getMessage());
        }
      }

      // Remove all operations that reached an acceptable state.
      pendingOperations.removeAll(completedOperations);

      if (pendingOperations.size() > 0 && totalTimePollingSeconds > pollingTimeoutSeconds) {
        List<String> pendingOperationNames = new ArrayList<String>();

        for (Operation pendingOperation : pendingOperations) {
          pendingOperationNames.add(pendingOperation.getName());
        }

        accumulator.addError(null, "Exceeded timeout of '" + pollingTimeoutSeconds + "' seconds while " +
            "polling for pending operations to complete: " + pendingOperationNames);

        timeoutExceeded = true;
      } else {
        // Update polling interval.
        int oldIncrement = pollIncrement;
        pollIncrement = pollInterval;
        pollInterval += oldIncrement;
        pollInterval = Math.min(pollInterval, maxPollingIntervalSeconds);
      }
    }

    return successfulTargets;
  }

  private boolean isPrefixValid(GoogleCloudSQLInstanceTemplate template,
      LocalizationContext templateLocalizationContext) {
    PluginExceptionConditionAccumulator accumulator = new PluginExceptionConditionAccumulator();

    GoogleCloudSQLInstanceTemplateConfigurationValidator.checkPrefix(template, accumulator, templateLocalizationContext);

    boolean isValid = accumulator.getConditionsByKey().isEmpty();

    if (!isValid) {
      LOG.info("Instance name prefix '{}' is invalid.",
          template.getConfigurationValue(INSTANCE_NAME_PREFIX, templateLocalizationContext));
    }

    return isValid;
  }
}
