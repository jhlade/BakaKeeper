package cz.zsstudanka.skola.bakakeeper.service;

import cz.zsstudanka.skola.bakakeeper.components.ReportManager;
import cz.zsstudanka.skola.bakakeeper.config.AppConfig;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLDAPAttributes;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLogType;
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

        // množina obsazených adres – UPN + mail + proxyAddresses z celého stromu
        // (žáci + zaměstnanci), aby nedošlo ke kolizi s existujícími adresami
        Set<String> occupiedAddresses = new HashSet<>(ldapUpns);
        // žákovské účty (předány jako parametr)
        collectOccupiedAddresses(ldapStudents, occupiedAddresses);
        // zaměstnanecké účty (načíst zvlášť – zaměstnanci běžně mají více proxy adres)
        List<StudentRecord> staffAccounts = ldapRepo.findAllStudents(
                config.getLdapBaseFaculty(), null);
        collectOccupiedAddresses(staffAccounts, occupiedAddresses);

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

            // vygenerovat unikátní UPN – kontrola proti UPN i proxyAddresses
            String proposedUpn = null;
            for (int attempt = 0; attempt < MAX_DN_ATTEMPTS; attempt++) {
                String candidate = BakaUtils.createUPNfromName(
                        sql.getSurname(), sql.getGivenName(), config.getMailDomain(), attempt);
                if (candidate != null && !occupiedAddresses.contains(candidate.toLowerCase())) {
                    proposedUpn = candidate;
                    break;
                }
                // pokud první pokus koliduje s UPN → pokus o párování
                if (attempt == 0 && candidate != null
                        && ldapUpns.contains(candidate.toLowerCase())) {
                    StudentRecord existingLdap = ldapStudents.stream()
                            .filter(s -> candidate.equalsIgnoreCase(s.getUpn()))
                            .findFirst().orElse(null);

                    if (existingLdap != null && repair) {
                        SyncResult pairResult = pairingService.attemptToPair(sql, existingLdap, true);
                        results.add(pairResult);
                        proposedUpn = null; // zpracováno párováním
                        break;
                    }
                }
            }

            if (proposedUpn == null) {
                // buď zpracováno párováním, nebo se nepodařilo vygenerovat UPN
                if (results.isEmpty() || !sql.getInternalId().equals(results.getLast().getEntityId())) {
                    results.add(SyncResult.error(sql.getInternalId(),
                            "Nelze vygenerovat unikátní UPN pro "
                                    + sql.getSurname() + " " + sql.getGivenName()));
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
                    occupiedAddresses.add(proposedUpn.toLowerCase());
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

        // 1-2. Kontrola změny jména (příjmení nebo křestní jméno)
        boolean nameChanged = !Objects.equals(sql.getSurname(), ldap.getSurname())
                || !Objects.equals(sql.getGivenName(), ldap.getGivenName());

        if (nameChanged) {
            listener.onProgress("Změna jména: " + sql.getInternalId()
                    + " SQL=" + sql.getSurname() + " " + sql.getGivenName()
                    + " LDAP=" + ldap.getSurname() + " " + ldap.getGivenName());

            if (repair) {
                // aktualizovat sn, givenName, displayName (ještě na starém DN)
                ldapRepo.updateAttribute(dn, EBakaLDAPAttributes.NAME_LAST, sql.getSurname());
                ldapRepo.updateAttribute(dn, EBakaLDAPAttributes.NAME_FIRST, sql.getGivenName());
                String display = sql.getSurname() + " " + sql.getGivenName();
                ldapRepo.updateAttribute(dn, EBakaLDAPAttributes.NAME_DISPLAY, display);
                changed = true;

                // přegenerovat login, e-mail a proxyAddresses – vždy při změně jména.
                // E_MAIL v SQL může být prázdný (scénář zmena-jmen) nebo stále obsahovat
                // starý e-mail (běžný případ) – regenerace proběhne v obou případech.
                changed |= regenerateLoginAndEmail(dn, sql, ldap, listener);

                // přejmenovat CN objektu (změna DN) – jako poslední operace,
                // protože invaliduje staré DN
                String newCn = sql.getSurname() + " " + sql.getGivenName();
                String currentCn = BakaUtils.parseCN(dn);
                if (!newCn.equals(currentCn)) {
                    String newDn = ldapRepo.renameObject(dn, newCn);
                    if (newDn != null) {
                        listener.onProgress("Přejmenování CN: " + sql.getInternalId()
                                + " " + currentCn + " → " + BakaUtils.parseCN(newDn));
                        dn = newDn;
                    } else {
                        ReportManager.log(EBakaLogType.LOG_ERR,
                                "Přejmenování CN selhalo pro " + sql.getInternalId()
                                        + " (" + currentCn + " → " + newCn + ").");
                    }
                }
            }
        }

        // 2.5 Kontrola chybějícího emailu u spárovaného žáka
        // Žák může mít LDAP účet (spárovaný přes INTERN_KOD), ale prázdný E_MAIL v SQL
        // – například při částečném selhání inicializace nebo ruční tvorbě účtu.
        // Pokud LDAP účet má UPN → zpětně zapsat do SQL.
        // Pokud nemá ani UPN → vygenerovat nový a zapsat do obou systémů.
        String sqlEmail = sql.getEmail();
        boolean emailMissing = (sqlEmail == null || sqlEmail.isEmpty());
        if (emailMissing && !nameChanged) {
            String ldapUpn = ldap.getUpn();
            if (ldapUpn != null && !ldapUpn.isEmpty()) {
                // LDAP má UPN, SQL nemá → zpětné doplnění
                listener.onProgress("Chybějící email v SQL: " + sql.getInternalId()
                        + " → zpětné doplnění z LDAP UPN: " + ldapUpn);
                if (repair) {
                    sqlRepo.updateEmail(sql.getInternalId(), ldapUpn);
                    changed = true;
                }
            } else {
                // ani LDAP nemá UPN → vygenerovat nový a zapsat do obou
                listener.onProgress("Chybějící email: " + sql.getInternalId()
                        + " " + sql.getSurname() + " " + sql.getGivenName()
                        + " → generování nového UPN");
                if (repair) {
                    changed |= regenerateLoginAndEmail(dn, sql, ldap, listener);
                }
            }
        }

        // 3. Třída (OU + skupiny)
        String sqlClass = sql.getClassName(); // "5.A"
        String ldapClass = resolveClassFromDn(ldap.getDn());
        if (sqlClass != null && !sqlClass.equals(ldapClass)) {
            if (repair) {
                // moveStudentToClass přesune objekt a vrátí nové DN
                dn = moveStudentToClass(dn, sql.getClassYear(), sql.getClassLetter());
                changed = true;
            }
            listener.onProgress("Neshoda třídy: " + sql.getInternalId()
                    + " SQL=" + sqlClass + " LDAP=" + ldapClass);
        }

        // 4. Kontrola externího mailu (po přesunu používáme aktuální dn)
        boolean extMailRestricted = ldap.isExtMailRestricted();
        boolean shouldBeRestricted = !config.getExtMailAllowed().contains(sql.getClassYear());
        if (extMailRestricted != shouldBeRestricted) {
            if (repair) {
                ldapRepo.updateAttribute(dn, EBakaLDAPAttributes.EXT02,
                        shouldBeRestricted ? "TRUE" : "FALSE");
                changed = true;
            }
        }

        // 5. UAC příznaky (expirace hesla – po přesunu používáme aktuální dn)
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
     * Přegeneruje login (UPN, sAMAccountName), e-mail a proxyAddresses
     * při změně jména žáka.
     *
     * <p>Operace:
     * <ol>
     *   <li>Vygenerovat nový UPN z nového jména (s kontrolou kolizí)</li>
     *   <li>Aktualizovat UPN, sAMAccountName a mail v LDAP</li>
     *   <li>Demotovat starý primární email v proxyAddresses na smtp: (sekundární)</li>
     *   <li>Přidat nový email jako SMTP: (primární) do proxyAddresses</li>
     *   <li>Zapsat nový email zpět do SQL evidence</li>
     * </ol>
     * </p>
     *
     * @param dn aktuální DN žáka
     * @param sql záznam z SQL evidence (nové jméno, prázdný email)
     * @param ldap záznam z LDAP (staré jméno, stará adresa, proxyAddresses)
     * @param listener pro logování průběhu
     * @return true pokud byla provedena změna
     */
    private boolean regenerateLoginAndEmail(String dn, StudentRecord sql,
                                             StudentRecord ldap,
                                             SyncProgressListener listener) {
        String newSurname = sql.getSurname();
        String newGivenName = sql.getGivenName();
        String oldEmail = ldap.getEmail(); // aktuální primární email v LDAP

        // 1. Vygenerovat nový UPN – kontrola kolizí proti existujícím UPN a proxyAddresses
        String newUpn = generateUniqueUpn(newSurname, newGivenName, config.getMailDomain(), dn);
        if (newUpn == null) {
            ReportManager.log(EBakaLogType.LOG_ERR,
                    "Nelze vygenerovat nový UPN pro " + newSurname + " " + newGivenName
                            + " (" + sql.getInternalId() + ").");
            return false;
        }

        String newSam = BakaUtils.createSAMloginFromUPNbase(newSurname, newGivenName, newUpn);

        listener.onProgress("Přegenerování loginu: " + sql.getInternalId()
                + " " + ldap.getUpn() + " → " + newUpn
                + " (SAM: " + ldap.getSamAccountName() + " → " + newSam + ")");

        // 2. Aktualizovat UPN, sAMAccountName a mail v LDAP
        ldapRepo.updateAttribute(dn, EBakaLDAPAttributes.UPN, newUpn);
        ldapRepo.updateAttribute(dn, EBakaLDAPAttributes.LOGIN, newSam);
        ldapRepo.updateAttribute(dn, EBakaLDAPAttributes.MAIL, newUpn);

        // 3. Správa proxyAddresses – zachovat celou historii
        List<String> currentProxy = ldap.getProxyAddresses();

        // demotovat starou primární adresu (SMTP:) na sekundární (smtp:)
        if (oldEmail != null && !oldEmail.isEmpty()) {
            String oldPrimary = "SMTP:" + oldEmail;
            String oldSecondary = "smtp:" + oldEmail;

            // odebrat starou primární SMTP: (pokud existuje)
            if (currentProxy.stream().anyMatch(p -> p.equals(oldPrimary))) {
                ldapRepo.removeAttribute(dn, EBakaLDAPAttributes.PROXY_ADDR, oldPrimary);
            }
            // přidat jako sekundární smtp: (pokud ještě neexistuje)
            if (currentProxy.stream().noneMatch(p -> p.equalsIgnoreCase(oldSecondary))) {
                ldapRepo.addAttribute(dn, EBakaLDAPAttributes.PROXY_ADDR, oldSecondary);
            }
        }

        // odebrat případnou existující primární SMTP: záznam pro nový email
        // (např. pokud by nová adresa už byla v proxy jako sekundární z dřívějška)
        String newPrimary = "SMTP:" + newUpn;
        String newSecondaryVariant = "smtp:" + newUpn;
        if (currentProxy.stream().anyMatch(p -> p.equalsIgnoreCase(newSecondaryVariant))) {
            ldapRepo.removeAttribute(dn, EBakaLDAPAttributes.PROXY_ADDR, newSecondaryVariant);
        }

        // přidat nový email jako primární SMTP:
        ldapRepo.addAttribute(dn, EBakaLDAPAttributes.PROXY_ADDR, newPrimary);

        // 4. Zapsat nový email do SQL evidence
        sqlRepo.updateEmail(sql.getInternalId(), newUpn);

        listener.onProgress("Email přegenerován: " + sql.getInternalId()
                + " " + (oldEmail != null ? oldEmail : "(žádný)") + " → " + newUpn);

        return true;
    }

    /**
     * Vygeneruje unikátní UPN pro nové jméno s kontrolou kolizí.
     *
     * <p>Kontroluje kolize proti celému stromu uživatelů (žáci + zaměstnanci):
     * <ul>
     *   <li>Existujícím UPN všech účtů</li>
     *   <li>Existujícím proxyAddresses všech účtů (celá historie adres)</li>
     * </ul>
     * Zaměstnanci běžně mají více záznamů v proxyAddresses, proto je kontrola
     * celého stromu klíčová pro předcházení kolizím.</p>
     *
     * @param surname nové příjmení
     * @param givenName nové jméno
     * @param domain mailová doména
     * @param excludeDn DN účtu, pro který generujeme (vyloučit z kontroly kolizí)
     * @return unikátní UPN, nebo null pokud se nepodařilo vygenerovat
     */
    private String generateUniqueUpn(String surname, String givenName,
                                      String domain, String excludeDn) {
        // načíst VŠECHNY uživatelské účty z celého stromu (žáci + zaměstnanci)
        // pro úplnou kontrolu kolizí UPN a proxyAddresses
        List<StudentRecord> allUsers = ldapRepo.findAllStudents(
                config.getLdapBase(), null);

        // množina obsazených adres: UPN + mail + všechny proxyAddresses (bez prefixu)
        Set<String> occupiedAddresses = new HashSet<>();
        for (StudentRecord s : allUsers) {
            // vyloučit účet, pro který generujeme nový UPN
            if (excludeDn != null && excludeDn.equalsIgnoreCase(s.getDn())) {
                continue;
            }
            if (s.getUpn() != null) {
                occupiedAddresses.add(s.getUpn().toLowerCase());
            }
            if (s.getEmail() != null) {
                occupiedAddresses.add(s.getEmail().toLowerCase());
            }
            for (String proxy : s.getProxyAddresses()) {
                // odstranit SMTP:/smtp: prefix pro normalizované porovnání
                String addr = proxy.startsWith("SMTP:") ? proxy.substring(5)
                        : proxy.startsWith("smtp:") ? proxy.substring(5)
                        : proxy;
                occupiedAddresses.add(addr.toLowerCase());
            }
        }

        // pokus o vygenerování unikátního UPN (max MAX_DN_ATTEMPTS pokusů)
        for (int attempt = 0; attempt < MAX_DN_ATTEMPTS; attempt++) {
            String candidate = BakaUtils.createUPNfromName(surname, givenName, domain, attempt);
            if (candidate != null && !occupiedAddresses.contains(candidate.toLowerCase())) {
                return candidate;
            }
        }

        return null;
    }

    /**
     * Přesune žákovský účet do správné třídní OU a aktualizuje skupiny.
     * Extrahováno z Student.moveToClass().
     *
     * <p>Pořadí operací: nejprve přesun do cílové OU (dokud je staré DN platné),
     * poté úprava skupin a atributů s novým DN.</p>
     *
     * @param dn aktuální DN žáka
     * @param year cílový ročník
     * @param letter písmeno cílové třídy
     * @return nové DN žáka po přesunu (nebo původní DN, pokud přesun selhal)
     */

    /**
     * Naplní množinu obsazenými e-mailovými adresami ze seznamu uživatelských účtů.
     * Pro každý účet přidá UPN, primární mail a všechny proxyAddresses (bez prefixu).
     *
     * @param accounts seznam účtů
     * @param target cílová množina adres (case-insensitive, lowercase)
     */
    private void collectOccupiedAddresses(List<StudentRecord> accounts, Set<String> target) {
        for (StudentRecord s : accounts) {
            if (s.getEmail() != null) {
                target.add(s.getEmail().toLowerCase());
            }
            if (s.getUpn() != null) {
                target.add(s.getUpn().toLowerCase());
            }
            for (String proxy : s.getProxyAddresses()) {
                String addr = proxy.startsWith("SMTP:") ? proxy.substring(5)
                        : proxy.startsWith("smtp:") ? proxy.substring(5)
                        : proxy;
                target.add(addr.toLowerCase());
            }
        }
    }

    private String moveStudentToClass(String dn, int year, String letter) {
        if (letter == null) letter = "A";

        String targetOu = "OU=Trida-" + letter.toUpperCase()
                + ",OU=Rocnik-" + year + "," + config.getLdapBaseStudents();
        String classGroup = "CN=Zaci-Trida-" + year + letter.toUpperCase()
                + "," + config.getLdapBaseStudentGroups();
        String baseGroup = "CN=Skupina-Zaci," + config.getLdapBaseGlobalGroups();

        // ověřit, že zdrojový objekt stále existuje na uvedeném DN
        if (!ldapRepo.checkDN(dn)) {
            ReportManager.log(EBakaLogType.LOG_ERR,
                    "Objekt [" + dn + "] v LDAP neexistuje – přesun třídy nebude proveden.");
            return dn;
        }

        // 1. Nejprve přesunout do cílové OU (dokud je staré DN platné)
        boolean moved = ldapRepo.moveObject(dn, targetOu);

        // vypočítat nové DN – po přesunu operace pracují s novým umístěním
        String activeDn = moved
                ? "CN=" + BakaUtils.parseCN(dn) + "," + targetOu
                : dn;

        // 2. Přeřadit skupiny (operace nad skupinovými objekty – používá activeDn jako member)
        ldapRepo.removeFromAllGroups(activeDn);
        ldapRepo.addToGroup(activeDn, baseGroup);
        ldapRepo.addToGroup(activeDn, classGroup);

        // 3. Aktualizovat titulek na novém DN
        ldapRepo.updateAttribute(activeDn, EBakaLDAPAttributes.TITLE, "Žák");

        return activeDn;
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
