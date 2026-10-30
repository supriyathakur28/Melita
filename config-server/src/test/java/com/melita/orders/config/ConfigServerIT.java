package com.melita.orders.config;

import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatusCode;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.security.user.name=config", "spring.security.user.password=secret"})
class ConfigServerIT {

    @TempDir
    static Path repository;

    @LocalServerPort
    int port;

    @BeforeAll
    static void createGitRepository() throws Exception {
        final Path configRepo = Files.createDirectories(repository.resolve("config-repo"));
        Files.writeString(configRepo.resolve("order-intake-service.yml"), "slots:\n  min-lead-working-days: 2\n");
        Files.writeString(configRepo.resolve("order-intake-service-docker.yml"), "spring.rabbitmq.host: rabbitmq\n");
        try (Git git = Git.init().setDirectory(repository.toFile()).setInitialBranch("main").call()) {
            git.add().addFilepattern(".").call();
            git.commit().setMessage("config").setAuthor("test", "test@example.com")
                    .setCommitter("test", "test@example.com").setSign(false).call();
        }
    }

    @DynamicPropertySource
    static void gitUri(final DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.config.server.git.uri", () -> repository.toString());
    }

    @Test
    void servesCommittedConfigurationPerApplicationAndProfile() {
        final String body = client().get().uri("/order-intake-service/docker")
                .headers(h -> h.setBasicAuth("config", "secret"))
                .retrieve().body(String.class);

        assertThat(body)
                .contains("\"slots.min-lead-working-days\":2")
                .contains("\"spring.rabbitmq.host\":\"rabbitmq\"");
    }

    @Test
    void rejectsUnauthenticatedRequests() {
        final HttpStatusCode status = client().get().uri("/order-intake-service/default")
                .exchange((request, response) -> response.getStatusCode());

        assertThat(status.value()).isEqualTo(401);
    }

    @Test
    void healthIsPublic() {
        final HttpStatusCode status = client().get().uri("/actuator/health")
                .exchange((request, response) -> response.getStatusCode());

        assertThat(status.value()).isEqualTo(200);
    }

    private RestClient client() {
        return RestClient.create("http://localhost:" + port);
    }
}
