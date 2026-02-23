package cz.zsstudanka.skola.bakakeeper.service;

import cz.zsstudanka.skola.bakakeeper.config.AppConfig;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLDAPAttributes;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaUAC;
import cz.zsstudanka.skola.bakakeeper.model.StudentRecord;
import cz.zsstudanka.skola.bakakeeper.model.entities.DataLDAP;
import cz.zsstudanka.skola.bakakeeper.repository.LDAPUserRepository;
import cz.zsstudanka.skola.bakakeeper.repository.StudentRepository;
import cz.zsstudanka.skola.bakakeeper.utils.BakaUtils;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementace služby pro správu žákovských účtů.
 * Extrahováno z Student.java a Sync.java (actionInit, actionCheck, checkData).
 *
 * @author Jan Hladěna
 */
public class StudentServiceImpl implements StudentService {

    private static final int MAX_DN_ATTEMPTS = 10;

    private final AppConfig config;
    private final StudentRepository sqlRepo;
    private final LDAPUserRepository ldapRepo;
    private final PasswordService passwordService;
    private final PairingService pairingService;

    public StudentServiceImpl(AppConfig config,
                               StudentRepository sqlRepo,
                               LDAPUserRepository ldapRepo,
                               PasswordService passwordService,
                               PairingService pairingService) {
        this.config = config;
        this.sqlRepo = sqlRepo;
        this.ldapRepo = ldapRepo;
        this.passwordService = passwordService;
        this.pairingService = pairingService;
    }

    @Override
    public List<SyncResult> initializeNewStudents(List<StudentRecord> sqlStudents,
                                                   List<StudentRecord> ldapStudents,
                                                   boolean repair,
                                                   SyncProgressListener listener) {
        listener.onPhaseStart("Inicializace nových žáků");
        List<SyncResult> results = new ArrayList<>();

        // indexy LDAP účtů podle UPN a interního ID
        Set<String> ldapUpns = ldapStudents.stream()
                .filter(s -> s.getUpn() != null)
                .map(s -> s.getUpn().toLowerCase())
                .collect(Collectors.toSet());
        Map<String, StudentRecord> ldapById = ldapStudents.stream()
                .filter(s -> s.getInternalId() != null)
                .collect(Collectors.toMap(StudentRecord::getInternalId, s -> s, (a, b) -> a));

        for (StudentRecord sql : sqlStudents) {
            // žák už má spárovaný LDAP účet → přeskočit
            if (sql.getInternalId() != null && ldapById.containsKey(sql.getInternalId())) {
                continue;
            }

            // žák nemá platný e-mail → potřebuje inicializaci
            String email = sql.getEmail();
            if (email != null && email.toLowerCase().endsWith("@" + config.getMailDomain().toLowerCase())) {
                continue; // už má platný školní mail
            }

            // vygenerovat UPN
            String proposedUpn = BakaUtils.createUPNfromName(
                    sql.getSurname(), sql.getGivenName(), config.getMailDomain());

            if (proposedUpn == null) {
                results.add(SyncResult.error(sql.getInternalId(),
                        "Nelze vygenerovat UPN pro " + sql.getSurname() + " " + sql.getGivenName()));
                continue;
            }

            // ověřit dostupnost UPN
            if (ldapUpns.contains(proposedUpn.toLowerCase())) {
                // UPN obsazený – pokus o párování
                StudentRecord existingLdap = ldapStudents.stream()
                        .filter(s -> proposedUpn.equalsIgnoreCase(s.getUpn()))
                        .findFirst().orElse(null);

                if (existingLdap != null && repair) {
                    SyncResult pairResult = pairingService.attemptToPair(sql, existingLdap, true);
                    results.add(pairResult);
                } else {
                    results.add(SyncResult.skipped(sql.getInternalId(),
                            "UPN " + proposedUpn + " je obsazený."));
                }
                continue;
            }

            if (repair) {
                // zapsat e-mail do SQL
                sqlRepo.updateEmail(sql.getInternalId(), proposedUpn);

                // vytvořit LDAP účet
                SyncResult createResult = createStudentAccount(sql);
                results.add(createResult);

                if (createResult.isSuccess()) {
                    ldapUpns.add(proposedUpn.toLowerCase());
                }
            } else {
                listener.onProgress("Nový žák: " + sql.getSurname() + " " + sql.getGivenName()
                        + " → " + proposedUpn);
                results.add(SyncResult.skipped(sql.getInternalId(), "Suchý běh – účet by byl vytvořen."));
            }
        }

        int ok = (int) results.stream().filter(SyncResult::isSuccess).count();
        int err = (int) results.stream().filter(r -> !r.isSuccess()).count();
        listener.onPhaseEnd("Inicializace nových žáků", ok, err);
        return results;
    }

    @Override
    public List<SyncResult> syncStudentData(List<StudentRecord> sqlStudents,
                                             List<StudentRecord> ldapStudents,
                                             boolean repair,
                                             SyncProgressListener listener) {
        listener.onPhaseStart("Synchronizace dat žáků");
        List<SyncResult> results = new ArrayList<>();

        // indexovat LDAP podle interního ID
        Map<String, StudentRecord> ldapById = ldapStudents.stream()
                .filter(s -> s.getInternalId() != null)
                .collect(Collectors.toMap(StudentRecord::getInternalId, s -> s, (a, b) -> a));

        for (StudentRecord sql : sqlStudents) {
            if (sql.getInternalId() == null) continue;

            StudentRecord ldap = ldapById.get(sql.getInternalId());
            if (ldap == null || ldap.getDn() == null) continue; // nespárovaný

            SyncResult result = syncSingleStudent(sql, ldap, repair, listener);
            results.add(result);
        }

        int ok = (int) results.stream().filter(SyncResult::isSuccess).count();
        int err = (int) results.stream().filter(r -> !r.isSuccess()).count();
        listener.onPhaseEnd("Synchronizace dat žáků", ok, err);
        return results;
    }

    @Override
    public List<SyncResult> retireOrphanedStudents(List<StudentRecord> sqlStudents,
                                                    List<StudentRecord> ldapStudents,
                                                    boolean repair,
                                                    SyncProgressListener listener) {
        listener.onPhaseStart("Vyřazení osiřelých účtů");
        List<SyncResult> results = new ArrayList<>();

        // ID žáků v evidenci
        Set<String> sqlIds = sqlStudents.stream()
                .filter(s -> s.getInternalId() != null)
                .map(StudentRecord::getInternalId)
                .collect(Collectors.toSet());

        for (StudentRecord ldap : ldapStudents) {
            if (ldap.getInternalId() == null) continue;
            if (sqlIds.contains(ldap.getInternalId())) continue; // je v evidenci

            if (repair) {
                SyncResult result = retireStudent(ldap);
                results.add(result);
            } else {
                listener.onProgress("Osiřelý účet: " + ldap.getDisplayName()
                        + " (" + ldap.getInternalId() + ")");
                results.add(SyncResult.skipped(ldap.getInternalId(), "Suchý běh – byl by vyřazen."));
            }
        }

        int ok = (int) results.stream().filter(SyncResult::isSuccess).count();
        int err = (int) results.stream().filter(r -> !r.isSuccess()).count();
        listener.onPhaseEnd("Vyřazení osiřelých účtů", ok, err);
        return results;
    }

    @Override
    public SyncResult createStudentAccount(StudentRecord student) {
        String surname = student.getSurname();
        String givenName = student.getGivenName();
        int year = student.getClassYear();
        String letter = (student.getClassLetter() != null) ? student.getClassLetter() : "A";

        // generovat unikátní DN
        String targetOu = "OU=Trida-" + letter.toUpperCase()
                + ",OU=Rocnik-" + year + "," + config.getLdapBaseStudents();
        String cn = surname + " " + givenName;
        String dn = "CN=" + cn + "," + targetOu;

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
            return SyncResult.error(student.getInternalId(),
                    "Nepodařilo se vygenerovat unikátní DN pro " + surname + " " + givenName);
        }

        // atributy nového účtu
        DataLDAP data = new DataLDAP();
        String login = BakaUtils.createUPNfromName(surname, givenName, config.getLdapDomain());
        String samLogin = (login != null) ? login.split("@")[0] : null;
        String upn = BakaUtils.createUPNfromName(surname, givenName, config.getMailDomain());

        if (upn == null || samLogin == null) {
            return SyncResult.error(student.getInternalId(), "Nelze vygenerovat přihlašovací údaje.");
        }

        data.put(EBakaLDAPAttributes.NAME_DISPLAY.attribute(), surname + " " + givenName);
        data.put(EBakaLDAPAttributes.MAIL.attribute(), upn);
        data.put(EBakaLDAPAttributes.UPN.attribute(), upn);
        data.put(EBakaLDAPAttributes.LOGIN.attribute(), samLogin);
        data.put(EBakaLDAPAttributes.EXT01.attribute(), student.getInternalId());
        data.put(EBakaLDAPAttributes.TITLE.attribute(), "Žák");

        // UAC příznaky
        int uac = EBakaUAC.NORMAL_ACCOUNT.value();
        if (config.getPwdNoExpire().contains(year)) {
            uac |= EBakaUAC.DONT_EXPIRE_PASSWORD.value();
        }
        if (config.getPwdNoChange().contains(year)) {
            uac |= EBakaUAC.PASSWD_CANT_CHANGE.value();
        }
        data.put(EBakaLDAPAttributes.UAC.attribute(), Integer.toString(uac));

        // omezení externího mailu
        String extMailRestricted = config.getExtMailAllowed().contains(year) ? "FALSE" : "TRUE";
        data.put(EBakaLDAPAttributes.EXT02.attribute(), extMailRestricted);

        // počáteční heslo
        Integer classId = 0;
        if (student.getClassNumber() != null) {
            try { classId = Integer.parseInt(student.getClassNumber()); } catch (NumberFormatException ignored) {}
        }
        String password = BakaUtils.createInitialPassword(surname, givenName, year, classId);
        data.put(EBakaLDAPAttributes.PW_UNICODE.attribute(), password);

        // vytvořit účet
        cn = BakaUtils.parseCN(dn);
        ldapRepo.createUser(cn, targetOu, data);

        // přidat do skupin
        String baseGroup = "CN=Skupina-Zaci," + config.getLdapBaseGlobalGroups();
        String classGroup = "CN=Zaci-Trida-" + year + letter.toUpperCase()
                + "," + config.getLdapBaseStudentGroups();

        ldapRepo.addToGroup(dn, baseGroup);
        ldapRepo.addToGroup(dn, classGroup);

        return SyncResult.created(student.getInternalId(),
                surname + " " + givenName + " → " + upn);
    }

    @Override
    public SyncResult retireStudent(StudentRecord student) {
        String dn = student.getDn();
        if (dn == null) {
            return SyncResult.error(student.getInternalId(), "Žák nemá DN.");
        }

        // nastavit titulek na "ABS {rok}"
        String yearStr = new SimpleDateFormat("yyyy").format(new Date());
        ldapRepo.updateAttribute(dn, EBakaLDAPAttributes.TITLE, "ABS " + yearStr);

        // deaktivovat účet
        int disabledUac = EBakaUAC.NORMAL_ACCOUNT.value()
                | EBakaUAC.ACCOUNTDISABLE.value()
                | EBakaUAC.PASSWORD_EXPIRED.value();
        ldapRepo.updateAttribute(dn, EBakaLDAPAttributes.UAC, Integer.toString(disabledUac));

        // odebrat ze všech skupin
        ldapRepo.removeFromAllGroups(dn);

        // přesunout do alumni OU (vytvořit OU pro rok, pokud neexistuje)
        String alumniOu = "OU=" + yearStr + "," + config.getLdapBaseAlumni();
        boolean moved = ldapRepo.moveObject(dn, alumniOu, true);

        if (!moved) {
            return SyncResult.error(student.getInternalId(),
                    student.getDisplayName() + " – účet deaktivován, ale přesun do alumni selhal.");
        }

        return SyncResult.retired(student.getInternalId(),
                student.getDisplayName() + " → alumni " + yearStr);
    }

    // ===========================
    // Interní logika
    // ===========================

    /**
     * Synchronizuje jednoho žáka – porovná SQL vs LDAP data.
     * Extrahováno z Student.sync().
     */
    private SyncResult syncSingleStudent(StudentRecord sql, StudentRecord ldap,
                                          boolean repair, SyncProgressListener listener) {
        String dn = ldap.getDn();
        boolean changed = false;

        // 1. Příjmení
        if (!Objects.equals(sql.getSurname(), ldap.getSurname())) {
            if (repair) {
                ldapRepo.updateAttribute(dn, EBakaLDAPAttributes.NAME_LAST, sql.getSurname());
                String display = sql.getSurname() + " " + sql.getGivenName();
                ldapRepo.updateAttribute(dn, EBakaLDAPAttributes.NAME_DISPLAY, display);
                changed = true;
            }
            listener.onProgress("Neshoda příjmení: " + sql.getInternalId()
                    + " SQL=" + sql.getSurname() + " LDAP=" + ldap.getSurname());
        }

        // 2. Jméno
        if (!Objects.equals(sql.getGivenName(), ldap.getGivenName())) {
            if (repair) {
                ldapRepo.updateAttribute(dn, EBakaLDAPAttributes.NAME_FIRST, sql.getGivenName());
                String display = sql.getSurname() + " " + sql.getGivenName();
                ldapRepo.updateAttribute(dn, EBakaLDAPAttributes.NAME_DISPLAY, display);
                changed = true;
            }
            listener.onProgress("Neshoda jména: " + sql.getInternalId()
                    + " SQL=" + sql.getGivenName() + " LDAP=" + ldap.getGivenName());
        }

        // 3. Třída (OU + skupiny)
        String sqlClass = sql.getClassName(); // "5.A"
        String ldapClass = resolveClassFromDn(ldap.getDn());
        if (sqlClass != null && !sqlClass.equals(ldapClass)) {
            if (repair) {
                moveStudentToClass(dn, sql.getClassYear(), sql.getClassLetter());
                changed = true;
            }
            listener.onProgress("Neshoda třídy: " + sql.getInternalId()
                    + " SQL=" + sqlClass + " LDAP=" + ldapClass);
        }

        // 4. Kontrola externího mailu
        boolean extMailRestricted = ldap.isExtMailRestricted();
        boolean shouldBeRestricted = !config.getExtMailAllowed().contains(sql.getClassYear());
        if (extMailRestricted != shouldBeRestricted) {
            if (repair) {
                ldapRepo.updateAttribute(dn, EBakaLDAPAttributes.EXT02,
                        shouldBeRestricted ? "TRUE" : "FALSE");
                changed = true;
            }
        }

        // 5. UAC příznaky (expirace hesla)
        if (ldap.getUac() > 0) {
            int expectedUac = computeExpectedUac(sql.getClassYear());
            if (ldap.getUac() != expectedUac) {
                if (repair) {
                    ldapRepo.updateAttribute(dn, EBakaLDAPAttributes.UAC,
                            Integer.toString(expectedUac));
                    changed = true;
                }
            }
        }

        if (changed) {
            return SyncResult.updated(sql.getInternalId(), "Data synchronizována.");
        }
        return SyncResult.noChange(sql.getInternalId());
    }

    /**
     * Přesune žákovský účet do správné třídní OU a aktualizuje skupiny.
     * Extrahováno z Student.moveToClass().
     */
    private void moveStudentToClass(String dn, int year, String letter) {
        if (letter == null) letter = "A";

        String targetOu = "OU=Trida-" + letter.toUpperCase()
                + ",OU=Rocnik-" + year + "," + config.getLdapBaseStudents();
        String classGroup = "CN=Zaci-Trida-" + year + letter.toUpperCase()
                + "," + config.getLdapBaseStudentGroups();
        String baseGroup = "CN=Skupina-Zaci," + config.getLdapBaseGlobalGroups();

        // přeřadit skupiny
        ldapRepo.removeFromAllGroups(dn);
        ldapRepo.addToGroup(dn, baseGroup);
        ldapRepo.addToGroup(dn, classGroup);

        // aktualizovat titulek
        ldapRepo.updateAttribute(dn, EBakaLDAPAttributes.TITLE, "Žák");

        // přesunout OU
        ldapRepo.moveObject(dn, targetOu);
    }

    /**
     * Odhadne třídu z DN (např. "CN=...,OU=Trida-A,OU=Rocnik-5,..." → "5.A").
     */
    private String resolveClassFromDn(String dn) {
        if (dn == null) return null;
        try {
            Integer year = BakaUtils.classYearFromDn(dn);
            String letter = BakaUtils.classLetterFromDn(dn);
            if (year != null && letter != null) {
                return year + "." + letter;
            }
        } catch (NumberFormatException ignored) {
            // DN neobsahuje platné označení třídy
        }
        return null;
    }

    /**
     * Vypočítá očekávaný UAC dle ročníku a politik.
     */
    private int computeExpectedUac(int classYear) {
        int uac = EBakaUAC.NORMAL_ACCOUNT.value();
        if (config.getPwdNoExpire().contains(classYear)) {
            uac |= EBakaUAC.DONT_EXPIRE_PASSWORD.value();
        }
        if (config.getPwdNoChange().contains(classYear)) {
            uac |= EBakaUAC.PASSWD_CANT_CHANGE.value();
        }
        return uac;
    }
}
