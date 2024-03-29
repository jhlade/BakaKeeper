package cz.zsstudanka.skola.bakakeeper.components;

import cz.zsstudanka.skola.bakakeeper.App;
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

    // LDAPS port
    static final Integer LDAPS_PORT = EBakaPorts.SRV_LDAPS.getPort();

    public static Boolean initialize() {

        final String jks_passphrase = new String("BakaKeeper");

        try {
            KeyStore jks = KeyStore.getInstance(KeyStore.getDefaultType());

            jks.load(null, jks_passphrase.toCharArray());
            FileOutputStream jksOutputStream = new FileOutputStream(Settings.getInstance().DEFAULT_JKS_FILE);

            SocketFactory factory = BakaSSLSocketFactory.getDefault();
            try {
                // navázání SSL spojení na výchozí LDAPS port 636
                SSLSocket socket = (SSLSocket) factory.createSocket(Settings.getInstance().getLDAP_host(), LDAPS_PORT);

                socket.startHandshake();
                Certificate[] certs = socket.getSession().getPeerCertificates();

                if (Settings.getInstance().beVerbose()) {
                    ReportManager.log(EBakaLogType.LOG_VERBOSE, "Získáno " + certs.length + " certifikátů.");
                }

                for (Certificate cert : certs) {
                    if (cert instanceof X509Certificate) {
                        // zápis certifikátu do úložiště
                        jks.setCertificateEntry(Settings.getInstance().getLDAP_host(), cert);
                    }
                }

            } catch (Exception e) {
                ReportManager.handleException("Nebylo možné navázast spojení se serverem.", e);
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
            App.FLAG_INIT = true;
            // nová inicializace úložiště
            return initialize();
        }

        return false;
    }

}
