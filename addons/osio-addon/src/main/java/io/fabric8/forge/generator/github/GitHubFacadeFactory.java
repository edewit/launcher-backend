package io.fabric8.forge.generator.github;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import io.fabric8.forge.generator.AttributeMapKeys;
import io.fabric8.forge.generator.git.GitAccount;
import io.fabric8.forge.generator.keycloak.TokenHelper;
import io.fabric8.launcher.core.api.Identities;
import io.fabric8.launcher.service.github.api.GitHubService;
import io.fabric8.launcher.service.github.api.GitHubServiceFactory;
import org.jboss.forge.addon.ui.context.UIContext;

/**
 * A factory to create a GitHubFacade with.
 */
@ApplicationScoped
public class GitHubFacadeFactory {

    @Inject
    private GitHubServiceFactory gitHubServiceFactory;

    @Inject
    private Identities identities;

    public GitHubFacade createGitHubFacade(UIContext context) {
        GitAccount details = (GitAccount) context.getAttributeMap().get(AttributeMapKeys.GIT_ACCOUNT);
        if (details == null) {
            details = GitAccount.loadFromSaaS(context);
        }
        GitHubService gitHubService = gitHubServiceFactory.create(identities.getGitHubIdentity(TokenHelper.getAuthHeader(context)));
        return new GitHubFacade(details, gitHubService);
    }
}
