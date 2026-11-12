package cz.zsstudanka.skola.bakakeeper.gui.viewmodel;

import cz.zsstudanka.skola.bakakeeper.gui.service.BackgroundTaskRunner;
import cz.zsstudanka.skola.bakakeeper.service.CheckResult;
import cz.zsstudanka.skola.bakakeeper.service.CheckService;
import javafx.beans.property.*;
import javafx.concurrent.Task;

/**
 * ViewModel pro dialog kontroly konektivity.
 * Drží stav jednotlivých kontrol (config, LDAP, SQL, SMTP).
 *
 * Kritické služby jsou konfigurace, AD a SQL – bez nich nelze pokračovat.
 * SMTP je nekritické: jeho selhání se projeví jen varováním
 * a aplikace poběží v režimu bez odesílání e-mailů.
 */
public class CheckViewModel {

    /** Stav jedné kontroly – spinner / ok / varování (nekritická služba nedostupná) / chyba. */
    public enum CheckState { PENDING, RUNNING, OK, WARNING, FAILED }

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
    private final BooleanProperty smtpOk = new SimpleBooleanProperty(false);

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
    /** Kritické služby (konfigurace, AD, SQL) jsou v pořádku – lze pokračovat. */
    public BooleanProperty allOkProperty() { return allOk; }
    /** SMTP je dostupné – lze odesílat e-maily. */
    public BooleanProperty smtpOkProperty() { return smtpOk; }

    /**
     * Spustí všechny kontroly paralelně.
     */
    public void runAllChecks(CheckService checkService) {
        runCheck(checkService, "config", configState, configMessage);
        runCheck(checkService, "ldap", ldapState, ldapMessage);
        runCheck(checkService, "sql", sqlState, sqlMessage);
        runCheck(checkService, "smtp", smtpState, smtpMessage, false);
    }

    private void runCheck(CheckService checkService, String type,
                          ObjectProperty<CheckState> state, StringProperty message) {
        runCheck(checkService, type, state, message, true);
    }

    /**
     * @param critical {@code false} = selhání se zobrazí jako varování a neblokuje pokračování
     */
    private void runCheck(CheckService checkService, String type,
                          ObjectProperty<CheckState> state, StringProperty message,
                          boolean critical) {
        CheckState failState = critical ? CheckState.FAILED : CheckState.WARNING;
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
            state.set(result.ok() ? CheckState.OK : failState);
            message.set(result.ok() || critical
                    ? result.message()
                    : nonCriticalMessage(result.message()));
            updateAllDone();
        });

        task.setOnFailed(e -> {
            state.set(failState);
            String msg = task.getException() != null
                    ? task.getException().getMessage()
                    : "Neznámá chyba";
            message.set(critical ? msg : nonCriticalMessage(msg));
            updateAllDone();
        });

        BackgroundTaskRunner.run(task);
    }

    private static String nonCriticalMessage(String detail) {
        String base = (detail == null || detail.isBlank()) ? "Služba není dostupná." : detail;
        return base + " Aplikace poběží bez odesílání e-mailů.";
    }

    private static boolean isFinished(CheckState s) {
        return s != CheckState.PENDING && s != CheckState.RUNNING;
    }

    private void updateAllDone() {
        boolean done = isFinished(configState.get())
                && isFinished(ldapState.get())
                && isFinished(sqlState.get())
                && isFinished(smtpState.get());

        // allOk MUSÍ být nastaveno DŘÍVE než allDone –
        // listener na allDone čte allOk, takže by jinak viděl starou hodnotu;
        // SMTP se do allOk nezapočítává (nekritická služba)
        if (done) {
            smtpOk.set(smtpState.get() == CheckState.OK);
            allOk.set(configState.get() == CheckState.OK
                    && ldapState.get() == CheckState.OK
                    && sqlState.get() == CheckState.OK);
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
        smtpOk.set(false);
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
        smtpOk.set(false);
    }
}
