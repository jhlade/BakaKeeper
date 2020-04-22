package cz.zsstudanka.skola.bakakeeper.routines;


import cz.zsstudanka.skola.bakakeeper.connectors.BakaADAuthenticator;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLDAPAttributes;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaSQL;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaUAC;
import cz.zsstudanka.skola.bakakeeper.model.collections.LDAPrecords;
import cz.zsstudanka.skola.bakakeeper.model.collections.SQLrecords;
import cz.zsstudanka.skola.bakakeeper.settings.Settings;
import cz.zsstudanka.skola.bakakeeper.utils.BakaUtils;

import java.text.SimpleDateFormat;
import java.util.*;

public class Test {

    public static void test_01() {
        System.out.println("====== [ TEST ] ======");

        /*
        String záznamDN = "CN=Aaadam Expirovaný,OU=Trida-E,OU=Rocnik-1,OU=Zaci,OU=Uzivatele,OU=Skola,DC=zsstu,DC=local";
        String vyřazenáOU = "OU=2020," + Settings.getInstance().getLDAP_baseAlumni();

        //BakaADAuthenticator.getInstance().moveObject(záznamDN, vyřazenáOU, true);

        BakaADAuthenticator.getInstance().createOU("Test 1999", Settings.getInstance().getLDAP_baseAlumni());
         */

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

        String pismeno = null;//"C";
        String rocnik = null;//"1";

        // načíst do struktur LDAP: existující FAC, ALU, STU
        LDAPrecords zaměstnanci = new LDAPrecords(Settings.getInstance().getLDAP_baseFaculty(), EBakaLDAPAttributes.OC_USER);
        LDAPrecords absolventi = new LDAPrecords(Settings.getInstance().getLDAP_baseAlumni(), EBakaLDAPAttributes.OC_USER);
        LDAPrecords žáci = new LDAPrecords(Settings.getInstance().getLDAP_baseStudents(), EBakaLDAPAttributes.OC_USER);
        //LDAPrecords žáci = new LDAPrecords("OU=Trida-"+pismeno+",OU=Rocnik-"+rocnik+"," + Settings.getInstance().getLDAP_baseStudents(), EBakaLDAPAttributes.OC_USER);
        LDAPrecords kontakty = new LDAPrecords(Settings.getInstance().getLDAP_baseContacts(), EBakaLDAPAttributes.OC_CONTACT);

        ArrayList<String> použité = new ArrayList<>();

        /*
        System.out.println(žáci.get("dolezal.jan@zs-studanka.cz"));
        System.out.println(žáci.get("dolezal.jan1@zs-studanka.cz"));
        */
        /*
        System.out.println(žáci);
        while (žáci.keyIterator().hasNext()) {
            System.out.println(žáci.get(žáci.keyIterator().next()));
        }*/

        /*
        if (true) {
            return;
        }*/

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
        while (evidence.iterator().hasNext()) { // TODO přepis na keyIterator

            // objekt?
            Map.Entry<String, Map> žák = evidence.iterator().next();

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
                                žáci.writeData(shodnýŽák.get(EBakaLDAPAttributes.DN.attribute()).toString(), zápis);
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

                            } else {
                                System.out.println("\t\t\t\t=> Jedná se o zcela jiného žáka.");
                                continue;
                            }
                        }

                        //continue; // "obsazeno aktivním žákem"
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

                System.out.println("Proběhne vytvoření nového žáka:");
                // připravit OU
                // připravit skupiny
                // addObjectToGroup

                String novýRočník = nezpracovanýZáznam.get(EBakaSQL.F_STU_BK_CLASSYEAR.basename()).toString();
                String nováTřída = nezpracovanýZáznam.get(EBakaSQL.F_STU_BK_CLASSLETTER.basename()).toString().toUpperCase();

                String cílováOU = "OU=Trida-" + nováTřída + ",OU=Rocnik-" + novýRočník + "," + Settings.getInstance().getLDAP_baseStudents();
                String skupinaZaci = "CN=Skupina-Zaci," + Settings.getInstance().getLDAP_baseGlobalGroups();
                String třídníSkupina = "CN=Zaci-Trida-" + novýRočník + nováTřída + "," + Settings.getInstance().getLDAP_baseStudentGroups();

                System.out.println("[OU] = " + cílováOU);
                System.out.println("[SK] = " + skupinaZaci);
                System.out.println("[SK] = " + třídníSkupina);

            }


        } else {
            System.out.println("========================================");
            System.out.println("Nezůstaly žádné nezpracované údaje z evidence.");
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
                    žáci.writeData(žáci.get(upnArtefakt).get(EBakaLDAPAttributes.DN.attribute()).toString(), změny);
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
