package io.fabric8.launcher.service.openshift.spi;

import io.fabric8.launcher.service.openshift.api.CloudProject;
import io.fabric8.launcher.service.openshift.api.CloudService;

/**
 * Defines the service provider interface for implementations of {@link CloudService}
 * that we won't expose in the API but need for testing or other purposes
 *
 * @author <a href="mailto:alr@redhat.com">Andrew Lee Rubinger</a>
 */
public interface CloudServiceSpi extends CloudService {

    /**
     * Deletes the specified, required project
     *
     * @param project
     * @return If the operation resulted in a deletion
     * @throws IllegalArgumentException If the project is not specified
     */
    boolean deleteProject(CloudProject project) throws IllegalArgumentException;

    /**
     * Deletes the specified, required project
     *
     * @param projectName
     * @return If the operation resulted in a deletion
     * @throws IllegalArgumentException If the project is not specified
     */
    boolean deleteProject(String projectName) throws IllegalArgumentException;

    /**
     * Delete the specified build config
     *
     * @param namespace the build config namespace
     * @param name the build config name
     */
    void deleteBuildConfig(String namespace, String name);

    /**
     * Delete the specified config map
     *
     * @param namespace the config map namespace
     * @param configName the config map name
     */
    void deleteConfigMap(String namespace, String configName);
}
