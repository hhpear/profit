package com.yuexin.bmp.agent.task;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class HmacAuthFilterTest {
    @Autowired private MockMvc mockMvc;

    @Test
    void rejectsInvalidSignature() throws Exception {
        mockMvc.perform(post("/internal/not-exist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Agent-Key", "customer-billing-agent")
                        .header("X-Agent-Timestamp", String.valueOf(Instant.now().getEpochSecond()))
                        .header("X-Agent-Nonce", "nonce")
                        .header("X-Agent-Signature", "00112233"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsAgentPrincipalOnAdminPath() throws Exception {
        String body = "{}";
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String nonce = UUID.randomUUID().toString();
        String signature = sign("POST", "/admin/not-exist", timestamp, nonce, body, "test-secret");

        mockMvc.perform(post("/admin/not-exist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("X-Agent-Key", "customer-billing-agent")
                        .header("X-Agent-Timestamp", timestamp)
                        .header("X-Agent-Nonce", nonce)
                        .header("X-Agent-Signature", signature))
                .andExpect(status().isForbidden());
    }

    @Test
    void allowsAgentPrincipalOnInternalPath() throws Exception {
        String body = "{}";
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String nonce = UUID.randomUUID().toString();
        String signature = sign("POST", "/internal/not-exist", timestamp, nonce, body, "test-secret");

        MvcResult result = mockMvc.perform(post("/internal/not-exist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("X-Agent-Key", "customer-billing-agent")
                        .header("X-Agent-Timestamp", timestamp)
                        .header("X-Agent-Nonce", nonce)
                        .header("X-Agent-Signature", signature))
                .andReturn();

        assertNotEquals(401, result.getResponse().getStatus());
        assertNotEquals(403, result.getResponse().getStatus());
        assertNotEquals(500, result.getResponse().getStatus());
    }

    @Test
    void allowsAdminPrincipalOnAdminPath() throws Exception {
        String body = "{}";
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String nonce = UUID.randomUUID().toString();
        String signature = sign("POST", "/admin/not-exist", timestamp, nonce, body, "test-secret");

        MvcResult result = mockMvc.perform(post("/admin/not-exist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("X-Agent-Key", "bmp-admin")
                        .header("X-Agent-Timestamp", timestamp)
                        .header("X-Agent-Nonce", nonce)
                        .header("X-Agent-Signature", signature))
                .andReturn();

        assertNotEquals(401, result.getResponse().getStatus());
        assertNotEquals(403, result.getResponse().getStatus());
        assertNotEquals(500, result.getResponse().getStatus());
    }

    @Test
    void rejectsReplayedNonce() throws Exception {
        String body = "{}";
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String nonce = UUID.randomUUID().toString();
        String signature = sign("POST", "/internal/not-exist", timestamp, nonce, body, "test-secret");

        mockMvc.perform(post("/internal/not-exist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("X-Agent-Key", "customer-billing-agent")
                        .header("X-Agent-Timestamp", timestamp)
                        .header("X-Agent-Nonce", nonce)
                        .header("X-Agent-Signature", signature))
                .andReturn();

        mockMvc.perform(post("/internal/not-exist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("X-Agent-Key", "customer-billing-agent")
                        .header("X-Agent-Timestamp", timestamp)
                        .header("X-Agent-Nonce", nonce)
                        .header("X-Agent-Signature", signature))
                .andExpect(status().isUnauthorized());
    }

    private String sign(String method, String uri, String timestamp, String nonce, String body, String secret) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] bodyHash = digest.digest(body.getBytes(StandardCharsets.UTF_8));
        String canonical = method + "\n" + uri + "\n" + timestamp + "\n" + nonce + "\n" + toHex(bodyHash);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return toHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
    }

    private String toHex(byte[] value) {
        StringBuilder builder = new StringBuilder(value.length * 2);
        for (byte b : value) {
            builder.append(Character.forDigit((b >> 4) & 0x0f, 16));
            builder.append(Character.forDigit(b & 0x0f, 16));
        }
        return builder.toString();
    }
}
