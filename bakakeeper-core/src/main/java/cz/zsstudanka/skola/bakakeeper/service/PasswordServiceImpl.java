package cz.zsstudanka.skola.bakakeeper.service;

import cz.zsstudanka.skola.bakakeeper.constants.EBakaLDAPAttributes;
import cz.zsstudanka.skola.bakakeeper.repository.LDAPUserRepository;
import cz.zsstudanka.skola.bakakeeper.utils.BakaUtils;

/**
 * Implementace správy hesel.
 * Extrahováno z Student.resetPassword() a Manipulation.setPassword().
 *
 * @author Jan Hladěna
 */
public class PasswordServiceImpl implements PasswordService {

    private static final int MAX_ATTEMPTS = 25;

    private final LDAPUserRepository ldapRepo;

    public PasswordServiceImpl(LDAPUserRepository ldapRepo) {
        this.ldapRepo = ldapRepo;
    }

    @Override
    public PasswordResetResult resetStudentPasswordWithResult(String dn, String surname, String givenName,
                                                               Integer classYear, Integer classId) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String password = BakaUtils.nextPassword(surname, givenName, classYear, classId, attempt);

            if (setPassword(dn, password, false)) {
                return new PasswordResetResult(
                        SyncResult.updated(dn, "Heslo resetováno."),
                        password);
            }
        }

        return new PasswordResetResult(
                SyncResult.error(dn, "Nepodařilo se nastavit heslo po " + MAX_ATTEMPTS + " pokusech."),
                null);
    }

    @Override
    public SyncResult resetStudentPassword(String dn, String surname, String givenName,
                                            Integer classYear, Integer classId) {
        return resetStudentPasswordWithResult(dn, surname, givenName, classYear, classId).result();
    }

    @Override
    public boolean setPassword(String dn, String password, boolean mustChange) {
        boolean pwdSet = ldapRepo.updateAttribute(dn, EBakaLDAPAttributes.PW_UNICODE, password);
        if (!pwdSet) return false;

        // pwdLastSet = 0 → vyžadovat změnu, -1 → neplatí
        String lastSet = mustChange ? "0" : "-1";
        ldapRepo.updateAttribute(dn, EBakaLDAPAttributes.PW_LASTSET, lastSet);

        return true;
    }
}
