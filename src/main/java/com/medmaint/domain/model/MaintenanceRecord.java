package com.medmaint.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Historique de maintenance réalisée (préventive ou corrective) + résultats de checklist. */
public class MaintenanceRecord {

    private long id;
    private long equipmentId;
    private String equipmentCode;   // affichage
    private String equipmentName;   // affichage
    private Long planId;
    private String planType;        // affichage
    private Long workOrderId;
    private String workOrderNo;     // affichage
    private Long checklistId;
    private String checklistName;   // affichage
    private String recordType;      // PREVENTIVE | CORRECTIVE
    private Long performedBy;
    private String performedByName; // affichage
    private LocalDateTime performedAt;
    private String result;          // enum ChecklistResult
    private String observations;
    private Integer durationMin;
    private BigDecimal cost;
    private LocalDateTime createdAt;

    private List<ChecklistResultEntry> checklistResults = new ArrayList<>();

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getEquipmentId() { return equipmentId; }
    public void setEquipmentId(long equipmentId) { this.equipmentId = equipmentId; }

    public String getEquipmentCode() { return equipmentCode; }
    public void setEquipmentCode(String equipmentCode) { this.equipmentCode = equipmentCode; }

    public String getEquipmentName() { return equipmentName; }
    public void setEquipmentName(String equipmentName) { this.equipmentName = equipmentName; }

    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }

    public String getPlanType() { return planType; }
    public void setPlanType(String planType) { this.planType = planType; }

    public Long getWorkOrderId() { return workOrderId; }
    public void setWorkOrderId(Long workOrderId) { this.workOrderId = workOrderId; }

    public String getWorkOrderNo() { return workOrderNo; }
    public void setWorkOrderNo(String workOrderNo) { this.workOrderNo = workOrderNo; }

    public Long getChecklistId() { return checklistId; }
    public void setChecklistId(Long checklistId) { this.checklistId = checklistId; }

    public String getChecklistName() { return checklistName; }
    public void setChecklistName(String checklistName) { this.checklistName = checklistName; }

    public String getRecordType() { return recordType; }
    public void setRecordType(String recordType) { this.recordType = recordType; }

    public Long getPerformedBy() { return performedBy; }
    public void setPerformedBy(Long performedBy) { this.performedBy = performedBy; }

    public String getPerformedByName() { return performedByName; }
    public void setPerformedByName(String performedByName) { this.performedByName = performedByName; }

    public LocalDateTime getPerformedAt() { return performedAt; }
    public void setPerformedAt(LocalDateTime performedAt) { this.performedAt = performedAt; }

    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }

    public String getObservations() { return observations; }
    public void setObservations(String observations) { this.observations = observations; }

    public Integer getDurationMin() { return durationMin; }
    public void setDurationMin(Integer durationMin) { this.durationMin = durationMin; }

    public BigDecimal getCost() { return cost; }
    public void setCost(BigDecimal cost) { this.cost = cost; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public List<ChecklistResultEntry> getChecklistResults() { return checklistResults; }
    public void setChecklistResults(List<ChecklistResultEntry> checklistResults) {
        this.checklistResults = checklistResults != null ? checklistResults : new ArrayList<>();
    }

    /** Résultat d'un item de checklist pour cet historique. */
    public static class ChecklistResultEntry {
        private long id;
        private long itemId;
        private String itemLabel;
        private String result;   // CONFORME | NON_CONFORME | NON_APPLICABLE
        private String notes;

        public long getId() { return id; }
        public void setId(long id) { this.id = id; }
        public long getItemId() { return itemId; }
        public void setItemId(long itemId) { this.itemId = itemId; }
        public String getItemLabel() { return itemLabel; }
        public void setItemLabel(String itemLabel) { this.itemLabel = itemLabel; }
        public String getResult() { return result; }
        public void setResult(String result) { this.result = result; }
        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
    }

    @Override
    public String toString() {
        return (equipmentCode != null ? equipmentCode : "") + " " + (performedAt != null ? performedAt.toLocalDate() : "");
    }
}
