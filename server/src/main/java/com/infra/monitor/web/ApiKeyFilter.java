package com.infra.monitor.web;

import com.infra.monitor.config.MonitorProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Optional shared-secret protection for /api/**. Disabled when monitor.security.api-key is blank. */
@Component
public class ApiKeyFilter extends OncePerRequestFilter {

    private final byte[] expected;

    public ApiKeyFilter(MonitorProperties props) {
        this.expected = props.security().apiKey().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return expected.length == 0 || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String supplied = request.getHeader("X-API-Key");
        if (supplied != null && MessageDigest.isEqual(supplied.getBytes(StandardCharsets.UTF_8), expected)) {
            chain.doFilter(request, response);
        } else {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or missing X-API-Key");
        }
    }
}
