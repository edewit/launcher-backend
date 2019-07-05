package io.fabric8.launcher.service.openshift.impl;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import io.fabric8.launcher.service.openshift.api.DuplicateProjectException;
import io.fabric8.launcher.service.openshift.api.OpenShiftProject;
import io.fabric8.launcher.service.openshift.api.OpenShiftServiceFactory;
import io.fabric8.launcher.service.openshift.api.OpenShiftUser;
import io.fabric8.openshift.api.model.BuildConfig;
import io.fabric8.openshift.client.OpenShiftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KubernetesServiceImpl extends BaseKubernetesService {
    private static final Logger LOG = LoggerFactory.getLogger(KubernetesServiceImpl.class);

    private static final String DEFAULT_NAMESPACE = "default";

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
                client.customResource(taskCRD).create(DEFAULT_NAMESPACE, resource);
                if (!getRegistry().isPresent()) {
                    client.load(getClass().getResourceAsStream("/registry.yaml")).createOrReplace();
                }
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
            LOG.debug("Deleted project: {}", projectName);
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
        buildConfig(project, sourceRepositoryUri);
        waitUntilBuildIsDone(project.getName());
        deploy(project);
    }

    private void buildConfig(OpenShiftProject project, URI sourceRepositoryUri) {
        final SimpleTemplate simpleTemplate = new SimpleTemplate(
                "${GIT_URL}", sourceRepositoryUri.toString(), "${PROJECT_NAME}", project.getName());
        try (InputStream is = simpleTemplate.parseTemplate("s2i-taskrun.yaml")) {
            getTaskCRD();
            CustomResourceDefinitionContext taskRun = new CustomResourceDefinitionContext.Builder()
                    .withName("taskruns.tekton.dev")
                    .withGroup("tekton.dev")
                    .withVersion("v1alpha1")
                    .withPlural("taskruns")
                    .withScope("Namespaced")
                    .build();

            client.customResource(taskRun).createOrReplace(DEFAULT_NAMESPACE, is);
        } catch (IOException e) {
            throw new RuntimeException("could not find template taskrun definition", e);
        }
    }

    private void deploy(OpenShiftProject project) {
        String clusterRegistryIP = getClusterRegistryIP().orElse("");
        final SimpleTemplate simpleTemplate = new SimpleTemplate(
                "${CLUSTER_IP}", clusterRegistryIP, "${PROJECT_NAME}", project.getName());

        try (InputStream is = simpleTemplate.parseTemplate("deployment.yaml")) {
            client.load(is).inNamespace(project.getName()).createOrReplace();
        } catch (IOException e) {
            throw new RuntimeException("could not deploy build image", e);
        }
    }

    private Optional<String> getClusterRegistryIP() {
        Optional<Object> clusterIP = getRegistry();
        return clusterIP.map(Object::toString);
    }

    private Optional<Object> getRegistry() {
        Predicate<Service> predicate = service -> "registry".equals(service.getMetadata().getName());
        Optional<Service> registryService = client.services().list().getItems().stream().filter(predicate).findFirst();
        return registryService.map(service -> service.getSpec().getClusterIP());
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

    private void waitUntilBuildIsDone(final String projectName) {
        final String label = String.format("tekton.dev/taskRun=s2i-%s-taskrun", projectName);
        final CountDownLatch countDownLatch = new CountDownLatch(1);

        final Runnable readyPodsPoller = () -> {
            try {
                List<Pod> list = client.pods().inNamespace(DEFAULT_NAMESPACE).withLabel(label).list().getItems();
                if (!list.isEmpty()) {
                    Pod pod = list.get(0);
                    if (!pod.getStatus().getContainerStatuses().isEmpty()) {
                        Predicate<ContainerStatus> predicate = containerStatus ->
                                containerStatus.getState().getTerminated() == null
                                        || !"Completed".equals(containerStatus.getState().getTerminated().getReason());
                        long count = pod.getStatus().getContainerStatuses().stream().filter(predicate).count();
                        if (count == 0) {
                            countDownLatch.countDown();
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        };

        wait(countDownLatch, readyPodsPoller);
    }

    private void wait(CountDownLatch countDownLatch, Runnable readyPodsPoller) {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        ScheduledFuture poller = executor.scheduleWithFixedDelay(readyPodsPoller, 0, 20, TimeUnit.SECONDS);
        ScheduledFuture logger = executor.scheduleWithFixedDelay(() -> LOG.debug("waiting..."), 0, 40, TimeUnit.SECONDS);
        try {
            countDownLatch.await(15, TimeUnit.MINUTES);
            executor.shutdown();
        } catch (InterruptedException e) {
            poller.cancel(true);
            logger.cancel(true);
            executor.shutdown();
            LOG.warn("Giving up after waiting for 15 minutes");
        }
    }
}
