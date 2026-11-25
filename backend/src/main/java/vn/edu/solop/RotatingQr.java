package vn.edu.solop;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

final class RotatingQr {
    static final long INTERVAL = 20_000;
    private static final long GRACE = 10_000;

    private RotatingQr() {}

    static String current(String secret, long session, long now) {
        return token(secret, session, now / INTERVAL);
    }

    static long nextRefresh(long now) {
        return (now / INTERVAL + 1) * INTERVAL;
    }

    static boolean matches(String secret, long session, long now, String supplied) {
        if (secret == null || supplied == null || !supplied.matches("[A-Za-z0-9_-]{43}")) return false;
        long bucket = now / INTERVAL;
        return equal(token(secret, session, bucket), supplied)
                || (now % INTERVAL < GRACE && equal(token(secret, session, bucket - 1), supplied));
    }

    private static String token(String secret, long session, long bucket) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(Base64.getUrlDecoder().decode(secret), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal((session + ":" + bucket).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", error);
        }
    }

    private static boolean equal(String expected, String supplied) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                supplied.getBytes(StandardCharsets.US_ASCII));
    }
}