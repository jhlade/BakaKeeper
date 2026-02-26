package cz.zsstudanka.skola.bakakeeper.service;

import cz.zsstudanka.skola.bakakeeper.components.ReportManager;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLogType;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaSQL;
import cz.zsstudanka.skola.bakakeeper.model.InternalUserSnapshot;
import cz.zsstudanka.skola.bakakeeper.repository.InternalUserRepository;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Implementace auditní služby.
 * Porovnává aktuální stav hesla správce v SQL s šifrovanou zálohou.
 *
 * @author Jan Hladěna
 */
public class AuditServiceImpl implements AuditService {

    /** Login správce v tabulce dbo.webuser. */
    private static final String ADMIN_LOGIN = EBakaSQL.LIT_LOGIN_ADMIN.field();

    private final InternalUserRepository internalUserRepo;
    private final AuditHistoryStore historyStore;

    public AuditServiceImpl(InternalUserRepository internalUserRepo,
                            AuditHistoryStore historyStore) {
        this.internalUserRepo = internalUserRepo;
        this.historyStore = historyStore;
    }

    @Override
    public AuditReport runFullAudit() {
        // v tuto chvíli zahrnuje pouze admin password audit
        return auditAdminPassword();
    }

    @Override
    public AuditReport auditAdminPassword() {
        List<AuditResult> results = new ArrayList<>();

        Optional<InternalUserSnapshot> currentOpt = internalUserRepo.findByLogin(ADMIN_LOGIN);
        if (currentOpt.isEmpty()) {
            results.add(new AuditResult.UserNotFound(ADMIN_LOGIN));
            return new AuditReport(results);
        }

        InternalUserSnapshot current = currentOpt.get();
        Optional<InternalUserSnapshot> lastOpt = historyStore.getLastSnapshot(ADMIN_LOGIN);

        if (lastOpt.isEmpty()) {
            // první záloha – uložit a oznámit
            historyStore.backup(current);
            results.add(new AuditResult.AdminPasswordFirstBackup(ADMIN_LOGIN));
            ReportManager.log(EBakaLogType.LOG_ERR_VERBOSE,
                    "Audit: první záloha hesla správce vytvořena.");
        } else if (current.passwordDiffers(lastOpt.get())) {
            // heslo se změnilo!
            results.add(new AuditResult.AdminPasswordChanged(
                    ADMIN_LOGIN, lastOpt.get().modified(), current.modified()));
            ReportManager.log(EBakaLogType.LOG_ERR,
                    "AUDIT VAROVÁNÍ: heslo správce Bakalářů se změnilo! "
                            + "Poslední známá úprava: " + lastOpt.get().modified()
                            + ", aktuální: " + current.modified());
        } else {
            // heslo je shodné – aktualizovat zálohu (refresh timestamp)
            historyStore.backup(current);
            results.add(new AuditResult.AdminPasswordOk(ADMIN_LOGIN, new Date()));
        }

        return new AuditReport(results);
    }

    @Override
    public AuditReport revertAdminPassword() {
        List<AuditResult> results = new ArrayList<>();

        Optional<InternalUserSnapshot> lastOpt = historyStore.getLastSnapshot(ADMIN_LOGIN);
        if (lastOpt.isEmpty()) {
            ReportManager.log(EBakaLogType.LOG_ERR,
                    "Audit: žádná záloha hesla správce k obnovení.");
            results.add(new AuditResult.UserNotFound(ADMIN_LOGIN));
            return new AuditReport(results);
        }

        internalUserRepo.writeBack(lastOpt.get());
        results.add(new AuditResult.AdminPasswordReverted(ADMIN_LOGIN));
        ReportManager.log(EBakaLogType.LOG_STDOUT,
                "Audit: heslo správce obnoveno ze zálohy.");

        return new AuditReport(results);
    }

    @Override
    public void backupAdminPassword() {
        Optional<InternalUserSnapshot> currentOpt = internalUserRepo.findByLogin(ADMIN_LOGIN);
        currentOpt.ifPresent(historyStore::backup);
    }
}
