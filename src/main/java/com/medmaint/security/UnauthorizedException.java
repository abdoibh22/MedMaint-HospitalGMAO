package com.medmaint.security;

/** Refus d'accès issu de la couche service (autorisation côté « backend »). */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
