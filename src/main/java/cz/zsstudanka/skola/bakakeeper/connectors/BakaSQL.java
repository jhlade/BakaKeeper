package cz.zsstudanka.skola.bakakeeper.connectors;

import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import cz.zsstudanka.skola.bakakeeper.components.ReportManager;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLogType;
import cz.zsstudanka.skola.bakakeeper.settings.Settings;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.Oid;

import java.sql.*;

/**
 * Konektor pro Microsoft SQL Server.
 *
 * @author Jan Hladěna
 */
public class BakaSQL {

    /** instance SQL konektoru */
    private static BakaSQL instance = null;

    /** připojení k SQL */
    private Connection con = null;

    /** stav připojení */
    private Boolean valid;

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
        return (this.con != null) ? true : false;
    }

    public Connection getConnection() {
        return this.con;
    }

    public ResultSet select(String sql) {

        if (!isConnected()) {
            return null;
        }

        try {
            Statement stmt = BakaSQL.getInstance().getConnection().createStatement();
            ResultSet rs = stmt.executeQuery(sql);

            return rs;
        } catch (Exception e) {
            ReportManager.handleException("Nebylo možné provést SQL dotaz.", e);
        }

        return null;
    }

    /**
     * Připojení pomocí NTLMv2 autentizace.
     */
    private void connectNTLM() {
        try {
            // JTDS ovladač
            Class.forName("net.sourceforge.jtds.jdbc.Driver");

            String url = "jdbc:jtds:sqlserver://" + Settings.getInstance().getSQL_host()
                    +"/" + Settings.getInstance().getSQL_database()
                    +";domain=" + Settings.getInstance().getLocalDomain().toUpperCase() + ";useNTLMv2=true;CharacterSet=UTF-8";

            con = DriverManager.getConnection(url, Settings.getInstance().getUser(), Settings.getInstance().getPass());

            if (this.con != null) {
                this.debugInfo();
                valid = true;
            } else {
                valid = false;
            }

        } catch (Exception e) {
            ReportManager.handleException("Nebylo možné vytvořit NTLM spojení se SQL serverem.", e);
            valid = false;
        }
    }

    /**
     * Připojení pomocí ověření protokolem Kerberos 5.
     */
    private void connectKerberos() {

        StringBuilder conString = new StringBuilder();

        // připojovací řetězec integrovaného ověřování
        conString.append("jdbc:");
        conString.append("sqlserver://" + Settings.getInstance().getSQL_host() + ":1433;");
        conString.append("ServerSpn=MSSQLSvc/" + Settings.getInstance().getSQL_hostFQDN() + ":1433@" + Settings.getInstance().getLocalDomain().toUpperCase() + ";");
        conString.append("DatabaseName=" + Settings.getInstance().getSQL_database() + ";");
        if (Settings.getInstance().useSSL()) {
            conString.append("EncryptionMethod=ssl;");
        }
        conString.append("integratedSecurity=true;authenticationScheme=JavaKerberos");

        String connectionUrl = conString.toString();

        if (Settings.getInstance().debugMode()) {
            System.out.println("[ DEBUG ] " + connectionUrl);
        }

        // vytvoření Krb5 tiketu
        try {
            BakaKerberos.generateTicket();
            //BakaKerberos.systemSettings();
        } catch (Exception e) {
            ReportManager.handleException("Nevytvořil se tiket služby MSSQLSvc.", e);
        }

        // vytvoření spojení
        try {
            // identifikace třídy
            //Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");

            SQLServerDataSource ds = new SQLServerDataSource();
            ds.setURL(connectionUrl);
            //ds.setIntegratedSecurity(true);
            //ds.setAuthenticationScheme("JavaKerberos");
            //ds.setUser(Settings.getInstance().getKrbUser());
            //ds.setPassword(Settings.getInstance().getPass());
            //GSSName client = GSSManager.getInstance().createName(Settings.getInstance().getKrbUser(), GSSName.NT_USER_NAME);
            GSSCredential credential = GSSManager.getInstance().createCredential(null, GSSCredential.DEFAULT_LIFETIME, new Oid("1.2.840.113554.1.2.2"), GSSCredential.ACCEPT_ONLY);
            ds.setGSSCredentials(credential);

            this.con = ds.getConnection();

            //this.con = java.sql.DriverManager.getConnection(connectionUrl);
            //this.con = java.sql.DriverManager.getConnection(connectionUrl, Settings.getInstance().getKrbUser(), Settings.getInstance().getPass());

            if (this.con != null) {
                debugInfo();
                valid = true;
            } else {
                valid = false;
            }

        } catch (Exception e) {
            ReportManager.handleException("Nebylo možné vytvořit Kerberos spojení se SQL serverem.", e);
            valid = false;
        }
    }

    /**
     * Ladící informace o spojení.
     */
    private void debugInfo() {
        if (Settings.getInstance().beVerbose()) {
            ReportManager.log("SQL připojení bylo vytvořeno.");
        }

        if (Settings.getInstance().debugMode()) {
            try {
                ReportManager.log(EBakaLogType.LOG_SQL, "Ověřování SQL: " + (Settings.getInstance().sql_NTLM() ? "NTLMv2" : "Kerberos V"));
                ReportManager.log(EBakaLogType.LOG_SQL, "Uživatel SQL: " + Settings.getInstance().getKrb_user());

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
     * Vyvoření spojení.
     */
    public void connect() {

        if (isConnected()) {
            return;
        }

        if (Settings.getInstance().sql_Kerberos()) {
            connectKerberos();
        }

        if (Settings.getInstance().sql_NTLM()) {
            connectNTLM();
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
}
