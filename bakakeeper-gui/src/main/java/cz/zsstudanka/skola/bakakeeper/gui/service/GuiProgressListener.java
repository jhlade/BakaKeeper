package cz.zsstudanka.skola.bakakeeper.gui.service;

import cz.zsstudanka.skola.bakakeeper.service.SyncProgressListener;
import cz.zsstudanka.skola.bakakeeper.service.SyncResult;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * GUI implementace SyncProgressListener.
 * Přeposílá události z pozadového vlákna na FX Application Thread.
 */
public class GuiProgressListener implements SyncProgressListener {

    private final StringProperty currentPhase = new SimpleStringProperty("");
    private final StringProperty statusMessage = new SimpleStringProperty("");
    private final ObservableList<SyncResult> results = FXCollections.observableArrayList();
    private final IntegerProperty successCount = new SimpleIntegerProperty(0);
    private final IntegerProperty errorCount = new SimpleIntegerProperty(0);

    public StringProperty currentPhaseProperty() {
        return currentPhase;
    }

    public StringProperty statusMessageProperty() {
        return statusMessage;
    }

    public ObservableList<SyncResult> getResults() {
        return results;
    }

    public IntegerProperty successCountProperty() {
        return successCount;
    }

    public IntegerProperty errorCountProperty() {
        return errorCount;
    }

    @Override
    public void onProgress(String message) {
        Platform.runLater(() -> statusMessage.set(message));
    }

    @Override
    public void onResult(SyncResult result) {
        Platform.runLater(() -> results.add(result));
    }

    @Override
    public void onPhaseStart(String phaseName) {
        Platform.runLater(() -> {
            currentPhase.set(phaseName);
            statusMessage.set("Fáze: " + phaseName);
        });
    }

    @Override
    public void onPhaseEnd(String phaseName, int ok, int err) {
        Platform.runLater(() -> {
            successCount.set(successCount.get() + ok);
            errorCount.set(errorCount.get() + err);
        });
    }
}
