package com.medmaint.service;

import com.medmaint.persistence.Jdbc;
import com.medmaint.security.Permission;
import com.medmaint.security.Session;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Recherche globale : équipements, codes, n° de série, demandes, OT,
 * techniciens, pièces, fournisseurs, contrats — résultats groupés par type.
 */
public final class SearchService {

    /** Un résultat de recherche : type, libellé, sous-titre, identifiant, route cible. */
    public record Result(String group, String title, String subtitle, String route, long entityId) {
    }

    public Map<String, List<Result>> globalSearch(String query, int limitPerGroup) {
        Session.get().require(Permission.EQUIPMENT_VIEW);
        Map<String, List<Result>> out = new LinkedHashMap<>();
        if (query == null || query.trim().length() < 2) {
            return out;
        }
        String q = "%" + query.trim().toLowerCase().replace("%", "\\%") + "%";
        int limit = Math.max(5, limitPerGroup);

        // Équipements (nom, code, n° série)
        put(out, "Équipements", Jdbc.query("""
                SELECT id, code, name, serial_number FROM equipment
                WHERE archived = FALSE AND (LOWER(code) LIKE ? OR LOWER(name) LIKE ?
                    OR LOWER(serial_number) LIKE ? OR LOWER(model) LIKE ?)
                ORDER BY code LIMIT ?""",
                rs -> new Result("Équipements",
                        rs.getString("code") + " — " + rs.getString("name"),
                        "Série : " + nullSafe(rs.getString("serial_number")),
                        "equipment.detail", rs.getLong(1)),
                q, q, q, q, limit));

        // Demandes
        if (Session.get().has(Permission.REQUEST_VIEW)) {
            put(out, "Demandes d'intervention", Jdbc.query("""
                    SELECT id, request_no, problem_description, status FROM intervention_requests
                    WHERE LOWER(request_no) LIKE ? OR LOWER(problem_description) LIKE ?
                    ORDER BY reported_at DESC LIMIT ?""",
                    rs -> new Result("Demandes d'intervention",
                            rs.getString("request_no") + " — " + abbrev(rs.getString("problem_description")),
                            "Statut : " + rs.getString("status"),
                            "requests", rs.getLong(1)),
                    q, q, limit));
        }

        // Ordres de travail
        if (Session.get().has(Permission.WORKORDER_VIEW)) {
            put(out, "Interventions", Jdbc.query("""
                    SELECT w.id, w.wo_no, e.name, w.status FROM work_orders w
                    JOIN equipment e ON e.id = w.equipment_id
                    WHERE LOWER(w.wo_no) LIKE ? OR LOWER(e.name) LIKE ?
                    ORDER BY w.opened_at DESC LIMIT ?""",
                    rs -> new Result("Interventions",
                            rs.getString("wo_no") + " — " + rs.getString("name"),
                            "Statut : " + rs.getString("status"),
                            "workorders.detail", rs.getLong(1)),
                    q, q, limit));
        }

        // Techniciens
        if (Session.get().has(Permission.TECHNICIAN_VIEW)) {
            put(out, "Techniciens", Jdbc.query("""
                    SELECT id, full_name, specialty, employee_no FROM technicians
                    WHERE LOWER(full_name) LIKE ? OR LOWER(specialty) LIKE ?
                        OR LOWER(employee_no) LIKE ?
                    ORDER BY full_name LIMIT ?""",
                    rs -> new Result("Techniciens",
                            rs.getString("full_name"),
                            nullSafe(rs.getString("specialty")) + " · " + rs.getString("employee_no"),
                            "technicians", rs.getLong(1)),
                    q, q, q, limit));
        }

        // Pièces de rechange
        if (Session.get().has(Permission.PART_VIEW)) {
            put(out, "Pièces de rechange", Jdbc.query("""
                    SELECT id, part_no, name, quantity FROM spare_parts
                    WHERE archived = FALSE AND (LOWER(part_no) LIKE ? OR LOWER(name) LIKE ?)
                    ORDER BY part_no LIMIT ?""",
                    rs -> new Result("Pièces de rechange",
                            rs.getString("part_no") + " — " + rs.getString("name"),
                            "Stock : " + rs.getLong("quantity"),
                            "parts", rs.getLong(1)),
                    q, q, limit));
        }

        // Fournisseurs
        if (Session.get().has(Permission.SUPPLIER_VIEW)) {
            put(out, "Fournisseurs", Jdbc.query("""
                    SELECT id, company, category FROM suppliers
                    WHERE archived = FALSE AND LOWER(company) LIKE ?
                    ORDER BY company LIMIT ?""",
                    rs -> new Result("Fournisseurs",
                            rs.getString("company"),
                            nullSafe(rs.getString("category")),
                            "suppliers", rs.getLong(1)),
                    q, limit));
        }

        // Contrats
        if (Session.get().has(Permission.CONTRACT_VIEW)) {
            put(out, "Contrats", Jdbc.query("""
                    SELECT id, contract_no, title FROM contracts
                    WHERE LOWER(contract_no) LIKE ? OR LOWER(title) LIKE ?
                    ORDER BY end_date LIMIT ?""",
                    rs -> new Result("Contrats",
                            rs.getString("contract_no") + " — " + rs.getString("title"),
                            null, "contracts", rs.getLong(1)),
                    q, q, limit));
        }

        // Services
        put(out, "Services hospitaliers", Jdbc.query("""
                SELECT id, code, name FROM departments
                WHERE LOWER(name) LIKE ? OR LOWER(code) LIKE ? ORDER BY name LIMIT ?""",
                rs -> new Result("Services hospitaliers",
                        rs.getString("name"), rs.getString("code"), "departments", rs.getLong(1)),
                q, q, limit));

        return out;
    }

    public long totalCount(String query) {
        return globalSearch(query, 100).values().stream().mapToLong(List::size).sum();
    }

    private static void put(Map<String, List<Result>> map, String group, List<Result> results) {
        if (results != null && !results.isEmpty()) {
            map.computeIfAbsent(group, k -> new ArrayList<>()).addAll(results);
        }
    }

    private static String nullSafe(String s) {
        return s == null ? "—" : s;
    }

    private static String abbrev(String s) {
        if (s == null) return "—";
        return s.length() > 60 ? s.substring(0, 60) + "…" : s;
    }
}
