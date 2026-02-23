package cz.zsstudanka.skola.bakakeeper.connectors;

import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import cz.zsstudanka.skola.bakakeeper.components.ReportManager;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLogType;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaPorts;
import cz.zsstudanka.skola.bakakeeper.settings.Settings;

import java.sql.*;

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

        if (!isConnected()) {
            connect();
        }

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

            String url = "jdbc:jtds:" + EBakaPorts.SRV_MSSQL.getScheme() + "://" + Settings.getInstance().getSQL_host()
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
        conString.append(EBakaPorts.SRV_MSSQL.getScheme() + "://" + Settings.getInstance().getSQL_host() + ":" + Integer.toString(EBakaPorts.SRV_MSSQL.getPort()) + "; ");
        conString.append("DatabaseName=" + Settings.getInstance().getSQL_database() + "; ");

        // UPN + heslo pro Kerberos
        conString.append("user=" + Settings.getInstance().getKrb_user() + "; ");
        conString.append("password=" + Settings.getInstance().getPass() + "; ");
        // SPN
        conString.append("ServerSpn=" + Settings.getInstance().getSQL_SPN() + "; ");

        if (Settings.getInstance().useSSL()) {
            conString.append("EncryptionMethod=ssl; encrypt=false; integratedSecurity=true; authenticationScheme=JavaKerberos; loginTimeout=1; ");
        }

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
