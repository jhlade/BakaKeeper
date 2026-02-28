package cz.zsstudanka.skola.bakakeeper.gui.view.dialog;

import cz.zsstudanka.skola.bakakeeper.config.EncryptedConfigLoader;
import cz.zsstudanka.skola.bakakeeper.config.YamlAppConfig;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.util.*;

/**
 * Průvodce vytvořením nové konfigurace – multi-step dialog.
 * Kroky: LDAP → SQL → SMTP → Přihlašovací údaje → Politiky → Uložení.
 */
public class ConfigWizardDialog extends Dialog<File> {

    private int currentStep = 0;
    private static final int TOTAL_STEPS = 6;
    private static final String[] STEP_NAMES = {
            "Active Directory", "SQL Server", "SMTP", "Přihlašovací údaje", "Politiky", "Uložení"
    };

    private final StackPane contentArea = new StackPane();
    private final VBox stepList = new VBox(4);
    private final Button backButton = new Button("\u2190 Zpět");
    private final Button nextButton = new Button("Další \u2192");
    private final Label[] stepLabels = new Label[TOTAL_STEPS];

    // pole formuláře
    private final TextField ldapDomain = new TextField();
    private final TextField ldapServer = new TextField();
    private final CheckBox ldapSsl = new CheckBox("SSL (port 636)");
    private final TextField ldapBase = new TextField();

    private final TextField sqlHost = new TextField();
    private final TextField sqlDatabase = new TextField("bakalari");
    private final ComboBox<String> sqlMethod = new ComboBox<>();
    private final TextField sqlPort = new TextField("1433");

    private final TextField mailDomain = new TextField();
    private final TextField smtpHost = new TextField();
    private final TextField adminMail = new TextField();
    private final TextField smtpPort = new TextField("587");
    private final CheckBox smtpStarttls = new CheckBox("STARTTLS");

    private final TextField globalUser = new TextField();
    private final PasswordField globalPass = new PasswordField();
    private final TextField sqlUser = new TextField();
    private final PasswordField sqlPass = new PasswordField();

    private final CheckBox[] pwdNoChange = new CheckBox[9];
    private final CheckBox[] pwdNoExpire = new CheckBox[9];
    private final CheckBox[] extMail = new CheckBox[9];

    private final PasswordField passphrase = new PasswordField();
    private final PasswordField passphraseConfirm = new PasswordField();
    private final Label saveStatus = new Label();

    private final Window owner;

    public ConfigWizardDialog(Window owner) {
        this.owner = owner;
        setTitle("Průvodce konfigurací");
        setHeaderText(null);
        initOwner(owner);
        setResizable(true);

        getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL);

        // sidebar se kroky
        stepList.setPadding(new Insets(10));
        stepList.setAlignment(Pos.TOP_LEFT);
        stepList.getStyleClass().add("wizard-step-list");
        for (int i = 0; i < TOTAL_STEPS; i++) {
            stepLabels[i] = new Label((i + 1) + ". " + STEP_NAMES[i]);
            stepLabels[i].getStyleClass().add("wizard-step");
            stepLabels[i].setPadding(new Insets(4, 8, 4, 8));
            stepList.getChildren().add(stepLabels[i]);
        }
        stepList.setPrefWidth(180);

        // navigační tlačítka
        backButton.setOnAction(e -> navigateTo(currentStep - 1));
        nextButton.setOnAction(e -> {
            if (currentStep < TOTAL_STEPS - 1) {
                navigateTo(currentStep + 1);
            } else {
                handleSave();
            }
        });

        HBox navBar = new HBox(8, backButton, nextButton);
        navBar.setAlignment(Pos.CENTER_RIGHT);
        navBar.setPadding(new Insets(8));

        // layout
        contentArea.setPadding(new Insets(10));
        contentArea.setMinWidth(380);
        contentArea.setMinHeight(280);

        VBox rightSide = new VBox(contentArea, navBar);
        VBox.setVgrow(contentArea, Priority.ALWAYS);

        HBox mainLayout = new HBox(stepList, new Separator(javafx.geometry.Orientation.VERTICAL), rightSide);
        HBox.setHgrow(rightSide, Priority.ALWAYS);

        getDialogPane().setContent(mainLayout);
        getDialogPane().setPrefWidth(620);
        getDialogPane().setPrefHeight(420);

        // inicializace comboboxu
        sqlMethod.getItems().addAll("ntlm", "kerberos", "sql");
        sqlMethod.setValue("ntlm");
        smtpStarttls.setSelected(true);

        // inicializace checkboxů politik
        for (int i = 0; i < 9; i++) {
            pwdNoChange[i] = new CheckBox((i + 1) + ". ročník");
            pwdNoExpire[i] = new CheckBox((i + 1) + ". ročník");
            extMail[i] = new CheckBox((i + 1) + ". ročník");
        }

        // zobrazit první krok
        navigateTo(0);

        // výsledek – File pokud úspěšně uloženo
        setResultConverter(bt -> null);
    }

    private void navigateTo(int step) {
        if (step < 0 || step >= TOTAL_STEPS) return;

        currentStep = step;
        backButton.setDisable(step == 0);
        nextButton.setText(step == TOTAL_STEPS - 1 ? "Uložit" : "Další \u2192");

        // zvýrazněni kroků
        for (int i = 0; i < TOTAL_STEPS; i++) {
            stepLabels[i].getStyleClass().removeAll("wizard-step-current", "wizard-step-completed");
            if (i == step) {
                stepLabels[i].getStyleClass().add("wizard-step-current");
            } else if (i < step) {
                stepLabels[i].getStyleClass().add("wizard-step-completed");
            }
        }

        contentArea.getChildren().clear();
        contentArea.getChildren().add(buildStepContent(step));
    }

    private Pane buildStepContent(int step) {
        return switch (step) {
            case 0 -> buildLdapStep();
            case 1 -> buildSqlStep();
            case 2 -> buildSmtpStep();
            case 3 -> buildCredentialsStep();
            case 4 -> buildPoliciesStep();
            case 5 -> buildSaveStep();
            default -> new VBox();
        };
    }

    private GridPane buildLdapStep() {
        GridPane grid = createFormGrid();
        int row = 0;
        addFormField(grid, row++, "Doména:", ldapDomain, "skola.local");
        addFormField(grid, row++, "Server:", ldapServer, "dc.skola.local");
        grid.add(ldapSsl, 1, row++);
        addFormField(grid, row++, "Kořenová OU:", ldapBase, "OU=Skola,DC=skola,DC=local");
        return grid;
    }

    private GridPane buildSqlStep() {
        GridPane grid = createFormGrid();
        int row = 0;
        addFormField(grid, row++, "Server:", sqlHost, "sql.skola.local");
        addFormField(grid, row++, "Databáze:", sqlDatabase, "bakalari");
        grid.add(new Label("Metoda:"), 0, row);
        grid.add(sqlMethod, 1, row++);
        addFormField(grid, row++, "Port:", sqlPort, "1433");
        return grid;
    }

    private GridPane buildSmtpStep() {
        GridPane grid = createFormGrid();
        int row = 0;
        addFormField(grid, row++, "Mailová doména:", mailDomain, "skola.cz");
        addFormField(grid, row++, "SMTP server:", smtpHost, "smtp.office365.com");
        addFormField(grid, row++, "E-mail správce:", adminMail, "admin@skola.cz");
        addFormField(grid, row++, "Port:", smtpPort, "587");
        grid.add(smtpStarttls, 1, row++);
        return grid;
    }

    private GridPane buildCredentialsStep() {
        GridPane grid = createFormGrid();
        int row = 0;
        addFormField(grid, row++, "Globální uživatel:", globalUser, "bakalari");
        grid.add(new Label("Globální heslo:"), 0, row);
        grid.add(globalPass, 1, row++);

        grid.add(new Separator(), 0, row++, 2, 1);
        grid.add(new Label("SQL uživatel (volitelné):"), 0, row);
        grid.add(sqlUser, 1, row++);
        grid.add(new Label("SQL heslo:"), 0, row);
        grid.add(sqlPass, 1, row++);
        return grid;
    }

    private VBox buildPoliciesStep() {
        VBox content = new VBox(12);
        content.setPadding(new Insets(4));

        content.getChildren().add(new Label("Heslo nelze změnit (žáci ročníků):"));
        FlowPane fp1 = new FlowPane(8, 4);
        fp1.getChildren().addAll(pwdNoChange);
        content.getChildren().add(fp1);

        content.getChildren().add(new Label("Heslo nikdy nevyprší:"));
        FlowPane fp2 = new FlowPane(8, 4);
        fp2.getChildren().addAll(pwdNoExpire);
        content.getChildren().add(fp2);

        content.getChildren().add(new Label("Povolená externí pošta:"));
        FlowPane fp3 = new FlowPane(8, 4);
        fp3.getChildren().addAll(extMail);
        content.getChildren().add(fp3);

        return content;
    }

    private VBox buildSaveStep() {
        VBox content = new VBox(12);
        content.setPadding(new Insets(4));

        content.getChildren().add(new Label("Passphrase pro šifrování:"));
        passphrase.setPromptText("Passphrase");
        passphrase.setPrefWidth(280);
        content.getChildren().add(passphrase);

        content.getChildren().add(new Label("Potvrzení passphrase:"));
        passphraseConfirm.setPromptText("Potvrzení");
        passphraseConfirm.setPrefWidth(280);
        content.getChildren().add(passphraseConfirm);

        saveStatus.setWrapText(true);
        content.getChildren().add(saveStatus);

        return content;
    }

    private GridPane createFormGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(4));
        return grid;
    }

    private void addFormField(GridPane grid, int row, String label, TextField field, String prompt) {
        grid.add(new Label(label), 0, row);
        field.setPromptText(prompt);
        field.setPrefWidth(260);
        grid.add(field, 1, row);
    }

    private void handleSave() {
        saveStatus.setText("");
        saveStatus.setStyle("");

        String pp = passphrase.getText();
        String ppConfirm = passphraseConfirm.getText();

        if (pp == null || pp.isBlank()) {
            saveStatus.setText("Passphrase nesmí být prázdná.");
            saveStatus.setStyle("-fx-text-fill: #ef4444;");
            return;
        }

        if (!pp.equals(ppConfirm)) {
            saveStatus.setText("Passphrase se neshodují.");
            saveStatus.setStyle("-fx-text-fill: #ef4444;");
            return;
        }

        // sestavení konfigurace
        Map<String, Object> configMap = buildConfigMap();

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Uložit konfiguraci");
        fileChooser.setInitialFileName("settings.dat");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Šifrovaná konfigurace", "*.dat"));

        File file = fileChooser.showSaveDialog(owner);
        if (file == null) return;

        try {
            YamlAppConfig yamlConfig = new YamlAppConfig(configMap);
            EncryptedConfigLoader.save(yamlConfig, file, pp.toCharArray());
            saveStatus.setText("Konfigurace uložena: " + file.getName());
            saveStatus.setStyle("-fx-text-fill: #22c55e;");

            // nastavit výsledek dialogu
            setResult(file);
        } catch (Exception ex) {
            saveStatus.setText("Chyba při ukládání: " + ex.getMessage());
            saveStatus.setStyle("-fx-text-fill: #ef4444;");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildConfigMap() {
        Map<String, Object> map = new LinkedHashMap<>();

        // LDAP
        Map<String, Object> ldap = new LinkedHashMap<>();
        ldap.put("domain", ldapDomain.getText());
        ldap.put("server", ldapServer.getText());
        ldap.put("ssl", ldapSsl.isSelected());
        ldap.put("base", ldapBase.getText());
        map.put("ldap", ldap);

        // SQL
        Map<String, Object> sql = new LinkedHashMap<>();
        sql.put("host", sqlHost.getText());
        sql.put("database", sqlDatabase.getText());
        sql.put("method", sqlMethod.getValue());
        try { sql.put("port", Integer.parseInt(sqlPort.getText())); }
        catch (NumberFormatException e) { sql.put("port", 1433); }
        if (!sqlUser.getText().isBlank()) {
            sql.put("user", sqlUser.getText());
            sql.put("password", sqlPass.getText());
        }
        map.put("sql", sql);

        // mail
        Map<String, Object> mail = new LinkedHashMap<>();
        mail.put("domain", mailDomain.getText());
        mail.put("smtp_host", smtpHost.getText());
        mail.put("admin_mail", adminMail.getText());
        try { mail.put("smtp_port", Integer.parseInt(smtpPort.getText())); }
        catch (NumberFormatException e) { mail.put("smtp_port", 587); }
        mail.put("smtp_starttls", smtpStarttls.isSelected());
        map.put("mail", mail);

        // credentials
        Map<String, Object> creds = new LinkedHashMap<>();
        creds.put("user", globalUser.getText());
        creds.put("password", globalPass.getText());
        map.put("credentials", creds);

        // politiky
        Map<String, Object> policies = new LinkedHashMap<>();
        policies.put("pwd_no_change", collectCheckedGrades(pwdNoChange));
        policies.put("pwd_no_expire", collectCheckedGrades(pwdNoExpire));
        policies.put("ext_mail_allowed", collectCheckedGrades(extMail));
        map.put("policies", policies);

        return map;
    }

    private List<Integer> collectCheckedGrades(CheckBox[] boxes) {
        List<Integer> grades = new ArrayList<>();
        for (int i = 0; i < boxes.length; i++) {
            if (boxes[i].isSelected()) {
                grades.add(i + 1);
            }
        }
        return grades;
    }
}
