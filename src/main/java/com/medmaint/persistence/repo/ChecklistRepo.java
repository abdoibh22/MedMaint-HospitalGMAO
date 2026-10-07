package com.medmaint.persistence.repo;

import com.medmaint.domain.model.Checklist;
import com.medmaint.domain.model.MaintenanceRecord;
import com.medmaint.persistence.Jdbc;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Checklists (modèles + items) et historique de maintenance réalisée. */
public final class ChecklistRepo {

    private static final Jdbc.RowMapper<Checklist> CHK_MAP = rs -> {
        Checklist c = new Checklist();
        c.setId(rs.getLong("id"));
        c.setName(rs.getString("name"));
        c.setCategoryId(Jdbc.getLongOrNull(rs, "category_id"));
        c.setCategoryName(rs.getString("category_name"));
        c.setDescription(rs.getString("description"));
        c.setCreatedAt(Jdbc.getDateTime(rs, "created_at"));
        return c;
    };

    private static final Jdbc.RowMapper<Checklist.ChecklistItem> ITEM_MAP = rs -> {
        Checklist.ChecklistItem i = new Checklist.ChecklistItem();
        i.setId(rs.getLong("id"));
        i.setChecklistId(rs.getLong("checklist_id"));
        i.setPosition(rs.getInt("position"));
        i.setLabel(rs.getString("label"));
        i.setHint(rs.getString("hint"));
        return i;
    };

    private static final String REC_BASE = """
            SELECT r.*, e.code AS equipment_code, e.name AS equipment_name,
                   p.maintenance_type AS plan_type, w.wo_no, c.name AS checklist_name,
                   u.full_name AS performed_by_name
            FROM maintenance_records r
            JOIN equipment e ON e.id = r.equipment_id
            LEFT JOIN maintenance_plans p ON p.id = r.plan_id
            LEFT JOIN work_orders w ON w.id = r.work_order_id
            LEFT JOIN maintenance_checklists c ON c.id = r.checklist_id
            LEFT JOIN users u ON u.id = r.performed_by
            """;

    private static final Jdbc.RowMapper<MaintenanceRecord> REC_MAP = rs -> {
        MaintenanceRecord r = new MaintenanceRecord();
        r.setId(rs.getLong("id"));
        r.setEquipmentId(rs.getLong("equipment_id"));
        r.setEquipmentCode(rs.getString("equipment_code"));
        r.setEquipmentName(rs.getString("equipment_name"));
        r.setPlanId(Jdbc.getLongOrNull(rs, "plan_id"));
        r.setPlanType(rs.getString("plan_type"));
        r.setWorkOrderId(Jdbc.getLongOrNull(rs, "work_order_id"));
        r.setWorkOrderNo(rs.getString("wo_no"));
        r.setChecklistId(Jdbc.getLongOrNull(rs, "checklist_id"));
        r.setChecklistName(rs.getString("checklist_name"));
        r.setRecordType(rs.getString("record_type"));
        r.setPerformedBy(Jdbc.getLongOrNull(rs, "performed_by"));
        r.setPerformedByName(rs.getString("performed_by_name"));
        r.setPerformedAt(Jdbc.getDateTime(rs, "performed_at"));
        r.setResult(rs.getString("result"));
        r.setObservations(rs.getString("observations"));
        r.setDurationMin((Integer) rs.getObject("duration_min"));
        r.setCost(rs.getBigDecimal("cost"));
        r.setCreatedAt(Jdbc.getDateTime(rs, "created_at"));
        return r;
    };

    // ---------- Checklists ----------

    public List<Checklist> findAllChecklists() {
        return Jdbc.query("""
                SELECT c.*, cat.name AS category_name
                FROM maintenance_checklists c
                LEFT JOIN equipment_categories cat ON cat.id = c.category_id
                ORDER BY c.name""", CHK_MAP);
    }

    public Optional<Checklist> findChecklist(long id) {
        return Jdbc.queryOne("""
                SELECT c.*, cat.name AS category_name
                FROM maintenance_checklists c
                LEFT JOIN equipment_categories cat ON cat.id = c.category_id
                WHERE c.id=?""", CHK_MAP, id);
    }

    public List<Checklist.ChecklistItem> findItems(long checklistId) {
        return Jdbc.query("SELECT * FROM checklist_items WHERE checklist_id=? ORDER BY position",
                ITEM_MAP, checklistId);
    }

    public long insertChecklist(Checklist c) {
        return Jdbc.insert("""
                INSERT INTO maintenance_checklists(name, category_id, description, created_at)
                VALUES (?,?,?,CURRENT_TIMESTAMP)""",
                c.getName(), c.getCategoryId(), c.getDescription());
    }

    public void updateChecklist(Checklist c) {
        Jdbc.update("UPDATE maintenance_checklists SET name=?, category_id=?, description=? WHERE id=?",
                c.getName(), c.getCategoryId(), c.getDescription(), c.getId());
    }

    public void deleteChecklist(long id) {
        Jdbc.update("DELETE FROM checklist_items WHERE checklist_id=?", id);
        Jdbc.update("DELETE FROM maintenance_checklists WHERE id=?", id);
    }

    public void insertItem(long checklistId, Checklist.ChecklistItem item) {
        Jdbc.update("INSERT INTO checklist_items(checklist_id, position, label, hint) VALUES (?,?,?,?)",
                checklistId, item.getPosition(), item.getLabel(), item.getHint());
    }

    public void replaceItems(long checklistId, List<Checklist.ChecklistItem> items) {
        Jdbc.update("DELETE FROM checklist_items WHERE checklist_id=?", checklistId);
        int pos = 1;
        for (Checklist.ChecklistItem i : items) {
            if (i.getLabel() == null || i.getLabel().isBlank()) {
                continue;
            }
            Jdbc.update("INSERT INTO checklist_items(checklist_id, position, label, hint) VALUES (?,?,?,?)",
                    checklistId, pos++, i.getLabel(), i.getHint());
        }
    }

    // ---------- Historique ----------

    public List<MaintenanceRecord> findRecordsByEquipment(long equipmentId, int limit) {
        return Jdbc.query(REC_BASE + " WHERE r.equipment_id=? ORDER BY r.performed_at DESC LIMIT ?",
                REC_MAP, equipmentId, limit);
    }

    public List<MaintenanceRecord> findRecentRecords(int limit) {
        return Jdbc.query(REC_BASE + " ORDER BY r.performed_at DESC LIMIT ?", REC_MAP, limit);
    }

    public Optional<MaintenanceRecord> findRecord(long id) {
        return Jdbc.queryOne(REC_BASE + " WHERE r.id=?", REC_MAP, id);
    }

    public long insertRecord(MaintenanceRecord r) {
        return Jdbc.insert("""
                INSERT INTO maintenance_records(equipment_id, plan_id, work_order_id, checklist_id,
                    record_type, performed_by, performed_at, result, observations, duration_min, cost,
                    created_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)""",
                r.getEquipmentId(), r.getPlanId(), r.getWorkOrderId(), r.getChecklistId(),
                r.getRecordType(), r.getPerformedBy(), r.getPerformedAt(), r.getResult(),
                r.getObservations(), r.getDurationMin(), r.getCost());
    }

    public void insertResult(long recordId, MaintenanceRecord.ChecklistResultEntry e) {
        Jdbc.update("""
                INSERT INTO checklist_results(record_id, item_id, item_label, result, notes)
                VALUES (?,?,?,?,?)""",
                recordId, e.getItemId(), e.getItemLabel(), e.getResult(), e.getNotes());
    }

    public List<MaintenanceRecord.ChecklistResultEntry> findResults(long recordId) {
        return Jdbc.query("SELECT * FROM checklist_results WHERE record_id=? ORDER BY id", rs -> {
            MaintenanceRecord.ChecklistResultEntry e = new MaintenanceRecord.ChecklistResultEntry();
            e.setId(rs.getLong("id"));
            e.setItemId(rs.getLong("item_id"));
            e.setItemLabel(rs.getString("item_label"));
            e.setResult(rs.getString("result"));
            e.setNotes(rs.getString("notes"));
            return e;
        }, recordId);
    }

    public long countRecords() {
        return Jdbc.count("SELECT COUNT(*) FROM maintenance_records");
    }

    public long countRecordsSince(int days) {
        return Jdbc.count("""
                SELECT COUNT(*) FROM maintenance_records
                WHERE performed_at >= DATEADD('DAY', ?, CURRENT_TIMESTAMP)""", -days);
    }

    /** Historique complet, rempli avec les résultats de checklist. */
    public List<MaintenanceRecord> withResults(List<MaintenanceRecord> records) {
        List<MaintenanceRecord> out = new ArrayList<>();
        for (MaintenanceRecord r : records) {
            r.setChecklistResults(findResults(r.getId()));
            out.add(r);
        }
        return out;
    }
}
