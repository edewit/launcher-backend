package io.fabric8.launcher.service.openshift.impl;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import io.fabric8.launcher.service.openshift.api.DuplicateProjectException;
import io.fabric8.launcher.service.openshift.api.OpenShiftProject;
import io.fabric8.launcher.service.openshift.api.OpenShiftServiceFactory;
import io.fabric8.launcher.service.openshift.api.OpenShiftUser;
import io.fabric8.openshift.api.model.BuildConfig;
import io.fabric8.openshift.client.OpenShiftClient;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.logging.Logger;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.logging.Level.FINEST;

public class KubernetesServiceImpl extends BaseKubernetesService {
    private static final Logger log = Logger.getLogger(KubernetesServiceImpl.class.getName());

    private CustomResourceDefinitionContext taskCRD;

    KubernetesServiceImpl(final OpenShiftServiceFactory.Parameters parameters) {
        super(parameters);
    }

    private CustomResourceDefinitionContext getTaskCRD() {
        if (taskCRD == null) {
            taskCRD = new CustomResourceDefinitionContext.Builder()
                    .withName("tasks.tekton.dev")
                    .withGroup("tekton.dev")
                    .withVersion("v1alpha1")
                    .withPlural("tasks")
                    .withScope("Namespaced")
                    .build();
            InputStream resource = getClass().getResourceAsStream("/s2i.yaml");
            try {
                client.customResource(taskCRD).create("default", resource);
            } catch (IOException e) {
                throw new RuntimeException("could not create s2i task crd", e);
            }
        }

        return taskCRD;
    }

    @Override
    public boolean deleteProject(String projectName) throws IllegalArgumentException {
        if (projectName == null || projectName.isEmpty()) {
            throw new IllegalArgumentException("project name must be specified");
        }
        final boolean deleted = client.namespaces().withName(projectName).delete();
        if (deleted) {
            log.log(FINEST, "Deleted project: {0}", projectName);
        }
        return deleted;
    }

    @Override
    public void deleteBuildConfig(String namespace, String name) {
        client.customResource(getTaskCRD()).delete(namespace, name);
    }

    @Override
    public OpenShiftProject createProject(String name) throws DuplicateProjectException, IllegalArgumentException {
        Namespace namespace = new NamespaceBuilder().withNewMetadata().withName(name).endMetadata().build();
        client.namespaces().create(namespace);
        return new OpenShiftProjectImpl(name, null);
    }

    @Override
    public Optional<OpenShiftProject> findProject(String name) throws IllegalArgumentException {
        Optional<Namespace> namespace = client.namespaces().list().getItems().stream().filter(n -> name.equals(n.getMetadata().getName())).findFirst();
        return namespace.map(namespace1 -> new OpenShiftProjectImpl(namespace1.getMetadata().getName(), null));
    }

    @Override
    public void configureProject(OpenShiftProject project, String sourceRepositoryProvider, URI sourceRepositoryUri) {
        String content = new Scanner(getClass().getResourceAsStream("/s2i-taskrun.yaml"), UTF_8.name()).useDelimiter("\\A").next();
        content = content.replaceAll("\\$\\{GIT_URL}", sourceRepositoryUri.toString());
        content = content.replaceAll("\\$\\{PROJECT_NAME}", project.getName());
        try (InputStream is = new ByteArrayInputStream(content.getBytes(UTF_8))) {
            getTaskCRD();
            CustomResourceDefinitionContext taskRun = new CustomResourceDefinitionContext.Builder()
                    .withName("taskruns.tekton.dev")
                    .withGroup("tekton.dev")
                    .withVersion("v1alpha1")
                    .withPlural("taskruns")
                    .withScope("Namespaced")
                    .build();

            client.customResource(taskRun).createOrReplace("default", is);
        } catch (IOException e) {
            throw new RuntimeException("could not find template taskrun definition", e);
        }
    }

    @Override
    public void configureProject(OpenShiftProject project, InputStream templateStream, String sourceRepositoryProvider, URI sourceRepositoryUri, String sourceRepositoryContextDir) {
    }

    @Override
    public void configureProject(OpenShiftProject project, InputStream templateStream, Map<String, String> parameters) {

    }

    @Override
    public List<URL> getWebhookUrls(OpenShiftProject project) throws IllegalArgumentException {
        return null;
    }

    @Override
    public boolean projectExists(String name) throws IllegalArgumentException {
        return false;
    }

    @Override
    public Map<String, URL> getRoutes(OpenShiftProject project) {
        return null;
    }

    @Override
    public URL getServiceURL(String serviceName, OpenShiftProject project) throws IllegalArgumentException {
        return null;
    }

    @Override
    public OpenShiftClient getOpenShiftClient() {
        return null;
    }

    @Override
    public OpenShiftUser getLoggedUser() {
        return null;
    }

    @Override
    public Optional<ConfigMap> getConfigMap(String configName, String namespace) {
        return Optional.empty();
    }

    @Override
    public ConfigMap createNewConfigMap(String ownerName) {
        return null;
    }

    @Override
    public void createConfigMap(String configName, String namespace, ConfigMap configMap) {

    }

    @Override
    public void updateConfigMap(String configName, String namespace, Map<String, String> data) {

    }

    @Override
    public void triggerBuild(String projectName, String namespace) {

    }

    @Override
    public void applyBuildConfig(BuildConfig buildConfig, String namespace) {

    }
}
