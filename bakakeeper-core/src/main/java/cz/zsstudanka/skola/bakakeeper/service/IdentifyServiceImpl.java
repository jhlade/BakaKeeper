package cz.zsstudanka.skola.bakakeeper.service;

import cz.zsstudanka.skola.bakakeeper.config.AppConfig;
import cz.zsstudanka.skola.bakakeeper.model.FacultyRecord;
import cz.zsstudanka.skola.bakakeeper.model.StudentRecord;
import cz.zsstudanka.skola.bakakeeper.repository.FacultyRepository;
import cz.zsstudanka.skola.bakakeeper.repository.LDAPUserRepository;
import cz.zsstudanka.skola.bakakeeper.repository.StudentRepository;
import cz.zsstudanka.skola.bakakeeper.utils.BakaUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Implementace služby pro identifikaci uživatelských účtů.
 *
 * <p>Pro kolekce (třídy, ročníky, *) vrací souhrn s počtem žáků a třídním učitelem.
 * Pro jednotlivce vrací kompletní detail z LDAP + SQL (jméno, mail, přihlášení,
 * heslo, třída, zástupce, proxyAddresses, skupiny).</p>
 *
 * @author Jan Hladěna
 */
public class IdentifyServiceImpl implements IdentifyService {

    private final AppConfig config;
    private final StudentRepository studentRepo;
    private final FacultyRepository facultyRepo;
    private final LDAPUserRepository ldapUserRepo;

    public IdentifyServiceImpl(AppConfig config,
                               StudentRepository studentRepo,
                               FacultyRepository facultyRepo,
                               LDAPUserRepository ldapUserRepo) {
        this.config = config;
        this.studentRepo = studentRepo;
        this.facultyRepo = facultyRepo;
        this.ldapUserRepo = ldapUserRepo;
    }

    @Override
    public List<IdentifyResult> identify(String query) {
        RangeSelector selector = RangeSelector.parse(query);

        List<IdentifyResult> results = new ArrayList<>();

        // souhrn tříd
        if (!selector.getClasses().isEmpty()) {
            results.add(resolveClasses(selector));
        }

        // individuální identifikace
        for (String id : selector.getIndividuals()) {
            results.add(resolveIndividual(id));
        }

        return results;
    }

    /**
     * Sestaví souhrn tříd – počet žáků a třídní učitel pro každou třídu.
     */
    private IdentifyResult.ClassSummaryResult resolveClasses(RangeSelector selector) {
        // třídní učitelé indexovaní podle classLabel
        Map<String, FacultyRecord> teachersByClass = facultyRepo.findActive(true).stream()
                .filter(f -> f.getClassLabel() != null)
                .collect(Collectors.toMap(FacultyRecord::getClassLabel, f -> f, (a, b) -> a));

        List<IdentifyResult.ClassInfo> classInfos = new ArrayList<>();
        int totalCount = 0;

        for (String cls : selector.getClasses()) {
            int year = Character.getNumericValue(cls.charAt(0));
            String letter = String.valueOf(cls.charAt(2));

            List<StudentRecord> students = studentRepo.findActive(year, letter);
            int count = students.size();

            // třída bez žáků – přeskočit
            if (count == 0) {
                continue;
            }

            totalCount += count;

            FacultyRecord teacher = teachersByClass.get(cls);
            String teacherName = (teacher != null)
                    ? teacher.getSurname() + " " + teacher.getGivenName()
                    : null;
            String teacherEmail = (teacher != null) ? teacher.getEmail() : null;

            classInfos.add(new IdentifyResult.ClassInfo(cls, count, teacherName, teacherEmail));
        }

        return new IdentifyResult.ClassSummaryResult(classInfos, totalCount);
    }

    /**
     * Identifikuje jednoho uživatele – sloučení LDAP a SQL dat.
     */
    private IdentifyResult resolveIndividual(String id) {
        String upn = completeUpn(id);

        // 1) hledání v LDAP – nejprve pod žáky, pak pod celou bází
        StudentRecord ldapRecord = ldapUserRepo.findByUPN(config.getLdapBaseStudents(), upn);
        if (ldapRecord == null) {
            ldapRecord = ldapUserRepo.findByUPN(config.getLdapBase(), upn);
        }

        if (ldapRecord == null || ldapRecord.getDn() == null) {
            return new IdentifyResult.NotFoundResult(id, "Uživatel s UPN " + upn + " nenalezen v AD.");
        }

        // 2) typ účtu podle DN
        String accountType = resolveAccountType(ldapRecord.getDn());

        // 3) skupiny (CN z DN)
        List<String> groupDns = ldapUserRepo.listMembership(ldapRecord.getDn());
        List<String> groups = groupDns.stream()
                .map(BakaUtils::parseCN)
                .toList();

        // 4) SQL data (třída, číslo v třídním výkazu, zákonný zástupce)
        String className = null;
        String classNumber = null;
        String defaultPassword = null;
        String guardianName = null;
        String guardianEmail = null;
        String guardianPhone = null;
        String teacherName = null;
        String teacherEmail = null;

        // žák nebo vyřazený žák – zkusit SQL evidenci
        if ("žák".equals(accountType) || "vyřazený".equals(accountType)) {
            StudentRecord sqlRecord = studentRepo.findByEmail(upn);

            if (sqlRecord != null) {
                className = sqlRecord.getClassName();
                classNumber = sqlRecord.getClassNumber();

                // výchozí heslo
                int classId = 0;
                if (classNumber != null) {
                    try { classId = Integer.parseInt(classNumber); } catch (NumberFormatException ignored) {}
                }
                defaultPassword = BakaUtils.createInitialPassword(
                        ldapRecord.getSurname(), ldapRecord.getGivenName(),
                        ldapRecord.getClassYear(), classId);

                // zákonný zástupce
                guardianName = buildGuardianName(sqlRecord);
                guardianEmail = sqlRecord.getGuardianEmail();
                guardianPhone = sqlRecord.getGuardianPhone();
            }

            // třídní učitel
            if (className != null) {
                FacultyRecord teacher = findClassTeacher(className);
                if (teacher != null) {
                    teacherName = teacher.getSurname() + " " + teacher.getGivenName();
                    teacherEmail = teacher.getEmail();
                }
            }
        }

        // proxyAddresses z LDAP záznamu
        List<String> proxyAddresses = (ldapRecord.getProxyAddresses() != null)
                ? ldapRecord.getProxyAddresses()
                : List.of();

        return new IdentifyResult.IndividualDetailResult(
                ldapRecord.getSurname(),
                ldapRecord.getGivenName(),
                ldapRecord.getDisplayName(),
                ldapRecord.getEmail(),
                className,
                classNumber,
                defaultPassword,
                ldapRecord.getDn(),
                ldapRecord.getLastLogon(),
                ldapRecord.getPwdLastSet(),
                ldapRecord.getUac(),
                accountType,
                proxyAddresses,
                groups,
                guardianName,
                guardianEmail,
                guardianPhone,
                teacherName,
                teacherEmail
        );
    }

    /**
     * Doplní doménu k loginu, pokud chybí @.
     */
    String completeUpn(String id) {
        return id.contains("@") ? id : id + "@" + config.getMailDomain();
    }

    /**
     * Určí typ účtu podle umístění v AD stromu.
     */
    private String resolveAccountType(String dn) {
        String baseStudents = config.getLdapBaseStudents();
        String baseAlumni = config.getLdapBaseAlumni();
        String baseTeachers = config.getLdapBaseTeachers();

        if (baseStudents != null && dn.contains(baseStudents)) {
            if (baseAlumni != null && dn.contains(baseAlumni)) {
                return "vyřazený";
            }
            return "žák";
        }
        if (baseTeachers != null && dn.contains(baseTeachers)) {
            return "učitel";
        }
        return "zaměstnanec";
    }

    /**
     * Sestaví jméno zákonného zástupce ze SQL záznamu.
     */
    private String buildGuardianName(StudentRecord sqlRecord) {
        String surname = sqlRecord.getGuardianSurname();
        String givenName = sqlRecord.getGuardianGivenName();

        if (surname == null && givenName == null) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        if (surname != null) sb.append(surname);
        if (givenName != null) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(givenName);
        }
        return sb.toString();
    }

    /**
     * Vyhledá třídního učitele podle označení třídy.
     */
    private FacultyRecord findClassTeacher(String classLabel) {
        return facultyRepo.findActive(true).stream()
                .filter(f -> classLabel.equals(f.getClassLabel()))
                .findFirst()
                .orElse(null);
    }
}
