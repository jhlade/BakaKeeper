package cz.zsstudanka.skola.bakakeeper.routines;

/**
 * Práce s exporty.
 *
 * @author Jan Hladěna
 */
public class Export {

    /**
     * Export žákovských informací ve formě CSV.
     *
     * Pole obsahují
     * INTERN_KOD  - interní ID žáka z Bakalářů
     * ROCNIK      - číslo ročníku z Bakalářů
     * TRIDA       - písmeno třídy z Bakalářů
     * PRIJMENI    - příjméní žáka z Bakalářů
     * JMENO       - jméno žáka z Bakalářů
     * AD_LOGIN    - přihlašovací jméno žáka z LDAP
     * AD_EMAIL    - primární e-mail žáka z LDAP
     * EMAIL       - e-mail žáka z Bakalářů
     * ZZ_KOD      - interní ID primárního zákonného zástupce žáka z Bakalářů
     * ZZ_JMENO    - jméno primárního zákonného zástupce žáka z Bakalářů
     * ZZ_PRIJMENI - příjmení primárního zákonného zástupce žáka z Bakalářů
     * ZZ_TELEFON  - telefon primárního zákonného zástupce žáka z Bakalářů
     * ZZ_EMAIL    - e-mail primárního zákonného zástupce žáka z Bakalářů
     *
     * @param outFile výstupní soubor
     * @deprecated
     */

    /*
    public static void exportStudentCSVdata(String outFile) {

        Settings.getInstance().load();

        StringBuilder outputBuffer = new StringBuilder();
        // TODO (zkontrolovat) header
        outputBuffer.append("INTERN_KOD;ROCNIK;TRIDA;C_TR_VYK;PRIJMENI;JMENO;AD_LOGIN;AD_EMAIL;AD_RPWD;EMAIL;ZZ_KOD;ZZ_JMENO;ZZ_PRIJMENI;ZZ_TELEFON;ZZ_EMAIL");
        outputBuffer.append("\n");

        // připojení k SQL serveru
        BakaSQL.getInstance().connect();

        for (int rocnik = 1; rocnik <= 9; rocnik++) {
            for (char trida = 'A'; trida <= 'E'; trida++) {

                // aktuální třída
                Trida tmpTrida = new Trida(rocnik, trida);
                tmpTrida.populate();

                if (Settings.getInstance().beVerbose()) {
                    System.err.println("[ INFO ] Exportuje se třída " + tmpTrida.getDisplayName() + ".");
                }

                // iterace
                while (tmpTrida.getZaci().iterator().hasNext()) {

                    Zak tmpZak = tmpTrida.getZaci().next();

                    outputBuffer.append("\"" + tmpZak.getIntern_kod() + "\"");
                    outputBuffer.append(";");
                    outputBuffer.append("\"" + tmpZak.getRocnik() + "\"");
                    outputBuffer.append(";");
                    outputBuffer.append("\"" + tmpZak.getPismenoTridy() + "\"");
                    outputBuffer.append(";");
                    outputBuffer.append("\"" + tmpZak.getCTrVyk() + "\"");
                    outputBuffer.append(";");
                    outputBuffer.append("\"" + tmpZak.getPrijmeni() + "\"");
                    outputBuffer.append(";");
                    outputBuffer.append("\"" + tmpZak.getJmeno() + "\"");
                    outputBuffer.append(";");

                    if (tmpZak.findByData()) {
                        // AD LOGIN
                        outputBuffer.append("\"" + tmpZak.getADLogin() + "\"");
                        outputBuffer.append(";");
                        // AD EMAIL
                        outputBuffer.append("\"" + tmpZak.getADEmail() + "\"");
                        outputBuffer.append(";");
                    } else {
                        // AD LOGIN
                        outputBuffer.append("\"" + "NULL" + "\"");
                        outputBuffer.append(";");
                        // AD EMAIL
                        outputBuffer.append("\"" + "NULL" + "\"");
                        outputBuffer.append(";");
                    }

                    outputBuffer.append("\"" + tmpZak.getRPwd() + "\"");
                    outputBuffer.append(";");

                    outputBuffer.append("\"" + tmpZak.getBakaEmail() + "\"");
                    outputBuffer.append(";");
                    outputBuffer.append("\"" + tmpZak.getZakonnyZastupce().getZZ_kod() + "\"");
                    outputBuffer.append(";");
                    outputBuffer.append("\"" + tmpZak.getZakonnyZastupce().getJmeno() + "\"");
                    outputBuffer.append(";");
                    outputBuffer.append("\"" + tmpZak.getZakonnyZastupce().getPrijmeni() + "\"");
                    outputBuffer.append(";");
                    outputBuffer.append("\"" + tmpZak.getZakonnyZastupce().getTelefon() + "\"");
                    outputBuffer.append(";");
                    outputBuffer.append("\"" + tmpZak.getZakonnyZastupce().getEmail() + "\"");
                    outputBuffer.append("\n");
                }
            }
        }

        // uložení nebo výstup
        if (outFile != null) {

            File outputFile = new File(outFile);

            try {
                PrintStream outStream = new PrintStream(new FileOutputStream(outFile));
                outStream.println(outputBuffer.toString());
                outStream.close();
            } catch (Exception e) {
                System.err.println("[ CHYBA ] Nebylo možné uložit výstup do souboru " + outputFile.getPath());

                if (Settings.getInstance().beVerbose()) {
                    System.err.println("[ CHYBA ] " + e.getMessage());
                }

                if (Settings.getInstance().debugMode()) {
                    e.printStackTrace();
                }
            }

        } else {
            // stdout
            System.out.println(outputBuffer.toString());
        }

    }*/

}
