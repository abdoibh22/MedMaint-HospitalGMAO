package com.medmaint.persistence.repo;

import com.medmaint.domain.model.EquipmentCategory;
import com.medmaint.domain.model.Manufacturer;
import com.medmaint.persistence.Jdbc;

import java.util.List;
import java.util.Optional;

/** Référentiels : catégories d'équipements et fabricants. */
public final class CategoryRepo {

    private static final Jdbc.RowMapper<EquipmentCategory> CAT_MAP = rs -> {
        EquipmentCategory c = new EquipmentCategory();
        c.setId(rs.getLong("id"));
        c.setCode(rs.getString("code"));
        c.setName(rs.getString("name"));
        c.setDescription(rs.getString("description"));
        c.setDefaultFrequency(rs.getString("default_frequency"));
        return c;
    };

    private static final Jdbc.RowMapper<Manufacturer> MFR_MAP = rs -> {
        Manufacturer m = new Manufacturer();
        m.setId(rs.getLong("id"));
        m.setName(rs.getString("name"));
        m.setCountry(rs.getString("country"));
        m.setWebsite(rs.getString("website"));
        m.setSupportEmail(rs.getString("support_email"));
        return m;
    };

    public List<EquipmentCategory> findAllCategories() {
        return Jdbc.query("SELECT * FROM equipment_categories ORDER BY name", CAT_MAP);
    }

    public Optional<EquipmentCategory> findCategory(long id) {
        return Jdbc.queryOne("SELECT * FROM equipment_categories WHERE id=?", CAT_MAP, id);
    }

    public boolean categoryCodeExists(String code, Long excludeId) {
        String sql = excludeId == null
                ? "SELECT COUNT(*) FROM equipment_categories WHERE LOWER(code)=LOWER(?)"
                : "SELECT COUNT(*) FROM equipment_categories WHERE LOWER(code)=LOWER(?) AND id<>?";
        return (excludeId == null ? Jdbc.count(sql, code) : Jdbc.count(sql, code, excludeId)) > 0;
    }

    public long insertCategory(EquipmentCategory c) {
        return Jdbc.insert("""
                INSERT INTO equipment_categories(code, name, description, default_frequency, created_at)
                VALUES (?,?,?,?,CURRENT_TIMESTAMP)""",
                c.getCode(), c.getName(), c.getDescription(), c.getDefaultFrequency());
    }

    public void updateCategory(EquipmentCategory c) {
        Jdbc.update("UPDATE equipment_categories SET code=?, name=?, description=?, default_frequency=? WHERE id=?",
                c.getCode(), c.getName(), c.getDescription(), c.getDefaultFrequency(), c.getId());
    }

    public void deleteCategory(long id) {
        Jdbc.update("DELETE FROM equipment_categories WHERE id=?", id);
    }

    public boolean categoryInUse(long id) {
        return Jdbc.count("SELECT COUNT(*) FROM equipment WHERE category_id=?", id) > 0;
    }

    // ---------- Fabricants ----------

    public List<Manufacturer> findAllManufacturers() {
        return Jdbc.query("SELECT * FROM manufacturers ORDER BY name", MFR_MAP);
    }

    public Optional<Manufacturer> findManufacturer(long id) {
        return Jdbc.queryOne("SELECT * FROM manufacturers WHERE id=?", MFR_MAP, id);
    }

    public long insertManufacturer(Manufacturer m) {
        return Jdbc.insert("INSERT INTO manufacturers(name, country, website, support_email) VALUES (?,?,?,?)",
                m.getName(), m.getCountry(), m.getWebsite(), m.getSupportEmail());
    }

    public void updateManufacturer(Manufacturer m) {
        Jdbc.update("UPDATE manufacturers SET name=?, country=?, website=?, support_email=? WHERE id=?",
                m.getName(), m.getCountry(), m.getWebsite(), m.getSupportEmail(), m.getId());
    }

    public void deleteManufacturer(long id) {
        Jdbc.update("DELETE FROM manufacturers WHERE id=?", id);
    }

    public boolean manufacturerInUse(long id) {
        return Jdbc.count("SELECT COUNT(*) FROM equipment WHERE manufacturer_id=?", id) > 0;
    }
}
