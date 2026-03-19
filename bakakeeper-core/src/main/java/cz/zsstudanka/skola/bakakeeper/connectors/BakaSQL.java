package cz.zsstudanka.skola.bakakeeper.connectors;

import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import cz.zsstudanka.skola.bakakeeper.components.ReportManager;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLogType;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaPorts;
import cz.zsstudanka.skola.bakakeeper.settings.Settings;

import java.sql.*;
import javax.sql.rowset.CachedRowSet;
import javax.sql.rowset.RowSetProvider;

/**
 * Konektor pro Microsoft SQL Server.
 *
 * @author Jan Hladěna
 */
public class BakaSQL implements SQLConnector {

    /** instance SQL konektoru */
    private static BakaSQL instance = null;

    /** připojení k SQL */
    private Connection con = null;

    /** stav připojení */
    private Boolean valid = false;

    /**
     * Vytvoření instance připojení.
     *
     * @return instance SQL spojení
     */
    public static BakaSQL getInstance() {
        if (BakaSQL.instance == null) {
            BakaSQL.instance = new BakaSQL();
        }

        return BakaSQL.instance;
    }

    public BakaSQL() {
    }

    /**
     * Zjištění stavu navázání spojení.
     *
     * @return spojení je nenulové
     */
    public Boolean isConnected() {
        if (this.con == null) {
            return false;
        }

        try {
            if (this.con.isClosed()) {
                closeConnectionQuietly();
                valid = false;
                return false;
            }
            try {
                if (!this.con.isValid(2)) {
                    closeConnectionQuietly();
                    valid = false;
                    return false;
                }
            } catch (AbstractMethodError | SQLFeatureNotSupportedException ignored) {
                // fallback pouze na isClosed() pro starší/neúplné JDBC ovladače
            }
            return true;
        } catch (SQLException e) {
            closeConnectionQuietly();
            valid = false;
            return false;
        }
    }

    public Connection getConnection() {

        if (!isConnected()) {
            connect();
        }

        return this.con;
    }

    public ResultSet select(String sql) {

        if (!isConnected()) {
            return null;
        }

        try (Statement stmt = getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            CachedRowSet cached = RowSetProvider.newFactory().createCachedRowSet();
            cached.populate(rs);
            return cached;
        } catch (Exception e) {
            ReportManager.handleException("Nebylo možné provést SQL dotaz.", e);
            closeConnectionQuietly();
            valid = false;
        }

        return null;
    }

    /**
     * Připojení pomocí NTLMv2 autentizace.
     */
    private void connectNTLM() {
        try {
            closeConnectionQuietly();

            // JTDS ovladač
            Class.forName("net.sourceforge.jtds.jdbc.Driver");

            int port = Settings.getInstance().getSqlPort();
            String url = "jdbc:jtds:" + EBakaPorts.SRV_MSSQL.getScheme() + "://" + Settings.getInstance().getSqlHost()
                    + ":" + port
                    + "/" + Settings.getInstance().getSqlDatabase()
                    + ";domain=" + Settings.getInstance().getLdapDomain().toUpperCase() + ";useNTLMv2=true;CharacterSet=UTF-8";

            con = DriverManager.getConnection(url, Settings.getInstance().getSqlUser(), Settings.getInstance().getSqlPass());

            if (this.con != null) {
                this.debugInfo();
                valid = true;
            } else {
                valid = false;
            }

        } catch (Exception e) {
            ReportManager.handleException("Nebylo možné vytvořit NTLM spojení se SQL serverem.", e);
            closeConnectionQuietly();
            valid = false;
        }
    }

    /**
     * Připojení pomocí ověření protokolem Kerberos 5.
     */
    private void connectKerberos() {
        closeConnectionQuietly();

        StringBuilder conString = new StringBuilder();

        // připojovací řetězec integrovaného ověřování
        int port = Settings.getInstance().getSqlPort();
        conString.append("jdbc:");
        conString.append(EBakaPorts.SRV_MSSQL.getScheme() + "://" + Settings.getInstance().getSqlHost() + ":" + port + "; ");
        conString.append("DatabaseName=" + Settings.getInstance().getSqlDatabase() + "; ");

        // UPN + heslo pro Kerberos
        conString.append("user=" + Settings.getInstance().getKrbUser() + "; ");
        conString.append("password=" + Settings.getInstance().getSqlPass() + "; ");
        // SPN
        conString.append("ServerSpn=" + Settings.getInstance().getSqlSpn() + "; ");
        conString.append("integratedSecurity=true; authenticationScheme=JavaKerberos; loginTimeout=1; ");

        String connectionUrl = conString.toString();

        // vytvoření Krb5 tiketu
        try {
            BakaKerberos.generateTicket();
        } catch (Exception e) {
            ReportManager.handleException("Nevytvořil se tiket služby MSSQLSvc.", e);
        }

        // vytvoření spojení
        try {
            // identifikace třídy
            Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");

            SQLServerDataSource ds = new SQLServerDataSource();
            // základní řetězec
            ds.setURL(connectionUrl);

            this.con = ds.getConnection();

            if (this.con != null) {
                debugInfo();
                valid = true;
            } else {
                valid = false;
            }

        } catch (Exception e) {
            ReportManager.handleException("Nebylo možné vytvořit Kerberos spojení se SQL serverem.", e);
            closeConnectionQuietly();
            valid = false;
        }
    }

    /**
     * Ladící informace o spojení.
     */
    private void debugInfo() {
        if (Settings.getInstance().isVerbose()) {
            ReportManager.log("SQL připojení bylo vytvořeno.");
        }

        if (Settings.getInstance().isDebug()) {
            try {
                String authMethod = Settings.getInstance().isSqlNtlm()
                        ? "NTLMv2"
                        : (Settings.getInstance().isSqlKerberos() ? "Kerberos V" : "SQL Server");
                ReportManager.log(EBakaLogType.LOG_SQL, "Ověřování SQL: " + authMethod);
                String sqlUser = Settings.getInstance().isSqlAuth()
                        ? Settings.getInstance().getSqlUser()
                        : Settings.getInstance().getKrbUser();
                ReportManager.log(EBakaLogType.LOG_SQL, "Uživatel SQL: " + sqlUser);

                DatabaseMetaData dbmd = con.getMetaData();
                ReportManager.log(EBakaLogType.LOG_SQL, "dbmd:verze ovladače = " + dbmd.getDriverVersion());
                ReportManager.log(EBakaLogType.LOG_SQL, "dbmd:název ovladače = " + dbmd.getDriverName());
                ReportManager.log(EBakaLogType.LOG_SQL, "db software = " + dbmd.getDatabaseProductName());
                ReportManager.log(EBakaLogType.LOG_SQL, "db verze    = " + dbmd.getDatabaseProductVersion());
            } catch (Exception e) {
                ReportManager.handleException("Nebylo možné získat ladící informace o SQL spojení.", e);
            }
        }
    }

    /**
     * Připojení přímým SQL Server ověřením (user/password bez doménového ověření).
     * Používá Microsoft JDBC Driver.
     */
    private void connectSQL() {
        try {
            closeConnectionQuietly();
            Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");

            int port = Settings.getInstance().getSqlPort();
            SQLServerDataSource ds = new SQLServerDataSource();
            ds.setServerName(Settings.getInstance().getSqlHost());
            ds.setPortNumber(port);
            ds.setDatabaseName(Settings.getInstance().getSqlDatabase());
            ds.setUser(Settings.getInstance().getSqlUser());
            ds.setPassword(Settings.getInstance().getSqlPass());
            ds.setEncrypt("true");
            ds.setTrustServerCertificate(true);

            this.con = ds.getConnection();

            if (this.con != null) {
                debugInfo();
                valid = true;
            } else {
                valid = false;
            }

        } catch (Exception e) {
            ReportManager.handleException("Nebylo možné vytvořit SQL Server spojení.", e);
            closeConnectionQuietly();
            valid = false;
        }
    }

    /**
     * Vyvoření spojení.
     */
    public void connect() {

        if (isConnected()) {
            return;
        }

        closeConnectionQuietly();
        valid = false;

        if (Settings.getInstance().isSqlKerberos()) {
            connectKerberos();
        }

        if (Settings.getInstance().isSqlNtlm()) {
            connectNTLM();
        }

        if (Settings.getInstance().isSqlAuth()) {
            connectSQL();
        }
    }

    /**
     * Ověření dostupnosti/konektivity.
     *
     * @return
     */
    public boolean testSQL() {
        connect();
        return valid;
    }

    @Override
    public boolean testConnection() {
        return testSQL();
    }

    private void closeConnectionQuietly() {
        if (this.con == null) {
            return;
        }
        try {
            this.con.close();
        } catch (Exception ignored) {
            // spojení už může být neplatné nebo zavřené
        } finally {
            this.con = null;
        }
    }
}
