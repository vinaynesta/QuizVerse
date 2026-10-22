package com.quizverse.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageModeTest {

    @Test
    void h2IsTemporaryPostgresIsNot() {
        assertTrue(StorageMode.isTemporary("jdbc:h2:file:./data/quizverse;AUTO_SERVER=TRUE"));
        assertFalse(StorageMode.isTemporary("jdbc:postgresql://db.example.com:5432/quizverse?sslmode=require"));
    }

    @Test
    void h2OnRailwayRefusesToStart() {
        assertThrows(IllegalStateException.class, () -> StorageMode.check(true, "production"));
    }

    @Test
    void h2ElsewhereAndPostgresOnRailwayStart() {
        assertDoesNotThrow(() -> StorageMode.check(true, null));
        assertDoesNotThrow(() -> StorageMode.check(true, ""));
        assertDoesNotThrow(() -> StorageMode.check(false, "production"));
    }
}
