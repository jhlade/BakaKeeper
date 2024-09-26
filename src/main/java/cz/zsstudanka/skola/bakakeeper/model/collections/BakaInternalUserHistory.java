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

    private final String dataFileName = "users.dat";
    private Map<String, Map<Date, BakaInternalUser>> database = new HashMap<>();

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

        if (!data.isFile()) {
            // uložit nový prázdný soubor
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
            if (Settings.getInstance().beVerbose()) {
                ReportManager.log(EBakaLogType.LOG_VERBOSE, "Probíhá deserializace datového souboru databáze lokálních uživatelů.");
            }

            ObjectInputStream inStream = new ObjectInputStream(
                new GZIPInputStream(
                        new EncryptionInputStream(
                                new FileInputStream(data),
                                Settings.getInstance().getPass().toCharArray()
                        )
                )
            );

            this.database = (Map<String, Map<Date, BakaInternalUser>>) inStream.readObject();

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

            if (Settings.getInstance().beVerbose()) {
                ReportManager.log(EBakaLogType.LOG_VERBOSE, "Probíhá serializace databáze lokálních uživatelů do souboru.");
            }

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

}
