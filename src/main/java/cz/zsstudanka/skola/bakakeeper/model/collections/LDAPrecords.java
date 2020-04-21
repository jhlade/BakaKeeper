package cz.zsstudanka.skola.bakakeeper.model.collections;

import cz.zsstudanka.skola.bakakeeper.connectors.BakaADAuthenticator;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLDAPAttributes;
import cz.zsstudanka.skola.bakakeeper.model.interfaces.IRecords;
import cz.zsstudanka.skola.bakakeeper.settings.Settings;

import java.util.*;

/**
 * Kolekce generických záznamů LDAP.
 *
 * @author Jan Hladěna
 */
public class LDAPrecords implements IRecords {

    static final String FLAG_ID = "baka_flag";
    static final String FLAG_0  = "0";
    static final String FLAG_1  = "1";

    /** klíčový atribut */
    private EBakaLDAPAttributes keyAttribute;

    /** identifikace kolekce */
    private String description;

    /** typ záznamů - "objectClass"; OC_* */
    private EBakaLDAPAttributes recordType;

    /** základní OU pro záznamy */
    private String base;

    /** instanční iterátor */
    private Iterator iterator;

    /** hrubá data; klíč = UPN pro uživatele, mail pro kontakty, obsah = interní data podle požadavků */
    private LinkedHashMap<String, Map> data;

    /** hrubá data k zápisu; klíč = DN, obsah = data k zápisu */
    private LinkedHashMap<String, Map<EBakaLDAPAttributes, Object>> writeData = new LinkedHashMap<>();

    /**
     * Konstrukce kolekce podle bázové OU z daného typu záznamů.
     *
     * @param base prohledávat OU
     * @param type získávat pouze záznamy typu
     */
    public LDAPrecords(String base, EBakaLDAPAttributes type) {
        this.base = base;
        this.recordType = type;

        // inicializace
        this.data = new LinkedHashMap<String, Map>();

        // dotaz na LDAP - hledání dle typu objektu
        HashMap<String, String> ldapQ = new HashMap<String, String>();
        ldapQ.put(type.attribute(), type.value());

        // hledaná data
        String[] retAttributes = new String[]{
                EBakaLDAPAttributes.DN.attribute(), // minumum = DN
        };

        // pro kontakty
        if (recordType.equals(EBakaLDAPAttributes.OC_CONTACT)) {
            retAttributes = new String[]{
                    EBakaLDAPAttributes.DN.attribute(),

                    EBakaLDAPAttributes.MAIL.attribute(),
                    EBakaLDAPAttributes.MOBILE.attribute(),

                    EBakaLDAPAttributes.NAME_FIRST.attribute(),
                    EBakaLDAPAttributes.NAME_LAST.attribute(),
                    EBakaLDAPAttributes.NAME_DISPLAY.attribute(),

                    EBakaLDAPAttributes.MSXCH_REQ_AUTH.attribute(),
                    EBakaLDAPAttributes.MSXCH_GAL_HIDDEN.attribute(),
                    EBakaLDAPAttributes.MEMBER_OF.attribute(),

                    EBakaLDAPAttributes.EXT01.attribute(),
            };

            keyAttribute = EBakaLDAPAttributes.MAIL;
        }

        // pro účty
        if (recordType.equals(EBakaLDAPAttributes.OC_USER)) {
            retAttributes = new String[]{
                    EBakaLDAPAttributes.DN.attribute(),

                    EBakaLDAPAttributes.UPN.attribute(),
                    EBakaLDAPAttributes.LOGIN.attribute(),
                    EBakaLDAPAttributes.MAIL.attribute(),

                    EBakaLDAPAttributes.NAME_FIRST.attribute(),
                    EBakaLDAPAttributes.NAME_LAST.attribute(),
                    EBakaLDAPAttributes.NAME_DISPLAY.attribute(),

                    EBakaLDAPAttributes.EXT01.attribute(),
                    EBakaLDAPAttributes.TITLE.attribute(),
            };

            keyAttribute = EBakaLDAPAttributes.UPN;
        }

        // naplnění daty
        Map<Integer, Map> info = BakaADAuthenticator.getInstance().getInfoInOU(
                base,
                ldapQ,
                retAttributes
        );

        if (info.size() > 0) {
            int i;
            for (i = 0; i < info.size(); i++) {
                // hack - pokud se explicitně nehledají absolventi
                if (!base.equals(Settings.getInstance().getLDAP_baseAlumni())
                        && info.get(i).get(EBakaLDAPAttributes.DN.attribute()).toString().toLowerCase()
                                .contains(Settings.getInstance().getLDAP_baseAlumni().toLowerCase())
                ) {
                    // absolvent mimo explicitní hledání absolventů - přeskočit
                    continue;
                } else {
                    addRecord(info.get(i).get(this.keyAttribute.attribute()).toString(), info.get(i));
                }

                // počáteční příznak zpracování záznamu
                this.data.get(info.get(i).get(this.keyAttribute.attribute()).toString()).put(FLAG_ID, FLAG_0);
            }
        }
    }

    /**
     * Přidání záznamu do kolekce.
     *
     * @param key UPN - klíč
     * @param data záznam
     */
    public void addRecord(String key, Map data) {
        this.data.put(key, data);
    }

    /**
     * Odebrání záznamu z kolekce.
     *
     * @param key klíč
     */
    public void removeRecord(String key) {
        if (this.data.containsKey(key)) {
            this.data.remove(key);
        }
    }

    /**
     * Získání záznamu podle klíče.
     *
     * @param key UPN - klíč
     * @return záznam
     */
    public Map get(String key) {

        if (this.data.containsKey(key)) {
            return this.data.get(key);
        }

        return null;
    }

    /**
     * Získání příznaku konkrétního objektu.
     *
     * @param key klíč objektu
     * @return příznak zpracování
     */
    public Boolean getFlag(String key) {
        if (this.data.containsKey(key)) {
            return (this.data.get(key).get(FLAG_ID).equals(FLAG_1)) ? true : false;
        }

        return null;
    }

    /**
     * Nastavení příznaku zpracování konkrétního objektu.
     *
     * @param key klíč objektu
     * @param flag příznak
     */
    public void setFlag(String key, Boolean flag) {

        if (this.data.containsKey(key)) {
            this.data.get(key).replace(FLAG_ID, (flag) ? FLAG_1 : FLAG_0);
        }
    }

    /**
     * Získání podmnožiny se zadaným stavem příznaku.
     *
     * @param flag příznak
     * @return podmnožina s daným příznakem
     */
    public LinkedHashMap<String, Map> getSubsetWithFlag(Boolean flag) {
        LinkedHashMap<String, Map> subset = new LinkedHashMap<>();

        // nový interní iterátor
        Iterator<Map.Entry<String, Map>> internalIterator = this.data.entrySet().iterator();

        while (internalIterator.hasNext()) {
            Map.Entry<String, Map> record = internalIterator.next();

            // naplnění podmnožiny záznamy s daným příznakem
            if (record.getValue().get(FLAG_ID).equals((flag) ? FLAG_1 : FLAG_0)) {
                subset.put(record.getKey(), record.getValue());
            }
        }

        return subset;
    }

    /**
     * Počet položek v seznamu.
     *
     * @return počet položek
     */
    public Integer count() {
        return this.data.size();
    }

    /**
     * Příprava dat ke zpětnému zápisu.
     *
     * @param dn plné DN objektu
     * @param data data k zápisu
     */
    public void writeData(String dn, Map<EBakaLDAPAttributes, Object> data) {

        if (this.writeData.containsKey(dn)) {
            for (Map.Entry<EBakaLDAPAttributes, Object> dataEntry : data.entrySet()) {
                this.writeData.get(dn).put(dataEntry.getKey(), dataEntry.getValue());
            }
        } else {
            this.writeData.put(dn, data);
        }
    }

    public Integer writesRemaining() {
        return this.writeData.size();
    }

    /**
     * Provedení zápisu čekajících dat zpět přímo do LDAPu.
     */
    public boolean commit() {

        if (this.writeData.size() > 0)
        {
            Iterator<String> writeIterator = this.writeData.keySet().iterator();
            while (writeIterator.hasNext()) {
                String dn = writeIterator.next();
                Map<EBakaLDAPAttributes, Object> dataSet = this.writeData.get(dn);

                Boolean attrMod = true;

                for (Map.Entry<EBakaLDAPAttributes, Object> data : dataSet.entrySet()) {
                    if (!Settings.getInstance().develMode()) {
                        attrMod &= BakaADAuthenticator.getInstance().replaceAttribute(dn, data.getKey(), data.getValue());
                    } else {
                        // attrMod vždy true
                        System.out.println("[ DEVEL ] [ LDAP ] Zde proběhne zápis do ostrých dat.");
                    }
                }

                if (attrMod) {
                    writeIterator.remove();
                }
            }

            return (this.writeData.size() == 0);
        }

        return true;
    }


    @Override
    public String toString() {
        StringBuilder records = new StringBuilder();

        if (this.data.size() > 0) {

            records.append("Seznam obsahuje celkem " + this.data.size() + " položek.\n");

            for (Map.Entry<String, Map> recEntry : data.entrySet()) {
                records.append(recEntry.getKey());
                records.append("\n");
            }

        } else {
            records.append("Seznam je prázný.\n");
        }

        return records.toString();
    }

    @Override
    public Iterator<Map.Entry<String, Map>> iterator() {

        if (this.iterator == null) {
            this.iterator = this.data.entrySet().iterator();
        }

        return this.iterator;
    }

    @Override
    public void resetIterator() {
        this.iterator = null;
    }

}
