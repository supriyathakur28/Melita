package com.melita.orders.fulfilment.config;

import com.melita.orders.platform.idempotency.RequestHasher;
import com.melita.orders.platform.pii.PiiCipher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
class ApplicationConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    RequestHasher requestHasher(final JsonMapper jsonMapper) {
        return new RequestHasher(jsonMapper);
    }

    /** This service's own key: it never shares encryption keys with other services. */
    @Bean
    PiiCipher piiCipher(@Value("${pii.encryption-key}") final String key) {
        return new PiiCipher(key);
    }
}
