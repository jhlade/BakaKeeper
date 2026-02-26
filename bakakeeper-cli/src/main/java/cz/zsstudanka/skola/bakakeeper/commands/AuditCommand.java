package cz.zsstudanka.skola.bakakeeper.commands;

import cz.zsstudanka.skola.bakakeeper.App;
import cz.zsstudanka.skola.bakakeeper.components.ReportManager;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLogType;
import cz.zsstudanka.skola.bakakeeper.service.AuditReport;
import cz.zsstudanka.skola.bakakeeper.service.AuditResult;
import cz.zsstudanka.skola.bakakeeper.service.ServiceFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.util.concurrent.Callable;

/**
 * Příkaz pro audit interních dat – kontrola hesla správce Bakalářů.
 *
 * @author Jan Hladěna
 */
@Command(name = "audit", description = "Provede audit interních dat (kontrola hesla správce).")
public class AuditCommand implements Callable<Integer> {

    @ParentCommand App app;

    @Option(names = "--admin-only",
            description = "Provede pouze kontrolu hesla správce.")
    boolean adminOnly;

    @Option(names = "--revert-admin",
            description = "Obnoví heslo správce z poslední zálohy.")
    boolean revertAdmin;

    @Override
    public Integer call() {
        app.applyGlobalFlags();

        ServiceFactory sf = app.createServiceFactory();
        AuditReport report;

        if (revertAdmin) {
            report = sf.getAuditService().revertAdminPassword();
        } else if (adminOnly) {
            report = sf.getAuditService().auditAdminPassword();
        } else {
            report = sf.getAuditService().runFullAudit();
        }

        // výpis výsledků
        for (AuditResult result : report.results()) {
            switch (result) {
                case AuditResult.AdminPasswordChanged r ->
                        ReportManager.log(EBakaLogType.LOG_ERR,
                                "VAROVÁNÍ: heslo správce se změnilo! "
                                        + "Poslední záloha: " + r.lastKnown()
                                        + ", aktuální: " + r.current());
                case AuditResult.AdminPasswordOk r ->
                        ReportManager.log(EBakaLogType.LOG_STDOUT,
                                "Heslo správce je shodné se zálohou (kontrola: " + r.lastChecked() + ").");
                case AuditResult.AdminPasswordFirstBackup r ->
                        ReportManager.log(EBakaLogType.LOG_STDOUT,
                                "Vytvořena první záloha hesla správce '" + r.login() + "'.");
                case AuditResult.AdminPasswordReverted r ->
                        ReportManager.log(EBakaLogType.LOG_STDOUT,
                                "Heslo správce '" + r.login() + "' obnoveno ze zálohy.");
                case AuditResult.UserNotFound r ->
                        ReportManager.log(EBakaLogType.LOG_ERR,
                                "Uživatel '" + r.login() + "' nebyl nalezen v databázi.");
            }
        }

        return report.isClean() ? 0 : 1;
    }
}
