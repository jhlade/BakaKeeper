package cz.zsstudanka.skola.bakakeeper.routines;

import cz.zsstudanka.skola.bakakeeper.connectors.BakaADAuthenticator;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLDAPAttributes;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaSQL;
import cz.zsstudanka.skola.bakakeeper.model.collections.SQLrecords;
import cz.zsstudanka.skola.bakakeeper.model.entities.DataSQL;
import cz.zsstudanka.skola.bakakeeper.settings.Settings;
import cz.zsstudanka.skola.bakakeeper.utils.BakaUtils;

import java.util.Locale;
import java.util.Map;

/**
 * Rutiny manipulačních operací.
 *
 * TODO.
 */
public class Manipulation {

    /** maximální počet pokusů o reset hesla */
    public static final int MAX_PASSWORD_ATTEMPTS = 10;

    // TODO - ze statiky singleton, pamatovat si si aktivní účet
    /** instance manipulačních operací */
    private static Manipulation instance = null;

    public Manipulation() {
        // TODO konstruktor
    }

    /**
     * Instance manipulačních operací.
     *
     * @return instance
     */
    public static Manipulation getInstance() {
        if (Manipulation.instance == null) {
            Manipulation.instance = new Manipulation();
        }

        return Manipulation.instance;
    }


    /**
     * Reset hesla daného účtu.
     *
     * @param upn UPN účtu
     * @return úspěch operace
     */
    public static boolean resetPassword(String upn) {

        // získání aktivního účtu
        Map<Integer, Map<String, String>> data = BakaADAuthenticator.getInstance().getUserInfo(upn.toLowerCase(Locale.ROOT) + "@" + Settings.getInstance().getMailDomain(), Settings.getInstance().getLDAP_base());

        if (data.size() == 1) {

            // žáci
            if (data.get(0).get(EBakaLDAPAttributes.DN.attribute()).contains(Settings.getInstance().getLDAP_baseStudents())) {
                return resetStudentPassword(upn);
            }

            // učitelé
            if (data.get(0).get(EBakaLDAPAttributes.DN.attribute()).contains(Settings.getInstance().getLDAP_baseTeachers())) {
                return resetFacultyPassword(upn);
            }

            // ostatní -- ? (vychovatelky, externí lektoři, ...)
        }

        // účet v AD nenalezen
        return false;
    }

    /**
     * Reset hesla žáka.
     *
     * @param studentUPN UPN žáka
     * @return úspěch operace
     */
    private static boolean resetStudentPassword(String studentUPN) {

        // získání aktivního účtu
        Map<Integer, Map<String, String>> data = BakaADAuthenticator.getInstance().getUserInfo(studentUPN.toLowerCase(Locale.ROOT) + "@" + Settings.getInstance().getMailDomain(), Settings.getInstance().getLDAP_baseStudents());

        // parciální načtení z evidence žáků - získání čísla v třídním výkazu
        SQLrecords catalog = new SQLrecords(BakaUtils.classYearFromDn(data.get(0).get(EBakaLDAPAttributes.DN.attribute())), BakaUtils.classLetterFromDn(data.get(0).get(EBakaLDAPAttributes.DN.attribute())));
        DataSQL studentRecord = catalog.getBy(EBakaSQL.F_STU_MAIL, data.get(0).get(EBakaLDAPAttributes.MAIL.attribute()));

        String password;
        boolean set = false;
        int attempt = 0;

        while (!set || attempt < MAX_PASSWORD_ATTEMPTS) {

            password = BakaUtils.nextPassword(
                    data.get(0).get(EBakaLDAPAttributes.NAME_LAST.attribute()),
                    data.get(0).get(EBakaLDAPAttributes.NAME_FIRST.attribute()),
                    BakaUtils.classYearFromDn(data.get(0).get(EBakaLDAPAttributes.DN.attribute())),
                    Integer.parseInt(studentRecord.get(EBakaSQL.F_STU_CLASS_ID.basename())),
                    attempt
            );

            set = setPassword(studentUPN, password, false);
            attempt++;
        }

        return set;
    }

    /**
     * Reset hesla učitele.
     * TODO
     *
     * @param facultyUPN UPN učitele
     * @return úspěch operace
     */
    private static boolean resetFacultyPassword(String facultyUPN) {
        // TODO - zatím není implementováno
        return false;
    }

    /**
     * Provedení nastavení hesla.
     *
     * @param upn UPN účtu
     * @param newPassword heslo
     * @param flagMustChange TODO příznak nutnosti změny hesla při dalším přihlášení
     * @return úspěch operace
     */
    private static boolean setPassword(String upn, String newPassword, boolean flagMustChange) {

        // získání aktivního účtu
        Map<Integer, Map<String, String>> data = BakaADAuthenticator.getInstance().getUserInfo(upn.toLowerCase(Locale.ROOT) + "@" + Settings.getInstance().getMailDomain(), Settings.getInstance().getLDAP_baseStudents());

        // extrakce DN
        String dn = data.get(0).get(EBakaLDAPAttributes.DN.attribute());

        // provední záznamu
        boolean set = BakaADAuthenticator.getInstance().replaceAttribute(dn, EBakaLDAPAttributes.PW_UNICODE, newPassword);
        BakaADAuthenticator.getInstance().replaceAttribute(dn, EBakaLDAPAttributes.PW_LASTSET, EBakaLDAPAttributes.PW_LASTSET.value());

        if (flagMustChange) {
            // TODO nutnost změny hesla při dalším přihlášení
        }

        // úspěch operace
        return set;
    }

}
