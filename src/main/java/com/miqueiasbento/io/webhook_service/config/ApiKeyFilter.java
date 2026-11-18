package com.miqueiasbento.io.webhook_service.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ApiKeyFilter extends OncePerRequestFilter {
    private final byte[] apiKey;

    public ApiKeyFilter(@Value("${app.security.api-key}") String configuredKey) {
        if (configuredKey == null || configuredKey.isBlank()) {
            throw new IllegalArgumentException("API_KEY must be configured");
        }
        this.apiKey = configuredKey.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        boolean destinations = path.equals("/api/v1/destinations")
                || path.startsWith("/api/v1/destinations/");
        boolean deliveries = path.equals("/api/v1/deliveries")
                || path.startsWith("/api/v1/deliveries/");
        return !(destinations || deliveries || path.equals("/api/v1/events"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String suppliedKey = request.getHeader("X-API-Key");
        if (suppliedKey == null || !MessageDigest.isEqual(
                apiKey, suppliedKey.getBytes(StandardCharsets.UTF_8))) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid API Key");
            return;
        }
        chain.doFilter(request, response);
    }
}
