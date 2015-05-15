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

package com.cloudera.director.google;

import static com.cloudera.director.google.GoogleCredentialsProviderConfigurationProperty.JSONKEY;
import static com.cloudera.director.google.GoogleCredentialsProviderConfigurationProperty.PROJECTID;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationProperty.LOCALSSDINTERFACETYPE;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationProperty.NETWORKNAME;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationProperty.ZONE;
import static com.cloudera.director.spi.v1.compute.ComputeInstanceTemplate.ComputeInstanceTemplateConfigurationPropertyToken.IMAGE;
import static com.cloudera.director.spi.v1.compute.ComputeInstanceTemplate.ComputeInstanceTemplateConfigurationPropertyToken.SSH_OPENSSH_PUBLIC_KEY;
import static com.cloudera.director.spi.v1.compute.ComputeInstanceTemplate.ComputeInstanceTemplateConfigurationPropertyToken.SSH_USERNAME;
import static com.cloudera.director.spi.v1.compute.ComputeInstanceTemplate.ComputeInstanceTemplateConfigurationPropertyToken.TYPE;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;

import com.cloudera.director.spi.v1.compute.ComputeInstance;
import com.cloudera.director.spi.v1.compute.ComputeInstanceTemplate;
import com.cloudera.director.spi.v1.compute.ComputeProvider;
import com.cloudera.director.spi.v1.model.ConfigurationProperty;
import com.cloudera.director.spi.v1.model.InstanceState;
import com.cloudera.director.spi.v1.model.InstanceStatus;
import com.cloudera.director.spi.v1.model.util.DefaultLocalizationContext;
import com.cloudera.director.spi.v1.model.util.SimpleConfiguration;
import com.cloudera.director.spi.v1.provider.CloudProvider;
import com.cloudera.director.spi.v1.provider.CloudProviderMetadata;
import com.cloudera.director.spi.v1.provider.Launcher;
import com.cloudera.director.spi.v1.provider.ResourceProviderMetadata;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class GoogleTest {

  // These four values need to be customized prior to running the test.
  private static final String GCP_PROJECT_ID = "shared-project";
  private static final String JSON_KEY_PATH = "";
  private static final String SSH_PUBLIC_KEY_PATH = "";
  private static final String SSH_USER_NAME = "";

  private static final DefaultLocalizationContext DEFAULT_LOCALIZATION_CONTEXT =
      new DefaultLocalizationContext(Locale.getDefault(), "");

  private static final int POLLING_INTERVAL_SECONDS = 5;

  private static String PROJECT_ID;
  private static String JSON_KEY;
  private static String SSH_PUBLIC_KEY;
  private static String USER_NAME;

  @BeforeClass
  public static void beforeClass() throws IOException {
    PROJECT_ID = GCP_PROJECT_ID;
    JSON_KEY = readFile(JSON_KEY_PATH, Charset.defaultCharset());
    SSH_PUBLIC_KEY = readFile(SSH_PUBLIC_KEY_PATH, Charset.defaultCharset());
    USER_NAME = SSH_USER_NAME;
  }

  private String localSSDInterfaceType;
  private String image;

  public GoogleTest(String localSSDInterfaceType, String image) {
    this.localSSDInterfaceType = localSSDInterfaceType;
    this.image = image;
  }

  @Parameterized.Parameters(name = "{index}: localSSDInterfaceType={0}, image={1}")
  public static Iterable<Object[]> data1() {
    return Arrays.asList(new Object[][]{
        {"SCSI", "centos"},
        {"SCSI", "rhel"}
    });
  }

  @Test
  public void testFullCycle() throws InterruptedException {

    // After a plugin is discovered and validated we get an instance of the Launcher.

    Launcher launcher = new GoogleLauncher();

    launcher.initialize(new File("."));

    // We register all the available providers based on metadata.

    assertEquals(1, launcher.getCloudProviderMetadata().size());
    CloudProviderMetadata metadata = launcher.getCloudProviderMetadata().get(0);

    assertEquals(GoogleCloudProvider.ID, metadata.getId());

    // During environment configuration we ask the user for the following properties.

    System.out.println("Configurations required for credentials:");
    for (ConfigurationProperty property :
        metadata.getCredentialsProviderMetadata().getCredentialsConfigurationProperties()) {
      System.out.println(property.getName(DEFAULT_LOCALIZATION_CONTEXT));
    }

    System.out.println("Other provider level configurations:");
    for (ConfigurationProperty property :
        metadata.getProviderConfigurationProperties()) {
      System.out.println(property.getName(DEFAULT_LOCALIZATION_CONTEXT));
    }

    // In order to create a cloud provider we need to configure credentials
    // (we expect them to be eagerly validated on cloud provider creation).

    Map<String, String> environmentConfig = new HashMap<String, String>();
    environmentConfig.put(PROJECTID.unwrap().getConfigKey(), PROJECT_ID);
    environmentConfig.put(JSONKEY.unwrap().getConfigKey(), JSON_KEY);

    CloudProvider provider = launcher.createCloudProvider(
        GoogleCloudProvider.ID, new SimpleConfiguration(environmentConfig),
        DEFAULT_LOCALIZATION_CONTEXT.getLocale());

    assertNotNull(provider);

    // Get the provider for compute instances.

    ResourceProviderMetadata computeMetadata = metadata.getResourceProviderMetadata("compute");

    System.out.println("Configurations required for 'compute' resource provider:");
    for (ConfigurationProperty property :
        computeMetadata.getProviderConfigurationProperties()) {
      System.out.println(property.getName(DEFAULT_LOCALIZATION_CONTEXT));
    }

    // TODO(duftler): The zone should really be selected by the user from a list of valid choices.
    // Do we want to enhance ConfigurationProperty to support querying provider for a set of values to choose from?
    Map<String, String> computeConfig = new HashMap<String, String>();
    computeConfig.put(ZONE.unwrap().getConfigKey(), "us-central1-a");

    ComputeProvider<ComputeInstance<ComputeInstanceTemplate>, ComputeInstanceTemplate> compute =
        (ComputeProvider<ComputeInstance<ComputeInstanceTemplate>, ComputeInstanceTemplate>)
            provider.createResourceProvider("compute", new SimpleConfiguration(computeConfig));

    // Prepare a resource template.

    System.out.println("Configurations required for template:");
    for (ConfigurationProperty property :
        computeMetadata.getResourceTemplateConfigurationProperties()) {
      System.out.println(property.getName(DEFAULT_LOCALIZATION_CONTEXT));
    }

    Map<String, String> templateConfig = new HashMap<String, String>();
    templateConfig.put(IMAGE.unwrap().getConfigKey(), image);
    templateConfig.put(TYPE.unwrap().getConfigKey(), "n1-standard-1");
    templateConfig.put(NETWORKNAME.unwrap().getConfigKey(), "default");
    templateConfig.put(LOCALSSDINTERFACETYPE.unwrap().getConfigKey(), localSSDInterfaceType);
    templateConfig.put(SSH_OPENSSH_PUBLIC_KEY.unwrap().getConfigKey(), SSH_PUBLIC_KEY);
    templateConfig.put(SSH_USERNAME.unwrap().getConfigKey(), USER_NAME);

    ComputeInstanceTemplate template =
        compute.createResourceTemplate("template-1", new SimpleConfiguration(templateConfig),
            new HashMap<String, String>());

    assertNotNull(template);

    // Use the template to create one resource.

    System.out.println("About to provision an instance...");

    List<String> instanceIds = Arrays.asList(UUID.randomUUID().toString());
    compute.allocate(template, instanceIds, 1);

    // Run a find by ID.

    System.out.println("About to lookup an instance...");

    Collection<ComputeInstance<ComputeInstanceTemplate>> instances = compute.find(template, instanceIds);

    // Loop until found.

    while (instances.size() == 0) {
      Thread.sleep(POLLING_INTERVAL_SECONDS * 1000);

      System.out.println("Polling...");

      instances = compute.find(template, instanceIds);
    }

    for (ComputeInstance foundInstance : instances) {
      System.out.println("Found instance '" + foundInstance.getId() + "' with private ip " +
          foundInstance.getPrivateIpAddress() + ".");
    }

    assertEquals(1, instances.size());

    ComputeInstance instance = instances.iterator().next();
    assertEquals(instanceIds.get(0), instance.getId());

    // Use the template to request creation of the same resource again.

    System.out.println("About to provision the same instance again...");

    compute.allocate(template, instanceIds, 1);
    instances = compute.find(template, instanceIds);
    assertEquals(1, instances.size());

    instance = instances.iterator().next();
    assertEquals(instanceIds.get(0), instance.getId());

    // Query the instance state until the instance status is RUNNING.

    pollInstanceState(compute, template, instanceIds, InstanceStatus.RUNNING);

    // Delete the resources.

    System.out.println("About to delete an instance...");

    compute.delete(template, instanceIds);

    // Query the instance state again until the instance status is UNKNOWN.

    pollInstanceState(compute, template, instanceIds, InstanceStatus.UNKNOWN);

    // Verify that the instance has been deleted.

    instances = compute.find(template, instanceIds);

    assertEquals(0, instances.size());
  }

  private static void pollInstanceState(
      ComputeProvider<ComputeInstance<ComputeInstanceTemplate>, ComputeInstanceTemplate> compute,
      ComputeInstanceTemplate template,
      List<String> instanceIds,
      InstanceStatus desiredStatus) throws InterruptedException {
    // Query the instance state until the instance status matches desiredStatus.

    System.out.println("About to query instance state until " + desiredStatus + "...");

    Map<String, InstanceState> idToInstanceStateMap = compute.getInstanceState(template, instanceIds);

    assertEquals(1, idToInstanceStateMap.size());

    for (Map.Entry<String, InstanceState> entry : idToInstanceStateMap.entrySet()) {
      System.out.printf("%s -> %s%n", entry.getKey(),
          entry.getValue().getInstanceStateDescription(DEFAULT_LOCALIZATION_CONTEXT));
    }

    while (idToInstanceStateMap.size() == 1
        && idToInstanceStateMap.values().toArray(new InstanceState[1])[0].getInstanceStatus() != desiredStatus) {
      Thread.sleep(POLLING_INTERVAL_SECONDS * 1000);

      System.out.println("Polling...");

      idToInstanceStateMap = compute.getInstanceState(template, instanceIds);

      for (Map.Entry<String, InstanceState> entry : idToInstanceStateMap.entrySet()) {
        System.out.printf("%s -> %s%n", entry.getKey(),
            entry.getValue().getInstanceStateDescription(DEFAULT_LOCALIZATION_CONTEXT));
      }
    }
  }

  private static String readFile(String path, Charset encoding) throws IOException {
    byte[] encoded = Files.readAllBytes(Paths.get(path));

    return new String(encoded, encoding);
  }
}
