package com.medmaint.domain.model;

/** Catégorie d'équipement (paramétrable par l'administrateur). */
public class EquipmentCategory {

    private long id;
    private String code;
    private String name;
    private String description;
    private String defaultFrequency;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getDefaultFrequency() { return defaultFrequency; }
    public void setDefaultFrequency(String defaultFrequency) { this.defaultFrequency = defaultFrequency; }

    @Override
    public String toString() { return name != null ? name : code; }
}
