package cz.zsstudanka.skola.bakakeeper.connectors;

import cz.zsstudanka.skola.bakakeeper.settings.Settings;

import java.io.*;
import java.net.URL;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;

import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.GSSName;
import org.ietf.jgss.Oid;

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

            if (Settings.getInstance().beVerbose()) {
                System.err.println("[ CHYBA ] Nezdařilo se vytvořit dočasný soubor pro zapsání konfigurace Kerberos. ");
            }

            if (Settings.getInstance().debugMode()) {
                System.err.println("[ CHYBA ] " + e.getMessage());
                e.printStackTrace(System.err);
            }

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
                System.err.println("[ CHYBA ] Nebylo možné připravit konfiguraci pro Kerberos.");

                if (Settings.getInstance().beVerbose()) {
                    System.err.println("[ CHYBA ] -- " + e.getMessage());
                }

                if (Settings.getInstance().debugMode()) {
                    e.printStackTrace(System.err);
                }
            }

            // vytvoření vlastní konfigurace krb5.conf
            try {
                PrintStream outKrb5Config = new PrintStream(new FileOutputStream(customKrb5Config.getAbsolutePath()));
                outKrb5Config.println(newKrb5Config.toString());
                outKrb5Config.close();
            } catch (Exception e) {
                System.err.println("[ CHYBA ] Nebylo možné vytvořit konfiguraci pro Kerberos.");

                if (Settings.getInstance().beVerbose()) {
                    System.err.println("[ CHYBA ] -- " + e.getMessage());
                }

                if (Settings.getInstance().debugMode()) {
                    e.printStackTrace(System.err);
                }
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
                System.err.println("[ INFO ] Probíhá pokus o přihlášení.");
            }

            // This means that LoginContext.login is indeed equal to kinit in that after each of them, we have a TGT.
            // The service ticket will be obtained and used later - according to the action performed in Subject.doAs().
            // přihlášení
            lc.login();

            if (Settings.getInstance().beVerbose()) {
                System.err.println("[ OK ] Přihlášení Kerberos proběhlo úspěšně.");
            }

            // vygenerování tokenu služby
            Subject clientSubject = lc.getSubject();
            byte[] serviceTicket = (byte[]) Subject.doAs(clientSubject, new BakaKerberos());

            if (Settings.getInstance().beVerbose()) {
                System.err.println("[ OK ] Tiket služby MSSQLSvc byl vygenerován (přijato " + serviceTicket.length + " bajtů).");
            }

            // uložení tokenu do souboru?
            File krb5Ticket = new File("./ticket.key");
            try {
                FileOutputStream ticketStream = new FileOutputStream(krb5Ticket);
                ticketStream.write(serviceTicket);
                ticketStream.close();
            } catch (Exception e) {
                // TODO
                System.err.println("[ CHYBA ] Uložení tokenu se nezdařilo: " + e.getLocalizedMessage());
                e.printStackTrace(System.err);
            }

        } catch (LoginException le) {
            System.err.println("[ CHYBA ] Nebyl vygenerován tiket služby - Kerberos V přihlášení se nezdařilo:");

            if (Settings.getInstance().beVerbose()) {
                System.err.println("[ CHYBA ] -- " + le.getLocalizedMessage());
            }

            if (Settings.getInstance().debugMode()) {
                le.printStackTrace(System.err);
            }
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

            // **** Need to add User name of a valid user here in the form of username@domain.suffix ****
            // "who the client wishes to be" - to asi zase bakalářem
            // přihlášení pod systémovým účtem
            GSSName client = manager.createName(Settings.getInstance().getKrbUser(), GSSName.NT_USER_NAME);

            // **** Need to input the SPN for the SQL Server you want to connect to.  This is just used to get the Service Ticket ****
            // **** This does NOT actually connect to SQL Server. ****
            // SPN služby ve tvaru MSSQLSvc/SRV-ZS-XXX-APP0.ZSXXX.LOCAL:1433
            GSSName service = manager.createName("MSSQLSvc/" + Settings.getInstance().getSQL_hostFQDN() + ":1433", null);

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
            System.err.println("[ CHYBA ] Privilegovaná operace se nezdařila: " + ex.getMessage());

            if (Settings.getInstance().debugMode()) {
                ex.printStackTrace(System.err);
            }

            throw new PrivilegedActionException(ex);
        }
    }

}
