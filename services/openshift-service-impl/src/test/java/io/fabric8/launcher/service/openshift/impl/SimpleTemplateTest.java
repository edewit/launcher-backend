package io.fabric8.launcher.service.openshift.impl;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import static org.junit.Assert.*;

public class SimpleTemplateTest {

    private SimpleTemplate template = new SimpleTemplate();

    @Test
    public void parseTemplate() {
        // given
        Map<String, String> context = new HashMap<>();
        String projectName = "super-duper";
        context.put("\\$\\{PROJECT_NAME}", projectName);

        // when
        InputStream inputStream = template.parseTemplate("deployment.yaml", context);

        // then
        String output = template.streamToString(inputStream);
        assertTrue(output.contains(projectName));
    }

    @Test
    public void createKey() {
        // given
        String key = "${PROJECT_NAME}";

        // when
        String templateKey = SimpleTemplate.createKey(key);

        // then
        assertEquals("\\$\\{PROJECT_NAME}", templateKey);
    }

    @Test
    public void contextBuilder() {
        // given
        String key = "${PROJECT_NAME}";
        String value = "something";

        // when
        Map<String, String> context = SimpleTemplate.ContextBuilder.context().with(key, value).with("super", "something");

        // then
        assertEquals(2, context.size());
        assertEquals(value, context.get(SimpleTemplate.createKey(key)));
    }

    @Test
    public void constructor() {
        // given
        String projectName = "dope-project";

        // when
        SimpleTemplate template = new SimpleTemplate("${PROJECT_NAME}", projectName);
        InputStream inputStream = template.parseTemplate("deployment.yaml");

        // then
        assertTrue(template.streamToString(inputStream).contains(projectName));
    }
}