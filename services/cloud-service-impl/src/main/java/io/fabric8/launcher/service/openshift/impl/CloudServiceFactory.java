package io.fabric8.launcher.service.openshift.impl;

import java.util.Optional;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import io.fabric8.launcher.base.identity.Identity;
import io.fabric8.launcher.base.identity.ImmutableUserPasswordIdentity;
import io.fabric8.launcher.base.identity.TokenIdentity;
import io.fabric8.launcher.service.openshift.api.ImmutableParameters;
import io.fabric8.launcher.service.openshift.api.OpenShiftClusterRegistry;
import io.fabric8.launcher.service.openshift.api.CloudService;

import static io.fabric8.launcher.service.openshift.api.OpenShiftEnvironment.LAUNCHER_MISSIONCONTROL_OPENSHIFT_PASSWORD;
import static io.fabric8.launcher.service.openshift.api.OpenShiftEnvironment.LAUNCHER_MISSIONCONTROL_OPENSHIFT_TOKEN;
import static io.fabric8.launcher.service.openshift.api.OpenShiftEnvironment.LAUNCHER_MISSIONCONTROL_OPENSHIFT_USERNAME;

/**
 * {@link io.fabric8.launcher.service.openshift.api.CloudServiceFactory} implementation
 *
 * @author <a href="mailto:xcoulon@redhat.com">Xavier Coulon</a>
 */
@ApplicationScoped
public class CloudServiceFactory implements io.fabric8.launcher.service.openshift.api.CloudServiceFactory {

    /**
     * Needed for proxying
     */
    @Deprecated
    CloudServiceFactory() {
        this.clusterRegistry = null;
    }

    @Inject
    public CloudServiceFactory(OpenShiftClusterRegistry clusterRegistry) {
        this.clusterRegistry = clusterRegistry;
    }

    private final OpenShiftClusterRegistry clusterRegistry;

    @Override
    public CloudService create() {
        Parameters parameters = ImmutableParameters.builder()
                .cluster(clusterRegistry.getDefault())
                .identity(getDefaultIdentity().
                        orElseThrow(() -> new IllegalStateException("OpenShift Credentials not found. Are the required environment variables set?")))
                .build();
        return create(parameters);
    }

    @Override
    public BaseCloudService create(Parameters parameters) {
        return new KubernetesService(parameters);
    }

    @Override
    public Optional<Identity> getDefaultIdentity() {
        if (!isDefaultIdentitySet()) {
            return Optional.empty();
        }
        // Read from the ENV variables
        String token = LAUNCHER_MISSIONCONTROL_OPENSHIFT_TOKEN.value();
        if (token != null) {
            return Optional.of(TokenIdentity.of(token));
        } else {
            String user = LAUNCHER_MISSIONCONTROL_OPENSHIFT_USERNAME.valueRequired();
            String password = LAUNCHER_MISSIONCONTROL_OPENSHIFT_PASSWORD.valueRequired();
            return Optional.of(ImmutableUserPasswordIdentity.of(user, password));
        }
    }

    private boolean isDefaultIdentitySet() {
        String user = LAUNCHER_MISSIONCONTROL_OPENSHIFT_USERNAME.value();
        String password = LAUNCHER_MISSIONCONTROL_OPENSHIFT_PASSWORD.value();
        String token = LAUNCHER_MISSIONCONTROL_OPENSHIFT_TOKEN.value();

        return ((user != null && password != null) || token != null);
    }
}