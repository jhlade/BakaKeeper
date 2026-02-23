package cz.zsstudanka.skola.bakakeeper.model.mapping;

import cz.zsstudanka.skola.bakakeeper.constants.EBakaLDAPAttributes;
import cz.zsstudanka.skola.bakakeeper.model.entities.DataLDAP;

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
}
