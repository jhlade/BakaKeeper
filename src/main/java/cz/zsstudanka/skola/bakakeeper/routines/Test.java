package cz.zsstudanka.skola.bakakeeper.routines;

import cz.zsstudanka.skola.bakakeeper.components.ReportManager;
import cz.zsstudanka.skola.bakakeeper.connectors.BakaADAuthenticator;
import cz.zsstudanka.skola.bakakeeper.connectors.BakaMailer;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLDAPAttributes;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaSQL;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaUAC;
import cz.zsstudanka.skola.bakakeeper.model.collections.LDAPrecords;
import cz.zsstudanka.skola.bakakeeper.model.collections.SQLrecords;
import cz.zsstudanka.skola.bakakeeper.model.entities.DataLDAP;
import cz.zsstudanka.skola.bakakeeper.settings.Settings;
import cz.zsstudanka.skola.bakakeeper.settings.Version;
import cz.zsstudanka.skola.bakakeeper.utils.BakaUtils;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.*;

public class Test {

    // 2020-08-31 práce se třídnictvím
    public static void test_09() {
        System.out.println("====== [ TEST 2020-08-31 SQL definice třídnictví ] ======");

        SQLrecords třídníUčitelé = new SQLrecords(true);

        System.out.println("Bylo nalezeno " + třídníUčitelé.count() + " třídních učitelů.");

        while (třídníUčitelé.iterator().hasNext()) {
            Map<String, String> učitel = třídníUčitelé.get(třídníUčitelé.iterator().next());

            String třída = učitel.get(EBakaSQL.F_CLASS_LABEL.basename());
            String příjmení = učitel.get(EBakaSQL.F_FAC_SURNAME.basename());
            String jméno = učitel.get(EBakaSQL.F_FAC_GIVENNAME.basename());
            String email = učitel.get(EBakaSQL.F_FAC_EMAIL.basename());

            System.out.println(třída + ":\t" + příjmení + " " + jméno + "\t\t("+email+")");
        }

        System.out.println("====== [ / TEST 2020-08-31 SQL definice třídnictví ] ======");

        System.out.println("\n====== [ TEST 2020-08-31 SQL definice aktivních učitelů ] ======");

        SQLrecords všichniUčitelé = new SQLrecords(false);

        System.out.println("Letos je aktivních " + všichniUčitelé.count() + " učitelů.");

        while (všichniUčitelé.iterator().hasNext()) {
            Map<String, String> učitel = všichniUčitelé.get(všichniUčitelé.iterator().next());

            //System.out.println("DUMP " + učitel.toString());

            String třída = (učitel.containsKey(EBakaSQL.F_CLASS_LABEL.basename())) ? učitel.get(EBakaSQL.F_CLASS_LABEL.basename()) : "netřídní";
            String příjmení = (učitel.containsKey(EBakaSQL.F_FAC_SURNAME.basename())) ? učitel.get(EBakaSQL.F_FAC_SURNAME.basename()) : "??? bez příjmení ???";
            String jméno = (učitel.containsKey(EBakaSQL.F_FAC_GIVENNAME.basename())) ? učitel.get(EBakaSQL.F_FAC_GIVENNAME.basename()) : "??? bze jména ???";
            String email = (učitel.containsKey(EBakaSQL.F_FAC_EMAIL.basename())) ? učitel.get(EBakaSQL.F_FAC_EMAIL.basename()) : "NULL";

            System.out.println(třída + ":\t" + příjmení + " " + jméno + "\t\t("+ email +")");
        }


        System.out.println("\n====== [ / TEST 2020-08-31 SQL definice aktivních učitelů ] ======");
    }

    // sestavy pro 2020/2021
    public static void test_08() {
        System.out.println("====== [ TEST 2020-08-31 RESET HESEL 1. ročníku ] ======");

        // třídní učitelé - manuální seznam
        Map<String, String> třídní = new HashMap<>();
        třídní.put("1.A", "Burgetová Lada");
        třídní.put("1.B", "Patlevičová Marcela");
        třídní.put("1.C", "Sedláková Jana");
        třídní.put("1.D", "Burgetová Martina");

        Map<String, String> sestavy = new HashMap<>();

        for (int ročník = 1; ročník <= 1; ročník++) {
            System.out.println("====== [ ROČNÍK " + ročník + " ] ======");

            for (char třída = 'A'; třída <= 'D'; třída++) {

                String literálTřídy = ročník + "." + třída;

                if (!třídní.containsKey(literálTřídy)) {
                    continue;
                }

                System.out.println("====== [ TŘÍDA " + literálTřídy + " ] ======");

                // SQL data
                SQLrecords dataTřídy = new SQLrecords(ročník, String.valueOf(třída));
                System.out.println("[i] Načteno " + dataTřídy.count() + " položek z evidence.");

                // LDAP data
                LDAPrecords dataAD = new LDAPrecords("OU=Trida-"+String.valueOf(třída)+",OU=Rocnik-"+String.valueOf(ročník)+"," + Settings.getInstance().getLDAP_baseStudents(), EBakaLDAPAttributes.OC_USER);
                System.out.println("[i] Načteno " + dataAD.count() + " položek z domény.");

                String třídníUčitel = třídní.get(literálTřídy);
                System.out.println("Třídní učitel: " + třídníUčitel);

                // tvorba sestavy
                StringBuilder sestava = new StringBuilder();
                sestava.append("\\documentclass[10pt]{article}\n\n");
                sestava.append("\\usepackage[czech]{babel}\n");
                sestava.append("\\usepackage[utf8]{inputenc}\n");
                sestava.append("\\usepackage{tabularx}\n\n");
                sestava.append("\\usepackage{geometry}\n");
                sestava.append("\\geometry{a4paper,total={170mm,257mm},left=20mm,top=20mm}\n");
                sestava.append("\\def\\arraystretch{1.5}%\n\n");
                sestava.append("\\usepackage{fancyhdr}\n");
                sestava.append("\\pagestyle{fancy}\n\n");
                sestava.append("\\begin{document}");

                sestava.append("\\fancyhf{}\n");
                sestava.append("\\lhead{" + literálTřídy + "}\n");
                sestava.append("\\rhead{" + třídníUčitel + "}\n");
                sestava.append("\\lfoot{" + Settings.getInstance().systemInfoTag() + "}");
                sestava.append("\\rfoot{" + Version.getInstance().getTag() + "}");

                sestava.append("\\noindent\n");
                sestava.append("\\Large{Třída " + literálTřídy + "}\n\n");
                sestava.append("\\begin{table}[htbp]\n\n");
                sestava.append("\\centering\n");
                sestava.append("\\begin{tabularx}{\\textwidth}{| c | X | X | r | c |}\n");
                sestava.append("\\hline\n");
                sestava.append("\\bf{Č.\\,tř.\\,výk} & \\bf{Příjmení} & \\bf{Jméno} & \\bf{UPN}  & \\bf{Heslo}  \\\\ \\hline \\hline\n");

                String template = "__CLASS_ID__ & __SURNAME__ & __GIVENNAME__ & \\texttt{__UPN__} & \\texttt{__PWD__} \\\\ \\hline\n";

                // získání dat - iterace nad evidencí
                while (dataTřídy.iterator().hasNext()) {

                    Map<String, String> žák = dataTřídy.get(dataTřídy.iterator().next());

                    // data
                    Integer číslo = Integer.parseInt(žák.get(EBakaSQL.F_STU_CLASS_ID.basename()));
                    String příjmení = žák.get(EBakaSQL.F_STU_SURNAME.basename());
                    String jméno = žák.get(EBakaSQL.F_STU_GIVENNAME.basename());
                    String upn = žák.get(EBakaSQL.F_STU_MAIL.basename());
                    String heslo = BakaUtils.createInitialPassword(příjmení, jméno, číslo);

                    // práce na heslech
                    System.out.println("[ " + žák.get(EBakaSQL.F_STU_ID.basename()) + " ] " + příjmení + " " + jméno);
                    String dnŽáka = dataAD.get(upn).get(EBakaLDAPAttributes.DN.attribute()).toString();
                    System.out.println(dnŽáka);

                    //BakaADAuthenticator.getInstance().replaceAttribute(dnŽáka, EBakaLDAPAttributes.PW_UNICODE, heslo);
                    //BakaADAuthenticator.getInstance().replaceAttribute(dnŽáka, EBakaLDAPAttributes.PW_LASTSET, EBakaLDAPAttributes.PW_LASTSET.value());

                    // zápis do tabulky v sestavě
                    sestava.append(template
                            .replace("__CLASS_ID__", číslo.toString())
                            .replace("__SURNAME__", příjmení)
                            .replace("__GIVENNAME__", jméno)
                            .replace("__UPN__", upn)
                            .replace("__PWD__", heslo)
                    );
                }

                sestava.append("\\end{tabularx}\n\n");
                sestava.append("\\end{table}\n\n");
                sestava.append("\\noindent\n");
                sestava.append("UPN = \\textit{User Principal Name}, slouží jako přihlašovací jméno do~služeb\n");
                sestava.append("Office~365 a~zároveň jako platný tvar e-mailové adresy.\\par\n");
                sestava.append("~\\par\n\n");
                sestava.append("\\noindent\n");
                sestava.append("Žáci si mohou své heslo sami změnit na~portálu https://heslo.zs-studanka.cz.\\par\n\n");
                sestava.append("\\end{document}\n");

                sestavy.put(literálTřídy, sestava.toString());

                System.out.println("====== [ /TŘÍDA " + literálTřídy + " ] ======\n");
            }

            System.out.println("====== [ /ROČNÍK " + ročník + " ] ======\n");
        }

        System.out.println("====== [ Ukládání sestav ] ======");

        Iterator<String> tex = sestavy.keySet().iterator();
        while (tex.hasNext()) {

            String třída = tex.next();

            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
            Date date = new Date();

            String soubor = "./reports/" + formatter.format(date)
                    + "_" + třída.toLowerCase().replace(".", "")
                    + ".tex";

            try (PrintStream out = new PrintStream(new FileOutputStream(soubor))) {
                out.print(sestavy.get(třída));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }

            // pdf lualatex
            try {
                Process p = Runtime.getRuntime().exec("/usr/bin/lualatex " + soubor);

                System.out.println("Čekání na LuaLaTeX ...");
                p.waitFor();
                System.out.println("PDF hotovo.");

            } catch (Exception x) {
                System.out.println("Proces pro LuaLaTeX se nespustil.");
                x.printStackTrace(System.err);
            }


            String zpráva = "V příloze naleznete sestavu s novými přístupovými údaji žáků " +
                    třída + " pro použití v prostředí Office365. Všichni žáci mají přiřazené " +
                    "odpovídající žákovské licence, mohou tedy ihned plně používat všechny cloudové služby O365.\n\n" +
                    "Tuto sestavu považujte za důvěrnou a distribuci " +
                    "hesel, pokud to bude možné, provádějte jednotlivě.\n\n";

            String[] třídníSplit = třídní.get(třída).split(" ");
            String mailTřídního = BakaUtils.removeAccents(třídníSplit[1]).toLowerCase()
                    + "."
                    + BakaUtils.removeAccents(třídníSplit[0]).toLowerCase()
                    + "@zs-studanka.cz";

            System.out.println("Příjemce: " + mailTřídního);


            BakaMailer.getInstance().mail(new String[]{mailTřídního, "ict@zs-studanka.cz"},
            //BakaMailer.getInstance().mail(new String[]{"ict@zs-studanka.cz"},
            //BakaMailer.getInstance().mail(new String[]{"jan.hladena@zs-studanka.cz"},
                    "Přístupové údaje žáků " + třída, zpráva,
                    new String[]{"./" + BakaUtils.fileBaseName(soubor.replace(".tex", ".pdf"))});

        }

        System.out.println("====== [ / TEST 2020-08-31 RESET HESEL 1. ročníku ] ======");
    }

    // 2020-08-25 webový přístup
    public static void test_07() {

    }

    // 2020-05-12 příprava na RM
    public static void test_06() {

    }

    public static void test_04() {

        // logování a reportování
        /*
        if (true) {
            ReportManager.log("Všechno ok.");
        } else {
            ReportManager.log(EBakaLogType.LOG_ERR, "Všechno špatně.");
        }

        Integer dělenec = 5;
        Integer dělitel = 0;

        try {
            Integer výsledek = dělenec / dělitel;
        } catch (Exception e) {
            ReportManager.printStackTrace(e);
        }*/

        // 2020-04-27
        Sync testSync = new Sync();

        //testSync.actionInit(true);
        //testSync.actionCheck(true);
        //testSync.checkData(true);
        testSync.syncGuardian(true);

        // odeslání výsledku
        ReportManager.getInstance().report();
    }

    // 2020-05-02 korekce rutiny opravy struktury + restrukturalizace DL
    public static void test_05() {
        Structure.checkAndRepairADStructure(true);
    }

    /**
     *  Dílčí úkol 3 - reset hesel všech žáků + sestavy
     *  zahájeno 2020-04-23 01:19
     *  dokončeno 2020-04-23 03:35
     *
     *
     */
    public static void test_02() {
        System.out.println("====== [ TEST 2020-04-23 RESET HESEL ] ======");

        // načtení evidence po jednotlivých třídách

        // třídní učitelé - manuální seznam
        Map<String, String> třídní = new HashMap<>();
        třídní.put("3.A", "Petrusová Kateřina");
        třídní.put("3.B", "Žáčková Martina");
        třídní.put("3.C", "Štěpánková Zuzana");
        třídní.put("4.C", "Trojánková Ilona");
        třídní.put("5.D", "Burgetová Martina");
        třídní.put("5.B", "Patlevičová Marcela");
        třídní.put("2.B", "Rybářová Jitka");
        třídní.put("2.C", "Košvancová Ludmila");
        třídní.put("4.A", "Oplatková Jaroslava");
        třídní.put("4.B", "Pospíšil Pavel");
        třídní.put("5.C", "Kuhnová Karolina");
        třídní.put("5.A", "Burgetová Lada");
        třídní.put("6.A", "Kárská Jitka");
        třídní.put("6.B", "Jílková Ilona");
        třídní.put("6.C", "Kovářová Lucie");
        třídní.put("8.A", "Fantová Monika"); // 2020-05-03 původní mail nedorazil // monika.fantova
        třídní.put("7.B", "Smotlachová Petra");
        třídní.put("8.B", "Zářecký Tomáš");
        třídní.put("9.A", "Nejedlá Ivana"); // 2020-05-03 původní mail nedorazil - překlep
        třídní.put("7.C", "Vilímová Iva");
        třídní.put("9.C", "Beranová Karla");
        třídní.put("7.A", "Antlová Petra");
        třídní.put("6.D", "Kaplan Miloš");
        třídní.put("2.A", "Řípová Zuzana");
        třídní.put("1.A", "Březinová Monika");
        třídní.put("1.D", "Sedláčková Lucie");
        třídní.put("1.B", "Štefková Alena");
        třídní.put("1.C", "Šmídová Hana");
        třídní.put("8.C", "Jarošová Soňa");
        třídní.put("9.B", "Hojková Andrea");

        Map<String, String> sestavy = new HashMap<>();

        // výjimky - 5.B, 4.A, 2C
        Map<String, Boolean> výjimky = new HashMap<>();
        výjimky.put("2.C", true);
        výjimky.put("4.A", true);
        výjimky.put("5.B", true);

        for (int ročník = 1; ročník <= 9; ročník++) {
            System.out.println("====== [ ROČNÍK " + ročník + " ] ======");

            for (char třída = 'A'; třída <= 'D'; třída++) {

                String literálTřídy = ročník + "." + třída;

                if (!třídní.containsKey(literálTřídy)) {
                    continue;
                }

                if (výjimky.containsKey(literálTřídy)) {
                    System.out.println("***** Výjimka pro třídu " + literálTřídy + " *****");
                    continue;
                }
                System.out.println("====== [ TŘÍDA " + literálTřídy + " ] ======");

                // SQL data
                SQLrecords dataTřídy = new SQLrecords(ročník, String.valueOf(třída));
                System.out.println("[i] Načteno " + dataTřídy.count() + " položek z evidence.");

                // LDAP data
                LDAPrecords dataAD = new LDAPrecords("OU=Trida-"+String.valueOf(třída)+",OU=Rocnik-"+String.valueOf(ročník)+"," + Settings.getInstance().getLDAP_baseStudents(), EBakaLDAPAttributes.OC_USER);
                System.out.println("[i] Načteno " + dataAD.count() + " položek z domény.");

                String třídníUčitel = třídní.get(literálTřídy);
                System.out.println("Třídní učitel: " + třídníUčitel);

                // tvorba sestavy
                StringBuilder sestava = new StringBuilder();
                sestava.append("\\documentclass[10pt]{article}\n\n");
                sestava.append("\\usepackage[czech]{babel}\n");
                sestava.append("\\usepackage[utf8]{inputenc}\n");
                sestava.append("\\usepackage{tabularx}\n\n");
                sestava.append("\\usepackage{geometry}\n");
                sestava.append("\\geometry{a4paper,total={170mm,257mm},left=20mm,top=20mm}\n");
                sestava.append("\\def\\arraystretch{1.5}%\n\n");
                sestava.append("\\usepackage{fancyhdr}\n");
                sestava.append("\\pagestyle{fancy}\n\n");
                sestava.append("\\begin{document}");

                sestava.append("\\fancyhf{}\n");
                sestava.append("\\lhead{" + literálTřídy + "}\n");
                sestava.append("\\rhead{" + třídníUčitel + "}\n");
                sestava.append("\\lfoot{" + Settings.getInstance().systemInfoTag() + "}");
                sestava.append("\\rfoot{" + Version.getInstance().getTag() + "}");

                sestava.append("\\noindent\n");
                sestava.append("\\Large{Třída " + literálTřídy + "}\n\n");
                sestava.append("\\begin{table}[htbp]\n\n");
                sestava.append("\\centering\n");
                sestava.append("\\begin{tabularx}{\\textwidth}{| c | X | X | r | c |}\n");
                sestava.append("\\hline\n");
                sestava.append("\\bf{Č.\\,tř.\\,výk} & \\bf{Příjmení} & \\bf{Jméno} & \\bf{UPN}  & \\bf{Heslo}  \\\\ \\hline \\hline\n");

                String template = "__CLASS_ID__ & __SURNAME__ & __GIVENNAME__ & \\texttt{__UPN__} & \\texttt{__PWD__} \\\\ \\hline\n";

                // získání dat - iterace nad evidencí
                while (dataTřídy.iterator().hasNext()) {

                    Map<String, String> žák = dataTřídy.get(dataTřídy.iterator().next());

                    // data
                    Integer číslo = Integer.parseInt(žák.get(EBakaSQL.F_STU_CLASS_ID.basename()));
                    String příjmení = žák.get(EBakaSQL.F_STU_SURNAME.basename());
                    String jméno = žák.get(EBakaSQL.F_STU_GIVENNAME.basename());
                    String upn = žák.get(EBakaSQL.F_STU_MAIL.basename());
                    String heslo = BakaUtils.createInitialPassword(příjmení, jméno, číslo);

                    // práce na heslech
                    System.out.println("[ " + žák.get(EBakaSQL.F_STU_ID.basename()) + " ] " + příjmení + " " + jméno);
                    String dnŽáka = dataAD.get(upn).get(EBakaLDAPAttributes.DN.attribute()).toString();
                    System.out.println(dnŽáka);

                    //BakaADAuthenticator.getInstance().replaceAttribute(dnŽáka, EBakaLDAPAttributes.PW_UNICODE, heslo);
                    //BakaADAuthenticator.getInstance().replaceAttribute(dnŽáka, EBakaLDAPAttributes.PW_LASTSET, EBakaLDAPAttributes.PW_LASTSET.value());

                    // zápis do tabulky v sestavě
                    sestava.append(template
                            .replace("__CLASS_ID__", číslo.toString())
                            .replace("__SURNAME__", příjmení)
                            .replace("__GIVENNAME__", jméno)
                            .replace("__UPN__", upn)
                            .replace("__PWD__", heslo)
                    );
                }

                sestava.append("\\end{tabularx}\n\n");
                sestava.append("\\end{table}\n\n");
                sestava.append("\\noindent\n");
                sestava.append("UPN = \\textit{User Principal Name}, slouží jako přihlašovací jméno do~služeb\n");
                sestava.append("Office~365 a~zároveň jako platný tvar e-mailové adresy.\\par\n");
                sestava.append("~\\par\n\n");
                sestava.append("\\noindent\n");
                sestava.append("Žáci si mohou své heslo sami změnit na~portálu https://heslo.zs-studanka.cz.\\par\n\n");
                sestava.append("\\end{document}\n");

                sestavy.put(literálTřídy, sestava.toString());

                System.out.println("====== [ /TŘÍDA " + literálTřídy + " ] ======\n");
            }

            System.out.println("====== [ /ROČNÍK " + ročník + " ] ======\n");
        }

        System.out.println("====== [ Ukládání sestav ] ======");

        Iterator<String> tex = sestavy.keySet().iterator();
        while (tex.hasNext()) {

            String třída = tex.next();

            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
            Date date = new Date();

            String soubor = "./reports/" + formatter.format(date)
                    + "_" + třída.toLowerCase().replace(".", "")
                    + ".tex";

            try (PrintStream out = new PrintStream(new FileOutputStream(soubor))) {
                out.print(sestavy.get(třída));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }

            // pdf lualatex
            try {
                Process p = Runtime.getRuntime().exec("/usr/bin/lualatex " + soubor);
            } catch (Exception x) {
                x.printStackTrace(System.err);
            }


            String zpráva = "V příloze naleznete sestavu s resetovanými přístupovými údaji žáků " +
                    třída + " pro použití v prostředí Office365. Všichni žáci mají přiřazené " +
                    "odpovídající žákovské licence, mohou tedy ihned plně používat Teams.\n\n" +
                    "Sestavu považujte za důvěrnou a distribuci " +
                    "hesel, pokud to bude možné, provádějte jednotlivě.\n\nVelmi se omlouvám za " +
                    "několikadenní zdržení proti původnímu plánu, ale školní evidence nebyla v nejlepším " +
                    "stavu pro tuto akci.\n\nH.";

            String[] třídníSplit = třídní.get(třída).split(" ");
            String mailTřídního = BakaUtils.removeAccents(třídníSplit[1]).toLowerCase()
                    + "."
                    + BakaUtils.removeAccents(třídníSplit[0]).toLowerCase()
                    + "@zs-studanka.cz";

            System.out.println("Příjemce: " + mailTřídního);


            //BakaMailer.getInstance().mail(new String[]{mailTřídního, "ict@zs-studanka.cz"},
            BakaMailer.getInstance().mail(new String[]{"ict@zs-studanka.cz"},
                    "Přístupové údaje žáků " + třída, zpráva,
                    new String[]{"./" + BakaUtils.fileBaseName(soubor.replace(".tex", ".pdf"))});

        }

        System.out.println("====== [ /TEST 2020-04-23 RESET HESEL ] =====");
    }

    // dokončeno 2020-04-23 01:17 - synchronizační rutina, PoC
    public static void test_01() {
        System.out.println("====== [ TEST ] ======");

        /*
        String záznamDN = "CN=Aaadam Expirovaný,OU=Trida-E,OU=Rocnik-1,OU=Zaci,OU=Uzivatele,OU=Skola,DC=zsstu,DC=local";
        String vyřazenáOU = "OU=2020," + Settings.getInstance().getLDAP_baseAlumni();

        //BakaADAuthenticator.getInstance().moveObject(záznamDN, vyřazenáOU, true);

        BakaADAuthenticator.getInstance().createOU("Test 1999", Settings.getInstance().getLDAP_baseAlumni());
         */

        /*
        String testovacíOU = "OU=2020," + Settings.getInstance().getLDAP_baseAlumni();
        String cn = "Malý Kocour";
        String dn = "cn=" + cn + "," + testovacíOU;

        // záleží na pořadí!
        Map<String, String> dataUživatele = new HashMap();
        dataUživatele.put(EBakaLDAPAttributes.CN.attribute(), cn);
        dataUživatele.put(EBakaLDAPAttributes.NAME_LAST.attribute(), "Malý");
        dataUživatele.put(EBakaLDAPAttributes.NAME_FIRST.attribute(), "Kocour");
        dataUživatele.put(EBakaLDAPAttributes.NAME_DISPLAY.attribute(), "Malý Kocour");

        dataUživatele.put(EBakaLDAPAttributes.MAIL.attribute(), "maly.kocour@zs-studanka.cz");
        dataUživatele.put(EBakaLDAPAttributes.UPN.attribute(), BakaUtils.createUPNfromName("Malý", "Kocour", "zs-studanka.cz"));
        dataUživatele.put(EBakaLDAPAttributes.UID.attribute(), BakaUtils.createUPNfromName("Malý", "Kocour", "zs-studanka.cz"));
        dataUživatele.put(EBakaLDAPAttributes.LOGIN.attribute(), BakaUtils.createSAMloginFromName("Malý", "Kocour"));
        dataUživatele.put(EBakaLDAPAttributes.TITLE.attribute(), "Testovací účet");
        dataUživatele.put(EBakaLDAPAttributes.EXT01.attribute(), "(dummy INTERN_KOD)");

        dataUživatele.put(EBakaLDAPAttributes.PW_UNICODE.attribute(), "PekloZlo-053");
        dataUživatele.put(EBakaLDAPAttributes.UAC.attribute(), EBakaUAC.NORMAL_ACCOUNT.toString());

        BakaADAuthenticator.getInstance().createNewUser(cn, testovacíOU, dataUživatele);

        System.out.println("== SKUPINY ==");
        BakaADAuthenticator.getInstance().addObjectToGroup(dn, "cn=Skupina-Zaci," + Settings.getInstance().getLDAP_baseGlobalGroups());

        // 2020-04-22 17:47
        if (true) {
            return;
        }
        */

        String pismeno = null;//"C";
        String rocnik = null;//"1";

        // načíst do struktur LDAP: existující FAC, ALU, STU
        LDAPrecords zaměstnanci = new LDAPrecords(Settings.getInstance().getLDAP_baseFaculty(), EBakaLDAPAttributes.OC_USER);
        LDAPrecords absolventi = new LDAPrecords(Settings.getInstance().getLDAP_baseAlumni(), EBakaLDAPAttributes.OC_USER);
        LDAPrecords žáci = new LDAPrecords(Settings.getInstance().getLDAP_baseStudents(), EBakaLDAPAttributes.OC_USER);
        //LDAPrecords žáci = new LDAPrecords("OU=Trida-"+pismeno+",OU=Rocnik-"+rocnik+"," + Settings.getInstance().getLDAP_baseStudents(), EBakaLDAPAttributes.OC_USER);
        LDAPrecords kontakty = new LDAPrecords(Settings.getInstance().getLDAP_baseContacts(), EBakaLDAPAttributes.OC_CONTACT);

        ArrayList<String> použité = new ArrayList<>();

        System.out.println("Zaměstnanci:\t" + zaměstnanci.count());
        System.out.println("Absolventi:\t" + absolventi.count());
        System.out.println("Aktivní žáci:\t" + žáci.count());
        System.out.println("Kontakty:\t" + kontakty.count());

        // načíst do struktur SQL: STU z Bakalářů
        SQLrecords evidence = new SQLrecords((rocnik != null) ? Integer.parseInt(rocnik) : null, pismeno);
        System.out.println("Evidence:\t" + evidence.count());

        // 1) najít v SQL žáky bez e-mailu v doméně školy
        // 2) vytvořit e-mail v doméně školy
        // 3) vyhledat v LDAP daný záznam
        // 4) pokud bude existovat v FAC, ABS, zkusit znovu
        // 5) pokud bude existovat v STU, vyhledat daný záznam a ověřit EXT01
        //  5a) pokud bude již exisotvat, porovnat EXT01 s INTERN_KOD
        //   5aa) pokud se nebude shodovat, vytvořit nový e-mail zkusit to znovu
        //   5ab) pokud se bude shodovat, přiřadit e-mail k žákovi
        // 5b) pokud nebude existovat, přiřadit mail k žákovi a provést do EXT01 zápis INTERN_KOD

        Boolean tmp = false;
        while (tmp) { //evidence.dataIterator().hasNext()) { // TODO přepis na keyIterator
        //while (evidence.dataIterator().hasNext()) { // TODO přepis na keyIterator

            // objekt?
            //Map.Entry<String, Map> žák = evidence.dataIterator().next();
            Map.Entry<String, Map> žák = null;//evidence.dataIterator().next();

            System.out.println("========================================");
            System.out.println("Žák:\t\t" + žák.getValue().get("PRIJMENI").toString() + " " + žák.getValue().get("JMENO").toString());
            System.out.println("Třída:\t\t" + žák.getValue().get("B_ROCNIK").toString() + "." + žák.getValue().get("B_TRIDA").toString());

            if (žák.getValue().get(EBakaSQL.F_STU_MAIL.basename()).toString().toLowerCase().contains(Settings.getInstance().getMailDomain())) {
                System.out.println("STAV:\t\tŽák má mail v doméně školy");

                // 6) kontrola existence žáka v LDAP
                if (žáci.get(žák.getValue().get(EBakaSQL.F_STU_MAIL.basename()).toString().toLowerCase()) != null) {

                    // 8) pokud ano, kontrola správného přiřazení do OU a do skupin
                    Map<String, Object> účetŽáka = žáci.get(žák.getValue().get(EBakaSQL.F_STU_MAIL.basename()).toString());

                    System.out.println("======>");
                    System.out.println("Artefakt: " + žák.getValue().get(EBakaSQL.F_STU_MAIL.basename()).toString());

                    if (účetŽáka.containsKey(EBakaLDAPAttributes.EXT01.attribute())) {
                        if (účetŽáka.get(EBakaLDAPAttributes.EXT01.attribute()).toString().equals(žák.getValue().get(EBakaSQL.F_STU_ID.basename()).toString())) {
                            System.out.println("ID: " + žák.getValue().get(EBakaSQL.F_STU_ID.basename()).toString() + " / " + účetŽáka.get(EBakaLDAPAttributes.EXT01.attribute()).toString());
                        } else {
                            System.out.println("ID se liší.");
                        }
                    } else {
                        System.out.println("CHYBA! LDAP záznam nemá ID.");
                    }

                    // TODO - kontrola
                    System.out.println("Jméno: " + žák.getValue().get(EBakaSQL.F_STU_GIVENNAME.basename()).toString() + " / " + účetŽáka.get(EBakaLDAPAttributes.NAME_FIRST.attribute()).toString());
                    System.out.println("Příjmení: " + žák.getValue().get(EBakaSQL.F_STU_SURNAME.basename()).toString() + " / " + účetŽáka.get(EBakaLDAPAttributes.NAME_LAST.attribute()).toString());
                    System.out.println("Ročník: " + žák.getValue().get(EBakaSQL.F_STU_BK_CLASSYEAR.basename()).toString() + " / " + BakaUtils.classYearFromDn(účetŽáka.get(EBakaLDAPAttributes.DN.attribute()).toString()));
                    System.out.println("Třída: " + žák.getValue().get(EBakaSQL.F_STU_BK_CLASSLETTER.basename()).toString() + " / " + BakaUtils.classLetterFromDn(účetŽáka.get(EBakaLDAPAttributes.DN.attribute()).toString()));
                    // + ID - zápis

                    ArrayList<String> skupiny = BakaADAuthenticator.getInstance().listMembership(účetŽáka.get(EBakaLDAPAttributes.DN.attribute()).toString());
                    if (skupiny.size() > 0) {
                        int sk = 0;
                        for (sk = 0; sk < skupiny.size(); sk++) {

                            String skupina = (skupiny.get(sk).split(",")[0]).split("=")[1];

                            System.out.println("Skupina: " + skupina);
                        }
                    }

                    // TODO - if kontrola
                    if (true) {
                        evidence.setFlag(žák.getKey(), true);
                        žáci.setFlag(žák.getValue().get(EBakaSQL.F_STU_MAIL.basename()).toString(), true);
                    }

                    // 9) kontrola a práce se ZZ
                    // TODO

                } else {
                    // 7) pokud ne, vytvořit nového a inicializovat vše
                    // TODO
                    System.out.println("[ TADY! ] JE POTŘEBA VYTVOŘIT NOVÉHO ŽÁKA.");
                }

                // TODO 9) práce se zákonným zástupcem

            } else {
                // null nebo není v doméně - OK
                if (žák.getValue().get(EBakaSQL.F_STU_MAIL.basename()).toString().length() > 0) {
                    System.out.println("STAV:\t\tŽák nemá mail v doméně školy.");
                    // TODO dojde k přepisu
                } else {
                    System.out.println("STAV:\t\tŽák nemá vyplněný žádný mail.");
                }

                String návrh;
                Boolean obsazený;
                Integer pokus = 0;

                // TEST 2020-04-21 17:23
                /*
                if (žák.getValue().get("PRIJMENI").toString().equalsIgnoreCase("Doležal")) {
                    pokus = 1;
                }*/

                do {
                    obsazený = false;

                    návrh = BakaUtils.createUPNfromName(žák.getValue().get(EBakaSQL.F_STU_SURNAME.basename()).toString(),
                            žák.getValue().get(EBakaSQL.F_STU_GIVENNAME.basename()).toString(), Settings.getInstance().getMailDomain(), pokus);
                    System.out.println("Návrh " + (pokus+1) + ":\t" + návrh);

                    pokus++; // INKREMENTACE POKUSU

                    if (použité.contains(návrh)) {
                        obsazený = true;
                        System.out.println("\t\t[x] Již zabráno předchozím pokusem.");
                        continue;
                    }


                    if (zaměstnanci.get(návrh) != null) {
                        obsazený = true;
                        System.out.println("\t\t[x] " + "obsazeno zaměstnancem.");
                        continue;
                    }

                    if (absolventi.get(návrh) != null) {
                        obsazený = true;
                        System.out.println("\t\t[x] " + "obsazeno vyřazeným žákem.");
                        continue;
                    }

                    if (žáci.get(návrh) != null) {

                        System.out.println("====== Navrhuje se: " + návrh);

                        obsazený = true;
                        System.out.println("\t\t[!] " + "obsazeno aktivním žákem, bude provedena podrobná kontrola.");

                        Map<String, Object> shodnýŽák = žáci.get(návrh);
                        System.out.println("\t\tShoda s:\t" + shodnýŽák.get(EBakaLDAPAttributes.NAME_LAST.attribute()) + " " + shodnýŽák.get(EBakaLDAPAttributes.NAME_FIRST.attribute()) + " (" + BakaUtils.classYearFromDn(shodnýŽák.get(EBakaLDAPAttributes.DN.attribute()).toString()).toString() + "." + BakaUtils.classLetterFromDn(shodnýŽák.get(EBakaLDAPAttributes.DN.attribute()).toString()) + ")");

                        String ext01atribut = (shodnýŽák.containsKey(EBakaLDAPAttributes.EXT01.attribute())) ? shodnýŽák.get(EBakaLDAPAttributes.EXT01.attribute()).toString() : "[!] žák nemá vyplněný atribut EXT01";
                        System.out.println("\t\tEXT01:\t\t" + ext01atribut);

                        if (!shodnýŽák.containsKey(EBakaLDAPAttributes.EXT01.attribute())) {
                            System.out.println("\t\t\t\tProběhne porovnání jména a třídy obou záznamů:");

                            Boolean test_příjmení, test_jméno, test_ročník, test_třída;

                            test_příjmení = shodnýŽák.get(EBakaLDAPAttributes.NAME_LAST.attribute()).equals(žák.getValue().get("PRIJMENI").toString());
                            test_jméno = shodnýŽák.get(EBakaLDAPAttributes.NAME_FIRST.attribute()).equals(žák.getValue().get("JMENO").toString());
                            test_ročník = BakaUtils.classYearFromDn(shodnýŽák.get(EBakaLDAPAttributes.DN.attribute()).toString()).toString().equals(žák.getValue().get("B_ROCNIK").toString());
                            test_třída = BakaUtils.classLetterFromDn(shodnýŽák.get(EBakaLDAPAttributes.DN.attribute()).toString()).equals(žák.getValue().get("B_TRIDA").toString());

                            System.out.println("\t\t\t\tPříjmení:\t[ " + ((test_příjmení) ? "SHODA" : "ROZDÍL") + " ]");
                            System.out.println("\t\t\t\tJméno:\t\t[ " + ((test_jméno) ? "SHODA" : "ROZDÍL") + " ]");
                            System.out.println("\t\t\t\tRočník:\t\t[ " + ((test_ročník) ? "SHODA" : "ROZDÍL") + " ]");
                            System.out.println("\t\t\t\tTřída:\t\t[ " + ((test_třída) ? "SHODA" : "ROZDÍL") + " ]");

                            float dělenec = 0;

                            if (test_příjmení) {
                                dělenec += 1;
                            }

                            if (test_jméno) {
                                dělenec += 1;
                            }

                            if (test_ročník) {
                                dělenec += 1;
                            }

                            if (test_třída) {
                                dělenec += 1;
                            }

                            float faktorShody = (dělenec / 4) * 100;

                            System.out.println("\t\t\t\tShoda:\t\t[ " + String.format("%.2f", faktorShody) + "% ]");

                            // návrh 2020-04-19
                            // 0.75 || (>=0.5 && ročník)
                            if (faktorShody >= 0.75 || (faktorShody >= 0.5 && test_ročník)) {
                                System.out.println("\t\t\t\tBylo dosaženo požadované míry shody.\n\t\t\t\tBude provedeno párování nalezených objektů\n\t\t\t\tpodle identifikátoru [" + žák.getValue().get("INTERN_KOD").toString() + "]");

                                // párování
                                Map<EBakaLDAPAttributes, String> zápis = new HashMap<>();
                                zápis.put(EBakaLDAPAttributes.EXT01, žák.getValue().get(EBakaSQL.F_STU_ID.basename()).toString());
                                žáci.addWriteData(shodnýŽák.get(EBakaLDAPAttributes.DN.attribute()).toString(), zápis);
                                žáci.setFlag(shodnýŽák.get(EBakaLDAPAttributes.UPN.attribute()).toString(), true);

                                // mail je tedy v pořádku
                                obsazený = false;

                                // obsadit
                                použité.add(návrh);

                                // příprava dat
                                HashMap<EBakaSQL, String> nováData = new HashMap<>();
                                nováData.put(EBakaSQL.F_STU_MAIL, návrh);
                                // příprava k zápisu
                                evidence.addWriteData(žák.getValue().get(EBakaSQL.F_STU_ID.basename()).toString(), nováData);
                                evidence.setFlag(žák.getValue().get(EBakaSQL.F_STU_ID.basename()).toString(), true);
                                // hotovo
                                break;
                            } else {
                                System.out.println("\t\t\t\tObjekt nemá kandidáta na párování, bude vytvořena nová adresa.");
                                continue;
                            }

                        } else {
                            // EXT01 existuje
                            System.out.println("\t\t\t\tProběhné porovnání interního kódu nalezených objektů.");

                            if (shodnýŽák.get(EBakaLDAPAttributes.EXT01.attribute()).equals(žák.getValue().get("INTERN_KOD").toString())) {
                                // OK!
                                žáci.setFlag(shodnýŽák.get(EBakaLDAPAttributes.UPN.attribute()).toString(), true);

                                System.out.println("\t\t\t\t=> Jedná se o tohoto žáka! Proběhne zápis adresy do ostrých dat.");
                                obsazený = false;

                                // příprava dat
                                HashMap<EBakaSQL, String> nováData = new HashMap<>();
                                nováData.put(EBakaSQL.F_STU_MAIL, návrh);

                                /*
                                Random rand = new Random();
                                if (rand.nextInt(10) < 5) {
                                    nováData.put(EBakaSQL.DEBUG, "KoKo_Data");
                                }
                                */

                                // příprava k zápisu
                                evidence.addWriteData(shodnýŽák.get(EBakaLDAPAttributes.EXT01.attribute()).toString(), nováData);
                                // FLAG
                                evidence.setFlag(žák.getValue().get(EBakaSQL.F_STU_ID.basename()).toString(), true);

                                // konec
                                break;
                            } else {
                                System.out.println("\t\t\t\t=> Jedná se o zcela jiného žáka.");
                                continue;
                            }
                        }

                        //continue; // "obsazeno aktivním žákem"
                    }

                    // konec, zapsat data
                    if (!obsazený) {
                        HashMap<EBakaSQL, String> nováKokoData = new HashMap<>();
                        nováKokoData.put(EBakaSQL.F_STU_MAIL, návrh);
                        evidence.addWriteData(žák.getValue().get(EBakaSQL.F_STU_ID.basename()).toString(), nováKokoData);
                    }

                } while (obsazený || pokus >= 10); // TODO ve finále nastavit globální limit (50? Nováků)

            }


        }

        System.out.println("=== LDAP writeback =====================");
        System.out.println("LDAP Data k zápisu: " + žáci.writesRemaining());
        System.out.println("LDAP probíhá zápis...");
        žáci.commit();
        System.out.println("LDAP Data k zápisu: " + žáci.writesRemaining());

        System.out.println("=== SQL writeback ======================");
        System.out.println("SQL Data k zápisu: " + evidence.writesRemaining());
        System.out.println("SQL probíhá zápis...");
        evidence.commit();
        System.out.println("SQL Data k zápisu: " + evidence.writesRemaining());

        System.out.println("=== STAV procesu =======================");
        System.out.println("Evidence: celkem " + evidence.count() + ", nezpracováno: " + evidence.getSubsetWithFlag(false).size());
        System.out.println("AD: celkem " + žáci.count() + ", nezpracováno: " + žáci.getSubsetWithFlag(false).size());

        // 2020-04-21
        if (evidence.getSubsetWithFlag(false).size() > 0) {
            System.out.println("========================================");
            System.out.println("Proběhne kontrola nezpracovaných údajů z evidence. Tyto děti neexistují v AD a budou jim vytvořeny nové účty:");

            Iterator<String> nezpracovanáEvidence = evidence.getSubsetWithFlag(false).keySet().iterator();
            while (nezpracovanáEvidence.hasNext()) {

                Map<String, String> nezpracovanýZáznam = evidence.get(nezpracovanáEvidence.next());

                System.out.println(nezpracovanýZáznam.get(EBakaSQL.F_STU_CLASS.basename())
                        + " : " + nezpracovanýZáznam.get(EBakaSQL.F_STU_SURNAME.basename())
                        + " " + nezpracovanýZáznam.get(EBakaSQL.F_STU_GIVENNAME.basename())
                );

                System.out.println("====> Proběhne vytvoření nového žáka:");


                DataLDAP nováData = new DataLDAP();
                String novýRočník = nezpracovanýZáznam.get(EBakaSQL.F_STU_BK_CLASSYEAR.basename()).toString();
                String nováTřída = nezpracovanýZáznam.get(EBakaSQL.F_STU_BK_CLASSLETTER.basename()).toString().toUpperCase();

                // připravit OU
                String cílováOU = "OU=Trida-" + nováTřída + ",OU=Rocnik-" + novýRočník + "," + Settings.getInstance().getLDAP_baseStudents();
                System.out.println("[OU] = " + cílováOU);

                String jméno = nezpracovanýZáznam.get(EBakaSQL.F_STU_GIVENNAME.basename());
                nováData.put(EBakaLDAPAttributes.NAME_FIRST.attribute(), jméno);
                System.out.println("Jméno: " + jméno);
                String příjmení = nezpracovanýZáznam.get(EBakaSQL.F_STU_SURNAME.basename());
                nováData.put(EBakaLDAPAttributes.NAME_LAST.attribute(), příjmení);
                System.out.println("Příjmení: " + příjmení);
                String cn = příjmení + " " + jméno;
                String display = cn;
                nováData.put(EBakaLDAPAttributes.NAME_DISPLAY.attribute(), display);
                System.out.println("Display: " + display);
                String bakaMail = nezpracovanýZáznam.get(EBakaSQL.F_STU_MAIL.basename());
                System.out.println("Bakamail: " + bakaMail);
                String mail = BakaUtils.createUPNfromName(příjmení, jméno, "zs-studanka.cz");
                nováData.put(EBakaLDAPAttributes.MAIL.attribute(), mail);
                System.out.println("Mail: " + mail);
                String upn = mail;
                nováData.put(EBakaLDAPAttributes.UPN.attribute(), upn);
                String login = BakaUtils.createSAMloginFromName(příjmení, jméno, 0);
                nováData.put(EBakaLDAPAttributes.LOGIN.attribute(), login);
                System.out.println("Legacy login: " + login);
                String uid = upn;
                nováData.put(EBakaLDAPAttributes.UID.attribute(), uid);

                Integer čísloVýkazu = Integer.parseInt(nezpracovanýZáznam.get(EBakaSQL.F_STU_CLASS_ID.basename()));
                System.out.println("Číslo výkazu: " + čísloVýkazu);

                String heslo = BakaUtils.createInitialPassword(příjmení, jméno, čísloVýkazu);
                System.out.println("Heslo: " + heslo);
                nováData.put(EBakaLDAPAttributes.PW_UNICODE.attribute(), heslo);
                nováData.put(EBakaLDAPAttributes.UAC.attribute(), EBakaUAC.NORMAL_ACCOUNT.toString());

                String title = novýRočník + "." + nováTřída;
                System.out.println("Pracovní pozice: " + title);
                nováData.put(EBakaLDAPAttributes.TITLE.attribute(), title);

                String internal_id = nezpracovanýZáznam.get(EBakaSQL.F_STU_ID.basename());
                System.out.println("ID/EXT01: " + internal_id);
                nováData.put(EBakaLDAPAttributes.EXT01.attribute(), internal_id);

                // připravit skupiny
                String skupinaZaci = "CN=Skupina-Zaci," + Settings.getInstance().getLDAP_baseGlobalGroups();
                String třídníSkupina = "CN=Zaci-Trida-" + novýRočník + nováTřída + "," + Settings.getInstance().getLDAP_baseStudentGroups();

                System.out.println("[SK] = " + skupinaZaci);
                System.out.println("[SK] = " + třídníSkupina);

                // create
                BakaADAuthenticator.getInstance().createNewUser(cn, cílováOU, nováData);

                // addObjectToGroup
                String dn = "CN=" + cn + "," + cílováOU;
                BakaADAuthenticator.getInstance().addObjectToGroup(dn, skupinaZaci);
                BakaADAuthenticator.getInstance().addObjectToGroup(dn, třídníSkupina);

            }


        } else {
            System.out.println("========================================");
            System.out.println("[ :) ] Nezůstaly žádné nezpracované údaje z evidence.");
        }

        if (žáci.getSubsetWithFlag(false).size() > 0) {
            System.out.println("========================================");
            System.out.println("Proběhne kontrola nezpracovaných údajů z AD. Tyto záznamy nejsou aktivní žáci a budou přesunuty do absolventů.:");

            Iterator<String> nezpracovanéAD = žáci.getSubsetWithFlag(false).keySet().iterator();
            while (nezpracovanéAD.hasNext()) {

                String upnArtefakt = nezpracovanéAD.next();

                Integer můjRočník = BakaUtils.classYearFromDn(žáci.get(upnArtefakt).get(EBakaLDAPAttributes.DN.attribute()).toString());
                String mojeTřída = BakaUtils.classLetterFromDn(žáci.get(upnArtefakt).get(EBakaLDAPAttributes.DN.attribute()).toString());
                Boolean skip = false;

                if (mojeTřída.equals("E") && můjRočník == 1) {
                    skip = true;
                    System.out.println("=== DEBUG ZÁZNAM ===");
                } else {
                    System.out.println("=== AD ===");
                }

                System.out.println("UPN: " + žáci.get(upnArtefakt).get(EBakaLDAPAttributes.UPN.attribute()));
                System.out.println("Příjmení: " + žáci.get(upnArtefakt).get(EBakaLDAPAttributes.NAME_LAST.attribute()));
                System.out.println("Jméno: " + žáci.get(upnArtefakt).get(EBakaLDAPAttributes.NAME_FIRST.attribute()));
                System.out.println("Display: " + žáci.get(upnArtefakt).get(EBakaLDAPAttributes.NAME_DISPLAY.attribute()));
                System.out.println("SAM: " + žáci.get(upnArtefakt).get(EBakaLDAPAttributes.LOGIN.attribute()));
                System.out.println("DN: " + žáci.get(upnArtefakt).get(EBakaLDAPAttributes.DN.attribute()));
                System.out.println("Ročník: " + můjRočník);
                System.out.println("Třída: " + mojeTřída);

                if (žáci.get(upnArtefakt).containsKey(EBakaLDAPAttributes.MEMBER_OF.attribute())) {
                    System.out.println("Skupiny: " + žáci.get(upnArtefakt).get(EBakaLDAPAttributes.MEMBER_OF.attribute()).toString());
                }

                if (!skip) {
                    // zamknout účet
                    // odebrat title, resp. "ABS 2020"
                    // odebrat skupiny
                    // přesunout do nové OU

                    // datum
                    SimpleDateFormat formatter = new SimpleDateFormat("yyyy");

                    // změny
                    HashMap<EBakaLDAPAttributes, String> změny = new HashMap<>();
                    změny.put(EBakaLDAPAttributes.TITLE, "ABS " + formatter.format(new Date()));
                    změny.put(EBakaLDAPAttributes.UAC, String.format("%d", EBakaUAC.ACCOUNTDISABLE.value()|EBakaUAC.PASSWORD_EXPIRED.value()));
                    // todo skupiny
                    žáci.addWriteData(žáci.get(upnArtefakt).get(EBakaLDAPAttributes.DN.attribute()).toString(), změny);
                    žáci.commit();

                    // nová ou
                    String absOU = "OU=" + formatter.format(new Date()) + "," + Settings.getInstance().getLDAP_baseAlumni();
                    System.out.println("Záznam bude přesunut pod OU [" + absOU + "]");
                    BakaADAuthenticator.getInstance().moveObject(žáci.get(upnArtefakt).get(EBakaLDAPAttributes.DN.attribute()).toString(), absOU, true);
                }


            }

        } else {
            System.out.println("========================================");
            System.out.println("Nezůstaly žádné nezpracované záznamy z Active Directory.");
        }


        // 2020-04-19 testování mailu ///
        //BakaMailer.getInstance().mail("Test systému", "Toto je kontrola posílání prosté zprávy se žluťučkým koněm úpějícím ďábelské ódy.");
        /*
        BakaMailer.getInstance().mail(new String[]{"jan.hladena@zs-studanka.cz"},
        "Test multipart s podpisem", "Toto je kontrola poslání přílohy se žluťučkým koněm úpějícím ďábelské ódy.",
        new String[]{"./test.txt"});
        */

        // testovací výpis
        //System.out.println(evidence.toString());

        // 2020-04-18 proveden přepis title na třídy -- OK
        /*
        //LDAPrecords zaci = new LDAPrecords("OU=Trida-E,OU=Rocnik-1," + Settings.getInstance().getLDAP_baseStudents(), EBakaLDAPAttributes.OC_USER);

        for (int r = 1; r <= 9; r++) {
            LDAPrecords zaci = new LDAPrecords("OU=Rocnik-" + r + "," + Settings.getInstance().getLDAP_baseStudents(), EBakaLDAPAttributes.OC_USER);

            System.out.println("OK, bylo nalezeno " + zaci.count() + " položek. Jsou to:");
            //System.out.println(zaci.toString());


            System.out.println("Zkusíme zapsat atributy!");
            zaci.resetIterator();

            while (zaci.iterator().hasNext()) {

                Map.Entry<String, Map> záznam = zaci.iterator().next();
                String dataKzápisu = záznam.getValue().get(EBakaLDAPAttributes.DN.attribute()).toString()
                        .replaceAll("CN=.*Trida-(.).*Rocnik-(.).*", "$2.$1");

                System.out.println("bude přepis na: " + dataKzápisu);

            BakaADAuthenticator.getInstance().replaceAttribute(
                    záznam.getValue().get(EBakaLDAPAttributes.DN.attribute()).toString(),
                    EBakaLDAPAttributes.TITLE,
                    dataKzápisu
            );
            }

        } // for
        */

        // 2020-04-07 funguje
        //Structure.checkAndRepairADStructure(true);

        /*
        Settings.getInstance().override("sql_con", "kerberos");

        System.out.println("Testování Kerberos V spojení na SQL Server:");
        if (BakaSQL.getInstance().testSQL()) {
            System.out.println("* [ OK ]");
        } else {
            System.out.println("* [ CHYBA ]");
        }
         */

        // 2020-04-14 jména
        /*
        String[][] jména = {
                {"Janíkovič", "Milisálek Kociáš"},
                {"Veselá Smutná", "Marie Tonička Křepelka"},
                {"Kocino Čertino", "Juan Carlos"},
                {"Nguyen Thi Dieu", "Linh"},
                {"Uttendorfská", "Viktorie"}
        };

        for (String[] jméno : jména) {
            String upn = BakaUtils.createUPNfromName(jméno[0], jméno[1], Settings.getInstance().getMailDomain());
            String sam = BakaUtils.createSAMloginFromName(jméno[0], jméno[1], 0);
            System.out.println(jméno[0] + " " + jméno[1] + " = " + upn + " [ ZSSTU\\" + sam + " ]");
        }
        */

        System.out.println("====== [ /TEST ] =====");
    }

}
