package com.medmaint.service;

import com.medmaint.domain.model.Technician;
import com.medmaint.persistence.Page;
import com.medmaint.persistence.repo.TechnicianRepo;
import com.medmaint.security.Permission;
import com.medmaint.security.Session;
import com.medmaint.security.ValidationException;

import java.util.List;
import java.util.Optional;

/** Gestion des techniciens : profils, compétences, statistiques et charge. */
public final class TechnicianService {

    private final TechnicianRepo repo = new TechnicianRepo();
    private final AuditService audit;

    public TechnicianService(AuditService audit) {
        this.audit = audit;
    }

    public Page<Technician> search(String text, String status, Long departmentId,
                                   int page, int size, String sort, String dir) {
        Session.get().require(Permission.TECHNICIAN_VIEW);
        Page<Technician> result = repo.search(text, status, departmentId, page, size, sort, dir);
        result.items().forEach(repo::fillStats);
        return result;
    }

    public Optional<Technician> get(long id) {
        Session.get().require(Permission.TECHNICIAN_VIEW);
        return repo.findById(id);
    }

    /** Profil complet avec statistiques et compétences. */
    public Technician profile(long id) {
        Technician t = get(id).orElseThrow(() -> new ValidationException("Technicien introuvable."));
        repo.fillStats(t);
        return t;
    }

    public List<Technician> all() {
        Session.get().require(Permission.TECHNICIAN_VIEW);
        return repo.findAll();
    }

    public Technician create(Technician t, List<String> skills) {
        Session.get().require(Permission.TECHNICIAN_MANAGE);
        validate(t, null);
        long id = repo.insert(t);
        t.setId(id);
        repo.replaceSkills(id, skills);
        audit.created("Technicien", t.getEmployeeNo(), t.getFullName());
        return t;
    }

    public void update(Technician t, List<String> skills) {
        Session.get().require(Permission.TECHNICIAN_MANAGE);
        validate(t, t.getId());
        Technician before = repo.findById(t.getId())
                .orElseThrow(() -> new ValidationException("Technicien introuvable."));
        repo.update(t);
        if (skills != null) {
            repo.replaceSkills(t.getId(), skills);
        }
        audit.updated("Technicien", t.getEmployeeNo(), t.getFullName(),
                before.getStatus(), t.getStatus());
    }

    public void delete(long id) {
        Session.get().require(Permission.TECHNICIAN_MANAGE);
        Technician t = repo.findById(id)
                .orElseThrow(() -> new ValidationException("Technicien introuvable."));
        if (repo.hasOpenWork(id)) {
            throw new ValidationException(
                    "Impossible de supprimer : ce technicien a des interventions ouvertes.");
        }
        repo.delete(id);
        audit.deleted("Technicien", t.getEmployeeNo(), t.getFullName(), null);
    }

    public List<Object[]> workload() {
        Session.get().require(Permission.TECHNICIAN_VIEW);
        return repo.workload();
    }

    public List<String> skillsOf(long technicianId) {
        return repo.findSkills(technicianId);
    }

    private void validate(Technician t, Long excludeId) {
        if (t.getFullName() == null || t.getFullName().isBlank()) {
            throw new ValidationException("Le nom du technicien est obligatoire.");
        }
        if (t.getEmployeeNo() == null || t.getEmployeeNo().isBlank()) {
            throw new ValidationException("Le matricule est obligatoire.");
        }
        if (repo.employeeNoExists(t.getEmployeeNo(), excludeId)) {
            throw new ValidationException("Le matricule \"" + t.getEmployeeNo() + "\" existe déjà.");
        }
        if (t.getEmail() != null && !t.getEmail().isBlank()
                && !t.getEmail().matches("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$")) {
            throw new ValidationException("Adresse e-mail invalide.");
        }
    }

    public TechnicianRepo repo() {
        return repo;
    }
}
