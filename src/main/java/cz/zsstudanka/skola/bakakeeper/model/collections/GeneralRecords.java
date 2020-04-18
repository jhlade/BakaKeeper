package cz.zsstudanka.skola.bakakeeper.model.collections;

import cz.zsstudanka.skola.bakakeeper.App;
import cz.zsstudanka.skola.bakakeeper.connectors.BakaADAuthenticator;
import cz.zsstudanka.skola.bakakeeper.connectors.BakaSQL;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLDAPAttributes;
import cz.zsstudanka.skola.bakakeeper.settings.Settings;
import cz.zsstudanka.skola.bakakeeper.utils.BakaUtils;

import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Kolekce obyčejných LDAP záznamů.
 * Prochází požadovanou nejbližší OU a ošetřuje liché záznamy bez aktuálních protějšků v SQL databázi.
 * Konstrukce by měla vždy proběhnout až po primární kontrole a synchronizaci konkrétních objektů, tedy pro odhalení
 * lichých záznamů po povýšení školního roku.
 *
 * @author Jan Hladěna
 * @deprecated
 */
public class GeneralRecords {

    /** záznamy */
    private Map<Integer, Map<String, Object>> records;

    /**
     * Konstruktor pro jednu konkrétní OU.
     *
     * @param OU organizační jednotka k prohledání
     */
    public GeneralRecords(String OU) {

        HashMap<String, String> ldapQ = new HashMap<String, String>();

        // kontakty nebo uživatelé
        if (OU.contains(Settings.getInstance().getLDAP_baseContacts())) {
            ldapQ.put(EBakaLDAPAttributes.OC_CONTACT.attribute(), EBakaLDAPAttributes.OC_CONTACT.value());
        } else {
            ldapQ.put(EBakaLDAPAttributes.ST_USER.attribute(), EBakaLDAPAttributes.ST_USER.value());
            ldapQ.put(EBakaLDAPAttributes.OC_USER.attribute(), EBakaLDAPAttributes.OC_USER.value());
        }

        // získaná data
        String[] data = {
                EBakaLDAPAttributes.DN.attribute(), // DN pro manipulaci s objektem
                EBakaLDAPAttributes.MAIL.attribute(),
                EBakaLDAPAttributes.NAME_FIRST.attribute(),
                EBakaLDAPAttributes.NAME_LAST.attribute(),
        };

        this.records = BakaADAuthenticator.getInstance().getInfoInOU(OU, ldapQ, data);

        // zpracování
        if (this.records.size() > 0) {
            for (int i = 0; i < this.records.size(); i++) {
                if (!this.recordInSQL(OU, i)) {
                    this.cleanUp(OU, i);
                }
            } // for
        } // size > 0
    }

    /**
     * Ověření existence LDAP záznamu proti SQL.
     *
     * @param OU organizační jednotka
     * @param r ID záznamu
     * @return záznam existuje v databázi
     */
    private boolean recordInSQL(String OU, int r) {

        if (OU.contains(Settings.getInstance().getLDAP_baseContacts())) {
            return existsAsZZ(r);
        } else {
            return existsAsZak(OU, r);
        }
    }

    /**
     * Ověření existence záznamu v databázi žáků.
     *
     * @param OU organizační jednotka
     * @param r ID záznamu
     * @return záznam existuje v evidenci žáků
     */
    private boolean existsAsZak(String OU, int r) {

        String trida = BakaUtils.tridaFromDN("CN={...}," + OU);

        if (Settings.getInstance().beVerbose()) {
            System.err.println("[ INFO ] Kontroluje se existence záznamu " + this.records.get(r).get(EBakaLDAPAttributes.NAME_FIRST.attribute()).toString() + " " + this.records.get(r).get(EBakaLDAPAttributes.NAME_LAST.attribute()).toString() + " ve třídě " + trida + ".");
        }

        BakaSQL.getInstance().connect();

        // TODO jak moc striktní pravidla nastavit?
        String sql = "SELECT dbo.zaci.INTERN_KOD,dbo.zaci.PRIJMENI,dbo.zaci.JMENO,dbo.zaci.TRIDA,dbo.zaci.E_MAIL FROM dbo.zaci "
                //+ "WHERE dbo.zaci.JMENO LIKE '" + this.records.get(r).get(EBakaLDAPAttributes.JMENO.attribute()).toString() + "%' "
                //+ "AND dbo.zaci.PRIJMENI LIKE '" + this.records.get(r).get(EBakaLDAPAttributes.PRIJMENI.attribute()).toString() + "%' "
                + "WHERE dbo.zaci.JMENO COLLATE Latin1_general_CI_AI LIKE '" + this.records.get(r).get(EBakaLDAPAttributes.NAME_FIRST.attribute()).toString() + "%' COLLATE Latin1_general_CI_AI "
                + "AND dbo.zaci.PRIJMENI COLLATE Latin1_general_CI_AI LIKE '" + this.records.get(r).get(EBakaLDAPAttributes.NAME_LAST.attribute()).toString() + "%' COLLATE Latin1_general_CI_AI "
                //+ "AND dbo.zaci.TRIDA LIKE '" + trida + "%' " // příliš striktní?
                + "AND dbo.zaci.EVID_DO IS NULL"
                + "";

        try {

            if (Settings.getInstance().debugMode()) {
                System.err.println("[ DEBUG ] Vykonává se SQL: " + sql);
            }

            ResultSet rs = BakaSQL.getInstance().select(sql);

            while (rs.next()) {

                if (Settings.getInstance().debugMode()) {
                    System.err.println("[ DEBUG ] Nalezena data pro záznam " + this.records.get(r).get(EBakaLDAPAttributes.NAME_FIRST.attribute()).toString() + " " + this.records.get(r).get(EBakaLDAPAttributes.NAME_LAST.attribute()).toString() + ": " + rs.getString("INTERN_KOD"));
                }

                return true;
            }

            System.err.println("[ INFO ] Data pro uživatele " + this.records.get(r).get(EBakaLDAPAttributes.NAME_FIRST.attribute()).toString() + " " + this.records.get(r).get(EBakaLDAPAttributes.NAME_LAST.attribute()).toString() + " (" + trida + ") nebyla nalezena.");

            if (Settings.getInstance().beVerbose()) {
                System.err.println("[ INFO ] Data pro LDAP záznam " + this.records.get(r).get(EBakaLDAPAttributes.NAME_FIRST.attribute()).toString() + " " + this.records.get(r).get(EBakaLDAPAttributes.NAME_LAST.attribute()).toString() + " nebyla nalezena, proběhne automatické vyčištění.");
            }
            return false;

        } catch (Exception e) {

            if (Settings.getInstance().beVerbose()) {
                System.err.println("[ CHYBA ] Nebylo možné provést kontrolu existence záznamu v evidenci žáků.");
            }

            if (Settings.getInstance().debugMode()) {
                System.err.println("[ CHYBA ] " + e.getMessage());
                e.printStackTrace(System.err);
            }
        }

        /*
        * // SQL dotaz
        String select = "SELECT dbo.zaci.INTERN_KOD,dbo.zaci.PRIJMENI,dbo.zaci.JMENO,dbo.zaci.TRIDA,dbo.zaci.E_MAIL," // data žáka
                + "dbo.zaci_zzd.ID AS ZZ_KOD,dbo.zaci_zzd.PRIJMENI AS ZZ_PRIJMENI,dbo.zaci_zzd.JMENO AS ZZ_JMENO,dbo.zaci_zzd.TEL_MOBIL AS ZZ_TELEFON,dbo.zaci_zzd.E_MAIL AS ZZ_MAIL " // data ZZ
                + "FROM dbo.zaci LEFT JOIN dbo.zaci_zzd ON (dbo.zaci_zzd.ID = (SELECT TOP 1 dbo.zaci_zzr.ID_ZZ FROM dbo.zaci_zzr WHERE dbo.zaci_zzr.INTERN_KOD = dbo.zaci.INTERN_KOD AND (dbo.zaci_zzr.JE_ZZ = '1' AND dbo.zaci_zzr.PRIMARNI = '1'))) " // detekce primárního ZZ
                + "WHERE dbo.zaci.TRIDA LIKE '%.%' AND dbo.zaci.EVID_DO IS NULL " // žák existuje
                + "AND dbo.zaci.TRIDA = '" + this.getCisloRocniku() + "." + this.getPismeno() + "' " // jedna konkrétní třída
                + "ORDER BY dbo.zaci.PRIJMENI ASC, dbo.zaci.JMENO ASC;"; // seřazení podle abecedy
        * */

        // fallback pro žádnou činnost
        return false;
    }

    /**
     * Ověření existence záznamu v evidenci primárních zákonných zástupců.
     *
     * @param r ID záznamu
     * @return záznam existuje v evidenci primárních zákonných zástupců
     */
    private boolean existsAsZZ(int r) {

        if (Settings.getInstance().beVerbose()) {
            System.err.println("[ INFO ] Kontroluje se existence záznamu " + this.records.get(r).get(EBakaLDAPAttributes.NAME_FIRST.attribute()).toString() + " " + this.records.get(r).get(EBakaLDAPAttributes.NAME_LAST.attribute()).toString() + " typu kontakt.");
        }

        BakaSQL.getInstance().connect();

        String sql = "SELECT dbo.zaci_zzd.ID AS ZZ_KOD,dbo.zaci_zzd.PRIJMENI AS ZZ_PRIJMENI,dbo.zaci_zzd.JMENO AS ZZ_JMENO,dbo.zaci_zzd.TEL_MOBIL AS ZZ_TELEFON,dbo.zaci_zzd.E_MAIL AS ZZ_MAIL, dbo.zaci.INTERN_KOD " // data
                + "FROM dbo.zaci_zzd LEFT JOIN dbo.zaci ON (dbo.zaci_zzd.ID = (SELECT TOP 1 dbo.zaci_zzr.ID_ZZ FROM dbo.zaci_zzr WHERE dbo.zaci_zzr.INTERN_KOD = dbo.zaci.INTERN_KOD AND (dbo.zaci_zzr.JE_ZZ = '1' AND dbo.zaci_zzr.PRIMARNI = '1'))) " // ZZ je primární
                + "WHERE dbo.zaci.TRIDA LIKE '%.%' AND dbo.zaci.EVID_DO IS NULL " // žák existuje v evidenci
                + "AND dbo.zaci_zzd.E_MAIL LIKE '" + this.records.get(r).get(EBakaLDAPAttributes.MAIL.attribute()) + "%' " // e-mail
                + "";

        try {

            if (Settings.getInstance().debugMode()) {
                System.err.println("[ DEBUG ] Vykonává se SQL: " + sql);
            }

            ResultSet rs = BakaSQL.getInstance().select(sql);

            while (rs.next()) {

                if (Settings.getInstance().debugMode()) {
                    System.err.println("[ DEBUG ] Nalezena data pro záznam " + this.records.get(r).get(EBakaLDAPAttributes.NAME_FIRST.attribute()).toString() + " " + this.records.get(r).get(EBakaLDAPAttributes.NAME_LAST.attribute()).toString() + ": primární zástupce žáka " + rs.getString("INTERN_KOD"));
                }

                return true;
            }

            System.err.println("[ INFO ] Data pro kontakt " + this.records.get(r).get(EBakaLDAPAttributes.NAME_FIRST.attribute()).toString() + " " + this.records.get(r).get(EBakaLDAPAttributes.NAME_LAST.attribute()).toString() + " nebyla v databázi aktivních primárních zákonných zástupců nalezena.");

            if (Settings.getInstance().beVerbose()) {
                System.err.println("[ INFO ] Data pro LDAP záznam kontaktu " + this.records.get(r).get(EBakaLDAPAttributes.NAME_FIRST.attribute()).toString() + " " + this.records.get(r).get(EBakaLDAPAttributes.NAME_LAST.attribute()).toString() + " nebyla nalezena, proběhne automatické vyčištění.");
            }
            return false;

        } catch (Exception e) {

            if (Settings.getInstance().beVerbose()) {
                System.err.println("[ CHYBA ] Nebylo možné provést kontrolu existence záznamu kontaktu v databázi zákonných zástupců žáků.");
            }

            if (Settings.getInstance().debugMode()) {
                System.err.println("[ CHYBA ] " + e.getMessage());
                e.printStackTrace(System.err);
            }
        }

        // fallback pro žádnou činnost
        return false;
    }

    /**
     * Automatické pročištění.
     *
     * @param OU organizační jednotka
     * @param r ID záznamu
     */
    private void cleanUp(String OU, int r) {

        if (OU.contains(Settings.getInstance().getLDAP_baseContacts())) {
            delete(r);
        } else {
            moveToAbs(r);
        }

    }

    /**
     * Smazání kontaktu.
     *
     * @param r ID záznamu
     */
    private void delete(int r) {

        if (App.FLAG_DEVEL) {
            //System.err.println("[ DEVEL ] Mazání záznamu ");
            return;
        }

    }

    /**
     * Přesun žáka mezi absolventy.
     *
     * @param r ID záznamu
     */
    private void moveToAbs(int r) {

        if (App.FLAG_DEVEL) {
            //System.err.println("[ DEVEL ] Přemísťování záznamu ");
            return;
        }

    }

}
