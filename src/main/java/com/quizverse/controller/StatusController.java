package com.quizverse.controller;

import com.quizverse.config.StorageMode;
import com.quizverse.security.GoogleTokenVerifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public facts about this server for every page: whether saved data is only temporary (test mode), and the
 * Google OAuth client id for the sign-in button (null when Google sign-in is off).
 */
@RestController
public class StatusController {

    private final StorageMode storage;
    private final GoogleTokenVerifier google;

    public StatusController(StorageMode storage, GoogleTokenVerifier google) {
        this.storage = storage;
        this.google = google;
    }

    public record StatusResponse(boolean temporaryStorage, String googleClientId) {}

    @GetMapping("/api/status")
    public StatusResponse status() {
        return new StatusResponse(storage.isTemporary(), google.clientId());
    }
}
