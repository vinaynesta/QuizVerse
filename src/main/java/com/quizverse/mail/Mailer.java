package com.quizverse.mail;

import com.quizverse.config.StorageMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sends plain-text email through Resend's HTTP API (https://resend.com/docs/api-reference/emails/send-email).
 * Off when RESEND_API_KEY or MAIL_FROM is blank. Then, in local test mode only (H2), the message is written to
 * the log so the forgot-password flow can be tried without a provider; in production nothing is logged, because
 * the body can contain a reset link.
 */
@Service
public class Mailer {

    private static final Logger log = LoggerFactory.getLogger(Mailer.class);
    private static final String RESEND_URL = "https://api.resend.com/emails";

    private final String apiKey;
    private final String from;
    private final boolean logInstead;
    private final RestClient http;

    public Mailer(@Value("${quizverse.mail.resend-api-key:}") String apiKey,
                  @Value("${quizverse.mail.from:}") String from,
                  StorageMode storage, RestClient.Builder http) {
        this.apiKey = apiKey.trim();
        this.from = from.trim();
        this.logInstead = storage.isTemporary();
        this.http = http.build();
    }

    public boolean isEnabled() {
        return !apiKey.isEmpty() && !from.isEmpty();
    }

    /** True when the message was handed to the provider (or logged in test mode). Never throws. */
    public boolean send(String to, String subject, String text, Map<String, String> headers) {
        if (!isEnabled()) {
            if (logInstead) log.info("Email is off (test mode). Would send to {}: {}\n{}", to, subject, text);
            else log.warn("Email is off (RESEND_API_KEY / MAIL_FROM not set); not sending \"{}\"", subject);
            return logInstead;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("from", from);
        body.put("to", new String[] {to});
        body.put("subject", subject);
        body.put("text", text);
        if (headers != null && !headers.isEmpty()) body.put("headers", headers);
        try {
            http.post().uri(RESEND_URL)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientException e) {
            // The message only: it doesn't include the API key or the email body.
            log.error("Sending \"{}\" failed: {}", subject, e.getMessage());
            return false;
        }
    }

    public boolean send(String to, String subject, String text) {
        return send(to, subject, text, null);
    }
}
