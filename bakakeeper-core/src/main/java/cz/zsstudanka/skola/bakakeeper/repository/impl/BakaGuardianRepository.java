package cz.zsstudanka.skola.bakakeeper.repository.impl;

import cz.zsstudanka.skola.bakakeeper.connectors.LDAPConnector;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLDAPAttributes;
import cz.zsstudanka.skola.bakakeeper.model.GuardianRecord;
import cz.zsstudanka.skola.bakakeeper.model.entities.DataLDAP;
import cz.zsstudanka.skola.bakakeeper.model.mapping.GuardianMapper;
import cz.zsstudanka.skola.bakakeeper.repository.GuardianRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementace GuardianRepository nad BakaADAuthenticator konektorem.
 * Dotazování kontaktů zákonných zástupců v AD.
 *
 * @author Jan Hladěna
 */
public class BakaGuardianRepository implements GuardianRepository {

    private final LDAPConnector ldap;

    /** atributy požadované pro kontakty */
    private static final String[] CONTACT_ATTRIBUTES = {
            EBakaLDAPAttributes.DN.attribute(),
            EBakaLDAPAttributes.MAIL.attribute(),
            EBakaLDAPAttributes.MOBILE.attribute(),
            EBakaLDAPAttributes.NAME_FIRST.attribute(),
            EBakaLDAPAttributes.NAME_LAST.attribute(),
            EBakaLDAPAttributes.NAME_DISPLAY.attribute(),
            EBakaLDAPAttributes.MSXCH_REQ_AUTH.attribute(),
            EBakaLDAPAttributes.MSXCH_GAL_HIDDEN.attribute(),
            EBakaLDAPAttributes.EXT01.attribute(),
    };

    public BakaGuardianRepository(LDAPConnector ldap) {
        this.ldap = ldap;
    }

    @Override
    public List<GuardianRecord> findAllContacts(String baseOu) {
        Map<Integer, Map<String, String>> raw = queryContacts(baseOu);
        List<GuardianRecord> result = new ArrayList<>();

        for (int i = 0; i < raw.size(); i++) {
            DataLDAP data = new DataLDAP(raw.get(i));
            GuardianRecord record = GuardianMapper.fromLDAP(data);
            if (record != null) {
                result.add(record);
            }
        }

        return result;
    }

    @Override
    public GuardianRecord findByInternalId(String baseOu, String internalId) {
        List<GuardianRecord> all = findAllContacts(baseOu);
        return all.stream()
                .filter(g -> internalId.equals(g.getInternalId()))
                .findFirst()
                .orElse(null);
    }

    @Override
    public void createContact(String cn, DataLDAP data) {
        ldap.createNewContact(cn, data);
    }

    @Override
    public boolean deleteContact(String dn) {
        return ldap.deleteContact(dn);
    }

    @Override
    public List<String> listMembership(String dn) {
        ArrayList<String> membership = ldap.listMembership(dn);
        return (membership != null) ? membership : new ArrayList<>();
    }

    @Override
    public boolean addToGroup(String dn, String groupDn) {
        return ldap.addObjectToGroup(dn, groupDn);
    }

    @Override
    public boolean removeFromAllGroups(String dn) {
        return ldap.removeObjectFromAllGroups(dn);
    }

    /**
     * Provede LDAP dotaz na kontakty v dané OU.
     */
    @SuppressWarnings("unchecked")
    private Map<Integer, Map<String, String>> queryContacts(String baseOu) {
        HashMap<String, String> filter = new HashMap<>();
        filter.put(EBakaLDAPAttributes.OC_CONTACT.attribute(), EBakaLDAPAttributes.OC_CONTACT.value());

        return ldap.getObjectInfo(baseOu, filter, CONTACT_ATTRIBUTES);
    }
}
