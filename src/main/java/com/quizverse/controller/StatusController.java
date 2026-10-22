package com.quizverse.controller;

import com.quizverse.config.StorageMode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public facts about this server for every page: whether saved data is only temporary (test mode). */
@RestController
public class StatusController {

    private final StorageMode storage;

    public StatusController(StorageMode storage) {
        this.storage = storage;
    }

    public record StatusResponse(boolean temporaryStorage) {}

    @GetMapping("/api/status")
    public StatusResponse status() {
        return new StatusResponse(storage.isTemporary());
    }
}
