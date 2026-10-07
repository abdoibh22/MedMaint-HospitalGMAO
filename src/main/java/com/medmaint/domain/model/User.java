package com.medmaint.domain.model;

import com.medmaint.security.Role;

import java.time.LocalDateTime;

/** Compte utilisateur de l'application. */
public class User {

    private long id;
    private String username;
    private String email;
    private String passwordHash;
    private String fullName;
    private String role;
    private Long departmentId;
    private String departmentName; // affichage (jointure)
    private String phone;
    private String status;          // ACTIF | SUSPENDU
    private boolean demo;
    private String notificationPrefs; // liste de types, ou ALL
    private LocalDateTime lastLogin;
    private LocalDateTime createdAt;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public Role roleEnum() { return Role.fromCode(role); }

    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }

    public String getDepartmentName() { return departmentName; }
    public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public boolean isDemo() { return demo; }
    public void setDemo(boolean demo) { this.demo = demo; }

    public String getNotificationPrefs() { return notificationPrefs; }
    public void setNotificationPrefs(String notificationPrefs) { this.notificationPrefs = notificationPrefs; }

    public LocalDateTime getLastLogin() { return lastLogin; }
    public void setLastLogin(LocalDateTime lastLogin) { this.lastLogin = lastLogin; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public boolean isActive() { return "ACTIF".equalsIgnoreCase(status); }

    /** Initiales pour l'avatar (ex. « Karim Belhadj » → « KB »). */
    public String initials() {
        if (fullName == null || fullName.isBlank()) {
            return username != null && !username.isBlank() ? username.substring(0, 1).toUpperCase() : "?";
        }
        String[] parts = fullName.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(2, parts.length); i++) {
            if (!parts[i].isEmpty()) {
                sb.append(Character.toUpperCase(parts[i].charAt(0)));
            }
        }
        return sb.toString();
    }
}
