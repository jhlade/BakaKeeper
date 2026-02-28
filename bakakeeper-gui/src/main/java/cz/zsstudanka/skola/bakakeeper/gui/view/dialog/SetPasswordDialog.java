package cz.zsstudanka.skola.bakakeeper.gui.view.dialog;

import cz.zsstudanka.skola.bakakeeper.config.AppConfig;
import cz.zsstudanka.skola.bakakeeper.connectors.BakaMailer;
import cz.zsstudanka.skola.bakakeeper.gui.service.BackgroundTaskRunner;
import cz.zsstudanka.skola.bakakeeper.model.StudentRecord;
import cz.zsstudanka.skola.bakakeeper.routines.Export;
import cz.zsstudanka.skola.bakakeeper.routines.ReportData;
import cz.zsstudanka.skola.bakakeeper.service.PasswordService;
import cz.zsstudanka.skola.bakakeeper.service.ServiceFactory;
import cz.zsstudanka.skola.bakakeeper.utils.PdfReportGenerator;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Dialog pro přímé nastavení hesla žáka.
 * Oznámení (PDF sestava) se odesílá pouze správci systému.
 */
public class SetPasswordDialog extends Dialog<Void> {

    public SetPasswordDialog(Window owner, StudentRecord student,
                             PasswordService passwordService,
                             Consumer<String> eventLogger, Runnable onComplete,
                             ServiceFactory serviceFactory, AppConfig config) {
        setTitle("Nastavit heslo");
        setHeaderText("Nastavit heslo: " + student.getDisplayName());
        initOwner(owner);

        ButtonType setType = new ButtonType("Nastavit", ButtonBar.ButtonData.OK_DONE);
        ButtonType closeType = new ButtonType("Zavřít", ButtonBar.ButtonData.CANCEL_CLOSE);
        getDialogPane().getButtonTypes().addAll(setType, closeType);

        // info
        GridPane info = new GridPane();
        info.setHgap(10);
        info.setVgap(4);
        info.add(new Label("Žák:"), 0, 0);
        info.add(new Label(student.getSurname() + " " + student.getGivenName()), 1, 0);
        info.add(new Label("Třída:"), 0, 1);
        info.add(new Label(student.getClassName()), 1, 1);

        // pole pro heslo
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Nové heslo");
        passwordField.setPrefWidth(250);

        PasswordField confirmField = new PasswordField();
        confirmField.setPromptText("Potvrzení hesla");
        confirmField.setPrefWidth(250);

        CheckBox mustChange = new CheckBox("Vyžadovat změnu hesla při příštím přihlášení");
        mustChange.setSelected(true);

        // oznámení jde pouze správci systému
        CheckBox sendNotification = new CheckBox("Odeslat oznámení správci");
        sendNotification.setSelected(false);
        // zakázat checkbox pokud není k dispozici poštovní konfigurace
        if (serviceFactory == null || config == null || config.getAdminMail() == null
                || config.getAdminMail().isBlank()) {
            sendNotification.setDisable(true);
            sendNotification.setText("Odeslat oznámení správci (pošta nekonfigurována)");
        }

        // validace
        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: #ef4444;");

        ProgressIndicator progress = new ProgressIndicator();
        progress.setPrefSize(18, 18);
        progress.setVisible(false);

        Label resultLabel = new Label();

        VBox content = new VBox(12, info,
                new Separator(),
                new Label("Nové heslo:"), passwordField,
                new Label("Potvrzení:"), confirmField,
                mustChange,
                sendNotification,
                errorLabel,
                new Separator(),
                new HBox(8, progress, resultLabel));
        content.setPadding(new Insets(10));
        getDialogPane().setContent(content);
        getDialogPane().setPrefWidth(420);

        // akce
        getDialogPane().lookupButton(setType).addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
            e.consume();
            errorLabel.setText("");

            String pwd = passwordField.getText();
            String confirm = confirmField.getText();

            if (pwd == null || pwd.isBlank()) {
                errorLabel.setText("Heslo nesmí být prázdné.");
                return;
            }

            if (!pwd.equals(confirm)) {
                errorLabel.setText("Hesla se neshodují.");
                return;
            }

            progress.setVisible(true);
            resultLabel.setText("Nastavuji heslo\u2026");

            Task<Boolean> task = new Task<>() {
                @Override
                protected Boolean call() {
                    return passwordService.setPassword(student.getDn(), pwd, mustChange.isSelected());
                }
            };

            task.setOnSucceeded(ev -> {
                progress.setVisible(false);
                if (task.getValue()) {
                    resultLabel.setText("Heslo bylo úspěšně nastaveno.");
                    resultLabel.setStyle("-fx-text-fill: #22c55e;");
                    eventLogger.accept("Nastavení hesla: " + student.getDisplayName());
                    if (onComplete != null) onComplete.run();

                    // odeslat oznámení správci systému
                    if (sendNotification.isSelected() && serviceFactory != null && config != null) {
                        sendAdminNotification(student, pwd, serviceFactory, config,
                                eventLogger, resultLabel, progress);
                    }
                } else {
                    resultLabel.setText("Nastavení hesla selhalo.");
                    resultLabel.setStyle("-fx-text-fill: #ef4444;");
                    eventLogger.accept("Nastavení hesla selhalo: " + student.getDisplayName());
                }
            });

            task.setOnFailed(ev -> {
                progress.setVisible(false);
                resultLabel.setText("Chyba: " +
                        (task.getException() != null ? task.getException().getMessage() : "neznámá"));
                resultLabel.setStyle("-fx-text-fill: #ef4444;");
                eventLogger.accept("Chyba nastavení hesla: " + student.getDisplayName());
            });

            BackgroundTaskRunner.run(task);
        });

        setResultConverter(bt -> null);
    }

    /**
     * Odešle PDF sestavu s nastaveným heslem pouze správci systému.
     * Na rozdíl od resetu hesla (kde se posílá i třídnímu učiteli) jde
     * oznámení o ručním nastavení hesla pouze správci.
     */
    private void sendAdminNotification(StudentRecord student, String password,
                                       ServiceFactory sf, AppConfig config,
                                       Consumer<String> eventLogger,
                                       Label statusLabel, ProgressIndicator progress) {
        statusLabel.setText("Odesílám oznámení správci\u2026");
        progress.setVisible(true);

        Task<Void> mailTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                int classNum = 0;
                try {
                    classNum = Integer.parseInt(student.getClassNumber());
                } catch (NumberFormatException | NullPointerException ignored) {}

                String upn = student.getUpn() != null ? student.getUpn() : student.getEmail();
                var row = new PdfReportGenerator.StudentReportRow(
                        classNum, student.getSurname(), student.getGivenName(),
                        upn, password, upn + "\t" + password);

                String classLabel = student.getClassName();
                if (classLabel == null || classLabel.isBlank()) classLabel = "Nezařazení";

                Map<String, List<PdfReportGenerator.StudentReportRow>> reportRows = new LinkedHashMap<>();
                reportRows.put(classLabel, List.of(row));

                // prázdná mapa třídních učitelů → e-mail půjde pouze správci
                Map<String, cz.zsstudanka.skola.bakakeeper.model.FacultyRecord> classTeachers =
                        new LinkedHashMap<>();

                ReportData data = new ReportData(reportRows, classTeachers, false, 1, 0);

                String emailBody = "Správcem systému bylo ručně nastaveno heslo žáka třídy {class}.\n"
                        + "V příloze naleznete sestavu s přístupovými údaji.\n\n"
                        + "Tuto sestavu považujte za důvěrnou.";

                Export.sendReports(data, "Nastavení hesla žáka ",
                        emailBody, config, BakaMailer.getInstance());
                return null;
            }
        };

        mailTask.setOnSucceeded(e -> {
            progress.setVisible(false);
            statusLabel.setText("Oznámení odesláno správci.");
            statusLabel.setStyle("-fx-text-fill: #22c55e;");
            eventLogger.accept("Oznámení správci odesláno: " + student.getDisplayName());
        });

        mailTask.setOnFailed(e -> {
            progress.setVisible(false);
            statusLabel.setText("Chyba při odesílání oznámení.");
            statusLabel.setStyle("-fx-text-fill: #ef4444;");
            eventLogger.accept("Chyba odesílání oznámení: " + student.getDisplayName()
                    + " – " + (mailTask.getException() != null
                    ? mailTask.getException().getMessage() : "neznámá"));
        });

        BackgroundTaskRunner.run(mailTask);
    }
}
