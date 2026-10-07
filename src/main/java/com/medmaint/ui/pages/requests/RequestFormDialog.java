package com.medmaint.ui.pages.requests;

import com.medmaint.domain.enums.EquipmentStatus;
import com.medmaint.domain.enums.Priority;
import com.medmaint.domain.model.Equipment;
import com.medmaint.domain.model.InterventionRequest;
import com.medmaint.persistence.repo.EquipmentRepo;
import com.medmaint.security.Session;
import com.medmaint.service.AppContext;
import com.medmaint.ui.components.FormDialog;
import com.medmaint.ui.components.Toast;
import com.medmaint.ui.i18n.Messages;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.util.ArrayList;
import java.util.List;

/**
 * Formulaire de demande d'intervention.
 * Scénario B : le personnel décrit la panne, choisit la priorité, joignez
 * une pièce si besoin ; une priorité « Critique » déclenche le flux d'alerte.
 */
public class RequestFormDialog extends FormDialog<InterventionRequest> {

    public enum Mode { CREATE, FROM_EQUIPMENT }

    private final Mode mode;
    private final Equipment preselected;

    private final ComboBox<Equipment> equipment = combo(List.of(), null);
    private final TextArea problem = textArea(null, 5);
    private final TextArea symptoms = textArea(null, 3);
    private final ComboBox<Priority> priority = combo(List.of(Priority.values()), Priority.NORMALE);
    private final CheckBox safety = new CheckBox(Messages.get("requests.safety"));
    private final ComboBox<String> availability = combo(
            List.of("INDISPONIBLE", "DEGRADE", "TOUTE_FONCTION"), "INDISPONIBLE");
    private final TextField attachments = textField(null);
    private final Label criticalBanner = new Label(Messages.get("requests.critical_banner"));

    public RequestFormDialog(Mode mode, Equipment preselected) {
        super(Messages.get("requests.new"));
        this.mode = mode;
        this.preselected = preselected;

        availability.setConverter(availabilityLabel());
        List<Equipment> eqs = new ArrayList<>(new EquipmentRepo().findAll(
                new EquipmentRepo.Filter(null, null, null, null, null, null, null, null, false, null),
                "code", "ASC"));
        equipment.getItems().setAll(eqs);
        equipment.setConverter(equipmentLabel());

        if (preselected != null) {
            equipment.setValue(preselected);
            equipment.setDisable(true);
        }

        criticalBanner.getStyleClass().add("alert-danger");
        criticalBanner.setWrapText(true);
        criticalBanner.setVisible(false);
        criticalBanner.setManaged(false);
        priority.valueProperty().addListener((o, a, b) -> updateBanner());
        safety.selectedProperty().addListener((o, a, b) -> updateBanner());

        addField("requests.equipment", equipment, true);
        addWideField("requests.problem", problem, true);
        addWideField("requests.symptoms", symptoms, false);
        addField("requests.priority", priority, true);
        addRaw(safety);
        addField("requests.availability", availability);
        addWideField("Documents (nom de fichier)", attachments, false);
        addRaw(criticalBanner);

        setOnSave(b -> save());
    }

    private void updateBanner() {
        boolean crit = priority.getValue() == Priority.CRITIQUE || safety.isSelected();
        criticalBanner.setVisible(crit);
        criticalBanner.setManaged(crit);
    }

    private javafx.util.StringConverter<Equipment> equipmentLabel() {
        return new javafx.util.StringConverter<>() {
            @Override
            public String toString(Equipment e) {
                return e == null ? Messages.get("common.select_option")
                        : e.getCode() + " — " + e.getName();
            }

            @Override
            public Equipment fromString(String s) {
                return null;
            }
        };
    }

    private javafx.util.StringConverter<String> availabilityLabel() {
        return new javafx.util.StringConverter<>() {
            @Override
            public String toString(String v) {
                if (v == null) return "";
                return switch (v) {
                    case "INDISPONIBLE" -> "Équipement indisponible";
                    case "DEGRADE" -> "Fonctionnement dégradé";
                    case "TOUTE_FONCTION" -> "Toutes les fonctions disponibles";
                    default -> v;
                };
            }

            @Override
            public String fromString(String s) {
                return s;
            }
        };
    }

    private void save() {
        if (equipment.getValue() == null) {
            showError(Messages.get("validation.required"));
            return;
        }
        if (problem.getText() == null || problem.getText().isBlank()) {
            showError(Messages.get("validation.required"));
            return;
        }
        InterventionRequest r = new InterventionRequest();
        r.setEquipmentId(equipment.getValue().getId());
        r.setProblemDescription(problem.getText().trim());
        r.setSymptoms(symptoms.getText() == null || symptoms.getText().isBlank()
                ? null : symptoms.getText().trim());
        r.setPriority(priority.getValue().name());
        r.setSafetyImpact(safety.isSelected());
        r.setAvailability(availability.getValue());
        r.setDepartmentId(equipment.getValue().getDepartmentId());

        try {
            InterventionRequest created = AppContext.get().requests().create(r);
            // Pièce jointe (nom de fichier saisi) : enregistrement du document de référence
            String attach = attachments.getText();
            if (attach != null && !attach.isBlank()) {
                try {
                    AppContext.get().documents().store("REQUEST", created.getId(),
                            attach.trim(), "AUTRE",
                            ("Demande " + created.getRequestNo() + "\n" + created.getProblemDescription())
                                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
                } catch (RuntimeException ignored) {
                    // la pièce jointe texte n'est qu'un bonus : la demande est déjà créée
                }
            }
            Toast.success(Messages.get("requests.created") + " " + created.getRequestNo());
            getDialogPane().getScene().getWindow().hide();
        } catch (com.medmaint.security.ValidationException ve) {
            showError(ve.getMessage());
        } catch (RuntimeException ex) {
            showError(Messages.get("msg.error_save"));
        }
    }
}
