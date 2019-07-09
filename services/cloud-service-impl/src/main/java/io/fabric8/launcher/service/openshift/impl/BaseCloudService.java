package io.fabric8.launcher.service.openshift.impl;

import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.DoneableConfigMap;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.RequestConfig;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.launcher.base.identity.Identity;
import io.fabric8.launcher.base.identity.IdentityVisitor;
import io.fabric8.launcher.base.identity.TokenIdentity;
import io.fabric8.launcher.base.identity.UserPasswordIdentity;
import io.fabric8.launcher.service.openshift.api.CloudProject;
import io.fabric8.launcher.service.openshift.api.CloudService;
import io.fabric8.launcher.service.openshift.api.CloudServiceFactory;
import io.fabric8.launcher.service.openshift.api.OpenShiftCluster;
import io.fabric8.launcher.service.openshift.spi.CloudServiceSpi;
import io.fabric8.openshift.api.model.Parameter;
import io.fabric8.openshift.api.model.ParameterBuilder;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.stripEnd;

abstract class BaseCloudService implements CloudService, CloudServiceSpi {

    final KubernetesClient client;

    @Nullable
    URL consoleUrl;

//    static {
//        // Avoid using ~/.kube/config
//        System.setProperty(Config.KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY, "false");
//        // Avoid using /var/run/secrets/kubernetes.io/serviceaccount/token
//        System.setProperty(Config.KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY, "false");
//    }

    BaseCloudService(final CloudServiceFactory.Parameters parameters) {
        OpenShiftCluster cluster = parameters.getCluster();
        Identity identity = parameters.getIdentity();
        ConfigBuilder configBuilder = new ConfigBuilder()
                .withMasterUrl(cluster.getApiUrl())
                //TODO Issue #17 never do this in production as it opens us to man-in-the-middle attacks
                .withTrustCerts(true);
        identity.accept(new IdentityVisitor() {
            @Override
            public void visit(TokenIdentity token) {
                configBuilder.withOauthToken(token.getToken());
            }

            @Override
            public void visit(UserPasswordIdentity userPassword) {
                configBuilder
                        .withUsername(userPassword.getUsername())
                        .withPassword(userPassword.getPassword());
            }
        });
        final Config config = configBuilder.build();
        String impersonateUsername = parameters.getImpersonateUsername();
        if (impersonateUsername != null) {
            // Impersonate the given user name (can be null)
            RequestConfig requestConfig = config.getRequestConfig();
            requestConfig.setImpersonateUsername(impersonateUsername);
            requestConfig.setImpersonateGroups("system:authenticated", "system:authenticated:oauth");
        }
        client = new DefaultKubernetesClient(config);
    }

    @Override
    public boolean deleteProject(CloudProject project) throws IllegalArgumentException {
        if (project == null) {
            throw new IllegalArgumentException("project must be specified");
        }
        final String projectName = project.getName();
        return this.deleteProject(projectName);
    }

    @Override
    public void deleteConfigMap(String namespace, String configName) {
        getResource(configName, namespace).delete();
    }

    Resource<ConfigMap, DoneableConfigMap> getResource(String configName, String namespace) {
        String configMapName = convertToKubernetesName(configName, false);
        return client.configMaps().inNamespace(namespace).withName(configMapName);

    }

    List<Parameter> getParameters(CloudProject project, String sourceRepositoryProvider,
                                  @Nullable URI sourceRepositoryUri,
                                  @Nullable String sourceRepositoryContextDir) {
        List<Parameter> parameters = new ArrayList<>();
        if (sourceRepositoryUri != null) {
            parameters.add(createParameter("SOURCE_REPOSITORY_URL", sourceRepositoryUri.toString()));
            String repositoryName = getRepositoryName(sourceRepositoryUri);
            if (isNotBlank(repositoryName)) {
                parameters.add(createParameter("SOURCE_REPOSITORY_NAME", repositoryName));
            }
        }
        parameters.add(createParameter("SOURCE_REPOSITORY_PROVIDER", sourceRepositoryProvider));
        if (sourceRepositoryContextDir != null) {
            parameters.add(createParameter("SOURCE_REPOSITORY_DIR", sourceRepositoryContextDir));
        }
        parameters.add(createParameter("PROJECT", project.getName()));
        if (consoleUrl != null) {
            parameters.add(createParameter("OPENSHIFT_CONSOLE_URL", consoleUrl.toString()));
        }
        parameters.add(createParameter("GITHUB_WEBHOOK_SECRET", Long.toString(System.currentTimeMillis())));
        return parameters;
    }

    Parameter createParameter(final String name, final String value) {
        return new ParameterBuilder().withName(name).withValue(value).build();
    }

    /**
     * Extract the Git Repository name to be used in the SOURCE_REPOSITORY_NAME parameter in the templates
     *
     * @param uri a GitHub URI (eg https://github.com/foo/bar)
     * @return the repository name of the given {@link URI} (eg. bar)
     */
    static String getRepositoryName(URI uri) {
        String path = stripEnd(uri.getPath(), "/");
        String substring = path.substring(path.lastIndexOf('/') + 1);
        return stripEnd(substring, ".git");
    }

    private static String convertToKubernetesName(String text, boolean allowDots) {
        String lower = text.toLowerCase();
        StringBuilder builder = new StringBuilder();
        boolean started = false;
        char lastCh = ' ';
        for (int i = 0, last = lower.length() - 1; i <= last; i++) {
            char ch = lower.charAt(i);
            boolean digit = ch >= '0' && ch <= '9';
            // names cannot start with a digit so lets add a prefix
            if (digit && builder.length() == 0) {
                builder.append('n');
            }
            if (!(ch >= 'a' && ch <= 'z') && !digit) {
                if (ch == '/') {
                    ch = '.';
                } else if (ch != '.' && ch != '-') {
                    ch = '-';
                }
                if (!allowDots && ch == '.') {
                    ch = '-';
                }
                if (!started || lastCh == '-' || lastCh == '.' || i == last) {
                    continue;
                }
            }
            builder.append(ch);
            started = true;
            lastCh = ch;
        }
        return builder.toString();
    }
}
