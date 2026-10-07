package com.medmaint.security;

/**
 * Erreur de validation métier détectée dans la couche service.
 * Le message est destiné à l'utilisateur (français) ; les détails techniques
 * restent dans les logs.
 */
public class ValidationException extends RuntimeException {

    public ValidationException(String message) {
        super(message);
    }

    public ValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
