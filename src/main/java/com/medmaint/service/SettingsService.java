package com.medmaint.service;

import com.medmaint.persistence.repo.SettingsRepo;
import com.medmaint.security.Permission;
import com.medmaint.security.Session;

import java.util.LinkedHashMap;
import java.util.Map;

/** Paramètres système (informations hôpital, préférences, listes configurables). */
public final class SettingsService {

    private final SettingsRepo repo = new SettingsRepo();
    private final AuditService audit;

    public SettingsService(AuditService audit) {
        this.audit = audit;
    }

    /** Paramètres par défaut (premier démarrage). */
    public void ensureDefaults() {
        Map<String, String> defaults = new LinkedHashMap<>();
        defaults.put("hospital.name", "Hôpital Central d'Alger — Département Biomédical");
        defaults.put("hospital.address", "1, rue Docteur Benzerdjeb, Alger");
        defaults.put("hospital.phone", "+213 (0) 21 00 00 00");
        defaults.put("hospital.email", "biomedical@medmaint.demo");
        defaults.put("hospital.logo_text", "CHU");
        defaults.put("app.locale", "fr");
        defaults.put("notif.pm_lead_days", "7");
        defaults.put("notif.contract_lead_days", "60");
        defaults.put("notif.low_stock_enabled", "true");
        defaults.put("stock.allow_negative", "false");
        defaults.put("doc.max_upload_mb", "10");
        defaults.put("maintenance.hourly_rate", "45.00");

        for (Map.Entry<String, String> e : defaults.entrySet()) {
            if (repo.get(e.getKey()).isEmpty()) {
                repo.set(e.getKey(), e.getValue(), null);
            }
        }
    }

    public String get(String key) {
        return repo.get(key).orElse("");
    }

    public String get(String key, String def) {
        String v = repo.get(key).orElse(null);
        return v == null || v.isBlank() ? def : v;
    }

    public int getInt(String key, int def) {
        try {
            return Integer.parseInt(get(key, String.valueOf(def)));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public boolean getBool(String key, boolean def) {
        String v = get(key, String.valueOf(def));
        return "true".equalsIgnoreCase(v);
    }

    public void set(String key, String value) {
        Session s = Session.get();
        if (s == null || !s.has(Permission.SETTINGS_MANAGE)) {
            if (s != null) {
                s.require(Permission.SETTINGS_MANAGE);
            }
        }
        String before = get(key, null);
        repo.set(key, value, s != null ? s.userId() : null);
        audit.updated("Paramètre", key, key, before, value);
    }

    public Map<String, String> all() {
        Session.get().require(Permission.SETTINGS_VIEW);
        return repo.findAll();
    }

    /** Groupes de paramètres exposés dans l'écran Paramètres. */
    public Map<String, String> grouped(String prefix) {
        Session.get().require(Permission.SETTINGS_VIEW);
        Map<String, String> out = new LinkedHashMap<>();
        repo.findAll().forEach((k, v) -> {
            if (k.startsWith(prefix)) {
                out.put(k, v);
            }
        });
        return out;
    }

    public SettingsRepo repo() {
        return repo;
    }
}
