package io.fabric8.launcher.core.api;

import io.fabric8.launcher.service.git.api.GitRepository;
import io.fabric8.launcher.service.openshift.api.CloudProject;
import org.immutables.value.Value;

import javax.annotation.Nullable;

/**
 * Value object containing the result of a {@link MissionControl#launch(Projectile)}
 * call.  Implementations should be immutable and therefore thread-safe.
 *
 * @author <a href="mailto:alr@redhat.com">Andrew Lee Rubinger</a>
 */
@Value.Immutable
public interface Boom {
    /**
     * @return the repository we've created for the user
     */
    @Nullable
    GitRepository getCreatedRepository();

    /**
     * @return the OpenShift project we've created for the user
     */
    CloudProject getCreatedProject();
}
