package cz.zsstudanka.bakakeeper.components;

import cz.zsstudanka.skola.bakakeeper.components.EncryptionManager;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Testy pro proudové zpracování AES/GCM šifrovaných dat.
 *
 * @author Jan Hladěna
 */
public class EncryptionStreamTest {

    @Test
    public void basicEncryptionTest() throws Exception {

        String password = "t4jnE.he$l@";
        String message = "Příliš žluťoučký kůň úpěl ďábelské ódy v Bakalářích.";

        String encryptedMessage = EncryptionManager.encrypt(message, password);
        String decryptedMessage = EncryptionManager.decrypt(encryptedMessage, password);

        assertEquals(message, decryptedMessage);
    }

}
