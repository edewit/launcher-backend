package io.fabric8.launcher.service.openshift.impl;

import io.fabric8.launcher.base.identity.ImmutableUserPasswordIdentity;
import io.fabric8.launcher.base.identity.UserPasswordIdentity;
import io.fabric8.launcher.service.openshift.api.*;
import io.fabric8.openshift.api.model.Project;
import jdk.nashorn.internal.ir.annotations.Immutable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class KubernetesServiceImplTest {

    @Mock
    OpenShiftCluster cluster;

    private KubernetesServiceImpl kubernetesService;

    @Before
    public void setUp() {
        when(cluster.getApiUrl()).thenReturn("https://192.168.99.108:8443");
        UserPasswordIdentity identity = ImmutableUserPasswordIdentity.builder().username("developer").password("developer").build();
        OpenShiftServiceFactory.Parameters parameters = ImmutableParameters.builder()
                .cluster(cluster)
                .identity(identity)
                .build();
        kubernetesService = new KubernetesServiceImpl(parameters);
    }

    @Test
    public void shouldCreateProject() {
        // given
        String projectName = "edewit";

        // when
        kubernetesService.createProject(projectName);

        // then
        Optional<OpenShiftProject> project = kubernetesService.findProject(projectName);
        assertTrue(project.isPresent());
        assertEquals(projectName, project.get().getName());

        // finally
        kubernetesService.deleteProject(projectName);
    }

    @Test
    public void shouldInstallS2iTask() throws URISyntaxException {
        // given
        OpenShiftProject project = kubernetesService.createProject("temp");

        // when
        kubernetesService.configureProject(project, "GITHUB", new URI("https://github.com/edewit/nodejs-ex"));

        // finally
//        kubernetesService.deleteProject(project.getName());
    }
}