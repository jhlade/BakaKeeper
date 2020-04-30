package cz.zsstudanka.skola.bakakeeper.settings;

import cz.zsstudanka.skola.bakakeeper.components.ReportManager;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLogType;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.security.spec.KeySpec;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Základní nastavení programových přístupů ke zdrojům.
 *
 * @author Jan Hladěna
 */
public class Settings {

    /** singleton - instance nastavení */
    private static Settings instance = null;

    /** vypisovat podrobné informace */
    private boolean beVerbose = false;
    /** vypisovat ladící informace */
    private boolean debugMode = false;

    /** běh programu je v režimu vývoje */
    private boolean develMode = false;

    /** uchovávané heslo k nastavení */
    private String PASSPHRASE = null;

    /** výchozí název souboru s nastavením */
    private final String DEFAULT_CONF_FILE = "./settings.conf";
    /** výchozí název datového souboru s nastavením */
    private final String DEFAULT_DATA_FILE = "./settings.dat";
    /** výchozí název souboru šifrovaného úložiště certifikátů */
    public final String DEFAULT_JKS_FILE = "./ssl.jks";

    /** nastavení jsou platná */
    private Boolean valid;

    /** velikost šifrovacího klíče */
    private final int keySize = 256;
    /** transformace AES/CBC/PKCS5Padding */
    private final String transformation = "AES/CBC/PKCS5Padding";
    /** šifrovací algoritmus AES-256 */
    private final String algorithm = "AES";

    // TODO zde je možné/nutné provést změny
    /** inicializační vektor šifrování */
    private final byte[] IV = { 0x26, 0x33, 0x6a, 0x15,
                                0x10, 0x12, 0x69, 0x7b,
                                0x1e, 0x01, 0x0c, 0x36,
                                0x11, 0x0e, 0x73, 0x3e};
    /** šifrovací klíč */
    private SecretKey key = null;
    /** šifra */
    private Cipher cipher = null;

    /** nastavení ve formátu tabulky */
    private Map<String, String> settings_data;

    /**
     * Nastavení je implementováno jako singleton.
     *
     * @return instance nastavení
     */
    public static Settings getInstance() {
        if (Settings.instance == null) {
            Settings.instance = new Settings();
        }

        return Settings.instance;
    }

    /**
     * Zjištění příznaku validity nastavení.
     *
     * @return příznak validity
     */
    public Boolean isValid() {
        return (this.valid != null) ? this.valid : false;
    }

    /**
     * Změna příznaku validity nastavení.
     *
     * @param validityFlag nová hodnota příznaku
     */
    private void setValidity(Boolean validityFlag) {
        this.valid = validityFlag;
    }

    /**
     * Změna příznaku validity na neplatný.
     */
    private void setInvalid() {
        this.setValidity(false);
    }

    /**
     * Změna příznaku validity na platný.
     */
    private void setValid() {
        this.setValidity(true);
    }

    /**
     * Načtení nastavení z výchozího datového nebo textového souboru. Případná
     * zašifrovaná data budou automaticky dešifrována zadaným klíčem.
     */
    public void load() {

        // ověření existence výchozího datového souboru
        File ddf = new File(this.DEFAULT_DATA_FILE);

        if (ddf.isFile()) {

            if (this.beVerbose) {
                ReportManager.log(EBakaLogType.LOG_VERBOSE, "Výchozí datový soubor byl nalezen.");
            }

            load(this.DEFAULT_DATA_FILE);
            return;
        } else {
            if (this.beVerbose) {
                ReportManager.log(EBakaLogType.LOG_VERBOSE, "Výchozí datový soubor neexistuje.");
            }
        }

        // ověření existence textového souboru
        File dtf = new File(this.DEFAULT_CONF_FILE);
        if (dtf.isFile()) {

            if (this.beVerbose) {
                ReportManager.log(EBakaLogType.LOG_VERBOSE, "Výchozí textový konfigurační soubor existuje.");
                ReportManager.log(EBakaLogType.LOG_VERBOSE, "Textový konfigurační soubor bude převeden na datový.");
            }

            load(this.DEFAULT_CONF_FILE);
            return;
        } else {
            if (this.beVerbose) {
                ReportManager.log(EBakaLogType.LOG_VERBOSE, "Výchozí textový konfigurační soubor neexistuje.");
            }
        }

        ReportManager.log(EBakaLogType.LOG_ERR, "Nebyl nalezen žádný výchozí konfigurační soubor. Proveďte inicializaci spuštětním programu s parametrem --init.");
    }

    /**
     * Načtení konfigurace ze souboru.
     *
     * @param filename jméno souboru s konfigurací (.dat/.conf)
     */
    public void load(String filename) {

        // ověření existence předávaného souboru
        File ccf = new File(filename);
        if (ccf.isFile()) {

            // datový konfigurační soubor
            if (filename.contains(".dat")) {

                Map<String, String> dataFromFile;

                try {

                    if (this.beVerbose) {
                        ReportManager.log(EBakaLogType.LOG_VERBOSE, "Probíhá deserializace datového souboru.");
                    }

                    InputStream dataInputStream;
                    if (this.useEncryption()) {
                        this.cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(IV));
                        dataInputStream = new CipherInputStream(new FileInputStream(filename), this.cipher);
                    } else {
                        dataInputStream = new FileInputStream(filename);
                    }
                    
                    ObjectInputStream inStream = new ObjectInputStream(new GZIPInputStream( dataInputStream ));
                    dataFromFile = (Map<String, String>) inStream.readObject();

                    loadDataSettings(dataFromFile);
                } catch (Exception e) {
                    if (this.debugMode) {
                        // pouze zpráva
                        ReportManager.exceptionMessage(e);
                    }

                    if (this.useEncryption()) {
                        ReportManager.log(EBakaLogType.LOG_ERR, "Došlo k chybě při práci s datovým souborem. Možná nesprávné heslo k šifrovanému nastavení?");
                    } else {
                        ReportManager.log(EBakaLogType.LOG_ERR, "Došlo k chybě při práci s datovým souborem.");
                    }
                }

                // textový konfigurační soubor
            } else if (filename.contains(".conf")) {
                String config = this.readConfigFile(filename);
                this.loadPlainSettings(config.toString());
            }

        } else {
            ReportManager.log(EBakaLogType.LOG_ERR, "Konfigurační soubor nebyl nalezen.");
            return;
        }

        // validace dat
        validate();
    }

    /**
     * Uložení nastavení do výchozího datového souboru. Je-li vybráno šifrování,
     * soubor bude zašifrovanám pomocí specifikovaného klíče.
     */
    public void save() {
        save(this.DEFAULT_DATA_FILE);
    }

    /**
     * Uložení nastavení do souboru. Podle přípony bude vytvořen textový/datový soubor.
     *
     * @param filename jméno výstupního souboru .conf/.dat
     */
    public void save(String filename) {

        File sdf = new File(filename);

        if (filename.contains(".dat")) {

            try {

                OutputStream outputDataStream;
                if (useEncryption()) {
                    this.cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(IV));
                    outputDataStream = new CipherOutputStream(new FileOutputStream(filename), this.cipher);
                } else {
                    outputDataStream = new FileOutputStream(filename);
                }

                ObjectOutputStream outFile = new ObjectOutputStream( new GZIPOutputStream( outputDataStream ));
                outFile.writeObject(this.settings_data);
                outFile.close();

            } catch (Exception e) {
                ReportManager.log(EBakaLogType.LOG_ERR, "Došlo k chybě při serializaci nastavení do souboru " + filename + ".");

                if (this.beVerbose) {
                    ReportManager.exceptionMessage(e);
                }
            }

        } else if (filename.contains(".conf")) {

            try {
                PrintStream outConfig = new PrintStream(new FileOutputStream(filename));
                outConfig.print(this.toString());
                outConfig.close();

            } catch (Exception e) {
                ReportManager.log(EBakaLogType.LOG_ERR, "Došlo k chybě při zápisu nastavení do souboru " + filename + ".");

                if (this.beVerbose) {
                    ReportManager.exceptionMessage(e);
                }

                if (this.debugMode) {
                    ReportManager.printStackTrace(e);
                }
            }
        }

    }

    /**
     * Načtení nastavení z datového objektu.
     *
     * @param data deserializovaná mapa nastavení
     */
    private void loadDataSettings(Map<String, String> data) {
        this.settings_data = data;
    }

    /**
     * Načtení konfiguračních dat z textové formy.
     *
     * @param data textový řetězec ve formátu konfigurace (parametr=hodnota na zvláštních řádcích)
     */
    private void loadPlainSettings(String data) {
        this.settings_data = parsePlainSettings(data);
    }

    /**
     * Vytvoření mapy z dat v textové formě.
     *
     * @param data textový řetězec ve formátu konfigurace (parametr=hodnota na zvláštních řádcích)
     * @return tabulková forma konfiguračních dat
     */
    private Map parsePlainSettings(String data) {

        if (beVerbose) {
            ReportManager.log(EBakaLogType.LOG_VERBOSE, "Začíná zpracování konfiguračních dat.");
        }

        Map parsedData = new LinkedHashMap<String, String>();

        String[] lines = data.replace("\"", "").split("\n");

        for (String line: lines) {
            int separator = line.indexOf("=");

            String[] params = new String[2];
            params[0] = line.substring(0, separator);
            params[1] = line.substring(separator + 1);

            parsedData.put(params[0], params[1]);
        }

        if (beVerbose) {
            ReportManager.log(EBakaLogType.LOG_OK, "Konfigurační data zpracována.");
        }

        return parsedData;
    }

    /**
     * Provede ověření vyplněných údajů a odpovídajícím způsobem nastaví příznak validity.
     */
    private void validate() {
        AtomicReference<Boolean> validity = new AtomicReference<>(true);
        InputStream configIS = getClass().getResourceAsStream("/settings.conf");

        Map refDataPattern = new HashMap<String, String>();

        // 1) načtení referenčního souboru
        refDataPattern = parsePlainSettings(readConfigStream(configIS));

        // 2) porovnání získaných dat s referenčními
        try {
        refDataPattern.forEach((key, value) -> {

            Boolean keyValid = (this.settings_data.containsKey(key)) ? true : false;
            validity.updateAndGet(v -> v & keyValid);

            if (debugMode) {
                ReportManager.log(EBakaLogType.LOG_DEBUG, "Porovnává se klíč " + key + " s načtenými daty... [ " + ((keyValid) ? "OK" : "CHYBA") + " ]");
            }
        });
        } catch (Exception e) {
            if (this.beVerbose) {
                if (useEncryption()) {
                    ReportManager.log(EBakaLogType.LOG_ERR_VERBOSE, "Chyba validace dat. Možná nesprávné heslo k šifrovanému nastavení?");
                } else {
                    ReportManager.log(EBakaLogType.LOG_ERR_VERBOSE, "Chyba validace dat.");
                }
            }

            if (this.debugMode) {
                ReportManager.printStackTrace(e);
            }

            this.setValidity(false);
            return;
        }

        this.setValidity(validity.get());
    }

    /**
     * Načtení textového souboru do formy textového řetězce k pozdějšímu zpracování.
     *
     * @param filename soubor s konfigurací
     * @return konfigurační řetězec
     */
    private String readConfigFile(String filename) {
        File openFile = new File(filename);
        return this.readConfigFile(openFile);
    }

    /**
     * Načtení textového souboru do formy textového řetězce k pozdějšímu zpracování.
     *
     * @param file ukazatel na soubor
     * @return konfigurační řetězec
     */
    private String readConfigFile(File file) {

        InputStream inputStream;

        try {
            inputStream = new FileInputStream(file);
            return readConfigStream(inputStream);
        } catch (Exception e) {
            ReportManager.log(EBakaLogType.LOG_ERR, String.format("Došlo k chybě při čtení souboru '%s'.", file.toPath()));

            if (this.beVerbose) {
                ReportManager.exceptionMessage(e);
            }

            if (this.debugMode) {
                ReportManager.printStackTrace(e);
            }
        }

        return null;
    }

    /**
     * Načtení konfiguračních dat ze vstupního proudu.
     *
     * @param input vstupní proud konfiguračních dat
     * @return konfigurační řetězec
     */
    private String readConfigStream(InputStream input) {

        StringBuilder config = new StringBuilder();

        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(input));

            if (this.beVerbose) {
                ReportManager.log(EBakaLogType.LOG_VERBOSE, "Probíhá načítání konfigurace ze souboru.");
            }

            String line;
            while ((line = reader.readLine()) != null) {

                if (this.debugMode) {
                    if (line.contains("pass=")) {
                        ReportManager.log(EBakaLogType.LOG_DEBUG, "Načítá se: pass=*****");
                    } else {
                        ReportManager.log(EBakaLogType.LOG_DEBUG, "Načítá se: " + line);
                    }
                }

                config.append(line);
                config.append("\n");
            }

            reader.close();
        } catch (Exception e) {
            ReportManager.log(EBakaLogType.LOG_ERR, "Došlo k závažné chybě při zpracování vstupních dat.");

            if (this.beVerbose) {
                ReportManager.exceptionMessage(e);
            }

            if (this.debugMode) {
                ReportManager.printStackTrace(e);
            }
        }

        return config.toString();
    }

    /**
     * Režim interaktivního zadávání konfiguračních dat.
     */
    public void interactivePrompt() {
        // načtení referenčního souboru v daném pořadí
        InputStream configIS = getClass().getResourceAsStream("/settings.conf");
        Map refDataPattern = new LinkedHashMap<String, String>();
        refDataPattern = parsePlainSettings(readConfigStream(configIS));

        // vstupní řádky
        Console console = System.console();
        StringBuilder inputData = new StringBuilder();

        // naplnění daty z dotazů
        refDataPattern.forEach((key, value) -> {

            System.out.println(value);

            String data;
            String defaultData = "";

            // zjednodušeně bez regexpu - výtah obsahu výchozí hodnoty
            if (value.toString().contains("[")) {
                defaultData = value.toString().substring(
                        value.toString().indexOf("[") + 1,
                        value.toString().indexOf("]")
                );
            }

            if (key.toString().contains("pass")) {
                data = new String(console.readPassword(key + " = "));
            } else {
                data = console.readLine(key + " = ");
            }

            // výchozí data
            if (!key.toString().contains("pass") && data.length() == 0) {
                data = defaultData;
            }

            // lokální úpravy
            // používat SSL pro komunikaci
            if (key.toString().equals("ssl")) {
                if (data.toLowerCase().equals("a")
                        || data.toLowerCase().equals("ano")
                        || data.toLowerCase().equals("y")
                        || data.toLowerCase().equals("yes")
                        || data.toLowerCase().equals("1")) {
                    data = "1";
                } else {
                    data = "0";
                }
            }

            /*
            // změna hesla na malá písmena
            if (!key.toString().contains("pass")) {
                data = data.toLowerCase();
            }
            */

            // zápis řádku
            inputData.append(key).append("=").append(data).append("\n");
        });

        // zpracování
        loadPlainSettings(inputData.toString());
        validate();
    }

    /**
     * Okamžitá změna nastavení - určeno pouze pro účely vývoje.
     *
     * @param param parametr nastavení
     * @param value nová hodnota
     */
    public void override(String param, String value) {
        this.settings_data.put(param, value);
    }

    /**
     * Nastavení mají generovat podrobné informace.
     *
     * @return stav příznaku zobrazování podrobných informací
     */
    public Boolean beVerbose() {
        return this.beVerbose;
    }

    /**
     * Nastavení mají generovat ladící informace.
     *
     * @return stav příznaku zobrazování ladících informací
     */
    public Boolean debugMode() {
        return this.debugMode;
    }

    /**
     * Aplikace je v režimu vývoje.
     *
     * @return stav příznaku vývojového režimu
     */
    public Boolean develMode() {
        return this.develMode;
    }

    /**
     * Nastavení příznaku vývojářského režimu.
     *
     * @param develMode příznak vývojářského režimu
     */
    public void setDevelMode(Boolean develMode) {
        this.develMode = develMode;
    }

    /**
     * Hostname nebo IP stroje, kde běží AD/LDAP, například srv-zs-stu-dc01
     *
     * @return String LDAP server
     */
    public String getLDAP_host() {
        if (this.settings_data.get("ad_srv").toLowerCase().contains(this.settings_data.get("domain").toLowerCase())) {
            return (this.settings_data.get("ad_srv").toUpperCase().replace("." + this.getLocalDomain().toUpperCase(), "")).toLowerCase();
        } else {
            return this.settings_data.get("ad_srv").toLowerCase();
        }
    }

    /**
     * Lokální doména AD,
     * například zsstu.local
     *
     * @return String LDAP doména
     */
    public String getLDAP_domain() {
        return this.settings_data.get("domain");
    }

    /**
     * Plně kvalifikované jméno LDAP serveru, například srv-zs-stu-dc01.zsstu.local
     *
     * @return String LDAP server FQDN
     */
    public String getLDAP_fqdn() {
        return getLDAP_host() + "." + getLDAP_domain();
    }

    /**
     * Základní řetězec organizační struktury uživatelů, například
     * OU=Uzivatele,OU=Skola,DC=zsstu,DC=local
     *
     * @return String základní LDAP řetězec
     */
    public String getLDAP_base() {
        return this.settings_data.get("ad_base");
    }

    /**
     * Základní řetězec organizační struktury pro žáky, například
     * OU=Zaci,OU=Uzivatele,OU=Skola,DC=zsstu,DC=local
     *
     * @return String základní LDAP řetězec pro žáky
     */
    public String getLDAP_baseStudents() {
        return this.settings_data.get("ad_base_zaci");
    }

    /**
     * Základní řetězec organizační struktury pro zaměstnance, například
     * OU=Zamestnanci,OU=Uzivatele,OU=Skola,DC=zsstu,DC=local
     *
     * @return String základní LDAP řetězec pro zaměstnance
     */
    public String getLDAP_baseFaculty() {
        return this.settings_data.get("ad_base_zamestnanci");
    }

    /**
     * Základní řetězec organizační struktury pro vyřazené žáky, například
     * OU=StudiumUkonceno,OU=Zaci,OU=Uzivatele,OU=Skola,DC=zsstu,DC=local
     *
     * @return
     */
    public String getLDAP_baseAlumni() {
        return this.settings_data.get("ad_base_absolventi");
    }

    /**
     * Základní řetězec organizační struktury pro karty globálních kontaktů, například
     * OU=Kontakty,OU=Skola,DC=zsstu,DC=local
     *
     * @return
     */
    public String getLDAP_baseContacts() {
        return this.settings_data.get("ad_base_kontakty");
    }

    /**
     * TODO distribuční skupiny
     *
     * @return
     */
    public String getLDAP_baseDL() {
        return this.settings_data.get("ad_base_skupiny_dl");
    }

    /**
     * TODO
     *
     * @return
     */
    public String getLDAP_baseDLContacts() {
        return "OU=DistribucniSeznamyZZ," + this.settings_data.get("ad_base_kontakty");
    }

    /**
     * Základní řetězec organizační struktury pro skupiny žáků, například
     * OU=Zaci,OU=Skupiny,OU=Skola,DC=zsstu,DC=local
     *
     * @return
     */
    public String getLDAP_baseStudentGroups() {
        return this.settings_data.get("ad_base_skupiny_zaci");
    }

    /**
     * Základní řetězec organizační struktury pro skupiny žáků, například
     * OU=Skupiny,OU=Skola,DC=zsstu,DC=local
     *
     * @return
     */
    public String getLDAP_baseGlobalGroups() {
        return this.settings_data.get("ad_base_skupiny_global");
    }

    /**
     * Externí (e-mailová) doména školy.
     *
     * @return e-mailová doména školy
     */
    public String getMailDomain() {
        return this.settings_data.get("mail_domain");
    }

    /**
     * Otevřená podoba uživatelského hesla doménového správce AD.
     *
     * @return heslo správce AD
     */
    public String getPass() {
        return this.settings_data.get("pass");
    }

    /**
     * Uživatelský účet správce domény.
     *
     * @return uživatelské jméno správce AD
     */
    public String getUser() {
        return this.settings_data.get("user");
    }

    /**
     * Příznak použití SSL při komunikaci s AD protokolem LDAP.
     *
     * @return příznak použití SSL
     */
    public Boolean useSSL() {
        return (this.settings_data.get("ssl").equals("1") ? true : false);
    }

    /**
     * Globální nastavení hesla pro šifrování a dešifrování konfigurace.
     *
     * @param passphrase heslo pro konfiguraci
     */
    public void setPassphrase(String passphrase) {
        this.PASSPHRASE = passphrase;

        try {

            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            KeySpec spec = new PBEKeySpec(this.PASSPHRASE.toCharArray(), Version.getInstance().getName().getBytes(), 65536, this.keySize);
            SecretKey tmp = factory.generateSecret(spec);
            key = new SecretKeySpec(tmp.getEncoded(), algorithm);

            cipher = Cipher.getInstance(transformation);

        } catch (Exception e) {
            ReportManager.log(EBakaLogType.LOG_ERR, "Inicializace šifrovacího mechanismu se nezdařila.");
            this.PASSPHRASE = null;
        }
    }

    /**
     * Zjištění stavu příznaku použití šifrování.
     *
     * @return stav příznaku použití šifrování
     */
    private Boolean useEncryption() {

        if (this.PASSPHRASE == null) return false;
        if (this.PASSPHRASE.length() > 0) return true;

        return false;
    }

    /**
     * Konverze uchovávaných nastavení do čisté textové podoby.
     *
     * @return odřádkovný výpis nastavení
     */
    @Override
    public String toString() {

        StringBuilder config = new StringBuilder();

        if (this.settings_data != null) {
            this.settings_data.forEach((key, value) -> config.append(key + "=" + value + "\n"));
        }

        return config.toString();
    }

    /**
     * Nastavení výpisu podrobných informací.
     *
     * @param verboseMode nový stav příznaku výpisu podrobných informací
     */
    public void verbosity(Boolean verboseMode) {
        this.beVerbose = verboseMode;
    }

    /**
     * Nastavení výpisu ladících informací.
     *
     * @param debugMode nový stav příznaku výpisu ladících informací
     */
    public void debug(Boolean debugMode) {
        this.debugMode = debugMode;
    }

    /**
     * Adresa SMTP serveru.
     *
     * @return IP nebo název SMTP serveru
     */
    public String getSMTP_host() {
        return this.settings_data.get("smtp_host");
    }

    /**
     * Nastavení SMTP uživatele. Předpokládá se použití lokálního Exchange nebo Office 365.
     * Uživatelem SMTP je globálně definovaná služba pro správu AD.
     *
     * @return celé UPN SMTP uživatele
     */
    public String getSMTP_user() {
        StringBuilder smtpUsername = new StringBuilder();

        smtpUsername.append(this.settings_data.get("user"));
        smtpUsername.append("@");
        smtpUsername.append(this.settings_data.get("mail_domain"));

        return smtpUsername.toString().toLowerCase();
    }

    /**
     * Heslo uživatele SMTP serveru. Předpokládá se použití lokálního Exchange nebo Office 365.
     * Uživatelem SMTP je globálně definovaná služba pro správu AD.
     *
     * @return heslo SMTP uživatele
     */
    public String getSMTP_pass() {
        return this.settings_data.get("pass");
    }

    /**
     * E-mailová adresa správce ICT.
     *
     * @return e-mail správce ICT
     */
    public String getAdminMail() {
        return this.settings_data.get("admin");
    }

    /**
     * Jméno SQL Serveru.
     *
     * @return adresa SQL Serveru
     */
    public String getSQL_host() {

        if (this.settings_data.get("sql_host").toLowerCase().contains(this.settings_data.get("domain").toLowerCase())) {
            return (this.settings_data.get("sql_host").toUpperCase().replace("." + this.getLocalDomain().toUpperCase(), "")).toLowerCase();
        } else {
            return this.settings_data.get("sql_host").toLowerCase();
        }
    }


    /**
     * Plné kvalifikované jméno SQL Serveru.
     *
     * @return SQL server FQDN
     */
    public String getSQL_hostFQDN() {
        return (getSQL_host().toLowerCase() + "." + getLocalDomain().toUpperCase());
    }

    /**
     * Jméno databáze s daty aplikace Bakaláři.
     *
     * @return databáze
     */
    public String getSQL_database() {
        return this.settings_data.get("sql_data");
    }

    /**
     * Krb5 formát jména
     *
     * @return username@REALM.LOCAL
     */
    public String getKrb_user() {
        StringBuilder krbUser = new StringBuilder();

        krbUser.append(this.settings_data.get("user").toLowerCase());
        krbUser.append("@");
        krbUser.append(this.settings_data.get("domain").toUpperCase());

        return krbUser.toString();
    }

    /**
     * Lokální doména AD
     *
     * @return řetězec domény
     */
    public String getLocalDomain() {
        return this.settings_data.get("domain");
    }

    /**
     * Metoda spojení se SQL Serverem
     *
     * @return String ntlm|kerberos
     */
    public String getSQLConnectionMethod() {
        return this.settings_data.get("sql_con").toLowerCase();
    }

    /**
     * SQL spojení je konfigurováno pro použití NTLMv2 ověřování.
     *
     * @return
     */
    public Boolean sql_NTLM() {
        return (getSQLConnectionMethod().equals("ntlm")) ? true : false;
    }

    /**
     * SQL spojení je konfigurováno pro použití protokolu Kerberos V k ověřování a delegování identity.
     *
     * @return
     */
    public Boolean sql_Kerberos() {
        return (getSQLConnectionMethod().equals("kerberos")) ? true : false;
    }

    /**
     * Informační značka o aktuálním zpracování požadavku.
     *
     * @return značka ve tvaru YYYY-MM-DD HH:mmm:ss @ hostname
     */
    public String systemInfoTag() {
        String hostname;
        String os;

        try {
             hostname = new BufferedReader(
                    new InputStreamReader(Runtime.getRuntime().exec("hostname").getInputStream()))
                    .readLine();
        } catch (IOException e) {
            hostname = "[unknown]";
        }

        try {
            os = System.getProperty("os.name") + " " + System.getProperty("os.version");
        } catch (Exception e) {
            os = "[unknown]";
        }

        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date date = new Date();

        return formatter.format(date) + " @ " + hostname + " (" + os + ")";
    }
}
