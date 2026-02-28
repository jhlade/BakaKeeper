package cz.zsstudanka.skola.bakakeeper.gui.view.component;

import javafx.beans.property.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/**
 * Stavový řádek aplikace – zobrazuje ikonu zámku, cestu ke konfiguračnímu souboru,
 * aktuální stav a progress bar.
 */
public class StatusBar extends HBox {

    private final Label lockIcon = new Label("\uD83D\uDD12"); // zamknutý zámek
    private final Label filePathLabel = new Label();
    private final Label statusLabel = new Label();
    private final ProgressBar progressBar = new ProgressBar();

    private final BooleanProperty locked = new SimpleBooleanProperty(true);

    public StatusBar() {
        getStyleClass().add("status-bar");
        setAlignment(Pos.CENTER_LEFT);
        setPadding(new Insets(4, 8, 4, 8));
        setSpacing(8);

        // ikona zámku
        lockIcon.getStyleClass().add("status-lock");
        locked.addListener((obs, oldVal, newVal) ->
                lockIcon.setText(newVal ? "\uD83D\uDD12" : "\uD83D\uDD13"));

        // cesta k souboru
        filePathLabel.getStyleClass().add("status-file-path");
        filePathLabel.setMaxWidth(300);

        // oddělovač
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // stav
        statusLabel.getStyleClass().add("status-text");

        // oddělovač
        Region spacer2 = new Region();
        HBox.setHgrow(spacer2, Priority.ALWAYS);

        // progress bar
        progressBar.setPrefWidth(150);
        progressBar.setVisible(false);
        progressBar.setManaged(false);

        getChildren().addAll(lockIcon, filePathLabel, spacer, statusLabel, spacer2, progressBar);
    }

    // -- Binding vlastnosti --

    public BooleanProperty lockedProperty() {
        return locked;
    }

    public StringProperty filePathProperty() {
        return filePathLabel.textProperty();
    }

    public StringProperty statusTextProperty() {
        return statusLabel.textProperty();
    }

    public DoubleProperty progressProperty() {
        return progressBar.progressProperty();
    }

    /**
     * Zobrazí nebo skryje progress bar.
     */
    public void setProgressVisible(boolean visible) {
        progressBar.setVisible(visible);
        progressBar.setManaged(visible);
    }
}
