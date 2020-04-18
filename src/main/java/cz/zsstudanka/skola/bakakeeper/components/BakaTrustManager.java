package cz.zsstudanka.skola.bakakeeper.components;

import cz.zsstudanka.skola.bakakeeper.App;
import cz.zsstudanka.skola.bakakeeper.settings.Settings;

import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * Správce důvěryhodnosti. Otevírá vlastní JKS úložiště s certifikátem LDAP serveru získaného při inicializaci.
 *
 * @author Jan Hladěna
 */
public class BakaTrustManager implements X509TrustManager {

    /** výchozí chování */
    private X509TrustManager defaultTM = null;
    /** vlastní úložiště klíčů */
    private X509TrustManager bakaTM = null;

    public BakaTrustManager() {

        // globální továrna správců důvěryhodnosti
        TrustManagerFactory tmf = null;

        try {
            tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init((KeyStore) null);
        } catch (NoSuchAlgorithmException | KeyStoreException e) {
            if (Settings.getInstance().beVerbose()) {
                System.err.println("[ CHYBA ] Nebylo možné použít výchozí úložiště klíčů.");
            }

            if (Settings.getInstance().debugMode()) {
                System.err.println("[ CHYBA ] " + e.getLocalizedMessage());
                e.printStackTrace(System.err);
            }
        }

        // získání prvního správce jako výchozího
        this.defaultTM = (X509TrustManager) tmf.getTrustManagers()[0];

        // otevření vlastního úložiště klíčů
        try {
            FileInputStream bakaJKS = new FileInputStream(Settings.getInstance().DEFAULT_JKS_FILE);
            KeyStore bakaTS = KeyStore.getInstance(KeyStore.getDefaultType());
            bakaTS.load(bakaJKS, App.PASSPHRASE.toCharArray());
            bakaJKS.close();

            try {
                // reinicializace továrny s vlastním úložištěm klíčů
                tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(bakaTS);

                // získání vlastního správce
                this.bakaTM = (X509TrustManager) tmf.getTrustManagers()[0];

            } catch (NoSuchAlgorithmException e) {
                if (Settings.getInstance().beVerbose()) {
                    System.err.println("[ CHYBA ] Nepodporovaný algoritmus.");
                }

                if (Settings.getInstance().debugMode()) {
                    System.err.println("[ CHYBA ] " + e.getLocalizedMessage());
                    e.printStackTrace(System.err);
                }
            }

        } catch (FileNotFoundException | KeyStoreException e) {
            if (Settings.getInstance().beVerbose()) {
                System.err.println("[ CHYBA ] Nebylo nalezeno úložiště certifikátů.");
            }

            if (Settings.getInstance().debugMode()) {
                System.err.println("[ CHYBA ] " + e.getLocalizedMessage());
                e.printStackTrace(System.err);
            }
        } catch (CertificateException e) {
            if (Settings.getInstance().beVerbose()) {
                System.err.println("[ CHYBA ] Neplatný certifikát.");
            }

            if (Settings.getInstance().debugMode()) {
                System.err.println("[ CHYBA ] " + e.getLocalizedMessage());
                e.printStackTrace(System.err);
            }
        } catch (NoSuchAlgorithmException e) {
            if (Settings.getInstance().beVerbose()) {
                System.err.println("[ CHYBA ] Nepodporovaný algoritmus.");
            }

            if (Settings.getInstance().debugMode()) {
                System.err.println("[ CHYBA ] " + e.getLocalizedMessage());
                e.printStackTrace(System.err);
            }
        } catch (IOException e) {
            if (Settings.getInstance().beVerbose()) {
                System.err.println("[ CHYBA ] Nebylo možné zpracovat certifikát.");
            }

            if (Settings.getInstance().debugMode()) {
                System.err.println("[ CHYBA ] " + e.getLocalizedMessage());
                e.printStackTrace(System.err);
            }
        }

    }

    @Override
    public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
        // výchozí chování - nepracuje se s klienty
        this.defaultTM.checkClientTrusted(x509Certificates, s);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {

        // probíhá manuální inicializace - certifikát bude automaticky přijat
        if (App.FLAG_INIT) {
            return;
        }

        try {
            // ověření serveru proti vlastnímu úložišti důvěryhodných klíčů
            this.bakaTM.checkServerTrusted(x509Certificates, s);
        } catch (Exception e) {
            // fallback na výchozí chování ověřování serverových certifikátů
            this.defaultTM.checkServerTrusted(x509Certificates, s);
        }
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        // výchozí chování
        return this.defaultTM.getAcceptedIssuers();
    }
}
