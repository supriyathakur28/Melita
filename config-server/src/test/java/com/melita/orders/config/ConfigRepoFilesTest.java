package com.melita.orders.config;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Catches a broken YAML file in config-repo/ at build time rather than when a service fails to start. */
class ConfigRepoFilesTest {

    private static final Path CONFIG_REPO = Path.of("..", "config-repo");

    @Test
    void everyConfigFileIsValidYaml() throws IOException {
        List<Path> files;
        try (Stream<Path> stream = Files.list(CONFIG_REPO)) {
            files = stream.filter(p -> p.toString().endsWith(".yml")).toList();
        }
        assertThat(files).isNotEmpty();
        for (Path file : files) {
            new Yaml().loadAll(Files.readString(file)).forEach(document -> { });
        }
    }

    @Test
    void containsNoLiteralSecrets() throws IOException {
        try (Stream<Path> stream = Files.list(CONFIG_REPO)) {
            for (Path file : stream.toList()) {
                for (String line : Files.readAllLines(file)) {
                    if (line.matches("\\s*(password|secret|client-secret|key)\\s*:.*")) {
                        assertThat(line).as("%s must take secrets from the environment", file).contains("${");
                    }
                }
            }
        }
    }
}
