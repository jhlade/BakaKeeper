package cz.zsstudanka.skola.bakakeeper.connectors;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testy přípravy konfigurace pro Kerberos.
 */
class BakaKerberosTest {

    @Test
    void jaasKonfiguraceJeLokalniSouborSFileUri() throws Exception {
        File jaas = BakaKerberos.extractJaasConfig();

        assertNotNull(jaas);
        assertTrue(jaas.isFile());
        // MSSQL JDBC 13.6+ přijímá jen lokální cestu nebo file: URI, ne jar:
        assertEquals("file", jaas.toURI().getScheme());
        assertTrue(Files.readString(jaas.toPath()).contains("SQLJDBCDriver"));
    }
}
