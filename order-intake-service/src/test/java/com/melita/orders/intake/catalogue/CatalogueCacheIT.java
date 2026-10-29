package com.melita.orders.intake.catalogue;

import com.melita.orders.intake.IntakeIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogueCacheIT extends IntakeIntegrationTest {

    @Autowired
    CatalogueService catalogueService;
    @Autowired
    StringRedisTemplate redis;

    @Test
    void catalogueIsCachedInRedisAsOneEntry() {
        catalogueService.current();

        String cached = redis.opsForValue().get("order-intake:catalogue::all");
        assertThat(cached).contains("INTERNET_1GBPS").doesNotContain("@class");
    }
}
