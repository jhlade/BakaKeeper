package cz.zsstudanka.skola.bakakeeper.settings;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Informace o verzi a sestavení programu.
 *
 * <p>Verze se načítá z {@code version.properties} generovaného Mavenem
 * (resource filtering). Pro změnu verze stačí upravit {@code <version>}
 * v parent {@code pom.xml} – Maven dosadí hodnotu automaticky.
 * Suffix {@code -SNAPSHOT} se v zobrazení nahrazuje na {@code -dev}.</p>
 *
 * @author Jan Hladěna
 */
public class Version {

    private static final String FALLBACK_VERSION = "26.3.0-dev";

    private static Version instance = null;

    private final String name         = "BakaKeeper";
    private final String purpose      = "Synchronizační nástroj evidence žáků v programu Bakaláři s uživatelskými účty vedenými v Active Directory.";
    private final String author       = "Jan Hladěna <jan.hladena@zs-studanka.cz>";
    private final String organization = "ZŠ Pardubice - Studánka";
    private final String version;
    private final String year         = "2020";

    private Version() {
        this.version = loadVersion();
    }

    /** Načte verzi z version.properties (Maven resource filtering), fallback na FALLBACK_VERSION. */
    private static String loadVersion() {
        try (InputStream is = Version.class.getResourceAsStream("/version.properties")) {
            if (is != null) {
                Properties props = new Properties();
                props.load(is);
                String v = props.getProperty("bk.version", FALLBACK_VERSION);
                // Maven SNAPSHOT → zobrazení jako dev
                return v.replace("-SNAPSHOT", "-dev");
            }
        } catch (IOException ignored) {
            // fallback
        }
        return FALLBACK_VERSION;
    }

    /**
     * Implementováno jako singleton.
     *
     * @return instance informací o verzi programu
     */
    public static Version getInstance() {
        if (Version.instance == null) {
            Version.instance = new Version();
        }

        return Version.instance;
    }

    /**
     * Verze programu.
     *
     * @return verze programu
     */
    public String getVersion() {
        return version;
    }

    /**
     * Jméno programu.
     *
     * @return název programu
     */
    public String getName() {
        return name;
    }

    /**
     * Autor programu.
     *
     * @return jméno autora
     */
    public String getAuthor() {
        return author;
    }

    /**
     * Hlavička organizace.
     *
     * @return název organizace
     */
    public String getOrganization() {
        return organization;
    }

    /**
     * Rok sestavení, copyright.
     *
     * @return rok
     */
    public String getYear() {
        return year;
    }

    /**
     * Štítek programu a jeho verze.
     *
     * @return krátká informace o názvu a verzi
     */
    public String getTag() {
        StringBuilder tag = new StringBuilder();

        tag.append(name);
        tag.append(" ");
        tag.append(version);

        return tag.toString();
    }

    /**
     * Textová informace o programu.
     *
     * @param fullLicense zobrazení plného znění licence
     *
     * @return víceřádkový informační řetězec
     */
    public String getInfo(Boolean fullLicense) {
        StringBuilder info = new StringBuilder();

        info.append(name + " " + version);
        if (fullLicense) {
            info.append(" - " + purpose);
        }
        info.append("\n");
        info.append("Copyright (c) " + year + " " + author + ", " + organization + "\n");

        if (fullLicense) {
            info.append("\n");

            // GPL 3.0 cz
            info.append("Tento program je svobodný software: můžete jej šířit a upravovat\n"
                      + "podle ustanovení Obecné veřejné licence GNU (GNU General Public\n"
                      + "Licence), vydávané Free Software Foundation a to buď podle 3. verze\n"
                      + "této Licence, nebo (podle vašeho uvážení) kterékoli pozdější verze.\n");

            info.append("\n");

            info.append("Tento program je rozšiřován v naději, že bude užitečný,\n"
                      + "avšak BEZ JAKÉKOLIV ZÁRUKY. Neposkytují se ani odvozené záruky\n"
                      + "PRODEJNOSTI anebo VHODNOSTI PRO URČITÝ ÚČEL. Další podrobnosti\n"
                      + "hledejte v Obecné veřejné licenci GNU.\n");

            info.append("\n");

            info.append("Kopii Obecné veřejné licence GNU jste měli obdržet spolu s tímto programem.\n"
                      + "Pokud se tak nestalo, najdete ji zde: <http://www.gnu.org/licenses/>.\n");

            info.append("\n");
        }

        return info.toString();
    }

    /**
     * Textová informace o programu.
     *
     * @return stručný informační řetězec
     */
    public String getInfo() {
        return getInfo(false);
    }
}
