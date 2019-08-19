package io.fabric8.launcher.web.endpoints.inputs;

import java.util.Set;

import javax.validation.Configuration;
import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;

import io.fabric8.launcher.booster.catalog.rhoar.Mission;
import io.fabric8.launcher.booster.catalog.rhoar.Runtime;
import io.fabric8.launcher.service.openshift.api.CloudService;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Created by hrishin on 6/15/18.
 */
class LaunchProjectileInputTest {

    private Validator validator;

    private LaunchProjectileInput launchProjectInput;

    @BeforeEach
    public void init() {
        initializeValidator();
        initializeLaunchInputs();
    }

    @Test
    void shouldNotViolateProjectNameConstraints() {
        // GIVEN
        launchProjectInput.setProjectName("Test-_123");

        // WHEN
        Set<ConstraintViolation<LaunchProjectileInput>> violations = validator.validate(launchProjectInput);

        // THEN
        assertThat(violations).isEmpty();
    }

    @Test
    void projectNameShouldStartWithAlphabeticCharacters() {
        // GIVEN
        launchProjectInput.setProjectName("123Test");

        // WHEN
        Set<ConstraintViolation<LaunchProjectileInput>> violations = validator.validate(launchProjectInput);

        // THEN
        assertThat(violations).isNotEmpty();
        assertThat(hasMessage(violations, CloudService.PROJECT_NAME_VALIDATION_MESSAGE)).isTrue();
    }

    @Test
    void projectNameShouldEndWithAlphanumericCharacters() {
        // GIVEN
        launchProjectInput.setProjectName("123Test**");
    
        // WHEN
        Set<ConstraintViolation<LaunchProjectileInput>> violations = validator.validate(launchProjectInput);

        // THEN
        assertThat(violations).isNotEmpty();
        assertThat(hasMessage(violations, CloudService.PROJECT_NAME_VALIDATION_MESSAGE)).isTrue();
    }

    private void initializeValidator() {
        final Configuration<?> cfg = Validation.byDefaultProvider().configure();
        cfg.messageInterpolator(new ParameterMessageInterpolator());
        this.validator = cfg.buildValidatorFactory().getValidator();
    }

    private void initializeLaunchInputs() {
        launchProjectInput = new LaunchProjectileInput();
        launchProjectInput.setMission(new Mission("rest-http"));
        launchProjectInput.setRuntime(new Runtime("vert.x"));
        launchProjectInput.setGitRepository("foo");
    }

    private boolean hasMessage(Set<ConstraintViolation<LaunchProjectileInput>> violations, String message) {
        return violations
                .stream()
                .anyMatch(v -> v.getMessage().equalsIgnoreCase(message));
    }
}
