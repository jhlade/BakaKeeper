package cz.zsstudanka.skola.bakakeeper.model.collections;

import cz.zsstudanka.skola.bakakeeper.components.ReportManager;
import cz.zsstudanka.skola.bakakeeper.connectors.BakaADAuthenticator;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLDAPAttributes;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLogType;
import cz.zsstudanka.skola.bakakeeper.model.entities.DataLDAP;
import cz.zsstudanka.skola.bakakeeper.model.interfaces.IRecords;
import cz.zsstudanka.skola.bakakeeper.settings.Settings;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Kolekce generických záznamů LDAP.
 *
 * @author Jan Hladěna
 */
public class LDAPrecords implements IRecords {

    /** klíčový atribut */
    private EBakaLDAPAttributes keyAttribute;

    /** identifikace kolekce */
    private String description;

    /** typ záznamů - "objectClass"; OC_* */
    private EBakaLDAPAttributes recordType;

    /** základní OU pro záznamy */
    private String base;

    /** datový instanční iterátor */
    @Deprecated
    private Iterator dataIterator;
    /** instanční iterátor */
    private Iterator iterator;

    /** hrubá data; klíč = UPN pro uživatele, mail pro kontakty, obsah = interní data podle požadavků */
    private HashMap<String, DataLDAP> data;

    /** hrubá data k zápisu; klíč = DN, obsah = data k zápisu */
    private HashMap<String, Map<EBakaLDAPAttributes, String>> dataToWrite = new LinkedHashMap<>();

    /** instanční podmnožina záznamů s neprázdným proxy-listem */
    private HashMap<String, DataLDAP> proxyData = null;

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
        this.data = new LinkedHashMap<String, DataLDAP>();

        // dotaz na LDAP - hledání dle typu objektu
        HashMap<String, String> ldapQ = new HashMap<String, String>();
        ldapQ.put(type.attribute(), type.value());

        // hledaná data
        String[] retAttributes = new String[]{
                EBakaLDAPAttributes.DN.attribute(), // minimum = DN
        };

        // pro kontakty
        if (recordType.equals(EBakaLDAPAttributes.OC_CONTACT)) {
            retAttributes = new String[]{
                    EBakaLDAPAttributes.DN.attribute(),

                    EBakaLDAPAttributes.MAIL.attribute(),
                    //EBakaLDAPAttributes.TARGET_ADDR.attribute(),
                    EBakaLDAPAttributes.MOBILE.attribute(),

                    EBakaLDAPAttributes.NAME_FIRST.attribute(),
                    EBakaLDAPAttributes.NAME_LAST.attribute(),
                    EBakaLDAPAttributes.NAME_DISPLAY.attribute(),

                    EBakaLDAPAttributes.MSXCH_REQ_AUTH.attribute(),
                    EBakaLDAPAttributes.MSXCH_GAL_HIDDEN.attribute(),
                    //EBakaLDAPAttributes.MEMBER_OF.attribute(),

                    EBakaLDAPAttributes.EXT01.attribute(),
            };

            keyAttribute = EBakaLDAPAttributes.EXT01;
        }

        // pro účty
        if (recordType.equals(EBakaLDAPAttributes.OC_USER)) {
            retAttributes = new String[]{
                    EBakaLDAPAttributes.DN.attribute(),
                    EBakaLDAPAttributes.UAC.attribute(),

                    EBakaLDAPAttributes.UPN.attribute(),
                    EBakaLDAPAttributes.LOGIN.attribute(),
                    EBakaLDAPAttributes.MAIL.attribute(),
                    EBakaLDAPAttributes.PROXY_ADDR.attribute(),

                    EBakaLDAPAttributes.NAME_FIRST.attribute(),
                    EBakaLDAPAttributes.NAME_LAST.attribute(),
                    EBakaLDAPAttributes.NAME_DISPLAY.attribute(),

                    EBakaLDAPAttributes.EXT01.attribute(), // ID
                    EBakaLDAPAttributes.EXT02.attribute(), // pošta pouze uvnitř organizace
                    EBakaLDAPAttributes.TITLE.attribute(),
            };

            keyAttribute = EBakaLDAPAttributes.UPN;
        }

        // naplnění daty
        Map<Integer, Map<String, String>> info = BakaADAuthenticator.getInstance().getObjectInfo(
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
                this.data.get(info.get(i).get(this.keyAttribute.attribute()).toString()).put(EBakaLDAPAttributes.BK_FLAG.attribute(), EBakaLDAPAttributes.BK_FLAG_FALSE.value());
            }
        }
    }

    /**
     * Přidání záznamu do kolekce.
     *
     * @param key UPN - klíč
     * @param data záznam
     */
    public void addRecord(String key, DataLDAP data) {
        this.data.put(key, data);
    }

    public void addRecord(String key, Map<String, String> data) {
        this.data.put(key, new DataLDAP(data));
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
    public DataLDAP get(String key) {

        if (this.data.containsKey(key)) {
            return this.data.get(key);
        }

        // zrychlená kontrola v proxy klíči
        // TODO kontrola+přepis
        if (this.getProxyListSubset().size() > 0) {
            Iterator<String> subKey = getProxyListSubset().keySet().iterator();
            while (subKey.hasNext()) {
                try {
                    // neparsuje se - pouze porovnání obsahu v prostém řetězci
                    if (getProxyListSubset().get(subKey).get(EBakaLDAPAttributes.PROXY_ADDR.attribute()).toString().toLowerCase().contains(key.toLowerCase())) {
                        return this.data.get(subKey);
                    }
                } catch (Exception e) {
                    ReportManager.handleException("Selhalo prohledávání podklíče v proxyAddresses poli.", e);
                    break;
                }
            }
        }

        return null;
    }

    /**
     * Získání jednoho záznamu podle konkrétního atributu a jeho řetězcové hodnoty.
     *
     * @param attr atribut
     * @param value hledaná hodnota
     * @return hledaný objekt
     */
    public DataLDAP getBy(EBakaLDAPAttributes attr, String value) {

        LinkedHashMap<String, DataLDAP> subset = this.getSubsetBy(attr, value);

        if (subset.size() == 1) {
            Iterator<String> internalIterator = subset.keySet().iterator();
            return this.get(internalIterator.next());
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
            return (this.data.get(key).get(EBakaLDAPAttributes.BK_FLAG.attribute()).equals(EBakaLDAPAttributes.BK_FLAG_TRUE.value())) ? true : false;
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
            this.data.get(key).replace(EBakaLDAPAttributes.BK_FLAG.attribute(), (flag) ? EBakaLDAPAttributes.BK_FLAG_TRUE.value() : EBakaLDAPAttributes.BK_FLAG_FALSE.value());
        }
    }

    /**
     * Získání podmnožiny se zadaným stavem příznaku.
     *
     * @param flag příznak
     * @return podmnožina s daným příznakem
     */
    public LinkedHashMap<String, DataLDAP> getSubsetWithFlag(Boolean flag) {
        return this.getSubsetBy(EBakaLDAPAttributes.BK_FLAG, (flag) ? EBakaLDAPAttributes.BK_FLAG_TRUE.value() : EBakaLDAPAttributes.BK_FLAG_FALSE.value());
    }

    /**
     * Obecná podmnožina dat podle vlastního klíče a hodnoty.
     *
     * @param attr klíč k porovnání
     * @param value hodnota k porovnání
     * @return podmnožina odpovídajících záznamů
     */
    public LinkedHashMap<String, DataLDAP> getSubsetBy(EBakaLDAPAttributes attr, String value) {
        LinkedHashMap<String, DataLDAP> subset = new LinkedHashMap<>();

        Iterator<String> internalIterator = this.data.keySet().iterator();
        while (internalIterator.hasNext()) {

            String subID = internalIterator.next();

            if (attr.equals(EBakaLDAPAttributes.PROXY_ADDR)) {
                // zvláštní případ - pokud existuje atribut proxyAddresses, je záznam přidán bez ohledu na hodnotu
                if (get(subID).containsKey(attr.attribute()) && get(subID).get(attr.attribute()) != null) {
                    subset.put(subID, get(subID));
                }
                continue;
            }

            if (get(subID).containsKey(attr.attribute()) && get(subID).get(attr.attribute()).equals(value)) {
                subset.put(subID, get(subID));
            }
        }

        return subset;
    }

    /**
     * Získání podmnožiny dat s neprázdným seznamem proxyAddresses.
     *
     * @return podmnožina s neprázdným proxyAddresses atributem
     */
    private HashMap<String, DataLDAP> getProxyListSubset() {
        if (this.proxyData != null) {
            return this.proxyData;
        }

        this.proxyData = getSubsetBy(EBakaLDAPAttributes.PROXY_ADDR, null);

        return this.proxyData;
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
     * @param preparedData data k zápisu
     */
    public void addWriteData(String dn, Map<EBakaLDAPAttributes, String> preparedData) {

        if (this.dataToWrite.containsKey(dn)) {
            for (Map.Entry<EBakaLDAPAttributes, String> dataEntry : preparedData.entrySet()) {
                this.dataToWrite.get(dn).put(dataEntry.getKey(), dataEntry.getValue());
            }
        } else {
            this.dataToWrite.put(dn, preparedData);
        }
    }

    /**
     * Úprava živých dat.
     * TODO
     *
     * @param key ientifikátor
     * @param newData data k okamžitému zápisu do mezipaměti
     */
    public void liveUpdate(String key, Map<EBakaLDAPAttributes, Object> newData) {

        DataLDAP refinedData = new DataLDAP();

        Iterator<EBakaLDAPAttributes> newDataIterator = newData.keySet().iterator();
        while (newDataIterator.hasNext()) {
            EBakaLDAPAttributes attr = newDataIterator.next();
            refinedData.put(attr.attribute(), newData.get(attr));
        }

        this.data.put(key, refinedData);
    }

    public Integer writesRemaining() {
        return this.dataToWrite.size();
    }

    /**
     * Provedení zápisu čekajících dat zpět přímo do LDAPu.
     */
    public boolean commit() {

        if (this.dataToWrite.size() > 0)
        {
            Iterator<String> writeIterator = this.dataToWrite.keySet().iterator();
            while (writeIterator.hasNext()) {
                String dn = writeIterator.next();
                Map<EBakaLDAPAttributes, String> dataSet = this.dataToWrite.get(dn);

                Boolean attrMod = true;

                for (Map.Entry<EBakaLDAPAttributes, String> data : dataSet.entrySet()) {

                    if (Settings.getInstance().debugMode()) {
                        ReportManager.log(EBakaLogType.LOG_LDAP, "REPLACE [" + data.getKey() + "] = [" + data.getValue().toString() + "] @ [" + dn + "]");
                    }

                    if (!Settings.getInstance().develMode()) {
                        attrMod &= BakaADAuthenticator.getInstance().replaceAttribute(dn, data.getKey(), data.getValue());
                    } else {
                        // attrMod vždy true
                        ReportManager.log(EBakaLogType.LOG_DEVEL, "[ LDAP ] Zde proběhne zápis do ostrých dat.");
                    }
                }

                if (attrMod) {
                    writeIterator.remove();
                }
            }

            return (this.dataToWrite.size() == 0);
        }

        return true;
    }


    @Override
    public String toString() {
        StringBuilder records = new StringBuilder();

        if (this.data.size() > 0) {

            records.append("Seznam obsahuje celkem " + this.data.size() + " položek.\n");

            Iterator<String> keyIterator = this.data.keySet().iterator();
            while (keyIterator.hasNext()) {
                records.append(keyIterator.next());
                records.append("\n");
            }

        } else {
            records.append("Seznam je prázný.\n");
        }

        return records.toString();
    }

    @Override
    public Iterator<String> iterator() {

        if (this.iterator == null) {
            this.iterator = this.data.keySet().iterator();
        }

        return this.iterator;
    }

    @Override
    public void resetIterator() {
        this.iterator = null;
    }

}
