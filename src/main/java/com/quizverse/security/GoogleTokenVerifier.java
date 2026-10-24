package com.quizverse.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * Checks the ID token the "Sign in with Google" button hands the page: signed by Google's published keys,
 * issued by Google, meant for our client id, and not expired. Off (isEnabled false) when
 * quizverse.google-client-id is blank, so the app runs without a Google Cloud project.
 */
@Service
public class GoogleTokenVerifier {

    private static final String GOOGLE_KEYS = "https://www.googleapis.com/oauth2/v3/certs";
    private static final Set<String> ISSUERS = Set.of("https://accounts.google.com", "accounts.google.com");

    /** {@code subject} is the Google account's stable id; the email can change. */
    public record GoogleAccount(String subject, String email, boolean emailVerified, String name) {}

    private final String clientId;
    private final JwtDecoder decoder;

    public GoogleTokenVerifier(@Value("${quizverse.google-client-id:}") String clientId) {
        this.clientId = clientId.trim();
        if (this.clientId.isEmpty()) {
            this.decoder = null;
            return;
        }
        // The keys are fetched (and cached) on first use, so startup needs no network.
        NimbusJwtDecoder nimbus = NimbusJwtDecoder.withJwkSetUri(GOOGLE_KEYS).build();
        nimbus.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(),
                new JwtClaimValidator<Object>("iss", iss -> iss != null && ISSUERS.contains(String.valueOf(iss))),
                new JwtClaimValidator<List<String>>("aud", aud -> aud != null && aud.contains(this.clientId))));
        this.decoder = nimbus;
    }

    public boolean isEnabled() {
        return decoder != null;
    }

    /** The OAuth client id, which is public: the page needs it to show the button. Null when off. */
    public String clientId() {
        return isEnabled() ? clientId : null;
    }

    /** Throws JwtException for a forged, expired or foreign token. */
    public GoogleAccount verify(String idToken) {
        if (!isEnabled()) throw new IllegalStateException("Google sign-in isn't configured");
        Jwt jwt = decoder.decode(idToken);
        Object verified = jwt.getClaims().get("email_verified");
        String email = jwt.getClaimAsString("email");
        if (jwt.getSubject() == null) throw new JwtException("Token has no subject");
        return new GoogleAccount(jwt.getSubject(), email,
                Boolean.TRUE.equals(verified) || "true".equals(verified), jwt.getClaimAsString("name"));
    }
}
