package cz.zsstudanka.skola.bakakeeper.model.collections;

import cz.zsstudanka.skola.bakakeeper.connectors.BakaSQL;
import cz.zsstudanka.skola.bakakeeper.model.interfaces.IRecords;
import cz.zsstudanka.skola.bakakeeper.settings.Settings;

import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class SQLrecords implements IRecords {

    final String FLAG_ID = "baka_flag";
    final String FLAG_0  = "0";
    final String FLAG_1  = "1";

    private final String[] STUDENT_DATA_STRUCTURE = {
            "INTERN_KOD", "C_TR_VYK", "PRIJMENI", "JMENO", "TRIDA", "B_ROCNIK", "B_TRIDA", "E_MAIL", // žák
            "ZZ_KOD", "ZZ_PRIJMENI", "ZZ_JMENO", "ZZ_TELEFON", "ZZ_MAIL" // zákonný zástupce
    };

    private final String[] FACULTY_DATA_STRUCTURE = {
            // TODO - učitelé
    };

    /** hrubá data; klíč INTERN_KOD, data = mapa podle SQL */
    private LinkedHashMap<String, Map<String, String>> data = new LinkedHashMap<>();

    /**
     * data připravená k zápisu pomocí UPDATE;
     * klíč = pár tabulka + interní kód objektu,
     * data = pár pole + nová hodnota
     *
     * Z bezpečnostních důvodů by mělo být používáno pouze pro pole e-mailové adresy žáka.
     */
    private LinkedHashMap<Map<String, String>, HashMap<String, String>> writeData = new LinkedHashMap<>();

    /** instanční iterátor */
    private Iterator iterator;

    public SQLrecords() {

        // připojení k databázi
        BakaSQL.getInstance().connect();

        // TODO přepis pomocí EBakaSQL
        // SQL dotaz
        String select = "SELECT dbo.zaci.INTERN_KOD,dbo.zaci.C_TR_VYK,dbo.zaci.PRIJMENI,dbo.zaci.JMENO,dbo.zaci.TRIDA,dbo.zaci.E_MAIL,SUBSTRING(dbo.zaci.TRIDA, 1, 1) AS B_ROCNIK,SUBSTRING(dbo.zaci.TRIDA, 3, 1) AS B_TRIDA," // data žáka
                + "dbo.zaci_zzd.ID AS ZZ_KOD,dbo.zaci_zzd.PRIJMENI AS ZZ_PRIJMENI,dbo.zaci_zzd.JMENO AS ZZ_JMENO,dbo.zaci_zzd.TEL_MOBIL AS ZZ_TELEFON,dbo.zaci_zzd.E_MAIL AS ZZ_MAIL " // data ZZ
                + "FROM dbo.zaci LEFT JOIN dbo.zaci_zzd ON (dbo.zaci_zzd.ID = (SELECT TOP 1 dbo.zaci_zzr.ID_ZZ FROM dbo.zaci_zzr WHERE dbo.zaci_zzr.INTERN_KOD = dbo.zaci.INTERN_KOD AND (dbo.zaci_zzr.JE_ZZ = '1' AND dbo.zaci_zzr.PRIMARNI = '1'))) " // detekce primárního ZZ
                + "WHERE dbo.zaci.TRIDA LIKE '%.%' AND dbo.zaci.EVID_DO IS NULL " // žák existuje
                //+ "AND dbo.zaci.TRIDA = '" + this.getCisloRocniku() + "." + this.getPismeno() + "' " // jedna konkrétní třída
                + "ORDER BY B_ROCNIK DESC, B_TRIDA ASC, dbo.zaci.PRIJMENI ASC, dbo.zaci.JMENO ASC;"; // seřazení podle třídy DESC, abecedy ASC

        try {
            // provedení dotazu
            ResultSet rs = BakaSQL.getInstance().select(select);

            // řádek
            while (rs.next()) {
                String rowID = rs.getString("INTERN_KOD");
                Map<String, String> rowData = new HashMap<String, String>();

                for (String col : this.STUDENT_DATA_STRUCTURE) {
                    rowData.put(col, (rs.getString(col) == null) ? "(NULL)" : rs.getString(col).trim());

                    if (Settings.getInstance().debugMode()) {
                        if (rs.getString(col) == null) {
                            System.out.println("[ DEBUG ] Nulová data pro položku [" + col + "] v záznamu [" + rowID + "]. Chybně vyplněné údaje v ecidenci?");
                        }
                    }
                }

                // prázdný příznak zpracování
                rowData.put(FLAG_ID, FLAG_0);

                addRecord(rowID, rowData);
            }

        } catch (Exception e) {

            if (Settings.getInstance().beVerbose()) {
                System.err.println("[ CHYBA ] Vykonávání SQL dotazu se nezdařilo.");
            }

            if (Settings.getInstance().debugMode()) {
                System.err.println("[ CHYBA ] " + e.getLocalizedMessage());
                e.printStackTrace(System.err);
            }

        }
    }

    /**
     * Počet záznamů v hrubých datech.
     *
     * @return počet záznamů
     */
    public Integer count() {
        return this.data.size();
    }

    /**
     * Přidání záznamu do kolekce.
     *
     * @param id interní kód záznamu
     * @param data získaná data
     */
    public void addRecord(String id, Map<String, String> data) {
        this.data.put(id, data);
    }

    /**
     * Odebrání záznamu z kolekce, pokud existuje.
     *
     * @param id interní kód záznamu
     */
    public void removeRecord(String id) {
        if (this.data.containsKey(id)) {
            this.data.remove(id);
        }
    }

    /**
     * Získání příznaku konkrétního objektu.
     *
     * @param key klíč objektu
     * @return příznak zpracování
     */
    @Override
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
    @Override
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
    public LinkedHashMap<String, Map<String, String>> getSubsetWithFlag(Boolean flag) {
        LinkedHashMap<String, Map<String, String>> subset = new LinkedHashMap<>();

        // nový interní iterátor
        Iterator<Map.Entry<String, Map<String, String>>> internalIterator = this.data.entrySet().iterator();

        while (internalIterator.hasNext()) {
            Map.Entry<String, Map<String, String>> record = internalIterator.next();

            // naplnění podmnožiny záznamy s daným příznakem
            if (record.getValue().get(FLAG_ID).equals((flag) ? FLAG_1 : FLAG_0)) {
                subset.put(record.getKey(), record.getValue());
            }
        }

        return subset;
    }

    /**
     * Přidání dat k zápisu.
     *
     * @param id
     * @param data
     */
    public void addWriteData(String id, Map<String, String> data) {

    }

    /**
     * Provedení zápisu dat.
     *
     * @return
     */
    public Boolean commit() {

        // TODO
        // UPDATE {table}
        // SET {col1} = '{val1}', {col2} = '{val2}', ...
        // WHERE {id_field} = '{id}'

        return true;
    }

    @Override
    public String toString() {
        StringBuilder print = new StringBuilder();

        if (this.data.size() > 0) {

            print.append("Seznam obsahuje celkem " + this.data.size() + " položek.\n");

            for (Map.Entry<String, Map<String, String>> recEntry : data.entrySet()) {
                print.append(recEntry.getKey());
                print.append(" : ");

                print.append(recEntry.getValue().get("TRIDA").toString());
                print.append(" : ");

                print.append(String.format("%02d", Integer.parseInt(recEntry.getValue().get("C_TR_VYK"))));
                print.append(" : ");

                print.append(recEntry.getValue().get("PRIJMENI").toString());
                print.append(" ");
                print.append(recEntry.getValue().get("JMENO").toString());

                print.append("\n");
            }

        } else {
            print.append("Seznam je prázný.\n");
        }

        return print.toString();
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
