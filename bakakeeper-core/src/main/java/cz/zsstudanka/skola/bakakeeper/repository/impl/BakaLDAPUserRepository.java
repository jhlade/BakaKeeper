package cz.zsstudanka.skola.bakakeeper.repository.impl;

import cz.zsstudanka.skola.bakakeeper.connectors.LDAPConnector;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLDAPAttributes;
import cz.zsstudanka.skola.bakakeeper.model.StudentRecord;
import cz.zsstudanka.skola.bakakeeper.model.entities.DataLDAP;
import cz.zsstudanka.skola.bakakeeper.model.mapping.StudentMapper;
import cz.zsstudanka.skola.bakakeeper.repository.LDAPUserRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementace LDAPUserRepository nad BakaADAuthenticator konektorem.
 * Extrahuje logiku dotazování z původního LDAPrecords.
 *
 * @author Jan Hladěna
 */
public class BakaLDAPUserRepository implements LDAPUserRepository {

    private final LDAPConnector ldap;

    /** atributy požadované pro uživatelské účty */
    private static final String[] USER_ATTRIBUTES = {
            EBakaLDAPAttributes.DN.attribute(),
            EBakaLDAPAttributes.UAC.attribute(),
            EBakaLDAPAttributes.UPN.attribute(),
            EBakaLDAPAttributes.LOGIN.attribute(),
            EBakaLDAPAttributes.MAIL.attribute(),
            EBakaLDAPAttributes.PROXY_ADDR.attribute(),
            EBakaLDAPAttributes.NAME_FIRST.attribute(),
            EBakaLDAPAttributes.NAME_LAST.attribute(),
            EBakaLDAPAttributes.NAME_DISPLAY.attribute(),
            EBakaLDAPAttributes.EXT01.attribute(),
            EBakaLDAPAttributes.EXT02.attribute(),
            EBakaLDAPAttributes.TITLE.attribute(),
            // extensionAttribute3-15 – pro konvergentní rekonciliaci pravidel
            EBakaLDAPAttributes.EXT03.attribute(),
            EBakaLDAPAttributes.EXT04.attribute(),
            EBakaLDAPAttributes.EXT05.attribute(),
            EBakaLDAPAttributes.EXT06.attribute(),
            EBakaLDAPAttributes.EXT07.attribute(),
            EBakaLDAPAttributes.EXT08.attribute(),
            EBakaLDAPAttributes.EXT09.attribute(),
            EBakaLDAPAttributes.EXT10.attribute(),
            EBakaLDAPAttributes.EXT11.attribute(),
            EBakaLDAPAttributes.EXT12.attribute(),
            EBakaLDAPAttributes.EXT13.attribute(),
            EBakaLDAPAttributes.EXT14.attribute(),
            EBakaLDAPAttributes.EXT15.attribute(),
    };

    public BakaLDAPUserRepository(LDAPConnector ldap) {
        this.ldap = ldap;
    }

    @Override
    public List<StudentRecord> findAllStudents(String baseOu, String alumniOu) {
        Map<Integer, Map<String, String>> raw = queryUsers(baseOu);
        List<StudentRecord> result = new ArrayList<>();

        if (raw == null) {
            return result;
        }

        for (int i = 0; i < raw.size(); i++) {
            Map<String, String> entry = raw.get(i);

            // ochrana proti NPE – nekonzistentní klíče v mapě
            if (entry == null) {
                continue;
            }

            // vyloučit absolventy (pokud nejsou explicitně hledáni)
            if (alumniOu != null && entry.get(EBakaLDAPAttributes.DN.attribute()) != null
                    && entry.get(EBakaLDAPAttributes.DN.attribute()).toLowerCase().contains(alumniOu.toLowerCase())) {
                continue;
            }

            DataLDAP data = new DataLDAP(entry);
            StudentRecord record = StudentMapper.fromLDAP(data);
            if (record != null) {
                result.add(record);
            }
        }

        return result;
    }

    @Override
    public List<StudentRecord> findAllAlumni(String alumniOu) {
        Map<Integer, Map<String, String>> raw = queryUsers(alumniOu);
        List<StudentRecord> result = new ArrayList<>();

        if (raw == null) {
            return result;
        }

        for (int i = 0; i < raw.size(); i++) {
            Map<String, String> entry = raw.get(i);
            if (entry == null) {
                continue;
            }

            DataLDAP data = new DataLDAP(entry);
            StudentRecord record = StudentMapper.fromLDAP(data);
            if (record != null) {
                result.add(record);
            }
        }

        return result;
    }

    @Override
    public StudentRecord findByUPN(String baseOu, String upn) {
        List<StudentRecord> all = findAllStudents(baseOu, null);
        return all.stream()
                .filter(s -> upn.equals(s.getUpn()))
                .findFirst()
                .orElse(null);
    }

    @Override
    public StudentRecord findByInternalId(String baseOu, String internalId) {
        List<StudentRecord> all = findAllStudents(baseOu, null);
        return all.stream()
                .filter(s -> internalId.equals(s.getInternalId()))
                .findFirst()
                .orElse(null);
    }

    @Override
    public void createUser(String cn, String targetOu, DataLDAP data) {
        ldap.createNewUser(cn, targetOu, data);
    }

    @Override
    public boolean updateAttribute(String dn, EBakaLDAPAttributes attr, String value) {
        return ldap.replaceAttribute(dn, attr, value);
    }

    @Override
    public boolean moveObject(String dn, String targetOu) {
        return ldap.moveObject(dn, targetOu);
    }

    @Override
    public boolean moveObject(String dn, String targetOu, boolean createOuIfNotExists) {
        return ldap.moveObject(dn, targetOu, createOuIfNotExists);
    }

    @Override
    public boolean checkDN(String dn) {
        return ldap.checkDN(dn);
    }

    @Override
    public boolean addToGroup(String dn, String groupDn) {
        return ldap.addObjectToGroup(dn, groupDn);
    }

    @Override
    public boolean removeFromAllGroups(String dn) {
        return ldap.removeObjectFromAllGroups(dn);
    }

    @Override
    public List<String> listMembership(String dn) {
        ArrayList<String> membership = ldap.listMembership(dn);
        return (membership != null) ? membership : new ArrayList<>();
    }

    @Override
    public List<String> listDirectMembers(String groupDn) {
        ArrayList<String> members = ldap.listDirectMembers(groupDn);
        return (members != null) ? members : new ArrayList<>();
    }

    @Override
    public boolean removeFromGroup(String dn, String groupDn) {
        return ldap.removeObjectFromGroup(dn, groupDn);
    }

    /**
     * Provede LDAP dotaz na uživatelské účty v dané OU.
     */
    @SuppressWarnings("unchecked")
    private Map<Integer, Map<String, String>> queryUsers(String baseOu) {
        HashMap<String, String> filter = new HashMap<>();
        filter.put(EBakaLDAPAttributes.OC_USER.attribute(), EBakaLDAPAttributes.OC_USER.value());

        return ldap.getObjectInfo(baseOu, filter, USER_ATTRIBUTES);
    }
}
