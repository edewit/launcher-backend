package io.fabric8.launcher.core.impl.steps;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;
import javax.enterprise.context.Dependent;
import javax.inject.Inject;

import io.fabric8.launcher.core.api.events.StatusMessageEvent;
import io.fabric8.launcher.core.api.projectiles.CreateProjectile;
import io.fabric8.launcher.service.git.api.GitRepository;
import io.fabric8.launcher.service.openshift.api.CloudProject;
import io.fabric8.launcher.service.openshift.api.CloudService;

import static io.fabric8.launcher.core.api.events.LauncherStatusEventKind.OPENSHIFT_CREATE;
import static io.fabric8.launcher.core.api.events.LauncherStatusEventKind.OPENSHIFT_PIPELINE;
import static io.fabric8.launcher.service.git.GitEnvironment.LAUNCHER_GIT_PROVIDER;
import static io.fabric8.launcher.service.git.spi.GitProviderType.GITHUB;
import static java.util.Collections.singletonMap;

/**
 * @author <a href="mailto:ggastald@redhat.com">George Gastaldi</a>
 */
@Dependent
public class CloudSteps {

    private static final Logger log = Logger.getLogger(CloudSteps.class.getName());

    /**
     * TODO: Use {@link io.fabric8.launcher.service.git.spi.GitServiceConfigs} instead
     */
    private static final String PROVIDER = LAUNCHER_GIT_PROVIDER.value(GITHUB.name()).toUpperCase();

    private final CloudService cloudService;

    @Inject
    public OpenShiftSteps(CloudService cloudService) {
        this.cloudService = cloudService;
    }

    /**
     * Creates an Openshift project if the project doesn't exist.
     */
    public CloudProject createOpenShiftProject(CreateProjectile projectile) {
        String projectName = projectile.getOpenShiftProjectName();
        CloudProject openShiftProject = cloudService.findProject(projectName)
                .orElseGet(() -> cloudService.createProject(projectName));
        projectile.getEventConsumer().accept(new StatusMessageEvent(projectile.getId(), OPENSHIFT_CREATE,
                                                                    singletonMap("location", openShiftProject.getConsoleOverviewUrl())));
        return openShiftProject;
    }

    public void configureBuildPipeline(CreateProjectile projectile, CloudProject cloudProject, @Nullable GitRepository gitRepository) {
        File path = projectile.getProjectLocation().toFile();
        List<AppInfo> apps = findProjectApps(path);
        if (apps.isEmpty()) {
            // Use Jenkins pipeline build
            cloudService.configureProject(cloudProject,
                                              PROVIDER,
                                              (gitRepository == null) ? null : gitRepository.getGitCloneUri());
        } else {
            // Use S2I builder templates
            for (AppInfo app : apps) {
                for (File tpl : app.resources) {
                    applyTemplate(cloudService, gitRepository, cloudProject, app, tpl);
                }
            }
            for (AppInfo app : apps) {
                for (File tpl : app.services) {
                    applyTemplate(cloudService, gitRepository, cloudProject, app, tpl);
                }
            }
            for (AppInfo app : apps) {
                for (File tpl : app.apps) {
                    applyTemplate(cloudService, gitRepository, cloudProject, app, tpl);
                }
            }
        }

        projectile.getEventConsumer().accept(new StatusMessageEvent(projectile.getId(), OPENSHIFT_PIPELINE,
                                                                    singletonMap("routes", cloudService.getRoutes(cloudProject))));
    }

    public List<URL> getWebhooks(CloudProject project) {
        return cloudService.getWebhookUrls(project);
    }

    private List<AppInfo> findProjectApps(File projectDir) {
        try (Stream<Path> stream = Files.walk(projectDir.toPath())) {
            return stream
                    .map(Path::toFile)
                    .filter(file -> file.isDirectory()
                            && file.getName().equals(".openshiftio"))
                    .map(file -> {
                        File contextDir = getContextDir(file.getParentFile(), projectDir);
                        List<File> resources = listYamlFiles(file, "resource.");
                        List<File> services = listYamlFiles(file, "service.");
                        List<File> apps = listYamlFiles(file, "application.");
                        boolean hasTemplates = !resources.isEmpty() || !services.isEmpty() || !apps.isEmpty();
                        if (contextDir != null && !contextDir.toString().isEmpty() && hasTemplates) {
                            return new AppInfo(contextDir.toString(), apps, resources, services);
                        } else {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.log(Level.SEVERE, "Error while finding project applications", e);
            return Collections.emptyList();
        }
    }

    private File getContextDir(File dir, File rootDir) {
        File rel = rootDir.toPath().relativize(dir.toPath()).toFile();
        if (!rel.toString().isEmpty()) {
            return rel;
        } else {
            return new File(".");
        }
    }

    private List<File> listYamlFiles(File dir, String prefix) {
        File[] ymls = dir.listFiles(f -> {
            String name = f.getName();
            return name.startsWith(prefix)
                    && (name.endsWith(".yml") || name.endsWith(".yaml"));
        });
        return ymls != null ? Arrays.asList(ymls) : Collections.emptyList();
    }

    private void applyTemplate(CloudService cloudService, @Nullable GitRepository gitRepository,
                               CloudProject cloudProject, AppInfo app, File tpl) {
        try (FileInputStream fis = new FileInputStream(tpl)) {
            cloudService.configureProject(cloudProject,
                                              fis,
                                              PROVIDER,
                                              (gitRepository == null) ? null : gitRepository.getGitCloneUri(),
                                              app.contextDir);
        } catch (FileNotFoundException e) {
            throw new IllegalStateException("Could not apply services template", e);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read services template", e);
        }
    }

    private class AppInfo {
        final List<File> resources;

        final List<File> services;

        AppInfo(String contextDir, List<File> apps, List<File> resources, List<File> services) {
            this.contextDir = contextDir;
            this.apps = new ArrayList<>(apps);
            this.resources = new ArrayList<>(resources);
            this.services = new ArrayList<>(services);
        }

        final String contextDir;

        final List<File> apps;
    }
}
