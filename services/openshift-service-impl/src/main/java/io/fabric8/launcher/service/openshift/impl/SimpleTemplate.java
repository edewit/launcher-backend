package io.fabric8.launcher.service.openshift.impl;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import static java.nio.charset.StandardCharsets.UTF_8;

class SimpleTemplate {

    private final ContextBuilder templateContext;

    SimpleTemplate(String... context) {
        templateContext = ContextBuilder.context();
        for (int i = 0; i < context.length; i++) {
            templateContext.with(context[i], context[++i]);
        }
    }

    InputStream parseTemplate(final String template) {
        return parseTemplate(template, templateContext);
    }

    InputStream parseTemplate(final String template, final Map<String, String> context) {
        InputStream resource = getClass().getResourceAsStream(String.format("/%s", template));
        String content = streamToString(resource);
        for (Map.Entry<String, String> entry : context.entrySet()) {
            content = content.replaceAll(entry.getKey(), entry.getValue());
        }

        return new BufferedInputStream(new ByteArrayInputStream(content.getBytes(UTF_8)));
    }

    String streamToString(InputStream resource) {
        return new Scanner(resource, UTF_8.name()).useDelimiter("\\A").next();
    }

    static String createKey(String key) {
        return key.replaceAll("(\\$|\\{)", "\\\\$1");
    }

    public static class ContextBuilder extends HashMap<String, String> {
        public ContextBuilder with(String key, String value) {
            put(createKey(key), value);
            return this;
        }

        public static ContextBuilder context() {
            return new ContextBuilder();
        }
    }
}
