package com.medmaint.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Technicien de maintenance / ingénieur biomédical. */
public class Technician {

    private long id;
    private Long userId;
    private String employeeNo;
    private String fullName;
    private Long departmentId;
    private String departmentName; // affichage
    private String specialty;
    private String phone;
    private String email;
    private String certifications;
    private String status;         // ACTIF | EN_CONGE | INACTIF
    private String availability;   // DISPONIBLE | OCCUPE | ABSENT
    private LocalDateTime createdAt;

    // --- statistiques calculées (agrégats) ---
    private long assignedCount;
    private long completedCount;
    private long openCount;
    private BigDecimal avgDurationHours;
    private String skillsCsv;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getEmployeeNo() { return employeeNo; }
    public void setEmployeeNo(String employeeNo) { this.employeeNo = employeeNo; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }

    public String getDepartmentName() { return departmentName; }
    public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }

    public String getSpecialty() { return specialty; }
    public void setSpecialty(String specialty) { this.specialty = specialty; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getCertifications() { return certifications; }
    public void setCertifications(String certifications) { this.certifications = certifications; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getAvailability() { return availability; }
    public void setAvailability(String availability) { this.availability = availability; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public long getAssignedCount() { return assignedCount; }
    public void setAssignedCount(long assignedCount) { this.assignedCount = assignedCount; }

    public long getCompletedCount() { return completedCount; }
    public void setCompletedCount(long completedCount) { this.completedCount = completedCount; }

    public long getOpenCount() { return openCount; }
    public void setOpenCount(long openCount) { this.openCount = openCount; }

    public BigDecimal getAvgDurationHours() { return avgDurationHours; }
    public void setAvgDurationHours(BigDecimal avgDurationHours) { this.avgDurationHours = avgDurationHours; }

    public String getSkillsCsv() { return skillsCsv; }
    public void setSkillsCsv(String skillsCsv) { this.skillsCsv = skillsCsv; }

    /** Charge simple : interventions ouvertes (0 = disponible). */
    public boolean isAvailable() {
        return "DISPONIBLE".equalsIgnoreCase(availability) && "ACTIF".equalsIgnoreCase(status);
    }

    public String initials() {
        if (fullName == null || fullName.isBlank()) return "?";
        String[] parts = fullName.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(2, parts.length); i++) {
            if (!parts[i].isEmpty()) sb.append(Character.toUpperCase(parts[i].charAt(0)));
        }
        return sb.toString();
    }

    @Override
    public String toString() { return fullName != null ? fullName : employeeNo; }
}
