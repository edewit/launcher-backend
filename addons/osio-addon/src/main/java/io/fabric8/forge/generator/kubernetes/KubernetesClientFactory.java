package io.fabric8.forge.generator.kubernetes;

import java.util.regex.Pattern;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;

import io.fabric8.forge.generator.EnvironmentVariables;
import io.fabric8.forge.generator.keycloak.KeycloakEndpoint;
import io.fabric8.forge.generator.keycloak.TokenHelper;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.launcher.core.api.Identities;
import io.fabric8.launcher.service.openshift.api.OpenShiftService;
import io.fabric8.launcher.service.openshift.api.OpenShiftServiceFactory;
import io.fabric8.utils.Strings;
import org.jboss.forge.addon.ui.context.UIContext;

/**
 * Factory that constructs a KubernetesClient.
 */
@ApplicationScoped
public class KubernetesClientFactory {
    private static final Pattern PATTERN = Pattern.compile("https?://api\\.(.+?)\\.");

    @Inject
    private Identities identities;

    @Inject
    private OpenShiftServiceFactory openShiftServiceFactory;

    public KubernetesClientHelper createKubernetesClient(UIContext context) {
        String authHeader = TokenHelper.getMandatoryAuthHeader(context);
        OpenShiftService openShiftService = openShiftServiceFactory.create(identities.getOpenShiftIdentity(authHeader, getCluster()));
        return new KubernetesClientHelper(createKubernetesClientForSSO(context), openShiftService);
    }

    private String getCluster() {
        String openShiftApiUrl = System.getenv(EnvironmentVariables.OPENSHIFT_API_URL);
        String cluster = PATTERN.matcher(openShiftApiUrl).group(1);
        return Strings.isNotBlank(cluster) ? cluster : null;
    }

    /**
     * Creates the kubernetes client for the SSO signed in user
     */
    private KubernetesClient createKubernetesClientForSSO(UIContext context) {
        String authHeader = TokenHelper.getMandatoryAuthHeader(context);
        String openshiftToken = TokenHelper.getMandatoryTokenFor(KeycloakEndpoint.GET_OPENSHIFT_TOKEN, authHeader);
        String openShiftApiUrl = System.getenv(EnvironmentVariables.OPENSHIFT_API_URL);
        if (Strings.isNullOrBlank(openShiftApiUrl)) {
            throw new WebApplicationException("No environment variable defined: "
                                                      + EnvironmentVariables.OPENSHIFT_API_URL + " so cannot connect to OpenShift Online!");
        }
        Config config = new ConfigBuilder().withMasterUrl(openShiftApiUrl).withOauthToken(openshiftToken).
                // TODO until we figure out the trust thing lets ignore warnings
                        withTrustCerts(true).
                        build();
        return new DefaultKubernetesClient(config);
    }

}
