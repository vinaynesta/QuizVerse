package com.quizverse.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoogleTokenVerifierTest {

    @Test
    void offWithoutClientId() {
        GoogleTokenVerifier verifier = new GoogleTokenVerifier("  ");
        assertFalse(verifier.isEnabled());
        assertNull(verifier.clientId());
        assertThrows(IllegalStateException.class, () -> verifier.verify("anything"));
    }

    @Test
    void rejectsTokensThatArentSignedJwts() {
        GoogleTokenVerifier verifier = new GoogleTokenVerifier("abc.apps.googleusercontent.com");
        assertTrue(verifier.isEnabled());
        assertEquals("abc.apps.googleusercontent.com", verifier.clientId());
        // Unsigned ("alg":"none") token claiming to be Google: rejected before any key lookup.
        String unsigned = "eyJhbGciOiJub25lIn0.eyJpc3MiOiJodHRwczovL2FjY291bnRzLmdvb2dsZS5jb20iLCJzdWIiOiIxIn0.";
        assertThrows(JwtException.class, () -> verifier.verify(unsigned));
        assertThrows(JwtException.class, () -> verifier.verify("not-a-jwt"));
    }
}
