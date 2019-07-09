package io.fabric8.launcher.service.openshift.impl;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import io.fabric8.launcher.base.Paths;
import io.fabric8.launcher.service.openshift.api.CloudProject;
import io.fabric8.launcher.service.openshift.api.OpenShiftResource;

/**
 * Implementation of a value object representing a project in OpenShift
 *
 * @author <a href="mailto:alr@redhat.com">Andrew Lee Rubinger</a>
 */
public final class CloudProjectImpl implements CloudProject {

    /**
     * Creates a new {@link CloudProject} value object
     *
     * @param name
     * @throws IllegalArgumentException
     */
    public CloudProjectImpl(final String name, final URL consoleUrl) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("name is required");
        }
        this.name = name;
        this.consoleUrl = Objects.toString(consoleUrl, null);
    }

    private static final String CONSOLE_OVERVIEW_URL_PREFIX = "/console/project/";

    private static final String CONSOLE_OVERVIEW_URL_SUFFIX = "/overview/";

    private final String name;

    private final String consoleUrl;

    private final List<OpenShiftResource> resources = new ArrayList<>();

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public URL getConsoleOverviewUrl() {
        if (consoleUrl == null) {
            return null;
        }
        String urlValue = Paths.join(consoleUrl,
                                     CONSOLE_OVERVIEW_URL_PREFIX,
                                     this.getName(),
                                     CONSOLE_OVERVIEW_URL_SUFFIX);
        final URL url;
        try {
            url = new URL(urlValue);
        } catch (final MalformedURLException murle) {
            throw new RuntimeException(murle);
        }

        return url;
    }

    /**
     * Adds a existing resource on the project
     *
     * @param resource the resource to add
     */
    public void addResource(final OpenShiftResource resource) {
        this.resources.add(resource);
    }

    @Override
    public List<OpenShiftResource> getResources() {
        return Collections.unmodifiableList(this.resources);
    }

    @Override
    public String toString() {
        return "[Project] " + this.name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CloudProjectImpl that = (CloudProjectImpl) o;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}
