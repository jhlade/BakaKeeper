package cz.zsstudanka.skola.bakakeeper.components;

import cz.zsstudanka.skola.bakakeeper.settings.Settings;
import cz.zsstudanka.skola.bakakeeper.settings.Version;

/**
 * Nápověda.
 *
 * @author Jan Hladěna
 */
public class HelpManager {

    public static String printHelp() {

        StringBuilder helpMessage = new StringBuilder();

        Version appVersion = Version.getInstance();
        helpMessage.append(appVersion.getInfo());
        helpMessage.append("\n");

        helpMessage.append("Použití programu: ");
        helpMessage.append("\n");

        helpMessage.append("--help\t\tVypíše tuto nápovědu.");
        helpMessage.append("\n");

        helpMessage.append("\n");
        helpMessage.append("--check\t\tProvede kontrolu nastavení programu.");
        helpMessage.append("\n");
        helpMessage.append("\t\t[-passphrase heslo ke konfiguraci]");
        helpMessage.append("\n");
        helpMessage.append("\t\t[--verbose]\tladící informace");
        helpMessage.append("\n");

        helpMessage.append("\n");
        helpMessage.append("--status\tProvede pravidelnou kontrolu stavu synchronizace.");
        helpMessage.append("\n");
        helpMessage.append("\t\t[-passphrase heslo ke konfiguraci]");
        helpMessage.append("\n");
        helpMessage.append("\t\t[--verbose]\tladící informace");
        helpMessage.append("\n");

        helpMessage.append("\n");
        helpMessage.append("--sync\t\tProvede synchronizaci.");
        helpMessage.append("\n");
        helpMessage.append("\t\t[-passphrase heslo ke konfiguraci]");
        helpMessage.append("\n");
        helpMessage.append("\t\t[--verbose]\tladící informace");
        helpMessage.append("\n");

        helpMessage.append("\n");
        helpMessage.append("--audit\t\tProvede podrobný bezpečnostní audit.");
        helpMessage.append("\n");
        helpMessage.append("\t\t[-passphrase heslo ke konfiguraci]");
        helpMessage.append("\n");
        helpMessage.append("\t\t[--verbose]\tladící informace");
        helpMessage.append("\n");

        if (Settings.getInstance().beVerbose()) {
            helpMessage.append("\n");
            helpMessage.append("--id uzivatelske.jmeno\n\t\tIdentifikace účtu uživatele.");
            helpMessage.append("\n");
            helpMessage.append("\t\t[-passphrase heslo ke konfiguraci]");
            helpMessage.append("\n");
            helpMessage.append("\t\t[--verbose]\tladící informace");
            helpMessage.append("\n");

            helpMessage.append("\n");
            helpMessage.append("--reset uzivatelske.jmeno\n\t\tProvede reset hesla uživatele.");
            helpMessage.append("\n");
            helpMessage.append("\t\t[-passphrase heslo ke konfiguraci]");
            helpMessage.append("\n");
            helpMessage.append("\t\t[--verbose]\tladící informace");
            helpMessage.append("\n");
        }

        helpMessage.append("\n");
        helpMessage.append("--export\tProvede export všech údajů o žácích ve formátu CSV.");
        helpMessage.append("\n");
        helpMessage.append("\t\t[-o seznam.csv]\tvýstupní soubor CSV; pokud není uveden, použije se standardní výstup");
        helpMessage.append("\n");
        helpMessage.append("\t\t[-passphrase heslo ke konfiguraci]");
        helpMessage.append("\n");
        helpMessage.append("\t\t[--verbose]\tladící informace");
        helpMessage.append("\n");

        helpMessage.append("\n");
        helpMessage.append("--init\t\tProběhne inicializace parametrů programu:");
        helpMessage.append("\n");
        helpMessage.append("\t\t-f soubor.conf\tnačtení prvotního nastavení ze souboru");
        helpMessage.append("\n");
        helpMessage.append("\t\t--interactive\tinteraktivní režim");
        helpMessage.append("\n");
        helpMessage.append("\t\t[-passphrase heslo ke konfiguraci]");
        helpMessage.append("\n");

        if (Settings.getInstance().beVerbose()) {
            helpMessage.append("\n");
            helpMessage.append("OK, pro ještě podrobnější ladění použijte --debug.");
            helpMessage.append("\n");
        }

        if (Settings.getInstance().debugMode()) {
            helpMessage.append("\n");
            helpMessage.append("    (___)     Tento program bohužel     (___)     ");
            helpMessage.append("\n");
            helpMessage.append("    (o o)     nemá schopnosti           (o o)     ");
            helpMessage.append("\n");
            helpMessage.append("     \\ /      svaté krávy.               \\ /      ");
            helpMessage.append("\n");
            helpMessage.append("      O       Bů.                         O       ");
            helpMessage.append("\n");
        }

        helpMessage.append("\n");

        return helpMessage.toString();
    }

}
