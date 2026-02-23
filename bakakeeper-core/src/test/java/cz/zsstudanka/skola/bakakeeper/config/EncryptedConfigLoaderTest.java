package cz.zsstudanka.skola.bakakeeper.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testy pro EncryptedConfigLoader – šifrované i nešifrované načítání/ukládání YAML.
 *
 * @author Jan Hladěna
 */
class EncryptedConfigLoaderTest {

    @TempDir
    Path tempDir;

    private YamlAppConfig loadTestConfig() {
        InputStream is = getClass().getResourceAsStream("/test-config.yml");
        assertNotNull(is);
        return new YamlAppConfig(is);
    }

    @Test
    void savePlainAndLoad() throws Exception {
        YamlAppConfig original = loadTestConfig();
        File file = tempDir.resolve("test.yml").toFile();

        EncryptedConfigLoader.savePlain(original, file);
        assertTrue(file.exists());
        assertTrue(file.length() > 100);

        YamlAppConfig loaded = EncryptedConfigLoader.loadPlain(file);
        assertTrue(loaded.isValid());
        assertEquals(original.getLdapDomain(), loaded.getLdapDomain());
        assertEquals(original.getSqlHost(), loaded.getSqlHost());
        assertEquals(original.getUser(), loaded.getUser());
        assertEquals(original.getPass(), loaded.getPass());
        assertEquals(original.getPwdNoChange(), loaded.getPwdNoChange());
        assertEquals(original.getRules().size(), loaded.getRules().size());
    }

    @Test
    void saveEncryptedAndLoad() throws Exception {
        YamlAppConfig original = loadTestConfig();
        File file = tempDir.resolve("test.dat").toFile();
        char[] passphrase = "tajneHeslo123!".toCharArray();

        EncryptedConfigLoader.save(original, file, passphrase);
        assertTrue(file.exists());
        assertTrue(file.length() > 50);

        YamlAppConfig loaded = EncryptedConfigLoader.load(file, passphrase);
        assertTrue(loaded.isValid());
        assertEquals(original.getLdapDomain(), loaded.getLdapDomain());
        assertEquals(original.getSqlHost(), loaded.getSqlHost());
        assertEquals(original.getUser(), loaded.getUser());
        assertEquals(original.getPass(), loaded.getPass());
        assertEquals(original.getExtMailAllowed(), loaded.getExtMailAllowed());
        assertEquals(original.getRules().size(), loaded.getRules().size());
    }

    @Test
    void wrongPassphraseFails() throws Exception {
        YamlAppConfig original = loadTestConfig();
        File file = tempDir.resolve("enc.dat").toFile();
        char[] correct = "spravneHeslo".toCharArray();
        char[] wrong = "spatneHeslo".toCharArray();

        EncryptedConfigLoader.save(original, file, correct);

        // špatné heslo by mělo vyhodit výjimku
        assertThrows(Exception.class, () -> EncryptedConfigLoader.load(file, wrong));
    }

    @Test
    void saveWithoutEncryption() throws Exception {
        YamlAppConfig original = loadTestConfig();
        File file = tempDir.resolve("noenc.dat").toFile();

        // null passphrase = bez šifrování
        EncryptedConfigLoader.save(original, file, null);
        assertTrue(file.exists());

        YamlAppConfig loaded = EncryptedConfigLoader.load(file, null);
        assertTrue(loaded.isValid());
        assertEquals(original.getLdapDomain(), loaded.getLdapDomain());
    }

    @Test
    void rulesPreservedAfterRoundTrip() throws Exception {
        YamlAppConfig original = loadTestConfig();
        File file = tempDir.resolve("rules.dat").toFile();
        char[] pass = "hesloProPravidla".toCharArray();

        EncryptedConfigLoader.save(original, file, pass);
        YamlAppConfig loaded = EncryptedConfigLoader.load(file, pass);

        assertEquals(2, loaded.getRules().size());
        SyncRule first = loaded.getRules().get(0);
        assertEquals("6.A", first.getMatch());
        assertEquals("extensionAttribute5", first.getAttribute());
        assertEquals("TRUE", first.getValue());
    }
}
