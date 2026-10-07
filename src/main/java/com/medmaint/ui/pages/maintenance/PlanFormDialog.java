package com.medmaint.ui.pages.maintenance;

import com.medmaint.domain.enums.MaintenanceFrequency;
import com.medmaint.domain.enums.PlanStatus;
import com.medmaint.domain.model.Equipment;
import com.medmaint.domain.model.MaintenancePlan;
import com.medmaint.domain.model.Technician;
import com.medmaint.persistence.repo.EquipmentRepo;
import com.medmaint.security.Session;
import com.medmaint.service.AppContext;
import com.medmaint.ui.components.FormDialog;
import com.medmaint.ui.components.Toast;
import com.medmaint.ui.i18n.Messages;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.time.LocalDate;
import java.util.List;

/**
 * Création / édition d'un plan de maintenance préventive.
 * La prochaine échéance est recalculée depuis la fréquence.
 */
public class PlanFormDialog extends FormDialog<MaintenancePlan> {

    private final MaintenancePlan editing;
    private final Equipment preselected;

    private final ComboBox<Equipment> equipment = combo(List.of(), null);
    private final TextField type = textField("Maintenance préventive standard");
    private final ComboBox<MaintenanceFrequency> frequency =
            combo(List.of(MaintenanceFrequency.values()), MaintenanceFrequency.MENSUELLE);
    private final TextField customDays = textField(null);
    private final ComboBox<com.medmaint.domain.model.Checklist> checklist = combo(List.of(), null);
    private final ComboBox<Technician> responsible = combo(List.of(), null);
    private final DatePicker lastPerformed = datePicker(null);
    private final DatePicker nextDue = datePicker(LocalDate.now().plusMonths(1));
    private final TextField duration = textField("60");
    private final TextField requiredParts = textField(null);
    private final TextField requiredTools = textField(null);
    private final TextArea instructions = textArea(null, 3);

    public PlanFormDialog(Equipment preselected) {
        this(null, preselected);
    }

    public PlanFormDialog(MaintenancePlan existing, Equipment preselected) {
        super(existing == null ? Messages.get("pm.new") : Messages.get("pm.edit"));
        this.editing = existing;
        this.preselected = preselected;

        List<Equipment> eqs = new EquipmentRepo().findAll(
                new EquipmentRepo.Filter(null, null, null, null, null, null, null, null, false, null),
                "code", "ASC");
        equipment.getItems().setAll(eqs);
        equipment.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(Equipment e) {
                return e == null ? Messages.get("common.select_option")
                        : e.getCode() + " — " + e.getName();
            }

            @Override
            public Equipment fromString(String s) {
                return null;
            }
        });

        checklist.getItems().add(null);
        checklist.getItems().addAll(AppContext.get().preventive().checklists());
        checklist.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(com.medmaint.domain.model.Checklist c) {
                return c == null ? Messages.get("common.none") : c.getName();
            }

            @Override
            public com.medmaint.domain.model.Checklist fromString(String s) {
                return null;
            }
        });

        responsible.getItems().add(null);
        responsible.getItems().addAll(AppContext.get().technicians().all());
        responsible.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(Technician t) {
                return t == null ? Messages.get("common.none") : t.getFullName();
            }

            @Override
            public Technician fromString(String s) {
                return null;
            }
        });

        // Recalcul automatique de la prochaine échéance quand la fréquence change
        frequency.valueProperty().addListener((o, a, b) -> recomputeNext());
        lastPerformed.valueProperty().addListener((o, a, b) -> recomputeNext());

        if (existing != null) {
            equipment.getItems().stream()
                    .filter(e -> e.getId() == existing.getEquipmentId())
                    .findFirst().ifPresent(equipment::setValue);
            type.setText(existing.getMaintenanceType());
            frequency.setValue(MaintenanceFrequency.fromCode(existing.getFrequency()));
            customDays.setText(existing.getCustomDays() == null ? null : String.valueOf(existing.getCustomDays()));
            checklist.getItems().stream()
                    .filter(c -> c != null && existing.getChecklistId() != null
                            && c.getId() == existing.getChecklistId())
                    .findFirst().ifPresent(checklist::setValue);
            responsible.getItems().stream()
                    .filter(t -> t != null && existing.getResponsibleId() != null
                            && t.getId() == existing.getResponsibleId())
                    .findFirst().ifPresent(responsible::setValue);
            lastPerformed.setValue(existing.getLastPerformed());
            nextDue.setValue(existing.getNextDue());
            duration.setText(existing.getEstimatedDurationMin() == null ? ""
                    : String.valueOf(existing.getEstimatedDurationMin()));
            requiredParts.setText(existing.getRequiredParts());
            requiredTools.setText(existing.getRequiredTools());
            instructions.setText(existing.getInstructions());
        } else {
            if (preselected != null) {
                equipment.setValue(preselected);
                equipment.setDisable(true);
            }
            recomputeNext();
        }

        addField("pm.equipment", equipment, true);
        addField("pm.type", type, true);
        addField("pm.frequency", frequency, true);
        addField("pm.custom_days", customDays);
        addField("pm.checklist", checklist);
        addField("pm.responsible", responsible);
        addField("pm.last_performed", lastPerformed);
        addField("pm.next_due", nextDue, true);
        addField("pm.duration", duration);
        addField("pm.required_parts", requiredParts);
        addField("pm.required_tools", requiredTools);
        addWideField("pm.instructions", instructions, false);

        setOnSave(b -> save());
    }

    private void recomputeNext() {
        if (editing != null) return;
        MaintenanceFrequency f = frequency.getValue();
        if (f == null) return;
        LocalDate base = lastPerformed.getValue() != null ? lastPerformed.getValue() : LocalDate.now();
        Integer custom = null;
        String cd = customDays.getText();
        if (cd != null && !cd.isBlank()) {
            try {
                custom = Integer.parseInt(cd.trim());
            } catch (NumberFormatException ignored) {
                custom = null;
            }
        }
        nextDue.setValue(f.nextDate(base, custom));
    }

    private void save() {
        if (equipment.getValue() == null) {
            showError(Messages.get("validation.required"));
            return;
        }
        if (type.getText() == null || type.getText().isBlank()) {
            showError(Messages.get("validation.required"));
            return;
        }
        MaintenancePlan p = editing != null ? editing : new MaintenancePlan();
        p.setEquipmentId(equipment.getValue().getId());
        p.setMaintenanceType(type.getText().trim());
        p.setFrequency(frequency.getValue().name());
        String cd = customDays.getText();
        p.setCustomDays(cd == null || cd.isBlank() ? null : Integer.parseInt(cd.trim()));
        p.setChecklistId(checklist.getValue() == null ? null : checklist.getValue().getId());
        p.setResponsibleId(responsible.getValue() == null ? null : responsible.getValue().getId());
        p.setLastPerformed(lastPerformed.getValue());
        p.setNextDue(nextDue.getValue());
        p.setEstimatedDurationMin(duration.getText() == null || duration.getText().isBlank()
                ? null : Integer.parseInt(duration.getText().trim()));
        p.setRequiredParts(requiredParts.getText());
        p.setRequiredTools(requiredTools.getText());
        p.setInstructions(instructions.getText());
        p.setStatus(PlanStatus.A_VENIR.name());
        p.setActive(true);

        try {
            if (editing == null) {
                AppContext.get().preventive().create(p);
                Toast.success(Messages.get("msg.created"));
            } else {
                AppContext.get().preventive().update(p);
                Toast.success(Messages.get("msg.saved"));
            }
            getDialogPane().getScene().getWindow().hide();
        } catch (com.medmaint.security.ValidationException ve) {
            showError(ve.getMessage());
        } catch (NumberFormatException nfe) {
            showError(Messages.get("validation.number"));
        } catch (RuntimeException ex) {
            showError(Messages.get("msg.error_save"));
        }
    }
}
