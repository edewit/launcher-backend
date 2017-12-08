/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.fabric8.forge.generator.kubernetes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;

import com.google.common.base.Objects;
import io.fabric8.forge.generator.EnvironmentVariables;
import io.fabric8.kubernetes.api.Controller;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.DoneableSecret;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.spaces.Space;
import io.fabric8.kubernetes.api.spaces.Spaces;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.launcher.service.openshift.api.OpenShiftService;
import io.fabric8.openshift.api.model.Build;
import io.fabric8.openshift.api.model.BuildRequest;
import io.fabric8.openshift.api.model.BuildRequestBuilder;
import io.fabric8.openshift.client.OpenShiftClient;
import io.fabric8.utils.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 */
public class KubernetesClientHelper {
    private static final int RETRY_TRIGGER_BUILD_COUNT = 5;
    private static final transient Logger LOG = LoggerFactory.getLogger(KubernetesClientHelper.class);

    private final KubernetesClient kubernetesClient;

    private final OpenShiftService openShiftService;

    protected KubernetesClientHelper(KubernetesClient kubernetesClient, OpenShiftService openShiftService) {
        this.kubernetesClient = kubernetesClient;
        this.openShiftService = openShiftService;
    }

    /**
     * Returns a unique key specific to the current user request
     */
    public String getUserCacheKey() {
        String answer = kubernetesClient.getConfiguration().getOauthToken();
        if (Strings.isNotBlank(answer)) {
            return answer;
        }
        LOG.warn("Could not find the OAuthToken to use as a user cache key!");
        return "TODO";
    }

    /**
     * Returns the namespace used to discover services like gogs and gitlab when on premise
     */
    public String getDiscoveryNamespace(String createInNamespace) {
        if (Strings.isNotBlank(createInNamespace)) {
            return createInNamespace;
        }
        String namespace = System.getenv(EnvironmentVariables.NAMESPACE);
        if (Strings.isNotBlank(namespace)) {
            return namespace;
        }
        namespace = kubernetesClient.getNamespace();
        if (Strings.isNotBlank(namespace)) {
            return namespace;
        }
        return KubernetesHelper.defaultNamespace();
    }

    public List<SpaceDTO> loadSpaces(String namespace) {
        List<SpaceDTO> answer = new ArrayList<>();
        if (namespace != null) {
            try {
                Spaces spacesValue = Spaces.load(kubernetesClient, namespace);
                if (spacesValue != null) {
                    SortedSet<Space> spaces = spacesValue.getSpaceSet();
                    for (Space space : spaces) {
                        answer.add(new SpaceDTO(space.getName(), space.getName()));
                    }
                }
            } catch (Exception e) {
                LOG.warn("Failed to load spaces: " + e, e);
            }
        }
        return answer;
    }

    public void ensureCDGihubSecretExists(String namespace, String gitOwnerName, String gitToken) {
        String secretName = "cd-github";
        String username = Base64Helper.base64encode(gitOwnerName);
        String password = Base64Helper.base64encode(gitToken);
        Secret secret = null;
        Resource<Secret, DoneableSecret> secretResource = kubernetesClient.secrets().inNamespace(namespace).withName(secretName);
        try {
            secret = secretResource.get();
        } catch (Exception e) {
            LOG.warn("Failed to lookup secret " + namespace + "/" + secretName + " due to: " + e, e);
        }
        if (secret == null ||
                !Objects.equal(username, getSecretData(secret, "username")) ||
                !Objects.equal(password, getSecretData(secret, "password"))) {

            try {
                LOG.info("Upserting Secret " + namespace + "/" + secretName);
                secretResource.createOrReplace(new SecretBuilder().
                        withNewMetadata().withName(secretName).addToLabels("jenkins", "sync").addToLabels("creator", "fabric8").endMetadata().
                        addToData("username", username).
                        addToData("password", password).
                        build());
            } catch (Exception e) {
                LOG.warn("Failed to upsert Secret " + namespace + "/" + secretName + " due to: " + e, e);
            }
        }
    }

    protected void triggerBuild(String namespace, String projectName) {
        for (int i = 0; i < RETRY_TRIGGER_BUILD_COUNT; i++) {
            if (i > 0) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    // ignore
                }
            }
            String triggeredBuildName;
            BuildRequest request = new BuildRequestBuilder().
                    withNewMetadata().withName(projectName).endMetadata().
                    addNewTriggeredBy().withMessage("Forge triggered").endTriggeredBy().
                    build();
            try {
                Build build = getOpenShiftClientOrNull().buildConfigs().inNamespace(namespace).withName(projectName).instantiate(request);
                if (build != null) {
                    triggeredBuildName = KubernetesHelper.getName(build);
                    LOG.info("Triggered build " + triggeredBuildName);
                    return;
                } else {
                    LOG.error("Failed to trigger build for " + namespace + "/" + projectName + " du to: no Build returned");
                }
            } catch (Exception e) {
                LOG.error("Failed to trigger build for " + namespace + "/" + projectName + " due to: " + e, e);
            }
        }
    }

    public boolean hasBuildConfig(String namespace, String projectName) {
        return openShiftService.hasBuildConfig(namespace, projectName);
    }

    private static String getSecretData(Secret secret, String key) {
        if (secret != null) {
            Map<String, String> data = secret.getData();
            if (data != null) {
                return data.get(key);
            }
        }
        return null;
    }

    public OpenShiftClient getOpenShiftClientOrNull() {
        return new Controller(kubernetesClient).getOpenShiftClientOrNull();
    }

    public KubernetesClient getKubernetesClient() {
        return kubernetesClient;
    }
}
