package com.medmaint.persistence.repo;

import com.medmaint.persistence.Jdbc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Paramètres système (clé/valeur). */
public final class SettingsRepo {

    public Map<String, String> findAll() {
        Map<String, String> out = new HashMap<>();
        for (Object[] row : Jdbc.query("SELECT setting_key, setting_value FROM settings",
                rs -> new Object[]{rs.getString(1), rs.getString(2)})) {
            out.put((String) row[0], (String) row[1]);
        }
        return out;
    }

    public Optional<String> get(String key) {
        return Jdbc.queryOne("SELECT setting_value FROM settings WHERE setting_key=?",
                rs -> rs.getString(1), key);
    }

    public void set(String key, String value, Long userId) {
        int updated = Jdbc.update(
                "UPDATE settings SET setting_value=?, updated_at=CURRENT_TIMESTAMP, updated_by=? WHERE setting_key=?",
                value, userId, key);
        if (updated == 0) {
            Jdbc.update("INSERT INTO settings(setting_key, setting_value, updated_at, updated_by) VALUES (?,?,CURRENT_TIMESTAMP,?)",
                    key, value, userId);
        }
    }

    public void delete(String key) {
        Jdbc.update("DELETE FROM settings WHERE setting_key=?", key);
    }

    public List<String> keysWithPrefix(String prefix) {
        return Jdbc.query("SELECT setting_key FROM settings WHERE setting_key LIKE ? ORDER BY setting_key",
                rs -> rs.getString(1), prefix + "%");
    }
}
