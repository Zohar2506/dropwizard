package io.dropwizard.configuration;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Resources;
import io.dropwizard.jackson.Jackson;
import org.fest.assertions.data.MapEntry;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import java.io.File;
import java.util.*;

import static org.fest.assertions.api.Assertions.assertThat;
import static org.fest.assertions.api.Assertions.failBecauseExceptionWasNotThrown;

public class ConfigurationFactoryTest {


    @SuppressWarnings("UnusedDeclaration")
    public static class ExampleServer {

        @JsonProperty
        private int port = 8000;

        public int getPort() {
            return port;
        }

    }

    @SuppressWarnings("UnusedDeclaration")
    public static class Example {

        @NotNull
        @Pattern(regexp = "[\\w]+[\\s]+[\\w]+")
        private String name;

        @JsonProperty
        private int age = 1;

        @JsonProperty
        private Map<String, String> properties = Maps.newLinkedHashMap();

        @JsonProperty
        private List<ExampleServer> servers = Lists.newArrayList();

        public String getName() {
            return name;
        }

        public Map<String, String> getProperties() {
            return properties;
        }

        public List<ExampleServer> getServers() {
            return servers;
        }

    }

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final ConfigurationFactory<Example> factory =
            new ConfigurationFactory<>(Example.class, validator, Jackson.newObjectMapper(), "dw");
    private File malformedFile;
    private File invalidFile;
    private File validFile;

    @After
    public void resetConfigOverrides() {
        for (Enumeration<?> props = System.getProperties().propertyNames(); props.hasMoreElements();) {
            String keyString = (String) props.nextElement();
            if (keyString.startsWith("dw.")) {
                System.clearProperty(keyString);
            }
        }
    }

    @Before
    public void setUp() throws Exception {
        this.malformedFile = new File(Resources.getResource("factory-test-malformed.yml").toURI());
        this.invalidFile = new File(Resources.getResource("factory-test-invalid.yml").toURI());
        this.validFile = new File(Resources.getResource("factory-test-valid.yml").toURI());
    }

    @Test
    public void loadsValidConfigFiles() throws Exception {
        final Example example = factory.build(validFile);

        assertThat(example.getName())
                .isEqualTo("Coda Hale");
        assertThat(example.getProperties())
                .contains(MapEntry.entry("debug", "true"),
                        MapEntry.entry("settings.enabled", "false"));

        assertThat(example.getServers())
                .hasSize(3);
        assertThat(example.getServers().get(0).getPort())
                .isEqualTo(8080);
    }

    @Test
    public void overridesProperties() throws Exception {
        System.setProperty("dw.name", "Hale Coda");
        System.setProperty("dw.properties.settings.enabled", "true");
        System.setProperty("dw.servers[0].port", "7000");
        System.setProperty("dw.servers[2].port", "9000");
        final Example example = factory.build(validFile);

        assertThat(example.getName())
                .isEqualTo("Hale Coda");
        assertThat(example.getProperties())
                .contains(MapEntry.entry("debug", "true"),
                        MapEntry.entry("settings.enabled", "true"));

        assertThat(example.getServers())
                .hasSize(3);
        assertThat(example.getServers().get(0).getPort())
                .isEqualTo(7000);
        assertThat(example.getServers().get(2).getPort())
                .isEqualTo(9000);
    }

    @Test
    public void throwsAnExceptionOnUnexpectedArrayOverride() throws Exception {
        System.setProperty("dw.servers.port", "9000");
        try {
            factory.build(validFile);
            failBecauseExceptionWasNotThrown(ConfigurationParsingException.class);
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage())
                    .containsOnlyOnce("target is an array but no index specified");
        }
    }

    @Test
    public void throwsAnExceptionOnOverrideArrayIndexOutOfBounds() throws Exception {
        System.setProperty("dw.servers[4].port", "9000");
        try {
            factory.build(validFile);
            failBecauseExceptionWasNotThrown(ConfigurationParsingException.class);
        } catch (ArrayIndexOutOfBoundsException e) {
            assertThat(e.getMessage())
                    .containsOnlyOnce("index is greater than size of array");
        }
    }

    @Test
    public void throwsAnExceptionOnMalformedFiles() throws Exception {
        try {
            factory.build(malformedFile);
            failBecauseExceptionWasNotThrown(ConfigurationParsingException.class);
        } catch (ConfigurationParsingException e) {
            assertThat(e.getMessage())
                    .containsOnlyOnce(" * Failed to parse configuration; Can not instantiate");
        }
    }

    @Test
    public void throwsAnExceptionOnInvalidFiles() throws Exception {
        try {
            factory.build(invalidFile);
        } catch (ConfigurationValidationException e) {
            if ("en".equals(Locale.getDefault().getLanguage())) {
                assertThat(e.getMessage())
                        .endsWith(String.format(
                                "factory-test-invalid.yml has an error:%n" +
                                        "  * name must match \"[\\w]+[\\s]+[\\w]+\" (was Boop)%n"));
            }
        }
    }
}
