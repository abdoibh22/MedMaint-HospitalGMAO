package com.medmaint.security;

/**
 * Permissions métier. Les services vérifient ces permissions (et pas seulement
 * l'UI) : toute action sensible passe par {@code Session.require(...)}.
 */
public enum Permission {
    DASHBOARD_VIEW("Voir le tableau de bord"),

    EQUIPMENT_VIEW("Consulter les équipements"),
    EQUIPMENT_CREATE("Créer des équipements"),
    EQUIPMENT_UPDATE("Modifier des équipements"),
    EQUIPMENT_DELETE("Supprimer / archiver des équipements"),

    REQUEST_VIEW("Consulter les demandes d'intervention"),
    REQUEST_CREATE("Créer des demandes d'intervention"),
    REQUEST_APPROVE("Approuver / rejeter des demandes"),
    REQUEST_ASSIGN("Affecter un technicien"),

    WORKORDER_VIEW("Consulter les ordres de travail"),
    WORKORDER_UPDATE("Modifier les ordres de travail"),
    WORKORDER_EXECUTE("Exécuter une intervention (diagnostic, travaux, pièces)"),
    WORKORDER_VALIDATE("Valider / clôturer des interventions"),

    PM_VIEW("Consulter la maintenance préventive"),
    PM_MANAGE("Gérer les plans de maintenance préventive"),
    PM_EXECUTE("Réaliser une maintenance préventive / checklist"),

    TECHNICIAN_VIEW("Consulter les techniciens"),
    TECHNICIAN_MANAGE("Gérer les techniciens"),

    DEPARTMENT_VIEW("Consulter les services hospitaliers"),
    DEPARTMENT_MANAGE("Gérer les services hospitaliers"),

    PART_VIEW("Consulter les pièces de rechange"),
    PART_MANAGE("Gérer les pièces et le stock"),

    SUPPLIER_VIEW("Consulter les fournisseurs"),
    SUPPLIER_MANAGE("Gérer les fournisseurs"),

    CONTRACT_VIEW("Consulter les contrats"),
    CONTRACT_MANAGE("Gérer les contrats"),

    DOCUMENT_VIEW("Consulter les documents"),
    DOCUMENT_UPLOAD("Téléverser des documents"),
    DOCUMENT_DELETE("Supprimer des documents"),

    REPORT_VIEW("Consulter les rapports et l'analytique"),
    EXPORT("Exporter les données"),

    NOTIFICATION_VIEW("Consulter les notifications"),

    USER_VIEW("Consulter les utilisateurs"),
    USER_MANAGE("Gérer les utilisateurs et les rôles"),

    AUDIT_VIEW("Consulter le journal d'audit"),

    SETTINGS_VIEW("Consulter les paramètres"),
    SETTINGS_MANAGE("Modifier les paramètres");

    private final String label;

    Permission(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
