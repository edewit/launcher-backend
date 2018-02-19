package io.fabric8.launcher.osio;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.validation.ConstraintViolationException;

import io.fabric8.launcher.core.api.Boom;
import io.fabric8.launcher.core.api.ImmutableBoom;
import io.fabric8.launcher.core.api.MissionControl;
import io.fabric8.launcher.core.api.Projectile;
import io.fabric8.launcher.core.api.ProjectileContext;
import io.fabric8.launcher.core.spi.Application;
import io.fabric8.launcher.osio.projectiles.ImmutableOsioProjectile;
import io.fabric8.launcher.osio.projectiles.ImportProjectile;
import io.fabric8.launcher.osio.projectiles.OsioProjectile;
import io.fabric8.launcher.osio.projectiles.OsioProjectileContext;
import io.fabric8.launcher.osio.steps.GitSteps;
import io.fabric8.launcher.osio.steps.OpenShiftSteps;
import io.fabric8.launcher.service.git.api.GitRepository;

import static io.fabric8.launcher.core.spi.Application.ApplicationType.LAUNCHER;
import static io.fabric8.launcher.core.spi.Application.ApplicationType.OSIO;

/**
 * @author <a href="mailto:ggastald@redhat.com">George Gastaldi</a>
 */
@Application(OSIO)
@RequestScoped
public class OsioMissionControl implements MissionControl {

    @Inject
    @Application(LAUNCHER)
    private MissionControl missionControl;

    @Inject
    private GitSteps gitSteps;

    @Inject
    private OpenShiftSteps openShiftSteps;

    @Override
    public void validate(ProjectileContext context) throws ConstraintViolationException {

    }

    @Override
    public Projectile prepare(ProjectileContext genericContext) {
        if (!(genericContext instanceof OsioProjectileContext)) {
            throw new IllegalArgumentException("OsioMissionControl only supports " + OsioProjectileContext.class.getName() + " instances");
        }
        OsioProjectileContext context = (OsioProjectileContext) genericContext;
        Projectile projectile = missionControl.prepare(context);
        return ImmutableOsioProjectile.builder()
                .from(projectile)
                .spacePath(context.getSpacePath())
                .pipelineId(context.getPipelineId())
                .build();
    }

    @Override
    public Boom launch(Projectile genericProjectile) throws IllegalArgumentException {
        if (!(genericProjectile instanceof OsioProjectile)) {
            throw new IllegalArgumentException("OsioMissionControl only supports " + OsioProjectile.class.getName() + " instances");
        }
        OsioProjectile projectile = (OsioProjectile) genericProjectile;
        GitRepository repository = gitSteps.createRepository(projectile);

// Workflow:
//        1. Github repository is created
        GitRepository repository = gitSteps.createRepository(projectile);
//        6. Webhoook is created
        gitSteps.createWebHooks(projectile, repository);
//        3. BuildConfig is created in Openshift
        openShiftSteps.createBuildConfig(projectile);
//        4. Jenkins job is created
//        5. Build is triggered
//        6. Webhoook is created
        openShiftSteps.createBuildConfig(projectile, repository);
        openShiftSteps.createJenkinsJob(projectile, repository);

//        2. Code is pushed to the repository
//        5. Build is triggered (if the webhook is created, pushing the code to the repository will automatically trigger the build)
        gitSteps.pushToGitRepository(projectile, repository);
        return ImmutableBoom.builder()
                .createdRepository(repository)
                .createdProject(null)
                .build();
    }


    public Boom launch(ImportProjectile importProjectile) {
        return null;
    }
}
