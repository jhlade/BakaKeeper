package cz.zsstudanka.skola.bakakeeper.routines;

import cz.zsstudanka.skola.bakakeeper.connectors.BakaADAuthenticator;
import cz.zsstudanka.skola.bakakeeper.connectors.BakaMailer;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLDAPAttributes;
import cz.zsstudanka.skola.bakakeeper.model.collections.LDAPrecords;
import cz.zsstudanka.skola.bakakeeper.model.collections.SQLrecords;
import cz.zsstudanka.skola.bakakeeper.model.entities.Student;
import cz.zsstudanka.skola.bakakeeper.settings.Settings;
import cz.zsstudanka.skola.bakakeeper.utils.BakaUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class Test {

    public static void test_01() {
        System.out.println("====== [ TEST ] ======");

        // načíst do struktur LDAP: existující FAC, ALU, STU
        LDAPrecords zaměstnanci = new LDAPrecords(Settings.getInstance().getLDAP_baseFaculty(), EBakaLDAPAttributes.OC_USER);
        LDAPrecords absolventi = new LDAPrecords(Settings.getInstance().getLDAP_baseAlumni(), EBakaLDAPAttributes.OC_USER);
        //LDAPrecords žáci = new LDAPrecords(Settings.getInstance().getLDAP_baseStudents(), EBakaLDAPAttributes.OC_USER);
        LDAPrecords žáci = new LDAPrecords("OU=Trida-A,OU=Rocnik-1," + Settings.getInstance().getLDAP_baseStudents(), EBakaLDAPAttributes.OC_USER);
        LDAPrecords kontakty = new LDAPrecords(Settings.getInstance().getLDAP_baseContacts(), EBakaLDAPAttributes.OC_CONTACT);

        System.out.println("Zaměstnanci:\t" + zaměstnanci.count());
        System.out.println("Absolventi:\t" + absolventi.count());
        System.out.println("Aktivní žáci:\t" + žáci.count());
        System.out.println("Kontakty:\t" + kontakty.count());

        // načíst do struktur SQL: STU z Bakalářů
        SQLrecords evidence = new SQLrecords();
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
        while (evidence.iterator().hasNext()) {

            // objekt?
            Map.Entry<String, Map> žák = evidence.iterator().next();

            System.out.println("========================================");
            System.out.println("Žák:\t\t" + žák.getValue().get("PRIJMENI").toString() + " " + žák.getValue().get("JMENO").toString());
            System.out.println("Třída:\t\t" + žák.getValue().get("B_ROCNIK").toString() + "." + žák.getValue().get("B_TRIDA").toString());

            if (žák.getValue().get("E_MAIL").toString().toLowerCase().contains(Settings.getInstance().getMailDomain())) {
                System.out.println("STAV:\t\tŽák má mail v doméně školy");
                // TODO

                // 6) kontrola existence žáka v LDAP
                // 7) pokud ne, vytvořit nového a inicializovat vše
                // 8) pokud ano, kontrola správného přiřazení do OU a do skupin

                // TODO 9) práce se zákonným zástupcem

            } else {
                if (žák.getValue().get("E_MAIL").toString().length() > 0) {
                    System.out.println("STAV:\t\tŽák nemá mail v doméně školy.");
                } else {
                    System.out.println("STAV:\t\tŽák nemá vyplněný žádný mail.");
                }

                String návrh;
                Boolean obsazený;
                Integer pokus = 0;

                do {
                    obsazený = false;

                    návrh = BakaUtils.createUPNfromName(žák.getValue().get("PRIJMENI").toString(), žák.getValue().get("JMENO").toString(), Settings.getInstance().getMailDomain(), pokus);
                    System.out.println("Návrh " + (pokus+1) + ":\t" + návrh);
                    pokus++;


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
                        obsazený = true;
                        System.out.println("\t\t[!] " + "obsazeno aktivním žákem, bude provedena podrobná kontrola.");

                        Map<String, String> shodnýŽák = žáci.get(návrh);
                        System.out.println("\t\tShoda s:\t" + shodnýŽák.get(EBakaLDAPAttributes.NAME_LAST.attribute()) + " " + shodnýŽák.get(EBakaLDAPAttributes.NAME_FIRST.attribute()) + " (" + BakaUtils.classYearFromDn(shodnýŽák.get(EBakaLDAPAttributes.DN.attribute())).toString() + "." + BakaUtils.classLetterFromDn(shodnýŽák.get(EBakaLDAPAttributes.DN.attribute())) + ")");

                        String ext01atribut = (shodnýŽák.containsKey(EBakaLDAPAttributes.EXT01.attribute())) ? shodnýŽák.get(EBakaLDAPAttributes.EXT01.attribute()) : "[!] žák nemá vyplněný atribut EXT01";
                        System.out.println("\t\tEXT01:\t\t" + ext01atribut);

                        if (!shodnýŽák.containsKey(EBakaLDAPAttributes.EXT01.attribute())) {
                            System.out.println("\t\t\t\tProběhne porovnání jména a třídy obou záznamů:");

                            Boolean test_příjmení, test_jméno, test_ročník, test_třída;

                            test_příjmení = shodnýŽák.get(EBakaLDAPAttributes.NAME_LAST.attribute()).equals(žák.getValue().get("PRIJMENI").toString());
                            test_jméno = shodnýŽák.get(EBakaLDAPAttributes.NAME_FIRST.attribute()).equals(žák.getValue().get("JMENO").toString());
                            test_ročník = BakaUtils.classYearFromDn(shodnýŽák.get(EBakaLDAPAttributes.DN.attribute())).toString().equals(žák.getValue().get("B_ROCNIK").toString());
                            test_třída = BakaUtils.classLetterFromDn(shodnýŽák.get(EBakaLDAPAttributes.DN.attribute())).equals(žák.getValue().get("B_TRIDA").toString());

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
                            if (faktorShody >= 0.75 ||  (faktorShody >= 0.5 && test_ročník)) {
                                System.out.println("\t\t\t\tBylo dosaženo požadované míry shody.\n\t\t\t\tBude provedeno párování nalezených objektů\n\t\t\t\tpodle identifikátoru [" + žák.getValue().get("INTERN_KOD").toString() + "]");

                                Map<EBakaLDAPAttributes, Object> zápis = new HashMap<>();
                                zápis.put(EBakaLDAPAttributes.EXT01, žák.getValue().get("INTERN_KOD").toString());
                                žáci.writeData(shodnýŽák.get(EBakaLDAPAttributes.DN.attribute()), zápis);


                            } else {
                                System.out.println("\t\t\t\tObjekt nemá kandidáta na párování, bude vygenerována nová adresa.");
                                continue;
                            }

                        } else {
                            // EXT01 existuje
                            System.out.println("\t\t\t\tProběhné porovnání interního kódu nalezených objektů.");

                            if (shodnýŽák.get(EBakaLDAPAttributes.EXT01.attribute()).equals(žák.getValue().get("INTERN_KOD").toString())) {
                                // OK!
                                System.out.println("\t\t\t\t=> Jedná se o tohoto žáka! Proběhne zápis adresy do ostrých dat.");

                                // TODO

                            } else {
                                System.out.println("\t\t\t\t=> Jedná se o zcela jiného žáka.");
                                continue;
                            }
                        }

                        continue;
                    }

                } while (obsazený || pokus >= 10); // TODO ve finále nastavit globální limit (50? Nováků)

            }

            /*
            System.out.println("Žák " + žák.getValue().get("PRIJMENI").toString() + " "
                    + žák.getValue().get("JMENO").toString() + "\t\t" + žák.getValue().get("E_MAIL").toString() + " "
                    + "délka ["+žák.getValue().get("E_MAIL").toString().length()+"]");
            */

        }

        System.out.println("======");
        System.out.println("LDAP Data k zápisu: " + žáci.writesRemaining());
        System.out.println("LDAP probíhá zápis...");
        žáci.commit();
        System.out.println("LDAP Data k zápisu: " + žáci.writesRemaining());

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

    public static void testOLD_2020_04_06() {
        //checkAndRepairStructure(true);

        //GeneralRecords gr = new GeneralRecords(Settings.getInstance().getLDAP_baseKontakty());

        /*
        for (int r = 1; r <= 9; r++) {
            for (char t = 'A'; t <= 'E'; t++) {
                GeneralRecords gr = new GeneralRecords("OU=Trida-"+t+",OU=Rocnik-"+r+"," + Settings.getInstance().getLDAP_baseZaci());
            }
        }
        */


        /*
        System.out.println("Výroba kontaktů 1.C:");
        Trida tr1a = new Trida(1, 'C');
        tr1a.populate();

        while (tr1a.getZaci().iterator().hasNext()) {

            Zak tmpZak = tr1a.getZaci().next();
            ZakonnyZastupce tmpZZ = tmpZak.getZakonnyZastupce();

            if (!tmpZZ.getZZ_kod().equals(ZakonnyZastupce.ZZ_NULL_KOD)) {
                BakaADAuthenticator.getInstance().createContact(
                        Settings.getInstance().getLDAP_baseKontakty(),
                        tmpZZ.getPrijmeni(),
                        tmpZZ.getJmeno(),
                        tmpZZ.getDisplayName(),
                        tmpZZ.getEmail(),
                        tmpZZ.getTelefon(),
                        new String[]{"Rodice-Trida-" + tr1a.getCisloRocniku() + tr1a.getPismeno()}
                );
            }
        }*/




        /*
        BakaADAuthenticator.getInstance().createContact(
                Settings.getInstance().getLDAP_baseKontakty(),
                "Novák",
                "Matouš",
                "Matouš Novák",
                "matous.novak@zs-studanka.cz",
                "605123456",
                null);
        */

        /*
        ZakonnyZastupce testZZ = new ZakonnyZastupce(
                "XYZ",
                "Alena",
                "Testová",
                "604234567",
                "alena.testova@joutsen.cz",
                null
        );*/

        //System.out.println("Existuje: " + testZZ.existsAsContact());

        //BakaADAuthenticator.getInstance().test_updateGroups();

        /*
        for (int r = 1; r <= 9; r++) {
            for (char t = 'A'; t <= 'E'; t++) {
                StringBuilder newName = new StringBuilder();
                newName.append(r).append(t);
                // OK!
                //BakaADAuthenticator.getInstance().test_createNewGroup("OU=DistribucniSeznamyZZ," + Settings.getInstance().getLDAP_baseKontakty(), newName.toString());
            }
        }*/


        /*
        Map<String, Object> info = BakaADAuthenticator.getInstance().getContactInfo("katerina.petrusova@zs-studanka.cz");

        if (info != null) {

            System.out.println("Kontakt: " + info.get(EBakaLDAPAttributes.DISPLAY_NAME.attribute()));
            System.out.println("Mail: " + info.get(EBakaLDAPAttributes.MAIL.attribute()));

            if (info.get(EBakaLDAPAttributes.MEMBER_OF.attribute()) != null) {

                if (info.get(EBakaLDAPAttributes.MEMBER_OF.attribute()) instanceof ArrayList) {

                    for (int m = 0; m  < ((ArrayList) info.get(EBakaLDAPAttributes.MEMBER_OF.attribute())).size(); m++) {
                        System.out.println("member-of: " + ((ArrayList) info.get(EBakaLDAPAttributes.MEMBER_OF.attribute())).get(m) );
                    }

                } else {
                    System.out.println("member-of: " + (info.get(EBakaLDAPAttributes.MEMBER_OF.attribute())).toString());
                }

            }


        }*/


        /*
        System.out.println("Aktuální školní rok je " + Settings.getInstance().getSkolniRok());
        System.out.println("Loňský školní rok byl " + Settings.getInstance().getMinulyRok());

        Zamestnanci zam = Zamestnanci.getInstance();
        Absolventi abs = Absolventi.getInstance();

        IUzivatelAD zam_1 = zam.findCollisions("jan.hladena@zs-studanka.cz");
        IUzivatelAD zam_2 = zam.findCollisions("hladena");
        IUzivatelAD abs_1 = abs.findCollisions("jan.hladena@zs-studanka.cz");

        System.out.println("Hledám kolizi podle mailu  Z1: " + ((zam_1 != null) ? "nalezena" : "nenalezena"));
        System.out.println("Hledám kolizi podle loginu Z1: " + ((zam_2 != null) ? "nalezena" : "nenalezena"));
        System.out.println("Hledám kolizi podle loginu A1: " + ((abs_1 != null) ? "nalezena" : "nenalezena"));

        */

        /*
        for (int z = 0; z < zam.count(); z++) {
            Zamestnanec itZ = zam.next();

            System.out.println(itZ.toString() + "\n");
        }*/

        /*
        Absolventi pokus = Absolventi.getInstance();
        System.out.println(Absolventi.getInstance().toString());
        */

        /*
        String[] ucitele = {"mandji", "hojkan", "hladena", "zak.miroslav", "bakalari", "travka", "fantmo"};

        for (int i = 0; i < ucitele.length; i++) {

            Zamestnanec uzivatel = new Zamestnanec(ucitele[i]);
            System.out.println("Jméno: " + uzivatel.getDisplayName());
            System.out.println("E-mail: " + uzivatel.getADEmail());
            System.out.println();
        }
        */
    }

}
