package cz.zsstudanka.skola.bakakeeper.model.collections;

import cz.zsstudanka.skola.bakakeeper.components.EncryptionInputStream;
import cz.zsstudanka.skola.bakakeeper.components.EncryptionOutputStream;
import cz.zsstudanka.skola.bakakeeper.components.ReportManager;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLogType;
import cz.zsstudanka.skola.bakakeeper.model.entities.BakaInternalUser;
import cz.zsstudanka.skola.bakakeeper.settings.Settings;

import java.io.*;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Zálohy a obnovy stavu interních uživatelských účtů.
 * Pro šifrování je použito heslo správce AD.
 *
 * @author Jan Hladěna
 */
public class BakaInternalUserHistory implements Serializable {

    static final long serialVersionUID = 1003L;

    private final String dataFileName = "users.dat";
    private HashMap<String, HashMap<Date, BakaInternalUser>> database = new HashMap<String, HashMap<Date, BakaInternalUser>>();

    private static BakaInternalUserHistory instance = null;

    /**
     * Instance databáze.
     *
     * @return databáze záloh
     */
    public static BakaInternalUserHistory getInstance() {
        if (instance == null) {
            instance = new BakaInternalUserHistory();
        }

        return instance;
    }

    public BakaInternalUserHistory() {
        File data = new File(dataFileName);

        if (!data.exists()) {
            // uložit nový prázdný soubor
            ReportManager.log(EBakaLogType.LOG_VERBOSE, "Vytváří ze nová prázdná databáze záloh interních uživatelů.");
            saveDatabase(data);
        }

        loadDatabase(data);
    }

    /**
     * Načtení dat.
     *
     * @param data datový soubor
     */
    private void loadDatabase(File data) {

        try {
            ReportManager.log(EBakaLogType.LOG_VERBOSE, "Probíhá deserializace datového souboru databáze lokálních uživatelů.");

            ObjectInputStream inStream = new ObjectInputStream(
                new GZIPInputStream(
                        new EncryptionInputStream(
                                new FileInputStream(data),
                                Settings.getInstance().getPass().toCharArray()
                        )
                )
            );

            this.database = (HashMap<String, HashMap<Date, BakaInternalUser>>) inStream.readObject();

        } catch (Exception e) {
            ReportManager.handleException("Došlo k chybě při načítání databáze lokálních uživatelů.", e);
        }

    }

    /**
     * Uložení databáze lokálních uživatelů.
     *
     * @param data datový soubor
     */
    private void saveDatabase(File data) {

        try {

            ReportManager.log(EBakaLogType.LOG_VERBOSE, "Probíhá serializace databáze lokálních uživatelů do šifrovaného souboru.");

            ObjectOutputStream outStream = new ObjectOutputStream(
                new GZIPOutputStream(
                        new EncryptionOutputStream(
                                new FileOutputStream(data),
                                Settings.getInstance().getPass().toCharArray()
                        )
                )
            );

            outStream.writeObject(this.database);
            outStream.close();

        } catch (Exception e) {
            ReportManager.handleException("Došlo k chybě při ukládání databáze lokálních uživatelů.", e);
        }

    }

    /**
     * Uložení databáze záloh do souboru.
     *
     * @param filename název souboru
     */
    private void saveDatabase(String filename) {
        saveDatabase(new File(filename));
    }

    /**
     * Seznam položek v záloze.
     *
     * @param login login uživatele
     * @return položky záloh
     */
    public HashMap<Date, BakaInternalUser> list(String login) {
        if (database.containsKey(login)) {
            return database.get(login);
        }

        return null;
    }

    /**
     * Uložení položky do zálohy.
     *
     * @param login identifikace účtu podle loginu
     */
    public void backup(String login) {
        BakaInternalUser user = new BakaInternalUser(login);
        ReportManager.log(EBakaLogType.LOG_DEVEL, "Záloha interního uživatele\n" + user.toString());

        if (!user.isValid()) {
            ReportManager.log(EBakaLogType.LOG_ERR, "Požadovaný interní uživatel není platný.");
            return;
        }

        // kontrola existujících položek
        if (database.containsKey(login)) {
            for (Map.Entry<Date, BakaInternalUser> backupEntry : database.get(login).entrySet()) {
                // shodný záznam
                if (backupEntry.getValue().compareTo(user) == 0) {
                    ReportManager.log(EBakaLogType.LOG_VERBOSE, "Záloha záznamu již existuje, nebude proveden žádný nový zápis.");
                    return;
                }
            }
        } else {
            // nový vstup
            this.database.put(login, new HashMap<>());
        }

        // zápis zálohy
        this.database.get(login).put(user.getModifiedDate(), user);

        // uložení
        this.saveDatabase(dataFileName);
    }

    /**
     * Obnova ze zálohy – zpětný zápis do databáze.
     *
     * @param login přihlašovací jméno interního uživatele
     * @param date vybrané datum bodu obnovy
     * @return výsledek operace
     */
    public Boolean restore(String login, Date date) {
        // vyhledání v databázi
        if (this.database.containsKey(login)) {
            // záznamy existují, požadované datum
            if (this.database.get(login).containsKey(date)) {

                return this.database.get(login).get(date).writeBack();

            } else {
                ReportManager.log(EBakaLogType.LOG_ERR_VERBOSE, "Požadovaná záloha interního uživatele v databázi neexistuje.");
            }
        } else {
            ReportManager.log(EBakaLogType.LOG_ERR_VERBOSE, "Interní uživatel v databázi záloh neexistuje.");
        }

        return false;
    }

    public Boolean restore(String login, String date) {
        return restore(login, new Date(date));
    }

    /**
     * Obnova uživatele k poslední provedené záloze.
     *
     * @param login přihlašovací jméno uživatele
     * @return výsledek operace
     */
    public Boolean restore(String login) {
        return restore(login, -1);
    }

    /**
     * Obnova uživatele podle specifického bodu daného pořadím.
     *
     * @param login přihlašovací jméno uživatele
     * @param index pořadí, nebo -1 pro poslední záznam
     * @return úspěch operace
     */
    public Boolean restore(String login, Integer index) {

        // neplatné pořadí
        if (index < -1) {
            ReportManager.log(EBakaLogType.LOG_ERR_VERBOSE, "Neplatná položka databáze.");
            return false;
        }

        // vyhledání v databázi
        if (this.database.containsKey(login)) {

            if (index == -1) {
                index = this.database.get(login).size() - 1;
            }

            int i = -1;
            for (Map.Entry<Date, BakaInternalUser> backupEntry : this.database.get(login).entrySet()) {
                i++;

                if (i != index) {
                    continue;
                }

                return backupEntry.getValue().writeBack();
            }

        }

        return false;
    }

}
