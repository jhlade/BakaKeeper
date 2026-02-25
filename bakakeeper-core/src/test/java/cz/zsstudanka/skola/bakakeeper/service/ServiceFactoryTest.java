package cz.zsstudanka.skola.bakakeeper.service;

import cz.zsstudanka.skola.bakakeeper.config.AppConfig;
import cz.zsstudanka.skola.bakakeeper.connectors.LDAPConnector;
import cz.zsstudanka.skola.bakakeeper.connectors.SQLConnector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testy pro ServiceFactory – ověření správného sestavení DI grafu.
 *
 * @author Jan Hladěna
 */
@ExtendWith(MockitoExtension.class)
class ServiceFactoryTest {

    @Mock AppConfig config;
    @Mock LDAPConnector ldap;
    @Mock SQLConnector sql;

    @Test
    void createAllComponents() {
        ServiceFactory factory = new ServiceFactory(config, ldap, sql);

        // repozitáře
        assertNotNull(factory.getStudentRepo());
        assertNotNull(factory.getFacultyRepo());
        assertNotNull(factory.getLdapUserRepo());
        assertNotNull(factory.getGuardianRepo());

        // služby
        assertNotNull(factory.getStructureService());
        assertNotNull(factory.getPasswordService());
        assertNotNull(factory.getPairingService());
        assertNotNull(factory.getStudentService());
        assertNotNull(factory.getGuardianService());
        assertNotNull(factory.getFacultyService());
        assertNotNull(factory.getRuleService());

        // orchestrátor
        assertNotNull(factory.getOrchestrator());

        // konfigurace
        assertSame(config, factory.getConfig());
    }

    @Test
    void componentsSameInstance() {
        ServiceFactory factory = new ServiceFactory(config, ldap, sql);

        // opakované volání vrací stejnou instanci (ne novou)
        assertSame(factory.getStudentRepo(), factory.getStudentRepo());
        assertSame(factory.getOrchestrator(), factory.getOrchestrator());
    }
}
