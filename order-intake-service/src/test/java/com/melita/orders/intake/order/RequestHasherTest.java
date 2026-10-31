package com.melita.orders.intake.order;

import com.melita.orders.intake.order.api.PlaceOrderRequest;
import com.melita.orders.platform.idempotency.RequestHasher;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class RequestHasherTest {

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final RequestHasher hasher = new RequestHasher(mapper);

    @Test
    void keyOrderAndWhitespaceDoNotChangeTheHash() {
        final String a = """
                {"externalReference":"ERP-1","items":[{"product":"MOBILE","packageCode":"MOBILE_PREPAID"}],
                 "customer":{"firstName":"Maria","lastName":"Borg","dateOfBirth":"1990-04-12",
                  "email":"maria.borg@example.com","mobileNumber":"+35679123456",
                  "identityDocument":{"type":"ID_CARD","number":"123456M"},
                  "address":{"line1":"12, Triq il-Kbira","locality":"Mosta","postcode":"MST 1234"}}}""";
        final String b = """
                {  "customer" : { "address":{"postcode":"MST 1234","locality":"Mosta","line1":"12, Triq il-Kbira"},
                   "identityDocument":{"number":"123456M","type":"ID_CARD"}, "mobileNumber":"+35679123456",
                   "email":"maria.borg@example.com","dateOfBirth":"1990-04-12","lastName":"Borg","firstName":"Maria"},
                   "items":[{"packageCode":"MOBILE_PREPAID","product":"MOBILE"}], "externalReference":"ERP-1" }""";

        assertThat(hasher.hash(mapper.readValue(a, PlaceOrderRequest.class)))
                .isEqualTo(hasher.hash(mapper.readValue(b, PlaceOrderRequest.class)))
                .hasSize(64);
    }

}
