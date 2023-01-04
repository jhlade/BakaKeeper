package cz.zsstudanka.skola.bakakeeper.connectors;

import cz.zsstudanka.skola.bakakeeper.components.ReportManager;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLogType;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaPorts;
import cz.zsstudanka.skola.bakakeeper.settings.Settings;
import org.ietf.jgss.*;

import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import java.io.*;
import java.net.URL;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;

/**
 * Vyjednání spojení pomocí protokolu Kerberos V.
 *
 * @author Jan Hladěna
 */
public class BakaKerberos implements PrivilegedExceptionAction {

    /**
     * Inicializace nastavení pro práci se službou Kerberos V.
     */
    public static void systemSettings() {

        // vytvoření nové konfigurace krb5.conf
        File customKrb5Config;

        try {
            // vytvoří se dočasný soubor ve výchozím tmp adresáři
            customKrb5Config = File.createTempFile("bakakrb5_", ".conf", null);
            customKrb5Config.deleteOnExit();
        } catch (Exception e) {
            ReportManager.handleException("Nezdařilo se vytvořit dočasný soubor pro zapsání konfigurace Kerberos.", e);
            customKrb5Config = null;
        }

        if (customKrb5Config.isFile()) {
            InputStream krb5Stream = BakaKerberos.class.getResourceAsStream("/krb5.conf");

            StringBuilder newKrb5Config = new StringBuilder();

            // načtení a nahrazení šablonových dat
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(krb5Stream));

                String confLine;
                while ((confLine = reader.readLine()) != null) {

                    newKrb5Config.append(
                            confLine.replace("{DOMAIN}", Settings.getInstance().getLocalDomain().toUpperCase())
                            .replace("{AD_SRV}", Settings.getInstance().getLDAP_fqdn().toUpperCase())
                    );
                    newKrb5Config.append("\n");
                }

                reader.close();

            } catch (Exception e) {
                ReportManager.handleException("Nebylo možné připravit konfiguraci pro Kerberos.", e);
            }

            // vytvoření vlastní konfigurace krb5.conf
            try {
                PrintStream outKrb5Config = new PrintStream(new FileOutputStream(customKrb5Config.getAbsolutePath()));
                outKrb5Config.println(newKrb5Config.toString());
                outKrb5Config.close();
            } catch (Exception e) {
                ReportManager.handleException("Nebylo možné vytvořit konfiguraci pro Kerberos.", e);
            }
        }

        // systémové konstanty

        // cesta ke konfiguraci pro Kerberos V
        System.setProperty("java.security.krb5.conf", customKrb5Config.getAbsolutePath());

        // použití interní konfigurace sezení - SQLJDBCDriver.conf
        URL url = BakaKerberos.class.getClassLoader().getResource("SQLJDBCDriver.conf");
        System.setProperty("java.security.auth.login.config", url.toExternalForm());

        System.setProperty("javax.security.auth.useSubjectCredsOnly", "false");

        // Kerberos - podrobný ladící režim
        if (Settings.getInstance().debugMode()) {
            System.setProperty("sun.security.krb5.debug", "true");
        }
    }


    /**
     * Vyžádání a vytvoření tiketu služby MSSQLSvc.
     *
     * @throws LoginException
     * @throws Exception
     */
    public static void generateTicket() throws LoginException, Exception {

        // globální nastavení
        BakaKerberos.systemSettings();

        // inicializace v kontextu systémového účtu
        LoginContext lc = new LoginContext("SQLJDBCDriver", new BakaKerberosCallback());

        try {
            // attempt authentication
            if (Settings.getInstance().beVerbose()) {
                ReportManager.log(EBakaLogType.LOG_VERBOSE, "Probíhá pokus o přihlášení prostřednictvím Kerberos.");
            }

            // přihlášení
            lc.login();

            if (Settings.getInstance().beVerbose()) {
                ReportManager.log(EBakaLogType.LOG_OK, "Přihlášení Kerberos proběhlo úspěšně.");
            }

            // vygenerování tokenu služby
            Subject clientSubject = lc.getSubject();
            byte[] serviceTicket = (byte[]) Subject.doAs(clientSubject, new BakaKerberos());

            if (Settings.getInstance().beVerbose()) {
                ReportManager.log(EBakaLogType.LOG_OK, "Tiket služby MSSQLSvc byl vygenerován (přijato " + serviceTicket.length + " bajtů).");
            }

            // uložení tokenu do souboru?
            File krb5Ticket = new File("./ticket.key");
            try {
                FileOutputStream ticketStream = new FileOutputStream(krb5Ticket);
                ticketStream.write(serviceTicket);
                ticketStream.close();
            } catch (Exception e) {
                ReportManager.handleException("Uložení tokenu se nezdařilo.", e);
            }
        } catch (LoginException le) {
            ReportManager.handleException("Nebyl vygenerován tiket služby - Kerberos přihlášení se nezdařilo.", le);
        }
    }

    /**
     * Privilegovaná operace.
     *
     * @return token služby
     * @throws Exception
     */
    public Object run() throws Exception {

        // callback - kopie tiketu služby
        try {
            Oid kerberos5Oid = new Oid("1.2.840.113554.1.2.2");
            GSSManager manager = GSSManager.getInstance();

            // přihlášení pod systémovým účtem
            GSSName client = manager.createName(Settings.getInstance().getKrb_user(), GSSName.NT_USER_NAME);

            // SPN služby ve tvaru MSSQLSvc/SRV-ZS-XXX-APP0.ZSXXX.LOCAL:1433
            GSSName service = manager.createName("MSSQLSvc/" + Settings.getInstance().getSQL_hostFQDN() + ":" + EBakaPorts.SRV_MSSQL.getPort(), null);

            GSSCredential clientCredentials = manager.createCredential(null, 8*60*60, kerberos5Oid, GSSCredential.INITIATE_ONLY);
            GSSContext gssContext = manager.createContext(service, kerberos5Oid, clientCredentials, GSSContext.DEFAULT_LIFETIME);

            // Vyžaduje SQL JDBC Driver od Microsoftu
            gssContext.requestCredDeleg(true);
            gssContext.requestMutualAuth(true);
            gssContext.requestInteg(true);

            // vytvoření tiketu
            byte[] serviceTicket = gssContext.initSecContext(new byte[0], 0, 0);
            //gssContext.dispose();
            return serviceTicket;

        } catch (Exception ex) {
            ReportManager.handleException("Privilegovaná operace se nezdařila.", ex);
            throw new PrivilegedActionException(ex);
        }
    }

}
