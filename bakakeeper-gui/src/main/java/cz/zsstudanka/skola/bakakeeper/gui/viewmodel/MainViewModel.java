package cz.zsstudanka.skola.bakakeeper.gui.viewmodel;

import cz.zsstudanka.skola.bakakeeper.gui.service.BackgroundTaskRunner;
import cz.zsstudanka.skola.bakakeeper.gui.service.GuiProgressListener;
import cz.zsstudanka.skola.bakakeeper.config.AppConfig;
import cz.zsstudanka.skola.bakakeeper.service.*;
import cz.zsstudanka.skola.bakakeeper.settings.Settings;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;

import java.io.File;

/**
 * Centrální ViewModel hlavního okna aplikace.
 * Drží stav konfigurace, ServiceFactory a výsledky operací.
 */
public class MainViewModel {

    private final BooleanProperty configLoaded = new SimpleBooleanProperty(false);
    private final StringProperty configFilePath = new SimpleStringProperty("");
    private final StringProperty statusText = new SimpleStringProperty("Připraven");
    private final DoubleProperty progress = new SimpleDoubleProperty(-1);
    private final BooleanProperty operationRunning = new SimpleBooleanProperty(false);
    private final StringProperty lastError = new SimpleStringProperty("");

    private final ObservableList<SyncResult> syncResults = FXCollections.observableArrayList();

    private ServiceFactory serviceFactory;
    private AppConfig config;
    private File currentFile;

    // -- vlastnosti pro binding --

    public BooleanProperty configLoadedProperty() { return configLoaded; }
    public StringProperty configFilePathProperty() { return configFilePath; }
    public StringProperty statusTextProperty() { return statusText; }
    public DoubleProperty progressProperty() { return progress; }
    public BooleanProperty operationRunningProperty() { return operationRunning; }
    public ReadOnlyStringProperty lastErrorProperty() { return lastError; }
    public ObservableList<SyncResult> getSyncResults() { return syncResults; }
    public ServiceFactory getServiceFactory() { return serviceFactory; }
    public AppConfig getConfig() { return config; }

    /**
     * Načte šifrovaný konfigurační soubor na pozadí.
     * Po dokončení aktualizuje configLoaded property – view by měl
     * naslouchat této property, ne přepisovat task handlery.
     */
    public void loadEncryptedConfig(File file, String passphrase) {
        lastError.set("");
        statusText.set("Načítám konfiguraci\u2026");

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                Settings settings = Settings.getInstance();
                settings.setPassphrase(passphrase);
                settings.load(file.getAbsolutePath());
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            config = Settings.getInstance();
            currentFile = file;
            configFilePath.set(file.getAbsolutePath());
            configLoaded.set(true);
            statusText.set("Konfigurace načtena: " + file.getName());
        });

        task.setOnFailed(e -> {
            configLoaded.set(false);
            String msg = task.getException() != null
                    ? task.getException().getMessage() : "Neznámá chyba";
            lastError.set(msg);
            statusText.set("Chyba při načítání konfigurace");
        });

        BackgroundTaskRunner.run(task);
    }

    /**
     * Načte nešifrovaný YAML konfigurační soubor na pozadí.
     */
    public void loadPlainConfig(File file) {
        lastError.set("");
        statusText.set("Načítám konfiguraci\u2026");

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                Settings settings = Settings.getInstance();
                settings.load(file.getAbsolutePath());
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            config = Settings.getInstance();
            currentFile = file;
            configFilePath.set(file.getAbsolutePath());
            configLoaded.set(true);
            statusText.set("Konfigurace načtena: " + file.getName());
        });

        task.setOnFailed(e -> {
            configLoaded.set(false);
            String msg = task.getException() != null
                    ? task.getException().getMessage() : "Neznámá chyba";
            lastError.set(msg);
            statusText.set("Chyba při načítání konfigurace");
        });

        BackgroundTaskRunner.run(task);
    }

    /**
     * Vytvoří ServiceFactory z aktuální konfigurace.
     */
    public void createServiceFactory() {
        if (config != null) {
            serviceFactory = new ServiceFactory(config);
        }
    }

    /**
     * Zavře aktuální konfiguraci a vrátí do výchozího stavu.
     */
    public void closeConfig() {
        serviceFactory = null;
        config = null;
        currentFile = null;
        configLoaded.set(false);
        configFilePath.set("");
        statusText.set("Připraven");
        lastError.set("");
        syncResults.clear();
    }

    /**
     * Spustí plnou synchronizaci na pozadí.
     *
     * @param repair     opravný režim
     * @param onComplete callback volaný po dokončení na FX vlákně (nullable)
     */
    public void runSync(boolean repair, Runnable onComplete) {
        if (serviceFactory == null) return;

        operationRunning.set(true);
        progress.set(-1);
        syncResults.clear();

        GuiProgressListener listener = new GuiProgressListener();
        listener.statusMessageProperty().addListener((obs, oldVal, newVal) ->
                Platform.runLater(() -> statusText.set(newVal)));
        listener.getResults().addListener((javafx.collections.ListChangeListener<SyncResult>) c -> {
            while (c.next()) {
                if (c.wasAdded()) {
                    Platform.runLater(() -> syncResults.addAll(c.getAddedSubList()));
                }
            }
        });

        Task<SyncReport> task = new Task<>() {
            @Override
            protected SyncReport call() {
                return serviceFactory.getOrchestrator().runFullSync(repair, listener);
            }
        };

        task.setOnSucceeded(e -> {
            SyncReport report = task.getValue();
            operationRunning.set(false);
            progress.set(1);
            statusText.set(String.format("Synchronizace dokončena – vytvořeno: %d, aktualizováno: %d, chyb: %d",
                    report.created(), report.updated(), report.errors()));
            if (onComplete != null) onComplete.run();
        });

        task.setOnFailed(e -> {
            operationRunning.set(false);
            progress.set(0);
            statusText.set("Chyba při synchronizaci: " +
                    (task.getException() != null ? task.getException().getMessage() : "neznámá chyba"));
        });

        BackgroundTaskRunner.run(task);
    }

    /**
     * Spustí omezenou synchronizaci na pozadí – jen pro daný ročník/třídu.
     *
     * @param classYear  ročník (null = nefiltrovat)
     * @param classLetter písmeno třídy (null = nefiltrovat)
     * @param repair     opravný režim
     * @param onComplete callback volaný po dokončení na FX vlákně (nullable)
     */
    public void runPartialSync(Integer classYear, String classLetter,
                                boolean repair, Runnable onComplete) {
        if (serviceFactory == null) return;

        operationRunning.set(true);
        progress.set(-1);
        syncResults.clear();

        GuiProgressListener listener = new GuiProgressListener();
        listener.statusMessageProperty().addListener((obs, oldVal, newVal) ->
                Platform.runLater(() -> statusText.set(newVal)));
        listener.getResults().addListener((javafx.collections.ListChangeListener<SyncResult>) c -> {
            while (c.next()) {
                if (c.wasAdded()) {
                    Platform.runLater(() -> syncResults.addAll(c.getAddedSubList()));
                }
            }
        });

        Task<SyncReport> task = new Task<>() {
            @Override
            protected SyncReport call() {
                return serviceFactory.getOrchestrator().runPartialSync(
                        classYear, classLetter, repair, listener);
            }
        };

        String scopeLabel = (classYear != null ? classYear.toString() : "vše")
                + (classLetter != null ? "." + classLetter : "");

        task.setOnSucceeded(e -> {
            SyncReport report = task.getValue();
            operationRunning.set(false);
            progress.set(1);
            statusText.set(String.format("Synchronizace (%s) dokončena – vytvořeno: %d, aktualizováno: %d, chyb: %d",
                    scopeLabel, report.created(), report.updated(), report.errors()));
            if (onComplete != null) onComplete.run();
        });

        task.setOnFailed(e -> {
            operationRunning.set(false);
            progress.set(0);
            statusText.set("Chyba při synchronizaci (" + scopeLabel + "): " +
                    (task.getException() != null ? task.getException().getMessage() : "neznámá chyba"));
        });

        BackgroundTaskRunner.run(task);
    }
}
