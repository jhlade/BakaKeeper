package cz.zsstudanka.skola.bakakeeper.connectors;

import cz.zsstudanka.skola.bakakeeper.components.ReportManager;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLogType;
import cz.zsstudanka.skola.bakakeeper.settings.Settings;
import cz.zsstudanka.skola.bakakeeper.utils.BakaUtils;

import javax.naming.ldap.LdapContext;

/**
 * Pomocná třída pro přesuny a přejmenování LDAP objektů.
 * Extrahováno z BakaADAuthenticator – renameObject a moveObject.
 *
 * @author Jan Hladěna
 */
class LdapObjectMover {

    /** maximální limit pro přejmenování objektu během přesunu */
    private final int MOVE_LIMIT = 99;

    /** továrna na LDAP spojení */
    private final LdapConnectionFactory connectionFactory;

    /** dotazovací engine pro ověřování DN/OU */
    private final LdapQueryEngine queryEngine;

    /** továrna objektů pro vytváření OU */
    private final LdapObjectFactory objectFactory;

    /**
     * Konstruktor.
     *
     * @param connectionFactory továrna na LDAP spojení
     * @param queryEngine dotazovací engine
     * @param objectFactory továrna objektů (pro createOU)
     */
    LdapObjectMover(LdapConnectionFactory connectionFactory, LdapQueryEngine queryEngine, LdapObjectFactory objectFactory) {
        this.connectionFactory = connectionFactory;
        this.queryEngine = queryEngine;
        this.objectFactory = objectFactory;
    }

    /**
     * Přejmenování objektu (změna CN) ve stejné OU.
     * Používá se při změně jména žáka – CN se změní na nové příjmení + jméno,
     * objekt zůstane ve stejné OU. Při kolizi názvu se generuje číselný sufix.
     *
     * @param objectDN plné DN objektu
     * @param newCn nový Common Name (např. "Nováková Jana")
     * @return nové DN po přejmenování, nebo null při chybě
     */
    public String renameObject(String objectDN, String newCn) {
        String currentOU = BakaUtils.parseBase(objectDN);
        String newObjectDN = "CN=" + newCn + "," + currentOU;

        // řešení kolizí přejmenováním (stejný postup jako moveObject)
        int attempt = 0;
        boolean dnOccupied;

        do {
            dnOccupied = queryEngine.checkDN(newObjectDN);
            attempt++;

            if (dnOccupied) {
                if (Settings.getInstance().isVerbose()) {
                    ReportManager.log(EBakaLogType.LOG_LDAP,
                            "Cílové DN [" + newObjectDN + "] již existuje, generuji nový název.");
                }
                newObjectDN = BakaUtils.nextDN(newObjectDN);
            }
        } while (dnOccupied && attempt <= MOVE_LIMIT);

        if (attempt >= MOVE_LIMIT) {
            ReportManager.log(EBakaLogType.LOG_ERR,
                    "Překročen limit pokusů o přejmenování objektu [" + objectDN + "].");
            return null;
        }

        // provedení přejmenování – try-finally pro správné uzavření
        LdapContext ctxRN = null;
        try {
            ctxRN = connectionFactory.createContext();
            ctxRN.rename(objectDN, newObjectDN);
        } catch (Exception e) {
            ReportManager.handleException(
                    "Nebylo možné přejmenovat objekt [" + objectDN + "] na [" + newObjectDN + "].", e);
            return null;
        } finally {
            if (ctxRN != null) { try { ctxRN.close(); } catch (Exception ignored) {} }
        }

        // ověřit existenci nového DN
        if (queryEngine.checkDN(newObjectDN)) {
            if (Settings.getInstance().isVerbose()) {
                ReportManager.log(EBakaLogType.LOG_LDAP,
                        "Objekt přejmenován: [" + objectDN + "] → [" + newObjectDN + "].");
            }
            return newObjectDN;
        }

        return null;
    }

    /**
     * Přesun objektu do požadované OU.
     *
     * @param objectDN plné DN objektu
     * @param ouName cílová OU
     * @return úspěch operace
     */
    public Boolean moveObject(String objectDN, String ouName) {
        return moveObject(objectDN, ouName, false, false);
    }

    /**
     * Přesun objektu do požadované OU. Pokud cílová OU neexistuje a je
     * nastaveno {@code createNewOUifNotExists}, bude vytvořena.
     *
     * @param objectDN plné DN objektu
     * @param ouName cílová OU
     * @param createNewOUifNotExists vytvořit novou OU, pokud neexistuje
     * @return úspěch operace
     */
    public Boolean moveObject(String objectDN, String ouName, Boolean createNewOUifNotExists) {
        return moveObject(objectDN, ouName, createNewOUifNotExists, false);
    }

    /**
     * Přesun objektu do požadované organizační jednotky.
     * Pokud je nastaveno {@code createNewOUifNotExists} a cílová OU neexistuje,
     * bude vytvořena.
     * Pokud je nastaveno {@code renameObject}, objekt bude v případě neúspěchu
     * přejmenován.
     *
     * @param objectDN plné DN objektu
     * @param ouName plná cesta cílové OU
     * @param createNewOUifNotExists vytvořit cílovou OU, pokud neexistuje
     * @param renameObject přejmenovat přesunovaný objekt, pokud v cíli již jiný objekt s požadovaným názvem existuje
     * @return úspěch operace
     */
    public Boolean moveObject(String objectDN, String ouName, Boolean createNewOUifNotExists, Boolean renameObject) {
        // 1. Zajistit existenci cílové OU – při createNewOUifNotExists vytvořit PŘED
        //    jakýmikoli checkDN voláními, aby se zamezilo LDAP error 32 při hledání
        //    v dosud neexistující OU.
        if (queryEngine.checkOU(ouName) == -1) {
            if (createNewOUifNotExists) {
                // vlastní název
                String cn = BakaUtils.parseLastOU(ouName);
                // cílová cesta
                String base = BakaUtils.parseBase(ouName);
                // vytvoření
                objectFactory.createOU(cn, base);

                if (Settings.getInstance().isVerbose()) {
                    ReportManager.log(EBakaLogType.LOG_LDAP,
                            "Vytvořena cílová OU [" + ouName + "].");
                }
            } else {
                if (Settings.getInstance().isVerbose()) {
                    ReportManager.log(EBakaLogType.LOG_ERR,
                            "Cílová organizační jednotka pro přesun objektu neexistuje.");
                }

                if (Settings.getInstance().isDebug()) {
                    ReportManager.log(EBakaLogType.LOG_ERR_DEBUG,
                            "Nebylo možné přesunout objekt [" + objectDN + "] do umístění [" + ouName + "].");
                }

                return false;
            }
        }

        // 2. Nový název objektu
        String objCN = BakaUtils.parseCN(objectDN);
        String newObjectDN = "CN=" + objCN + "," + ouName;

        // 3. Prvotní kontrola existence cílového objektu (kolize)
        if (queryEngine.checkDN(newObjectDN) && !renameObject) {
            if (Settings.getInstance().isVerbose()) {
                ReportManager.log(EBakaLogType.LOG_ERR, "Cílový název objektu již existuje, přesun nebude proveden.");
            }

            if (Settings.getInstance().isDebug()) {
                ReportManager.log(EBakaLogType.LOG_ERR_DEBUG, "Nebylo možné přesunout objekt [" + objectDN + "] do umístění [" + ouName + "].");
            }

            return false;
        }

        // 4. Řešení kolizí přejmenováním
        int moveAttempt = 0;
        Boolean dnOccupied;

        do {
            dnOccupied = queryEngine.checkDN(newObjectDN);
            moveAttempt ++;

            if (dnOccupied) {

                if (Settings.getInstance().isVerbose()) {
                    ReportManager.log("Název přesunovaného objektu v cíli již exituje, bude vygenerován nový.");
                }

                newObjectDN = BakaUtils.nextDN(newObjectDN);

                if (Settings.getInstance().isDebug()) {
                    ReportManager.log(EBakaLogType.LOG_LDAP, "Byl vygenerován nový název [" + newObjectDN + "].");
                }
            }

        } while (dnOccupied && moveAttempt <= MOVE_LIMIT);

        if (moveAttempt >= MOVE_LIMIT) {
            ReportManager.log(EBakaLogType.LOG_ERR, "Byl překročen maximální limit pro přejmenování LDAP objektu.");
        }

        // 5. Provedení přesunu – try-finally pro správné uzavření
        LdapContext ctxOM = null;
        try {
            ctxOM = connectionFactory.createContext();
            ctxOM.rename(objectDN, newObjectDN);
        } catch (Exception e) {
            ReportManager.handleException("Nebylo možné přesunout objekt.", e);
        } finally {
            if (ctxOM != null) { try { ctxOM.close(); } catch (Exception ignored) {} }
        }

        // kontrola existence objektu po přesunutí
        return queryEngine.checkDN(newObjectDN);
    }
}
