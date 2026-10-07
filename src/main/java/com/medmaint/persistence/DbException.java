package com.medmaint.persistence;

/** Erreur technique de base de données (détails journalisés, jamais montrés bruts à l'utilisateur). */
public class DbException extends RuntimeException {

    public DbException(String message, Throwable cause) {
        super(message, cause);
    }
}
