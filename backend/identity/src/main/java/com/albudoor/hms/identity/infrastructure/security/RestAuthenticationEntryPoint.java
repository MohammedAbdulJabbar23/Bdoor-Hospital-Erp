package com.albudoor.hms.identity.infrastructure.security;

import com.albudoor.hms.platform.web.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Returns 401 Unauthorized (with an {@link ApiError}-shaped body) when an unauthenticated
 * request hits a protected endpoint. Without this, Spring Security's default entry point
 * lets such requests fall through to a 403, which is incorrect for missing/invalid credentials.
 * Authenticated-but-forbidden requests are still handled by the access-denied path (403).
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ApiError body = ApiError.of(401, "UNAUTHORIZED", "Authentication is required to access this resource.");
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
