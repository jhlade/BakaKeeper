package cz.zsstudanka.skola.bakakeeper.gui;

import cz.zsstudanka.skola.bakakeeper.gui.service.BackgroundTaskRunner;
import cz.zsstudanka.skola.bakakeeper.gui.util.Icons;
import cz.zsstudanka.skola.bakakeeper.gui.view.CheckDialog;
import cz.zsstudanka.skola.bakakeeper.gui.view.MainView;
import cz.zsstudanka.skola.bakakeeper.gui.view.WelcomeView;
import cz.zsstudanka.skola.bakakeeper.gui.viewmodel.MainViewModel;
import cz.zsstudanka.skola.bakakeeper.settings.Version;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.util.Optional;

/**
 * Hlavní třída GUI aplikace BakaKeeper.
 * Řídí přechody mezi obrazovkami: Welcome → Check → Main.
 */
public class BakaKeeperApp extends Application {

    private MainViewModel viewModel;
    private Stage primaryStage;

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        this.viewModel = new MainViewModel();

        // globální zachycení neošetřených výjimek
        Thread.setDefaultUncaughtExceptionHandler((t, ex) ->
                Platform.runLater(() -> showUnhandledError(ex)));

        // ikony okna
        Icons.loadWindowIcons(primaryStage);

        primaryStage.setTitle(Version.getInstance().getName() + " " + Version.getInstance().getVersion());
        primaryStage.setMinWidth(900);
        primaryStage.setMinHeight(600);

        // naslouchat změně stavu configLoaded → přejít na kontrolu/hlavní okno
        viewModel.configLoadedProperty().addListener((obs, wasLoaded, isLoaded) -> {
            if (isLoaded) {
                runCheckAndShowMain();
            }
        });

        // chybová hláška při selhání načtení
        viewModel.lastErrorProperty().addListener((obs, oldErr, newErr) -> {
            if (newErr != null && !newErr.isBlank()) {
                showError("Chyba při načítání konfigurace", newErr);
            }
        });

        // úvodní obrazovka
        showWelcome();
        primaryStage.show();
    }

    private void showWelcome() {
        WelcomeView welcomeView = new WelcomeView(primaryStage, viewModel);

        StackPane root = new StackPane(welcomeView);
        Scene scene = new Scene(root, 900, 600);
        loadStylesheet(scene);
        primaryStage.setScene(scene);
    }

    /**
     * Po načtení konfigurace: zobrazí CheckDialog okamžitě, ServiceFactory
     * a kontroly konektivity běží na pozadí (nezablokuje FX vlákno).
     */
    private void runCheckAndShowMain() {
        var checkDialog = new CheckDialog(primaryStage, () -> {
            viewModel.createServiceFactory();
            var sf = viewModel.getServiceFactory();
            if (sf == null) {
                throw new IllegalStateException("Nepodařilo se vytvořit připojení ke službám.");
            }
            return sf.getCheckService();
        });

        Optional<Boolean> result = checkDialog.showAndWait();

        if (result.isPresent() && result.get()) {
            showMainView();
        } else {
            // uživatel zrušil kontrolu – zůstat na welcome
            viewModel.closeConfig();
        }
    }

    private void showMainView() {
        MainView mainView = new MainView(primaryStage, viewModel);

        Scene scene = new Scene(mainView, 1100, 700);
        loadStylesheet(scene);
        primaryStage.setScene(scene);

        // načíst data do stromů
        mainView.loadTreeData();
    }

    private void loadStylesheet(Scene scene) {
        var css = getClass().getResource(
                "/cz/zsstudanka/skola/bakakeeper/gui/css/bakakeeper.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
    }

    private void showError(String title, String detail) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(primaryStage);
        alert.setTitle("Chyba");
        alert.setHeaderText(title);
        alert.setContentText(detail);
        alert.showAndWait();
    }

    private void showUnhandledError(Throwable ex) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(primaryStage);
        alert.setTitle("Neočekávaná chyba");
        alert.setHeaderText("Došlo k neočekávané chybě");
        alert.setContentText(ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());

        java.io.StringWriter sw = new java.io.StringWriter();
        ex.printStackTrace(new java.io.PrintWriter(sw));

        javafx.scene.control.TextArea detailArea = new javafx.scene.control.TextArea(sw.toString());
        detailArea.setEditable(false);
        detailArea.setWrapText(true);
        detailArea.setMaxWidth(Double.MAX_VALUE);
        detailArea.setMaxHeight(Double.MAX_VALUE);

        alert.getDialogPane().setExpandableContent(detailArea);
        alert.showAndWait();
    }

    @Override
    public void stop() {
        BackgroundTaskRunner.shutdown();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
