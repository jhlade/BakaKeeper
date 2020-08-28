package cz.zsstudanka.bakakeeper.utils;

import cz.zsstudanka.skola.bakakeeper.components.EncryptionManager;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class EncryptionStreamTest {

    @Test
    public void basicEncryptionTest() {

        String password = "t4jnE.he$l@";
        String message = "Příliš žluťoučký kůň úpěl ďábelské ódy v Bakalářích.";

        String encryptedMessage = "";

        try {
            encryptedMessage = EncryptionManager.encrypt(message, password);
        } catch (Exception e) {
            //
        }

        String decryptedMessage = "";

        try {
            decryptedMessage = EncryptionManager.decrypt(encryptedMessage, password);
        } catch (Exception e) {
            //
        }

        assertEquals(message, decryptedMessage);
    }

}
