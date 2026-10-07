package com.medmaint.domain.model;

import com.medmaint.domain.enums.Criticality;
import com.medmaint.domain.enums.EquipmentStatus;
import com.medmaint.domain.enums.MaintenanceFrequency;
import com.medmaint.domain.enums.RiskLevel;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Équipement biomédical / technique.
 * Regroupe identification, classification, localisation, acquisition,
 * maintenance et informations techniques.
 */
public class Equipment {

    private long id;
    private String code;
    private String name;
    private long categoryId;
    private String categoryName;      // affichage (jointure)
    private String subcategory;
    private Long manufacturerId;
    private String manufacturerName;  // affichage (jointure)
    private String model;
    private String serialNumber;
    private String referenceNumber;
    private String equipmentType;
    private String criticality;       // enum Criticality
    private String riskLevel;         // enum RiskLevel
    private String deviceClass;
    private String status;            // enum EquipmentStatus
    private long departmentId;
    private String departmentName;    // affichage (jointure)
    private Long locationId;
    private String locationName;      // affichage (jointure)
    private String locationDetails;
    private Long supplierId;
    private String supplierName;      // affichage (jointure)
    private LocalDate purchaseDate;
    private LocalDate receptionDate;
    private LocalDate commissioningDate;
    private BigDecimal purchasePrice;
    private Integer warrantyMonths;
    private LocalDate warrantyEndDate;
    private String maintenanceType;
    private String pmFrequency;       // enum MaintenanceFrequency
    private LocalDate lastMaintenanceDate;
    private LocalDate nextMaintenanceDate;
    private Long responsibleId;
    private String responsibleName;   // affichage (jointure)
    private Long contractId;
    private String contractNo;        // affichage (jointure)
    private String powerSupply;
    private String powerRating;
    private String dimensions;
    private BigDecimal weightKg;
    private Integer fabricationYear;
    private String notes;
    private boolean archived;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public long getCategoryId() { return categoryId; }
    public void setCategoryId(long categoryId) { this.categoryId = categoryId; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public String getSubcategory() { return subcategory; }
    public void setSubcategory(String subcategory) { this.subcategory = subcategory; }

    public Long getManufacturerId() { return manufacturerId; }
    public void setManufacturerId(Long manufacturerId) { this.manufacturerId = manufacturerId; }

    public String getManufacturerName() { return manufacturerName; }
    public void setManufacturerName(String manufacturerName) { this.manufacturerName = manufacturerName; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }

    public String getReferenceNumber() { return referenceNumber; }
    public void setReferenceNumber(String referenceNumber) { this.referenceNumber = referenceNumber; }

    public String getEquipmentType() { return equipmentType; }
    public void setEquipmentType(String equipmentType) { this.equipmentType = equipmentType; }

    public String getCriticality() { return criticality; }
    public void setCriticality(String criticality) { this.criticality = criticality; }
    public Criticality criticalityEnum() { return Criticality.fromCode(criticality); }

    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public RiskLevel riskLevelEnum() { return RiskLevel.fromCode(riskLevel); }

    public String getDeviceClass() { return deviceClass; }
    public void setDeviceClass(String deviceClass) { this.deviceClass = deviceClass; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public EquipmentStatus statusEnum() { return EquipmentStatus.fromCode(status); }

    public long getDepartmentId() { return departmentId; }
    public void setDepartmentId(long departmentId) { this.departmentId = departmentId; }

    public String getDepartmentName() { return departmentName; }
    public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }

    public Long getLocationId() { return locationId; }
    public void setLocationId(Long locationId) { this.locationId = locationId; }

    public String getLocationName() { return locationName; }
    public void setLocationName(String locationName) { this.locationName = locationName; }

    public String getLocationDetails() { return locationDetails; }
    public void setLocationDetails(String locationDetails) { this.locationDetails = locationDetails; }

    /** Localisation affichée : service + lieu + précision. */
    public String getFullLocation() {
        StringBuilder sb = new StringBuilder();
        if (departmentName != null) sb.append(departmentName);
        if (locationName != null && !locationName.isBlank()) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(locationName);
        }
        if (locationDetails != null && !locationDetails.isBlank()) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(locationDetails);
        }
        return sb.toString();
    }

    public Long getSupplierId() { return supplierId; }
    public void setSupplierId(Long supplierId) { this.supplierId = supplierId; }

    public String getSupplierName() { return supplierName; }
    public void setSupplierName(String supplierName) { this.supplierName = supplierName; }

    public LocalDate getPurchaseDate() { return purchaseDate; }
    public void setPurchaseDate(LocalDate purchaseDate) { this.purchaseDate = purchaseDate; }

    public LocalDate getReceptionDate() { return receptionDate; }
    public void setReceptionDate(LocalDate receptionDate) { this.receptionDate = receptionDate; }

    public LocalDate getCommissioningDate() { return commissioningDate; }
    public void setCommissioningDate(LocalDate commissioningDate) { this.commissioningDate = commissioningDate; }

    public BigDecimal getPurchasePrice() { return purchasePrice; }
    public void setPurchasePrice(BigDecimal purchasePrice) { this.purchasePrice = purchasePrice; }

    public Integer getWarrantyMonths() { return warrantyMonths; }
    public void setWarrantyMonths(Integer warrantyMonths) { this.warrantyMonths = warrantyMonths; }

    public LocalDate getWarrantyEndDate() { return warrantyEndDate; }
    public void setWarrantyEndDate(LocalDate warrantyEndDate) { this.warrantyEndDate = warrantyEndDate; }

    /** @return true si la garantie est encore valide à la date donnée. */
    public boolean isUnderWarranty(LocalDate today) {
        return warrantyEndDate != null && !today.isAfter(warrantyEndDate);
    }

    public String getMaintenanceType() { return maintenanceType; }
    public void setMaintenanceType(String maintenanceType) { this.maintenanceType = maintenanceType; }

    public String getPmFrequency() { return pmFrequency; }
    public void setPmFrequency(String pmFrequency) { this.pmFrequency = pmFrequency; }
    public MaintenanceFrequency pmFrequencyEnum() { return MaintenanceFrequency.fromCode(pmFrequency); }

    public LocalDate getLastMaintenanceDate() { return lastMaintenanceDate; }
    public void setLastMaintenanceDate(LocalDate lastMaintenanceDate) { this.lastMaintenanceDate = lastMaintenanceDate; }

    public LocalDate getNextMaintenanceDate() { return nextMaintenanceDate; }
    public void setNextMaintenanceDate(LocalDate nextMaintenanceDate) { this.nextMaintenanceDate = nextMaintenanceDate; }

    public Long getResponsibleId() { return responsibleId; }
    public void setResponsibleId(Long responsibleId) { this.responsibleId = responsibleId; }

    public String getResponsibleName() { return responsibleName; }
    public void setResponsibleName(String responsibleName) { this.responsibleName = responsibleName; }

    public Long getContractId() { return contractId; }
    public void setContractId(Long contractId) { this.contractId = contractId; }

    public String getContractNo() { return contractNo; }
    public void setContractNo(String contractNo) { this.contractNo = contractNo; }

    public String getPowerSupply() { return powerSupply; }
    public void setPowerSupply(String powerSupply) { this.powerSupply = powerSupply; }

    public String getPowerRating() { return powerRating; }
    public void setPowerRating(String powerRating) { this.powerRating = powerRating; }

    public String getDimensions() { return dimensions; }
    public void setDimensions(String dimensions) { this.dimensions = dimensions; }

    public BigDecimal getWeightKg() { return weightKg; }
    public void setWeightKg(BigDecimal weightKg) { this.weightKg = weightKg; }

    public Integer getFabricationYear() { return fabricationYear; }
    public void setFabricationYear(Integer fabricationYear) { this.fabricationYear = fabricationYear; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public boolean isArchived() { return archived; }
    public void setArchived(boolean archived) { this.archived = archived; }

    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public boolean isRetired() {
        EquipmentStatus s = statusEnum();
        return s == EquipmentStatus.REFORME || s == EquipmentStatus.PERDU;
    }
}
