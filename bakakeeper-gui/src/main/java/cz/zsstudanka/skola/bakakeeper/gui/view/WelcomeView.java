package cz.zsstudanka.skola.bakakeeper.gui.view;

import cz.zsstudanka.skola.bakakeeper.gui.util.Icons;
import cz.zsstudanka.skola.bakakeeper.gui.viewmodel.MainViewModel;
import cz.zsstudanka.skola.bakakeeper.settings.Version;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.Optional;

/**
 * Úvodní obrazovka – zobrazuje logo, název aplikace a nabízí
 * otevření konfigurace nebo vytvoření nové.
 *
 * View pouze deleguje na ViewModel – nemanipuluje s task handlery.
 * Přechod na další obrazovku řídí BakaKeeperApp přes configLoadedProperty.
 */
public class WelcomeView extends VBox {

    private final Stage stage;
    private final MainViewModel viewModel;

    public WelcomeView(Stage stage, MainViewModel viewModel) {
        this.stage = stage;
        this.viewModel = viewModel;

        setAlignment(Pos.CENTER);
        setSpacing(20);
        setPadding(new Insets(40));
        getStyleClass().add("welcome-view");

        // logo
        Image logo = Icons.getIcon(128);
        if (logo != null) {
            ImageView logoView = new ImageView(logo);
            logoView.setFitWidth(128);
            logoView.setFitHeight(128);
            logoView.setPreserveRatio(true);
            getChildren().add(logoView);
        }

        // název a verze
        Label titleLabel = new Label(Version.getInstance().getName());
        titleLabel.getStyleClass().add("welcome-title");

        Label versionLabel = new Label("verze " + Version.getInstance().getVersion());
        versionLabel.getStyleClass().add("welcome-version");

        Label subtitleLabel = new Label(Version.getInstance().getOrganization());
        subtitleLabel.getStyleClass().add("welcome-subtitle");

        // tlačítka
        Button openButton = new Button("Otevřít konfiguraci\u2026");
        openButton.getStyleClass().add("welcome-button");
        openButton.setDefaultButton(true);
        openButton.setOnAction(e -> handleOpenConfig());

        Button createButton = new Button("Vytvořit novou konfiguraci\u2026");
        createButton.getStyleClass().add("welcome-button");
        createButton.setOnAction(e -> handleCreateConfig());

        getChildren().addAll(titleLabel, versionLabel, subtitleLabel, openButton, createButton);
    }

    private void handleOpenConfig() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Otevřít konfigurační soubor");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Šifrovaná konfigurace", "*.dat"),
                new FileChooser.ExtensionFilter("YAML konfigurace", "*.yml", "*.yaml"),
                new FileChooser.ExtensionFilter("Všechny soubory", "*.*")
        );

        File file = fileChooser.showOpenDialog(stage);
        if (file == null) return;

        if (file.getName().endsWith(".dat")) {
            Optional<String> passphrase = askPassphrase();
            if (passphrase.isEmpty() || passphrase.get().isBlank()) return;
            viewModel.loadEncryptedConfig(file, passphrase.get());
        } else {
            viewModel.loadPlainConfig(file);
        }
    }

    private void handleCreateConfig() {
        var wizard = new cz.zsstudanka.skola.bakakeeper.gui.view.dialog.ConfigWizardDialog(stage);
        Optional<File> result = wizard.showAndWait();
        if (result.isPresent()) {
            File file = result.get();
            if (file.getName().endsWith(".dat")) {
                Optional<String> passphrase = askPassphrase();
                if (passphrase.isPresent() && !passphrase.get().isBlank()) {
                    viewModel.loadEncryptedConfig(file, passphrase.get());
                }
            } else {
                viewModel.loadPlainConfig(file);
            }
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

        // fokus na heslo po zobrazení dialogu
        dialog.setOnShown(e -> passwordField.requestFocus());
        return dialog.showAndWait();
    }
}
