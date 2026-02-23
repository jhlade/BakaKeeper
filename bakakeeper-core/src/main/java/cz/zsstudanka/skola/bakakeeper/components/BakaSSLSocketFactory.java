package cz.zsstudanka.skola.bakakeeper.components;

import javax.net.SocketFactory;
import javax.net.ssl.*;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.*;

/**
 * Prázdná socket factory pro dočasný příjem všech certifikátů.
 *
 * @author Jan Hladěna
 */
public class BakaSSLSocketFactory extends SSLSocketFactory {

    private SSLSocketFactory socketFactory;

    public BakaSSLSocketFactory() {
        try {
            SSLContext sslctx = SSLContext.getInstance("SSL");
            sslctx.init(null, new TrustManager[]{ new BakaTrustManager() }, new SecureRandom());
            this.socketFactory = sslctx.getSocketFactory();
        } catch (NoSuchAlgorithmException e) {
            ReportManager.handleException("Nepodporovaný algoritmus.", e);
        } catch (KeyManagementException e) {
            ReportManager.handleException("Chyba při zpracování klíče.", e);
        }
    }

    public static SocketFactory getDefault() {
        return new BakaSSLSocketFactory();
    }

    @Override
    public String[] getDefaultCipherSuites() {
        return socketFactory.getDefaultCipherSuites();
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return socketFactory.getSupportedCipherSuites();
    }

    @Override
    public Socket createSocket(Socket socket, String string, int i, boolean bln) throws IOException {
        return socketFactory.createSocket(socket, string, i, bln);
    }

    @Override
    public Socket createSocket(String string, int i) throws IOException, UnknownHostException {
        return socketFactory.createSocket(string, i);
    }

    @Override
    public Socket createSocket(String string, int i, InetAddress ia, int i1) throws IOException, UnknownHostException {
        return socketFactory.createSocket(string, i, ia, i1);
    }

    @Override
    public Socket createSocket(InetAddress ia, int i) throws IOException {
        return socketFactory.createSocket(ia, i);
    }

    @Override
    public Socket createSocket(InetAddress ia, int i, InetAddress ia1, int i1) throws IOException {
        return socketFactory.createSocket(ia, i, ia1, i1);
    }
}
