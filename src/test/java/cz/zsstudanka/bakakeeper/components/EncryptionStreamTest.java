package cz.zsstudanka.bakakeeper.components;

import cz.zsstudanka.skola.bakakeeper.components.EncryptionInputStream;
import cz.zsstudanka.skola.bakakeeper.components.EncryptionManager;
import cz.zsstudanka.skola.bakakeeper.components.EncryptionOutputStream;
import org.junit.Test;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

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

        String encryptedMessage = EncryptionManager.encrypt(message, password);
        String decryptedMessage = EncryptionManager.decrypt(encryptedMessage, password);

        assertEquals(message, decryptedMessage);
    }

    @Test
    public void streamEncryptionTest() throws Exception {

        File tempFile = File.createTempFile("streamEncryptionTest", ".dat");

        // ---

        OutputStream ods;
        ods = new EncryptionOutputStream( new FileOutputStream(tempFile.getAbsolutePath()) , password);

        ods.write(message.getBytes());
        ods.close();

        // ---

        String inputData;

        InputStream ids;
        ids = new EncryptionInputStream( new FileInputStream(tempFile.getAbsolutePath()), password);

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
