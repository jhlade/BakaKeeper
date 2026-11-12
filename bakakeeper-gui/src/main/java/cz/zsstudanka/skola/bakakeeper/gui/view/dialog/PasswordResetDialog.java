package cz.zsstudanka.skola.bakakeeper.gui.view.dialog;

import cz.zsstudanka.skola.bakakeeper.config.AppConfig;
import cz.zsstudanka.skola.bakakeeper.connectors.BakaMailer;
import cz.zsstudanka.skola.bakakeeper.gui.service.BackgroundTaskRunner;
import cz.zsstudanka.skola.bakakeeper.gui.view.MainView;
import cz.zsstudanka.skola.bakakeeper.model.FacultyRecord;
import cz.zsstudanka.skola.bakakeeper.model.StudentRecord;
import cz.zsstudanka.skola.bakakeeper.routines.Export;
import cz.zsstudanka.skola.bakakeeper.routines.ReportData;
import cz.zsstudanka.skola.bakakeeper.service.PasswordService;
import cz.zsstudanka.skola.bakakeeper.service.ServiceFactory;
import cz.zsstudanka.skola.bakakeeper.utils.BakaUtils;
import cz.zsstudanka.skola.bakakeeper.utils.PdfReportGenerator;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Dialog pro reset hesla žáka – generování výchozího hesla
 * s možností okamžitého odeslání sestavy třídnímu učiteli.
 */
public class PasswordResetDialog extends Dialog<Void> {

    public PasswordResetDialog(Window owner, StudentRecord student,
                               PasswordService passwordService,
                               Consumer<String> eventLogger,
                               Runnable onComplete,
                               ServiceFactory serviceFactory,
                               AppConfig config,
                               boolean mailAvailable) {
        setTitle("Reset hesla");
        setHeaderText("Reset hesla: " + student.getDisplayName());
        initOwner(owner);

        ButtonType resetType = new ButtonType("Resetovat heslo", ButtonBar.ButtonData.OK_DONE);
        ButtonType closeType = new ButtonType("Zavřít", ButtonBar.ButtonData.CANCEL_CLOSE);
        getDialogPane().getButtonTypes().addAll(resetType, closeType);

        // info o žákovi
        GridPane info = new GridPane();
        info.setHgap(10);
        info.setVgap(4);
        info.add(new Label("Žák:"), 0, 0);
        info.add(new Label(student.getSurname() + " " + student.getGivenName()), 1, 0);
        info.add(new Label("Třída:"), 0, 1);
        info.add(new Label(student.getClassName()), 1, 1);
        if (student.getUpn() != null) {
            info.add(new Label("UPN:"), 0, 2);
            info.add(new Label(student.getUpn()), 1, 2);
        }

        // výsledek
        Label resultLabel = new Label();
        resultLabel.getStyleClass().add("password-result");

        // předvyplnit výchozí heslo (stejný algoritmus jako resetStudentPassword)
        int classNum = 0;
        try {
            classNum = Integer.parseInt(student.getClassNumber());
        } catch (NumberFormatException | NullPointerException ignored) {}
        String defaultPassword = BakaUtils.createInitialPassword(
                student.getSurname(), student.getGivenName(),
                student.getClassYear(), classNum);

        TextField generatedPassword = new TextField(defaultPassword);
        generatedPassword.setEditable(false);
        generatedPassword.setPrefWidth(250);

        Button copyButton = new Button("Kopírovat");
        copyButton.setOnAction(e -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(generatedPassword.getText());
            Clipboard.getSystemClipboard().setContent(content);
        });

        HBox passwordBox = new HBox(8, generatedPassword, copyButton);

        // checkbox – odeslat sestavu
        CheckBox sendReport = new CheckBox("Odeslat sestavu třídnímu učiteli");
        sendReport.setSelected(false);

        CheckBox sendAdmin = new CheckBox("Odeslat sestavu správci");
        sendAdmin.setSelected(false);

        // pokud jde sestava třídnímu, správce dostává kopii automaticky (nelze odškrtnout);
        // pokud třídní nedostává nic, lze o zaslání správci rozhodnout samostatně
        sendReport.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
            if (isSelected) {
                sendAdmin.setSelected(true);
                sendAdmin.setDisable(true);
            } else {
                sendAdmin.setDisable(false);
            }
        });

        CheckBox mustChange = new CheckBox("Vyžadovat změnu hesla při příštím přihlášení");
        mustChange.setSelected(false);

        // progress
        ProgressIndicator progress = new ProgressIndicator();
        progress.setPrefSize(18, 18);
        progress.setVisible(false);

        VBox content = new VBox(12, info,
                new Separator(),
                mustChange,
                sendReport,
                sendAdmin,
                new Separator(),
                new Label("Výchozí heslo:"),
                passwordBox,
                new HBox(8, progress, resultLabel));
        content.setPadding(new Insets(10));

        // SMTP nedostupné – heslo lze resetovat, sestava se neodešle
        if (!mailAvailable) {
            MainView.disableMailOptions(sendReport, sendAdmin);
            content.getChildren().add(content.getChildren().indexOf(sendAdmin) + 1,
                    MainView.mailUnavailableNotice());
        }

        getDialogPane().setContent(content);
        getDialogPane().setPrefWidth(420);

        // akce
        getDialogPane().lookupButton(resetType).addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
            e.consume();
            progress.setVisible(true);
            resultLabel.setText("Resetuji heslo\u2026");

            Task<String> task = new Task<>() {
                @Override
                protected String call() {
                    int cn = 0;
                    try {
                        cn = Integer.parseInt(student.getClassNumber());
                    } catch (NumberFormatException | NullPointerException ignored) {}
                    var result = passwordService.resetStudentPasswordWithResult(
                            student.getDn(),
                            student.getSurname(),
                            student.getGivenName(),
                            student.getClassYear(),
                            cn);
                    return result.password();
                }
            };

            task.setOnSucceeded(ev -> {
                progress.setVisible(false);
                String pwd = task.getValue();
                generatedPassword.setText(pwd);
                copyButton.setDisable(false);
                resultLabel.setText("Heslo bylo úspěšně resetováno.");
                resultLabel.setStyle("-fx-text-fill: #22c55e;");
                eventLogger.accept("Reset hesla: " + student.getDisplayName());

                if (onComplete != null) onComplete.run();

                // odeslat sestavu třídnímu učiteli a/nebo správci
                if ((sendReport.isSelected() || sendAdmin.isSelected())
                        && serviceFactory != null && config != null) {
                    sendPasswordReport(student, pwd, serviceFactory, config,
                            eventLogger, resultLabel, progress,
                            sendReport.isSelected(), sendAdmin.isSelected());
                }
            });

            task.setOnFailed(ev -> {
                progress.setVisible(false);
                resultLabel.setText("Chyba: " +
                        (task.getException() != null ? task.getException().getMessage() : "neznámá"));
                resultLabel.setStyle("-fx-text-fill: #ef4444;");
                eventLogger.accept("Chyba resetu hesla: " + student.getDisplayName());
            });

            BackgroundTaskRunner.run(task);
        });

        setResultConverter(bt -> null);
    }

    /**
     * Odešle PDF sestavu s novým heslem třídnímu učiteli a/nebo správci
     * dle zaškrtnutých voleb v dialogu.
     */
    private void sendPasswordReport(StudentRecord student, String password,
                                     ServiceFactory sf, AppConfig config,
                                     Consumer<String> eventLogger,
                                     Label statusLabel, ProgressIndicator progress,
                                     boolean notifyClassTeacher, boolean notifyAdmin) {
        statusLabel.setText("Odesílám sestavu\u2026");
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

                // třídní učitel
                Map<String, FacultyRecord> classTeachers = new LinkedHashMap<>();
                List<FacultyRecord> teachers = sf.getFacultyRepo().findActive(true);
                for (FacultyRecord t : teachers) {
                    if (classLabel.equals(t.getClassLabel())) {
                        classTeachers.put(classLabel, t);
                        break;
                    }
                }

                ReportData data = new ReportData(reportRows, classTeachers, true, 1, 0);
                Export.sendReports(data, "Přístupové údaje žáka ",
                        Export.resetEmailBody(), config, BakaMailer.getInstance(),
                        notifyClassTeacher, notifyAdmin);
                return null;
            }
        };

        mailTask.setOnSucceeded(e -> {
            progress.setVisible(false);
            statusLabel.setText("Sestava odeslána.");
            statusLabel.setStyle("-fx-text-fill: #22c55e;");
            eventLogger.accept("Sestava odeslána: " + student.getDisplayName());
        });

        mailTask.setOnFailed(e -> {
            progress.setVisible(false);
            statusLabel.setText("Chyba při odesílání sestavy.");
            statusLabel.setStyle("-fx-text-fill: #ef4444;");
            eventLogger.accept("Chyba odesílání sestavy: " + student.getDisplayName()
                    + " – " + (mailTask.getException() != null
                    ? mailTask.getException().getMessage() : "neznámá"));
        });

        BackgroundTaskRunner.run(mailTask);
    }
}
