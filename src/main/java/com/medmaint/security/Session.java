package com.medmaint.security;

import java.util.Collections;
import java.util.Set;

/**
 * Session utilisateur courante (mémoire de l'application).
 * Toute action sensible doit passer par {@link #require(Permission)} :
 * l'autorisation est ainsi appliquée dans la couche service, pas seulement dans l'UI.
 */
public final class Session {

    private static volatile Session current;

    private final long userId;
    private final String username;
    private final String fullName;
    private final Role role;
    private final Long departmentId;
    private final String departmentName;

    private Session(long userId, String username, String fullName, Role role,
                    Long departmentId, String departmentName) {
        this.userId = userId;
        this.username = username;
        this.fullName = fullName;
        this.role = role;
        this.departmentId = departmentId;
        this.departmentName = departmentName;
    }

    public static Session create(long userId, String username, String fullName, Role role,
                                 Long departmentId, String departmentName) {
        Session s = new Session(userId, username, fullName, role, departmentId, departmentName);
        current = s;
        return s;
    }

    public static Session get() {
        return current;
    }

    public static boolean isLoggedIn() {
        return current != null;
    }

    public static void clear() {
        current = null;
    }

    /** Vérifie la permission ; lève {@link UnauthorizedException} sinon. */
    public void require(Permission p) {
        if (!has(p)) {
            throw new UnauthorizedException(
                    "Accès refusé : vous n'avez pas la permission « " + p.label() + " ».");
        }
    }

    public boolean has(Permission p) {
        return role.has(p);
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    public long userId() {
        return userId;
    }

    public String username() {
        return username;
    }

    public String fullName() {
        return fullName;
    }

    public Role role() {
        return role;
    }

    public String roleLabel() {
        return role.label();
    }

    public Long departmentId() {
        return departmentId;
    }

    public String departmentName() {
        return departmentName;
    }

    public Set<Permission> permissions() {
        return Collections.unmodifiableSet(role.permissions());
    }
}
