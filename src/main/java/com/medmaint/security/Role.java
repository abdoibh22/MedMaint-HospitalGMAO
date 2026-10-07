package com.medmaint.security;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

import static com.medmaint.security.Permission.*;

/**
 * Rôles applicatifs et leur matrice de permissions.
 * La matrice est la source de vérité ; elle est aussi persistée dans les tables
 * roles / permissions / role_permissions pour être inspectable et paramétrable.
 */
public enum Role {

    ADMIN("Administrateur", "Accès complet au système", EnumSet.allOf(Permission.class)),

    MANAGER("Responsable Maintenance",
            "Approuve et affecte les demandes, planifie la maintenance, gère pièces et fournisseurs",
            EnumSet.of(
                    DASHBOARD_VIEW, EQUIPMENT_VIEW, EQUIPMENT_CREATE, EQUIPMENT_UPDATE,
                    REQUEST_VIEW, REQUEST_CREATE, REQUEST_APPROVE, REQUEST_ASSIGN,
                    WORKORDER_VIEW, WORKORDER_UPDATE, WORKORDER_EXECUTE, WORKORDER_VALIDATE,
                    PM_VIEW, PM_MANAGE, PM_EXECUTE,
                    TECHNICIAN_VIEW, TECHNICIAN_MANAGE,
                    DEPARTMENT_VIEW, PART_VIEW, PART_MANAGE,
                    SUPPLIER_VIEW, SUPPLIER_MANAGE, CONTRACT_VIEW, CONTRACT_MANAGE,
                    DOCUMENT_VIEW, DOCUMENT_UPLOAD, DOCUMENT_DELETE,
                    REPORT_VIEW, EXPORT, NOTIFICATION_VIEW, USER_VIEW, SETTINGS_VIEW)),

    ENGINEER("Ingénieur Biomédical",
             "Gère les équipements biomédicaux, réalise et valide les interventions, pilote les checklists",
             EnumSet.of(
                     DASHBOARD_VIEW, EQUIPMENT_VIEW, EQUIPMENT_CREATE, EQUIPMENT_UPDATE,
                     REQUEST_VIEW, REQUEST_APPROVE, REQUEST_ASSIGN,
                     WORKORDER_VIEW, WORKORDER_UPDATE, WORKORDER_EXECUTE, WORKORDER_VALIDATE,
                     PM_VIEW, PM_MANAGE, PM_EXECUTE,
                     TECHNICIAN_VIEW, DEPARTMENT_VIEW,
                     PART_VIEW, PART_MANAGE, SUPPLIER_VIEW, CONTRACT_VIEW,
                     DOCUMENT_VIEW, DOCUMENT_UPLOAD, DOCUMENT_DELETE,
                     REPORT_VIEW, EXPORT, NOTIFICATION_VIEW, SETTINGS_VIEW)),

    TECHNICIAN("Technicien",
               "Exécute les interventions assignées, saisit diagnostic et travaux, consomme des pièces",
               EnumSet.of(
                       DASHBOARD_VIEW, EQUIPMENT_VIEW,
                       REQUEST_VIEW,
                       WORKORDER_VIEW, WORKORDER_UPDATE, WORKORDER_EXECUTE,
                       PM_VIEW, PM_EXECUTE,
                       TECHNICIAN_VIEW, DEPARTMENT_VIEW,
                       PART_VIEW, DOCUMENT_VIEW, DOCUMENT_UPLOAD,
                       NOTIFICATION_VIEW)),

    DEPT_MANAGER("Responsable de Service",
                 "Consulte les équipements de son service, signale les pannes et suit ses demandes",
                 EnumSet.of(
                         DASHBOARD_VIEW, EQUIPMENT_VIEW,
                         REQUEST_VIEW, REQUEST_CREATE,
                         WORKORDER_VIEW, DEPARTMENT_VIEW,
                         DOCUMENT_VIEW, DOCUMENT_UPLOAD, NOTIFICATION_VIEW)),

    REQUESTER("Utilisateur / Demandeur",
              "Signale les problèmes d'équipement et suit ses demandes",
              EnumSet.of(
                      DASHBOARD_VIEW, EQUIPMENT_VIEW,
                      REQUEST_VIEW, REQUEST_CREATE, NOTIFICATION_VIEW));

    private final String label;
    private final String description;
    private final Set<Permission> permissions;

    Role(String label, String description, Set<Permission> permissions) {
        this.label = label;
        this.description = description;
        this.permissions = permissions;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    public Set<Permission> permissions() {
        return permissions;
    }

    public boolean has(Permission p) {
        return permissions.contains(p);
    }

    public static Role fromCode(String code) {
        if (code == null) {
            return REQUESTER;
        }
        try {
            return valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return REQUESTER;
        }
    }

    public static Role fromNameInsensitive(String name) {
        return Arrays.stream(values())
                .filter(r -> r.label().equalsIgnoreCase(name) || r.name().equalsIgnoreCase(name))
                .findFirst()
                .orElse(REQUESTER);
    }
}
