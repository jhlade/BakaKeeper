package cz.zsstudanka.skola.bakakeeper.gui.view;

import cz.zsstudanka.skola.bakakeeper.gui.service.BackgroundTaskRunner;
import cz.zsstudanka.skola.bakakeeper.gui.viewmodel.CheckViewModel;
import cz.zsstudanka.skola.bakakeeper.gui.viewmodel.CheckViewModel.CheckState;
import cz.zsstudanka.skola.bakakeeper.service.CheckService;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.StringProperty;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Window;

import java.util.concurrent.Callable;

/**
 * Dialog kontroly konektivity – zobrazuje stav připojení
 * ke konfiguraci, LDAP, SQL a SMTP se spinnery a ikonami OK/FAIL.
 *
 * Přijímá {@link Callable} pro vytvoření CheckService, aby se inicializace
 * (připojení k LDAP/SQL) provedla na pozadí a nezablokovala FX vlákno.
 * Dialog se zobrazí okamžitě a při otevření rozmlží pozadí.
 */
public class CheckDialog extends Dialog<Boolean> {

    private final CheckViewModel viewModel = new CheckViewModel();
    private final Callable<CheckService> checkServiceFactory;
    private final ButtonType proceedType;
    private final ButtonType retryType;

    /**
     * @param owner               rodičovské okno
     * @param checkServiceFactory factory pro vytvoření CheckService (běží na pozadí)
     */
    public CheckDialog(Window owner, Callable<CheckService> checkServiceFactory) {
        this.checkServiceFactory = checkServiceFactory;

        setTitle("Kontrola konektivity");
        setHeaderText("Inicializuji připojení\u2026");
        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        setResizable(false);

        // tlačítka
        proceedType = new ButtonType("Pokračovat", ButtonBar.ButtonData.OK_DONE);
        retryType = new ButtonType("Zkusit znovu", ButtonBar.ButtonData.LEFT);
        getDialogPane().getButtonTypes().addAll(proceedType, retryType, ButtonType.CANCEL);
        getDialogPane().lookupButton(proceedType).setDisable(true);
        getDialogPane().lookupButton(retryType).setDisable(true);

        // obsah – mřížka kontrol
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        addCheckRow(grid, 0, "Konfigurace", viewModel.configStateProperty(), viewModel.configMessageProperty());
        addCheckRow(grid, 1, "Active Directory", viewModel.ldapStateProperty(), viewModel.ldapMessageProperty());
        addCheckRow(grid, 2, "SQL Server", viewModel.sqlStateProperty(), viewModel.sqlMessageProperty());
        addCheckRow(grid, 3, "SMTP", viewModel.smtpStateProperty(), viewModel.smtpMessageProperty());

        getDialogPane().setContent(grid);
        getDialogPane().setPrefWidth(480);

        // binding pro tlačítka – aktivovat po dokončení všech kontrol
        viewModel.allDoneProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                getDialogPane().lookupButton(proceedType).setDisable(!viewModel.allOkProperty().get());
                getDialogPane().lookupButton(retryType).setDisable(false);
            }
        });

        // retry – znovu inicializace + kontroly
        getDialogPane().lookupButton(retryType).addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
            e.consume();
            getDialogPane().lookupButton(proceedType).setDisable(true);
            getDialogPane().lookupButton(retryType).setDisable(true);
            viewModel.reset();
            runInitAndChecks();
        });

        // výsledek dialogu
        setResultConverter(bt -> bt == proceedType);

        // po zobrazení: rozmlžit pozadí + spustit inicializaci na pozadí
        setOnShown(e -> {
            applyOwnerBlur(owner, true);
            runInitAndChecks();
        });

        setOnHidden(e -> applyOwnerBlur(owner, false));
    }

    /**
     * Na pozadí vytvoří CheckService (připojení k LDAP/SQL),
     * pak spustí 4 paralelní kontroly.
     */
    private void runInitAndChecks() {
        setHeaderText("Inicializuji připojení\u2026");
        // spinnery viditelné okamžitě
        viewModel.setAllRunning();

        Task<CheckService> initTask = new Task<>() {
            @Override
            protected CheckService call() throws Exception {
                return checkServiceFactory.call();
            }
        };

        initTask.setOnSucceeded(e -> {
            CheckService cs = initTask.getValue();
            if (cs != null) {
                setHeaderText("Ověřuji dostupnost služeb\u2026");
                viewModel.runAllChecks(cs);
            } else {
                setHeaderText("Chyba při inicializaci");
                getDialogPane().lookupButton(retryType).setDisable(false);
            }
        });

        initTask.setOnFailed(e -> {
            String msg = initTask.getException() != null
                    ? initTask.getException().getMessage() : "Neznámá chyba";
            setHeaderText("Inicializace selhala: " + msg);
            getDialogPane().lookupButton(retryType).setDisable(false);
        });

        BackgroundTaskRunner.run(initTask);
    }

    private void applyOwnerBlur(Window owner, boolean blur) {
        if (owner != null && owner.getScene() != null) {
            Node root = owner.getScene().getRoot();
            root.setEffect(blur ? new GaussianBlur(8) : null);
        }
    }

    private void addCheckRow(GridPane grid, int row,
                             String label,
                             ObjectProperty<CheckState> stateProperty,
                             StringProperty messageProperty) {
        Label nameLabel = new Label(label);
        nameLabel.setMinWidth(120);
        nameLabel.getStyleClass().add("check-service-name");

        HBox stateBox = new HBox(6);
        stateBox.setAlignment(Pos.CENTER_LEFT);

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(18, 18);
        spinner.setMaxSize(18, 18);
        spinner.setVisible(false);

        Label stateLabel = new Label();
        stateLabel.getStyleClass().add("check-state-label");

        stateBox.getChildren().addAll(spinner, stateLabel);

        Label msgLabel = new Label();
        msgLabel.getStyleClass().add("check-message");
        msgLabel.setWrapText(true);
        msgLabel.setMaxWidth(250);
        msgLabel.textProperty().bind(messageProperty);

        stateProperty.addListener((obs, oldVal, newVal) -> {
            switch (newVal) {
                case PENDING -> {
                    spinner.setVisible(false);
                    stateLabel.setText("");
                    stateLabel.getStyleClass().removeAll("check-ok", "check-fail");
                }
                case RUNNING -> {
                    spinner.setVisible(true);
                    spinner.setProgress(-1);
                    stateLabel.setText("");
                    stateLabel.getStyleClass().removeAll("check-ok", "check-fail");
                }
                case OK -> {
                    spinner.setVisible(false);
                    stateLabel.setText("\u2714");
                    stateLabel.getStyleClass().add("check-ok");
                }
                case FAILED -> {
                    spinner.setVisible(false);
                    stateLabel.setText("\u2718");
                    stateLabel.getStyleClass().add("check-fail");
                }
            }
        });

        grid.add(nameLabel, 0, row);
        grid.add(stateBox, 1, row);
        grid.add(msgLabel, 2, row);
    }
}
