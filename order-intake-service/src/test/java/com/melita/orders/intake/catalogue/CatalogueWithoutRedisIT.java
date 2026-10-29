package com.melita.orders.intake.catalogue;

import com.melita.orders.intake.IntakeIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/** Redis is an optimisation: with Redis unreachable, the catalogue is read from MySQL. */
@TestPropertySource(properties = {"spring.data.redis.host=localhost", "spring.data.redis.port=1"})
class CatalogueWithoutRedisIT extends IntakeIntegrationTest {

    @Autowired
    CatalogueService catalogueService;

    @Test
    void catalogueIsServedFromTheDatabaseWhenRedisIsDown() {
        Catalogue catalogue = catalogueService.current();

        assertThat(catalogue.findPackage("MOBILE_POSTPAID")).isPresent();
    }
}
