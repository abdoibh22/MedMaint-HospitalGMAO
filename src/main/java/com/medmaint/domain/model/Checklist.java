package com.medmaint.domain.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Checklist de maintenance (modèle) et ses items. */
public class Checklist {

    private long id;
    private String name;
    private Long categoryId;
    private String categoryName; // affichage
    private String description;
    private LocalDateTime createdAt;
    private List<ChecklistItem> items = new ArrayList<>();

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public List<ChecklistItem> getItems() { return items; }
    public void setItems(List<ChecklistItem> items) { this.items = items != null ? items : new ArrayList<>(); }

    /** Item simple d'une checklist. */
    public static class ChecklistItem {
        private long id;
        private long checklistId;
        private int position;
        private String label;
        private String hint;

        public long getId() { return id; }
        public void setId(long id) { this.id = id; }
        public long getChecklistId() { return checklistId; }
        public void setChecklistId(long checklistId) { this.checklistId = checklistId; }
        public int getPosition() { return position; }
        public void setPosition(int position) { this.position = position; }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public String getHint() { return hint; }
        public void setHint(String hint) { this.hint = hint; }
    }

    @Override
    public String toString() { return name; }
}
