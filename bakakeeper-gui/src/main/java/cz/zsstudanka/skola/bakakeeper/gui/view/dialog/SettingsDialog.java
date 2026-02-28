package cz.zsstudanka.skola.bakakeeper.gui.view.dialog;

import cz.zsstudanka.skola.bakakeeper.config.AppConfig;
import cz.zsstudanka.skola.bakakeeper.config.EncryptedConfigLoader;
import cz.zsstudanka.skola.bakakeeper.config.YamlAppConfig;
import cz.zsstudanka.skola.bakakeeper.settings.Settings;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Window;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.StringWriter;
import java.util.*;

/**
 * Dialog pro úpravu nastavení aplikace.
 * 7 kroků: AD, SQL, SMTP, Přihlašovací údaje, Politiky, Pravidla, Uložení.
 * Hodnoty se předvyplní z aktuální konfigurace.
 */
public class SettingsDialog extends Dialog<Boolean> {

    private int currentStep = 0;
    private static final int TOTAL_STEPS = 7;
    private static final String[] STEP_NAMES = {
            "Active Directory", "SQL Server", "SMTP",
            "Přihlašovací údaje", "Politiky", "Pravidla", "Uložení"
    };

    private final StackPane contentArea = new StackPane();
    private final VBox stepList = new VBox(4);
    private final Button backButton = new Button("\u2190 Zpět");
    private final Button nextButton = new Button("Další \u2192");
    private final Label[] stepLabels = new Label[TOTAL_STEPS];

    // LDAP
    private final TextField ldapDomain = new TextField();
    private final TextField ldapServer = new TextField();
    private final CheckBox ldapSsl = new CheckBox("SSL (port 636)");
    private final TextField ldapPort = new TextField();
    private final TextField ldapBase = new TextField();
    private final TextField ldapStudents = new TextField();
    private final TextField ldapAlumni = new TextField();
    private final TextField ldapFaculty = new TextField();
    private final TextField ldapTeachers = new TextField();
    private final TextField ldapManagement = new TextField();
    private final TextField ldapStudentGroups = new TextField();
    private final TextField ldapGlobalGroups = new TextField();
    private final TextField ldapDistLists = new TextField();
    private final TextField ldapContacts = new TextField();

    // SQL
    private final TextField sqlHost = new TextField();
    private final TextField sqlPort = new TextField();
    private final TextField sqlDatabase = new TextField();
    private final ComboBox<String> sqlMethod = new ComboBox<>();

    // SMTP
    private final TextField mailDomain = new TextField();
    private final TextField smtpHost = new TextField();
    private final TextField smtpPort = new TextField();
    private final TextField adminMail = new TextField();
    private final CheckBox smtpAuth = new CheckBox("Vyžadovat autentizaci");
    private final CheckBox smtpStarttls = new CheckBox("STARTTLS");

    // Přihlašovací údaje
    private final TextField globalUser = new TextField();
    private final PasswordField globalPass = new PasswordField();
    private final TextField ldapUser = new TextField();
    private final PasswordField ldapPass = new PasswordField();
    private final TextField sqlUser = new TextField();
    private final PasswordField sqlPass = new PasswordField();
    private final TextField smtpUser = new TextField();
    private final PasswordField smtpPass = new PasswordField();

    // Politiky
    private final CheckBox[] pwdNoChange = new CheckBox[9];
    private final CheckBox[] pwdNoExpire = new CheckBox[9];
    private final CheckBox[] extMail = new CheckBox[9];

    // Pravidla
    private final TextArea rulesEditor = new TextArea();

    // Uložení
    private final PasswordField passphrase = new PasswordField();
    private final PasswordField passphraseConfirm = new PasswordField();
    private final Label saveStatus = new Label();

    private final String filePath;

    /**
     * @param owner    vlastník dialogu
     * @param config   aktuální konfigurace k předvyplnění
     * @param filePath cesta k souboru s konfigurací
     */
    public SettingsDialog(Window owner, AppConfig config, String filePath) {
        this.filePath = filePath;
        setTitle("Nastavení");
        setHeaderText(null);
        initOwner(owner);
        setResizable(true);

        getDialogPane().getButtonTypes().add(ButtonType.CANCEL);

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
        contentArea.setMinWidth(430);
        contentArea.setMinHeight(340);

        VBox rightSide = new VBox(contentArea, navBar);
        VBox.setVgrow(contentArea, Priority.ALWAYS);

        HBox mainLayout = new HBox(stepList,
                new Separator(Orientation.VERTICAL), rightSide);
        HBox.setHgrow(rightSide, Priority.ALWAYS);

        getDialogPane().setContent(mainLayout);
        getDialogPane().setPrefWidth(700);
        getDialogPane().setPrefHeight(520);

        // inicializace ComboBoxu
        sqlMethod.getItems().addAll("ntlm", "kerberos", "sql");

        // inicializace checkboxů politik
        for (int i = 0; i < 9; i++) {
            pwdNoChange[i] = new CheckBox((i + 1) + ". ročník");
            pwdNoExpire[i] = new CheckBox((i + 1) + ". ročník");
            extMail[i] = new CheckBox((i + 1) + ". ročník");
        }

        // editor pravidel – monospace
        rulesEditor.setStyle("-fx-font-family: 'monospace'; -fx-font-size: 13px;");
        rulesEditor.setWrapText(false);

        // předvyplnění z konfigurace
        prefill(config);

        // zobrazit první krok
        navigateTo(0);

        setResultConverter(bt -> null);
    }

    /** Předvyplní všechna pole z aktuální konfigurace. */
    private void prefill(AppConfig config) {
        if (config == null) return;

        // LDAP
        setField(ldapDomain, config.getLdapDomain());
        setField(ldapServer, config.getLdapServer());
        ldapSsl.setSelected(config.isLdapSsl());
        setField(ldapPort, String.valueOf(config.getLdapPort()));
        setField(ldapBase, config.getLdapBase());
        setField(ldapStudents, config.getLdapBaseStudents());
        setField(ldapAlumni, config.getLdapBaseAlumni());
        setField(ldapFaculty, config.getLdapBaseFaculty());
        setField(ldapTeachers, config.getLdapBaseTeachers());
        setField(ldapManagement, config.getLdapBaseManagement());
        setField(ldapStudentGroups, config.getLdapBaseStudentGroups());
        setField(ldapGlobalGroups, config.getLdapBaseGlobalGroups());
        setField(ldapDistLists, config.getLdapBaseDistributionLists());
        setField(ldapContacts, config.getLdapBaseContacts());

        // SQL
        setField(sqlHost, config.getSqlHost());
        setField(sqlPort, String.valueOf(config.getSqlPort()));
        setField(sqlDatabase, config.getSqlDatabase());
        sqlMethod.setValue(config.getSqlConnectionMethod() != null
                ? config.getSqlConnectionMethod() : "ntlm");

        // SMTP
        setField(mailDomain, config.getMailDomain());
        setField(smtpHost, config.getSmtpHost());
        setField(smtpPort, String.valueOf(config.getSmtpPort()));
        setField(adminMail, config.getAdminMail());
        smtpAuth.setSelected(config.isSmtpAuth());
        smtpStarttls.setSelected(config.isSmtpStarttls());

        // Přihlašovací údaje
        setField(globalUser, config.getUser());
        globalPass.setText(config.getPass() != null ? config.getPass() : "");
        // per-service – jen pokud se liší od globálních
        if (!Objects.equals(config.getLdapUser(), config.getUser())) {
            setField(ldapUser, config.getLdapUser());
            ldapPass.setText(config.getLdapPass() != null ? config.getLdapPass() : "");
        }
        if (!Objects.equals(config.getSqlUser(), config.getUser())) {
            setField(sqlUser, config.getSqlUser());
            sqlPass.setText(config.getSqlPass() != null ? config.getSqlPass() : "");
        }
        if (!Objects.equals(config.getSmtpUser(), config.getUser())) {
            setField(smtpUser, config.getSmtpUser());
            smtpPass.setText(config.getSmtpPass() != null ? config.getSmtpPass() : "");
        }

        // Politiky
        fillPolicyBoxes(pwdNoChange, config.getPwdNoChange());
        fillPolicyBoxes(pwdNoExpire, config.getPwdNoExpire());
        fillPolicyBoxes(extMail, config.getExtMailAllowed());

        // Pravidla – serializovat jako YAML
        try {
            List<Map<String, Object>> rulesOut = new ArrayList<>();
            for (var rule : config.getRules()) {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("scope", rule.getScope().name());
                if (rule.getMatch() != null) r.put("match", rule.getMatch());
                if (rule.getAttributes() != null && !rule.getAttributes().isEmpty()) {
                    List<Map<String, Object>> attrsOut = new ArrayList<>();
                    for (var attr : rule.getAttributes()) {
                        Map<String, Object> a = new LinkedHashMap<>();
                        a.put("attribute", attr.attribute());
                        a.put("value", attr.value());
                        attrsOut.add(a);
                    }
                    r.put("attributes", attrsOut);
                }
                if (rule.getGroups() != null && !rule.getGroups().isEmpty()) {
                    r.put("groups", new ArrayList<>(rule.getGroups()));
                }
                rulesOut.add(r);
            }
            Map<String, Object> rulesMap = new LinkedHashMap<>();
            rulesMap.put("rules", rulesOut);
            Yaml yaml = new Yaml();
            StringWriter sw = new StringWriter();
            yaml.dump(rulesMap, sw);
            rulesEditor.setText(sw.toString());
        } catch (Exception e) {
            rulesEditor.setText("rules: []\n");
        }
    }

    private void setField(TextField field, String value) {
        field.setText(value != null ? value : "");
    }

    private void fillPolicyBoxes(CheckBox[] boxes, List<Integer> grades) {
        if (grades == null) return;
        for (int g : grades) {
            if (g >= 1 && g <= 9) {
                boxes[g - 1].setSelected(true);
            }
        }
    }

    // ---- Navigace ----

    private void navigateTo(int step) {
        if (step < 0 || step >= TOTAL_STEPS) return;

        currentStep = step;
        backButton.setDisable(step == 0);
        nextButton.setText(step == TOTAL_STEPS - 1 ? "Uložit" : "Další \u2192");

        for (int i = 0; i < TOTAL_STEPS; i++) {
            stepLabels[i].getStyleClass().removeAll(
                    "wizard-step-current", "wizard-step-completed");
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
            case 5 -> buildRulesStep();
            case 6 -> buildSaveStep();
            default -> new VBox();
        };
    }

    // ---- Kroky ----

    private Pane buildLdapStep() {
        GridPane grid = createFormGrid();
        int row = 0;
        addFormField(grid, row++, "Doména:", ldapDomain);
        addFormField(grid, row++, "Server:", ldapServer);
        grid.add(ldapSsl, 1, row++);
        addFormField(grid, row++, "Port:", ldapPort);
        addFormField(grid, row++, "Kořenová OU:", ldapBase);
        addFormField(grid, row++, "OU žáků:", ldapStudents);
        addFormField(grid, row++, "OU absolventů:", ldapAlumni);
        addFormField(grid, row++, "OU zaměstnanců:", ldapFaculty);
        addFormField(grid, row++, "OU učitelů:", ldapTeachers);
        addFormField(grid, row++, "OU vedení:", ldapManagement);
        addFormField(grid, row++, "OU skupin žáků:", ldapStudentGroups);
        addFormField(grid, row++, "OU glob. skupin:", ldapGlobalGroups);
        addFormField(grid, row++, "OU distr. seznamů:", ldapDistLists);
        addFormField(grid, row++, "OU kontaktů:", ldapContacts);

        ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true);
        scroll.setPadding(Insets.EMPTY);

        VBox wrapper = new VBox(scroll);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        return wrapper;
    }

    private Pane buildSqlStep() {
        GridPane grid = createFormGrid();
        int row = 0;
        addFormField(grid, row++, "Server:", sqlHost);
        addFormField(grid, row++, "Port:", sqlPort);
        addFormField(grid, row++, "Databáze:", sqlDatabase);
        grid.add(new Label("Metoda:"), 0, row);
        grid.add(sqlMethod, 1, row);
        return grid;
    }

    private Pane buildSmtpStep() {
        GridPane grid = createFormGrid();
        int row = 0;
        addFormField(grid, row++, "Mailová doména:", mailDomain);
        addFormField(grid, row++, "SMTP server:", smtpHost);
        addFormField(grid, row++, "Port:", smtpPort);
        addFormField(grid, row++, "E-mail správce:", adminMail);
        grid.add(smtpAuth, 1, row++);
        grid.add(smtpStarttls, 1, row);
        return grid;
    }

    private Pane buildCredentialsStep() {
        GridPane grid = createFormGrid();
        int row = 0;

        grid.add(new Label("Globální přihlašovací údaje"), 0, row++, 2, 1);
        addFormField(grid, row++, "Uživatel:", globalUser);
        grid.add(new Label("Heslo:"), 0, row);
        globalPass.setPrefWidth(280);
        grid.add(globalPass, 1, row++);

        grid.add(new Separator(), 0, row++, 2, 1);
        grid.add(new Label("Per-service (volitelné, přepíší globální)"), 0, row++, 2, 1);

        addFormField(grid, row++, "LDAP uživatel:", ldapUser);
        grid.add(new Label("LDAP heslo:"), 0, row);
        ldapPass.setPrefWidth(280);
        grid.add(ldapPass, 1, row++);

        addFormField(grid, row++, "SQL uživatel:", sqlUser);
        grid.add(new Label("SQL heslo:"), 0, row);
        sqlPass.setPrefWidth(280);
        grid.add(sqlPass, 1, row++);

        addFormField(grid, row++, "SMTP uživatel:", smtpUser);
        grid.add(new Label("SMTP heslo:"), 0, row);
        smtpPass.setPrefWidth(280);
        grid.add(smtpPass, 1, row);

        ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true);
        scroll.setPadding(Insets.EMPTY);

        VBox wrapper = new VBox(scroll);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        return wrapper;
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

    private VBox buildRulesStep() {
        VBox content = new VBox(8);
        content.setPadding(new Insets(4));

        Label info = new Label("Deklarativní pravidla synchronizace (YAML formát):");
        info.setWrapText(true);

        VBox.setVgrow(rulesEditor, Priority.ALWAYS);

        content.getChildren().addAll(info, rulesEditor);
        return content;
    }

    private VBox buildSaveStep() {
        VBox content = new VBox(12);
        content.setPadding(new Insets(4));

        boolean isEncrypted = filePath != null && filePath.endsWith(".dat");

        if (isEncrypted) {
            content.getChildren().add(new Label(
                    "Nová passphrase (ponechte prázdné pro zachování stávající):"));
            passphrase.setPromptText("Nová passphrase");
            passphrase.setPrefWidth(280);
            content.getChildren().add(passphrase);

            content.getChildren().add(new Label("Potvrzení passphrase:"));
            passphraseConfirm.setPromptText("Potvrzení");
            passphraseConfirm.setPrefWidth(280);
            content.getChildren().add(passphraseConfirm);
        } else {
            content.getChildren().add(new Label(
                    "Konfigurace bude uložena jako nešifrovaný YAML."));
        }

        content.getChildren().add(new Separator());
        content.getChildren().add(new Label("Cesta: " + filePath));

        saveStatus.setWrapText(true);
        content.getChildren().add(saveStatus);

        return content;
    }

    // ---- Formulářové helpery ----

    private GridPane createFormGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(4));
        return grid;
    }

    private void addFormField(GridPane grid, int row, String label, TextField field) {
        grid.add(new Label(label), 0, row);
        field.setPrefWidth(280);
        grid.add(field, 1, row);
    }

    // ---- Uložení ----

    @SuppressWarnings("unchecked")
    private void handleSave() {
        saveStatus.setText("");
        saveStatus.setStyle("");

        boolean isEncrypted = filePath != null && filePath.endsWith(".dat");

        // validace passphrase (jen pro šifrované)
        String pp = passphrase.getText();
        String ppConfirm = passphraseConfirm.getText();
        if (isEncrypted && pp != null && !pp.isBlank()) {
            if (!pp.equals(ppConfirm)) {
                saveStatus.setText("Passphrase se neshodují.");
                saveStatus.setStyle("-fx-text-fill: #ef4444;");
                return;
            }
        }

        // parsování pravidel
        List<Object> parsedRules;
        try {
            Yaml yaml = new Yaml();
            Object parsed = yaml.load(rulesEditor.getText());
            if (parsed instanceof Map<?, ?> map) {
                Object r = map.get("rules");
                if (r instanceof List<?> list) {
                    parsedRules = (List<Object>) list;
                } else {
                    parsedRules = List.of();
                }
            } else if (parsed instanceof List<?> list) {
                parsedRules = (List<Object>) list;
            } else {
                parsedRules = List.of();
            }
        } catch (Exception e) {
            saveStatus.setText("Chyba v YAML pravidlech: " + e.getMessage());
            saveStatus.setStyle("-fx-text-fill: #ef4444;");
            return;
        }

        // sestavení konfigurační mapy
        Map<String, Object> configMap = buildConfigMap(parsedRules);

        try {
            File file = new File(filePath);
            YamlAppConfig yamlConfig = new YamlAppConfig(configMap);

            if (isEncrypted) {
                // pokud nová passphrase → použít ji; jinak stávající ze Settings
                char[] passChars;
                if (pp != null && !pp.isBlank()) {
                    passChars = pp.toCharArray();
                } else {
                    // použijeme stávající passphrase z Settings
                    char[] existing = Settings.getInstance().getPassphrase();
                    if (existing == null || existing.length == 0) {
                        saveStatus.setText("Zadejte passphrase pro šifrování.");
                        saveStatus.setStyle("-fx-text-fill: #ef4444;");
                        return;
                    }
                    passChars = existing;
                }
                EncryptedConfigLoader.save(yamlConfig, file, passChars);
            } else {
                EncryptedConfigLoader.savePlain(yamlConfig, file);
            }

            // znovu načíst konfiguraci
            Settings.getInstance().load(filePath);

            saveStatus.setText("Konfigurace uložena.");
            saveStatus.setStyle("-fx-text-fill: #22c55e;");

            setResult(Boolean.TRUE);
        } catch (Exception ex) {
            saveStatus.setText("Chyba při ukládání: " + ex.getMessage());
            saveStatus.setStyle("-fx-text-fill: #ef4444;");
        }
    }

    private Map<String, Object> buildConfigMap(List<Object> parsedRules) {
        Map<String, Object> map = new LinkedHashMap<>();

        // LDAP
        Map<String, Object> ldap = new LinkedHashMap<>();
        ldap.put("domain", ldapDomain.getText());
        ldap.put("server", ldapServer.getText());
        ldap.put("ssl", ldapSsl.isSelected());
        putIfNotEmpty(ldap, "port", parseIntOrNull(ldapPort.getText()));
        ldap.put("base", ldapBase.getText());
        ldap.put("students", ldapStudents.getText());
        ldap.put("alumni", ldapAlumni.getText());
        ldap.put("faculty", ldapFaculty.getText());
        ldap.put("teachers", ldapTeachers.getText());
        ldap.put("management", ldapManagement.getText());
        ldap.put("student_groups", ldapStudentGroups.getText());
        ldap.put("global_groups", ldapGlobalGroups.getText());
        ldap.put("distribution_lists", ldapDistLists.getText());
        ldap.put("contacts", ldapContacts.getText());
        putIfNotBlank(ldap, "user", ldapUser.getText());
        putIfNotBlank(ldap, "password", ldapPass.getText());
        map.put("ldap", ldap);

        // SQL
        Map<String, Object> sql = new LinkedHashMap<>();
        sql.put("host", sqlHost.getText());
        putIfNotEmpty(sql, "port", parseIntOrNull(sqlPort.getText()));
        sql.put("database", sqlDatabase.getText());
        sql.put("method", sqlMethod.getValue());
        putIfNotBlank(sql, "user", sqlUser.getText());
        putIfNotBlank(sql, "password", sqlPass.getText());
        map.put("sql", sql);

        // Mail
        Map<String, Object> mail = new LinkedHashMap<>();
        mail.put("domain", mailDomain.getText());
        mail.put("smtp_host", smtpHost.getText());
        putIfNotEmpty(mail, "port", parseIntOrNull(smtpPort.getText()));
        mail.put("admin", adminMail.getText());
        if (!smtpAuth.isSelected()) mail.put("auth", false);
        if (!smtpStarttls.isSelected()) mail.put("starttls", false);
        putIfNotBlank(mail, "user", smtpUser.getText());
        putIfNotBlank(mail, "password", smtpPass.getText());
        map.put("mail", mail);

        // Credentials
        Map<String, Object> creds = new LinkedHashMap<>();
        creds.put("user", globalUser.getText());
        creds.put("password", globalPass.getText());
        map.put("credentials", creds);

        // Politiky
        Map<String, Object> policies = new LinkedHashMap<>();
        policies.put("pwd_no_change", collectCheckedGrades(pwdNoChange));
        policies.put("pwd_no_expire", collectCheckedGrades(pwdNoExpire));
        policies.put("ext_mail_allowed", collectCheckedGrades(extMail));
        map.put("policies", policies);

        // Pravidla
        map.put("rules", parsedRules);

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

    private Integer parseIntOrNull(String text) {
        if (text == null || text.isBlank()) return null;
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void putIfNotEmpty(Map<String, Object> map, String key, Object value) {
        if (value != null) map.put(key, value);
    }

    private void putIfNotBlank(Map<String, Object> map, String key, String value) {
        if (value != null && !value.isBlank()) map.put(key, value);
    }
}
