package com.medmaint.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Pièce de rechange (inventaire). */
public class SparePart {

    private long id;
    private String partNo;
    private String name;
    private String category;
    private String manufacturer;
    private String compatibleEquipment;
    private int quantity;
    private int minQuantity;
    private int maxQuantity;
    private BigDecimal unitCost;
    private Long supplierId;
    private String supplierName;    // affichage
    private String storageLocation;
    private LocalDate expiryDate;
    private boolean archived;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getPartNo() { return partNo; }
    public void setPartNo(String partNo) { this.partNo = partNo; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getManufacturer() { return manufacturer; }
    public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; }

    public String getCompatibleEquipment() { return compatibleEquipment; }
    public void setCompatibleEquipment(String compatibleEquipment) { this.compatibleEquipment = compatibleEquipment; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public int getMinQuantity() { return minQuantity; }
    public void setMinQuantity(int minQuantity) { this.minQuantity = minQuantity; }

    public int getMaxQuantity() { return maxQuantity; }
    public void setMaxQuantity(int maxQuantity) { this.maxQuantity = maxQuantity; }

    public BigDecimal getUnitCost() { return unitCost; }
    public void setUnitCost(BigDecimal unitCost) { this.unitCost = unitCost; }

    public Long getSupplierId() { return supplierId; }
    public void setSupplierId(Long supplierId) { this.supplierId = supplierId; }

    public String getSupplierName() { return supplierName; }
    public void setSupplierName(String supplierName) { this.supplierName = supplierName; }

    public String getStorageLocation() { return storageLocation; }
    public void setStorageLocation(String storageLocation) { this.storageLocation = storageLocation; }

    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }

    public boolean isArchived() { return archived; }
    public void setArchived(boolean archived) { this.archived = archived; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    /** @return true si le stock est inférieur au minimum → alerte. */
    public boolean isLowStock() {
        return quantity < minQuantity;
    }

    public boolean isExpired(LocalDate today) {
        return expiryDate != null && today.isAfter(expiryDate);
    }

    public BigDecimal stockValue() {
        BigDecimal cost = unitCost != null ? unitCost : BigDecimal.ZERO;
        return cost.multiply(BigDecimal.valueOf(quantity));
    }

    @Override
    public String toString() { return partNo + " — " + name; }
}
