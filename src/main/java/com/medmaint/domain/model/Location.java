package com.medmaint.domain.model;

/** Localisation physique (bâtiment / étage / zone) rattachée à un service. */
public class Location {

    private long id;
    private long departmentId;
    private String code;
    private String name;
    private String floor;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getDepartmentId() { return departmentId; }
    public void setDepartmentId(long departmentId) { this.departmentId = departmentId; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getFloor() { return floor; }
    public void setFloor(String floor) { this.floor = floor; }

    @Override
    public String toString() {
        if (floor == null || floor.isBlank()) {
            return name;
        }
        return name + " — " + floor;
    }
}
