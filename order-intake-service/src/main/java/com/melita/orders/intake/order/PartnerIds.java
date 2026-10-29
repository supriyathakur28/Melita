package com.melita.orders.intake.order;

import com.melita.orders.platform.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The partner is identified by the {@code partner_id} claim (set by a Keycloak mapper per client), not by
 * the OAuth client ID, so a partner can rotate or add clients without losing access to its orders.
 */
final class PartnerIds {

    static final String CLAIM = "partner_id";

    private PartnerIds() {
    }

    static String from(Jwt jwt) {
        String partnerId = jwt == null ? null : jwt.getClaimAsString(CLAIM);
        if (partnerId == null || partnerId.isBlank()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "PARTNER_NOT_CONFIGURED",
                    "The token does not identify a partner");
        }
        return partnerId;
    }
}
