package com.yuexin.bmp.agent.runtime.config;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class HmacSigner {
    private HmacSigner() { }

    public static String sign(String method, String path, String timestamp, String nonce, byte[] body, String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bodyHash = digest.digest(body == null ? new byte[0] : body);
            String canonical = method + "\n" + path + "\n" + timestamp + "\n" + nonce + "\n" + toHex(bodyHash);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return toHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("cannot sign request", e);
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
}
