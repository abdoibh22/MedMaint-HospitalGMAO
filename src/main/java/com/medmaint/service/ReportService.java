package com.medmaint.service;

import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.FontFactory;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import com.medmaint.config.AppConfig;
import com.medmaint.persistence.Jdbc;
import com.medmaint.security.Permission;
import com.medmaint.security.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Module de rapports : jeux de données filtrés + export CSV et PDF.
 * Chaque rapport est une requête SQL réelle ; aucun chiffre n'est simulé.
 */
public final class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm");

    /** Un rapport = en-têtes + lignes (chaque ligne = liste de chaînes). */
    public record ReportData(String title, List<String> headers, List<List<String>> rows) {
        public boolean isEmpty() {
            return rows.isEmpty();
        }
    }

    /** Filtres communs aux rapports. */
    public record Filters(LocalDate from, LocalDate to, Long departmentId, Long categoryId,
                          Long equipmentId, Long technicianId, String type, String status) {
        public static Filters all() {
            return new Filters(null, null, null, null, null, null, null, null);
        }
    }

    // ---------- Jeux de données ----------

    /** Rapport : inventaire des équipements. */
    public ReportData equipmentInventory(Filters f) {
        Session.get().require(Permission.REPORT_VIEW);
        StringBuilder sql = new StringBuilder("""
                SELECT e.code, e.name, c.name, m.name, e.model, e.serial_number,
                       d.name, e.status, e.criticality, e.purchase_date, e.warranty_end_date,
                       e.last_maintenance_date, e.next_maintenance_date
                FROM equipment e
                JOIN equipment_categories c ON c.id = e.category_id
                LEFT JOIN manufacturers m ON m.id = e.manufacturer_id
                JOIN departments d ON d.id = e.department_id
                WHERE e.archived = FALSE""");
        List<Object> params = new ArrayList<>();
        appendFilters(sql, params, f, false);
        sql.append(" ORDER BY e.code");
        List<List<String>> rows = new ArrayList<>();
        for (Object[] r : Jdbc.query(sql.toString(), rowMapper(), params.toArray())) {
            rows.add(List.of(str(r[0]), str(r[1]), str(r[2]), str(r[3]), str(r[4]), str(r[5]),
                    str(r[6]), str(r[7]), str(r[8]), str(r[9]), str(r[10]), str(r[11]), str(r[12])));
        }
        return new ReportData("Inventaire des équipements",
                List.of("Code", "Équipement", "Catégorie", "Fabricant", "Modèle", "N° série",
                        "Service", "Statut", "Criticité", "Achat", "Fin garantie",
                        "Dern. maintenance", "Proch. maintenance"),
                rows);
    }

    /** Rapport : historique de maintenance d'un équipement (ou de tous). */
    public ReportData maintenanceHistory(Filters f) {
        Session.get().require(Permission.REPORT_VIEW);
        StringBuilder sql = new StringBuilder("""
                SELECT e.code, e.name, r.record_type, r.performed_at, u.full_name,
                       r.result, r.duration_min, r.cost, r.observations
                FROM maintenance_records r
                JOIN equipment e ON e.id = r.equipment_id
                LEFT JOIN users u ON u.id = r.performed_by
                WHERE 1=1""");
        List<Object> params = new ArrayList<>();
        if (f.from() != null) {
            sql.append(" AND r.performed_at >= ?");
            params.add(f.from().atStartOfDay());
        }
        if (f.to() != null) {
            sql.append(" AND r.performed_at < ?");
            params.add(f.to().plusDays(1).atStartOfDay());
        }
        if (f.equipmentId() != null) {
            sql.append(" AND r.equipment_id = ?");
            params.add(f.equipmentId());
        }
        if (f.departmentId() != null) {
            sql.append(" AND e.department_id = ?");
            params.add(f.departmentId());
        }
        if (f.categoryId() != null) {
            sql.append(" AND e.category_id = ?");
            params.add(f.categoryId());
        }
        sql.append(" ORDER BY r.performed_at DESC");
        List<List<String>> rows = new ArrayList<>();
        for (Object[] r : Jdbc.query(sql.toString(), rowMapper(), params.toArray())) {
            rows.add(List.of(str(r[0]), str(r[1]), str(r[2]), str(r[3]), str(r[4]),
                    str(r[5]), str(r[6]), str(r[7]), str(r[8])));
        }
        return new ReportData("Historique de maintenance",
                List.of("Code", "Équipement", "Type", "Date", "Réalisé par", "Résultat",
                        "Durée (min)", "Coût", "Observations"),
                rows);
    }

    /** Rapport : maintenance préventive (plans + état). */
    public ReportData preventiveReport(Filters f) {
        Session.get().require(Permission.REPORT_VIEW);
        StringBuilder sql = new StringBuilder("""
                SELECT e.code, e.name, p.maintenance_type, p.frequency, p.last_performed,
                       p.next_due, t.full_name, p.status
                FROM maintenance_plans p
                JOIN equipment e ON e.id = p.equipment_id
                LEFT JOIN technicians t ON t.id = p.responsible_id
                WHERE p.active = TRUE""");
        List<Object> params = new ArrayList<>();
        appendFilters(sql, params, f, true);
        sql.append(" ORDER BY p.next_due");
        List<List<String>> rows = new ArrayList<>();
        for (Object[] r : Jdbc.query(sql.toString(), rowMapper(), params.toArray())) {
            String status = statusPlan(r[5], str(r[7]));
            rows.add(List.of(str(r[0]), str(r[1]), str(r[2]), str(r[3]), str(r[4]),
                    str(r[5]), str(r[6]), status));
        }
        return new ReportData("Maintenance préventive",
                List.of("Code", "Équipement", "Type", "Fréquence", "Dernière", "Prochaine",
                        "Responsable", "État"),
                rows);
    }

    /** Rapport : interventions correctives. */
    public ReportData correctiveReport(Filters f) {
        Session.get().require(Permission.REPORT_VIEW);
        StringBuilder sql = new StringBuilder("""
                SELECT w.wo_no, e.code, e.name, d.name, t.full_name, w.priority, w.status,
                       w.opened_at, w.completed_at, w.downtime_hours, w.total_cost, w.failure_type
                FROM work_orders w
                JOIN equipment e ON e.id = w.equipment_id
                LEFT JOIN departments d ON d.id = e.department_id
                LEFT JOIN technicians t ON t.id = w.technician_id
                WHERE 1=1""");
        List<Object> params = new ArrayList<>();
        if (f.from() != null) {
            sql.append(" AND w.opened_at >= ?");
            params.add(f.from().atStartOfDay());
        }
        if (f.to() != null) {
            sql.append(" AND w.opened_at < ?");
            params.add(f.to().plusDays(1).atStartOfDay());
        }
        if (f.departmentId() != null) {
            sql.append(" AND e.department_id = ?");
            params.add(f.departmentId());
        }
        if (f.categoryId() != null) {
            sql.append(" AND e.category_id = ?");
            params.add(f.categoryId());
        }
        if (f.equipmentId() != null) {
            sql.append(" AND w.equipment_id = ?");
            params.add(f.equipmentId());
        }
        if (f.technicianId() != null) {
            sql.append(" AND w.technician_id = ?");
            params.add(f.technicianId());
        }
        if (f.status() != null && !f.status().isBlank()) {
            sql.append(" AND w.status = ?");
            params.add(f.status());
        }
        sql.append(" ORDER BY w.opened_at DESC");
        List<List<String>> rows = new ArrayList<>();
        for (Object[] r : Jdbc.query(sql.toString(), rowMapper(), params.toArray())) {
            rows.add(List.of(str(r[0]), str(r[1]), str(r[2]), str(r[3]), str(r[4]), str(r[5]),
                    str(r[6]), str(r[7]), str(r[8]), str(r[9]), str(r[10]), str(r[11])));
        }
        return new ReportData("Interventions correctives",
                List.of("N° OT", "Code", "Équipement", "Service", "Technicien", "Priorité",
                        "Statut", "Ouvert le", "Terminé le", "Arrêt (h)", "Coût", "Type panne"),
                rows);
    }

    /** Rapport : statistiques de pannes par catégorie. */
    public ReportData breakdownStats(Filters f) {
        Session.get().require(Permission.REPORT_VIEW);
        List<List<String>> rows = new ArrayList<>();
        for (Object[] r : new com.medmaint.persistence.repo.WorkOrderRepo().failuresByCategory(12)) {
            rows.add(List.of(str(r[0]), str(r[1])));
        }
        for (Object[] r : Jdbc.query("""
                SELECT c.name, COUNT(e.id)
                FROM equipment_categories c
                LEFT JOIN equipment e ON e.category_id = c.id AND e.archived = FALSE
                GROUP BY c.name ORDER BY c.name""",
                (Jdbc.RowMapper<Object[]>) rs -> new Object[]{rs.getString(1), rs.getLong(2)})) {
            rows.add(List.of(str(r[0]) + " (parc)", str(r[1])));
        }
        return new ReportData("Statistiques de pannes (12 mois)",
                List.of("Catégorie", "Valeur"), rows);
    }

    /** Rapport : coûts de maintenance. */
    public ReportData costReport(Filters f) {
        Session.get().require(Permission.REPORT_VIEW);
        List<List<String>> rows = new ArrayList<>();
        for (Object[] r : new com.medmaint.persistence.repo.WorkOrderRepo().costByMonth(12)) {
            rows.add(List.of("Mois " + str(r[0]), "OT", str(r[1])));
        }
        for (Object[] r : new com.medmaint.persistence.repo.SparePartRepo().topConsumedParts(25)) {
            rows.add(List.of("Pièce " + str(r[0]) + " — " + str(r[1]), "Pièces",
                    str(r[3]) + " (" + str(r[2]) + " u.)"));
        }
        return new ReportData("Coûts de maintenance",
                List.of("Poste", "Nature", "Montant / détail"), rows);
    }

    /** Rapport : consommation de pièces. */
    public ReportData partsConsumption(Filters f) {
        Session.get().require(Permission.REPORT_VIEW);
        List<List<String>> rows = new ArrayList<>();
        for (Object[] r : new com.medmaint.persistence.repo.SparePartRepo().topConsumedParts(50)) {
            rows.add(List.of(str(r[0]), str(r[1]), str(r[2]), str(r[3])));
        }
        return new ReportData("Consommation de pièces de rechange",
                List.of("N° pièce", "Désignation", "Quantité", "Coût"), rows);
    }

    /** Rapport : performance techniciens. */
    public ReportData technicianPerformance(Filters f) {
        Session.get().require(Permission.REPORT_VIEW);
        List<List<String>> rows = new ArrayList<>();
        for (Object[] r : Jdbc.query("""
                SELECT t.full_name, t.specialty,
                       COUNT(w.id) AS total,
                       SUM(CASE WHEN w.status IN ('VALIDEE','CLOTUREE') THEN 1 ELSE 0 END) AS done,
                       SUM(CASE WHEN w.status NOT IN ('RESOLUE','VALIDEE','CLOTUREE') THEN 1 ELSE 0 END) AS open,
                       AVG(CASE WHEN w.started_at IS NOT NULL AND w.completed_at IS NOT NULL
                                THEN DATEDIFF('MINUTE', w.started_at, w.completed_at) / 60.0 END) AS avg_h
                FROM technicians t
                LEFT JOIN work_orders w ON w.technician_id = t.id
                GROUP BY t.full_name, t.specialty
                ORDER BY total DESC""",
                (Jdbc.RowMapper<Object[]>) rs -> new Object[]{
                        rs.getString(1), rs.getString(2), rs.getLong(3), rs.getLong(4),
                        rs.getLong(5), rs.getBigDecimal(6)})) {
            rows.add(List.of(str(r[0]), str(r[1]), str(r[2]), str(r[3]), str(r[4]),
                    r[5] == null ? "—" : String.format("%.1f h", ((java.math.BigDecimal) r[5]).doubleValue())));
        }
        return new ReportData("Performance des techniciens",
                List.of("Technicien", "Spécialité", "Total OT", "Terminées", "Ouvertes", "Durée moy."),
                rows);
    }

    /** Rapport : disponibilité des équipements. */
    public ReportData availabilityReport(Filters f) {
        Session.get().require(Permission.REPORT_VIEW);
        BigDecimalAvailability ba = new BigDecimalAvailability();
        List<List<String>> rows = new ArrayList<>();
        for (Object[] r : Jdbc.query("""
                SELECT e.code, e.name, d.name,
                       COALESCE(SUM(w.downtime_hours), 0) AS downtime, COUNT(w.id)
                FROM equipment e
                JOIN departments d ON d.id = e.department_id
                LEFT JOIN work_orders w ON w.equipment_id = e.id
                     AND w.opened_at >= DATEADD('MONTH', -12, CURRENT_TIMESTAMP)
                WHERE e.archived = FALSE
                GROUP BY e.code, e.name, d.name
                ORDER BY downtime DESC""",
                (Jdbc.RowMapper<Object[]>) rs -> new Object[]{
                        rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getBigDecimal(4), rs.getLong(5)})) {
            rows.add(List.of(str(r[0]), str(r[1]), str(r[2]), str(r[3]) + " h",
                    str(r[4]), ba.percent((java.math.BigDecimal) r[3])));
        }
        return new ReportData("Disponibilité des équipements (12 mois)",
                List.of("Code", "Équipement", "Service", "Arrêts", "Pannes", "Disponibilité"),
                rows);
    }

    /** Rapport : temps d'arrêt par service. */
    public ReportData downtimeReport(Filters f) {
        Session.get().require(Permission.REPORT_VIEW);
        List<List<String>> rows = new ArrayList<>();
        for (Object[] r : new com.medmaint.persistence.repo.WorkOrderRepo().downtimeByDepartment(12)) {
            rows.add(List.of(str(r[0]), str(r[1]) + " h"));
        }
        return new ReportData("Temps d'arrêt par service (12 mois)",
                List.of("Service", "Arrêt total"), rows);
    }

    /** Rapport : contrats. */
    public ReportData contractReport(Filters f) {
        Session.get().require(Permission.REPORT_VIEW);
        List<List<String>> rows = new ArrayList<>();
        for (Object[] r : Jdbc.query("""
                SELECT c.contract_no, c.title, s.company, c.start_date, c.end_date,
                       c.annual_cost, c.status,
                       (SELECT COUNT(*) FROM contract_equipment ce WHERE ce.contract_id = c.id)
                FROM contracts c LEFT JOIN suppliers s ON s.id = c.supplier_id
                ORDER BY c.end_date""",
                (Jdbc.RowMapper<Object[]>) rs -> new Object[]{
                        rs.getString(1), rs.getString(2), rs.getString(3), rs.getObject(4),
                        rs.getObject(5), rs.getBigDecimal(6), rs.getString(7), rs.getLong(8)})) {
            String eff = contractStatus(str(r[6]), (java.sql.Date) r[3], (java.sql.Date) r[4]);
            rows.add(List.of(str(r[0]), str(r[1]), str(r[2]), str(r[3]), str(r[4]),
                    str(r[5]), eff, str(r[7])));
        }
        return new ReportData("Contrats de maintenance",
                List.of("N°", "Intitulé", "Fournisseur", "Début", "Fin", "Coût annuel",
                        "Statut", "Équipements"),
                rows);
    }

    /** Liste des rapports disponibles (pour l'UI). */
    public static List<String> reportNames() {
        return List.of(
                "Inventaire des équipements",
                "Historique de maintenance",
                "Maintenance préventive",
                "Interventions correctives",
                "Statistiques de pannes",
                "Coûts de maintenance",
                "Consommation de pièces",
                "Performance des techniciens",
                "Disponibilité des équipements",
                "Temps d'arrêt par service",
                "Contrats de maintenance");
    }

    public ReportData build(String reportName, Filters f) {
        return switch (reportName) {
            case "Inventaire des équipements" -> equipmentInventory(f);
            case "Historique de maintenance" -> maintenanceHistory(f);
            case "Maintenance préventive" -> preventiveReport(f);
            case "Interventions correctives" -> correctiveReport(f);
            case "Statistiques de pannes" -> breakdownStats(f);
            case "Coûts de maintenance" -> costReport(f);
            case "Consommation de pièces" -> partsConsumption(f);
            case "Performance des techniciens" -> technicianPerformance(f);
            case "Disponibilité des équipements" -> availabilityReport(f);
            case "Temps d'arrêt par service" -> downtimeReport(f);
            case "Contrats de maintenance" -> contractReport(f);
            default -> throw new IllegalArgumentException("Rapport inconnu : " + reportName);
        };
    }

    // ---------- Exports ----------

    /** Export CSV (UTF-8 avec BOM pour Excel). @return chemin du fichier généré. */
    public Path exportCsv(ReportData data) {
        Session.get().require(Permission.EXPORT);
        Path dir = AppConfig.get().exportDir();
        try {
            Files.createDirectories(dir);
            String name = sanitizeFile(data.title()) + "_" + LocalDateTime.now().format(TS) + ".csv";
            Path out = dir.resolve(name);
            try (java.io.Writer w = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
                w.write('\uFEFF'); // BOM Excel
                w.write(String.join(";", data.headers()) + "\n");
                for (List<String> row : data.rows()) {
                    w.write(String.join(";", row.stream().map(ReportService::escapeCsv).toList()) + "\n");
                }
            }
            return out;
        } catch (IOException e) {
            log.error("Export CSV impossible", e);
            throw new com.medmaint.security.ValidationException("Export CSV impossible.");
        }
    }

    /** Export PDF (OpenPDF) avec en-tête et tableau. @return chemin du fichier généré. */
    public Path exportPdf(ReportData data, Filters f) {
        Session.get().require(Permission.EXPORT);
        Path dir = AppConfig.get().exportDir();
        try {
            Files.createDirectories(dir);
            String name = sanitizeFile(data.title()) + "_" + LocalDateTime.now().format(TS) + ".pdf";
            Path out = dir.resolve(name);
            try (OutputStream os = Files.newOutputStream(out)) {
                writePdf(data, f, os);
            }
            return out;
        } catch (IOException | DocumentException e) {
            log.error("Export PDF impossible", e);
            throw new com.medmaint.security.ValidationException("Export PDF impossible.");
        }
    }

    private void writePdf(ReportData data, Filters f, OutputStream os) throws DocumentException, IOException {
        Document pdf = new Document(PageSize.A4.rotate(), 30, 30, 40, 30);
        PdfWriter.getInstance(pdf, os);
        pdf.open();

        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, new Color(15, 76, 92));
        Font metaFont = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY);
        Font headFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE);
        Font cellFont = FontFactory.getFont(FontFactory.HELVETICA, 8);

        Paragraph title = new Paragraph(AppConfig.APP_NAME + " — " + data.title(), titleFont);
        title.setAlignment(Element.ALIGN_LEFT);
        pdf.add(title);

        StringBuilder meta = new StringBuilder();
        meta.append("Généré le ").append(LocalDate.now())
                .append(" par ").append(Session.get() != null ? Session.get().fullName() : "—");
        if (f != null && (f.from() != null || f.to() != null)) {
            meta.append(" · Période : ").append(f.from() != null ? f.from() : "…")
                    .append(" → ").append(f.to() != null ? f.to() : "…");
        }
        Paragraph metaP = new Paragraph(meta.toString(), metaFont);
        metaP.setSpacingAfter(8);
        pdf.add(metaP);

        if (data.isEmpty()) {
            pdf.add(new Paragraph("Aucune donnée pour les filtres sélectionnés.", cellFont));
            pdf.close();
            return;
        }

        PdfPTable table = new PdfPTable(data.headers().size());
        table.setWidthPercentage(100);
        table.setWidths(widths(data));

        for (String h : data.headers()) {
            PdfPCell c = new PdfPCell(new Phrase(h, headFont));
            c.setBackgroundColor(new Color(15, 76, 92));
            c.setPadding(4);
            c.setHorizontalAlignment(Element.ALIGN_LEFT);
            table.addCell(c);
        }
        for (List<String> row : data.rows()) {
            for (String cell : row) {
                PdfPCell c = new PdfPCell(new Phrase(cell == null ? "" : cell, cellFont));
                c.setPadding(3);
                table.addCell(c);
            }
        }
        pdf.add(table);
        pdf.close();
    }

    private static float[] widths(ReportData data) {
        int n = Math.max(1, data.headers().size());
        float[] w = new float[n];
        float total = 0;
        for (int i = 0; i < n; i++) {
            float len = Math.max(6, data.headers().get(i).length() + 4);
            w[i] = len;
            total += len;
        }
        for (int i = 0; i < n; i++) {
            w[i] = w[i] / total * 100;
        }
        return w;
    }

    // ---------- Helpers ----------

    private void appendFilters(StringBuilder sql, List<Object> params, Filters f, boolean aliasEq) {
        String eq = aliasEq ? "e." : "e.";
        if (f == null) return;
        if (f.from() != null) {
            // pas de date commune à tous les rapports : filtrage par équipement/service/catégorie
        }
        if (f.departmentId() != null) {
            sql.append(" AND ").append(eq).append("department_id = ?");
            params.add(f.departmentId());
        }
        if (f.categoryId() != null) {
            sql.append(" AND ").append(eq).append("category_id = ?");
            params.add(f.categoryId());
        }
        if (f.equipmentId() != null) {
            sql.append(" AND ").append(eq).append("id = ?");
            params.add(f.equipmentId());
        }
        if (f.status() != null && !f.status().isBlank()) {
            sql.append(" AND ").append(eq).append("status = ?");
            params.add(f.status());
        }
    }

    private static Jdbc.RowMapper<Object[]> rowMapper() {
        return rs -> {
            int n = rs.getMetaData().getColumnCount();
            Object[] out = new Object[n];
            for (int i = 1; i <= n; i++) {
                out[i - 1] = rs.getObject(i);
            }
            return out;
        };
    }

    private static String str(Object o) {
        if (o == null) return "—";
        if (o instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        }
        if (o instanceof java.sql.Date d) {
            return d.toLocalDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        }
        if (o instanceof java.time.LocalDate d) {
            return d.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        }
        if (o instanceof java.math.BigDecimal bd) {
            return bd.stripTrailingZeros().toPlainString();
        }
        return String.valueOf(o);
    }

    private static String statusPlan(Object nextDue, String stored) {
        if (nextDue == null) return stored == null ? "—" : stored;
        LocalDate d = nextDue instanceof java.sql.Date sd ? sd.toLocalDate() : LocalDate.now();
        if (d.isBefore(LocalDate.now())) return "En retard";
        if (d.equals(LocalDate.now())) return "Aujourd'hui";
        if (!d.isAfter(LocalDate.now().plusDays(7))) return "Cette semaine";
        return "À venir";
    }

    private static String contractStatus(String stored, java.sql.Date start, java.sql.Date end) {
        if ("SUSPENDU".equals(stored)) return "Suspendu";
        LocalDate today = LocalDate.now();
        if (end != null) {
            LocalDate e = end.toLocalDate();
            if (today.isAfter(e)) return "Expiré";
            if (!today.plusDays(60).isBefore(e)) return "Expirant bientôt";
        }
        return "Actif";
    }

    private static String escapeCsv(String v) {
        if (v == null) return "";
        if (v.contains(";") || v.contains("\"") || v.contains("\n")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    private static String sanitizeFile(String s) {
        return s.replaceAll("[^\\p{L}\\p{N}]+", "_");
    }

    /** Petit helper d'affichage de % de disponibilité par équipement. */
    private static final class BigDecimalAvailability {
        String percent(java.math.BigDecimal downtimeHours) {
            double downtime = downtimeHours == null ? 0 : downtimeHours.doubleValue();
            double total = 24.0 * 365;
            double pct = Math.max(0, (total - downtime) / total * 100);
            return String.format("%.1f %%", pct);
        }
    }
}
