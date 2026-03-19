package cz.zsstudanka.skola.bakakeeper.settings;

import cz.zsstudanka.skola.bakakeeper.config.EncryptedConfigLoader;
import cz.zsstudanka.skola.bakakeeper.config.YamlAppConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testy pro Settings.
 */
class SettingsTest {

    @TempDir
    Path tempDir;

    private YamlAppConfig loadTestConfig() {
        InputStream is = getClass().getResourceAsStream("/test-config.yml");
        assertNotNull(is);
        return new YamlAppConfig(is);
    }

    @Test
    void loadOrThrowClearsPreviousConfigAfterDecryptFailure() throws Exception {
        Settings settings = Settings.getInstance();

        File plainFile = tempDir.resolve("settings.yml").toFile();
        EncryptedConfigLoader.savePlain(loadTestConfig(), plainFile);

        settings.setPassphrase("spravne-heslo");
        settings.loadOrThrow(plainFile.getAbsolutePath());
        assertTrue(settings.isValid());

        File encryptedFile = tempDir.resolve("settings.dat").toFile();
        EncryptedConfigLoader.save(loadTestConfig(), encryptedFile, "spravne-heslo".toCharArray());

        settings.setPassphrase("spatne-heslo");
        assertThrows(IllegalStateException.class, () -> settings.loadOrThrow(encryptedFile.getAbsolutePath()));
        assertFalse(settings.isValid());
    }
}
