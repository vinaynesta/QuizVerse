package com.quizverse.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

/**
 * Signed tokens that prove when a timed quiz started and what was answered when it finished, so the time limit
 * is checked on the server without storing anything. A start token is "quizKey|startMillis" plus an HMAC; a
 * receipt binds the quiz, start, finish and the chosen options. Both are signed with QUIZVERSE_SIGNING_KEY,
 * or a random key made at startup when it isn't set (a redeploy then leaves running quizzes unranked).
 */
@Component
public class QuizTimer {

    private static final Logger log = LoggerFactory.getLogger(QuizTimer.class);
    /** Slack for slow networks and the auto-submit at zero. */
    public static final long GRACE_MILLIS = 90_000;

    public record Start(String quizKey, long startMillis) {}

    private final byte[] key;
    private final Clock clock;

    public QuizTimer(@Value("${quizverse.signing-key:}") String signingKey, Clock clock) {
        this.clock = clock;
        if (signingKey.isBlank()) {
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            this.key = random;
            log.warn("QUIZVERSE_SIGNING_KEY is not set; quizzes running during a restart won't be ranked");
        } else {
            this.key = signingKey.getBytes(StandardCharsets.UTF_8);
        }
    }

    public String start(String quizKey) {
        String body = quizKey + "|" + clock.millis();
        return encode(body) + "." + sign("start|" + body);
    }

    /** The start in a token this server signed, or null. */
    public Start readStart(String token) {
        String body = verify(token, "start|");
        if (body == null) return null;
        int bar = body.lastIndexOf('|');
        try {
            return new Start(body.substring(0, bar), Long.parseLong(body.substring(bar + 1)));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** True when now is within the quiz's time (plus grace) from the start. */
    public boolean onTime(Start start, int minutes) {
        long elapsed = clock.millis() - start.startMillis();
        return elapsed >= 0 && elapsed <= minutes * 60_000L + GRACE_MILLIS;
    }

    public String receipt(String quizKey, long startMillis, List<Integer> chosen) {
        String body = quizKey + "|" + startMillis + "|" + clock.millis();
        return encode(body) + "." + sign("receipt|" + body + "|" + chosen);
    }

    /** Receipts are for saving the result straight away (or after logging in), not for keeping. */
    static final long RECEIPT_LIFETIME_MILLIS = 24 * 60 * 60 * 1000L;

    /** True when the receipt was signed here for exactly this quiz and these answers, within the last day. */
    public boolean checkReceipt(String receipt, String quizKey, List<Integer> chosen) {
        if (receipt == null) return false;
        int dot = receipt.indexOf('.');
        if (dot < 0) return false;
        String body;
        try {
            body = new String(Base64.getUrlDecoder().decode(receipt.substring(0, dot)), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (!body.startsWith(quizKey + "|")) return false;
        try {
            long finished = Long.parseLong(body.substring(body.lastIndexOf('|') + 1));
            if (clock.millis() - finished > RECEIPT_LIFETIME_MILLIS) return false;
        } catch (NumberFormatException e) {
            return false;
        }
        return MessageDigest.isEqual(sign("receipt|" + body + "|" + chosen).getBytes(StandardCharsets.UTF_8),
                receipt.substring(dot + 1).getBytes(StandardCharsets.UTF_8));
    }

    private String verify(String token, String purpose) {
        if (token == null || token.length() > 400) return null;
        int dot = token.indexOf('.');
        if (dot < 0) return null;
        try {
            String body = new String(Base64.getUrlDecoder().decode(token.substring(0, dot)), StandardCharsets.UTF_8);
            boolean ok = MessageDigest.isEqual(sign(purpose + body).getBytes(StandardCharsets.UTF_8),
                    token.substring(dot + 1).getBytes(StandardCharsets.UTF_8));
            return ok ? body : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String encode(String body) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(body.getBytes(StandardCharsets.UTF_8));
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(Objects.requireNonNull(data).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
