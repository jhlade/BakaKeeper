package cz.zsstudanka.skola.bakakeeper.model.collections;

import cz.zsstudanka.skola.bakakeeper.components.ReportManager;
import cz.zsstudanka.skola.bakakeeper.connectors.BakaSQL;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLogType;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaSQL;
import cz.zsstudanka.skola.bakakeeper.model.entities.DataSQL;
import cz.zsstudanka.skola.bakakeeper.model.interfaces.IRecords;
import cz.zsstudanka.skola.bakakeeper.settings.Settings;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class SQLrecords implements IRecords {

    private EBakaSQL sql_table;

    /**
     * Pole žáka a s ním spjatého primárního zákonného zůástupce.
     */
    private final EBakaSQL[] STUDENT_DATA_STRUCTURE = {
            EBakaSQL.F_STU_ID, // PK
            EBakaSQL.F_STU_CLASS_ID,
            EBakaSQL.F_GUA_SURNAME, EBakaSQL.F_GUA_GIVENNAME,
            EBakaSQL.F_STU_CLASS, EBakaSQL.F_STU_BK_CLASSYEAR, EBakaSQL.F_STU_BK_CLASSLETTER,
            EBakaSQL.F_STU_MAIL, // [žák]
            EBakaSQL.S_STU_BK_GUA_ID, // ZZ PK
            EBakaSQL.S_STU_BK_GUA_SURNAME, EBakaSQL.S_STU_BK_GUA_GIVENNAME,
            EBakaSQL.S_STU_BK_GUA_MAIL, EBakaSQL.S_STU_BK_GUA_MOBILE // [zákonný zástupce]
    };

    /**
     * Pole učitele; TODO - možná opačný join s příznakem třídnictví?
     *
     * Tohle jsou všichni aktivní učitelé, nenulová třída označuje třídnictví:
     * SELECT dbo.ucitele.INTERN_KOD, dbo.ucitele.E_MAIL, dbo.ucitele.JMENO, dbo.ucitele.PRIJMENI, dbo.tridy.ZKRATKA
     * FROM dbo.tridy RIGHT JOIN dbo.ucitele ON (dbo.tridy.TRIDNICTVI = dbo.ucitele.INTERN_KOD)
     * WHERE dbo.ucitele.UCI_LETOS = '1' AND (dbo.tridy.ZKRATKA LIKE '[1-9].[A-E]' OR dbo.tridy.ZKRATKA IS NULL)
     * ORDER BY dbo.ucitele.PRIJMENI;
     *
     * Pouze třídní učitelé:
     * SELECT dbo.ucitele.INTERN_KOD, dbo.ucitele.E_MAIL, dbo.ucitele.JMENO, dbo.ucitele.PRIJMENI, dbo.tridy.ZKRATKA
     * FROM dbo.ucitele RIGHT JOIN dbo.tridy ON (dbo.tridy.TRIDNICTVI = dbo.ucitele.INTERN_KOD)
     * WHERE dbo.tridy.ZKRATKA LIKE '[1-9].[A-E]' AND dbo.ucitele.UCI_LETOS = '1'
     * ORDER BY dbo.tridy.ZKRATKA;
     */
    private final EBakaSQL[] FACULTY_DATA_STRUCTURE = {
            EBakaSQL.F_FAC_ID, // PK
            EBakaSQL.F_FAC_SURNAME, EBakaSQL.F_FAC_GIVENNAME, // jméno
            EBakaSQL.F_FAC_EMAIL,
            EBakaSQL.F_CLASS_LABEL, // je vlastníkem třídy (literál [1-9].[A-E] nebo NULL)
    };

    private final EBakaSQL[] GUARDIAN_DATA_STRUCTURE = {
        // TODO - (zvlášť ?) zákonní zástupci
    };

    /** použitá struktura */
    private EBakaSQL[] dataStructure;
    /** konkrétní SELECT */
    private String select;

    /** hrubá data; klíč INTERN_KOD, data = mapa podle SQL */
    private LinkedHashMap<String, DataSQL> data = new LinkedHashMap<>();

    /**
     * Data připravená k atomickému transakčnímu zápisu pomocí UPDATE;
     * klíč = pár tabulka + interní kód objektu (table, pk = 'id'),
     * data = pár pole + nová hodnota (colName = 'newValue')
     *
     * [!] Z bezpečnostních důvodů by mělo být v praxi používáno
     * pouze pro pole e-mailové adresy žáka.
     */
    private LinkedHashMap<Map<EBakaSQL, HashMap<EBakaSQL, String>>, Map<EBakaSQL, String>> writeData = new LinkedHashMap<>();

    /** datový instanční iterátor */
    @Deprecated
    private Iterator dataIterator;
    /** instanční iterátor */
    private Iterator iterator;

    public SQLrecords() {
        populate();
    }


    /**
     * Konstruktor pro data učitelů.
     *
     * @param classTeachersOnly je-li PRAVDA, konstruují se pouze učitelé s aktuálním třídnictvím
     */
    public SQLrecords(boolean classTeachersOnly) {

        // tabulka učitele
        this.sql_table = EBakaSQL.TBL_FAC;
        // struktura pro tabulku učitele
        this.dataStructure = FACULTY_DATA_STRUCTURE;

        StringBuilder selectBuilder = new StringBuilder();

        // INTERN_KOD
        selectBuilder.append(EBakaSQL.F_FAC_ID.field());
        selectBuilder.append(", ");
        // E_MAIL
        selectBuilder.append(EBakaSQL.F_FAC_EMAIL.field());
        selectBuilder.append(", ");
        // JMENO
        selectBuilder.append(EBakaSQL.F_FAC_GIVENNAME.field());
        selectBuilder.append(", ");
        // PRIJMENI
        selectBuilder.append(EBakaSQL.F_FAC_SURNAME.field());
        selectBuilder.append(", ");
        // TRIDA
        selectBuilder.append(EBakaSQL.F_CLASS_LABEL.field());
        selectBuilder.append(" ");

        selectBuilder.append("FROM ");

        // pouze třídní učitelé
        if (classTeachersOnly) {
            selectBuilder.append(EBakaSQL.TBL_FAC.field() + " RIGHT JOIN " + EBakaSQL.TBL_CLASS.field() + " ");
            selectBuilder.append("ON ");
            selectBuilder.append("(");
                selectBuilder.append(EBakaSQL.F_CLASS_TEACHER.field() + " = " + EBakaSQL.F_FAC_ID.field());
            selectBuilder.append(") ");

            selectBuilder.append("WHERE ");
            selectBuilder.append(EBakaSQL.F_CLASS_LABEL.field() + " LIKE '[1-9].[A-E]' ");
            selectBuilder.append("AND ");
            selectBuilder.append(EBakaSQL.F_FAC_ACTIVE.field() + " = '" + EBakaSQL.LIT_TRUE.field() + "' ");

            selectBuilder.append("ORDER BY " + EBakaSQL.F_CLASS_LABEL.field() + " ASC");
        } else {
            // všichni aktivní učitelé
            selectBuilder.append(EBakaSQL.TBL_CLASS.field() + " RIGHT JOIN " + EBakaSQL.TBL_FAC.field() + " ");
            selectBuilder.append("ON ");
            selectBuilder.append("(");
            selectBuilder.append(EBakaSQL.F_CLASS_TEACHER.field() + " = " + EBakaSQL.F_FAC_ID.field());
            selectBuilder.append(") ");

            selectBuilder.append("WHERE ");
            selectBuilder.append(EBakaSQL.F_FAC_ACTIVE.field() + " = '" + EBakaSQL.LIT_TRUE.field() + "' ");
            selectBuilder.append("AND ");
            selectBuilder.append("(");
                selectBuilder.append(EBakaSQL.F_CLASS_LABEL.field() + " LIKE '[1-9].[A-E]' ");
                selectBuilder.append("OR ");
                selectBuilder.append(EBakaSQL.F_CLASS_LABEL.field() + " IS NULL");
            selectBuilder.append(")");

            selectBuilder.append("ORDER BY " + EBakaSQL.F_FAC_SURNAME.field() + " ASC");
        }

        this.select = selectBuilder.toString();

        populate();
    }

    /**
     * Konstruktor pro data žáků.
     *
     * @param classYear ročník žáků
     * @param classLetter písmeno třídy žáků
     */
    public SQLrecords(Integer classYear, String classLetter) {

        // tabulka žáka
        this.sql_table = EBakaSQL.TBL_STU;
        // struktura pro tabulku žáka a zákonného zástupce
        this.dataStructure = STUDENT_DATA_STRUCTURE;

        StringBuilder selectBuilder = new StringBuilder();

        // INTERN_KOD
        selectBuilder.append(EBakaSQL.F_STU_ID.field());
        selectBuilder.append(", ");
        // C_TR_VYK
        selectBuilder.append(EBakaSQL.F_STU_CLASS_ID.field());
        selectBuilder.append(", ");
        // PRIJMENI
        selectBuilder.append(EBakaSQL.F_STU_SURNAME.field());
        selectBuilder.append(", ");
        // JMENO
        selectBuilder.append(EBakaSQL.F_STU_GIVENNAME.field());
        selectBuilder.append(", ");
        // TRIDA
        selectBuilder.append(EBakaSQL.F_STU_CLASS.field());
        selectBuilder.append(", ");
        // EMAIL
        selectBuilder.append(EBakaSQL.F_STU_MAIL.field());
        selectBuilder.append(", ");
        // classYear
        selectBuilder.append(EBakaSQL.S_STU_BK_CLASSYEAR.field());
        selectBuilder.append(", ");
        // classLetter
        selectBuilder.append(EBakaSQL.S_STU_BK_CLASSLETTER.field());
        selectBuilder.append(", ");

        // GUARDIAN
        selectBuilder.append(EBakaSQL.S_STU_BK_GUA_ID.field());
        selectBuilder.append(", ");
        selectBuilder.append(EBakaSQL.S_STU_BK_GUA_SURNAME.field());
        selectBuilder.append(", ");
        selectBuilder.append(EBakaSQL.S_STU_BK_GUA_GIVENNAME.field());
        selectBuilder.append(", ");
        selectBuilder.append(EBakaSQL.S_STU_BK_GUA_MOBILE.field());
        selectBuilder.append(", ");
        selectBuilder.append(EBakaSQL.S_STU_BK_GUA_MAIL.field());
        selectBuilder.append(" ");

        // FROM
        selectBuilder.append("FROM " + EBakaSQL.TBL_STU.field() + " ");
        selectBuilder.append("LEFT JOIN " + EBakaSQL.TBL_GUA.field() + " ");
        selectBuilder.append("ON ");
        selectBuilder.append("(");
            selectBuilder.append(EBakaSQL.F_GUA_ID.field() + " = ");
            selectBuilder.append("(");
                selectBuilder.append("SELECT TOP 1 " + EBakaSQL.F_GS_GUAID.field() + " ");
                selectBuilder.append("FROM " + EBakaSQL.TBL_STU_GUA.field() + " ");
                selectBuilder.append("WHERE " + EBakaSQL.F_GS_STUID.field() + " = " + EBakaSQL.F_STU_ID.field() + " ");
                selectBuilder.append("AND ");
                selectBuilder.append("(");
                    selectBuilder.append(EBakaSQL.FS_GS_IS_GUA.field() + " = '" + EBakaSQL.LIT_TRUE.field() + "'");
                    selectBuilder.append(" AND ");
                    selectBuilder.append(EBakaSQL.FS_GS_IS_PRI.field() + " = '" + EBakaSQL.LIT_TRUE.field() + "'");
                selectBuilder.append(")");
            selectBuilder.append(")");
        selectBuilder.append(")");
        selectBuilder.append(" ");

        // WHERE
        selectBuilder.append("WHERE ");
        selectBuilder.append(EBakaSQL.F_STU_CLASS.field() + " LIKE '[1-9].[A-E]' ");
        selectBuilder.append("AND " + EBakaSQL.F_STU_EXPIRED.field() + " IS NULL ");

        // filtrování podle ročníku/třídy
        if (classYear != null || classLetter != null) {
            String tmpLetter = (classLetter == null) ? "%" : classLetter;
            String tmpYear = (classYear == null) ? "%" : classYear.toString();
            selectBuilder.append("AND " + EBakaSQL.F_STU_CLASS.field() + " LIKE '" + tmpYear + "." + tmpLetter + "'");
            selectBuilder.append(" ");
        }

        // pořadí
        selectBuilder.append("ORDER BY " + EBakaSQL.F_STU_BK_CLASSYEAR.basename() + " DESC, " + EBakaSQL.F_STU_BK_CLASSLETTER.basename() + " ASC, " + EBakaSQL.F_STU_SURNAME.field() + " ASC, " + EBakaSQL.F_STU_GIVENNAME.field() + " ASC");

        this.select = selectBuilder.toString();

        populate();
    }

    private void populate() {
        // připojení k databázi
        BakaSQL.getInstance().connect();

        StringBuilder genSelect = new StringBuilder();
        genSelect.append("SELECT ");
        genSelect.append(this.select);
        genSelect.append(";");

        if (Settings.getInstance().debugMode()) {
            ReportManager.log(EBakaLogType.LOG_SQL, "Provede se následující dotaz:");
            ReportManager.log(EBakaLogType.LOG_SQL, genSelect.toString());
        }

        try {
            // provedení dotazu
            ResultSet rs = BakaSQL.getInstance().select(genSelect.toString());

            // řádek
            while (rs.next()) {
                // primární klíč je první položka v poli
                String rowID = rs.getString(this.dataStructure[0].basename());
                DataSQL rowData = new DataSQL();//new HashMap<String, String>();

                for (EBakaSQL col : this.dataStructure) {
                    rowData.put(col.basename(), (rs.getString(col.basename()) == null) ? EBakaSQL.NULL.basename() : rs.getString(col.basename()).trim());

                    if (Settings.getInstance().debugMode()) {
                        if (rs.getString(col.basename()) == null) {
                            ReportManager.log(EBakaLogType.LOG_SQL, "Nulová data pro položku [" + col.field() + "] v záznamu [" + rowID + "]. Chybně vyplněné údaje v evidenci?");
                        }
                    }
                }

                // prázdný příznak zpracování
                rowData.put(EBakaSQL.BK_FLAG.basename(), EBakaSQL.LIT_FALSE.basename());

                addRecord(rowID, rowData);
            }

        } catch (Exception e) {
            ReportManager.handleException("Vykonávání SQL dotazu se nezdařilo.", e, true);
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
     * Získání jednoho záznamu podle ID.
     *
     * @param id interní kód
     * @return data
     */
    public DataSQL get(String id) {
        if (this.data.containsKey(id)) {
            return this.data.get(id);
        }

        return null;
    }

    /**
     * Přidání záznamu do kolekce.
     *
     * @param id interní kód záznamu
     * @param data získaná data
     */
    public void addRecord(String id, DataSQL data) {
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
            return (this.data.get(key).get(EBakaSQL.BK_FLAG.basename()).equals(EBakaSQL.LIT_TRUE.basename())) ? true : false;
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
            this.data.get(key).replace(EBakaSQL.BK_FLAG.basename(), (flag) ? EBakaSQL.LIT_TRUE.basename() : EBakaSQL.LIT_FALSE.basename());
        }
    }

    /**
     * Získání podmnožiny se zadaným stavem příznaku.
     *
     * @param flag příznak
     * @return podmnožina s daným příznakem
     */
    public LinkedHashMap<String, DataSQL> getSubsetWithFlag(Boolean flag) {
        return getSubsetBy(EBakaSQL.BK_FLAG, (flag) ? EBakaSQL.LIT_TRUE.basename() : EBakaSQL.LIT_FALSE.basename());
    }

    /**
     * Získání podmnožiny dat podle zadaného klíče a hodnoty.
     *
     * @param attr klíč k porovnání
     * @param value hodnota k porovnání
     * @return podmnožina dat
     */
    public LinkedHashMap<String, DataSQL> getSubsetBy(EBakaSQL attr, String value) {
        LinkedHashMap<String, DataSQL> subset = new LinkedHashMap<>();

        // interní iterátor
        Iterator<String> internalIterator = this.data.keySet().iterator();
        while (internalIterator.hasNext()) {

            String subID = internalIterator.next();

            if (get(subID).containsKey(attr.basename()) && get(subID).get(attr.basename()).equals(value)) {
                subset.put(subID, get(subID));
            }
        }

        return subset;
    }

    /**
     * Přidání dat k zápisu.
     *
     * @param id hodnota primárního klíče
     * @param data data připravená k zápisu
     */
    public void addWriteData(String id, Map<EBakaSQL, String> data) {

        // identifikace primárního klíče
        HashMap<EBakaSQL, String> primaryKey = new HashMap<>();
        primaryKey.put(this.sql_table.primaryKey(), id);

        // identifikace tabulky
        HashMap<EBakaSQL, HashMap<EBakaSQL, String>> table = new HashMap<>();
        table.put(this.sql_table, primaryKey);

        // data
        this.writeData.put(table, data);
    }

    /**
     * Počet čekajících operací zápisu.
     *
     * @return počet čekajících operací
     */
    public Integer writesRemaining() {
        return this.writeData.size();
    }

    /**
     * Provedení zápisu dat.
     *
     * @return
     */
    public Boolean commit() {

        if (this.writeData.size() > 0) {

            if (Settings.getInstance().debugMode()) {
                ReportManager.log(EBakaLogType.LOG_SQL, "Proběhne pokus o zpracování " + this.writeData.size() + " SQL transakcí.");
            }

            /**
             * 1 - nová hodnota
             * 2 - konkrétní primární klíč
             *
             * {tableName} - tabulka
             * {colName} - pole
             * {primaryKey} - pole primárního klíče
             */
            String genericUpdatePreparedStatement = "UPDATE {tableName} SET {colName} = ? WHERE {primaryKey} = ?";
            PreparedStatement updatePS = null;

            Iterator<Map<EBakaSQL, HashMap<EBakaSQL, String>>> writeKeyIterator = this.writeData.keySet().iterator();
            while (writeKeyIterator.hasNext()) {
                Map<EBakaSQL, HashMap<EBakaSQL, String>> writeKey = writeKeyIterator.next();

                // zpracování
                Boolean update = true;

                // identifikace dat
                Iterator<EBakaSQL> tableIterator = writeKey.keySet().iterator();
                while (tableIterator.hasNext()) {

                    // tabulka
                    EBakaSQL tableName = tableIterator.next();

                    if (Settings.getInstance().debugMode()) {
                        ReportManager.log(EBakaLogType.LOG_SQL, "Tabulka: " + tableName.field());
                    }

                    // řádek
                    Iterator<EBakaSQL> primaryKeyFieldIterator = writeKey.get(tableName).keySet().iterator();
                    while (primaryKeyFieldIterator.hasNext()) {

                        EBakaSQL primaryKeyField = primaryKeyFieldIterator.next();
                        String primaryKeyValue = writeKey.get(tableName).get(primaryKeyField);

                        if (Settings.getInstance().debugMode()) {
                            ReportManager.log(EBakaLogType.LOG_SQL, "Primární klíč: " + primaryKeyField.field() + " = [" + primaryKeyValue + "]");
                        }

                        // data - atomické transakce
                        Map<EBakaSQL, String> data = this.writeData.get(writeKey);
                        if (Settings.getInstance().debugMode()) {
                            ReportManager.log(EBakaLogType.LOG_SQL, "Páry data k zápisu: " + data.size());
                        }

                        Iterator<EBakaSQL> dataIterator = data.keySet().iterator();
                        while (dataIterator.hasNext()) {

                            EBakaSQL dataField = dataIterator.next();
                            String dataValue =  data.get(dataField);

                            if (Settings.getInstance().debugMode()) {
                                ReportManager.log(EBakaLogType.LOG_SQL, "Proběhne pokus o SQL transakci s dotazem");
                                ReportManager.log(EBakaLogType.LOG_SQL,"UPDATE " + tableName.field() + " SET " + dataField.field()
                                                + " = '" + dataValue + "' "
                                                + "WHERE " + primaryKeyField.field() + " = '" + primaryKeyValue + "';");
                            }

                            try {
                                // vypnutí autocommitu
                                BakaSQL.getInstance().getConnection().setAutoCommit(false);
                                // příprava dotazu
                                updatePS = BakaSQL.getInstance().getConnection().prepareStatement(
                                        genericUpdatePreparedStatement.replace("{tableName}", tableName.field())
                                                .replace("{colName}", dataField.field())
                                                .replace("{primaryKey}", primaryKeyField.field())
                                );

                                updatePS.setString(1, dataValue);
                                updatePS.setString(2, primaryKeyValue);

                                // provedení atomického dotazu
                                if (!Settings.getInstance().develMode()) {

                                    int executeResult = updatePS.executeUpdate();

                                    if (Settings.getInstance().debugMode()) {
                                        ReportManager.log(EBakaLogType.LOG_SQL, "Bylo ovlivněno " + executeResult + " řádků.");
                                    }

                                    update &= (executeResult == 1);
                                } else {
                                    ReportManager.log(EBakaLogType.LOG_DEVEL, "[ SQL ] Zde proběhne zápis do ostrých dat.");
                                }

                                // commit
                                if (!Settings.getInstance().develMode()) {
                                    BakaSQL.getInstance().getConnection().commit();
                                }
                            } catch (Exception e) {
                                ReportManager.handleException("Nezdařilo se provést SQL transakci! Proběhne pokus o rollback.", e);

                                // rollback
                                try {
                                    if (!Settings.getInstance().develMode()) {
                                        BakaSQL.getInstance().getConnection().rollback();
                                    }
                                } catch (Exception eR) {
                                    ReportManager.handleException("Nezdařilo se vykonat rollback!", eR);
                                }

                            } finally {

                                try {
                                    // uzavření
                                    if (updatePS != null) {
                                        updatePS.close();
                                    }

                                    // znovuzapnutí autocommitu
                                    BakaSQL.getInstance().getConnection().setAutoCommit(true);
                                } catch (Exception eF) {
                                    ReportManager.handleException("Během pokusu o SQL transakci bylo ztraceno spojení se serverem.", eF);
                                }
                            } // transakce
                        } //data
                    } // řádek
                } // tabulka

                if (update) {
                    writeKeyIterator.remove();
                } else {
                    if (Settings.getInstance().beVerbose()) {
                        ReportManager.log(EBakaLogType.LOG_ERR_VERBOSE, "Nebylo možné provést některou z transakcí.");
                    }
                }
            } // pole dat ke zpětnému zápisu

            return (this.writeData.size() == 0);
        } // žádná data

        return true;
    }

    @Override
    public String toString() {
        StringBuilder print = new StringBuilder();

        if (this.data.size() > 0) {

            print.append("Seznam obsahuje celkem " + this.data.size() + " položek.\n");

            for (Map.Entry<String, DataSQL> recEntry : data.entrySet()) {
                print.append(recEntry.getKey());
                print.append(" : ");

                print.append(recEntry.getValue().get(EBakaSQL.F_STU_CLASS.basename()).toString());
                print.append(" : ");

                print.append(String.format("%02d", Integer.parseInt(recEntry.getValue().get(EBakaSQL.F_STU_CLASS_ID.basename()))));
                print.append(" : ");

                print.append(recEntry.getValue().get(EBakaSQL.F_STU_SURNAME.basename()).toString());
                print.append(" ");
                print.append(recEntry.getValue().get(EBakaSQL.F_STU_GIVENNAME.basename()).toString());

                print.append("\n");
            }

        } else {
            print.append("Seznam je prázný.\n");
        }

        return print.toString();
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
