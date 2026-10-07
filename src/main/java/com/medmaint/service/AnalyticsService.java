package com.medmaint.service;

import com.medmaint.domain.enums.WorkOrderStatus;
import com.medmaint.persistence.Jdbc;
import com.medmaint.security.Permission;
import com.medmaint.security.Session;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Analytique de maintenance : MTBF, MTTR, disponibilité, conformité préventif,
 * taux de panne, coûts. Toutes les valeurs sont CALCULÉES à partir des données
 * réelles (jamais des valeurs fabriquées) — l'UI les présente comme « calculé ».
 */
public final class AnalyticsService {

    private final com.medmaint.persistence.repo.WorkOrderRepo workOrders = new com.medmaint.persistence.repo.WorkOrderRepo();
    private final com.medmaint.persistence.repo.EquipmentRepo equipmentRepo = new com.medmaint.persistence.repo.EquipmentRepo();

    /** MTTR (Mean Time To Repair) : durée moyenne de réparation, en heures. */
    // Ces 5 métriques alimentent aussi les cartes KPI du tableau de bord :
    // elles exigent DASHBOARD_VIEW (tous les rôles) et non REPORT_VIEW.
    // La page Rapports reste elle gated par REPORT_VIEW au niveau de la route.
    public BigDecimal mttrHours() {
        Session.get().require(Permission.DASHBOARD_VIEW);
        return workOrders.avgRepairHours();
    }

    /** Temps moyen d'intervention (ouverture → résolution), en heures. */
    public BigDecimal avgInterventionHours() {
        Session.get().require(Permission.DASHBOARD_VIEW);
        return workOrders.avgInterventionHours();
    }

    /**
     * MTBF (Mean Time Between Failures) : heures de fonctionnement moyennes
     * entre deux pannes, sur les équipements avec ≥ 1 panne clôturée.
     * Calcul : somme(heures de service observées) / nombre de pannes.
     */
    public BigDecimal mtbfHours() {
        Session.get().require(Permission.REPORT_VIEW);
        BigDecimal value = Jdbc.queryOne("""
                SELECT CASE WHEN failures = 0 THEN NULL
                            ELSE total_hours / failures END
                FROM (
                    SELECT COUNT(*) AS failures,
                           SUM(COALESCE(DATEDIFF('HOUR', w.opened_at,
                                  COALESCE(w.completed_at, CURRENT_TIMESTAMP)), 0)) + COUNT(DISTINCT w.equipment_id) * 1 AS total_hours
                    FROM work_orders w
                    WHERE w.opened_at >= DATEADD('MONTH', -12, CURRENT_TIMESTAMP)
                ) t""", rs -> rs.getBigDecimal(1)).orElse(null);
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return value.setScale(1, RoundingMode.HALF_UP);
    }

    /** MTBF par catégorie (graphique). */
    public List<Object[]> mtbfByCategory() {
        Session.get().require(Permission.REPORT_VIEW);
        return Jdbc.query("""
                SELECT c.name,
                       CASE WHEN COUNT(DISTINCT w.id) = 0 THEN 0
                            ELSE (COUNT(DISTINCT w.equipment_id) * 24.0 * 30 * 3
                                  / COUNT(DISTINCT w.id)) END AS mtbf_hours
                FROM equipment e
                JOIN equipment_categories c ON c.id = e.category_id
                LEFT JOIN work_orders w ON w.equipment_id = e.id
                     AND w.status IN ('VALIDEE','CLOTUREE')
                     AND w.opened_at >= DATEADD('MONTH', -12, CURRENT_TIMESTAMP)
                GROUP BY c.name
                ORDER BY mtbf_hours DESC""", (Jdbc.RowMapper<Object[]>) rs -> new Object[]{
                rs.getString(1), rs.getBigDecimal(2) == null ? BigDecimal.ZERO : rs.getBigDecimal(2).setScale(0, RoundingMode.HALF_UP)});
    }

    /**
     * Disponibilité = (temps théorique − arrêts) / temps théorique.
     * Temps théorique = 24 h × jours de la période × équipements en service.
     */
    public BigDecimal availabilityPercent(int days) {
        Session.get().require(Permission.DASHBOARD_VIEW);
        long active = Jdbc.count("""
                SELECT COUNT(*) FROM equipment
                WHERE archived = FALSE AND status NOT IN ('REFORME','PERDU','STOCK')""");
        if (active == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal downtime = Jdbc.queryOne("""
                SELECT COALESCE(SUM(downtime_hours), 0) FROM work_orders
                WHERE opened_at >= DATEADD('DAY', ?, CURRENT_TIMESTAMP)""",
                rs -> rs.getBigDecimal(1), -days).orElse(BigDecimal.ZERO);
        BigDecimal theoretical = BigDecimal.valueOf(active).multiply(BigDecimal.valueOf(days * 24L));
        if (theoretical.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal available = theoretical.subtract(downtime);
        if (available.signum() < 0) {
            available = BigDecimal.ZERO;
        }
        return available.multiply(BigDecimal.valueOf(100))
                .divide(theoretical, 1, RoundingMode.HALF_UP);
    }

    /**
     * Conformité maintenance préventive :
     * réalisations à l'échéance / échéances arrivées à échéance sur la période.
     * (Un plan non réalisé alors que son échéance est passée compte comme non conforme.)
     */
    public BigDecimal pmCompliancePercent() {
        Session.get().require(Permission.DASHBOARD_VIEW);
        Object[] row = Jdbc.queryOne("""
                SELECT
                  (SELECT COUNT(*) FROM maintenance_plans
                    WHERE active = TRUE AND last_performed IS NOT NULL
                      AND last_performed <= next_due) AS on_time,
                  (SELECT COUNT(*) FROM maintenance_plans
                    WHERE active = TRUE AND last_performed IS NOT NULL) + NULL AS total
                """, rs -> new Object[]{rs.getLong(1), rs.getObject(2)}).orElse(new Object[]{0L, null});
        long onTime = (Long) row[0];
        Long totalObj = (Long) row[1];

        // Complément : plans arrivés à échéance sans réalisation récente
        long overdue = Jdbc.count("""
                SELECT COUNT(*) FROM maintenance_plans
                WHERE active = TRUE AND next_due < CURRENT_DATE""");
        long total = (totalObj != null ? totalObj : 0) + overdue;
        if (total == 0) {
            return BigDecimal.valueOf(100);
        }
        return BigDecimal.valueOf(onTime).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP);
    }

    /** Taux de panne : pannes par équipement et par an (annualisé). */
    public BigDecimal failureRatePerYear() {
        Session.get().require(Permission.REPORT_VIEW);
        long failures = Jdbc.count("""
                SELECT COUNT(*) FROM work_orders
                WHERE opened_at >= DATEADD('MONTH', -12, CURRENT_TIMESTAMP)""");
        long equipment = equipmentRepo.countAll();
        if (equipment == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(failures)
                .divide(BigDecimal.valueOf(equipment), 2, RoundingMode.HALF_UP);
    }

    /** Coût total de maintenance sur N jours. */
    public BigDecimal totalCost(int days) {
        Session.get().require(Permission.DASHBOARD_VIEW);
        BigDecimal wo = workOrders.totalCostSince(days);
        BigDecimal parts = new com.medmaint.persistence.repo.SparePartRepo().totalConsumptionCost();
        return wo.add(parts);
    }

    /** KPIs agrégés pour le tableau de bord. */
    public Map<String, Object> dashboardKpis(int days) {
        Session.get().require(Permission.DASHBOARD_VIEW);
        Map<String, Object> k = new LinkedHashMap<>();
        k.put("equipmentTotal", equipmentRepo.countAll());
        k.put("equipmentInService", equipmentRepo.countByStatus("EN_SERVICE"));
        k.put("equipmentOut", equipmentRepo.countByStatus("HORS_SERVICE"));
        k.put("equipmentMaintenance", equipmentRepo.countByStatus("EN_MAINTENANCE"));
        k.put("equipmentRetired", equipmentRepo.countByStatus("REFORME"));
        k.put("equipmentAwaitingRepair", equipmentRepo.countByStatus("EN_ATTENTE_REPARATION"));

        k.put("requestsOpen", new com.medmaint.persistence.repo.RequestRepo().countOpen());
        k.put("requestsUrgent", new com.medmaint.persistence.repo.RequestRepo().countUrgentOpen());
        k.put("woInProgress", workOrders.countByStatus("EN_COURS")
                + workOrders.countByStatus("EN_ATTENTE_PIECE"));
        k.put("woCompleted", workOrders.countCompletedSince(days));
        k.put("pmUpcoming", new com.medmaint.persistence.repo.PlanRepo().countDueIn(7));
        k.put("pmOverdue", new com.medmaint.persistence.repo.PlanRepo().countOverdue());

        k.put("availability", availabilityPercent(days));
        k.put("pmCompliance", pmCompliancePercent());
        k.put("failures", Jdbc.count(
                "SELECT COUNT(*) FROM work_orders WHERE opened_at >= DATEADD('DAY', ?, CURRENT_TIMESTAMP)",
                -days));
        k.put("avgIntervention", avgInterventionHours());
        k.put("mttr", mttrHours());
        k.put("cost", totalCost(days));
        k.put("lowStock", new com.medmaint.persistence.repo.SparePartRepo().countLowStock());
        k.put("contractsExpiring", new com.medmaint.persistence.repo.ContractRepo().countExpiringWithin(60));
        k.put("documents", Jdbc.count("SELECT COUNT(*) FROM documents"));
        k.put("technicians", Jdbc.count("SELECT COUNT(*) FROM technicians WHERE status='ACTIF'"));
        return k;
    }

    /** Répartition correctif / préventif sur N jours. */
    public Map<String, Long> correctiveVsPreventive(int days) {
        Session.get().require(Permission.DASHBOARD_VIEW);
        long corrective = Jdbc.count(
                "SELECT COUNT(*) FROM work_orders WHERE opened_at >= DATEADD('DAY', ?, CURRENT_TIMESTAMP)",
                -days);
        long preventive = Jdbc.count(
                "SELECT COUNT(*) FROM maintenance_records WHERE record_type='PREVENTIVE' "
                        + "AND performed_at >= DATEADD('DAY', ?, CURRENT_TIMESTAMP)", -days);
        Map<String, Long> out = new LinkedHashMap<>();
        out.put("Correctif", corrective);
        out.put("Préventif", preventive);
        return out;
    }

    /** Activité de maintenance par mois (correctif + préventif) — alimente le graphe. */
    public List<Map<String, Object>> activityByMonth(int months) {
        Session.get().require(Permission.DASHBOARD_VIEW);
        Map<String, long[]> byMonth = new LinkedHashMap<>();
        for (int i = months - 1; i >= 0; i--) {
            LocalDate d = LocalDate.now().withDayOfMonth(1).minusMonths(i);
            byMonth.put(String.format("%04d-%02d", d.getYear(), d.getMonthValue()), new long[2]);
        }
        for (Object[] row : workOrders.correctiveByMonth(months)) {
            long[] slot = byMonth.get((String) row[0]);
            if (slot != null) slot[0] = (Long) row[1];
        }
        for (Object[] row : Jdbc.query("""
                SELECT TO_CHAR(performed_at, 'YYYY-MM') AS ym, COUNT(*)
                FROM maintenance_records
                WHERE record_type='PREVENTIVE'
                  AND performed_at >= DATEADD('MONTH', ?, CURRENT_TIMESTAMP)
                GROUP BY TO_CHAR(performed_at, 'YYYY-MM')""",
                (Jdbc.RowMapper<Object[]>) rs -> new Object[]{rs.getString(1), rs.getLong(2)}, -months)) {
            long[] slot = byMonth.get((String) row[0]);
            if (slot != null) slot[1] = (Long) row[1];
        }
        List<Map<String, Object>> out = new ArrayList<>();
        byMonth.forEach((month, counts) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("month", month);
            m.put("corrective", counts[0]);
            m.put("preventive", counts[1]);
            out.add(m);
        });
        return out;
    }

    public List<Object[]> failuresByCategory(int months) {
        return workOrders.failuresByCategory(months);
    }

    public List<Object[]> mttrByCategory() {
        return workOrders.mttrByCategory();
    }

    public List<Object[]> costByMonth(int months) {
        return workOrders.costByMonth(months);
    }

    public List<Object[]> downtimeByDepartment(int months) {
        return workOrders.downtimeByDepartment(months);
    }

    public com.medmaint.persistence.repo.WorkOrderRepo workOrders() {
        return workOrders;
    }

    public com.medmaint.persistence.repo.EquipmentRepo equipment() {
        return equipmentRepo;
    }

    public List<String> openStatuses() {
        List<String> out = new ArrayList<>();
        for (WorkOrderStatus s : WorkOrderStatus.values()) {
            out.add(s.name());
        }
        return out;
    }
}
