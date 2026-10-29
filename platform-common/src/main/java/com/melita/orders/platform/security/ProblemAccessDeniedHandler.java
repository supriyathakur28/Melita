package com.melita.orders.platform.security;

import com.melita.orders.platform.web.SecurityProblems;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

/** 403 as problem JSON, keeping the standard {@code WWW-Authenticate: Bearer error="insufficient_scope"}. */
public class ProblemAccessDeniedHandler implements AccessDeniedHandler {

    private final BearerTokenAccessDeniedHandler bearer = new BearerTokenAccessDeniedHandler();
    private final JsonMapper jsonMapper;

    public ProblemAccessDeniedHandler(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        bearer.handle(request, response, accessDeniedException);
        ProblemResponses.write(response, jsonMapper, SecurityProblems.forbidden());
    }
}
