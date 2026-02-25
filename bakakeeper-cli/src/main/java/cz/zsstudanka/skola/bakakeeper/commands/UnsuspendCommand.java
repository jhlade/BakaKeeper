package cz.zsstudanka.skola.bakakeeper.commands;

import cz.zsstudanka.skola.bakakeeper.App;
import cz.zsstudanka.skola.bakakeeper.components.ReportManager;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLogType;
import cz.zsstudanka.skola.bakakeeper.model.StudentRecord;
import cz.zsstudanka.skola.bakakeeper.service.*;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.util.concurrent.Callable;

/**
 * Příkaz pro povolení (unsuspend) uživatelských účtů.
 * Odebere příznak ACCOUNTDISABLE z UAC.
 *
 * @author Jan Hladěna
 */
@Command(name = "unsuspend", description = "Povolí zakázané účty uživatelů (odebere ACCOUNTDISABLE).")
public class UnsuspendCommand implements Callable<Integer> {

    @ParentCommand App app;

    @Parameters(index = "0", description = "Rozsah výběru (*, ročník, třída, UPN – oddělené čárkou).")
    String scope;

    @Override
    public Integer call() {
        app.applyGlobalFlags();

        ServiceFactory sf = app.createServiceFactory();

        // parsování rozsahu
        RangeSelector selector = RangeSelector.parse(scope);
        ResolvedSelection selection = selector.resolve(sf.getStudentRepo(), sf.getFacultyRepo(),
                sf.getConfig().getMailDomain());

        // varování pro nenalezené žáky
        for (String nf : selection.notFound()) {
            ReportManager.log(EBakaLogType.LOG_ERR, "Žák s UPN " + nf + " nebyl nalezen v evidenci.");
        }

        int successCount = 0;
        int failureCount = 0;

        // iterace přes všechny vybrané žáky
        for (var entry : selection.studentsByClass().entrySet()) {
            for (StudentRecord sqlStudent : entry.getValue()) {
                String email = sqlStudent.getEmail();
                if (email == null || email.isEmpty()) {
                    continue;
                }

                ReportManager.logWait(EBakaLogType.LOG_INFO,
                        "Povolení účtu " + sqlStudent.getSurname() + " " + sqlStudent.getGivenName());

                // vyhledat LDAP záznam (DN + UAC)
                StudentRecord ldapStudent = sf.getLdapUserRepo().findByUPN(
                        sf.getConfig().getLdapBaseStudents(), email);

                if (ldapStudent == null || ldapStudent.getDn() == null) {
                    ReportManager.logResult(EBakaLogType.LOG_ERR);
                    ReportManager.log(EBakaLogType.LOG_ERR,
                            "Účet " + email + " nenalezen v AD.");
                    failureCount++;
                    continue;
                }

                SyncResult result = sf.getAccountService().unsuspendAccount(
                        ldapStudent.getDn(), ldapStudent.getUac());

                if (result.isSuccess()) {
                    ReportManager.logResult(EBakaLogType.LOG_OK);
                    ReportManager.log(EBakaLogType.LOG_VERBOSE, result.getDescription());
                } else {
                    ReportManager.logResult(EBakaLogType.LOG_ERR);
                    ReportManager.log(EBakaLogType.LOG_ERR, result.getDescription());
                }

                if (result.getType() == SyncResult.Type.UPDATED) {
                    successCount++;
                } else if (result.getType() == SyncResult.Type.ERROR) {
                    failureCount++;
                }
            }
        }

        ReportManager.log(EBakaLogType.LOG_STDOUT,
                "Unsuspend dokončen: " + successCount + " povolených, "
                        + failureCount + " chyb.");

        return failureCount > 0 ? 1 : 0;
    }
}
