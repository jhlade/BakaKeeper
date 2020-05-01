package cz.zsstudanka.skola.bakakeeper.routines;

import cz.zsstudanka.skola.bakakeeper.components.ReportManager;
import cz.zsstudanka.skola.bakakeeper.connectors.BakaADAuthenticator;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLDAPAttributes;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLogType;
import cz.zsstudanka.skola.bakakeeper.settings.Settings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Kontrola základních struktur nutných pro správný běh prostředí.
 * Do struktury spadají organizační jednotky, skupiny zabezpečení a distribuční seznamy.
 *
 * Základní hierarchie OU byla stanovena projektem ITI:
 *
 * Skola
 * + Skupiny
 *   + Distribucni (vytvořeno dodatečně na ZŠ Pardubice - Studánka)
 *   + Uzivatele
 *   + Zaci
 * + Uzivatele
 *   + Zaci
 *     + Rocnik-1
 *     ...
 *     + Rocnik-9
 *       + Trida-A
 *       + Trida-B
 *       + Trida-C
 *       + Trida-D
 *       + Trida-E
 *     + StudiumUkonceno (vytvořeno dodatečně na ZŠ Pardubice - Studánka)
 *       + 2018 (vytvářeno automaticky)
 *       ...
 *       + 2020
 *   + Zamestnanci
 *     + Ucitele
 *     + Vedeni
 * + Kontakty (vytvořeno dodatečně na ZŠ Pardubice - Studánka)
 *
 *
 *
 * @author Jan Hladěna
 */
public class Structure {

    // řetězcové literály logických hodnot pro LDAP
    static final String LIT_TRUE  = "TRUE";
    static final String LIT_FALSE = "FALSE";

    /**
     * Kontrola základní hierarchické struktury OU a skupin v Active Directory.
     * V případě nalezení chyb bude voliteně proveden pokus o opravu.
     *
     * @param repair pokusit se provést automatickou opravu nebo rekonstrukci struktury
     * @return celkový výsledek kontroly struktury
     */
    public static Boolean checkAndRepairADStructure(Boolean repair) {

        // ukazatel celkového průběhu
        ArrayList<Boolean> results = new ArrayList<Boolean>();

        // příprava

        // vygenerování definic/pořadí OU za běhu
        // Tvar: 0 OU, 1 info
        ArrayList<String[]> ous = new ArrayList<>();
        ous.add(new String[]{Settings.getInstance().getLDAP_baseFaculty(), "Zaměstnanci (globální)"});
        ous.add(new String[]{Settings.getInstance().getLDAP_baseStudents(), "Žáci (globální)"});
        for (int r = 1; r <= 9; r ++) {
            ous.add(new String[]{"OU=Rocnik-" + r + "," + Settings.getInstance().getLDAP_baseStudents(), "Žáci " + r + ". ročníku"});
            for (char t = 'A'; t <= 'E'; t++) {
                ous.add(new String[]{"OU=Trida-" + t + ",OU=Rocnik-" + r + "," + Settings.getInstance().getLDAP_baseStudents(), "Žáci třídy " + r + "." + t});
            }
        }
        ous.add(new String[]{Settings.getInstance().getLDAP_baseAlumni(), "Vyřazení žáci"});
        ous.add(new String[]{Settings.getInstance().getLDAP_baseStudentGroups(), "Skupiny žáků (O365)"});
        ous.add(new String[]{Settings.getInstance().getLDAP_baseGlobalGroups(), "Globální skupiny uživatelů"});
        ous.add(new String[]{Settings.getInstance().getLDAP_baseContacts(), "Kontakty (O365)"});
        ous.add(new String[]{Settings.getInstance().getLDAP_baseDL(), "Distribuční seznamy (O365)"});

        // vygenerování definic/pořadí skupin za běhu
        // Tvar:
        // 0 cn,
        // 1 (plné dn) nadřazená skupina (prázdná - nutné, null - neprobíhá ověřování),
        // 2 mail,
        // 3 displayName,
        // 4 OU,
        // 5 hide (skrytí msExchg pro GAL),
        // 6 auth (vyžadovat autentizaci - lze zasílat pouze z interních adres),
        // 7 groupType (security, distribution),
        // 8 description
        ArrayList<String[]> groups = new ArrayList<>();
        // hierarchie žáků a distribuční seznamy
        groups.add(new String[]{"Zaci-Vsichni", "CN=" + "Skupina-Zaci" + "," + Settings.getInstance().getLDAP_baseGlobalGroups(), "zaci@" + Settings.getInstance().getMailDomain(), "Všichni žáci školy", Settings.getInstance().getLDAP_baseStudentGroups(), LIT_TRUE, LIT_TRUE, EBakaLDAPAttributes.GT_SECURITY.value(), "Všichni žáci školy"});
        groups.add(new String[]{"Rodice-Vsichni", null, "rodice-cela-skola@" + Settings.getInstance().getMailDomain(), "Všichni rodiče", Settings.getInstance().getLDAP_baseDL(), LIT_TRUE, LIT_TRUE, EBakaLDAPAttributes.GT_DISTRIBUTION.value(), "Všichni rodiče všech žáků"});
        groups.add(new String[]{"Ucitele-Tridni", null, "ucitele-tridni@" + Settings.getInstance().getMailDomain(), "Všichni třídní učitelé", Settings.getInstance().getLDAP_baseDL(), LIT_TRUE, LIT_TRUE, EBakaLDAPAttributes.GT_DISTRIBUTION.value(), "Všichni třídní učitelé"});
        // stupně
        for (int s = 1; s <= 2; s++) {
            groups.add(new String[]{"Zaci-Stupen-" + s, "CN=" + "Zaci-Vsichni" + "," + Settings.getInstance().getLDAP_baseStudentGroups(), "zaci-stupen-" + s + "@" + Settings.getInstance().getMailDomain(), "Žáci - " + s + ". stupeň", Settings.getInstance().getLDAP_baseStudentGroups(), LIT_TRUE, LIT_TRUE, EBakaLDAPAttributes.GT_SECURITY.value(), "Žáci - " + s + ". stupeň"});
            groups.add(new String[]{"Rodice-Stupen-" + s, "CN=" + "Rodice-Vsichni" + "," + Settings.getInstance().getLDAP_baseDL(), "rodice-stupen-" + s + "@" + Settings.getInstance().getMailDomain(), "Rodiče žáků " + s + ". stupně", Settings.getInstance().getLDAP_baseDL(), LIT_TRUE, LIT_TRUE, EBakaLDAPAttributes.GT_DISTRIBUTION.value(), "Rodiče žáků - " + s + ". stupeň"});
            groups.add(new String[]{"Ucitele-Tridni-Stupen-" + s, "CN=" + "Ucitele-Tridni" + "," + Settings.getInstance().getLDAP_baseDL(), "tridni-stupen-" + s + "@" + Settings.getInstance().getMailDomain(), "Třídní učitelé " + s + ". stupně", Settings.getInstance().getLDAP_baseDL(), LIT_TRUE, LIT_TRUE, EBakaLDAPAttributes.GT_DISTRIBUTION.value(), "Třídní učitelé - " + s + ". stupeň"});
        }
        // ročníky
        for (int r = 1; r <= 9; r++) {
            groups.add(new String[]{"Rodice-Rocnik-" + r, "CN=" + ((r <= 5) ? "Rodice-Stupen-1" : "Rodice-Stupen-2") + "," + Settings.getInstance().getLDAP_baseDL(), "rodice-rocnik-" + r + "@" + Settings.getInstance().getMailDomain(), "Rodiče žáků " + r + ". ročníku", Settings.getInstance().getLDAP_baseDL(), LIT_TRUE, LIT_TRUE, EBakaLDAPAttributes.GT_DISTRIBUTION.value(), "Rodiče žáků " + r + ". ročníku"});
            groups.add(new String[]{"Ucitele-Tridni-Rocnik-" + r, "CN=" + ((r <= 5) ? "Ucitele-Tridni-Stupen-1" : "Ucitele-Tridni-Stupen-2") + "," + Settings.getInstance().getLDAP_baseDL(), "tridni-rocnik-" + r + "@" + Settings.getInstance().getMailDomain(), "Třídní učitelé " + r + ". ročníku", Settings.getInstance().getLDAP_baseDL(), LIT_TRUE, LIT_TRUE, EBakaLDAPAttributes.GT_DISTRIBUTION.value(), "Třídní učitelé " + r + ". ročníku"});
            groups.add(new String[]{"Zaci-Rocnik-" + r, "CN=" + ((r <= 5) ? "Zaci-Stupen-1" : "Zaci-Stupen-2") + "," + Settings.getInstance().getLDAP_baseStudentGroups(), "zaci-rocnik-" + r + "@" + Settings.getInstance().getMailDomain(), "Žáci - " + r + ". ročník", Settings.getInstance().getLDAP_baseStudentGroups(), LIT_FALSE, LIT_TRUE, EBakaLDAPAttributes.GT_SECURITY.value(), "Žáci - " + r + ". ročníku"});
            // jednotlivé třídy
            for (char t = 'A'; t <= 'E'; t++) {
                groups.add(new String[]{"Rodice-Trida-" + r + t, "CN=" + "Rodice-Rocnik-" + r + "," + Settings.getInstance().getLDAP_baseDL(), ("rodice-trida-" + r + t + "@" + Settings.getInstance().getMailDomain()).toLowerCase(), "Rodiče " + r + "." + t, Settings.getInstance().getLDAP_baseDL(), LIT_TRUE, LIT_TRUE, EBakaLDAPAttributes.GT_DISTRIBUTION.value(), "Rodiče žáků třídy " + r + "." + t});
                groups.add(new String[]{"Ucitele-Tridni-" + r + t, "CN=" + "Ucitele-Tridni-Rocnik-" + r + "," + Settings.getInstance().getLDAP_baseDL(), ("tridni-" + r + t + "@" + Settings.getInstance().getMailDomain()).toLowerCase(), "Třídní učitel " + r + "." + t, Settings.getInstance().getLDAP_baseDL(), LIT_TRUE, LIT_TRUE, EBakaLDAPAttributes.GT_DISTRIBUTION.value(), "Třídní učitel " + r + "." + t});
                groups.add(new String[]{"Zaci-Trida-" + r + t, "CN=" + "Zaci-Rocnik-" + r + "," + Settings.getInstance().getLDAP_baseStudentGroups(), ("zaci-" + r + t + "@" + Settings.getInstance().getMailDomain()).toLowerCase(), "Žáci " + r + "." + t, Settings.getInstance().getLDAP_baseStudentGroups(), LIT_FALSE, LIT_TRUE, EBakaLDAPAttributes.GT_SECURITY.value(), "Žáci třídy " + r + "." + t});
            }
        }

        // zpracování

        // 0) načtení konfigurace a vytvoření připojení k AD
        Settings.getInstance().load();
        BakaADAuthenticator.getInstance();


        // 1) kontrola hierarchie organizačních jednotek
        for (String[] ou : ous) {

            if (Settings.getInstance().beVerbose()) {
                ReportManager.logWait(EBakaLogType.LOG_TEST, "Kontrola integrity kontejneru objektů " + ou[1]);
            }

            int check_ou = BakaADAuthenticator.getInstance().checkOU(ou[0]);

            if (check_ou >= 0) {

                if (Settings.getInstance().beVerbose()) {
                    ReportManager.log(EBakaLogType.LOG_OK, ((Settings.getInstance().debugMode()) ? "[ celkem " + check_ou + " položek ]" : ""));
                }

                results.add(true);

            } else {

                if (Settings.getInstance().beVerbose()) {
                    ReportManager.logResult(EBakaLogType.LOG_ERR_VERBOSE);
                }

                results.add(false);

                // Oprava - vytvoření nové OU
                if (repair) {

                    results.remove(results.size() - 1);

                    if (Settings.getInstance().beVerbose()) {
                        ReportManager.logWait(EBakaLogType.LOG_INFO, "Probíhá pokus o opravu");
                    }

                    BakaADAuthenticator.getInstance().createOU(
                            ou[0].split(",")[0],
                            ou[0].replace(ou[0].split(",")[0] + ",", "")
                    );

                    // nová kontrola existence OU
                    int check_ou_rebuild = BakaADAuthenticator.getInstance().checkOU(ou[0]);

                    if (check_ou_rebuild >= 0) {

                        if (Settings.getInstance().beVerbose()) {
                            ReportManager.log(EBakaLogType.LOG_OK, ((Settings.getInstance().debugMode()) ? "[ celkem " + check_ou_rebuild + " položek ]" : ""));
                        }

                        results.add(true);

                    } else {
                        // oprava se nezdařila
                        if (Settings.getInstance().beVerbose()) {
                            ReportManager.logResult(EBakaLogType.LOG_ERR_VERBOSE);
                        }

                        if (Settings.getInstance().beVerbose()) {
                            ReportManager.log(EBakaLogType.LOG_ERR_DEBUG, "Nebylo možné vytvořit organizační jednotku pro objekty " + ou[1] + ".");
                        }

                        results.add(false);
                    }

                } // oprava - konstrukce OU

            }

        } // OU smyčka 1)

        // 2) kontrola hierarchie skupin a distribučních seznamů
        // Tvar:
        // 0 cn,
        // 1 nadřazená skupina (prázdná - nutné, null - neprobíhá ověřování),
        // 2 mail,
        // 3 displayName,
        // 4 OU,
        // 5 hide,
        // 6 auth,
        // 7 groupType,
        // 8 description
        for (String[] gr : groups) {

            if (Settings.getInstance().beVerbose()) {
                ReportManager.logWait(EBakaLogType.LOG_TEST, "Kontrola definované skupiny " + gr[3]);
            }

            // získání objektu
            Map<Integer, Map<String, Object>> info = BakaADAuthenticator.getInstance().getGroupInfo(gr[0], gr[4]);

            // objekt existuje
            if (info != null && info.size() > 0) {

                if (Settings.getInstance().beVerbose()) {
                    ReportManager.logResult(EBakaLogType.LOG_OK);
                }

                results.add(true);

                // subrutina kontroly skupiny
                checkADGroupStructure(gr, repair);

            } else {
                // objekt nebyl v OU nalezen
                if (Settings.getInstance().beVerbose()) {
                    ReportManager.logResult(EBakaLogType.LOG_ERR_VERBOSE);
                }

                results.add(false);

                // oprava - vytvoření nového objektu
                if (repair) {

                    results.remove(results.size() - 1);

                    if (Settings.getInstance().beVerbose()) {
                        ReportManager.logWait(EBakaLogType.LOG_INFO, "Probíhá pokus o opravu");
                    }

                    // parametry dle definic
                    HashMap<String, String> newData = new HashMap<>();
                    newData.put(EBakaLDAPAttributes.GT_GENERAL.attribute(), gr[7]);
                    newData.put(EBakaLDAPAttributes.MAIL.attribute(), gr[2]);
                    newData.put(EBakaLDAPAttributes.NAME_DISPLAY.attribute(), gr[3]);
                    newData.put(EBakaLDAPAttributes.DESCRIPTION.attribute(), gr[8]);
                    newData.put(EBakaLDAPAttributes.MSXCH_GAL_HIDDEN.attribute(), gr[5]);
                    newData.put(EBakaLDAPAttributes.MSXCH_REQ_AUTH.attribute(), gr[6]);

                    // vytvoření skupiny
                    BakaADAuthenticator.getInstance().createGroup(
                            gr[0], // název
                            gr[4], // cílová OU
                            new String[]{gr[1]}, // členství
                            newData // data
                    );

                    // nové ověření existence
                    Map<Integer, Map<String, Object>> inforep = BakaADAuthenticator.getInstance().getGroupInfo(gr[0], gr[4]);

                    if (inforep != null && inforep.size() > 0) {

                        if (Settings.getInstance().beVerbose()) {
                            ReportManager.logResult(EBakaLogType.LOG_OK);
                        }

                        results.add(true);

                    } else {

                        if (Settings.getInstance().beVerbose()) {
                            ReportManager.logResult(EBakaLogType.LOG_ERR_VERBOSE);
                        }

                        results.add(false);
                    }

                    // oprava - vytvoření nové skupiny
                } else {
                    // neprobíhá oprava - chyba
                    ReportManager.log(EBakaLogType.LOG_ERR, "Definovaná skupina " + gr[3] + " nebyla ve struktuře nalezena.");
                }


            } // ošetření neexistence objektu

        } // GR smyčka 2)

        return (results.contains(false)) ? false : true;
    }

    /**
     * Kontrola a oprava hierarchie skupin a jejich vlastností.
     * Subrutina.
     *
     * @param data pole řetězců (0 cn, 1 nadřazená skupina, 2 mail, 3 displayName, 4 OU, 5 hide, 6 auth, 7 groupType) kontrolované skupiny
     * @param attemptRepair v případě nalezení chyby se pokusit o automatickou opravu
     * @return celkový stav průběhu kontroly nebo opravy
     */
    private static void checkADGroupStructure(String[] data, Boolean attemptRepair) {

        if (Settings.getInstance().beVerbose()) {
            ReportManager.log(EBakaLogType.LOG_INFO, "Probíhá podrobná kontrola atributů definované skupiny CN=" + data[0] + ".");
        }

        Map<Integer, Map<String, Object>> info = BakaADAuthenticator.getInstance().getGroupInfo(data[0], data[4]);


        Map<EBakaLDAPAttributes, String[]> values = new HashMap<>();
        values.put(EBakaLDAPAttributes.MAIL, new String[] {"e-mailové adresy", data[2]});
        values.put(EBakaLDAPAttributes.NAME_DISPLAY, new String[] {"zobrazovaného jména", data[3]});
        values.put(EBakaLDAPAttributes.MSXCH_GAL_HIDDEN, new String[] {"parametru skrytí v GAL", data[5]});
        values.put(EBakaLDAPAttributes.MSXCH_REQ_AUTH, new String[] {"parametru povinné autentizace", data[6]});
        values.put(EBakaLDAPAttributes.GT_GENERAL, new String[] {"typu skupiny", data[7]});
        values.put(EBakaLDAPAttributes.MEMBER_OF, new String[] {"nadřazené skupiny", data[1]});
        values.put(EBakaLDAPAttributes.DESCRIPTION, new String[] {"slovního označení", data[8]});

        // debug
        if (Settings.getInstance().debugMode()) {
            ReportManager.log(EBakaLogType.LOG_DEBUG, "Získaná data skupiny: " + info.get(0).toString());
        }

        for (Map.Entry<EBakaLDAPAttributes, String[]> entry : values.entrySet()) {

            if (Settings.getInstance().beVerbose()) {
                ReportManager.logWait(EBakaLogType.LOG_TEST, "Kontrola " + entry.getValue()[0]);
            }

            // klíč existuje a je shodný
            // NEBO klíč neexistuje a je nulový
            if (
                    (info.get(0).containsKey(entry.getKey().attribute()) && info.get(0).get(entry.getKey().attribute()).toString().equalsIgnoreCase(entry.getValue()[1]))
                    || (!info.get(0).containsKey(entry.getKey().attribute()) && entry.getValue()[1] == (null))
            ) {
                if (Settings.getInstance().beVerbose()) {
                    ReportManager.logResult(EBakaLogType.LOG_OK);
                }
            } else {
                if (Settings.getInstance().beVerbose()) {
                    ReportManager.logResult(EBakaLogType.LOG_ERR_VERBOSE);
                }

                if (Settings.getInstance().debugMode()) {
                    if (!info.get(0).containsKey(entry.getKey().attribute())) {
                        ReportManager.log(EBakaLogType.LOG_DEBUG, "Požadovaný atribut skupiny neexistuje.");
                    } else {
                        ReportManager.log(EBakaLogType.LOG_DEBUG, "(" + entry.getKey().attribute() + ") Očekáváno: [" + entry.getValue()[1] + "], Získáno: [" + info.get(0).get(entry.getKey().attribute()) + "]");
                    }
                }

                // oprava
                if (attemptRepair) {

                    if (Settings.getInstance().beVerbose()) {
                        ReportManager.logWait(EBakaLogType.LOG_INFO, "Probíhá pokus o opravu");
                    }

                    // cokoliv mimo nadřazené skupiny
                    if (!entry.getKey().equals(EBakaLDAPAttributes.MEMBER_OF)) {
                        BakaADAuthenticator.getInstance().setGroupInfo(data[4], data[0], entry.getKey(), entry.getValue()[1]);
                    } else {
                        // oprava nadřazené skupiny

                        // současné skupiny
                        ArrayList<String> currentGroups = BakaADAuthenticator.getInstance().listMembership(info.get(0).get(EBakaLDAPAttributes.DN.attribute()).toString());

                        // odebrání ze všech skupin
                        if (currentGroups.size() > 0) {
                            Iterator<String> cg = currentGroups.iterator();
                            while (cg.hasNext()) {
                                BakaADAuthenticator.getInstance().removeObjectFromGroup(info.get(0).get(EBakaLDAPAttributes.DN.attribute()).toString(), cg.next());
                            }
                        }

                        // přidání do patřičné (jedné) skupiny
                        if (data[1] != null) {
                            BakaADAuthenticator.getInstance().addObjectToGroup(info.get(0).get(EBakaLDAPAttributes.DN.attribute()).toString(), data[1]);
                        }
                    }

                    // opětovná kontrola po provedení operace
                    Map<Integer, Map<String, Object>> info_rep = BakaADAuthenticator.getInstance().getGroupInfo(data[0], data[4]);

                    if (info_rep.get(0).containsKey(entry.getKey().attribute()) && info_rep.get(0).get(entry.getKey().attribute()).toString().equalsIgnoreCase(entry.getValue()[1])) {
                        if (Settings.getInstance().beVerbose()) {
                            ReportManager.logResult(EBakaLogType.LOG_OK);
                        }
                    } else {
                        if (Settings.getInstance().beVerbose()) {
                            ReportManager.logResult(EBakaLogType.LOG_ERR_VERBOSE);
                        }
                    }

                }
            }

        }

    }

}
