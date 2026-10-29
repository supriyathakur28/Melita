package com.melita.orders.management.api;

import org.springframework.security.oauth2.jwt.Jwt;

final class Agents {

    private Agents() {
    }

    /** The agent's user name from the token, never from the request body. */
    static String name(Jwt jwt) {
        String username = jwt.getClaimAsString("preferred_username");
        return username != null ? username : jwt.getSubject();
    }
}
