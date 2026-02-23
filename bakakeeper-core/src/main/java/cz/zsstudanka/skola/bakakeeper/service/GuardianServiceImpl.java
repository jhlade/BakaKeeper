package cz.zsstudanka.skola.bakakeeper.service;

import cz.zsstudanka.skola.bakakeeper.config.AppConfig;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLDAPAttributes;
import cz.zsstudanka.skola.bakakeeper.model.GuardianRecord;
import cz.zsstudanka.skola.bakakeeper.model.StudentRecord;
import cz.zsstudanka.skola.bakakeeper.model.entities.DataLDAP;
import cz.zsstudanka.skola.bakakeeper.repository.GuardianRepository;
import cz.zsstudanka.skola.bakakeeper.repository.LDAPUserRepository;
import cz.zsstudanka.skola.bakakeeper.utils.BakaUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementace služby pro správu kontaktů zákonných zástupců.
 * Extrahováno z Guardian.java a Sync.syncGuardian().
 *
 * @author Jan Hladěna
 */
public class GuardianServiceImpl implements GuardianService {

    private static final int MAX_DN_ATTEMPTS = 10;

    private final AppConfig config;
    private final GuardianRepository guardianRepo;
    private final LDAPUserRepository ldapRepo;

    public GuardianServiceImpl(AppConfig config,
                                GuardianRepository guardianRepo,
                                LDAPUserRepository ldapRepo) {
        this.config = config;
        this.guardianRepo = guardianRepo;
        this.ldapRepo = ldapRepo;
    }

    @Override
    public List<SyncResult> syncGuardians(List<StudentRecord> sqlStudents,
                                           List<GuardianRecord> existingContacts,
                                           boolean repair,
                                           SyncProgressListener listener) {
        listener.onPhaseStart("Synchronizace zákonných zástupců");
        List<SyncResult> results = new ArrayList<>();

        // index kontaktů podle interního ID
        Map<String, GuardianRecord> contactById = existingContacts.stream()
                .filter(g -> g.getInternalId() != null)
                .collect(Collectors.toMap(GuardianRecord::getInternalId, g -> g, (a, b) -> a));
        Set<String> processedIds = new HashSet<>();

        // mapování tříd → DL DN pro distribuční skupiny
        Map<String, List<String>> classDlMembers = new LinkedHashMap<>();

        // Fáze 1: Vytvořit/aktualizovat kontakty dle evidence
        for (StudentRecord sql : sqlStudents) {
            String guardianId = sql.getGuardianInternalId();
            if (guardianId == null || guardianId.isBlank()) continue;
            if (processedIds.contains(guardianId)) continue;
            processedIds.add(guardianId);

            GuardianRecord existing = contactById.get(guardianId);

            if (existing != null) {
                // kontakt existuje → synchronizovat data
                SyncResult result = syncSingleGuardian(sql, existing, repair, listener);
                results.add(result);
            } else if (repair) {
                // kontakt neexistuje → vytvořit
                SyncResult result = createGuardianContact(sql);
                results.add(result);
            } else {
                listener.onProgress("Nový zástupce: " + guardianId);
                results.add(SyncResult.skipped(guardianId, "Suchý běh – kontakt by byl vytvořen."));
            }

            // zapamatovat DL členství pro třídu
            if (sql.getClassName() != null) {
                String dlDn = "CN=Rodice-Trida-" + sql.getClassName().replace(".", "")
                        + "," + config.getLdapBaseDistributionLists();
                GuardianRecord contact = (existing != null) ? existing : contactById.get(guardianId);
                if (contact != null && contact.getDn() != null) {
                    classDlMembers.computeIfAbsent(dlDn, k -> new ArrayList<>())
                            .add(contact.getDn());
                }
            }
        }

        // Fáze 2: Smazat osiřelé kontakty
        for (GuardianRecord contact : existingContacts) {
            if (contact.getInternalId() == null) continue;
            if (processedIds.contains(contact.getInternalId())) continue;

            if (repair) {
                guardianRepo.deleteContact(contact.getDn());
                results.add(SyncResult.retired(contact.getInternalId(),
                        "Kontakt " + contact.getDisplayName() + " smazán (osiřelý)."));
            } else {
                results.add(SyncResult.skipped(contact.getInternalId(),
                        "Osiřelý kontakt: " + contact.getDisplayName()));
            }
        }

        // Fáze 3: Aktualizovat distribuční skupiny
        if (repair) {
            for (Map.Entry<String, List<String>> entry : classDlMembers.entrySet()) {
                updateDistributionList(entry.getKey(), entry.getValue());
            }
        }

        int ok = (int) results.stream().filter(SyncResult::isSuccess).count();
        int err = (int) results.stream().filter(r -> !r.isSuccess()).count();
        listener.onPhaseEnd("Synchronizace zákonných zástupců", ok, err);
        return results;
    }

    // ===========================
    // Interní
    // ===========================

    /**
     * Synchronizuje data jednoho kontaktu (příjmení, jméno, email, telefon).
     * SQL evidence je autoritativní zdroj – při neshodě se LDAP data opraví.
     */
    private SyncResult syncSingleGuardian(StudentRecord sql, GuardianRecord existing,
                                           boolean repair, SyncProgressListener listener) {
        String dn = existing.getDn();
        if (dn == null) {
            return SyncResult.noChange(existing.getInternalId());
        }

        List<String> changes = new ArrayList<>();

        // příjmení
        String expectedSurname = sql.getGuardianSurname();
        if (expectedSurname != null && !expectedSurname.equals(existing.getSurname())) {
            changes.add("sn: " + existing.getSurname() + " → " + expectedSurname);
            if (repair) {
                ldapRepo.updateAttribute(dn, EBakaLDAPAttributes.NAME_LAST, expectedSurname);
            }
        }

        // jméno
        String expectedGivenName = sql.getGuardianGivenName();
        if (expectedGivenName != null && !expectedGivenName.equals(existing.getGivenName())) {
            changes.add("givenName: " + existing.getGivenName() + " → " + expectedGivenName);
            if (repair) {
                ldapRepo.updateAttribute(dn, EBakaLDAPAttributes.NAME_FIRST, expectedGivenName);
            }
        }

        // displayName (odvozený z příjmení + jméno)
        if (expectedSurname != null) {
            String expectedDisplay = (expectedGivenName != null && !expectedGivenName.isBlank())
                    ? expectedSurname + " " + expectedGivenName : expectedSurname;
            if (!expectedDisplay.equals(existing.getDisplayName())) {
                if (repair) {
                    ldapRepo.updateAttribute(dn, EBakaLDAPAttributes.NAME_DISPLAY, expectedDisplay);
                }
            }
        }

        // e-mail
        String expectedEmail = sql.getGuardianEmail();
        if (expectedEmail != null && !expectedEmail.isBlank()
                && !expectedEmail.equals(existing.getEmail())) {
            changes.add("mail: " + existing.getEmail() + " → " + expectedEmail);
            if (repair) {
                ldapRepo.updateAttribute(dn, EBakaLDAPAttributes.MAIL, expectedEmail);
            }
        }

        // telefon
        String expectedPhone = sql.getGuardianPhone();
        if (expectedPhone != null && !expectedPhone.isBlank()
                && !expectedPhone.equals(existing.getPhone())) {
            changes.add("tel: " + existing.getPhone() + " → " + expectedPhone);
            if (repair) {
                ldapRepo.updateAttribute(dn, EBakaLDAPAttributes.MOBILE, expectedPhone);
            }
        }

        if (changes.isEmpty()) {
            return SyncResult.noChange(existing.getInternalId());
        }

        String desc = String.join(", ", changes);
        if (!repair) {
            return SyncResult.skipped(existing.getInternalId(), "Suchý běh – změny: " + desc);
        }
        return SyncResult.updated(existing.getInternalId(), "Kontakt aktualizován: " + desc);
    }

    /**
     * Vytvoří nový kontakt zákonného zástupce v AD.
     */
    private SyncResult createGuardianContact(StudentRecord sql) {
        String guardianId = sql.getGuardianInternalId();
        String surname = sql.getGuardianSurname();
        String givenName = sql.getGuardianGivenName();

        if (surname == null || surname.isBlank()) {
            return SyncResult.error(guardianId, "Chybí příjmení zákonného zástupce v evidenci.");
        }
        if (givenName == null || givenName.isBlank()) {
            givenName = ""; // jméno může chybět
        }

        // vygenerovat DN
        String targetOu = config.getLdapBaseContacts();
        String displayName = (givenName.isBlank()) ? surname : surname + " " + givenName;
        String dn = "CN=" + displayName + "," + targetOu;

        for (int attempt = 0; attempt < MAX_DN_ATTEMPTS; attempt++) {
            if (!ldapRepo.checkDN(dn)) {
                break; // DN je volné
            }
            dn = BakaUtils.nextDN(dn);
            if (attempt == MAX_DN_ATTEMPTS - 1) {
                dn = null; // vyčerpány pokusy
            }
        }

        if (dn == null) {
            return SyncResult.error(guardianId, "Nelze vygenerovat unikátní DN.");
        }

        DataLDAP data = new DataLDAP();
        data.put(EBakaLDAPAttributes.NAME_DISPLAY.attribute(), displayName);
        data.put(EBakaLDAPAttributes.NAME_LAST.attribute(), surname);
        if (!givenName.isBlank()) {
            data.put(EBakaLDAPAttributes.NAME_FIRST.attribute(), givenName);
        }
        data.put(EBakaLDAPAttributes.EXT01.attribute(), guardianId);
        data.put(EBakaLDAPAttributes.MSXCH_GAL_HIDDEN.attribute(), "TRUE");
        data.put(EBakaLDAPAttributes.MSXCH_REQ_AUTH.attribute(), "TRUE");

        // e-mail a telefon (pokud jsou k dispozici)
        String email = sql.getGuardianEmail();
        if (email != null && !email.isBlank()) {
            data.put(EBakaLDAPAttributes.MAIL.attribute(), email);
        }
        String phone = sql.getGuardianPhone();
        if (phone != null && !phone.isBlank()) {
            data.put(EBakaLDAPAttributes.MOBILE.attribute(), phone);
        }

        String cn = BakaUtils.parseCN(dn);
        guardianRepo.createContact(cn, data);

        return SyncResult.created(guardianId, displayName);
    }

    /**
     * Nastaví členy distribuční skupiny.
     */
    private void updateDistributionList(String dlDn, List<String> expectedMembers) {
        List<String> currentMembers = ldapRepo.listDirectMembers(dlDn);

        // přidat chybějící
        for (String memberDn : expectedMembers) {
            if (!currentMembers.contains(memberDn)) {
                ldapRepo.addToGroup(memberDn, dlDn);
            }
        }

        // odebrat nadbytečné
        for (String memberDn : currentMembers) {
            if (!expectedMembers.contains(memberDn)) {
                ldapRepo.removeFromGroup(memberDn, dlDn);
            }
        }
    }
}
