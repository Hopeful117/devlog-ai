package com.hopeful117.devlogai.shared.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Correlation-ID";
    public static final String MDC_KEY = "correlationId";
    private static final int MAX_CORRELATION_ID_LENGTH = 128;
    private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String correlationId = resolveCorrelationId(request.getHeader(HEADER_NAME));
        long startedAt = System.nanoTime();
        MDC.put(MDC_KEY, correlationId);
        try {
            response.setHeader(HEADER_NAME, correlationId);
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            int status = response.getStatus();
            if (status >= 500) {
                log.error("HTTP request completed method={} path={} status={} durationMs={}",
                        request.getMethod(), request.getRequestURI(), status, durationMs);
            } else if (status >= 400) {
                log.warn("HTTP request completed method={} path={} status={} durationMs={}",
                        request.getMethod(), request.getRequestURI(), status, durationMs);
            } else if (isHealthCheck(request.getRequestURI())) {
                log.debug("HTTP health check completed path={} status={} durationMs={}",
                        request.getRequestURI(), status, durationMs);
            } else {
                log.info("HTTP request completed method={} path={} status={} durationMs={}",
                        request.getMethod(), request.getRequestURI(), status, durationMs);
            }
            MDC.remove(MDC_KEY);
        }
    }

    private boolean isHealthCheck(String path) {
        return "/health".equals(path) || "/actuator/health".equals(path);
    }

    static String resolveCorrelationId(String candidate) {
        if (candidate == null || candidate.isBlank()
                || candidate.length() > MAX_CORRELATION_ID_LENGTH
                || !candidate.matches("[A-Za-z0-9._:-]+")) {
            return UUID.randomUUID().toString();
        }
        return candidate;
    }
}
