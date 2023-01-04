package cz.zsstudanka.skola.bakakeeper.constants;

/**
 * Číselník služeb
 *
 * @author Jan Hladěna
 */
public enum EBakaPorts {

    SRV_LDAP("ldap", 363, "LDAP"),
    SRV_LDAPS("ldaps", 636, "LDAPS"),
    SRV_GC("ldap", 3268, "AD Global Catalog"),
    SRV_GCS("ldaps", 3269, "AD Global Catalog (SSL)"),
    SRV_MSSQL("sqlserver", 1433, "MS SQL Server"),
    SRV_SMTP("smtp", 587, "Simple Mail Transfer Protocol");

    /** schéma */
    private final String scheme;

    /** číslo portu */
    private final int port;

    /** název služby */
    private final String description;

    /**
     *
     * @param scheme schéma
     * @param port číslo portu
     * @param description název nebo popis služby
     */
    EBakaPorts(String scheme, int port, String description) {
        this.scheme = scheme;
        this.port = port;
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public int getPort() {
        return this.port;
    }

    public java.lang.String getScheme() {
        return scheme;
    }
}
