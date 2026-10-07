package com.medmaint.domain.model;

import java.time.LocalDateTime;

/** Document joint (manuel, facture, rapport, photo…) à une entité. */
public class Document {

    private long id;
    private String entityType;   // EQUIPMENT | WORK_ORDER | MAINTENANCE_PLAN | SUPPLIER | CONTRACT | USER | REQUEST | MAINTENANCE_RECORD
    private long entityId;
    private String filename;
    private String storedName;
    private String mimeType;
    private String category;     // enum DocumentCategory
    private long sizeBytes;
    private Long uploadedBy;
    private String uploadedByName; // affichage
    private LocalDateTime uploadedAt;
    private String description;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }

    public long getEntityId() { return entityId; }
    public void setEntityId(long entityId) { this.entityId = entityId; }

    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }

    public String getStoredName() { return storedName; }
    public void setStoredName(String storedName) { this.storedName = storedName; }

    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }

    public Long getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(Long uploadedBy) { this.uploadedBy = uploadedBy; }

    public String getUploadedByName() { return uploadedByName; }
    public void setUploadedByName(String uploadedByName) { this.uploadedByName = uploadedByName; }

    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    /** Taille lisible : « 1,4 Mo ». */
    public String humanSize() {
        if (sizeBytes < 1024) return sizeBytes + " o";
        if (sizeBytes < 1024 * 1024) return String.format("%.1f Ko", sizeBytes / 1024.0);
        return String.format("%.1f Mo", sizeBytes / (1024.0 * 1024.0));
    }

    @Override
    public String toString() { return filename; }
}
