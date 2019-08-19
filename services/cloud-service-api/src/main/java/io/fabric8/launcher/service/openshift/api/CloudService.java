package io.fabric8.launcher.service.openshift.api;

import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.openshift.api.model.BuildConfig;
import io.fabric8.openshift.client.OpenShiftClient;

/**
 * Defines the operations we support with the OpenShift backend
 *
 * @author <a href="mailto:alr@redhat.com">Andrew Lee Rubinger</a>
 */
public interface CloudService {

    String PROJECT_NAME_REGEX = "^[a-zA-Z](?!.*--)(?!.*__)[a-zA-Z0-9-_]{2,38}[a-zA-Z0-9]$";
    String PROJECT_NAME_VALIDATION_MESSAGE = "projectName should consist of only alphanumeric characters, '-' and '_'. " +
            "It should start with alphabetic and end with alphanumeric characters.";

    /**
     * Creates a project with the specified, required name.
     *
     * @param name the name of the project to create
     * @return the created {@link CloudProject}
     * @throws DuplicateProjectException
     * @throws IllegalArgumentException  If the name is not specified
     */
    CloudProject createProject(String name)
            throws DuplicateProjectException, IllegalArgumentException;


    /**
     * Finds an {@link CloudProject} with the specified, required name
     *
     * @param name the name of the project to find
     * @return an {@link Optional} with an existing {@link CloudProject}
     * @throws IllegalArgumentException if the name is not specified
     */
    Optional<CloudProject> findProject(String name) throws IllegalArgumentException;

    /**
     * Creates all resources for the given {@link CloudProject}, using a standard project template.
     * The project template creates a pipeline build for the passed {@code sourceRepositoryUri}
     *
     * @param project                  the project in which the pipeline will be created
     * @param sourceRepositoryProvider the provider used for this source repository
     * @param sourceRepositoryUri      the location of the source repository to build the OpenShift application from
     */
    void configureProject(CloudProject project, String sourceRepositoryProvider, URI sourceRepositoryUri);

    /**
     * Creates all resources for the given {@link CloudProject}, using a standard project template.
     * The project template creates an S2I build for the passed {@code sourceRepositoryUri}
     *
     * @param project                    the project in which the pipeline will be created
     * @param sourceRepositoryProvider   the provider used for this source repository
     * @param sourceRepositoryUri        the location of the source repository to build the OpenShift application from
     * @param sourceRepositoryContextDir the location within the source repository where the application source can be found
     */
    void configureProject(CloudProject project, InputStream templateStream, String sourceRepositoryProvider, URI sourceRepositoryUri, String sourceRepositoryContextDir);

    /**
     * Creates all resources for the given {@link CloudProject}, using the given template and parameters.
     *
     * @param project        the project in which the pipeline will be created
     * @param templateStream the template to read
     * @param parameters     a {@link Map} containing the parameters for the templateStream. Cannot be null
     */
    void configureProject(CloudProject project, InputStream templateStream, Map<String, String> parameters);

    /**
     * @param project The project for which to construct webhook URLs
     * @return the list of webhook URLs associated with the Build Configuration, which
     * GitHub can use to trigger a build upon change pushes (if any).
     * @throws IllegalArgumentException If the project is not specified
     */
    List<URL> getWebhookUrls(CloudProject project) throws IllegalArgumentException;

    /**
     * Check if the specified project name exists
     *
     * @param name the project name. Required
     * @return <code>true</code> if the project name exists in this Openshift
     * @throws IllegalArgumentException If the project name is not specified
     */
    boolean projectExists(String name) throws IllegalArgumentException;

    /**
     * Returns a {@link Map} of routes for a given project
     *
     * @param project The {@link CloudProject} this service belongs to.
     * @return
     */
    Map<String, URL> getRoutes(CloudProject project);

    /**
     * Returns an optional the service URL for a given project and service name.
     *
     * @param serviceName the service name
     * @param project     The {@link CloudProject} this service belongs to.
     * @return an {@link URL} for the service URL for a given service name.
     * @throws IllegalArgumentException if the URL cannot be found for the serviceName and project
     */
    URL getServiceURL(String serviceName, CloudProject project) throws IllegalArgumentException;

    /**
     * @return the underlying {@link OpenShiftClient} for advanced operations
     */
    OpenShiftClient getOpenShiftClient();

    /**
     * @return the logged user
     */
    OpenShiftUser getLoggedUser();

    // Used in OSIO
    Optional<ConfigMap> getConfigMap(String configName, String namespace);

    ConfigMap createNewConfigMap(String ownerName);

    void createConfigMap(String configName, String namespace, ConfigMap configMap);

    void updateConfigMap(String configName, String namespace, Map<String, String> data);

    void triggerBuild(String projectName, String namespace);

    void applyBuildConfig(BuildConfig buildConfig, String namespace);
}