package com.medmaint.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Contrat de maintenance avec un fournisseur / sous-traitant. */
public class Contract {

    private long id;
    private String contractNo;
    private Long supplierId;
    private String supplierName;   // affichage
    private String title;
    private String contractType;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal annualCost;
    private String sla;
    private String responseTime;
    private String contactName;
    private String contactPhone;
    private String contactEmail;
    private String status;         // ACTIF | SUSPENDU (statut effectif dérivé des dates)
    private String notes;
    private LocalDateTime createdAt;
    private Integer equipmentCount; // affichage

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getContractNo() { return contractNo; }
    public void setContractNo(String contractNo) { this.contractNo = contractNo; }

    public Long getSupplierId() { return supplierId; }
    public void setSupplierId(Long supplierId) { this.supplierId = supplierId; }

    public String getSupplierName() { return supplierName; }
    public void setSupplierName(String supplierName) { this.supplierName = supplierName; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContractType() { return contractType; }
    public void setContractType(String contractType) { this.contractType = contractType; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public BigDecimal getAnnualCost() { return annualCost; }
    public void setAnnualCost(BigDecimal annualCost) { this.annualCost = annualCost; }

    public String getSla() { return sla; }
    public void setSla(String sla) { this.sla = sla; }

    public String getResponseTime() { return responseTime; }
    public void setResponseTime(String responseTime) { this.responseTime = responseTime; }

    public String getContactName() { return contactName; }
    public void setContactName(String contactName) { this.contactName = contactName; }

    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }

    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public Integer getEquipmentCount() { return equipmentCount; }
    public void setEquipmentCount(Integer equipmentCount) { this.equipmentCount = equipmentCount; }

    @Override
    public String toString() { return contractNo != null ? contractNo + " — " + title : title; }
}
