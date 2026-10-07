package com.medmaint.domain.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Plan de maintenance préventive pour un équipement. */
public class MaintenancePlan {

    private long id;
    private long equipmentId;
    private String equipmentCode;   // affichage
    private String equipmentName;   // affichage
    private String equipmentStatus; // affichage
    private long categoryId;        // affichage
    private String maintenanceType;
    private String frequency;       // enum MaintenanceFrequency
    private Integer customDays;
    private Long checklistId;
    private String checklistName;   // affichage
    private Long responsibleId;
    private String responsibleName; // affichage
    private LocalDate lastPerformed;
    private LocalDate nextDue;
    private Integer estimatedDurationMin;
    private String requiredParts;
    private String requiredTools;
    private String instructions;
    private String status;          // état calculé / stocké
    private boolean active = true;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getEquipmentId() { return equipmentId; }
    public void setEquipmentId(long equipmentId) { this.equipmentId = equipmentId; }

    public String getEquipmentCode() { return equipmentCode; }
    public void setEquipmentCode(String equipmentCode) { this.equipmentCode = equipmentCode; }

    public String getEquipmentName() { return equipmentName; }
    public void setEquipmentName(String equipmentName) { this.equipmentName = equipmentName; }

    public String getEquipmentStatus() { return equipmentStatus; }
    public void setEquipmentStatus(String equipmentStatus) { this.equipmentStatus = equipmentStatus; }

    public long getCategoryId() { return categoryId; }
    public void setCategoryId(long categoryId) { this.categoryId = categoryId; }

    public String getMaintenanceType() { return maintenanceType; }
    public void setMaintenanceType(String maintenanceType) { this.maintenanceType = maintenanceType; }

    public String getFrequency() { return frequency; }
    public void setFrequency(String frequency) { this.frequency = frequency; }

    public Integer getCustomDays() { return customDays; }
    public void setCustomDays(Integer customDays) { this.customDays = customDays; }

    public Long getChecklistId() { return checklistId; }
    public void setChecklistId(Long checklistId) { this.checklistId = checklistId; }

    public String getChecklistName() { return checklistName; }
    public void setChecklistName(String checklistName) { this.checklistName = checklistName; }

    public Long getResponsibleId() { return responsibleId; }
    public void setResponsibleId(Long responsibleId) { this.responsibleId = responsibleId; }

    public String getResponsibleName() { return responsibleName; }
    public void setResponsibleName(String responsibleName) { this.responsibleName = responsibleName; }

    public LocalDate getLastPerformed() { return lastPerformed; }
    public void setLastPerformed(LocalDate lastPerformed) { this.lastPerformed = lastPerformed; }

    public LocalDate getNextDue() { return nextDue; }
    public void setNextDue(LocalDate nextDue) { this.nextDue = nextDue; }

    public Integer getEstimatedDurationMin() { return estimatedDurationMin; }
    public void setEstimatedDurationMin(Integer estimatedDurationMin) { this.estimatedDurationMin = estimatedDurationMin; }

    public String getRequiredParts() { return requiredParts; }
    public void setRequiredParts(String requiredParts) { this.requiredParts = requiredParts; }

    public String getRequiredTools() { return requiredTools; }
    public void setRequiredTools(String requiredTools) { this.requiredTools = requiredTools; }

    public String getInstructions() { return instructions; }
    public void setInstructions(String instructions) { this.instructions = instructions; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public String toString() {
        return (equipmentCode != null ? equipmentCode + " — " : "") + maintenanceType;
    }
}
