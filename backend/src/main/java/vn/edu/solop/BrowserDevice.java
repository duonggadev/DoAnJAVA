package vn.edu.solop;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
class BrowserDevice {
    private final byte[] key;
    private final boolean secure;

    BrowserDevice(@Value("${solop.device-key:}") String configured,
                  @Value("${server.servlet.session.cookie.secure:false}") boolean secure) {
        this.secure = secure;
        if (configured.isBlank()) {
            key = new byte[32];
            new SecureRandom().nextBytes(key);
        } else {
            key = Base64.getUrlDecoder().decode(configured);
            if (key.length < 32) throw new IllegalArgumentException("Device key must contain at least 32 bytes");
        }
    }

    String identify(HttpServletRequest request, HttpServletResponse response) {
        if (request.getCookies() != null) {
            for (var cookie : request.getCookies()) {
                if (!cookie.getName().equals("SL_DEVICE")) continue;
                String value = cookie.getValue();
                if (!value.matches("[a-f0-9-]{36}\\.[a-f0-9]{64}")) continue;
                String id = value.substring(0, 36);
                if (MessageDigest.isEqual(value.substring(37).getBytes(StandardCharsets.US_ASCII),
                        digest("cookie:" + id).getBytes(StandardCharsets.US_ASCII))) return id;
            }
        }
        String id = UUID.randomUUID().toString();
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from("SL_DEVICE", id + "." + digest("cookie:" + id))
                .httpOnly(true).secure(secure).sameSite("Strict").path("/api").maxAge(java.time.Duration.ofDays(30))
                .build().toString());
        return id;
    }

    String hashForSession(String device, long session) {
        return digest("session:" + session + ":" + device);
    }

    private String digest(String value) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException(exception);
        }
    }
}