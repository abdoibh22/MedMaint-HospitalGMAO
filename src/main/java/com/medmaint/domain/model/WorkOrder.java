package com.medmaint.domain.model;

import com.medmaint.domain.enums.Priority;
import com.medmaint.domain.enums.WorkOrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Ordre de travail / intervention corrective. */
public class WorkOrder {

    private long id;
    private String woNo;
    private Long requestId;
    private String requestNo;        // affichage
    private long equipmentId;
    private String equipmentCode;    // affichage
    private String equipmentName;    // affichage
    private String equipmentSerial;  // affichage
    private String departmentName;   // affichage
    private Long technicianId;
    private String technicianName;   // affichage
    private String team;
    private String priority;
    private String status;
    private LocalDateTime openedAt;
    private LocalDateTime scheduledAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime validatedAt;
    private LocalDateTime closedAt;
    private String diagnosis;
    private String failureCause;
    private String correctiveAction;
    private String workPerformed;
    private String failureType;
    private BigDecimal laborHours;
    private BigDecimal downtimeHours;
    private BigDecimal laborCost;
    private BigDecimal partsCost;
    private BigDecimal totalCost;
    private String comments;
    private Long createdBy;
    private String createdByName;    // affichage
    private Long validatedBy;
    private String validatedByName;  // affichage
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getWoNo() { return woNo; }
    public void setWoNo(String woNo) { this.woNo = woNo; }

    public Long getRequestId() { return requestId; }
    public void setRequestId(Long requestId) { this.requestId = requestId; }

    public String getRequestNo() { return requestNo; }
    public void setRequestNo(String requestNo) { this.requestNo = requestNo; }

    public long getEquipmentId() { return equipmentId; }
    public void setEquipmentId(long equipmentId) { this.equipmentId = equipmentId; }

    public String getEquipmentCode() { return equipmentCode; }
    public void setEquipmentCode(String equipmentCode) { this.equipmentCode = equipmentCode; }

    public String getEquipmentName() { return equipmentName; }
    public void setEquipmentName(String equipmentName) { this.equipmentName = equipmentName; }

    public String getEquipmentSerial() { return equipmentSerial; }
    public void setEquipmentSerial(String equipmentSerial) { this.equipmentSerial = equipmentSerial; }

    public String getDepartmentName() { return departmentName; }
    public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }

    public Long getTechnicianId() { return technicianId; }
    public void setTechnicianId(Long technicianId) { this.technicianId = technicianId; }

    public String getTechnicianName() { return technicianName; }
    public void setTechnicianName(String technicianName) { this.technicianName = technicianName; }

    public String getTeam() { return team; }
    public void setTeam(String team) { this.team = team; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public Priority priorityEnum() { return Priority.fromCode(priority); }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public WorkOrderStatus statusEnum() { return WorkOrderStatus.fromCode(status); }

    public LocalDateTime getOpenedAt() { return openedAt; }
    public void setOpenedAt(LocalDateTime openedAt) { this.openedAt = openedAt; }

    public LocalDateTime getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(LocalDateTime scheduledAt) { this.scheduledAt = scheduledAt; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    public LocalDateTime getValidatedAt() { return validatedAt; }
    public void setValidatedAt(LocalDateTime validatedAt) { this.validatedAt = validatedAt; }

    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime closedAt) { this.closedAt = closedAt; }

    public String getDiagnosis() { return diagnosis; }
    public void setDiagnosis(String diagnosis) { this.diagnosis = diagnosis; }

    public String getFailureCause() { return failureCause; }
    public void setFailureCause(String failureCause) { this.failureCause = failureCause; }

    public String getCorrectiveAction() { return correctiveAction; }
    public void setCorrectiveAction(String correctiveAction) { this.correctiveAction = correctiveAction; }

    public String getWorkPerformed() { return workPerformed; }
    public void setWorkPerformed(String workPerformed) { this.workPerformed = workPerformed; }

    public String getFailureType() { return failureType; }
    public void setFailureType(String failureType) { this.failureType = failureType; }

    public BigDecimal getLaborHours() { return laborHours; }
    public void setLaborHours(BigDecimal laborHours) { this.laborHours = laborHours; }

    public BigDecimal getDowntimeHours() { return downtimeHours; }
    public void setDowntimeHours(BigDecimal downtimeHours) { this.downtimeHours = downtimeHours; }

    public BigDecimal getLaborCost() { return laborCost; }
    public void setLaborCost(BigDecimal laborCost) { this.laborCost = laborCost; }

    public BigDecimal getPartsCost() { return partsCost; }
    public void setPartsCost(BigDecimal partsCost) { this.partsCost = partsCost; }

    public BigDecimal getTotalCost() { return totalCost; }
    public void setTotalCost(BigDecimal totalCost) { this.totalCost = totalCost; }

    public String getComments() { return comments; }
    public void setComments(String comments) { this.comments = comments; }

    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }

    public String getCreatedByName() { return createdByName; }
    public void setCreatedByName(String createdByName) { this.createdByName = createdByName; }

    public Long getValidatedBy() { return validatedBy; }
    public void setValidatedBy(Long validatedBy) { this.validatedBy = validatedBy; }

    public String getValidatedByName() { return validatedByName; }
    public void setValidatedByName(String validatedByName) { this.validatedByName = validatedByName; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public boolean isOverdue() {
        return statusEnum().isOpen() && scheduledAt != null
                && scheduledAt.isBefore(LocalDateTime.now());
    }

    @Override
    public String toString() { return woNo; }
}
