package cz.zsstudanka.skola.bakakeeper.service;

import cz.zsstudanka.skola.bakakeeper.constants.EBakaLDAPAttributes;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaUAC;
import cz.zsstudanka.skola.bakakeeper.repository.LDAPUserRepository;

/**
 * Implementace správy stavu účtů.
 * Pouze nastavuje/odebírá příznak ACCOUNTDISABLE – nepřesouvá OU, neodebírá skupiny.
 *
 * @author Jan Hladěna
 */
public class AccountServiceImpl implements AccountService {

    private final LDAPUserRepository ldapRepo;

    public AccountServiceImpl(LDAPUserRepository ldapRepo) {
        this.ldapRepo = ldapRepo;
    }

    @Override
    public SyncResult suspendAccount(String dn, int currentUac) {
        if (EBakaUAC.ACCOUNTDISABLE.checkFlag(currentUac)) {
            return SyncResult.noChange(dn, "Účet je již zakázán.");
        }

        int newUac = EBakaUAC.ACCOUNTDISABLE.setFlag(currentUac);
        boolean success = ldapRepo.updateAttribute(dn, EBakaLDAPAttributes.UAC, Integer.toString(newUac));

        if (success) {
            return SyncResult.updated(dn, "Účet zakázán.");
        }
        return SyncResult.error(dn, "Nepodařilo se zakázat účet.");
    }

    @Override
    public SyncResult unsuspendAccount(String dn, int currentUac) {
        if (!EBakaUAC.ACCOUNTDISABLE.checkFlag(currentUac)) {
            return SyncResult.noChange(dn, "Účet je již povolen.");
        }

        int newUac = EBakaUAC.ACCOUNTDISABLE.clearFlag(currentUac);
        boolean success = ldapRepo.updateAttribute(dn, EBakaLDAPAttributes.UAC, Integer.toString(newUac));

        if (success) {
            return SyncResult.updated(dn, "Účet povolen.");
        }
        return SyncResult.error(dn, "Nepodařilo se povolit účet.");
    }
}
