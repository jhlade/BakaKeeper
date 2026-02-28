package cz.zsstudanka.skola.bakakeeper.gui.view;

import cz.zsstudanka.skola.bakakeeper.connectors.BakaMailer;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaUAC;
import cz.zsstudanka.skola.bakakeeper.gui.view.component.StatusBar;
import cz.zsstudanka.skola.bakakeeper.gui.view.dialog.AboutDialog;
import cz.zsstudanka.skola.bakakeeper.gui.view.dialog.PasswordResetDialog;
import cz.zsstudanka.skola.bakakeeper.gui.view.dialog.SetPasswordDialog;
import cz.zsstudanka.skola.bakakeeper.gui.view.dialog.SettingsDialog;
import cz.zsstudanka.skola.bakakeeper.gui.viewmodel.MainViewModel;
import cz.zsstudanka.skola.bakakeeper.model.FacultyRecord;
import cz.zsstudanka.skola.bakakeeper.model.StudentRecord;
import cz.zsstudanka.skola.bakakeeper.routines.Export;
import cz.zsstudanka.skola.bakakeeper.routines.ReportData;
import cz.zsstudanka.skola.bakakeeper.service.*;
import cz.zsstudanka.skola.bakakeeper.utils.BakaUtils;
import cz.zsstudanka.skola.bakakeeper.utils.PdfReportGenerator;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Hlavní okno aplikace – menu, dvoupanelový strom, detail a stavový řádek.
 */
public class MainView extends BorderPane {

    private final Stage stage;
    private final MainViewModel viewModel;
    private final StatusBar statusBar;

    private final TreeView<TreeItemData> sqlTree = new TreeView<>();
    private final TreeView<TreeItemData> ldapTree = new TreeView<>();
    private final GridPane detailPane = new GridPane();
    private final ListView<SyncResult> syncResultList = new ListView<>();
    private final ObservableList<String> eventLog = FXCollections.observableArrayList();
    private final ListView<String> eventListView = new ListView<>(eventLog);

    /** Index pro cross-select: internalId → TreeItem v SQL stromu */
    private final Map<String, TreeItem<TreeItemData>> sqlTreeIndex = new HashMap<>();
    /** Index pro cross-select: internalId → TreeItem v LDAP stromu */
    private final Map<String, TreeItem<TreeItemData>> ldapTreeIndex = new HashMap<>();
    /** Flag zabraňující cyklickému cross-selectu */
    private boolean suppressCrossSelect = false;
    /** Aktuálně aktivní strom (poslední strom, kde došlo k výběru) */
    private TreeView<TreeItemData> activeTree = sqlTree;
    /** Menu položky pro operace se studenty (zrcadlení kontextového menu) */
    private MenuItem menuResetPwd;
    private MenuItem menuSetPwd;
    private MenuItem menuToggleAccount;
    /** Souhrn výsledků synchronizace v toolbaru */
    private final Label syncSummary = new Label();
    /** Mapování třída (např. "5.A") → jméno třídního učitele */
    private final Map<String, String> classTeacherNames = new HashMap<>();
    /** Spodní TabPane a záložka Detail – pro automatické přepínání */
    private TabPane bottomTabs;
    private Tab detailTab;
    /** Placeholder pro prázdný seznam synchronizace */
    private final Label syncPlaceholder = new Label(
            "Spusťte kontrolu stavu nebo synchronizaci pomocí tlačítek výše.");

    /**
     * @param stage     primární stage
     * @param viewModel hlavní ViewModel
     */
    public MainView(Stage stage, MainViewModel viewModel) {
        this.stage = stage;
        this.viewModel = viewModel;
        this.statusBar = new StatusBar();

        buildMenuBar();
        buildCenter();
        buildStatusBar();
    }

    // ---- Menu ----

    private void buildMenuBar() {
        MenuBar menuBar = new MenuBar();

        // macOS – systémová lišta
        menuBar.setUseSystemMenuBar(true);

        // Soubor
        Menu fileMenu = new Menu("Soubor");
        MenuItem openItem = new MenuItem("Otevřít konfiguraci\u2026");
        openItem.setAccelerator(new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN));
        openItem.setOnAction(e -> handleOpenConfig());

        MenuItem closeItem = new MenuItem("Zavřít konfiguraci");
        closeItem.disableProperty().bind(viewModel.configLoadedProperty().not());
        closeItem.setOnAction(e -> viewModel.closeConfig());

        MenuItem exitItem = new MenuItem("Konec");
        exitItem.setAccelerator(new KeyCodeCombination(KeyCode.Q, KeyCombination.SHORTCUT_DOWN));
        exitItem.setOnAction(e -> Platform.exit());

        fileMenu.getItems().addAll(openItem, closeItem, new SeparatorMenuItem(), exitItem);

        // Nástroje
        Menu toolsMenu = new Menu("Nástroje");
        MenuItem syncItem = new MenuItem("Synchronizace");
        syncItem.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN));
        syncItem.disableProperty().bind(Bindings.or(
                viewModel.configLoadedProperty().not(),
                viewModel.operationRunningProperty()));
        syncItem.setOnAction(e -> handleSync());

        MenuItem checkItem = new MenuItem("Kontrola připojení");
        checkItem.setAccelerator(new KeyCodeCombination(KeyCode.T, KeyCombination.SHORTCUT_DOWN));
        checkItem.disableProperty().bind(Bindings.or(
                viewModel.configLoadedProperty().not(),
                viewModel.operationRunningProperty()));
        checkItem.setOnAction(e -> handleCheck());

        MenuItem settingsItem = new MenuItem("Nastavení\u2026");
        settingsItem.disableProperty().bind(viewModel.configLoadedProperty().not());
        settingsItem.setOnAction(e -> handleSettings());

        menuResetPwd = new MenuItem("Reset hesla\u2026");
        menuResetPwd.setDisable(true);
        menuResetPwd.setOnAction(e -> handleResetPassword(activeTree));

        menuSetPwd = new MenuItem("Nastavit heslo\u2026");
        menuSetPwd.setDisable(true);
        menuSetPwd.setOnAction(e -> handleSetPassword(activeTree));

        menuToggleAccount = new MenuItem("Zakázat účet");
        menuToggleAccount.setDisable(true);
        menuToggleAccount.setOnAction(e -> handleToggleAccount(activeTree));

        toolsMenu.getItems().addAll(syncItem, checkItem,
                new SeparatorMenuItem(), settingsItem,
                new SeparatorMenuItem(), menuResetPwd, menuSetPwd, menuToggleAccount);

        // Zobrazení
        Menu viewMenu = new Menu("Zobrazení");
        MenuItem refreshItem = new MenuItem("Obnovit data");
        refreshItem.setAccelerator(new KeyCodeCombination(KeyCode.F5));
        refreshItem.disableProperty().bind(viewModel.configLoadedProperty().not());
        refreshItem.setOnAction(e -> loadTreeData());

        viewMenu.getItems().add(refreshItem);

        // Nápověda
        Menu helpMenu = new Menu("Nápověda");
        MenuItem aboutItem = new MenuItem("O programu");
        aboutItem.setOnAction(e -> new AboutDialog(stage).showAndWait());

        helpMenu.getItems().add(aboutItem);

        menuBar.getMenus().addAll(fileMenu, toolsMenu, viewMenu, helpMenu);
        setTop(menuBar);
    }

    // ---- Centrální oblast ----

    private void buildCenter() {
        // SQL strom (evidence Bakalářů)
        sqlTree.setShowRoot(true);
        sqlTree.setRoot(new TreeItem<>(new TreeItemData.RootNode("Evidence Bakalářů")));
        sqlTree.getRoot().setExpanded(true);
        sqlTree.setCellFactory(tv -> new PersonTreeCell());

        // LDAP strom (Active Directory)
        ldapTree.setShowRoot(true);
        ldapTree.setRoot(new TreeItem<>(new TreeItemData.RootNode("Active Directory")));
        ldapTree.getRoot().setExpanded(true);
        ldapTree.setCellFactory(tv -> new PersonTreeCell());

        // výběr v kterémkoliv stromu → zobrazit detail + cross-select
        installCrossSelect();

        // kontextové menu
        setupContextMenu(sqlTree);
        setupContextMenu(ldapTree);

        // popisky panelů
        VBox sqlPanel = new VBox(4,
                createPanelHeader("Evidence Bakalářů (SQL)"),
                sqlTree);
        VBox.setVgrow(sqlTree, Priority.ALWAYS);

        VBox ldapPanel = new VBox(4,
                createPanelHeader("Active Directory (LDAP)"),
                ldapTree);
        VBox.setVgrow(ldapTree, Priority.ALWAYS);

        // horizontální split – dva stromy
        SplitPane treeSplit = new SplitPane(sqlPanel, ldapPanel);
        treeSplit.setDividerPositions(0.5);

        // detail panel
        detailPane.setHgap(10);
        detailPane.setVgap(6);
        detailPane.setPadding(new Insets(10));
        detailPane.getStyleClass().add("detail-pane");

        ScrollPane detailScroll = new ScrollPane(detailPane);
        detailScroll.setFitToWidth(true);
        detailScroll.setPrefHeight(200);

        // seznam výsledků synchronizace – zobrazujeme jen změny a problémy (ne NO_CHANGE)
        syncResultList.setCellFactory(lv -> new SyncResultCell());
        FilteredList<SyncResult> filteredSyncResults = new FilteredList<>(
                viewModel.getSyncResults(),
                r -> r.getType() != SyncResult.Type.NO_CHANGE);
        syncResultList.setItems(filteredSyncResults);
        syncPlaceholder.setWrapText(true);
        syncResultList.setPlaceholder(syncPlaceholder);

        // toolbar pro synchronizaci
        ComboBox<String> scopeSelector = new ComboBox<>();
        scopeSelector.getItems().add("Vše");
        for (int y = 1; y <= 9; y++) {
            scopeSelector.getItems().add(y + ". ročník");
            for (String letter : new String[]{"A", "B", "C", "D", "E"}) {
                scopeSelector.getItems().add(y + "." + letter);
            }
        }
        scopeSelector.setValue("Vše");
        scopeSelector.setPrefWidth(130);

        Button statusButton = new Button("Stav");
        statusButton.setOnAction(e -> handleScopedSync(scopeSelector.getValue(), false));
        statusButton.disableProperty().bind(Bindings.or(
                viewModel.configLoadedProperty().not(),
                viewModel.operationRunningProperty()));

        Button syncButton = new Button("Synchronizovat");
        syncButton.setOnAction(e -> handleScopedSync(scopeSelector.getValue(), true));
        syncButton.disableProperty().bind(Bindings.or(
                viewModel.configLoadedProperty().not(),
                viewModel.operationRunningProperty()));

        syncSummary.setText("");

        HBox syncToolbar = new HBox(8,
                new Label("Rozsah:"), scopeSelector,
                statusButton, syncButton,
                new Separator(Orientation.VERTICAL),
                syncSummary);
        syncToolbar.setPadding(new Insets(4, 8, 4, 8));
        syncToolbar.setAlignment(Pos.CENTER_LEFT);

        VBox syncPane = new VBox(syncToolbar, syncResultList);
        VBox.setVgrow(syncResultList, Priority.ALWAYS);

        // aktualizace souhrnu při změně výsledků
        viewModel.getSyncResults().addListener(
                (javafx.collections.ListChangeListener<SyncResult>) c -> {
                    long total = viewModel.getSyncResults().size();
                    long errors = viewModel.getSyncResults().stream()
                            .filter(r -> r.getType() == SyncResult.Type.ERROR).count();
                    long changes = viewModel.getSyncResults().stream()
                            .filter(r -> r.getType() != SyncResult.Type.NO_CHANGE
                                    && r.getType() != SyncResult.Type.ERROR).count();
                    if (total == 0) {
                        syncSummary.setText("");
                    } else if (errors == 0 && changes == 0) {
                        // vše v pořádku – žádné změny ani chyby
                        syncSummary.setText("Vše v pořádku (zkontrolováno: " + total + ")");
                        syncPlaceholder.setText("\u2714 Vše v pořádku");
                    } else {
                        syncSummary.setText(String.format(
                                "Zkontrolováno: %d  Změny: %d  Chyby: %d",
                                total, changes, errors));
                    }
                });

        // log událostí aktuální relace
        eventListView.setPlaceholder(new Label("Žádné události"));
        eventListView.setPrefHeight(150);

        // vertikální split – stromy nahoře, detail/výsledky dole
        TabPane bottomTabs = new TabPane();
        bottomTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        Tab detailTab = new Tab("Detail", detailScroll);
        bottomTabs.getTabs().addAll(
                new Tab("Synchronizace", syncPane),
                detailTab,
                new Tab("Události", eventListView)
        );
        this.detailTab = detailTab;
        this.bottomTabs = bottomTabs;

        SplitPane mainSplit = new SplitPane(treeSplit, bottomTabs);
        mainSplit.setOrientation(Orientation.VERTICAL);
        mainSplit.setDividerPositions(0.65);

        setCenter(mainSplit);
    }

    private Label createPanelHeader(String text) {
        Label header = new Label(text);
        header.getStyleClass().add("panel-header");
        header.setPadding(new Insets(4, 8, 4, 8));
        return header;
    }

    // ---- Status bar ----

    private void buildStatusBar() {
        statusBar.lockedProperty().bind(viewModel.configLoadedProperty().not());
        statusBar.filePathProperty().bind(viewModel.configFilePathProperty());
        statusBar.statusTextProperty().bind(viewModel.statusTextProperty());

        viewModel.operationRunningProperty().addListener((obs, oldVal, newVal) ->
                statusBar.setProgressVisible(newVal));
        statusBar.progressProperty().bind(viewModel.progressProperty());

        setBottom(statusBar);
    }

    // ---- Akce ----

    private void handleOpenConfig() {
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Otevřít konfigurační soubor");
        fileChooser.getExtensionFilters().addAll(
                new javafx.stage.FileChooser.ExtensionFilter("Šifrovaná konfigurace", "*.dat"),
                new javafx.stage.FileChooser.ExtensionFilter("YAML konfigurace", "*.yml", "*.yaml"),
                new javafx.stage.FileChooser.ExtensionFilter("Všechny soubory", "*.*")
        );
        java.io.File file = fileChooser.showOpenDialog(stage);
        if (file == null) return;

        // zavřít aktuální konfiguraci (configLoaded true→false)
        viewModel.closeConfig();

        if (file.getName().endsWith(".dat")) {
            // dialog s PasswordField (ne TextInputDialog s plain textem)
            Optional<String> passphrase = askPassphrase();
            if (passphrase.isEmpty() || passphrase.get().isBlank()) return;
            // ViewModel nastaví configLoaded→true, BakaKeeperApp zachytí změnu
            viewModel.loadEncryptedConfig(file, passphrase.get());
        } else {
            viewModel.loadPlainConfig(file);
        }
    }

    private Optional<String> askPassphrase() {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Passphrase");
        dialog.setHeaderText("Zadejte passphrase pro dešifrování konfigurace");
        dialog.initOwner(stage);

        ButtonType okType = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(okType, ButtonType.CANCEL);

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Passphrase");
        passwordField.setPrefWidth(300);

        VBox content = new VBox(8, new Label("Passphrase:"), passwordField);
        content.setPadding(new Insets(10));
        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(bt -> bt == okType ? passwordField.getText() : null);
        dialog.setOnShown(e -> passwordField.requestFocus());
        return dialog.showAndWait();
    }

    private void handleSync() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.initOwner(stage);
        confirm.setTitle("Synchronizace");
        confirm.setHeaderText("Spustit plnou synchronizaci?");
        confirm.setContentText("Budou synchronizovány všechny záznamy mezi evidencí Bakalářů a Active Directory.");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            viewModel.runSync(false, () -> {
                logEvent("Synchronizace dokončena");
                loadTreeData();
            });
        }
    }

    /**
     * Spustí synchronizaci s definovaným rozsahem ze záložky Synchronizace.
     * Parsuje scope string (např. "Vše", "3. ročník", "5.B") na classYear/classLetter.
     *
     * @param scope  zvolený rozsah z ComboBoxu
     * @param repair true = opravit, false = jen kontrola stavu
     */
    private void handleScopedSync(String scope, boolean repair) {
        String action = repair ? "synchronizaci" : "kontrolu stavu";
        String confirmText = "Vše".equals(scope)
                ? "Bude provedena " + action + " všech záznamů."
                : "Bude provedena " + action + " pro rozsah: " + scope + ".";

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.initOwner(stage);
        confirm.setTitle(repair ? "Synchronizace" : "Kontrola stavu");
        confirm.setHeaderText("Spustit " + action + "?");
        confirm.setContentText(confirmText);

        ButtonType yesButton = new ButtonType("Ano", ButtonBar.ButtonData.YES);
        ButtonType noButton = new ButtonType("Ne", ButtonBar.ButtonData.NO);
        confirm.getButtonTypes().setAll(yesButton, noButton);

        confirm.showAndWait().ifPresent(bt -> {
            if (bt != yesButton) return;

            // vyčistit předchozí výsledky a resetovat placeholder
            viewModel.getSyncResults().clear();
            syncSummary.setText("");
            syncPlaceholder.setText("Probíhá " + (repair ? "synchronizace" : "kontrola stavu") + "…");

            Runnable onComplete = () -> {
                logEvent((repair ? "Synchronizace" : "Kontrola stavu")
                        + " dokončena (" + scope + ")");
                loadTreeData();
            };

            if ("Vše".equals(scope)) {
                viewModel.runSync(repair, onComplete);
            } else {
                // parsovat rozsah
                Integer classYear = null;
                String classLetter = null;

                if (scope.contains(". ročník")) {
                    // "3. ročník" → classYear=3
                    classYear = Integer.parseInt(scope.substring(0, scope.indexOf('.')));
                } else if (scope.contains(".")) {
                    // "5.B" → classYear=5, classLetter="B"
                    String[] parts = scope.split("\\.");
                    classYear = Integer.parseInt(parts[0]);
                    classLetter = parts[1];
                }

                viewModel.runPartialSync(classYear, classLetter, repair, onComplete);
            }
        });
    }

    private void handleCheck() {
        var dialog = new CheckDialog(stage, () -> {
            viewModel.createServiceFactory();
            var sf = viewModel.getServiceFactory();
            if (sf == null) {
                throw new IllegalStateException("Nepodařilo se vytvořit připojení ke službám.");
            }
            return sf.getCheckService();
        });
        dialog.showAndWait();
    }

    /**
     * Načte data do stromů (SQL evidence + LDAP).
     * Volá se z BakaKeeperApp po úspěšné kontrole konektivity.
     */
    public void loadTreeData() {
        var sf = viewModel.getServiceFactory();
        var config = viewModel.getConfig();
        if (sf == null || config == null) return;

        viewModel.statusTextProperty().set("Načítám data\u2026");

        cz.zsstudanka.skola.bakakeeper.gui.service.BackgroundTaskRunner.run(
                new javafx.concurrent.Task<TreeLoadResult>() {
                    @Override
                    protected TreeLoadResult call() {
                        var sqlStudents = sf.getStudentRepo().findActive(null, null);
                        var ldapStudents = sf.getLdapUserRepo().findAllStudents(
                                config.getLdapBaseStudents(), config.getLdapBaseAlumni());
                        var faculty = sf.getFacultyRepo().findActive(false);
                        var comparison = buildComparison(sqlStudents, ldapStudents);
                        return new TreeLoadResult(sqlStudents, ldapStudents, faculty, comparison);
                    }

                    @Override
                    protected void succeeded() {
                        var result = getValue();
                        var cmp = result.comparison();

                        populateTree(sqlTree, "Evidence Bakalářů", cmp.sqlSide(),
                                result.faculty(), sqlTreeIndex);
                        populateTree(ldapTree, "Active Directory", cmp.ldapSide(),
                                result.faculty(), ldapTreeIndex);

                        // mapování třída → jméno třídního učitele
                        classTeacherNames.clear();
                        if (result.faculty() != null) {
                            for (var f : result.faculty()) {
                                if (f.getClassLabel() != null && f.getSurname() != null) {
                                    classTeacherNames.put(f.getClassLabel(),
                                            f.getSurname() + " " + (f.getGivenName() != null ? f.getGivenName() : ""));
                                }
                            }
                        }

                        installCrossSelect();

                        String msg = String.format("Načteno %d SQL + %d LDAP záznamů, %d učitelů",
                                        result.sqlStudents().size(), result.ldapStudents().size(),
                                        result.faculty().size());
                        viewModel.statusTextProperty().set(msg);
                        logEvent(msg);
                    }

                    @Override
                    protected void failed() {
                        String msg = "Chyba při načítání dat";
                        viewModel.statusTextProperty().set(msg);
                        logEvent(msg);
                    }
                });
    }

    /** Výsledek porovnání jednoho studenta. */
    record ComparedStudent(
            StudentRecord student,
            SyncStatus status,
            StudentRecord counterpart,
            List<FieldDifference> differences
    ) {}

    /** Jeden rozdíl v atributu. */
    record FieldDifference(String fieldName, String sqlValue, String ldapValue) {}

    /** Celkový výsledek porovnání obou stran. */
    record ComparisonResult(
            List<ComparedStudent> sqlSide,
            List<ComparedStudent> ldapSide
    ) {}

    private record TreeLoadResult(
            List<StudentRecord> sqlStudents,
            List<StudentRecord> ldapStudents,
            List<FacultyRecord> faculty,
            ComparisonResult comparison) {
    }

    /**
     * Spáruje SQL a LDAP záznamy podle internalId a porovná klíčové atributy.
     * Čistě výpočetní – bez závislosti na UI, volá se na pozadí.
     */
    private static ComparisonResult buildComparison(
            List<StudentRecord> sqlStudents, List<StudentRecord> ldapStudents) {

        // indexy podle internalId
        Map<String, StudentRecord> ldapIndex = new HashMap<>();
        for (var ldap : ldapStudents) {
            if (ldap.getInternalId() != null && !ldap.getInternalId().isBlank()) {
                ldapIndex.put(ldap.getInternalId(), ldap);
            }
        }
        Map<String, StudentRecord> sqlIndex = new HashMap<>();
        for (var sql : sqlStudents) {
            if (sql.getInternalId() != null && !sql.getInternalId().isBlank()) {
                sqlIndex.put(sql.getInternalId(), sql);
            }
        }

        // SQL strana
        List<ComparedStudent> sqlSide = new ArrayList<>();
        for (var sql : sqlStudents) {
            String id = sql.getInternalId();
            StudentRecord ldap = (id != null) ? ldapIndex.get(id) : null;
            if (ldap == null) {
                sqlSide.add(new ComparedStudent(sql, SyncStatus.MISSING_IN_AD, null, List.of()));
            } else {
                var diffs = compareFields(sql, ldap);
                SyncStatus status = diffs.isEmpty() ? SyncStatus.OK : SyncStatus.MISMATCH;
                sqlSide.add(new ComparedStudent(sql, status, ldap, diffs));
            }
        }

        // LDAP strana
        List<ComparedStudent> ldapSide = new ArrayList<>();
        for (var ldap : ldapStudents) {
            String id = ldap.getInternalId();
            StudentRecord sql = (id != null) ? sqlIndex.get(id) : null;
            if (sql == null) {
                ldapSide.add(new ComparedStudent(ldap, SyncStatus.MISSING_IN_SQL, null, List.of()));
            } else {
                var diffs = compareFields(sql, ldap);
                SyncStatus status = diffs.isEmpty() ? SyncStatus.OK : SyncStatus.MISMATCH;
                ldapSide.add(new ComparedStudent(ldap, status, sql, diffs));
            }
        }

        return new ComparisonResult(sqlSide, ldapSide);
    }

    /**
     * Porovná 4 klíčové atributy SQL ↔ LDAP záznamu.
     */
    private static List<FieldDifference> compareFields(StudentRecord sql, StudentRecord ldap) {
        List<FieldDifference> diffs = new ArrayList<>();

        if (!Objects.equals(sql.getSurname(), ldap.getSurname())) {
            diffs.add(new FieldDifference("Příjmení", sql.getSurname(), ldap.getSurname()));
        }
        if (!Objects.equals(sql.getGivenName(), ldap.getGivenName())) {
            diffs.add(new FieldDifference("Jméno", sql.getGivenName(), ldap.getGivenName()));
        }

        // třída: SQL className vs. LDAP DN → BakaUtils.classStringFromDN()
        String ldapClass = (ldap.getDn() != null) ? BakaUtils.classStringFromDN(ldap.getDn()) : null;
        if (!Objects.equals(sql.getClassName(), ldapClass)) {
            diffs.add(new FieldDifference("Třída", sql.getClassName(), ldapClass));
        }

        // e-mail / UPN – case-insensitive
        String sqlEmail = sql.getEmail();
        String ldapUpn = ldap.getUpn();
        if (sqlEmail != null && ldapUpn != null) {
            if (!sqlEmail.equalsIgnoreCase(ldapUpn)) {
                diffs.add(new FieldDifference("E-mail / UPN", sqlEmail, ldapUpn));
            }
        } else if (!Objects.equals(sqlEmail, ldapUpn)) {
            diffs.add(new FieldDifference("E-mail / UPN", sqlEmail, ldapUpn));
        }

        return diffs;
    }

    /**
     * Naplní strom seskupenými záznamy a zároveň buduje index pro cross-select.
     * Struktura: Kořen → Žáci (rozbaleni, ročníky → třídy) + Učitelé.
     */
    private void populateTree(TreeView<TreeItemData> tree, String rootLabel,
                              List<ComparedStudent> students,
                              List<FacultyRecord> faculty,
                              Map<String, TreeItem<TreeItemData>> index) {
        index.clear();

        TreeItem<TreeItemData> root = new TreeItem<>(new TreeItemData.RootNode(rootLabel));
        root.setExpanded(true);

        // ---- Žáci ----
        boolean[] categoryHasIssues = {false};

        students.stream()
                .collect(Collectors.groupingBy(cs -> {
                    int year = cs.student().getClassYear();
                    if (year <= 0 && cs.student().getDn() != null) {
                        Integer dnYear = BakaUtils.classYearFromDn(cs.student().getDn());
                        if (dnYear != null && dnYear > 0) return dnYear;
                    }
                    return Math.max(year, 0);
                }))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(yearEntry -> {
                    boolean[] yearHasIssues = {false};

                    // nejprve vybudovat třídy a ročník, pak vytvořit uzly s hasIssues
                    List<TreeItem<TreeItemData>> classItems = new ArrayList<>();

                    yearEntry.getValue().stream()
                            .collect(Collectors.groupingBy(cs -> {
                                String cn = cs.student().getClassName();
                                if (cn != null && !cn.isBlank()) return cn;
                                if (cs.student().getDn() != null) {
                                    String fromDn = BakaUtils.classStringFromDN(cs.student().getDn());
                                    if (fromDn != null && !fromDn.isBlank()) return fromDn;
                                }
                                return "\u2014";
                            }))
                            .entrySet().stream()
                            .sorted(Map.Entry.comparingByKey())
                            .forEach(classEntry -> {
                                boolean classHasIssues = classEntry.getValue().stream()
                                        .anyMatch(cs -> cs.status() != SyncStatus.OK);

                                TreeItem<TreeItemData> classItem = new TreeItem<>(
                                        new TreeItemData.ClassNode(
                                                classEntry.getKey(),
                                                classEntry.getValue().size(),
                                                classHasIssues));

                                classEntry.getValue().stream()
                                        .sorted(Comparator.comparing(
                                                        (ComparedStudent cs) -> cs.student().getSurname(),
                                                        Comparator.nullsLast(Comparator.naturalOrder()))
                                                .thenComparing(
                                                        cs -> cs.student().getGivenName(),
                                                        Comparator.nullsLast(Comparator.naturalOrder())))
                                        .forEach(cs -> {
                                            TreeItem<TreeItemData> item = new TreeItem<>(
                                                    new TreeItemData.StudentNode(cs.student(), cs.status()));
                                            classItem.getChildren().add(item);

                                            String id = cs.student().getInternalId();
                                            if (id != null && !id.isBlank()) {
                                                index.put(id, item);
                                            }
                                        });

                                if (classHasIssues) yearHasIssues[0] = true;
                                classItems.add(classItem);
                            });

                    TreeItem<TreeItemData> yearItem = new TreeItem<>(
                            new TreeItemData.GradeNode(yearEntry.getKey(), yearHasIssues[0]));
                    yearItem.setExpanded(false);
                    yearItem.getChildren().addAll(classItems);

                    if (yearHasIssues[0]) categoryHasIssues[0] = true;
                    root.getChildren().add(yearItem); // dočasně, přesune se do kategorie
                });

        // vytvořit kategorii Žáci s hasIssues a přesunout ročníky
        List<TreeItem<TreeItemData>> gradeItems = new ArrayList<>(root.getChildren());
        root.getChildren().clear();

        TreeItem<TreeItemData> studentsCategory = new TreeItem<>(
                new TreeItemData.CategoryNode("Žáci", students.size(), categoryHasIssues[0]));
        studentsCategory.setExpanded(true);
        studentsCategory.getChildren().addAll(gradeItems);

        root.getChildren().add(studentsCategory);

        // ---- Učitelé ----
        if (faculty != null && !faculty.isEmpty()) {
            TreeItem<TreeItemData> teachersCategory = new TreeItem<>(
                    new TreeItemData.CategoryNode("Učitelé", faculty.size(), false));
            teachersCategory.setExpanded(false);

            faculty.stream()
                    .sorted(Comparator.comparing(FacultyRecord::getSurname,
                                    Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(FacultyRecord::getGivenName,
                                    Comparator.nullsLast(Comparator.naturalOrder())))
                    .forEach(f -> teachersCategory.getChildren().add(
                            new TreeItem<>(new TreeItemData.FacultyNode(f))));

            root.getChildren().add(teachersCategory);
        }

        tree.setRoot(root);
    }

    /**
     * Nainstaluje oboustranný cross-select mezi SQL a LDAP stromy.
     * Výběr žáka v jednom stromu automaticky vybere protějšek ve druhém.
     */
    private void installCrossSelect() {
        sqlTree.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            showDetail(newVal);
            if (!suppressCrossSelect) activeTree = sqlTree;
            if (suppressCrossSelect) return;
            crossSelectCounterpart(newVal, ldapTree, ldapTreeIndex);
            updateMenuState();
        });

        ldapTree.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            showDetail(newVal);
            if (!suppressCrossSelect) activeTree = ldapTree;
            if (suppressCrossSelect) return;
            crossSelectCounterpart(newVal, sqlTree, sqlTreeIndex);
            updateMenuState();
        });
    }

    /**
     * Vybere protějšek vybraného žáka v druhém stromu.
     */
    private void crossSelectCounterpart(TreeItem<TreeItemData> selected,
                                         TreeView<TreeItemData> targetTree,
                                         Map<String, TreeItem<TreeItemData>> targetIndex) {
        suppressCrossSelect = true;
        try {
            if (selected == null || !(selected.getValue() instanceof TreeItemData.StudentNode sn)) {
                targetTree.getSelectionModel().clearSelection();
                return;
            }

            String id = sn.student().getInternalId();
            TreeItem<TreeItemData> counterpart = (id != null) ? targetIndex.get(id) : null;

            if (counterpart != null) {
                // expandovat rodiče (třída → ročník → kořen)
                TreeItem<TreeItemData> parent = counterpart.getParent();
                while (parent != null) {
                    parent.setExpanded(true);
                    parent = parent.getParent();
                }
                targetTree.getSelectionModel().select(counterpart);
                targetTree.scrollTo(targetTree.getRow(counterpart));
            } else {
                targetTree.getSelectionModel().clearSelection();
            }
        } finally {
            suppressCrossSelect = false;
        }
    }

    // ---- Detail (3-sloupcový: Pole | Evidence SQL | Active Directory LDAP) ----

    private void showDetail(TreeItem<TreeItemData> item) {
        detailPane.getChildren().clear();
        detailPane.getColumnConstraints().clear();
        if (item == null || item.getValue() == null) return;

        // přepnout na záložku Detail při výběru uzlu ve stromu
        if (bottomTabs != null && detailTab != null) {
            bottomTabs.getSelectionModel().select(detailTab);
        }

        TreeItemData data = item.getValue();
        if (data instanceof TreeItemData.StudentNode sn) {
            // najít protějšek podle internalId
            String id = sn.student().getInternalId();
            StudentRecord counterpart = null;
            List<FieldDifference> diffs = List.of();

            // zkusit najít ComparedStudent informace z indexu
            if (id != null) {
                var sqlItem = sqlTreeIndex.get(id);
                var ldapItem = ldapTreeIndex.get(id);
                if (sqlItem != null && sqlItem.getValue() instanceof TreeItemData.StudentNode sqlSn) {
                    counterpart = findCounterpart(sqlSn, ldapTreeIndex);
                    diffs = findDifferences(sqlSn, ldapTreeIndex);
                }
                if (counterpart == null && ldapItem != null
                        && ldapItem.getValue() instanceof TreeItemData.StudentNode ldapSn) {
                    counterpart = findCounterpart(ldapSn, sqlTreeIndex);
                    diffs = findDifferences(ldapSn, sqlTreeIndex);
                }
            }

            showStudentDetail(sn.student(), sn.status(), counterpart, diffs);
        } else if (data instanceof TreeItemData.ClassNode cn) {
            String teacher = classTeacherNames.get(cn.classLabel());
            String classText = teacher != null
                    ? cn.classLabel() + " (" + teacher.trim() + ")"
                    : cn.classLabel();
            addSimpleRow(0, "Třída", classText);
            addSimpleRow(1, "Počet žáků", String.valueOf(cn.studentCount()));
            if (teacher != null) {
                addSimpleRow(2, "Třídní učitel", teacher.trim());
            }
        } else if (data instanceof TreeItemData.GradeNode gn) {
            addSimpleRow(0, "Ročník", gn.year() > 0 ? gn.year() + "." : "Nezařazení");
        } else if (data instanceof TreeItemData.CategoryNode cat) {
            addSimpleRow(0, "Kategorie", cat.label());
            addSimpleRow(1, "Počet", String.valueOf(cat.count()));
        } else if (data instanceof TreeItemData.FacultyNode fn) {
            showFacultyDetail(fn.faculty());
        }
    }

    private void showFacultyDetail(FacultyRecord f) {
        int row = 0;
        addSimpleRow(row++, "Příjmení", f.getSurname());
        addSimpleRow(row++, "Jméno", f.getGivenName());
        if (f.getClassLabel() != null) {
            addSimpleRow(row++, "Třídní učitel", f.getClassLabel());
        }
        if (f.getEmail() != null) addSimpleRow(row++, "E-mail", f.getEmail());
        if (f.getDn() != null) addSimpleRow(row++, "DN", f.getDn());
        if (f.getInternalId() != null) addSimpleRow(row++, "Interní ID", f.getInternalId());
    }

    /** Najde protějšek studenta přes index druhého stromu. */
    private StudentRecord findCounterpart(TreeItemData.StudentNode sn,
                                           Map<String, TreeItem<TreeItemData>> otherIndex) {
        String id = sn.student().getInternalId();
        if (id == null) return null;
        var item = otherIndex.get(id);
        if (item != null && item.getValue() instanceof TreeItemData.StudentNode other) {
            return other.student();
        }
        return null;
    }

    /** Najde seznam rozdílů z buildComparison() přes strom. */
    private List<FieldDifference> findDifferences(TreeItemData.StudentNode sn,
                                                   Map<String, TreeItem<TreeItemData>> otherIndex) {
        // rekonstruujeme porovnání – jednoduché, protože data jsou v paměti
        String id = sn.student().getInternalId();
        if (id == null) return List.of();
        var otherItem = otherIndex.get(id);
        if (otherItem == null) return List.of();
        if (!(otherItem.getValue() instanceof TreeItemData.StudentNode other)) return List.of();

        // určit, kdo je SQL a kdo LDAP – SQL strom je ten, kde index = sqlTreeIndex
        StudentRecord sql, ldap;
        if (sqlTreeIndex.containsKey(id)
                && sqlTreeIndex.get(id).getValue() instanceof TreeItemData.StudentNode sqlSn) {
            sql = sqlSn.student();
        } else {
            sql = sn.student();
        }
        if (ldapTreeIndex.containsKey(id)
                && ldapTreeIndex.get(id).getValue() instanceof TreeItemData.StudentNode ldapSn) {
            ldap = ldapSn.student();
        } else {
            ldap = other.student();
        }
        return compareFields(sql, ldap);
    }

    /**
     * 3-sloupcový detail studenta: Pole | Evidence (SQL) | Active Directory (LDAP).
     */
    private void showStudentDetail(StudentRecord selected, SyncStatus status,
                                    StudentRecord counterpart, List<FieldDifference> diffs) {
        // nastavit 3 sloupce
        ColumnConstraints colLabel = new ColumnConstraints();
        colLabel.setMinWidth(130);
        ColumnConstraints colSql = new ColumnConstraints();
        colSql.setMinWidth(180);
        colSql.setHgrow(Priority.ALWAYS);
        ColumnConstraints colLdap = new ColumnConstraints();
        colLdap.setMinWidth(180);
        colLdap.setHgrow(Priority.ALWAYS);
        detailPane.getColumnConstraints().addAll(colLabel, colSql, colLdap);

        // sada názvů polí s neshodou pro barevné zvýraznění
        Set<String> diffFields = new HashSet<>();
        for (var d : diffs) {
            diffFields.add(d.fieldName());
        }

        // určit SQL a LDAP stranu
        StudentRecord sql = null;
        StudentRecord ldap = null;
        if (status == SyncStatus.MISSING_IN_SQL) {
            ldap = selected;
            sql = counterpart; // může být null
        } else {
            sql = selected;
            ldap = counterpart; // může být null
            // pokud vybraný je z LDAP stromu ale má protějšek, přehodit
            if (counterpart != null && selected.getDn() != null && selected.getClassName() == null) {
                ldap = selected;
                sql = counterpart;
            }
        }

        int row = 0;

        // záhlaví
        addHeaderRow(row++);

        // stav
        String statusText = switch (status) {
            case OK -> "\u2714 Spárováno";
            case MISMATCH -> "\u26A0 Neshoda";
            case MISSING_IN_AD -> "\u2718 Chybí v AD";
            case MISSING_IN_SQL -> "\u2718 Chybí v evidenci";
            default -> status.name();
        };
        addSimpleRow(row++, "Stav", statusText);

        // porovnávaná pole
        row = addComparedRow(row, "Příjmení",
                sql != null ? sql.getSurname() : null,
                ldap != null ? ldap.getSurname() : null,
                diffFields.contains("Příjmení"));
        row = addComparedRow(row, "Jméno",
                sql != null ? sql.getGivenName() : null,
                ldap != null ? ldap.getGivenName() : null,
                diffFields.contains("Jméno"));
        row = addComparedRow(row, "Třída",
                sql != null ? sql.getClassName() : null,
                ldap != null && ldap.getDn() != null ? BakaUtils.classStringFromDN(ldap.getDn()) : null,
                diffFields.contains("Třída"));

        // třídní učitel – dohledat z mapy
        String studentClass = sql != null ? sql.getClassName()
                : (ldap != null && ldap.getDn() != null ? BakaUtils.classStringFromDN(ldap.getDn()) : null);
        String teacher = studentClass != null ? classTeacherNames.get(studentClass) : null;
        if (teacher != null) {
            row = addComparedRow(row, "Třídní učitel", teacher.trim(), teacher.trim(), false);
        }

        row = addComparedRow(row, "E-mail / UPN",
                sql != null ? sql.getEmail() : null,
                ldap != null ? ldap.getUpn() : null,
                diffFields.contains("E-mail / UPN"));

        // identifikace
        row = addComparedRow(row, "Interní ID",
                sql != null ? sql.getInternalId() : null,
                ldap != null ? ldap.getInternalId() : null,
                false);
        row = addComparedRow(row, "Číslo v TV",
                sql != null ? sql.getClassNumber() : null,
                null, false);

        // LDAP specifické atributy
        row = addComparedRow(row, "DN", null,
                ldap != null ? ldap.getDn() : null, false);
        row = addComparedRow(row, "sAMAccountName", null,
                ldap != null ? ldap.getSamAccountName() : null, false);
        row = addComparedRow(row, "UAC", null,
                ldap != null ? decodeUac(ldap.getUac()) : null, false);
        row = addComparedRow(row, "Poslední přihlášení", null,
                ldap != null ? formatFileTime(ldap.getLastLogon()) : null, false);
        row = addComparedRow(row, "Změna hesla", null,
                ldap != null ? formatFileTime(ldap.getPwdLastSet()) : null, false);
        row = addComparedRow(row, "Skupiny", null,
                ldap != null ? formatMemberOf(ldap.getMemberOf()) : null, false);

        // SQL specifické atributy – zákonný zástupce
        if (sql != null && sql.getGuardianSurname() != null) {
            String guardian = sql.getGuardianSurname()
                    + " " + (sql.getGuardianGivenName() != null ? sql.getGuardianGivenName() : "");
            row = addComparedRow(row, "Zákonný zástupce", guardian.trim(), null, false);
            row = addComparedRow(row, "E-mail zástupce",
                    sql.getGuardianEmail(), null, false);
            row = addComparedRow(row, "Telefon zástupce",
                    sql.getGuardianPhone(), null, false);
        }
    }

    /** Záhlaví tabulky: Pole | Evidence (SQL) | Active Directory (LDAP). */
    private void addHeaderRow(int row) {
        Label h1 = new Label("Pole");
        h1.getStyleClass().addAll("detail-label", "detail-header");
        Label h2 = new Label("Evidence (SQL)");
        h2.getStyleClass().addAll("detail-value", "detail-header");
        Label h3 = new Label("Active Directory (LDAP)");
        h3.getStyleClass().addAll("detail-value", "detail-header");
        detailPane.add(h1, 0, row);
        detailPane.add(h2, 1, row);
        detailPane.add(h3, 2, row);
    }

    /** Řádek s porovnáním SQL ↔ LDAP hodnoty; rozdíl se zvýrazní červeně. */
    private int addComparedRow(int row, String fieldName,
                                String sqlValue, String ldapValue, boolean isDiff) {
        // přeskočit řádek, kde obě hodnoty jsou null/prázdné
        if ((sqlValue == null || sqlValue.isBlank())
                && (ldapValue == null || ldapValue.isBlank())) {
            return row;
        }

        Label labelNode = new Label(fieldName + ":");
        labelNode.getStyleClass().add("detail-label");

        Label sqlNode = new Label(sqlValue != null && !sqlValue.isBlank() ? sqlValue : "—");
        sqlNode.getStyleClass().add("detail-value");
        sqlNode.setWrapText(true);

        Label ldapNode = new Label(ldapValue != null && !ldapValue.isBlank() ? ldapValue : "—");
        ldapNode.getStyleClass().add("detail-value");
        ldapNode.setWrapText(true);

        if (isDiff) {
            sqlNode.getStyleClass().add("detail-diff");
            ldapNode.getStyleClass().add("detail-diff");
        }

        detailPane.add(labelNode, 0, row);
        detailPane.add(sqlNode, 1, row);
        detailPane.add(ldapNode, 2, row);
        return row + 1;
    }

    /** Jednoduchý 1-sloupcový řádek (pro třídu, ročník). */
    private void addSimpleRow(int row, String label, String value) {
        Label labelNode = new Label(label + ":");
        labelNode.getStyleClass().add("detail-label");
        Label valueNode = new Label(value != null ? value : "—");
        valueNode.getStyleClass().add("detail-value");
        valueNode.setWrapText(true);
        detailPane.add(labelNode, 0, row);
        detailPane.add(valueNode, 1, row);
    }

    // ---- Pomocné formátovací metody ----

    /** Windows FILETIME (100ns od 1.1.1601) → čitelný datum+čas v CET. */
    private static String formatFileTime(Long fileTime) {
        if (fileTime == null || fileTime == 0) return null;
        // Windows FILETIME epoch: 1601-01-01; Java epoch: 1970-01-01
        // rozdíl: 11644473600 sekund
        long epochSeconds = (fileTime / 10_000_000L) - 11_644_473_600L;
        if (epochSeconds < 0 || epochSeconds > 4_102_444_800L) return null; // rok > 2100 = neplatné
        Instant instant = Instant.ofEpochSecond(epochSeconds);
        return DateTimeFormatter.ofPattern("d.M.yyyy HH:mm")
                .withZone(ZoneId.of("Europe/Prague"))
                .format(instant);
    }

    /** UAC bitmask → čitelné flagy přes EBakaUAC. */
    private static String decodeUac(int uac) {
        if (uac == 0) return "0";
        List<String> flags = new ArrayList<>();
        for (EBakaUAC flag : EBakaUAC.values()) {
            if (flag.checkFlag(uac)) {
                flags.add(flag.name());
            }
        }
        return "0x" + Integer.toHexString(uac).toUpperCase()
                + (flags.isEmpty() ? "" : " (" + String.join(", ", flags) + ")");
    }

    /** Seznam memberOf DN → zkrácené CN. */
    private static String formatMemberOf(List<String> memberOf) {
        if (memberOf == null || memberOf.isEmpty()) return null;
        return memberOf.stream()
                .map(BakaUtils::parseCN)
                .filter(cn -> cn != null && !cn.isBlank())
                .collect(Collectors.joining(", "));
    }

    // ---- Kontextové menu ----

    private void setupContextMenu(TreeView<TreeItemData> tree) {
        ContextMenu contextMenu = new ContextMenu();

        MenuItem resetPassword = new MenuItem("Reset hesla\u2026");
        resetPassword.setOnAction(e -> handleResetPassword(tree));

        MenuItem setPassword = new MenuItem("Nastavit heslo\u2026");
        setPassword.setOnAction(e -> handleSetPassword(tree));

        // jedna položka – text a akce se nastaví dynamicky dle UAC
        MenuItem toggleAccount = new MenuItem("Zakázat účet");
        toggleAccount.setOnAction(e -> handleToggleAccount(tree));

        SeparatorMenuItem separator = new SeparatorMenuItem();

        contextMenu.getItems().addAll(resetPassword, setPassword, separator, toggleAccount);

        tree.setContextMenu(contextMenu);

        // dynamicky zobrazit/skrýt položky podle typu vybraného uzlu
        tree.setOnContextMenuRequested(event -> {
            var selected = tree.getSelectionModel().getSelectedItem();
            if (selected == null || selected.getValue() == null) {
                contextMenu.getItems().forEach(item -> item.setVisible(false));
                return;
            }
            TreeItemData data = selected.getValue();
            boolean isStudent = data instanceof TreeItemData.StudentNode sn;
            boolean isResetScope = isStudent
                    || data instanceof TreeItemData.ClassNode
                    || data instanceof TreeItemData.GradeNode
                    || (data instanceof TreeItemData.CategoryNode cat && "Žáci".equals(cat.label()));

            // reset hesla – student, třída, ročník, kategorie Žáci
            resetPassword.setVisible(isResetScope);
            if (isResetScope && !isStudent) {
                resetPassword.setText("Hromadný reset hesel\u2026");
            } else {
                resetPassword.setText("Reset hesla\u2026");
            }

            // nastavit heslo – jen student
            setPassword.setVisible(isStudent);

            // zakázat/povolit – jen student s DN; text dle aktuálního UAC stavu
            if (isStudent && data instanceof TreeItemData.StudentNode studentNode
                    && studentNode.student().getDn() != null) {
                toggleAccount.setVisible(true);
                boolean disabled = EBakaUAC.ACCOUNTDISABLE.checkFlag(studentNode.student().getUac());
                toggleAccount.setText(disabled ? "Povolit účet" : "Zakázat účet");
            } else {
                toggleAccount.setVisible(false);
            }

            separator.setVisible(isStudent);
        });
    }

    /** Vrátí vybraného studenta, nebo null. */
    private StudentRecord getSelectedStudent(TreeView<TreeItemData> tree) {
        var selected = tree.getSelectionModel().getSelectedItem();
        if (selected != null && selected.getValue() instanceof TreeItemData.StudentNode sn) {
            return sn.student();
        }
        return null;
    }

    /**
     * Sesbírá všechny StudentNode záznamy pod daným TreeItem (rekurzivně).
     */
    private List<StudentRecord> collectStudents(TreeItem<TreeItemData> item) {
        List<StudentRecord> result = new ArrayList<>();
        collectStudentsRecursive(item, result);
        return result;
    }

    private void collectStudentsRecursive(TreeItem<TreeItemData> item, List<StudentRecord> result) {
        if (item.getValue() instanceof TreeItemData.StudentNode sn) {
            StudentRecord student = sn.student();
            if (student.getDn() != null) {
                result.add(student);
            } else {
                // SQL strana – zkusit najít LDAP protějšek s DN
                String id = student.getInternalId();
                if (id != null) {
                    var ldapItem = ldapTreeIndex.get(id);
                    if (ldapItem != null
                            && ldapItem.getValue() instanceof TreeItemData.StudentNode ldapSn
                            && ldapSn.student().getDn() != null) {
                        // zkopírovat SQL-specifická metadata na LDAP protějšek
                        copySqlMetadata(student, ldapSn.student());
                        result.add(ldapSn.student());
                    }
                }
            }
        }
        for (var child : item.getChildren()) {
            collectStudentsRecursive(child, result);
        }
    }

    /**
     * Pokusí se zjistit DN studenta, případně použije protějšek z LDAP stromu.
     * SQL záznamy nemají DN – ale pokud je student spárovaný, LDAP protějšek DN má.
     */
    private String resolveStudentDn(StudentRecord student) {
        if (student.getDn() != null) return student.getDn();
        String id = student.getInternalId();
        if (id == null) return null;
        var ldapItem = ldapTreeIndex.get(id);
        if (ldapItem != null && ldapItem.getValue() instanceof TreeItemData.StudentNode sn) {
            return sn.student().getDn();
        }
        return null;
    }

    /**
     * Vrátí studenta se správným DN pro operace s heslem.
     * Pokud je student ze SQL stromu, ale má protějšek v LDAP, použije LDAP záznam.
     * Pokud student nemá DN vůbec, zobrazí chybovou hlášku a vrátí null.
     */
    private StudentRecord resolveStudentForPassword(TreeView<TreeItemData> tree) {
        StudentRecord student = getSelectedStudent(tree);
        if (student == null) return null;

        String dn = resolveStudentDn(student);
        if (dn == null) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.initOwner(stage);
            alert.setTitle("Heslo nelze nastavit");
            alert.setHeaderText("Žák nemá účet v Active Directory");
            alert.setContentText("Nelze nastavit heslo pro žáka "
                    + student.getDisplayName() + " – nemá odpovídající účet v AD.");
            alert.showAndWait();
            return null;
        }

        // pokud původní student nemá DN, použijeme LDAP protějšek
        // a zkopírujeme SQL-specifická metadata (classNumber, className)
        if (student.getDn() == null) {
            String id = student.getInternalId();
            if (id != null) {
                var ldapItem = ldapTreeIndex.get(id);
                if (ldapItem != null && ldapItem.getValue() instanceof TreeItemData.StudentNode sn) {
                    copySqlMetadata(student, sn.student());
                    return sn.student();
                }
            }
        }
        return student;
    }

    /**
     * Zkopíruje SQL-specifická metadata (classNumber, className) z SQL záznamu
     * na LDAP protějšek, pokud je LDAP záznam nemá.
     * LDAP záznamy typicky nemají classNumber (číslo v třídním výkazu)
     * ani className (odvodí se z DN).
     */
    private void copySqlMetadata(StudentRecord sqlStudent, StudentRecord ldapStudent) {
        if (sqlStudent.getClassNumber() != null && ldapStudent.getClassNumber() == null) {
            ldapStudent.setClassNumber(sqlStudent.getClassNumber());
        }
        if (sqlStudent.getClassName() != null && ldapStudent.getClassName() == null) {
            ldapStudent.setClassName(sqlStudent.getClassName());
        }
    }

    private void handleResetPassword(TreeView<TreeItemData> tree) {
        var selected = tree.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        var sf = viewModel.getServiceFactory();
        if (sf == null) return;

        TreeItemData data = selected.getValue();

        if (data instanceof TreeItemData.StudentNode) {
            // jednotlivec – dialog s heslem (resolve DN z LDAP protějšku pokud potřeba)
            StudentRecord student = resolveStudentForPassword(tree);
            if (student == null) return;
            var dialog = new PasswordResetDialog(stage, student, sf.getPasswordService(),
                    this::logEvent, this::loadTreeData, sf, viewModel.getConfig());
            dialog.showAndWait();
        } else {
            // hromadný reset – třída / ročník / všichni žáci
            List<StudentRecord> students = collectStudents(selected);
            if (students.isEmpty()) return;

            String scopeLabel = switch (data) {
                case TreeItemData.ClassNode cn -> "třídu " + cn.classLabel();
                case TreeItemData.GradeNode gn -> gn.year() + ". ročník";
                case TreeItemData.CategoryNode cat -> "všechny žáky";
                default -> "vybrané žáky";
            };

            Dialog<Boolean> confirm = new Dialog<>();
            confirm.initOwner(stage);
            confirm.setTitle("Hromadný reset hesel");
            confirm.setHeaderText("Provést hromadný reset hesel?");

            Label infoLabel = new Label(String.format(
                    "Bude resetováno %d hesel pro %s na výchozí hodnotu.",
                    students.size(), scopeLabel));
            infoLabel.setWrapText(true);

            CheckBox sendReport = new CheckBox("Odeslat sestavu třídnímu učiteli");
            sendReport.setSelected(true);

            VBox dialogContent = new VBox(12, infoLabel, new Separator(), sendReport);
            dialogContent.setPadding(new Insets(10));
            confirm.getDialogPane().setContent(dialogContent);
            confirm.getDialogPane().setPrefWidth(400);

            ButtonType resetType = new ButtonType("Resetovat hesla", ButtonBar.ButtonData.OK_DONE);
            ButtonType closeType = new ButtonType("Zavřít", ButtonBar.ButtonData.CANCEL_CLOSE);
            confirm.getDialogPane().getButtonTypes().addAll(resetType, closeType);

            confirm.setResultConverter(bt -> bt == resetType ? Boolean.TRUE : null);

            confirm.showAndWait().ifPresent(result -> {
                if (Boolean.TRUE.equals(result)) {
                    runBatchPasswordReset(students, sf.getPasswordService(),
                            scopeLabel, sendReport.isSelected());
                }
            });
        }
    }

    /** Výsledek hromadného resetu hesel. */
    private record BatchResetResult(
            int ok, int fail,
            Map<String, List<PdfReportGenerator.StudentReportRow>> reportRows) {}

    /**
     * Spustí hromadný reset hesel na pozadí s průběhem ve stavovém řádku.
     * Sbírá hesla pro generování PDF sestav.
     */
    private void runBatchPasswordReset(List<StudentRecord> students,
                                        PasswordService passwordService,
                                        String scopeLabel,
                                        boolean sendReport) {
        viewModel.operationRunningProperty().set(true);
        viewModel.statusTextProperty().set("Resetuji hesla\u2026");

        cz.zsstudanka.skola.bakakeeper.gui.service.BackgroundTaskRunner.run(
                new javafx.concurrent.Task<BatchResetResult>() {
                    @Override
                    protected BatchResetResult call() {
                        int ok = 0;
                        int fail = 0;
                        int total = students.size();
                        Map<String, List<PdfReportGenerator.StudentReportRow>> reportRows =
                                new LinkedHashMap<>();

                        for (int i = 0; i < total; i++) {
                            var s = students.get(i);
                            try {
                                int classNum = 0;
                                try {
                                    classNum = Integer.parseInt(s.getClassNumber());
                                } catch (NumberFormatException ignored) {}

                                var result = passwordService.resetStudentPasswordWithResult(
                                        s.getDn(), s.getSurname(), s.getGivenName(),
                                        s.getClassYear(), classNum);

                                if (result.isSuccess()) {
                                    ok++;
                                    String upn = s.getUpn() != null ? s.getUpn() : s.getEmail();
                                    if (upn != null && !upn.isBlank()) {
                                        String cls = s.getClassName() != null
                                                ? s.getClassName() : "Nezařazení";
                                        reportRows.computeIfAbsent(cls, k -> new ArrayList<>())
                                                .add(new PdfReportGenerator.StudentReportRow(
                                                        classNum, s.getSurname(), s.getGivenName(),
                                                        upn, result.password(),
                                                        upn + "\t" + result.password()));
                                    }
                                } else {
                                    fail++;
                                }
                            } catch (Exception ex) {
                                fail++;
                            }
                            int progress = i + 1;
                            Platform.runLater(() -> {
                                viewModel.progressProperty().set((double) progress / total);
                                viewModel.statusTextProperty().set(
                                        String.format("Reset hesel: %d / %d", progress, total));
                            });
                        }
                        return new BatchResetResult(ok, fail, reportRows);
                    }

                    @Override
                    protected void succeeded() {
                        var result = getValue();
                        viewModel.operationRunningProperty().set(false);
                        viewModel.progressProperty().set(1);
                        String msg = String.format(
                                "Reset hesel dokončen (%s): %d úspěšně, %d chyb",
                                scopeLabel, result.ok(), result.fail());
                        viewModel.statusTextProperty().set(msg);
                        logEvent(msg);
                        loadTreeData();

                        if (sendReport && !result.reportRows().isEmpty()) {
                            sendBatchReport(result.reportRows());
                        }
                    }

                    @Override
                    protected void failed() {
                        viewModel.operationRunningProperty().set(false);
                        String msg = "Chyba při resetu hesel (" + scopeLabel + ")";
                        viewModel.statusTextProperty().set(msg);
                        logEvent(msg);
                    }
                });
    }

    /**
     * Odešle PDF sestavy s resetovanými hesly třídním učitelům a správci.
     */
    private void sendBatchReport(
            Map<String, List<PdfReportGenerator.StudentReportRow>> reportRows) {
        logEvent("Odesílám sestavy\u2026");

        cz.zsstudanka.skola.bakakeeper.gui.service.BackgroundTaskRunner.run(
                new javafx.concurrent.Task<Void>() {
                    @Override
                    protected Void call() throws Exception {
                        var sf = viewModel.getServiceFactory();
                        var config = viewModel.getConfig();
                        if (sf == null || config == null) return null;

                        // seřadit řádky v každé třídě podle příjmení
                        for (var list : reportRows.values()) {
                            list.sort(Comparator.comparing(
                                    PdfReportGenerator.StudentReportRow::surname));
                        }

                        // třídní učitelé
                        Map<String, FacultyRecord> classTeachers = new LinkedHashMap<>();
                        List<FacultyRecord> teachers = sf.getFacultyRepo().findActive(true);
                        for (FacultyRecord t : teachers) {
                            if (t.getClassLabel() != null
                                    && reportRows.containsKey(t.getClassLabel())) {
                                classTeachers.put(t.getClassLabel(), t);
                            }
                        }

                        int totalOk = reportRows.values().stream()
                                .mapToInt(List::size).sum();
                        ReportData data = new ReportData(
                                reportRows, classTeachers, true, totalOk, 0);
                        Export.sendReports(data, "Přístupové údaje žáků ",
                                Export.resetEmailBody(), config,
                                BakaMailer.getInstance());
                        return null;
                    }

                    @Override
                    protected void succeeded() {
                        logEvent("Sestavy úspěšně odeslány.");
                    }

                    @Override
                    protected void failed() {
                        String errMsg = getException() != null
                                ? getException().getMessage() : "neznámá chyba";
                        logEvent("Chyba při odesílání sestav: " + errMsg);
                    }
                });
    }

    private void handleSetPassword(TreeView<TreeItemData> tree) {
        StudentRecord student = resolveStudentForPassword(tree);
        if (student == null) return;

        var sf = viewModel.getServiceFactory();
        if (sf == null) return;

        var dialog = new SetPasswordDialog(stage, student, sf.getPasswordService(),
                this::logEvent, this::loadTreeData, sf, viewModel.getConfig());
        dialog.showAndWait();
    }

    /**
     * Zakáže nebo povolí účet podle aktuálního stavu UAC.
     * Zobrazí modální dialog s potvrzením (Ano / Ne) a výsledek zapíše do záložky Události.
     */
    private void handleToggleAccount(TreeView<TreeItemData> tree) {
        StudentRecord student = getSelectedStudent(tree);
        if (student == null || student.getDn() == null) return;

        var sf = viewModel.getServiceFactory();
        if (sf == null) return;

        boolean isDisabled = EBakaUAC.ACCOUNTDISABLE.checkFlag(student.getUac());
        String action = isDisabled ? "Povolit" : "Zakázat";

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.initOwner(stage);
        confirm.setTitle(action + " účet");
        confirm.setHeaderText(action + " účet: " + student.getDisplayName() + "?");
        confirm.setContentText(isDisabled
                ? "Účet bude aktivován v Active Directory."
                : "Účet bude deaktivován v Active Directory.");

        // Ano / Ne místo výchozích OK / Storno
        ButtonType yesButton = new ButtonType("Ano", ButtonBar.ButtonData.YES);
        ButtonType noButton = new ButtonType("Ne", ButtonBar.ButtonData.NO);
        confirm.getButtonTypes().setAll(yesButton, noButton);

        confirm.showAndWait().ifPresent(bt -> {
            if (bt == yesButton) {
                cz.zsstudanka.skola.bakakeeper.gui.service.BackgroundTaskRunner.run(
                        new javafx.concurrent.Task<SyncResult>() {
                            @Override
                            protected SyncResult call() {
                                return isDisabled
                                        ? sf.getAccountService().unsuspendAccount(student.getDn(), student.getUac())
                                        : sf.getAccountService().suspendAccount(student.getDn(), student.getUac());
                            }

                            @Override
                            protected void succeeded() {
                                String msg = (isDisabled ? "Účet povolen: " : "Účet zakázán: ")
                                        + student.getDisplayName();
                                viewModel.statusTextProperty().set(msg);
                                logEvent(msg);
                                loadTreeData(); // znovu načíst stav z AD
                            }

                            @Override
                            protected void failed() {
                                String msg = "Chyba při změně stavu účtu: " + student.getDisplayName();
                                viewModel.statusTextProperty().set(msg);
                                logEvent(msg);
                            }
                        });
            }
        });
    }

    // ---- Event log ----

    /** Zapíše událost do záložky Události s časovou značkou. Nejnovější nahoře. */
    private void logEvent(String message) {
        String timestamp = DateTimeFormatter.ofPattern("HH:mm:ss")
                .withZone(ZoneId.systemDefault())
                .format(Instant.now());
        String entry = "[" + timestamp + "] " + message;
        Platform.runLater(() -> {
            eventLog.addFirst(entry);
            eventListView.scrollTo(0);
        });
    }

    // ---- Aktualizace stavu menu ----

    /**
     * Aktualizuje stav menu položek (Reset hesla, Nastavit heslo, Zakázat/Povolit účet)
     * podle aktuálně vybraného uzlu ve stromu.
     */
    private void updateMenuState() {
        if (menuResetPwd == null) return; // ještě nebyl inicializován

        var selected = activeTree.getSelectionModel().getSelectedItem();
        boolean hasStudent = selected != null
                && selected.getValue() instanceof TreeItemData.StudentNode;
        boolean hasScope = hasStudent
                || (selected != null && (selected.getValue() instanceof TreeItemData.ClassNode
                        || selected.getValue() instanceof TreeItemData.GradeNode
                        || (selected.getValue() instanceof TreeItemData.CategoryNode cat
                            && "Žáci".equals(cat.label()))));

        boolean configOk = viewModel.configLoadedProperty().get();

        menuResetPwd.setDisable(!hasScope || !configOk);
        menuSetPwd.setDisable(!hasStudent || !configOk);

        if (hasStudent) {
            var sn = (TreeItemData.StudentNode) selected.getValue();
            String dn = resolveStudentDn(sn.student());
            boolean isDisabled = dn != null
                    && EBakaUAC.ACCOUNTDISABLE.checkFlag(sn.student().getUac());
            menuToggleAccount.setDisable(dn == null || !configOk);
            menuToggleAccount.setText(isDisabled ? "Povolit účet" : "Zakázat účet");
        } else {
            menuToggleAccount.setDisable(true);
        }

        if (hasScope && !hasStudent) {
            menuResetPwd.setText("Hromadný reset hesel\u2026");
        } else {
            menuResetPwd.setText("Reset hesla\u2026");
        }
    }

    // ---- Nastavení ----

    /** Otevře dialog nastavení. Po uložení znovu načte konfiguraci a data. */
    private void handleSettings() {
        var config = viewModel.getConfig();
        var filePath = viewModel.configFilePathProperty().get();
        if (config == null || filePath == null || filePath.isBlank()) return;

        var dialog = new SettingsDialog(stage, config, filePath);
        dialog.showAndWait().ifPresent(saved -> {
            if (Boolean.TRUE.equals(saved)) {
                viewModel.createServiceFactory();
                loadTreeData();
                logEvent("Nastavení uloženo a znovu načteno.");
            }
        });
    }

    // ---- Vnořené pomocné třídy ----

    /**
     * Datové položky stromu – sealed interface pro pattern matching.
     */
    public sealed interface TreeItemData {
        record RootNode(String label) implements TreeItemData {}
        record CategoryNode(String label, int count, boolean hasIssues) implements TreeItemData {}
        record GradeNode(int year, boolean hasIssues) implements TreeItemData {}
        record ClassNode(String classLabel, int studentCount, boolean hasIssues) implements TreeItemData {}
        record StudentNode(StudentRecord student, SyncStatus status) implements TreeItemData {}
        record FacultyNode(FacultyRecord faculty) implements TreeItemData {}
    }

    /**
     * Stav synchronizace položky.
     */
    public enum SyncStatus {
        OK, MISMATCH, MISSING_IN_AD, MISSING_IN_SQL, ERROR, UNKNOWN
    }

    /**
     * Buňka stromu – zobrazuje osoby s barevným zvýrazněním stavu.
     */
    private static class PersonTreeCell extends TreeCell<TreeItemData> {
        @Override
        protected void updateItem(TreeItemData item, boolean empty) {
            super.updateItem(item, empty);
            getStyleClass().removeAll("tree-cell-ok", "tree-cell-mismatch",
                    "tree-cell-missing", "tree-cell-error", "tree-cell-category");

            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }

            switch (item) {
                case TreeItemData.RootNode rn -> setText(rn.label());
                case TreeItemData.CategoryNode cat -> {
                    String catText = cat.label() + " (" + cat.count() + ")";
                    setText(cat.hasIssues() ? "\u26A0 " + catText : catText);
                    getStyleClass().add("tree-cell-category");
                    if (cat.hasIssues()) getStyleClass().add("tree-cell-mismatch");
                }
                case TreeItemData.GradeNode gn -> {
                    String gradeText = gn.year() > 0 ? gn.year() + ". ročník" : "Nezařazení";
                    setText(gn.hasIssues() ? "\u26A0 " + gradeText : gradeText);
                    if (gn.hasIssues()) getStyleClass().add("tree-cell-mismatch");
                }
                case TreeItemData.ClassNode cn -> {
                    String classText = cn.classLabel() + " (" + cn.studentCount() + ")";
                    setText(cn.hasIssues() ? "\u26A0 " + classText : classText);
                    if (cn.hasIssues()) getStyleClass().add("tree-cell-mismatch");
                }
                case TreeItemData.StudentNode sn -> {
                    String name = sn.student().getSurname() + " " + sn.student().getGivenName();
                    switch (sn.status()) {
                        case OK -> getStyleClass().add("tree-cell-ok");
                        case MISMATCH -> {
                            name = "\u26A0 " + name;
                            getStyleClass().add("tree-cell-mismatch");
                        }
                        case MISSING_IN_AD, MISSING_IN_SQL -> {
                            name = "\u26A0 " + name;
                            getStyleClass().add("tree-cell-missing");
                        }
                        case ERROR -> getStyleClass().add("tree-cell-error");
                        default -> {}
                    }
                    setText(name);
                }
                case TreeItemData.FacultyNode fn -> {
                    String label = fn.faculty().getSurname() + " " + fn.faculty().getGivenName();
                    if (fn.faculty().getClassLabel() != null) {
                        label += " (" + fn.faculty().getClassLabel() + ")";
                    }
                    setText(label);
                }
            }
        }
    }

    /**
     * Buňka seznamu výsledků synchronizace.
     */
    private static class SyncResultCell extends ListCell<SyncResult> {
        @Override
        protected void updateItem(SyncResult item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }

            String icon = switch (item.getType()) {
                case CREATED -> "\u2795"; // ➕
                case UPDATED -> "\u270F"; // ✏
                case RETIRED -> "\u274C"; // ❌
                case PAIRED -> "\uD83D\uDD17"; // 🔗
                case ERROR -> "\u26A0"; // ⚠
                case SKIPPED -> "\u23ED"; // ⏭
                case NO_CHANGE -> "\u2714"; // ✔
                default -> "\u2022"; // •
            };

            // bezpečné sestavení textu – entityId i description mohou být null
            String id = item.getEntityId();
            String desc = item.getDescription();
            var sb = new StringBuilder(icon);
            if (id != null && !id.isBlank()) {
                sb.append(" [").append(id).append("]");
            }
            if (desc != null && !desc.isBlank()) {
                sb.append(" ").append(desc);
            }
            setText(sb.toString());

            getStyleClass().removeAll("sync-ok", "sync-error", "sync-warn");
            if (item.getType() == SyncResult.Type.ERROR) {
                getStyleClass().add("sync-error");
            } else if (item.getType() == SyncResult.Type.SKIPPED) {
                getStyleClass().add("sync-warn");
            } else {
                getStyleClass().add("sync-ok");
            }
        }
    }
}
