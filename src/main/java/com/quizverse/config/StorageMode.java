package com.quizverse.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Whether accounts live in the H2 file database (local development) or PostgreSQL. Without the prod profile
 * the app quietly uses H2, which on Railway sits on the container's disk and is wiped by every deploy. So on
 * Railway (it sets RAILWAY_ENVIRONMENT_NAME) H2 stops the app from starting; elsewhere pages show a
 * "test mode" strip via GET /api/status.
 */
@Component
public class StorageMode {

    private final boolean temporary;

    public StorageMode(@Value("${spring.datasource.url}") String datasourceUrl, Environment env) {
        this.temporary = isTemporary(datasourceUrl);
        check(temporary, env.getProperty("RAILWAY_ENVIRONMENT_NAME"));
    }

    public boolean isTemporary() {
        return temporary;
    }

    static boolean isTemporary(String datasourceUrl) {
        return datasourceUrl.startsWith("jdbc:h2:");
    }

    static void check(boolean temporary, String railwayEnvironment) {
        if (temporary && railwayEnvironment != null && !railwayEnvironment.isBlank()) {
            throw new IllegalStateException("Running on Railway with the H2 database, which every deploy wipes. "
                    + "Set SPRING_PROFILES_ACTIVE=prod and the PG* variables on the service (see README, Configuration).");
        }
    }
}
