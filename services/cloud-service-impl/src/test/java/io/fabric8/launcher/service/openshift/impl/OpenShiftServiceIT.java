package io.fabric8.launcher.service.openshift.impl;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.fabric8.launcher.base.test.EnvironmentVariableController;
import io.fabric8.launcher.service.openshift.api.CloudProject;
import io.fabric8.launcher.service.openshift.api.CloudService;
import io.fabric8.launcher.service.openshift.api.DuplicateProjectException;
import io.fabric8.launcher.service.openshift.api.OpenShiftEnvironment;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author <a href="mailto:alr@redhat.com">Andrew Lee Rubinger</a>
 * @author <a href="mailto:rmartine@redhat.com">Ricardo Martinelli de
 * Oliveira</a>
 * @author <a href="mailto:xcoulon@redhat.com">Xavier Coulon</a>
 */
public class OpenShiftServiceIT {

    @Rule
    public DeleteOpenShiftProjectRule deleteOpenShiftProjectRule = new DeleteOpenShiftProjectRule(this);

    private static final Logger log = Logger.getLogger(OpenShiftServiceIT.class.getName());

    private static final String PREFIX_NAME_PROJECT = "test-project-";

    private io.fabric8.launcher.service.openshift.api.CloudServiceFactory cloudServiceFactory;

    private CloudService cloudService;

    @Before
    public void setUp() {
        this.cloudServiceFactory = new io.fabric8.launcher.service.openshift.impl.CloudServiceFactory(new OpenShiftClusterRegistryImpl());
        this.cloudService = cloudServiceFactory.create();
    }


    CloudService getCloudService() {
        return cloudService;
    }


    @Test
    public void createProjectOnly() {
        // given
        final String projectName = getUniqueProjectName();
        // when (just) creating the project
        final CloudProject project = triggerCreateProject(projectName);
        // then
        final String actualName = project.getName();
        assertThat(actualName).isEqualTo(projectName);
    }

    @Test
    public void createProjectAndApplyTemplate() throws IOException {
        // given
        final String projectName = getUniqueProjectName();

        // when creating the project and then applying the template
        final CloudProject project = triggerCreateProject(projectName);
        log.log(Level.INFO, "Created project: \'" + projectName + "\'");

        try (InputStream template = getClass().getResourceAsStream("/foo-pipeline-template.yaml")) {
            Map<String, String> parameters = new HashMap<>();
            parameters.put("GIT_URL", "https://foo.com/blah");
            parameters.put("GIT_REF", "kontinu8");
            cloudService.configureProject(project, template, parameters);
        }

        // then
        final String actualName = project.getName();
        assertThat(actualName).isEqualTo(projectName);
        assertThat(project.getResources()).isNotNull().hasSize(1);
        assertThat(project.getResources().get(0).getKind()).isEqualTo("BuildConfig");
        assertThat(cloudService.getWebhookUrls(project)).hasSize(1);
        assertThat(cloudService.getWebhookUrls(project).get(0)).isEqualTo(
                new URL(OpenShiftEnvironment.LAUNCHER_MISSIONCONTROL_OPENSHIFT_CONSOLE_URL.value()
                                + "/oapi/v1/namespaces/" + project.getName() + "/buildconfigs/helloworld-pipeline/webhooks/kontinu8/github"));
    }

    @Test
    public void duplicateProjectNameShouldFail() {
        // given
        final CloudProject project = triggerCreateProject(getUniqueProjectName());
        // when
        final String name = project.getName();
        assertThatThrownBy(() -> cloudService.createProject(name)).isInstanceOf(DuplicateProjectException.class);

        // then using same name should fail with DPE here
    }

    @Test
    public void projectExists() {
        // given
        final CloudProject project = triggerCreateProject(getUniqueProjectName());
        // when
        final String name = project.getName();

        // then
        assertThat(cloudService.projectExists(name)).isTrue();
    }

    @Test(expected = IllegalArgumentException.class)
    public void projectExistsShouldFailIfNull() {
        cloudService.projectExists(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void projectExistsShouldFailIfEmpty() {
        cloudService.projectExists("");
    }

    @Test
    public void findProject() {
        // given
        String projectName = getUniqueProjectName();
        final CloudProject project = triggerCreateProject(projectName);
        // when
        final String name = project.getName();
        assertThat(cloudService.findProject(name)).isPresent();
    }

    @Test
    public void findProjectWithNonExistingName() {
        assertThat(cloudService.findProject("foo-project")).isNotPresent();
    }

    @Test
    public void getServiceURL() {
        // given
        CloudProject openShiftProject = triggerCreateProject(getUniqueProjectName());
        InputStream serviceYamlFile = getClass().getClassLoader().getResourceAsStream("foo-service-template.yaml");
        cloudService.configureProject(openShiftProject, serviceYamlFile, Collections.emptyMap());
        // when
        URL serviceURL = cloudService.getServiceURL("foo", openShiftProject);
        //then
        assertThat(serviceURL).isNotNull();
    }

    @Test
    public void should_return_routes() {
        // given
        CloudProject openShiftProject = triggerCreateProject(getUniqueProjectName());
        InputStream serviceYamlFile = getClass().getClassLoader().getResourceAsStream("foo-service-template.yaml");
        cloudService.configureProject(openShiftProject, serviceYamlFile, Collections.emptyMap());
        // when
        Map<String, URL> routes = cloudService.getRoutes(openShiftProject);
        //then
        assertThat(routes).isNotEmpty().containsOnlyKeys("foo");
    }

    @Test
    public void getServiceURLWithInexistentService() {
        CloudProject openShiftProject = triggerCreateProject(getUniqueProjectName());
        assertThatThrownBy(() -> cloudService.getServiceURL("foo", openShiftProject)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void isDefaultIdentitySetWithToken() {
        String originalUserValue = OpenShiftEnvironment.LAUNCHER_MISSIONCONTROL_OPENSHIFT_USERNAME.value();
        String originalPasswordValue = OpenShiftEnvironment.LAUNCHER_MISSIONCONTROL_OPENSHIFT_PASSWORD.value();
        String originalTokenValue = OpenShiftEnvironment.LAUNCHER_MISSIONCONTROL_OPENSHIFT_TOKEN.value();

        try {
            EnvironmentVariableController.setEnv(OpenShiftEnvironment.LAUNCHER_MISSIONCONTROL_OPENSHIFT_TOKEN.propertyKey(), "token");
            assertThat(cloudServiceFactory.getDefaultIdentity()).isPresent();
            EnvironmentVariableController.removeEnv(OpenShiftEnvironment.LAUNCHER_MISSIONCONTROL_OPENSHIFT_USERNAME.propertyKey());
            EnvironmentVariableController.removeEnv(OpenShiftEnvironment.LAUNCHER_MISSIONCONTROL_OPENSHIFT_PASSWORD.propertyKey());
            EnvironmentVariableController.removeEnv(OpenShiftEnvironment.LAUNCHER_MISSIONCONTROL_OPENSHIFT_TOKEN.propertyKey());
            assertThat(cloudServiceFactory.getDefaultIdentity()).isNotPresent();
        } finally {
            EnvironmentVariableController.setEnv(OpenShiftEnvironment.LAUNCHER_MISSIONCONTROL_OPENSHIFT_USERNAME.propertyKey(), originalUserValue);
            EnvironmentVariableController.setEnv(OpenShiftEnvironment.LAUNCHER_MISSIONCONTROL_OPENSHIFT_PASSWORD.propertyKey(), originalPasswordValue);
            EnvironmentVariableController.setEnv(OpenShiftEnvironment.LAUNCHER_MISSIONCONTROL_OPENSHIFT_TOKEN.propertyKey(), originalTokenValue);
        }
    }

    @Test
    public void openShiftClientIsNotNull() {
        assertThat(cloudService.getOpenShiftClient()).isNotNull();
    }

    private String getUniqueProjectName() {
        return PREFIX_NAME_PROJECT + System.currentTimeMillis();
    }

    private CloudProject triggerCreateProject(final String projectName) {
        final CloudProject project = cloudService.createProject(projectName);
        log.log(Level.INFO, "Created project: \'" + projectName + "\'");
        deleteOpenShiftProjectRule.add(project);
        return project;
    }
}
