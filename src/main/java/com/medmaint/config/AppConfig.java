package com.medmaint.config;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Configuration applicative. Les secrets et chemins sont lus dans les variables
 * d'environnement — aucun secret en dur dans le code.
 *
 * Variables supportées :
 *   MEDMAINT_DB_URL       URL JDBC H2 (défaut : fichier local ./data/medmaint)
 *   MEDMAINT_DB_USER      utilisateur base (défaut : sa)
 *   MEDMAINT_DB_PASSWORD  mot de passe base (défaut : vide)
 *   MEDMAINT_UPLOAD_DIR   dossier des documents (défaut : ./uploads)
 *   MEDMAINT_MAX_UPLOAD   taille max upload en octets (défaut : 10 Mo)
 */
public final class AppConfig {

    public static final String APP_NAME = "MedMaint";
    public static final String APP_SUBTITLE = "GMAO — Équipements biomédicaux";

    private static final AppConfig INSTANCE = new AppConfig();

    private final String dbUrl;
    private final String dbUser;
    private final String dbPassword;
    private final Path uploadDir;
    private final Path exportDir;
    private final Path logDir;
    private final long maxUploadBytes;

    private AppConfig() {
        this.dbUrl = env("MEDMAINT_DB_URL", "jdbc:h2:file:./data/medmaint;DB_CLOSE_DELAY=-1;AUTO_SERVER=FALSE");
        this.dbUser = env("MEDMAINT_DB_USER", "sa");
        this.dbPassword = env("MEDMAINT_DB_PASSWORD", "");
        this.uploadDir = Paths.get(env("MEDMAINT_UPLOAD_DIR", "./uploads"));
        this.exportDir = Paths.get(env("MEDMAINT_EXPORT_DIR", "./exports"));
        this.logDir = Paths.get(env("MEDMAINT_LOG_DIR", "./logs"));
        this.maxUploadBytes = Long.parseLong(env("MEDMAINT_MAX_UPLOAD", String.valueOf(10L * 1024 * 1024)));
    }

    public static AppConfig get() {
        return INSTANCE;
    }

    private static String env(String key, String def) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? def : v.trim();
    }

    public String dbUrl() {
        return dbUrl;
    }

    public String dbUser() {
        return dbUser;
    }

    public String dbPassword() {
        return dbPassword;
    }

    public Path uploadDir() {
        return uploadDir;
    }

    public Path exportDir() {
        return exportDir;
    }

    public Path logDir() {
        return logDir;
    }

    public long maxUploadBytes() {
        return maxUploadBytes;
    }
}
