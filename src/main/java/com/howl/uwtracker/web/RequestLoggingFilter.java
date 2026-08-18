package com.howl.uwtracker.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import static net.logstash.logback.argument.StructuredArguments.kv;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Logs every request's headers and (for JSON bodies) payload alongside its outcome, so failures
 * like an unexplained 401 on /upload-run (Cloud Run's own access log has no headers or body) can
 * actually be diagnosed after the fact. Registered in {@link WebMvcConfig#requestLoggingFilter()}
 * with an explicit url-pattern list rather than a bare {@code @Component} — this must only wrap the
 * API surface (/api/**, plus the top-level plugin endpoints), not SpaFallbackController's HTML
 * forwards or static asset requests, which would otherwise dwarf the log volume with nothing
 * debuggable in it.
 *
 * <p>{@code X-Machine-Key} and {@code Cookie} are credentials, not debug data — never written raw.
 * The machine key keeps a 5-char prefix (enough to correlate a run of failures back to one key
 * without making the log itself a usable credential); the cookie is fully redacted.
 */
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    private static final int MAX_BODY_CHARS = 10_000;
    private static final int MACHINE_KEY_PREFIX_LEN = 5;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);
        long startNanos = System.nanoTime();
        try {
            filterChain.doFilter(wrappedRequest, response);
        } finally {
            long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;
            log.info("http request",
                    kv("method", request.getMethod()),
                    kv("path", request.getRequestURI()),
                    kv("status", response.getStatus()),
                    kv("latencyMs", latencyMs),
                    kv("headers", collectHeaders(wrappedRequest)),
                    kv("body", extractBody(wrappedRequest)));
        }
    }

    private Map<String, String> collectHeaders(HttpServletRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        if (names == null) {
            return headers;
        }
        for (String name : Collections.list(names)) {
            headers.put(name, redact(name, request.getHeader(name)));
        }
        return headers;
    }

    private String redact(String headerName, String value) {
        if (value == null) {
            return null;
        }
        switch (headerName.toLowerCase(Locale.ROOT)) {
            case "x-machine-key":
                String prefix = value.length() <= MACHINE_KEY_PREFIX_LEN ? value : value.substring(0, MACHINE_KEY_PREFIX_LEN);
                return prefix + "...(redacted)";
            case "cookie":
                return "(redacted)";
            default:
                return value;
        }
    }

    // Only JSON bodies are logged (the only payload shape this app's endpoints accept) — anything
    // else (empty GETs, the odd non-JSON content type) is skipped rather than dumped as raw bytes.
    private String extractBody(ContentCachingRequestWrapper request) {
        String contentType = request.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).contains("application/json")) {
            return null;
        }
        byte[] content = request.getContentAsByteArray();
        if (content.length == 0) {
            return null;
        }
        String encoding = request.getCharacterEncoding() != null ? request.getCharacterEncoding() : StandardCharsets.UTF_8.name();
        String body;
        try {
            body = new String(content, encoding);
        } catch (IOException e) {
            body = new String(content, StandardCharsets.UTF_8);
        }
        if (body.length() > MAX_BODY_CHARS) {
            return body.substring(0, MAX_BODY_CHARS) + "...(truncated)";
        }
        return body;
    }
}
