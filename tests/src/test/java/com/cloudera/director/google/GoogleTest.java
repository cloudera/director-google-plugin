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

import com.cloudera.director.spi.v1.compute.ComputeInstance;
import com.cloudera.director.spi.v1.compute.ComputeInstanceTemplate;
import com.cloudera.director.spi.v1.compute.ComputeProvider;
import com.cloudera.director.spi.v1.model.ConfigurationProperty;
import com.cloudera.director.spi.v1.model.InstanceState;
import com.cloudera.director.spi.v1.model.InstanceStatus;
import com.cloudera.director.spi.v1.model.util.SimpleConfiguration;
import com.cloudera.director.spi.v1.provider.CloudProvider;
import com.cloudera.director.spi.v1.provider.CloudProviderMetadata;
import com.cloudera.director.spi.v1.provider.Launcher;
import com.cloudera.director.spi.v1.provider.ResourceProviderMetadata;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import static com.cloudera.director.google.GoogleCredentialsProviderConfigurationProperty.JSONKEY;
import static com.cloudera.director.google.GoogleCredentialsProviderConfigurationProperty.PROJECTID;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationProperty.BOOTDISKSIZEGB;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationProperty.LOCALSSDCOUNT;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationProperty.LOCALSSDINTERFACETYPE;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationProperty.NETWORKNAME;
import static com.cloudera.director.google.compute.GoogleComputeInstanceTemplateConfigurationProperty.ZONE;
import static com.cloudera.director.spi.v1.compute.ComputeInstanceTemplate.ComputeInstanceTemplateConfigurationProperty.IMAGE;
import static com.cloudera.director.spi.v1.compute.ComputeInstanceTemplate.ComputeInstanceTemplateConfigurationProperty.TYPE;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;

@RunWith(Parameterized.class)
public class GoogleTest {

  // These two values need to be customized prior to running the test.
  private static final String JSON_KEY_PATH = "";
  private static final String GCP_PROJECT_ID = "shared-project";

  private static final int POLLING_INTERVAL_SECONDS = 5;

  private static String PROJECT_ID;
  private static String JSON_KEY;

  @BeforeClass
  public static void beforeClass() throws IOException {
    PROJECT_ID = GCP_PROJECT_ID;
    JSON_KEY = readFile(JSON_KEY_PATH, Charset.defaultCharset());
  }

  private String localSSDInterfaceType;
  private String image;

  public GoogleTest(String localSSDInterfaceType, String image) {
    this.localSSDInterfaceType = localSSDInterfaceType;
    this.image = image;
  }

  @Parameterized.Parameters(name = "{index}: localSSDInterfaceType={0}, image={1}")
  public static Iterable<Object[]> data1() {
    return Arrays.asList(new Object[][] {
            { "SCSI", "ubuntu" },
            { "NVME", "nvmeDebian" }
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
      System.out.println(property);
    }

    System.out.println("Other provider level configurations:");
    for (ConfigurationProperty property :
        metadata.getProviderConfigurationProperties()) {
      System.out.println(property);
    }

    // In order to create a cloud provider we need to configure credentials
    // (we expect them to be eagerly validated on cloud provider creation).

    Map<String, String> environmentConfig = new HashMap<String, String>();
    environmentConfig.put(PROJECTID.getConfigKey(), PROJECT_ID);
    environmentConfig.put(JSONKEY.getConfigKey(), JSON_KEY);

    CloudProvider provider = launcher.createCloudProvider(
        GoogleCloudProvider.ID, new SimpleConfiguration(environmentConfig));

    assertNotNull(provider);

    // Get the provider for compute instances.

    ResourceProviderMetadata computeMetadata = metadata.getResourceProviderMetadata("compute");

    System.out.println("Configurations required for 'compute' resource provider:");
    for (ConfigurationProperty property :
        computeMetadata.getProviderConfigurationProperties()) {
      System.out.println(property);
    }

    // TODO(duftler): The zone should really be selected by the user from a list of valid choices.
    // Do we want to enhance ConfigurationProperty to support querying provider for a set of values to choose from?
    Map<String, String> computeConfig = new HashMap<String, String>();
    computeConfig.put(ZONE.getConfigKey(), "us-central1-a");

    ComputeProvider<ComputeInstance<ComputeInstanceTemplate>, ComputeInstanceTemplate> compute =
        (ComputeProvider<ComputeInstance<ComputeInstanceTemplate>, ComputeInstanceTemplate>)
            provider.createResourceProvider("compute", new SimpleConfiguration(computeConfig));

    // Prepare a resource template.

    System.out.println("Configurations required for template:");
    for (ConfigurationProperty property :
        computeMetadata.getResourceTemplateConfigurationProperties()) {
      System.out.println(property);
    }

    Map<String, String> templateConfig = new HashMap<String, String>();
    templateConfig.put(IMAGE.getConfigKey(), image);
    templateConfig.put(TYPE.getConfigKey(), "n1-standard-1");
    templateConfig.put(NETWORKNAME.getConfigKey(), "default");
    templateConfig.put(BOOTDISKSIZEGB.getConfigKey(), "30");
    templateConfig.put(LOCALSSDCOUNT.getConfigKey(), "2");
    templateConfig.put(LOCALSSDINTERFACETYPE.getConfigKey(), localSSDInterfaceType);

    ComputeInstanceTemplate template = (ComputeInstanceTemplate)
        compute.createResourceTemplate("template-1", new SimpleConfiguration(templateConfig),
            new HashMap<String, String>());

    assertNotNull(template);

    // Use the template to create one resource.

    System.out.println("About to provision an instance...");

    List<String> instanceIds = Arrays.asList(UUID.randomUUID().toString());
    Collection<ComputeInstance<ComputeInstanceTemplate>> instances =
        compute.allocate(template, instanceIds, 1);
    assertEquals(1, instances.size());

    ComputeInstance instance = instances.iterator().next();
    assertEquals(instanceIds.get(0), instance.getId());

    // Run a find by ID.

    System.out.println("About to lookup an instance...");

    Collection<ComputeInstance<ComputeInstanceTemplate>> found = compute.find(template, instanceIds);

    // Loop until found.

    while (found.size() == 0) {
      Thread.sleep(POLLING_INTERVAL_SECONDS * 1000);

      System.out.println("Polling...");

      found = compute.find(template, instanceIds);
    }

    for (ComputeInstance foundInstance : found) {
      System.out.println("Found instance '" + foundInstance.getId() + "' with private ip " +
                         foundInstance.getPrivateIpAddress() + ".");
    }

    assertEquals(1, found.size());

    // Use the template to request creation of the same resource again.

    System.out.println("About to provision the same instance again...");

    instances = compute.allocate(template, instanceIds, 1);
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

    found = compute.find(template, instanceIds);

    assertEquals(0, found.size());
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
      System.out.println(entry.getKey() + " -> " + entry.getValue().getInstanceStateDescription(Locale.getDefault()));
    }

    while (idToInstanceStateMap.size() == 1
            && idToInstanceStateMap.values().toArray(new InstanceState[1])[0].getInstanceStatus() != desiredStatus) {
      Thread.sleep(POLLING_INTERVAL_SECONDS * 1000);

      System.out.println("Polling...");

      idToInstanceStateMap = compute.getInstanceState(template, instanceIds);

      for (Map.Entry<String, InstanceState> entry : idToInstanceStateMap.entrySet()) {
        System.out.println(entry.getKey() + " -> " + entry.getValue().getInstanceStateDescription(Locale.getDefault()));
      }
    }
  }

  private static String readFile(String path, Charset encoding) throws IOException {
    byte[] encoded = Files.readAllBytes(Paths.get(path));

    return new String(encoded, encoding);
  }
}
