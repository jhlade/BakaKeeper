package cz.zsstudanka.skola.bakakeeper.commands;

import cz.zsstudanka.skola.bakakeeper.App;
import cz.zsstudanka.skola.bakakeeper.components.ReportManager;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLogType;
import cz.zsstudanka.skola.bakakeeper.model.collections.BakaInternalUserHistory;
import cz.zsstudanka.skola.bakakeeper.model.entities.BakaInternalUser;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Příkaz pro správu interních uživatelů (záloha, obnovení, výpis).
 *
 * @author Jan Hladěna
 */
@Command(name = "internaldb", description = "Správa interních uživatelů (list, backup, restore).")
public class InternalDbCommand implements Callable<Integer> {

    @ParentCommand App app;

    @Parameters(index = "0", description = "Akce: list, backup, restore.")
    String action;

    @Parameters(index = "1", description = "Uživatelský login.")
    String login;

    @Parameters(index = "2", arity = "0..1", description = "Index zálohy (jen pro restore).")
    Integer index;

    @Override
    public Integer call() {
        app.applyGlobalFlags();
        app.loadSettings();

        switch (action) {
            case "list" -> {
                Map<Date, BakaInternalUser> data = BakaInternalUserHistory.getInstance().list(login);
                if (data != null) {
                    BakaInternalUser current = new BakaInternalUser(login);
                    int i = 0;
                    for (Map.Entry<Date, BakaInternalUser> entry : data.entrySet()) {
                        String mark = (entry.getValue().compareTo(current) == 0) ? "*" : "";
                        ReportManager.log("[ " + i + " ]" + mark + "\t[" + entry.getKey() + "] : " + entry.getValue().getLogin());
                        i++;
                    }
                } else {
                    ReportManager.log("Pro zadaného uživatele neexistují žádné zálohy.");
                }
            }
            case "backup" -> BakaInternalUserHistory.getInstance().backup(login);
            case "restore" -> {
                if (index == null) {
                    ReportManager.log(EBakaLogType.LOG_ERR, "Pro restore je nutné zadat index zálohy.");
                    return 1;
                }
                BakaInternalUserHistory.getInstance().restore(login, index);
            }
            default -> {
                ReportManager.log(EBakaLogType.LOG_ERR, "Neznámá akce: " + action + ". Povolené: list, backup, restore.");
                return 1;
            }
        }

        return 0;
    }
}
