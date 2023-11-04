package cz.zsstudanka.bakakeeper.components;

import cz.zsstudanka.skola.bakakeeper.components.EncryptionInputStream;
import cz.zsstudanka.skola.bakakeeper.components.EncryptionManager;
import cz.zsstudanka.skola.bakakeeper.components.EncryptionOutputStream;
import org.junit.Test;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static org.junit.Assert.assertEquals;

/**
 * Testy pro proudové zpracování AES-GCM šifrovaných dat.
 *
 * @author Jan Hladěna
 */
public class EncryptionStreamTest {

    final String password = "t4jnE.he$l@";
    final String message = "Příliš žluťoučký kůň úpěl ďábelské ódy v Bakalářích.";

    @Test
    public void basicEncryptionTest() throws Exception {

        String encryptedMessage = EncryptionManager.encrypt(message, password.toCharArray());
        String decryptedMessage = EncryptionManager.decrypt(encryptedMessage, password.toCharArray());

        assertEquals(message, decryptedMessage);
    }

    @Test
    public void objectStreamEncryptionTest() throws Exception {

        File tempFile = File.createTempFile("objectStreamEncryptionTest", ".dat");

        // ---

        OutputStream ods;
        ods = new EncryptionOutputStream(new FileOutputStream(tempFile.getAbsolutePath()), password.toCharArray());
        ObjectOutputStream outFile = new ObjectOutputStream( new GZIPOutputStream( ods ));

        outFile.writeObject(message);
        outFile.close();

        // ---

        String inputData = new String();

        InputStream ids;
        ids = ( new EncryptionInputStream(new FileInputStream(tempFile.getAbsolutePath()), password.toCharArray()) );
        ObjectInputStream inStream = new ObjectInputStream(new GZIPInputStream( ids ));

        inputData = (String) inStream.readObject();
        inStream.close();

        ids.close();

        tempFile.delete();
        // ---
        assertEquals(message, inputData);
    }

    @Test
    public void gzipStreamEncryptionTest() throws Exception {

        File tempFile = File.createTempFile("gzipStreamEncryptionTest", ".dat");

        // ---

        OutputStream ods;
        ods = new EncryptionOutputStream(new FileOutputStream(tempFile.getAbsolutePath()), password.toCharArray());
        OutputStream outFile = new GZIPOutputStream( ods );

        outFile.write(this.message.getBytes());
        outFile.close();

        // ---

        String inputData = new String();

        InputStream ids;
        ids = ( new EncryptionInputStream(new FileInputStream(tempFile.getAbsolutePath()), password.toCharArray()) );

        try {
            ids = new GZIPInputStream((ids));
        } catch (Exception e) {
            e.printStackTrace();
        }

        StringBuilder textBuilder = new StringBuilder();
        try (Reader reader = new BufferedReader(new InputStreamReader
                (ids, Charset.forName(StandardCharsets.UTF_8.name())))) {
            int c = 0;
            while ((c = reader.read()) != -1) {
                textBuilder.append((char) c);
            }
        }

        inputData = textBuilder.toString();
        ids.close();

        tempFile.delete();
        // ---
        assertEquals(message, inputData);
    }
}
