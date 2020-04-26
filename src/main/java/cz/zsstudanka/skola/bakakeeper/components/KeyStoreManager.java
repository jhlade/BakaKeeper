package cz.zsstudanka.skola.bakakeeper.components;

import cz.zsstudanka.skola.bakakeeper.App;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLogType;
import cz.zsstudanka.skola.bakakeeper.settings.Settings;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocket;
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
    static final Integer LDAPS_PORT = 636;

    public static Boolean initialize() {

        try {
            KeyStore jks = KeyStore.getInstance(KeyStore.getDefaultType());
            jks.load(null, App.PASSPHRASE.toCharArray());
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
                    if(cert instanceof X509Certificate) {
                        jks.setCertificateEntry(Settings.getInstance().getLDAP_host(), cert);
                    }
                }

            } catch (Exception e) {
                ReportManager.handleException("Nebylo možné navázast spojení se serverem.", e);
                return false;
            }

            jks.store(jksOutputStream, App.PASSPHRASE.toCharArray());
            jksOutputStream.close();

        } catch (Exception e) {
            ReportManager.handleException("Nebylo možné otevřít úložiště klíčů.", e);
            return false;
        }

        return true;
    }

}
