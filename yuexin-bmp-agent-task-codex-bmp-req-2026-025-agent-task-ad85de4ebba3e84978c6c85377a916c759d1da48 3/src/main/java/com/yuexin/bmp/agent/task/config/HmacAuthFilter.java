package com.yuexin.bmp.agent.task.config;

import com.yuexin.bmp.agent.task.AgentTaskProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class HmacAuthFilter extends OncePerRequestFilter {
    private final AgentTaskProperties properties;
    private final ConcurrentHashMap<String, Long> seenNonces = new ConcurrentHashMap<>();

    public HmacAuthFilter(AgentTaskProperties properties) {
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.startsWith("/internal/") || path.startsWith("/admin/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("X-Request-Id", request.getHeader("X-Request-Id")==null?"":request.getHeader("X-Request-Id"));
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int read;
        while ((read = request.getInputStream().read(chunk)) >= 0) {
            buffer.write(chunk, 0, read);
        }
        CachedBodyRequestWrapper wrapped = new CachedBodyRequestWrapper(request, buffer.toByteArray());

        String principal = requiredHeader(wrapped, "X-Agent-Key", response);
        String timestamp = requiredHeader(wrapped, "X-Agent-Timestamp", response);
        String nonce = requiredHeader(wrapped, "X-Agent-Nonce", response);
        String signature = requiredHeader(wrapped, "X-Agent-Signature", response);
        if (principal == null || timestamp == null || nonce == null || signature == null) {
            return;
        }

        String secret = properties.getPrincipalSecrets().get(principal);
        if (wrapped.getRequestURI().startsWith("/internal/profit-alert/")) {
            if (secret == null || secret.isEmpty()) { reject(response,401,"UNAUTHENTICATED"); return; }
            String requestId = wrapped.getHeader("X-Request-Id");
            if(requestId==null || requestId.trim().isEmpty()){reject(response,400,"INVALID_ARGUMENT");return;}
            wrapped.setAttribute("profit.requestId",requestId);
        } else if(secret==null){ secret=properties.getHmacSecret(); }
        if (secret == null || secret.isEmpty()) {
            reject(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "hmac secret is not configured");
            return;
        }

        long timestampValue;
        try {
            timestampValue = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "invalid timestamp");
            return;
        }
        long now = Instant.now().getEpochSecond();
        if (Math.abs(now - timestampValue) > properties.getTimestampWindowSeconds()) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "timestamp is outside allowed window");
            return;
        }

        boolean signatureValid;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bodyHash = digest.digest(wrapped.getBody());
            String canonical = wrapped.getMethod() + "\n" + wrapped.getRequestURI() + "\n" + timestamp + "\n"
                    + nonce + "\n" + toHex(bodyHash);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8));
            byte[] actual = parseHex(signature);
            signatureValid = MessageDigest.isEqual(expected, actual);
        } catch (Exception e) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "signature verification failed");
            return;
        }
        if (!signatureValid) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "invalid signature");
            return;
        }

        String nonceKey = principal + ":" + nonce;
        Long previousTimestamp = seenNonces.putIfAbsent(nonceKey, timestampValue);
        if (previousTimestamp != null) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "nonce already used");
            return;
        }

        String path = wrapped.getRequestURI();
        boolean allowed;
        if (path.startsWith("/internal/")) {
            allowed = properties.getAgentPrincipals().contains(principal);
        } else if (path.startsWith("/admin/")) {
            allowed = properties.getAdminPrincipals().contains(principal);
        } else {
            allowed = false;
        }
        if (!allowed) {
            reject(response, HttpServletResponse.SC_FORBIDDEN, "principal is not allowed for this path");
            return;
        }

        seenNonces.entrySet().removeIf(entry -> now - entry.getValue() > properties.getTimestampWindowSeconds());
        wrapped.setAttribute("authenticatedPrincipal",principal);
        chain.doFilter(wrapped, response);
    }

    private String requiredHeader(HttpServletRequest request, String name, HttpServletResponse response) {
        String value = request.getHeader(name);
        if (value == null || value.trim().isEmpty()) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "missing header " + name);
            return null;
        }
        return value;
    }

    private void reject(HttpServletResponse response, int status, String message) {
        try {
            response.setStatus(status);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            java.util.Map<String,Object> body = new java.util.LinkedHashMap<>();
            body.put("code", status==401?"UNAUTHENTICATED":status==403?"FORBIDDEN":status>=500?"SOURCE_UNAVAILABLE":"INVALID_ARGUMENT");
            body.put("message", message); body.put("requestId", response.getHeader("X-Request-Id")); body.put("retryable", status>=500);
            response.getWriter().write(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(body));
        } catch (IOException ignored) {
            // Response is already committed or unavailable.
        }
    }

    private static String toHex(byte[] value) {
        StringBuilder builder = new StringBuilder(value.length * 2);
        for (byte b : value) {
            builder.append(Character.forDigit((b >> 4) & 0x0f, 16));
            builder.append(Character.forDigit(b & 0x0f, 16));
        }
        return builder.toString();
    }

    private static byte[] parseHex(String value) {
        if (value.length() % 2 != 0) {
            throw new IllegalArgumentException("invalid hex length");
        }
        byte[] result = new byte[value.length() / 2];
        for (int i = 0; i < result.length; i++) {
            int high = Character.digit(value.charAt(i * 2), 16);
            int low = Character.digit(value.charAt(i * 2 + 1), 16);
            if (high < 0 || low < 0) {
                throw new IllegalArgumentException("invalid hex character");
            }
            result[i] = (byte) ((high << 4) | low);
        }
        return result;
    }
}
