package cz.zsstudanka.skola.bakakeeper.config;

import cz.zsstudanka.skola.bakakeeper.components.EncryptionInputStream;
import cz.zsstudanka.skola.bakakeeper.components.EncryptionOutputStream;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Načítání a ukládání konfigurace v šifrovaném YAML formátu.
 * Používá AES-256-GCM šifrování přes EncryptionInputStream/OutputStream.
 *
 * Formát souboru: YAML → GZIP → AES-256-GCM → soubor (.dat)
 *
 * @author Jan Hladěna
 */
public class EncryptedConfigLoader {

    private EncryptedConfigLoader() {
    }

    /**
     * Načte šifrovanou konfiguraci ze souboru.
     *
     * @param file soubor s šifrovanou konfigurací (.dat)
     * @param passphrase heslo pro dešifrování
     * @return načtená konfigurace
     * @throws IOException chyba při čtení
     */
    public static YamlAppConfig load(File file, char[] passphrase) throws IOException {
        InputStream input;
        if (passphrase != null && passphrase.length > 0) {
            input = new EncryptionInputStream(new FileInputStream(file), passphrase);
        } else {
            input = new FileInputStream(file);
        }

        try (InputStream yamlStream = new GZIPInputStream(input)) {
            return new YamlAppConfig(yamlStream);
        }
    }

    /**
     * Načte nešifrovanou YAML konfiguraci ze souboru.
     *
     * @param file soubor s YAML konfigurací (.yml)
     * @return načtená konfigurace
     * @throws IOException chyba při čtení
     */
    public static YamlAppConfig loadPlain(File file) throws IOException {
        try (InputStream input = new FileInputStream(file)) {
            return new YamlAppConfig(input);
        }
    }

    /**
     * Uloží konfiguraci do šifrovaného souboru.
     *
     * @param config konfigurace k uložení
     * @param file výstupní soubor (.dat)
     * @param passphrase heslo pro šifrování
     * @throws IOException chyba při zápisu
     */
    public static void save(YamlAppConfig config, File file, char[] passphrase) throws IOException {
        OutputStream output;
        if (passphrase != null && passphrase.length > 0) {
            output = new EncryptionOutputStream(new FileOutputStream(file), passphrase);
        } else {
            output = new FileOutputStream(file);
        }

        try (OutputStream gzip = new GZIPOutputStream(output)) {
            OutputStreamWriter writer = new OutputStreamWriter(gzip, "UTF-8");
            config.writeTo(writer);
            writer.flush();
        }
    }

    /**
     * Uloží konfiguraci do nešifrovaného YAML souboru.
     *
     * @param config konfigurace k uložení
     * @param file výstupní soubor (.yml)
     * @throws IOException chyba při zápisu
     */
    public static void savePlain(YamlAppConfig config, File file) throws IOException {
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8")) {
            config.writeTo(writer);
        }
    }
}
