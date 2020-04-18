package cz.zsstudanka.skola.bakakeeper.settings;

/**
 * Informace o verzi a sestavení programu.
 *
 * @author Jan Hladěna
 */
public class Version {

    private static Version instance = null;

    private final String name         = "BakaKeeper";
    private final String author       = "Jan Hladěna <jan.hladena@zs-studanka.cz>";
    private final String organization = "ZŠ Pardubice - Studánka";
    private final String version      = "1.0.0";
    private final String year         = "2020";

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
     * @return víceřádkový informační řetězec
     */
    public String getInfo() {
        StringBuilder info = new StringBuilder();

        info.append(name);
        info.append(" ");
        info.append(version);
        info.append("\n");
        info.append("(c)");
        info.append(" ");
        info.append(year);
        info.append(" ");
        info.append(author);
        info.append(", ");
        info.append(organization);
        info.append("\n");

        return info.toString();
    }
}
