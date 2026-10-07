package com.medmaint.persistence;

import com.medmaint.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Gestionnaire de connexion H2 unique (application monoposte).
 * La connexion est ouverte paresseusement et refermée par {@link #close()}.
 */
public final class Db {

    private static final Logger log = LoggerFactory.getLogger(Db.class);

    private static Connection connection;
    private static String overrideUrl;
    private static String overrideUser;
    private static String overridePassword;

    private Db() {
    }

    /** Permet aux tests d'utiliser une base en mémoire distincte. */
    public static synchronized void override(String url, String user, String password) {
        overrideUrl = url;
        overrideUser = user;
        overridePassword = password;
        closeQuietly();
    }

    public static synchronized Connection get() {
        if (connection == null || isClosed(connection)) {
            AppConfig cfg = AppConfig.get();
            String url = overrideUrl != null ? overrideUrl : cfg.dbUrl();
            String user = overrideUser != null ? overrideUser : cfg.dbUser();
            String pass = overridePassword != null ? overridePassword : cfg.dbPassword();
            try {
                connection = DriverManager.getConnection(url, user, pass);
                connection.setAutoCommit(true);
                log.debug("Connexion base ouverte : {}", url.contains("mem:") ? "mem" : "fichier");
            } catch (SQLException e) {
                throw new DbException("Impossible de se connecter à la base de données.", e);
            }
        }
        return connection;
    }

    private static boolean isClosed(Connection c) {
        try {
            return c == null || c.isClosed();
        } catch (SQLException e) {
            return true;
        }
    }

    public static synchronized void close() {
        closeQuietly();
    }

    private static synchronized void closeQuietly() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                log.warn("Fermeture connexion échouée : {}", e.getMessage());
            }
            connection = null;
        }
    }
}
