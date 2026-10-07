package com.medmaint.domain.model;

import com.medmaint.domain.enums.Priority;
import com.medmaint.domain.enums.RequestStatus;

import java.time.LocalDateTime;

/** Demande d'intervention (signalement de panne) déposée par le personnel. */
public class InterventionRequest {

    private long id;
    private String requestNo;
    private long equipmentId;
    private String equipmentCode;    // affichage
    private String equipmentName;    // affichage
    private long departmentId;
    private String departmentName;   // affichage
    private long requesterId;
    private String requesterName;    // affichage
    private LocalDateTime reportedAt;
    private String problemDescription;
    private String symptoms;
    private String priority;         // enum Priority
    private boolean safetyImpact;
    private String availability;     // INDISPONIBLE | DEGRADE | TOUTE_FONCTION | AUTRE
    private String status;           // enum RequestStatus
    private Long assignedTechnicianId;
    private String assignedTechnicianName; // affichage
    private Long workOrderId;        // ordre de travail lié
    private String workOrderNo;      // affichage
    private String rejectionReason;
    private String resolutionNote;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getRequestNo() { return requestNo; }
    public void setRequestNo(String requestNo) { this.requestNo = requestNo; }

    public long getEquipmentId() { return equipmentId; }
    public void setEquipmentId(long equipmentId) { this.equipmentId = equipmentId; }

    public String getEquipmentCode() { return equipmentCode; }
    public void setEquipmentCode(String equipmentCode) { this.equipmentCode = equipmentCode; }

    public String getEquipmentName() { return equipmentName; }
    public void setEquipmentName(String equipmentName) { this.equipmentName = equipmentName; }

    public long getDepartmentId() { return departmentId; }
    public void setDepartmentId(long departmentId) { this.departmentId = departmentId; }

    public String getDepartmentName() { return departmentName; }
    public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }

    public long getRequesterId() { return requesterId; }
    public void setRequesterId(long requesterId) { this.requesterId = requesterId; }

    public String getRequesterName() { return requesterName; }
    public void setRequesterName(String requesterName) { this.requesterName = requesterName; }

    public LocalDateTime getReportedAt() { return reportedAt; }
    public void setReportedAt(LocalDateTime reportedAt) { this.reportedAt = reportedAt; }

    public String getProblemDescription() { return problemDescription; }
    public void setProblemDescription(String problemDescription) { this.problemDescription = problemDescription; }

    public String getSymptoms() { return symptoms; }
    public void setSymptoms(String symptoms) { this.symptoms = symptoms; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public Priority priorityEnum() { return Priority.fromCode(priority); }

    public boolean isSafetyImpact() { return safetyImpact; }
    public void setSafetyImpact(boolean safetyImpact) { this.safetyImpact = safetyImpact; }

    public String getAvailability() { return availability; }
    public void setAvailability(String availability) { this.availability = availability; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public RequestStatus statusEnum() { return RequestStatus.fromCode(status); }

    public Long getAssignedTechnicianId() { return assignedTechnicianId; }
    public void setAssignedTechnicianId(Long assignedTechnicianId) { this.assignedTechnicianId = assignedTechnicianId; }

    public String getAssignedTechnicianName() { return assignedTechnicianName; }
    public void setAssignedTechnicianName(String assignedTechnicianName) { this.assignedTechnicianName = assignedTechnicianName; }

    public Long getWorkOrderId() { return workOrderId; }
    public void setWorkOrderId(Long workOrderId) { this.workOrderId = workOrderId; }

    public String getWorkOrderNo() { return workOrderNo; }
    public void setWorkOrderNo(String workOrderNo) { this.workOrderNo = workOrderNo; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    public String getResolutionNote() { return resolutionNote; }
    public void setResolutionNote(String resolutionNote) { this.resolutionNote = resolutionNote; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public boolean isCritical() {
        return priorityEnum() == Priority.CRITIQUE || safetyImpact;
    }

    @Override
    public String toString() { return requestNo; }
}
