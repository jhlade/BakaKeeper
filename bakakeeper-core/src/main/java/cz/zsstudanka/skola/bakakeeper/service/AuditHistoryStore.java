package cz.zsstudanka.skola.bakakeeper.service;

import cz.zsstudanka.skola.bakakeeper.components.EncryptionInputStream;
import cz.zsstudanka.skola.bakakeeper.components.EncryptionOutputStream;
import cz.zsstudanka.skola.bakakeeper.components.ReportManager;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLogType;
import cz.zsstudanka.skola.bakakeeper.model.InternalUserSnapshot;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Šifrované úložiště historických snapshotů interních uživatelů.
 * Ukládá data do souboru {@code users.dat} ve formátu:
 * {@code EncryptionOutputStream → GZIPOutputStream → ObjectOutputStream}.
 *
 * <p>Uchovává pouze hash hesla, metodu a sůl – <b>nikdy</b> plaintext heslo.</p>
 *
 * @author Jan Hladěna
 */
public class AuditHistoryStore {

    private static final String DEFAULT_FILE = "./users.dat";

    private final String filePath;
    private final char[] passphrase;

    /** login → (datum → snapshot) */
    private Map<String, Map<Date, InternalUserSnapshot>> store;

    /**
     * Vytvoří úložiště s výchozí cestou {@code ./users.dat}.
     *
     * @param passphrase heslo pro šifrování/dešifrování
     */
    public AuditHistoryStore(char[] passphrase) {
        this(DEFAULT_FILE, passphrase);
    }

    /**
     * Vytvoří úložiště s explicitní cestou.
     *
     * @param filePath   cesta k datovému souboru
     * @param passphrase heslo pro šifrování/dešifrování
     */
    public AuditHistoryStore(String filePath, char[] passphrase) {
        this.filePath = filePath;
        this.passphrase = passphrase;
        this.store = new HashMap<>();
        load();
    }

    /**
     * Uloží snapshot uživatele s aktuálním datem.
     *
     * @param snapshot snapshot k záloze
     */
    public void backup(InternalUserSnapshot snapshot) {
        store.computeIfAbsent(snapshot.login(), k -> new TreeMap<>())
                .put(new Date(), snapshot);
        save();
    }

    /**
     * Vrátí poslední známý snapshot pro daný login.
     *
     * @param login přihlašovací jméno
     * @return poslední snapshot, nebo prázdný pokud žádný neexistuje
     */
    public Optional<InternalUserSnapshot> getLastSnapshot(String login) {
        Map<Date, InternalUserSnapshot> history = store.get(login);
        if (history == null || history.isEmpty()) {
            return Optional.empty();
        }
        // TreeMap – poslední klíč je nejnovější
        if (history instanceof TreeMap<Date, InternalUserSnapshot> tm) {
            return Optional.of(tm.lastEntry().getValue());
        }
        // fallback – najít nejnovější ručně
        return history.entrySet().stream()
                .max(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue);
    }

    /**
     * Vrátí celou historii snapshotů pro daný login.
     *
     * @param login přihlašovací jméno
     * @return mapa datum → snapshot (může být prázdná)
     */
    public Map<Date, InternalUserSnapshot> getHistory(String login) {
        return store.getOrDefault(login, Map.of());
    }

    /**
     * Zjistí, zda existuje alespoň jedna záloha pro daný login.
     *
     * @param login přihlašovací jméno
     * @return true pokud záloha existuje
     */
    public boolean hasBackup(String login) {
        Map<Date, InternalUserSnapshot> history = store.get(login);
        return history != null && !history.isEmpty();
    }

    /** Načte úložiště ze souboru. */
    @SuppressWarnings("unchecked")
    private void load() {
        File file = new File(filePath);
        if (!file.exists()) {
            ReportManager.log(EBakaLogType.LOG_ERR_VERBOSE,
                    "Soubor auditní zálohy " + filePath + " neexistuje – bude vytvořen při první záloze.");
            return;
        }

        try (FileInputStream fis = new FileInputStream(file);
             EncryptionInputStream decrypted = new EncryptionInputStream(fis, passphrase);
             GZIPInputStream gzip = new GZIPInputStream(decrypted);
             ObjectInputStream ois = new ObjectInputStream(gzip)) {

            Object obj = ois.readObject();
            if (obj instanceof Map<?, ?> map) {
                this.store = (Map<String, Map<Date, InternalUserSnapshot>>) map;
            }
        } catch (Exception e) {
            ReportManager.log(EBakaLogType.LOG_ERR,
                    "Chyba při načítání auditní zálohy: " + e.getMessage());
            this.store = new HashMap<>();
        }
    }

    /** Uloží úložiště do souboru. */
    private void save() {
        try (FileOutputStream fos = new FileOutputStream(filePath);
             EncryptionOutputStream encrypted = new EncryptionOutputStream(fos, passphrase);
             GZIPOutputStream gzip = new GZIPOutputStream(encrypted);
             ObjectOutputStream oos = new ObjectOutputStream(gzip)) {

            oos.writeObject(store);
        } catch (Exception e) {
            ReportManager.log(EBakaLogType.LOG_ERR,
                    "Chyba při ukládání auditní zálohy: " + e.getMessage());
        }
    }
}
