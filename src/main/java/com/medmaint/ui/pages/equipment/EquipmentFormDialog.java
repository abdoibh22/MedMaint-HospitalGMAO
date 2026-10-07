package com.medmaint.ui.pages.equipment;

import com.medmaint.domain.enums.Criticality;
import com.medmaint.domain.enums.EquipmentStatus;
import com.medmaint.domain.enums.MaintenanceFrequency;
import com.medmaint.domain.enums.RiskLevel;
import com.medmaint.domain.model.Department;
import com.medmaint.domain.model.Equipment;
import com.medmaint.domain.model.EquipmentCategory;
import com.medmaint.domain.model.Location;
import com.medmaint.domain.model.Manufacturer;
import com.medmaint.domain.model.Supplier;
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
import javafx.scene.layout.Priority;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Formulaire d'équipement (création / édition) : identification, classification,
 * localisation, acquisition, maintenance, informations techniques.
 */
public class EquipmentFormDialog extends FormDialog<Equipment> {

    private final Equipment editing;
    private final EquipmentRepo repo = new EquipmentRepo();

    private final TextField code = textField(null);
    private final TextField name = textField(null);
    private final ComboBox<EquipmentCategory> category = combo(List.of(), null);
    private final TextField subcategory = textField(null);
    private final ComboBox<Manufacturer> manufacturer = combo(List.of(), null);
    private final TextField model = textField(null);
    private final TextField serial = textField(null);
    private final TextField reference = textField(null);
    private final TextField equipmentType = textField(null);
    private final ComboBox<Criticality> criticality = combo(List.of(Criticality.values()), Criticality.MOYENNE);
    private final ComboBox<RiskLevel> risk = combo(List.of(RiskLevel.values()), RiskLevel.MOYEN);
    private final TextField deviceClass = textField(null);
    private final ComboBox<EquipmentStatus> status = combo(List.of(EquipmentStatus.values()), EquipmentStatus.EN_SERVICE);
    private final ComboBox<Department> department = combo(List.of(), null);
    private final ComboBox<Location> location = combo(List.of(), null);
    private final TextField locationDetails = textField(null);
    private final ComboBox<Supplier> supplier = combo(List.of(), null);

    private final DatePicker purchaseDate = datePicker(null);
    private final DatePicker receptionDate = datePicker(null);
    private final DatePicker commissioningDate = datePicker(null);
    private final TextField price = textField(null);
    private final TextField warrantyMonths = textField(null);
    private final DatePicker warrantyEnd = datePicker(null);

    private final ComboBox<String> maintenanceType = combo(
            List.of("Préventive", "Corrective", "Préventive + Contrat", "Contrat constructeur"), "Préventive");
    private final ComboBox<MaintenanceFrequency> frequency = combo(
            List.of(MaintenanceFrequency.values()), MaintenanceFrequency.MENSUELLE);
    private final DatePicker lastMaintenance = datePicker(null);
    private final DatePicker nextMaintenance = datePicker(null);

    private final TextField powerSupply = textField(null);
    private final TextField powerRating = textField(null);
    private final TextField dimensions = textField(null);
    private final TextField weight = textField(null);
    private final TextField fabricationYear = textField(null);
    private final TextArea notes = textArea(null, 3);

    public EquipmentFormDialog(Equipment existing) {
        super(existing == null ? Messages.get("equipment.new") : Messages.get("equipment.edit"));
        this.editing = existing;

        // Chargement des référentiels
        List<EquipmentCategory> cats = AppContext.get().references().allCategories();
        category.getItems().setAll(cats);
        List<Department> deps = AppContext.get().references().allDepartments();
        department.getItems().setAll(deps);
        manufacturer.getItems().setAll(AppContext.get().references().allManufacturers());
        manufacturer.getItems().add(null);
        supplier.getItems().setAll(AppContext.get().suppliers().suppliers().findAll());
        supplier.getItems().add(null);

        if (existing != null) {
            populate(existing);
        } else {
            code.setText(AppContext.get().equipment().nextCode());
            status.setValue(EquipmentStatus.EN_SERVICE);
            commissioningDate.setValue(LocalDate.now());
            manufacturer.getItems().sort((a, b) -> {
                if (a == null) return 1;
                if (b == null) return -1;
                return a.getName().compareToIgnoreCase(b.getName());
            });
        }

        department.valueProperty().addListener((o, a, b) -> {
            location.getItems().clear();
            location.getItems().add(null);
            if (b != null) {
                location.getItems().setAll(AppContext.get().references().locations(b.getId()));
            }
            location.getItems().sort((a2, b2) -> a2 == null ? -1 : a2.toString().compareTo(b2.toString()));
        });
        if (existing != null && existing.getDepartmentId() > 0) {
            department.getItems().stream()
                    .filter(d -> d.getId() == existing.getDepartmentId())
                    .findFirst().ifPresent(department::setValue);
            if (existing.getLocationId() != null) {
                location.getItems().stream()
                        .filter(l -> l != null && l.getId() == existing.getLocationId())
                        .findFirst().ifPresent(location::setValue);
            }
        }

        // --- Identification ---
        addField("equipment.code", code, true);
        addField("equipment.name", name, true);
        addField("equipment.category", category, true);
        addField("equipment.subcategory", subcategory);
        addField("equipment.manufacturer", manufacturer);
        addField("equipment.model", model);
        addField("equipment.serial", serial);
        addField("equipment.reference", reference);
        addField("equipment.type", equipmentType);

        // --- Classification ---
        addField("equipment.criticality", criticality);
        addField("equipment.risk", risk);
        addField("equipment.device_class", deviceClass);
        addField("equipment.status", status);

        // --- Localisation ---
        addField("equipment.service", department, true);
        addField("equipment.location", location);
        addField("equipment.location_details", locationDetails);
        addField("equipment.supplier", supplier);

        // --- Acquisition ---
        addField("equipment.purchase_date", purchaseDate);
        addField("equipment.reception_date", receptionDate);
        addField("equipment.commissioning", commissioningDate);
        addField("equipment.price", price);
        addField("equipment.warranty", warrantyMonths);
        addField("equipment.warranty_end", warrantyEnd);

        // --- Maintenance ---
        addField("equipment.maintenance_type", maintenanceType);
        addField("equipment.frequency", frequency);
        addField("equipment.last_maintenance", lastMaintenance);
        addField("equipment.next_maintenance", nextMaintenance);

        // --- Technique ---
        addField("equipment.power_supply", powerSupply);
        addField("equipment.power", powerRating);
        addField("equipment.dimensions", dimensions);
        addField("equipment.weight", weight);
        addField("equipment.year", fabricationYear);
        addWideField("equipment.notes", notes, false);

        setOnSave(b -> save());
    }

    private void populate(Equipment e) {
        code.setText(e.getCode());
        name.setText(e.getName());
        subcategory.setText(e.getSubcategory());
        category.getItems().stream().filter(c -> c.getId() == e.getCategoryId())
                .findFirst().ifPresent(category::setValue);
        manufacturer.getItems().stream()
                .filter(m -> m != null && e.getManufacturerId() != null && m.getId() == e.getManufacturerId())
                .findFirst().ifPresent(manufacturer::setValue);
        model.setText(e.getModel());
        serial.setText(e.getSerialNumber());
        reference.setText(e.getReferenceNumber());
        equipmentType.setText(e.getEquipmentType());
        criticality.setValue(e.criticalityEnum());
        risk.setValue(e.riskLevelEnum());
        deviceClass.setText(e.getDeviceClass());
        status.setValue(e.statusEnum());
        supplier.getItems().stream()
                .filter(s -> s != null && e.getSupplierId() != null && s.getId() == e.getSupplierId())
                .findFirst().ifPresent(supplier::setValue);
        purchaseDate.setValue(e.getPurchaseDate());
        receptionDate.setValue(e.getReceptionDate());
        commissioningDate.setValue(e.getCommissioningDate());
        price.setText(e.getPurchasePrice() == null ? "" : e.getPurchasePrice().toPlainString());
        warrantyMonths.setText(e.getWarrantyMonths() == null ? "" : String.valueOf(e.getWarrantyMonths()));
        warrantyEnd.setValue(e.getWarrantyEndDate());
        maintenanceType.setValue(e.getMaintenanceType());
        if (e.getPmFrequency() != null && !e.getPmFrequency().isBlank()) {
            frequency.setValue(MaintenanceFrequency.fromCode(e.getPmFrequency()));
        } else {
            frequency.setValue(null);
        }
        lastMaintenance.setValue(e.getLastMaintenanceDate());
        nextMaintenance.setValue(e.getNextMaintenanceDate());
        powerSupply.setText(e.getPowerSupply());
        powerRating.setText(e.getPowerRating());
        dimensions.setText(e.getDimensions());
        weight.setText(e.getWeightKg() == null ? "" : e.getWeightKg().toPlainString());
        fabricationYear.setText(e.getFabricationYear() == null ? "" : String.valueOf(e.getFabricationYear()));
        notes.setText(e.getNotes());
        locationDetails.setText(e.getLocationDetails());
    }

    private void save() {
        Equipment e = editing != null ? editing : new Equipment();
        e.setCode(code.getText().trim());
        e.setName(name.getText().trim());
        if (category.getValue() == null) {
            throw new com.medmaint.security.ValidationException(Messages.get("validation.required"));
        }
        e.setCategoryId(category.getValue().getId());
        e.setSubcategory(trim(subcategory));
        e.setManufacturerId(manufacturer.getValue() == null ? null : manufacturer.getValue().getId());
        e.setModel(trim(model));
        e.setSerialNumber(trim(serial));
        e.setReferenceNumber(trim(reference));
        e.setEquipmentType(trim(equipmentType));
        e.setCriticality(criticality.getValue().name());
        e.setRiskLevel(risk.getValue().name());
        e.setDeviceClass(trim(deviceClass));
        e.setStatus(status.getValue().name());
        if (department.getValue() == null) {
            throw new com.medmaint.security.ValidationException(Messages.get("validation.required"));
        }
        e.setDepartmentId(department.getValue().getId());
        e.setLocationId(location.getValue() == null ? null : location.getValue().getId());
        e.setLocationDetails(trim(locationDetails));
        e.setSupplierId(supplier.getValue() == null ? null : supplier.getValue().getId());
        e.setPurchaseDate(purchaseDate.getValue());
        e.setReceptionDate(receptionDate.getValue());
        e.setCommissioningDate(commissioningDate.getValue());
        e.setPurchasePrice(parseDecimal(price, Messages.get("equipment.price")));
        e.setWarrantyMonths(parseInt(warrantyMonths, Messages.get("equipment.warranty")));
        e.setWarrantyEndDate(warrantyEnd.getValue());
        e.setMaintenanceType(maintenanceType.getValue());
        e.setPmFrequency(frequency.getValue() == null ? null : frequency.getValue().name());
        e.setLastMaintenanceDate(lastMaintenance.getValue());
        e.setNextMaintenanceDate(nextMaintenance.getValue());
        e.setPowerSupply(trim(powerSupply));
        e.setPowerRating(trim(powerRating));
        e.setDimensions(trim(dimensions));
        e.setWeightKg(parseDecimal(weight, Messages.get("equipment.weight")));
        e.setFabricationYear(parseInt(fabricationYear, Messages.get("equipment.year")));
        e.setNotes(trim(notes));

        try {
            if (editing == null) {
                AppContext.get().equipment().create(e);
                Toast.success(Messages.get("msg.created"));
            } else {
                AppContext.get().equipment().update(e);
                Toast.success(Messages.get("msg.saved"));
            }
            getDialogPane().getScene().getWindow().hide();
        } catch (com.medmaint.security.ValidationException ve) {
            showError(ve.getMessage());
        } catch (RuntimeException ex) {
            showError(Messages.get("msg.error_save"));
        }
    }

    private static String trim(TextField f) {
        String v = f.getText();
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static String trim(TextArea f) {
        String v = f.getText();
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static BigDecimal parseDecimal(TextField f, String field) {
        String v = f.getText();
        if (v == null || v.isBlank()) return null;
        try {
            return new BigDecimal(v.trim().replace(",", "."));
        } catch (NumberFormatException e) {
            throw new com.medmaint.security.ValidationException(
                    field + " : " + Messages.get("validation.number"));
        }
    }

    private static Integer parseInt(TextField f, String field) {
        String v = f.getText();
        if (v == null || v.isBlank()) return null;
        try {
            return Integer.valueOf(v.trim());
        } catch (NumberFormatException e) {
            throw new com.medmaint.security.ValidationException(
                    field + " : " + Messages.get("validation.number"));
        }
    }
}
