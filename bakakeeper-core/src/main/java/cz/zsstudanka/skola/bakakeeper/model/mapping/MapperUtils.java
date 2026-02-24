package cz.zsstudanka.skola.bakakeeper.model.mapping;

import cz.zsstudanka.skola.bakakeeper.constants.EBakaLDAPAttributes;
import cz.zsstudanka.skola.bakakeeper.model.entities.DataLDAP;

import java.util.ArrayList;
import java.util.List;

/**
 * Pomocné metody sdílené mezi mappery.
 *
 * @author Jan Hladěna
 */
class MapperUtils {

    private MapperUtils() {
        // utilita – bez instancí
    }

    /**
     * Bezpečné získání String atributu z DataLDAP.
     *
     * @param ldap LDAP data
     * @param attr požadovaný atribut
     * @return hodnota jako String nebo null
     */
    static String getStringAttr(DataLDAP ldap, EBakaLDAPAttributes attr) {
        Object val = ldap.get(attr.attribute());
        return (val != null) ? val.toString() : null;
    }

    /**
     * Bezpečné získání multi-value atributu z DataLDAP.
     *
     * <p>LDAP konektor vrací jednoprvkové atributy jako {@code String},
     * víceprvkové jako {@code ArrayList<Object>}. Tato metoda sjednocuje
     * oba formáty do {@code List<String>}.</p>
     *
     * @param ldap LDAP data
     * @param attr požadovaný atribut
     * @return seznam hodnot (nikdy null, může být prázdný)
     */
    @SuppressWarnings("unchecked")
    static List<String> getMultiValueAttr(DataLDAP ldap, EBakaLDAPAttributes attr) {
        List<String> result = new ArrayList<>();
        Object val = ldap.get(attr.attribute());
        if (val == null) {
            return result;
        }

        if (val instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) {
                    result.add(item.toString());
                }
            }
        } else {
            // jednoprvkový atribut – vrácen jako String
            result.add(val.toString());
        }
        return result;
    }
}
