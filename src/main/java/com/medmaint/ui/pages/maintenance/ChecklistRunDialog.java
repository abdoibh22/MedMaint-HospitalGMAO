package com.medmaint.ui.pages.maintenance;

import com.medmaint.domain.enums.ChecklistResult;
import com.medmaint.domain.model.Checklist;
import com.medmaint.domain.model.MaintenancePlan;
import com.medmaint.domain.model.MaintenanceRecord;
import com.medmaint.security.Session;
import com.medmaint.service.AppContext;
import com.medmaint.ui.components.FormDialog;
import com.medmaint.ui.components.Toast;
import com.medmaint.ui.components.Ui;
import com.medmaint.ui.i18n.Messages;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Exécution d'une maintenance préventive (scénario C) :
 * checklist item par item (Conforme / Non conforme / Non applicable + note),
 * observations, durée, coût.
 * À l'enregistrement : historique créé, dates dernière/prochaine maintenance
 * de l'équipement mises à jour automatiquement.
 */
public class ChecklistRunDialog extends FormDialog<Object> {

    private final MaintenancePlan plan;
    private final Checklist checklist;
    private final Runnable onDone;

    private final TextArea observations = textArea(null, 4);
    private final TextField duration = textField("60");
    private final TextField cost = textField("0");
    private final GridPane itemsGrid = new GridPane();
    private final List<Row> rows = new ArrayList<>();

    private record Row(Checklist.ChecklistItem item,
                       ComboBox<ChecklistResult> result,
                       TextField notes) {
    }

    public ChecklistRunDialog(MaintenancePlan plan, Checklist checklist, Runnable onDone) {
        super(Messages.get("pm.checklist_title",
                plan.getEquipmentCode() + " — " + plan.getMaintenanceType()));
        this.plan = plan;
        this.checklist = checklist;
        this.onDone = onDone;

        // En-tête contextuel
        VBox intro = new VBox(4);
        intro.setPadding(new Insets(4, 0, 8, 0));
        Label eq = Ui.h3(plan.getEquipmentCode() + " — " + plan.getEquipmentName());
        Label hint = Ui.muted(Messages.get("pm.run_hint"));
        Label resp = Ui.muted(Messages.get("pm.responsible") + " : "
                + Ui.orDash(Session.get().fullName())
                + "  ·  " + Messages.get("pm.next_due") + " : " + Ui.date(plan.getNextDue()));
        intro.getChildren().addAll(eq, hint, resp);
        addRaw(intro);

        // Items de la checklist
        itemsGrid.setHgap(12);
        itemsGrid.setVgap(8);
        List<Checklist.ChecklistItem> items = checklist == null
                ? List.of()
                : AppContext.get().preventive().checklistItems(checklist.getId());
        if (items.isEmpty()) {
            Label none = Ui.muted("Aucune checklist associée à ce plan. Saisissez les observations libres.");
            addRaw(none);
        } else {
            int r = 0;
            for (Checklist.ChecklistItem item : items) {
                Label lbl = new Label(item.getPosition() + ". " + item.getLabel());
                lbl.setWrapText(true);
                lbl.setMaxWidth(330);
                if (item.getHint() != null && !item.getHint().isBlank()) {
                    lbl.setTooltip(new javafx.scene.control.Tooltip(item.getHint()));
                }
                ComboBox<ChecklistResult> combo = FormDialog.combo(
                        List.of(ChecklistResult.values()), ChecklistResult.CONFORME);
                combo.setConverter(new javafx.util.StringConverter<>() {
                    @Override
                    public String toString(ChecklistResult c) {
                        return c == null ? "" : c.label();
                    }

                    @Override
                    public ChecklistResult fromString(String s) {
                        return null;
                    }
                });
                combo.setPrefWidth(160);
                TextField note = FormDialog.textField(null);
                note.setPromptText("Note");
                HBoxRow(item, combo, note, lbl, r);
                r++;
            }
            addRaw(itemsGrid);
        }

        addWideField("pm.observations", observations, false);
        addField("pm.duration_done", duration);
        addField("pm.cost", cost);

        setOnSave(b -> complete());
    }

    private void HBoxRow(Checklist.ChecklistItem item, ComboBox<ChecklistResult> combo,
                         TextField note, Label lbl, int r) {
        itemsGrid.add(lbl, 0, r);
        itemsGrid.add(combo, 1, r);
        itemsGrid.add(note, 2, r);
        javafx.scene.layout.ColumnConstraints c0 = new javafx.scene.layout.ColumnConstraints(340);
        javafx.scene.layout.ColumnConstraints c1 = new javafx.scene.layout.ColumnConstraints(170);
        javafx.scene.layout.ColumnConstraints c2 = new javafx.scene.layout.ColumnConstraints(200);
        c2.setHgrow(Priority.ALWAYS);
        if (itemsGrid.getColumnConstraints().isEmpty()) {
            itemsGrid.getColumnConstraints().addAll(c0, c1, c2);
        }
        rows.add(new Row(item, combo, note));
    }

    private void complete() {
        try {
            List<MaintenanceRecord.ChecklistResultEntry> results = new ArrayList<>();
            for (Row row : rows) {
                ChecklistResult res = row.result().getValue();
                MaintenanceRecord.ChecklistResultEntry entry = new MaintenanceRecord.ChecklistResultEntry();
                entry.setItemId(row.item() == null ? 0 : row.item().getId());
                entry.setItemLabel(row.item() != null ? row.item().getLabel() : "Point de contrôle");
                entry.setResult(res == null ? ChecklistResult.CONFORME.name() : res.name());
                String noteText = row.notes().getText();
                entry.setNotes(noteText == null || noteText.isBlank() ? null : noteText);
                results.add(entry);
            }
            Integer dur = duration.getText() == null || duration.getText().isBlank()
                    ? null : Integer.parseInt(duration.getText().trim());
            BigDecimal cst = cost.getText() == null || cost.getText().isBlank()
                    ? BigDecimal.ZERO : new BigDecimal(cost.getText().trim().replace(",", "."));

            AppContext.get().preventive().execute(plan.getId(), results,
                    observations.getText(), dur, cst);

            Toast.success(Messages.get("pm.executed"));
            getDialogPane().getScene().getWindow().hide();
            if (onDone != null) onDone.run();
        } catch (NumberFormatException nfe) {
            showError(Messages.get("validation.number"));
        } catch (com.medmaint.security.ValidationException ve) {
            showError(ve.getMessage());
        } catch (RuntimeException ex) {
            showError(Messages.get("msg.error_save"));
        }
    }

    @Override
    public void addRaw(Node node) {
        super.addRaw(node);
    }
}
