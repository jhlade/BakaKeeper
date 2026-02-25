package cz.zsstudanka.skola.bakakeeper.service;

import cz.zsstudanka.skola.bakakeeper.config.AppConfig;
import cz.zsstudanka.skola.bakakeeper.model.FacultyRecord;
import cz.zsstudanka.skola.bakakeeper.model.StudentRecord;
import cz.zsstudanka.skola.bakakeeper.repository.FacultyRepository;
import cz.zsstudanka.skola.bakakeeper.repository.LDAPUserRepository;
import cz.zsstudanka.skola.bakakeeper.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Testy pro IdentifyServiceImpl.
 *
 * @author Jan Hladěna
 */
@ExtendWith(MockitoExtension.class)
class IdentifyServiceImplTest {

    @Mock AppConfig config;
    @Mock StudentRepository studentRepo;
    @Mock FacultyRepository facultyRepo;
    @Mock LDAPUserRepository ldapUserRepo;

    private IdentifyServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new IdentifyServiceImpl(config, studentRepo, facultyRepo, ldapUserRepo);

        // výchozí konfigurace
        lenient().when(config.getMailDomain()).thenReturn("skola.cz");
        lenient().when(config.getLdapBase()).thenReturn("OU=Uzivatele,OU=Skola,DC=skola,DC=local");
        lenient().when(config.getLdapBaseStudents()).thenReturn("OU=Zaci,OU=Uzivatele,OU=Skola,DC=skola,DC=local");
        lenient().when(config.getLdapBaseAlumni()).thenReturn("OU=Vyrazeni,OU=Zaci,OU=Uzivatele,OU=Skola,DC=skola,DC=local");
        lenient().when(config.getLdapBaseTeachers()).thenReturn("OU=Ucitele,OU=Zamestnanci,OU=Uzivatele,OU=Skola,DC=skola,DC=local");
    }

    @Test
    void identifyClassSummary() {
        // třída 5.A s třídním učitelem
        FacultyRecord teacher = new FacultyRecord();
        teacher.setClassLabel("5.A");
        teacher.setSurname("Burgetová");
        teacher.setGivenName("Lada");
        teacher.setEmail("burgetova@skola.cz");
        when(facultyRepo.findActive(true)).thenReturn(List.of(teacher));

        StudentRecord s1 = createStudent("001", "Novák", "Tomáš");
        StudentRecord s2 = createStudent("002", "Veselá", "Marie");
        when(studentRepo.findActive(5, "A")).thenReturn(List.of(s1, s2));

        List<IdentifyResult> results = service.identify("5.A");

        assertEquals(1, results.size());
        assertInstanceOf(IdentifyResult.ClassSummaryResult.class, results.get(0));

        IdentifyResult.ClassSummaryResult summary = (IdentifyResult.ClassSummaryResult) results.get(0);
        assertEquals(1, summary.classes().size());
        assertEquals("5.A", summary.classes().get(0).classLabel());
        assertEquals(2, summary.classes().get(0).studentCount());
        assertEquals("Burgetová Lada", summary.classes().get(0).teacherName());
        assertEquals("burgetova@skola.cz", summary.classes().get(0).teacherEmail());
        assertEquals(2, summary.totalCount());
    }

    @Test
    void identifyIndividualWithUpn() {
        // žák s plným UPN
        StudentRecord ldapRecord = createLdapStudent(
                "001", "Novák", "Tomáš", "Tomáš Novák",
                "novak.tomas@skola.cz",
                "CN=Novák Tomáš,OU=Trida-A,OU=Rocnik-5,OU=Zaci,OU=Uzivatele,OU=Skola,DC=skola,DC=local");
        ldapRecord.setLastLogon(133500000000000000L);
        ldapRecord.setPwdLastSet(133400000000000000L);
        ldapRecord.setUac(512);
        ldapRecord.setProxyAddresses(List.of("SMTP:novak.tomas@skola.cz", "smtp:tomas.novak@skola.cz"));

        when(ldapUserRepo.findByUPN(config.getLdapBaseStudents(), "novak.tomas@skola.cz"))
                .thenReturn(ldapRecord);
        when(ldapUserRepo.listMembership(ldapRecord.getDn()))
                .thenReturn(List.of("CN=Zaci-5A,OU=Skupiny,DC=skola,DC=local"));

        StudentRecord sqlRecord = new StudentRecord();
        sqlRecord.setClassName("5.A");
        sqlRecord.setClassNumber("15");
        sqlRecord.setGuardianSurname("Nováková");
        sqlRecord.setGuardianGivenName("Jana");
        sqlRecord.setGuardianEmail("novakova@email.cz");
        sqlRecord.setGuardianPhone("+420777111222");
        when(studentRepo.findByEmail("novak.tomas@skola.cz")).thenReturn(sqlRecord);

        FacultyRecord teacher = new FacultyRecord();
        teacher.setClassLabel("5.A");
        teacher.setSurname("Burgetová");
        teacher.setGivenName("Lada");
        teacher.setEmail("burgetova@skola.cz");
        when(facultyRepo.findActive(true)).thenReturn(List.of(teacher));

        List<IdentifyResult> results = service.identify("novak.tomas@skola.cz");

        assertEquals(1, results.size());
        assertInstanceOf(IdentifyResult.IndividualDetailResult.class, results.get(0));

        IdentifyResult.IndividualDetailResult d = (IdentifyResult.IndividualDetailResult) results.get(0);
        assertEquals("Novák", d.surname());
        assertEquals("Tomáš", d.givenName());
        assertEquals("Tomáš Novák", d.displayName());
        assertEquals("novak.tomas@skola.cz", d.email());
        assertEquals("5.A", d.className());
        assertEquals("15", d.classNumber());
        assertNotNull(d.defaultPassword());
        assertEquals("žák", d.accountType());
        assertEquals(133500000000000000L, d.lastLogon());
        assertEquals(133400000000000000L, d.pwdLastSet());
        assertEquals(512, d.uac());
        assertEquals(2, d.proxyAddresses().size());
        assertEquals(1, d.groups().size());
        assertEquals("Zaci-5A", d.groups().get(0));
        assertEquals("Nováková Jana", d.guardianName());
        assertEquals("novakova@email.cz", d.guardianEmail());
        assertEquals("+420777111222", d.guardianPhone());
        assertEquals("Burgetová Lada", d.teacherName());
    }

    @Test
    void identifyBareLogin() {
        // bare login bez @ – služba doplní doménu
        StudentRecord ldapRecord = createLdapStudent(
                "001", "Novák", "Tomáš", "Tomáš Novák",
                "novak.tomas@skola.cz",
                "CN=Novák Tomáš,OU=Trida-A,OU=Rocnik-5,OU=Zaci,OU=Uzivatele,OU=Skola,DC=skola,DC=local");

        when(ldapUserRepo.findByUPN(config.getLdapBaseStudents(), "novak.tomas@skola.cz"))
                .thenReturn(ldapRecord);
        when(ldapUserRepo.listMembership(ldapRecord.getDn())).thenReturn(List.of());

        StudentRecord sqlRecord = new StudentRecord();
        sqlRecord.setClassName("5.A");
        sqlRecord.setClassNumber("15");
        when(studentRepo.findByEmail("novak.tomas@skola.cz")).thenReturn(sqlRecord);
        when(facultyRepo.findActive(true)).thenReturn(List.of());

        List<IdentifyResult> results = service.identify("novak.tomas");

        assertEquals(1, results.size());
        assertInstanceOf(IdentifyResult.IndividualDetailResult.class, results.get(0));

        IdentifyResult.IndividualDetailResult d = (IdentifyResult.IndividualDetailResult) results.get(0);
        assertEquals("novak.tomas@skola.cz", d.email());
    }

    @Test
    void identifyNotFound() {
        when(ldapUserRepo.findByUPN(config.getLdapBaseStudents(), "neexistuje@skola.cz"))
                .thenReturn(null);
        when(ldapUserRepo.findByUPN(config.getLdapBase(), "neexistuje@skola.cz"))
                .thenReturn(null);

        List<IdentifyResult> results = service.identify("neexistuje@skola.cz");

        assertEquals(1, results.size());
        assertInstanceOf(IdentifyResult.NotFoundResult.class, results.get(0));

        IdentifyResult.NotFoundResult nf = (IdentifyResult.NotFoundResult) results.get(0);
        assertEquals("neexistuje@skola.cz", nf.query());
        assertTrue(nf.message().contains("nenalezen"));
    }

    @Test
    void identifyMixedClassAndIndividual() {
        // kombinace: třída + individuální dotaz
        FacultyRecord teacher = new FacultyRecord();
        teacher.setClassLabel("5.A");
        teacher.setSurname("Burgetová");
        teacher.setGivenName("Lada");
        teacher.setEmail("burgetova@skola.cz");
        when(facultyRepo.findActive(true)).thenReturn(List.of(teacher));

        StudentRecord s1 = createStudent("001", "Novák", "Tomáš");
        when(studentRepo.findActive(5, "A")).thenReturn(List.of(s1));

        // individuální žák nenalezen
        when(ldapUserRepo.findByUPN(config.getLdapBaseStudents(), "neexistuje@skola.cz"))
                .thenReturn(null);
        when(ldapUserRepo.findByUPN(config.getLdapBase(), "neexistuje@skola.cz"))
                .thenReturn(null);

        List<IdentifyResult> results = service.identify("5.A,neexistuje@skola.cz");

        assertEquals(2, results.size());
        assertInstanceOf(IdentifyResult.ClassSummaryResult.class, results.get(0));
        assertInstanceOf(IdentifyResult.NotFoundResult.class, results.get(1));
    }

    @Test
    void identifyTeacherAccountType() {
        // učitel – DN obsahuje OU=Ucitele
        StudentRecord ldapRecord = createLdapStudent(
                "T001", "Kolářová", "Eva", "Eva Kolářová",
                "kolarova@skola.cz",
                "CN=Kolářová Eva,OU=Ucitele,OU=Zamestnanci,OU=Uzivatele,OU=Skola,DC=skola,DC=local");

        when(ldapUserRepo.findByUPN(config.getLdapBaseStudents(), "kolarova@skola.cz"))
                .thenReturn(null);
        when(ldapUserRepo.findByUPN(config.getLdapBase(), "kolarova@skola.cz"))
                .thenReturn(ldapRecord);
        when(ldapUserRepo.listMembership(ldapRecord.getDn())).thenReturn(List.of());

        List<IdentifyResult> results = service.identify("kolarova@skola.cz");

        assertEquals(1, results.size());
        IdentifyResult.IndividualDetailResult d = (IdentifyResult.IndividualDetailResult) results.get(0);
        assertEquals("učitel", d.accountType());
        // učitel nemá heslo ani zástupce
        assertNull(d.defaultPassword());
        assertNull(d.guardianName());
    }

    @Test
    void completeUpnAppendsMailDomain() {
        assertEquals("novak.tomas@skola.cz", service.completeUpn("novak.tomas"));
        assertEquals("novak.tomas@skola.cz", service.completeUpn("novak.tomas@skola.cz"));
    }

    // === Pomocné metody ===

    private static StudentRecord createStudent(String id, String surname, String givenName) {
        StudentRecord s = new StudentRecord();
        s.setInternalId(id);
        s.setSurname(surname);
        s.setGivenName(givenName);
        return s;
    }

    private static StudentRecord createLdapStudent(String id, String surname, String givenName,
                                                     String displayName, String email, String dn) {
        StudentRecord s = new StudentRecord();
        s.setInternalId(id);
        s.setSurname(surname);
        s.setGivenName(givenName);
        s.setDisplayName(displayName);
        s.setEmail(email);
        s.setDn(dn);
        s.setClassYear(5);
        return s;
    }
}
