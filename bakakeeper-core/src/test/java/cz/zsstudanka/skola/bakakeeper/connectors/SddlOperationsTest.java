package cz.zsstudanka.skola.bakakeeper.connectors;

import cz.jhlade.adsddl.dacl.AceBuilder;
import cz.jhlade.adsddl.dacl.AceSelector;
import cz.jhlade.adsddl.dacl.DaclOperations;
import cz.jhlade.adsddl.data.AdExtendedRight;
import cz.jhlade.adsddl.data.WellKnownSid;
import net.tirasa.adsddl.ntsd.ACE;
import net.tirasa.adsddl.ntsd.SDDL;
import net.tirasa.adsddl.ntsd.controls.SDFlagsControl;
import net.tirasa.adsddl.ntsd.data.AceFlag;
import net.tirasa.adsddl.ntsd.data.AceRights.ObjectRight;
import net.tirasa.adsddl.ntsd.data.AceType;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.directory.*;
import javax.naming.ldap.Control;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Hashtable;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testy pro práci s příznakem User Cannot Change Password (UCP) v NT Security Descriptoru
 * pomocí knihovny {@code cz.jhlade:adsddl} ({@link DaclOperations}).
 *
 * @author Jan Hladěna
 */
class SddlOperationsTest {

    @Test
    void defaultSddlAllowsPasswordChange() {
        SDDL sddl = SDDL.parse("O:BAG:SYD:(A;;RPWPCRCCDCLCLORCWOWDSDDTSW;;;BA)(A;;RPWPCRCCDCLCLORCWOWDSDDTSW;;;SY)");
        assertFalse(DaclOperations.isUserCannotChangePassword(sddl));
    }

    @Test
    void setCannotChangePasswordAndRoundTrip() {
        SDDL sddl = SDDL.parse("O:BAG:SYD:(A;CI;GA;;;BA)");
        assertFalse(DaclOperations.isUserCannotChangePassword(sddl));

        // zakázání změny hesla (cannot = true)
        DaclOperations.setUserCannotChangePassword(sddl, true);
        assertTrue(DaclOperations.isUserCannotChangePassword(sddl));

        // ověření správných SID (Everyone S-1-1-0 a Self S-1-5-10) – regrese původní chyby v net.tirasa:adsddl:1.9
        ACE everyoneAce = sddl.getDacl().findFirstAce(
                AceSelector.hasSid(WellKnownSid.EVERYONE.toSid())
                        .and(AceSelector.hasExtendedRight(AdExtendedRight.USER_CHANGE_PASSWORD)));
        ACE selfAce = sddl.getDacl().findFirstAce(
                AceSelector.hasSid(WellKnownSid.PRINCIPAL_SELF.toSid())
                        .and(AceSelector.hasExtendedRight(AdExtendedRight.USER_CHANGE_PASSWORD)));

        assertNotNull(everyoneAce, "ACE pro Everyone (S-1-1-0) musí existovat");
        assertNotNull(selfAce, "ACE pro Principal Self (S-1-5-10) musí existovat");
        assertEquals(AceType.ACCESS_DENIED_OBJECT_ACE_TYPE, everyoneAce.getType());
        assertEquals(AceType.ACCESS_DENIED_OBJECT_ACE_TYPE, selfAce.getType());
        assertEquals("S-1-1-0", everyoneAce.getSid().toString());
        assertEquals("S-1-5-10", selfAce.getSid().toString());

        // binární serializace a deserializace (wire format)
        byte[] bytes = sddl.toByteArray();
        SDDL parsed = new SDDL(bytes);
        assertTrue(DaclOperations.isUserCannotChangePassword(parsed));

        // opětovné povolení změny hesla (cannot = false)
        DaclOperations.setUserCannotChangePassword(parsed, false);
        assertFalse(DaclOperations.isUserCannotChangePassword(parsed));

        SDDL parsedAfterAllow = new SDDL(parsed.toByteArray());
        assertFalse(DaclOperations.isUserCannotChangePassword(parsedAfterAllow));
    }

    @Test
    void rsatSingleEveryoneAllowAceIsEvaluatedAsAllowed() {
        // chování RSAT GUI ve Windows Server 2016+: po odškrtnutí 'User cannot change password'
        // vytvoří pouze OA ACE pro Everyone (S-1-1-0), zatímco Self (S-1-5-10) chybí
        SDDL sddl = SDDL.parse("D:");
        sddl.getDacl().addAceCanonical(
                AceBuilder.allow(WellKnownSid.EVERYONE)
                        .withExtendedRight(AdExtendedRight.USER_CHANGE_PASSWORD)
                        .build()
        );

        assertFalse(DaclOperations.isUserCannotChangePassword(sddl));
    }

    @Test
    void canonicalOrderingPreservedBeforeInheritedAces() {
        SDDL sddl = SDDL.parse("D:");
        // zděděné ACE na konci DACL
        sddl.getDacl().addAceCanonical(
                AceBuilder.allow(WellKnownSid.AUTHENTICATED_USERS)
                        .withRights(ObjectRight.GR)
                        .withFlags(AceFlag.INHERITED_ACE)
                        .build()
        );

        DaclOperations.setUserCannotChangePassword(sddl, true);

        List<ACE> aces = sddl.getDacl().getAces();
        assertEquals(3, aces.size());
        // explicitní DENY ACE musí být v kanonickém pořadí před zděděným ALLOW ACE
        assertEquals(AceType.ACCESS_DENIED_OBJECT_ACE_TYPE, aces.get(0).getType());
        assertEquals(AceType.ACCESS_DENIED_OBJECT_ACE_TYPE, aces.get(1).getType());
        assertTrue(aces.get(2).getFlags().contains(AceFlag.INHERITED_ACE));
    }

    @Test
    void liveSamba4UserCannotChangePasswordToggle() throws Exception {
        // integrační test proti Podman kontejneru bakadev-samba4 (pokud běží na localhost:389)
        boolean sambaRunning = false;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", 389), 500);
            sambaRunning = true;
        } catch (Exception ignored) {
        }
        Assumptions.assumeTrue(sambaRunning, "Podman Samba4 AD DC neběží na localhost:389 – přeskakuji live test.");

        Hashtable<String, String> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, "ldap://127.0.0.1:389/");
        env.put(Context.SECURITY_AUTHENTICATION, "simple");
        env.put(Context.SECURITY_PRINCIPAL, "bakalari@skola.local");
        env.put(Context.SECURITY_CREDENTIALS, "Baka1234.2026");
        env.put("java.naming.ldap.attributes.binary", "nTSecurityDescriptor");

        LdapContext ctx = new InitialLdapContext(env, new Control[]{new SDFlagsControl(0x04)});
        try {
            String userDn = "CN=Holub Duboslav,OU=Trida-B,OU=Rocnik-1,OU=Zaci,OU=Uzivatele,OU=Skola,DC=skola,DC=local";

            // 1. načtení aktuálního nTSecurityDescriptoru (DACL)
            byte[] origBytes = readNtSecurityDescriptor(ctx, userDn);
            assertNotNull(origBytes);
            SDDL origSddl = new SDDL(origBytes);
            boolean origCannotChange = DaclOperations.isUserCannotChangePassword(origSddl);

            try {
                // 2. přepnutí na opačnou hodnotu a zápis do Samba4 AD
                SDDL toggledSddl = new SDDL(origBytes);
                byte[] toggledBytes = DaclOperations.setUserCannotChangePassword(toggledSddl, !origCannotChange).toByteArray();
                ctx.modifyAttributes(userDn, new ModificationItem[]{
                        new ModificationItem(DirContext.REPLACE_ATTRIBUTE,
                                new BasicAttribute("nTSecurityDescriptor", toggledBytes))
                });

                // 3. znovunačtení ze Samba4 a ověření změny
                byte[] afterToggleBytes = readNtSecurityDescriptor(ctx, userDn);
                SDDL afterToggleSddl = new SDDL(afterToggleBytes);
                assertEquals(!origCannotChange, DaclOperations.isUserCannotChangePassword(afterToggleSddl));

                // 4. přepnutí zpět na původní hodnotu a ověření
                byte[] restoredBytes = DaclOperations.setUserCannotChangePassword(afterToggleSddl, origCannotChange).toByteArray();
                ctx.modifyAttributes(userDn, new ModificationItem[]{
                        new ModificationItem(DirContext.REPLACE_ATTRIBUTE,
                                new BasicAttribute("nTSecurityDescriptor", restoredBytes))
                });

                byte[] finalBytes = readNtSecurityDescriptor(ctx, userDn);
                SDDL finalSddl = new SDDL(finalBytes);
                assertEquals(origCannotChange, DaclOperations.isUserCannotChangePassword(finalSddl));
            } finally {
                // jistota obnovy původního deskriptoru
                ctx.modifyAttributes(userDn, new ModificationItem[]{
                        new ModificationItem(DirContext.REPLACE_ATTRIBUTE,
                                new BasicAttribute("nTSecurityDescriptor", origBytes))
                });
            }
        } finally {
            ctx.close();
        }
    }

    private static byte[] readNtSecurityDescriptor(LdapContext ctx, String dn) throws Exception {
        SearchControls ctls = new SearchControls();
        ctls.setSearchScope(SearchControls.OBJECT_SCOPE);
        ctls.setReturningAttributes(new String[]{"nTSecurityDescriptor"});
        NamingEnumeration<SearchResult> results = ctx.search(dn, "(objectClass=user)", ctls);
        assertTrue(results.hasMore(), "Testovací uživatel v Samba4 nebyl nalezen: " + dn);
        SearchResult sr = results.next();
        results.close();
        return (byte[]) sr.getAttributes().get("nTSecurityDescriptor").get();
    }
}
