package com.medmaint.service;

import com.medmaint.domain.model.Department;
import com.medmaint.domain.model.EquipmentCategory;
import com.medmaint.domain.model.Location;
import com.medmaint.domain.model.Manufacturer;
import com.medmaint.persistence.Page;
import com.medmaint.persistence.repo.CategoryRepo;
import com.medmaint.persistence.repo.DepartmentRepo;
import com.medmaint.security.Permission;
import com.medmaint.security.Session;
import com.medmaint.security.ValidationException;

import java.util.List;
import java.util.Optional;

/** Services hospitaliers, localisations, catégories et fabricants (paramétrage). */
public final class ReferenceService {

    private final DepartmentRepo departments = new DepartmentRepo();
    private final CategoryRepo refs = new CategoryRepo();
    private final AuditService audit;

    public ReferenceService(AuditService audit) {
        this.audit = audit;
    }

    // ---------- Services hospitaliers ----------

    public List<Department> allDepartments() {
        Session.get().require(Permission.DEPARTMENT_VIEW);
        return departments.findAll();
    }

    public Page<Department> searchDepartments(String text, int page, int size) {
        Session.get().require(Permission.DEPARTMENT_VIEW);
        return departments.search(text, page, size);
    }

    public Optional<Department> getDepartment(long id) {
        return departments.findById(id);
    }

    public Department createDepartment(Department d) {
        Session.get().require(Permission.DEPARTMENT_MANAGE);
        validateDepartment(d, null);
        long id = departments.insert(d);
        d.setId(id);
        audit.created("Service hospitalier", d.getCode(), d.getName());
        return d;
    }

    public void updateDepartment(Department d) {
        Session.get().require(Permission.DEPARTMENT_MANAGE);
        validateDepartment(d, d.getId());
        departments.update(d);
        audit.updated("Service hospitalier", d.getCode(), d.getName(), null, d.getName());
    }

    public void deleteDepartment(long id) {
        Session.get().require(Permission.DEPARTMENT_MANAGE);
        if (departments.hasEquipment(id)) {
            throw new ValidationException(
                    "Impossible de supprimer : ce service contient encore des équipements.");
        }
        Department d = departments.findById(id)
                .orElseThrow(() -> new ValidationException("Service introuvable."));
        departments.delete(id);
        audit.deleted("Service hospitalier", d.getCode(), d.getName(), null);
    }

    private void validateDepartment(Department d, Long excludeId) {
        if (d.getCode() == null || d.getCode().isBlank()) {
            throw new ValidationException("Le code du service est obligatoire.");
        }
        if (d.getName() == null || d.getName().isBlank()) {
            throw new ValidationException("Le nom du service est obligatoire.");
        }
        Optional<Department> dup = departments.findByCode(d.getCode());
        if (dup.isPresent() && (excludeId == null || dup.get().getId() != excludeId)) {
            throw new ValidationException("Le code \"" + d.getCode() + "\" existe déjà.");
        }
    }

    /** Nombre d'équipements / demandes ouvertes par service (vue d'ensemble). */
    public List<Object[]> departmentEquipmentCounts() {
        return departments.equipmentCountByDepartment();
    }

    // ---------- Localisations ----------

    public List<Location> locations(Long departmentId) {
        return departments.findLocations(departmentId);
    }

    public Location createLocation(Location l) {
        Session.get().require(Permission.SETTINGS_MANAGE);
        if (l.getName() == null || l.getName().isBlank()) {
            throw new ValidationException("Le nom de la localisation est obligatoire.");
        }
        if (l.getCode() == null || l.getCode().isBlank()) {
            l.setCode("LOC-" + (departments.findLocations(null).size() + 1));
        }
        long id = departments.insertLocation(l);
        l.setId(id);
        return l;
    }

    public void updateLocation(Location l) {
        Session.get().require(Permission.SETTINGS_MANAGE);
        departments.updateLocation(l);
    }

    public void deleteLocation(long id) {
        Session.get().require(Permission.SETTINGS_MANAGE);
        if (departments.locationInUse(id)) {
            throw new ValidationException(
                    "Impossible : des équipements sont rattachés à cette localisation.");
        }
        departments.deleteLocation(id);
    }

    // ---------- Catégories ----------

    public List<EquipmentCategory> allCategories() {
        Session.get().require(Permission.EQUIPMENT_VIEW);
        return refs.findAllCategories();
    }

    public EquipmentCategory createCategory(EquipmentCategory c) {
        Session.get().require(Permission.SETTINGS_MANAGE);
        validateCategory(c, null);
        long id = refs.insertCategory(c);
        c.setId(id);
        audit.created("Catégorie d'équipement", c.getCode(), c.getName());
        return c;
    }

    public void updateCategory(EquipmentCategory c) {
        Session.get().require(Permission.SETTINGS_MANAGE);
        validateCategory(c, c.getId());
        refs.updateCategory(c);
        audit.updated("Catégorie d'équipement", c.getCode(), c.getName(), null, c.getName());
    }

    public void deleteCategory(long id) {
        Session.get().require(Permission.SETTINGS_MANAGE);
        if (refs.categoryInUse(id)) {
            throw new ValidationException(
                    "Impossible : cette catégorie est utilisée par des équipements.");
        }
        refs.deleteCategory(id);
        audit.deleted("Catégorie d'équipement", String.valueOf(id), null, null);
    }

    private void validateCategory(EquipmentCategory c, Long excludeId) {
        if (c.getCode() == null || c.getCode().isBlank()) {
            throw new ValidationException("Le code de la catégorie est obligatoire.");
        }
        if (c.getName() == null || c.getName().isBlank()) {
            throw new ValidationException("Le nom de la catégorie est obligatoire.");
        }
        if (refs.categoryCodeExists(c.getCode(), excludeId)) {
            throw new ValidationException("Le code \"" + c.getCode() + "\" existe déjà.");
        }
    }

    // ---------- Fabricants ----------

    public List<Manufacturer> allManufacturers() {
        return refs.findAllManufacturers();
    }

    public Manufacturer createManufacturer(Manufacturer m) {
        Session.get().require(Permission.SETTINGS_MANAGE);
        if (m.getName() == null || m.getName().isBlank()) {
            throw new ValidationException("Le nom du fabricant est obligatoire.");
        }
        long id = refs.insertManufacturer(m);
        m.setId(id);
        audit.created("Fabricant", String.valueOf(id), m.getName());
        return m;
    }

    public void updateManufacturer(Manufacturer m) {
        Session.get().require(Permission.SETTINGS_MANAGE);
        refs.updateManufacturer(m);
        audit.updated("Fabricant", String.valueOf(m.getId()), m.getName(), null, m.getName());
    }

    public void deleteManufacturer(long id) {
        Session.get().require(Permission.SETTINGS_MANAGE);
        if (refs.manufacturerInUse(id)) {
            throw new ValidationException("Impossible : ce fabricant est utilisé par des équipements.");
        }
        refs.deleteManufacturer(id);
        audit.deleted("Fabricant", String.valueOf(id), null, null);
    }

    public DepartmentRepo departments() {
        return departments;
    }

    public CategoryRepo refs() {
        return refs;
    }
}
