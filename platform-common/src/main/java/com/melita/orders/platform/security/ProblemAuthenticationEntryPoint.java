package com.melita.orders.platform.security;

import com.melita.orders.platform.web.SecurityProblems;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

/**
 * 401 as problem JSON. Delegates to the standard bearer-token entry point first, so the
 * {@code WWW-Authenticate} header stays RFC 6750 compliant.
 */
public class ProblemAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final BearerTokenAuthenticationEntryPoint bearer = new BearerTokenAuthenticationEntryPoint();
    private final JsonMapper jsonMapper;

    public ProblemAuthenticationEntryPoint(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        bearer.commence(request, response, authException);
        ProblemResponses.write(response, jsonMapper, SecurityProblems.unauthorized());
    }
}
