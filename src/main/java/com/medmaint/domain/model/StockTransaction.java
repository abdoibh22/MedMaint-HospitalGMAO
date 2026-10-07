package com.medmaint.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Transaction de stock (entrée, sortie, transfert, ajustement, consommation). */
public class StockTransaction {

    private long id;
    private long partId;
    private String partNo;          // affichage
    private String partName;        // affichage
    private String txType;          // enum StockTxType
    private int quantity;
    private BigDecimal unitCost;
    private Long workOrderId;
    private String workOrderNo;     // affichage
    private Long equipmentId;
    private String equipmentCode;   // affichage
    private Long userId;
    private String userName;        // affichage
    private String note;
    private LocalDateTime createdAt;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getPartId() { return partId; }
    public void setPartId(long partId) { this.partId = partId; }

    public String getPartNo() { return partNo; }
    public void setPartNo(String partNo) { this.partNo = partNo; }

    public String getPartName() { return partName; }
    public void setPartName(String partName) { this.partName = partName; }

    public String getTxType() { return txType; }
    public void setTxType(String txType) { this.txType = txType; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public BigDecimal getUnitCost() { return unitCost; }
    public void setUnitCost(BigDecimal unitCost) { this.unitCost = unitCost; }

    public Long getWorkOrderId() { return workOrderId; }
    public void setWorkOrderId(Long workOrderId) { this.workOrderId = workOrderId; }

    public String getWorkOrderNo() { return workOrderNo; }
    public void setWorkOrderNo(String workOrderNo) { this.workOrderNo = workOrderNo; }

    public Long getEquipmentId() { return equipmentId; }
    public void setEquipmentId(Long equipmentId) { this.equipmentId = equipmentId; }

    public String getEquipmentCode() { return equipmentCode; }
    public void setEquipmentCode(String equipmentCode) { this.equipmentCode = equipmentCode; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    /** Valeur monétaire de la transaction. */
    public BigDecimal value() {
        BigDecimal c = unitCost != null ? unitCost : BigDecimal.ZERO;
        return c.multiply(BigDecimal.valueOf(quantity));
    }
}
