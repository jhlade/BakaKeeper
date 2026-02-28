package cz.zsstudanka.skola.bakakeeper.gui.viewmodel;

import cz.zsstudanka.skola.bakakeeper.gui.service.BackgroundTaskRunner;
import cz.zsstudanka.skola.bakakeeper.service.CheckResult;
import cz.zsstudanka.skola.bakakeeper.service.CheckService;
import javafx.beans.property.*;
import javafx.concurrent.Task;

/**
 * ViewModel pro dialog kontroly konektivity.
 * Drží stav jednotlivých kontrol (config, LDAP, SQL, SMTP).
 */
public class CheckViewModel {

    /** Stav jedné kontroly – spinner / ok / chyba. */
    public enum CheckState { PENDING, RUNNING, OK, FAILED }

    private final ObjectProperty<CheckState> configState = new SimpleObjectProperty<>(CheckState.PENDING);
    private final ObjectProperty<CheckState> ldapState = new SimpleObjectProperty<>(CheckState.PENDING);
    private final ObjectProperty<CheckState> sqlState = new SimpleObjectProperty<>(CheckState.PENDING);
    private final ObjectProperty<CheckState> smtpState = new SimpleObjectProperty<>(CheckState.PENDING);

    private final StringProperty configMessage = new SimpleStringProperty("");
    private final StringProperty ldapMessage = new SimpleStringProperty("");
    private final StringProperty sqlMessage = new SimpleStringProperty("");
    private final StringProperty smtpMessage = new SimpleStringProperty("");

    private final BooleanProperty allDone = new SimpleBooleanProperty(false);
    private final BooleanProperty allOk = new SimpleBooleanProperty(false);

    // -- vlastnosti pro binding --

    public ObjectProperty<CheckState> configStateProperty() { return configState; }
    public ObjectProperty<CheckState> ldapStateProperty() { return ldapState; }
    public ObjectProperty<CheckState> sqlStateProperty() { return sqlState; }
    public ObjectProperty<CheckState> smtpStateProperty() { return smtpState; }

    public StringProperty configMessageProperty() { return configMessage; }
    public StringProperty ldapMessageProperty() { return ldapMessage; }
    public StringProperty sqlMessageProperty() { return sqlMessage; }
    public StringProperty smtpMessageProperty() { return smtpMessage; }

    public BooleanProperty allDoneProperty() { return allDone; }
    public BooleanProperty allOkProperty() { return allOk; }

    /**
     * Spustí všechny kontroly paralelně.
     */
    public void runAllChecks(CheckService checkService) {
        runCheck(checkService, "config", configState, configMessage);
        runCheck(checkService, "ldap", ldapState, ldapMessage);
        runCheck(checkService, "sql", sqlState, sqlMessage);
        runCheck(checkService, "smtp", smtpState, smtpMessage);
    }

    private void runCheck(CheckService checkService, String type,
                          ObjectProperty<CheckState> state, StringProperty message) {
        state.set(CheckState.RUNNING);
        message.set("");

        Task<CheckResult> task = new Task<>() {
            @Override
            protected CheckResult call() {
                return switch (type) {
                    case "config" -> checkService.checkConfig();
                    case "ldap" -> checkService.checkLdap();
                    case "sql" -> checkService.checkSql();
                    case "smtp" -> checkService.checkSmtp();
                    default -> CheckResult.failure(type, "Neznámý typ kontroly");
                };
            }
        };

        task.setOnSucceeded(e -> {
            CheckResult result = task.getValue();
            state.set(result.ok() ? CheckState.OK : CheckState.FAILED);
            message.set(result.message());
            updateAllDone();
        });

        task.setOnFailed(e -> {
            state.set(CheckState.FAILED);
            message.set(task.getException() != null
                    ? task.getException().getMessage()
                    : "Neznámá chyba");
            updateAllDone();
        });

        BackgroundTaskRunner.run(task);
    }

    private void updateAllDone() {
        boolean done = configState.get() != CheckState.PENDING && configState.get() != CheckState.RUNNING
                && ldapState.get() != CheckState.PENDING && ldapState.get() != CheckState.RUNNING
                && sqlState.get() != CheckState.PENDING && sqlState.get() != CheckState.RUNNING
                && smtpState.get() != CheckState.PENDING && smtpState.get() != CheckState.RUNNING;

        // allOk MUSÍ být nastaveno DŘÍVE než allDone –
        // listener na allDone čte allOk, takže by jinak viděl starou hodnotu
        if (done) {
            allOk.set(configState.get() == CheckState.OK
                    && ldapState.get() == CheckState.OK
                    && sqlState.get() == CheckState.OK
                    && smtpState.get() == CheckState.OK);
        }

        allDone.set(done);
    }

    /**
     * Resetuje stav všech kontrol do PENDING.
     */
    public void reset() {
        configState.set(CheckState.PENDING);
        ldapState.set(CheckState.PENDING);
        sqlState.set(CheckState.PENDING);
        smtpState.set(CheckState.PENDING);
        configMessage.set("");
        ldapMessage.set("");
        sqlMessage.set("");
        smtpMessage.set("");
        allDone.set(false);
        allOk.set(false);
    }

    /**
     * Nastaví všechny kontroly do stavu RUNNING (spinnery viditelné).
     * Volá se hned po zobrazení dialogu, ještě před zahájením inicializace.
     */
    public void setAllRunning() {
        configState.set(CheckState.RUNNING);
        ldapState.set(CheckState.RUNNING);
        sqlState.set(CheckState.RUNNING);
        smtpState.set(CheckState.RUNNING);
        allDone.set(false);
        allOk.set(false);
    }
}
