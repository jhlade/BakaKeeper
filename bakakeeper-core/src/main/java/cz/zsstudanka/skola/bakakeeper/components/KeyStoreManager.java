package cz.zsstudanka.skola.bakakeeper.components;

import cz.zsstudanka.skola.bakakeeper.RuntimeContext;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLogType;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaPorts;
import cz.zsstudanka.skola.bakakeeper.settings.Settings;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocket;
import java.io.File;
import java.io.FileOutputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

/**
 * Inicializace lokálního úložiště klíčů pro komunikaci s lokálním AD - LDAPS.
 *
 * @author Jan Hladěna
 */
public class KeyStoreManager {

    public static Boolean initialize() {

        final String jks_passphrase = new String("BakaKeeper");

        // FQDN a port z konfigurace
        String ldapFqdn = Settings.getInstance().getLDAP_fqdn();
        int ldapPort = Settings.getInstance().getLdapPort();

        try {
            KeyStore jks = KeyStore.getInstance(KeyStore.getDefaultType());

            jks.load(null, jks_passphrase.toCharArray());
            FileOutputStream jksOutputStream = new FileOutputStream(Settings.getInstance().DEFAULT_JKS_FILE);

            SocketFactory factory = BakaSSLSocketFactory.getDefault();
            try {
                // navázání SSL spojení na LDAPS port z konfigurace
                if (Settings.getInstance().beVerbose()) {
                    ReportManager.log(EBakaLogType.LOG_VERBOSE, "Připojuji se na " + ldapFqdn + ":" + ldapPort + " pro získání certifikátu.");
                }
                SSLSocket socket = (SSLSocket) factory.createSocket(ldapFqdn, ldapPort);

                socket.startHandshake();
                Certificate[] certs = socket.getSession().getPeerCertificates();

                if (Settings.getInstance().beVerbose()) {
                    ReportManager.log(EBakaLogType.LOG_VERBOSE, "Získáno " + certs.length + " certifikátů.");
                }

                for (Certificate cert : certs) {
                    if (cert instanceof X509Certificate) {
                        // zápis certifikátu do úložiště pod FQDN
                        jks.setCertificateEntry(ldapFqdn, cert);
                    }
                }

            } catch (Exception e) {
                ReportManager.handleException("Nebylo možné navázat spojení se serverem " + ldapFqdn + ":" + ldapPort + ".", e);
                return false;
            }

            jks.store(jksOutputStream, jks_passphrase.toCharArray());
            jksOutputStream.close();

        } catch (Exception e) {
            ReportManager.handleException("Nebylo možné otevřít úložiště klíčů.", e);
            return false;
        }

        return true;
    }

    /**
     * Znovuobdržení certifikátu nejjednodušší možnou cestou - stávající úložiště je smazáno a je
     * provedena jeho reinicializace.
     *
     * @return výsledek nové inicializace
     */
    public static Boolean reinitialize() {

        File keystoreFile = new File(Settings.getInstance().DEFAULT_JKS_FILE);

        if (!keystoreFile.exists() || keystoreFile.delete()) {
            // nastavení příznaku inicializace
            RuntimeContext.FLAG_INIT = true;
            // nová inicializace úložiště
            return initialize();
        }

        return false;
    }

}
