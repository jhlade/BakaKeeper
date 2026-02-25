package cz.zsstudanka.skola.bakakeeper.commands;

import cz.zsstudanka.skola.bakakeeper.App;
import cz.zsstudanka.skola.bakakeeper.components.ReportManager;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLogType;
import cz.zsstudanka.skola.bakakeeper.model.StudentRecord;
import cz.zsstudanka.skola.bakakeeper.service.ServiceFactory;
import cz.zsstudanka.skola.bakakeeper.service.SyncResult;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.util.concurrent.Callable;

/**
 * Příkaz pro reset hesla žáka.
 *
 * @author Jan Hladěna
 */
@Command(name = "reset", description = "Provede reset hesla uživatele na výchozí hodnotu.")
public class ResetPasswordCommand implements Callable<Integer> {

    @ParentCommand App app;

    @Parameters(index = "0", description = "Uživatelské jméno (login).")
    String login;

    @Override
    public Integer call() {
        app.applyGlobalFlags();

        ServiceFactory sf = app.createServiceFactory();
        ReportManager.logWait(EBakaLogType.LOG_STDOUT, "Probíhá pokus o reset hesla účtu " + login);

        // vyhledat žáka v LDAP
        String upn = login.contains("@") ? login : login + "@" + sf.getConfig().getMailDomain();
        StudentRecord student = sf.getLdapUserRepo().findByUPN(sf.getConfig().getLdapBaseStudents(), upn);

        if (student == null || student.getDn() == null) {
            ReportManager.logResult(EBakaLogType.LOG_ERR);
            ReportManager.log(EBakaLogType.LOG_ERR, "Účet " + upn + " nenalezen v AD.");
            return 1;
        }

        // získat classId z SQL evidence
        int classId = 0;
        StudentRecord sqlStudent = sf.getStudentRepo().findByInternalId(student.getInternalId());
        if (sqlStudent != null && sqlStudent.getClassNumber() != null) {
            try { classId = Integer.parseInt(sqlStudent.getClassNumber()); } catch (NumberFormatException ignored) {}
        }

        SyncResult result = sf.getPasswordService().resetStudentPassword(
                student.getDn(), student.getSurname(), student.getGivenName(),
                student.getClassYear(), classId);

        if (result.isSuccess()) {
            ReportManager.logResult(EBakaLogType.LOG_OK);
            return 0;
        } else {
            ReportManager.logResult(EBakaLogType.LOG_ERR);
            ReportManager.log(EBakaLogType.LOG_ERR, result.getDescription());
            return 1;
        }
    }
}
