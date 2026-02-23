package cz.zsstudanka.skola.bakakeeper.components;

import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Testy pro proudové zpracování AES-GCM šifrovaných dat.
 *
 * @author Jan Hladěna
 */
class EncryptionStreamTest {

    final String password = "t4jnE.he$l@";
    final String message = "Příliš žluťoučký kůň úpěl ďábelské ódy v Bakalářích.";

    @Test
    void basicEncryptionTest() throws Exception {
        String encryptedMessage = EncryptionManager.encrypt(message, password.toCharArray());
        String decryptedMessage = EncryptionManager.decrypt(encryptedMessage, password.toCharArray());

        assertEquals(message, decryptedMessage);
    }

    @Test
    void objectStreamEncryptionTest() throws Exception {
        File tempFile = File.createTempFile("objectStreamEncryptionTest", ".dat");

        OutputStream ods = new EncryptionOutputStream(new FileOutputStream(tempFile.getAbsolutePath()), password.toCharArray());
        ObjectOutputStream outFile = new ObjectOutputStream(new GZIPOutputStream(ods));
        outFile.writeObject(message);
        outFile.close();

        InputStream ids = new EncryptionInputStream(new FileInputStream(tempFile.getAbsolutePath()), password.toCharArray());
        ObjectInputStream inStream = new ObjectInputStream(new GZIPInputStream(ids));
        String inputData = (String) inStream.readObject();
        inStream.close();
        ids.close();

        tempFile.delete();

        assertEquals(message, inputData);
    }

    @Test
    void gzipStreamEncryptionTest() throws Exception {
        File tempFile = File.createTempFile("gzipStreamEncryptionTest", ".dat");

        OutputStream ods = new EncryptionOutputStream(new FileOutputStream(tempFile.getAbsolutePath()), password.toCharArray());
        OutputStream outFile = new GZIPOutputStream(ods);
        outFile.write(this.message.getBytes());
        outFile.close();

        InputStream ids = new EncryptionInputStream(new FileInputStream(tempFile.getAbsolutePath()), password.toCharArray());

        try {
            ids = new GZIPInputStream(ids);
        } catch (Exception e) {
            e.printStackTrace();
        }

        StringBuilder textBuilder = new StringBuilder();
        try (Reader reader = new BufferedReader(new InputStreamReader(ids, StandardCharsets.UTF_8))) {
            int c;
            while ((c = reader.read()) != -1) {
                textBuilder.append((char) c);
            }
        }

        String inputData = textBuilder.toString();
        ids.close();

        tempFile.delete();

        assertEquals(message, inputData);
    }
}
