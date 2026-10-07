package com.medmaint.service;

import com.medmaint.domain.model.User;
import com.medmaint.persistence.repo.UserRepo;
import com.medmaint.security.PasswordHasher;
import com.medmaint.security.Role;
import com.medmaint.security.Session;
import com.medmaint.security.UnauthorizedException;
import com.medmaint.security.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Authentification et gestion des comptes.
 * - vérification d'identité (PBKDF2)
 * - contrôle de compte actif
 * - création / mise à jour d'utilisateurs avec validations
 * - changement de mot de passe
 */
public final class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final Pattern EMAIL = Pattern.compile("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$");

    private final UserRepo repo = new UserRepo();
    private final AuditService audit;
    private final NotificationService notifications;

    public AuthService(AuditService audit, NotificationService notifications) {
        this.audit = audit;
        this.notifications = notifications;
    }

    /**
     * Authentifie un utilisateur et ouvre la session.
     * @throws UnauthorizedException identifiants invalides ou compte désactivé
     */
    public Session login(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isEmpty()) {
            throw new UnauthorizedException("Veuillez saisir votre identifiant et votre mot de passe.");
        }
        Optional<User> found = repo.findByUsername(username.trim())
                .or(() -> repo.findByEmail(username.trim()));
        User u = found.orElse(null);
        // Comparaison systématique pour éviter d'indiquer quel identifiant existe.
        boolean ok = u != null && PasswordHasher.verify(password, u.getPasswordHash());
        if (!ok || u == null) {
            throw new UnauthorizedException("Identifiant ou mot de passe incorrect.");
        }
        if (!u.isActive()) {
            throw new UnauthorizedException("Votre compte est désactivé. Contactez l'administrateur.");
        }
        repo.updateLastLogin(u.getId());
        Session s = Session.create(u.getId(), u.getUsername(), u.getFullName(), u.roleEnum(),
                u.getDepartmentId(), u.getDepartmentName());
        audit.login();
        log.info("Connexion de {} ({})", u.getUsername(), u.roleEnum().name());
        return s;
    }

    public void logout() {
        Session s = Session.get();
        if (s != null) {
            audit.record("LOGOUT", "Session", null, s.username(), null, null);
        }
        Session.clear();
    }

    public Optional<User> findById(long id) {
        return repo.findById(id);
    }

    public List<User> all() {
        return repo.search(null, null, null, null, 1, 1000, "fullName", "ASC").items();
    }

    // ---------- Gestion des comptes (admin) ----------

    public User create(User u, String rawPassword) {
        Session.get().require(com.medmaint.security.Permission.USER_MANAGE);
        validate(u, rawPassword, null);
        if (repo.findByUsername(u.getUsername()).isPresent()) {
            throw new ValidationException("Cet identifiant existe déjà.");
        }
        if (repo.findByEmail(u.getEmail()).isPresent()) {
            throw new ValidationException("Cette adresse e-mail est déjà utilisée.");
        }
        u.setPasswordHash(PasswordHasher.hash(rawPassword));
        if (u.getStatus() == null) u.setStatus("ACTIF");
        if (u.getNotificationPrefs() == null) u.setNotificationPrefs("ALL");
        long id = repo.insert(u);
        u.setId(id);
        audit.created("Utilisateur", String.valueOf(id), u.getFullName() + " (" + u.getRole() + ")");
        return u;
    }

    public void update(User u) {
        Session.get().require(com.medmaint.security.Permission.USER_MANAGE);
        validate(u, null, u.getId());
        if (repo.findByEmail(u.getEmail())
                .filter(existing -> existing.getId() != u.getId()).isPresent()) {
            throw new ValidationException("Cette adresse e-mail est déjà utilisée.");
        }
        User before = repo.findById(u.getId()).orElse(null);
        repo.update(u);
        audit.updated("Utilisateur", String.valueOf(u.getId()), u.getFullName(),
                before != null ? before.getRole() : null, u.getRole());
    }

    public void changePassword(long userId, String current, String next) {
        User u = repo.findById(userId)
                .orElseThrow(() -> new ValidationException("Utilisateur introuvable."));
        Session s = Session.get();
        boolean self = s != null && s.userId() == userId;
        boolean admin = s != null && s.isAdmin();
        if (!self && !admin) {
            throw new UnauthorizedException("Seul l'administrateur peut modifier le mot de passe d'un autre compte.");
        }
        if (!admin) {
            if (current == null || !PasswordHasher.verify(current, u.getPasswordHash())) {
                throw new ValidationException("Mot de passe actuel incorrect.");
            }
        }
        if (next == null || next.length() < 8) {
            throw new ValidationException("Le nouveau mot de passe doit contenir au moins 8 caractères.");
        }
        repo.updatePassword(userId, PasswordHasher.hash(next));
        audit.record("PASSWORD_CHANGE", "Utilisateur", String.valueOf(userId), u.getUsername(), null, null);
    }

    public void resetPassword(long userId, String newPassword) {
        Session.get().require(com.medmaint.security.Permission.USER_MANAGE);
        if (newPassword == null || newPassword.length() < 8) {
            throw new ValidationException("Le mot de passe doit contenir au moins 8 caractères.");
        }
        repo.updatePassword(userId, PasswordHasher.hash(newPassword));
        audit.record("PASSWORD_RESET", "Utilisateur", String.valueOf(userId), null, null, null);
    }

    public void delete(long userId) {
        Session s = Session.get();
        s.require(com.medmaint.security.Permission.USER_MANAGE);
        if (s.userId() == userId) {
            throw new ValidationException("Vous ne pouvez pas supprimer votre propre compte.");
        }
        User u = repo.findById(userId).orElse(null);
        if (u != null && u.isDemo()) {
            throw new ValidationException("Les comptes de démonstration ne peuvent pas être supprimés.");
        }
        if (u != null && "ADMIN".equals(u.getRole()) && repo.countByRole("ADMIN") <= 1) {
            throw new ValidationException("Impossible de supprimer le dernier administrateur.");
        }
        repo.delete(userId);
        audit.deleted("Utilisateur", String.valueOf(userId), u != null ? u.getFullName() : null, null);
    }

    public void setStatus(long userId, String status) {
        Session.get().require(com.medmaint.security.Permission.USER_MANAGE);
        User u = repo.findById(userId)
                .orElseThrow(() -> new ValidationException("Utilisateur introuvable."));
        if ("SUSPENDU".equals(status) && Session.get().userId() == userId) {
            throw new ValidationException("Vous ne pouvez pas suspendre votre propre compte.");
        }
        User before = repo.findById(userId).orElse(null);
        u.setStatus(status);
        repo.update(u);
        audit.updated("Utilisateur", String.valueOf(userId), u.getFullName(),
                before != null ? before.getStatus() : null, status);
    }

    private void validate(User u, String password, Long excludeId) {
        if (u.getFullName() == null || u.getFullName().isBlank()) {
            throw new ValidationException("Le nom complet est obligatoire.");
        }
        if (u.getUsername() == null || u.getUsername().isBlank() || u.getUsername().length() < 3) {
            throw new ValidationException("L'identifiant doit contenir au moins 3 caractères.");
        }
        if (u.getEmail() == null || !EMAIL.matcher(u.getEmail()).matches()) {
            throw new ValidationException("Adresse e-mail invalide.");
        }
        if (password != null && password.length() < 8) {
            throw new ValidationException("Le mot de passe doit contenir au moins 8 caractères.");
        }
        if (u.getRole() == null) {
            throw new ValidationException("Le rôle est obligatoire.");
        }
        try {
            Role.fromCode(u.getRole());
        } catch (RuntimeException e) {
            throw new ValidationException("Rôle inconnu : " + u.getRole());
        }
    }

    public UserRepo repo() {
        return repo;
    }

    public LocalDateTime now() {
        return LocalDateTime.now();
    }
}
