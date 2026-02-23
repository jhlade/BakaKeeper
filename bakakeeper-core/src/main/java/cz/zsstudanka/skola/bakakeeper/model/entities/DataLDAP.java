package cz.zsstudanka.skola.bakakeeper.model.entities;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Abstrakce pro LDAP data.
 *
 * @author Jan Hladěna
 */
public class DataLDAP extends HashMap<String, Object> {

    public DataLDAP() {
        super();
    }

    public DataLDAP (Map<String, ?> map) {
        Iterator<String> mapIterator = map.keySet().iterator();
        while (mapIterator.hasNext()) {
            String mapKey = mapIterator.next();
            this.put(mapKey, map.get(mapKey));
        }
    }

}
