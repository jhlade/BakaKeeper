package cz.zsstudanka.skola.bakakeeper.service;

import cz.zsstudanka.skola.bakakeeper.constants.EBakaLDAPAttributes;
import cz.zsstudanka.skola.bakakeeper.model.StudentRecord;
import cz.zsstudanka.skola.bakakeeper.repository.LDAPUserRepository;
import cz.zsstudanka.skola.bakakeeper.utils.BakaUtils;

/**
 * Implementace párování SQL záznamů s existujícími LDAP účty.
 * Extrahováno ze Sync.attemptToPair().
 *
 * Skórovací logika: porovnává 4 kritéria (příjmení, jméno, ročník, třída).
 * Práh: >= 0.75 NEBO (>= 0.5 A ročník sedí).
 *
 * @author Jan Hladěna
 */
public class PairingServiceImpl implements PairingService {

    private final LDAPUserRepository ldapRepo;

    public PairingServiceImpl(LDAPUserRepository ldapRepo) {
        this.ldapRepo = ldapRepo;
    }

    @Override
    public SyncResult attemptToPair(StudentRecord sqlStudent, StudentRecord ldapStudent, boolean repair) {
        // LDAP účet už je spárovaný?
        if (ldapStudent.getInternalId() != null && !ldapStudent.getInternalId().isBlank()) {
            return SyncResult.skipped(sqlStudent.getInternalId(),
                    "LDAP účet již spárován s ID " + ldapStudent.getInternalId());
        }

        // bodové ohodnocení shody
        double score = 0.0;
        boolean yearMatch = false;

        // 1. příjmení
        if (sqlStudent.getSurname() != null
                && sqlStudent.getSurname().equalsIgnoreCase(ldapStudent.getSurname())) {
            score += 0.25;
        }

        // 2. jméno
        if (sqlStudent.getGivenName() != null
                && sqlStudent.getGivenName().equalsIgnoreCase(ldapStudent.getGivenName())) {
            score += 0.25;
        }

        // 3. ročník (z DN)
        Integer ldapYear = null;
        try { ldapYear = BakaUtils.classYearFromDn(ldapStudent.getDn()); } catch (NumberFormatException ignored) {}
        if (ldapYear != null && sqlStudent.getClassYear() == ldapYear) {
            score += 0.25;
            yearMatch = true;
        }

        // 4. třída (písmeno z DN)
        String ldapLetter = BakaUtils.classLetterFromDn(ldapStudent.getDn());
        if (ldapLetter != null && ldapLetter.equalsIgnoreCase(sqlStudent.getClassLetter())) {
            score += 0.25;
        }

        // vyhodnocení
        boolean pairSuccess = (score >= 0.75) || (score >= 0.5 && yearMatch);

        if (pairSuccess && repair) {
            // zapsat interní ID do LDAP
            ldapRepo.updateAttribute(ldapStudent.getDn(),
                    EBakaLDAPAttributes.EXT01, sqlStudent.getInternalId());

            return SyncResult.paired(sqlStudent.getInternalId(),
                    "Spárováno s " + ldapStudent.getDisplayName() + " (skóre " + score + ")");
        }

        if (pairSuccess) {
            return SyncResult.skipped(sqlStudent.getInternalId(),
                    "Párování by uspělo (skóre " + score + ") – suchý běh.");
        }

        return SyncResult.skipped(sqlStudent.getInternalId(),
                "Párování neúspěšné (skóre " + score + ").");
    }
}
