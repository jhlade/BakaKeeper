package cz.zsstudanka.skola.bakakeeper.model.entities;

import cz.zsstudanka.skola.bakakeeper.connectors.BakaADAuthenticator;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLDAPAttributes;
import cz.zsstudanka.skola.bakakeeper.model.interfaces.IUzivatelAD;
import cz.zsstudanka.skola.bakakeeper.settings.Settings;

import java.util.ArrayList;
import java.util.Map;

/**
 * Model zaměstnance - má prioritu nad žáky.
 * Pouze ke čtení.
 *
 * Zaměstnanec je objekt uživatel Active Directory.
 *
 * @author Jan Hladěna
 * @deprecated
 */
public class Zamestnanec implements IUzivatelAD {

    /** lokální login zaměstnance ve formátu 4+2 */
    private String ad_login;
    /** primární e-mailová adresa serveru Exchange - primární klíč pro porovnávání */
    private String ad_mail;

    /** zobrazované uživatelské jméno */
    private String displayName;

    /** ladící zprávy */
    private ArrayList<String> debugMessages;

    /**
     * Konstrukce jednoho zaměstnance podle loginu.
     *
     * @param login samAccountName zaměstnance
     */
    public Zamestnanec(String login) {

        if (BakaADAuthenticator.getInstance().isAuthenticated()) {

            Map getData = null;//BakaADAuthenticator.getInstance().getUserInfo(login, new String[]{"sAMAccountName", "mail", "displayName"});

            this.ad_login = getData.get("sAMAccountName").toString();
            this.ad_mail = getData.get("mail").toString();
            this.displayName = getData.get("displayName").toString();

        }
    }

    /**
     * Kompletní konstruktor.
     *
     * @param ad_login login v Active Directory
     * @param ad_mail e-mail v serveru Exchange
     * @param displayName zobrazované jméno
     */
    public Zamestnanec(String ad_login, String ad_mail, String displayName) {
        this.ad_login = ad_login;
        this.ad_mail = (ad_mail == null) ? "NULL" : ad_mail;
        this.displayName = displayName;

        this.debugMessages = new ArrayList<>();
    }

    public String getADLogin() {
        return this.ad_login;
    }

    public String getADEmail() {
        return this.ad_mail;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    @Override
    public ArrayList<String> getDebugMessages() {
        return this.debugMessages;
    }

    @Override
    public String toString() {

        StringBuilder info = new StringBuilder();

        info.append("Zaměstnanec:\t").append(this.getDisplayName())
        .append("\nLogin:\t\t").append(this.getADLogin())
        .append("\nE-mail:\t\t").append(this.getADEmail());

        if (Settings.getInstance().debugMode() && this.getDebugMessages().size() > 0) {
            info.append("\n[ POZNÁMKY ]\n");
            for (int m = 0; m < this.getDebugMessages().size(); m++) {
                info.append("[").append(m+1).append("] ").append(this.getDebugMessages().get(m)).append("\n");
            }
        }

        return info.toString();
    }

    /**
     * Konstruktor zaměstnance z AD loginu.
     *
     * @param ad_login login zaměstnance
     * @return instance zaměstnance
     */
    public static Zamestnanec createFromLogin(String ad_login) {

        String[] info = {
                EBakaLDAPAttributes.LOGIN.attribute(),
                EBakaLDAPAttributes.MAIL.attribute(),
                EBakaLDAPAttributes.NAME_DISPLAY.attribute()
        };

        Map<String, Object> user = null;//BakaADAuthenticator.getInstance().getUserInfo(ad_login, info);

        if (user != null && user.size() > 0) {
            return new Zamestnanec(
                user.get(EBakaLDAPAttributes.LOGIN.attribute()).toString(),
                user.get(EBakaLDAPAttributes.MAIL.attribute()).toString(),
                user.get(EBakaLDAPAttributes.NAME_DISPLAY.attribute()).toString()
            );
        }

        return null;
    }

}
