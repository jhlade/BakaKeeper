package cz.zsstudanka.skola.bakakeeper.routines;

import cz.zsstudanka.skola.bakakeeper.connectors.BakaADAuthenticator;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLDAPAttributes;
import cz.zsstudanka.skola.bakakeeper.model.collections.LDAPrecords;
import cz.zsstudanka.skola.bakakeeper.model.collections.SQLrecords;
import cz.zsstudanka.skola.bakakeeper.settings.Settings;

import java.util.Map;

public class Test {

    public static void test_01() {
        System.out.println("====== [ TEST ] ======");

        // načíst do struktur LDAP: existující FAC, ALU, STU
        LDAPrecords zaměstnanci = new LDAPrecords(Settings.getInstance().getLDAP_baseFaculty(), EBakaLDAPAttributes.OC_USER);
        LDAPrecords absolventi = new LDAPrecords(Settings.getInstance().getLDAP_baseAlumni(), EBakaLDAPAttributes.OC_USER);
        LDAPrecords žáci = new LDAPrecords(Settings.getInstance().getLDAP_baseStudents(), EBakaLDAPAttributes.OC_USER);
        LDAPrecords kontakty = new LDAPrecords(Settings.getInstance().getLDAP_baseContacts(), EBakaLDAPAttributes.OC_CONTACT);

        System.out.println("Zaměstnanci:\t" + zaměstnanci.count());
        System.out.println("Absolventi:\t" + absolventi.count());
        System.out.println("Aktivní žáci:\t" + žáci.count());
        System.out.println("Kontakty:\t" + kontakty.count());

        // načíst do struktur SQL: STU z Bakalářů
        SQLrecords evidence = new SQLrecords();
        System.out.println("Evidence:\t" + evidence.count());

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
