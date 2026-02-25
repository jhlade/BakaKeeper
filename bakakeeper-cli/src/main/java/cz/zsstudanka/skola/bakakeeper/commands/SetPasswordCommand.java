package cz.zsstudanka.skola.bakakeeper.commands;

import cz.zsstudanka.skola.bakakeeper.App;
import cz.zsstudanka.skola.bakakeeper.components.ReportManager;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLogType;
import cz.zsstudanka.skola.bakakeeper.model.StudentRecord;
import cz.zsstudanka.skola.bakakeeper.service.ServiceFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.util.concurrent.Callable;

/**
 * Příkaz pro okamžité nastavení hesla účtu.
 *
 * @author Jan Hladěna
 */
@Command(name = "set", description = "Provede okamžité nastavení hesla uživatele.")
public class SetPasswordCommand implements Callable<Integer> {

    @ParentCommand App app;

    @Parameters(index = "0", description = "Uživatelské jméno (login).")
    String login;

    @Parameters(index = "1", description = "Nové heslo.")
    String password;

    @Override
    public Integer call() {
        app.applyGlobalFlags();

        ServiceFactory sf = app.createServiceFactory();
        ReportManager.logWait(EBakaLogType.LOG_STDOUT, "Probíhá pokus o okamžité nastavení hesla účtu " + login);

        String upn = login.contains("@") ? login : login + "@" + sf.getConfig().getMailDomain();
        StudentRecord student = sf.getLdapUserRepo().findByUPN(sf.getConfig().getLdapBaseStudents(), upn);

        if (student == null || student.getDn() == null) {
            ReportManager.logResult(EBakaLogType.LOG_ERR);
            ReportManager.log(EBakaLogType.LOG_ERR, "Účet " + upn + " nenalezen v AD.");
            return 1;
        }

        if (sf.getPasswordService().setPassword(student.getDn(), password, false)) {
            ReportManager.logResult(EBakaLogType.LOG_OK);
            return 0;
        } else {
            ReportManager.logResult(EBakaLogType.LOG_ERR);
            return 1;
        }
    }
}
