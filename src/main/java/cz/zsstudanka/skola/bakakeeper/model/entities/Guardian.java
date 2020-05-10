package cz.zsstudanka.skola.bakakeeper.model.entities;

import cz.zsstudanka.skola.bakakeeper.components.ReportManager;
import cz.zsstudanka.skola.bakakeeper.connectors.BakaADAuthenticator;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLDAPAttributes;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLogType;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaSQL;
import cz.zsstudanka.skola.bakakeeper.model.interfaces.IRecordLDAP;
import cz.zsstudanka.skola.bakakeeper.model.interfaces.IRecordSQL;
import cz.zsstudanka.skola.bakakeeper.settings.Settings;
import cz.zsstudanka.skola.bakakeeper.utils.BakaUtils;

import java.util.ArrayList;

/**
 * Zákonný zástupce. Je reprezentován SQL záznamem jako primární zákonný zástupce
 * a jako kontakt v LDAP.
 *
 * implements IRecordLDAP, IRecordSQL
 *
 * @author Jan Hladěna
 */
public class Guardian implements IRecordLDAP, IRecordSQL {

    /** maximální počet pokusů o vytvoření nového účtu */
    private static final int MAX_LIMIT = 10;

    /** parciálně vytvořený záznam */
    private Boolean partial = false;

    private DataLDAP dataLDAP;
    private DataSQL dataSQL;

    /**
     * Konstruktor kontaktu na základě SQL a LDAP dat.
     *
     * @param dataSQL data získaná z evidence
     * @param dataLDAP data získaná z AD
     */
    protected Guardian(DataSQL dataSQL, DataLDAP dataLDAP) {

        if (dataSQL == null || dataLDAP == null) {
            this.partial = true;
        }

        this.dataSQL = dataSQL;
        this.dataLDAP = dataLDAP;
    }

    /**
     * Validace dat.
     * TODO
     *
     * @return data zákonného zástupce jsou platná
     */
    public Boolean validateData() {

        if (dataSQL == null) {
            return false;
        }

        Boolean isValid = true;

        isValid &= !dataSQL.get(EBakaSQL.F_GUA_BK_SURNAME.basename()).equals(EBakaSQL.NULL.basename());
        isValid &= !dataSQL.get(EBakaSQL.F_GUA_BK_GIVENNAME.basename()).equals(EBakaSQL.NULL.basename());
        isValid &= !dataSQL.get(EBakaSQL.F_GUA_BK_SURNAME.basename()).contains(",");
        isValid &= !dataSQL.get(EBakaSQL.F_GUA_BK_GIVENNAME.basename()).contains(",");
        isValid &= !dataSQL.get(EBakaSQL.F_GUA_BK_SURNAME.basename()).contains(".");
        isValid &= !dataSQL.get(EBakaSQL.F_GUA_BK_GIVENNAME.basename()).contains(".");
        isValid &= BakaUtils.mailIsValid(dataSQL.get(EBakaSQL.F_GUA_BK_MAIL.basename()));

        return isValid;
    }

    /**
     * TODO
     */
    protected void initializeContact() {

        // pouze parciální data
        if (!this.partial || this.dataLDAP != null) {
            if (Settings.getInstance().beVerbose()) {
                ReportManager.log(EBakaLogType.LOG_ERR_VERBOSE, "Inicializace nového kontaktu je možná pouze z parciálních SQL dat.");
            }

            if (Settings.getInstance().debugMode()) {
                ReportManager.log(EBakaLogType.LOG_ERR_DEBUG, "Bylo předáno " + this.dataLDAP.size() + " atributů.");
            }

            return;
        }

        // ověření unikátního DN + smyčka na generování nového DN kontaktu
        int dnAttempt = 0;
        Boolean dnOccupied;

        // počáteční název
        String cn = this.dataSQL.get(EBakaSQL.F_GUA_BK_SURNAME.basename()) + " " + this.dataSQL.get(EBakaSQL.F_GUA_BK_GIVENNAME.basename());
        String dn = "CN=" + cn + "," + Settings.getInstance().getLDAP_baseContacts();

        do {
            dnAttempt++;

            if (Settings.getInstance().debugMode()) {
                ReportManager.log(EBakaLogType.LOG_LDAP, "Pokus č. " + dnAttempt + ": navrhuje se nové DN [" + dn + "].");
            }

            dnOccupied = BakaADAuthenticator.getInstance().checkDN(dn);

            if (dnOccupied) {
                if (Settings.getInstance().debugMode()) {
                    ReportManager.log(EBakaLogType.LOG_LDAP, "Název je obsazen. Bude vygenerován nový.");
                }

                dn = BakaUtils.nextDN(dn);
            }

            cn = BakaUtils.parseCN(dn);

        } while (dnOccupied && dnAttempt <= MAX_LIMIT);

        // překročení limitu
        if (dnAttempt >= MAX_LIMIT) {
            ReportManager.log(EBakaLogType.LOG_ERR, "Došlo k závažné chybě - byl překročen maximální limit návrhů nového unikátního jména v adresáři.");
        }

        // data kontaktu
        DataLDAP newData = new DataLDAP();
        newData.put(EBakaLDAPAttributes.CN.attribute(), cn);
        newData.put(EBakaLDAPAttributes.NAME_DISPLAY.attribute(), this.dataSQL.get(EBakaSQL.F_GUA_BK_SURNAME.basename()) + " " + this.dataSQL.get(EBakaSQL.F_GUA_BK_GIVENNAME.basename()));
        newData.put(EBakaLDAPAttributes.NAME_LAST.attribute(), this.dataSQL.get(EBakaSQL.F_GUA_BK_SURNAME.basename()));
        newData.put(EBakaLDAPAttributes.NAME_FIRST.attribute(), this.dataSQL.get(EBakaSQL.F_GUA_BK_GIVENNAME.basename()));
        newData.put(EBakaLDAPAttributes.EXT01.attribute(), this.dataSQL.get(EBakaSQL.F_GUA_BK_ID.basename()));

        // e-mail
        if (BakaUtils.validateEmail(this.dataSQL.get(EBakaSQL.F_GUA_BK_MAIL.basename())).length() > 0) {
            newData.put(EBakaLDAPAttributes.MAIL.attribute(), BakaUtils.validateEmail(this.dataSQL.get(EBakaSQL.F_GUA_BK_MAIL.basename())));
        }

        // telefon
        if (BakaUtils.validatePhone(this.dataSQL.get(EBakaSQL.F_GUA_BK_MOBILE.basename())).length() > 0) {
            newData.put(EBakaLDAPAttributes.MOBILE.attribute(), BakaUtils.validatePhone(this.dataSQL.get(EBakaSQL.F_GUA_BK_MOBILE.basename())));
        }

        // autorizace + skyrtí v GAL
        newData.put(EBakaLDAPAttributes.MSXCH_REQ_AUTH.attribute(), EBakaLDAPAttributes.BK_LITERAL_TRUE.value());
        newData.put(EBakaLDAPAttributes.MSXCH_GAL_HIDDEN.attribute(), EBakaLDAPAttributes.BK_LITERAL_TRUE.value());

        BakaADAuthenticator.getInstance().createNewContact(cn, newData);

        this.dataLDAP = newData;
    }

    /**
     * Smazání kontaktu z adresáře.
     *
     * @return úspěch oeprace
     */
    public Boolean deleteContact() {
        return BakaADAuthenticator.getInstance().deleteContact(this.getDN());
    }

    /**
     * Synchronizace údajů z evidence do LDAP.
     *
     * @return úspěch operace
     */
    public Boolean sync(Boolean repair) {

        if (partial) {
            if (Settings.getInstance().debugMode()) {
                ReportManager.log(EBakaLogType.LOG_ERR_DEBUG, "Parciální účet zákonného zástupce není možné kontrolovat.");
            }

            return null;
        }

        Boolean result = true;

        if (Settings.getInstance().beVerbose()) {
            ReportManager.log(EBakaLogType.LOG_INFO, "Probíhá kontrola údajů zákonného zástupce žáka ("+ getSQLdata(EBakaSQL.F_STU_CLASS) +") "+ getSQLdata(EBakaSQL.F_STU_SURNAME) + " " + getSQLdata(EBakaSQL.F_STU_GIVENNAME) + ".");
        }

        // příjmení
        if (Settings.getInstance().beVerbose()) {
            ReportManager.logWait(EBakaLogType.LOG_TEST, "Příjmení zákonného zástupce");
        }

        if (getLDAPdata(EBakaLDAPAttributes.NAME_LAST).equals(getSQLdata(EBakaSQL.F_GUA_BK_SURNAME))) {
            if (Settings.getInstance().beVerbose()) {
                ReportManager.logResult(EBakaLogType.LOG_OK);
            }
        } else {

            if (Settings.getInstance().beVerbose()) {
                ReportManager.logResult(EBakaLogType.LOG_ERR_VERBOSE);
            }

            if (Settings.getInstance().debugMode()) {
                ReportManager.log(EBakaLogType.LOG_ERR_DEBUG, "Očekáváno [" + getSQLdata(EBakaSQL.F_GUA_BK_SURNAME) + "], získaná hodnota [" + getLDAPdata(EBakaLDAPAttributes.NAME_LAST) + "].");
            }

            if (repair) {

                if (Settings.getInstance().beVerbose()) {
                    ReportManager.log(EBakaLogType.LOG_INFO, "Proběhne pokus o opravu příjmení.");
                }

                result &= setLDAPdata(EBakaLDAPAttributes.NAME_LAST, getSQLdata(EBakaSQL.F_GUA_BK_SURNAME));
                result &= setLDAPdata(EBakaLDAPAttributes.NAME_DISPLAY, getSQLdata(EBakaSQL.F_GUA_BK_SURNAME) + " " + getSQLdata(EBakaSQL.F_GUA_BK_GIVENNAME));
            } else {
                result = false;
            }
        }

        // jméno
        if (Settings.getInstance().beVerbose()) {
            ReportManager.logWait(EBakaLogType.LOG_TEST, "Jméno zákonného zástupce");
        }
        if (getLDAPdata(EBakaLDAPAttributes.NAME_FIRST).equals(getSQLdata(EBakaSQL.F_GUA_BK_GIVENNAME))) {

            if (Settings.getInstance().beVerbose()) {
                ReportManager.logResult(EBakaLogType.LOG_OK);
            }

        } else {

            if (Settings.getInstance().beVerbose()) {
                ReportManager.logResult(EBakaLogType.LOG_ERR_VERBOSE);
            }

            if (Settings.getInstance().debugMode()) {
                ReportManager.log(EBakaLogType.LOG_ERR_DEBUG, "Očekáváno [" + getSQLdata(EBakaSQL.F_GUA_BK_GIVENNAME) + "], získaná hodnota [" + getLDAPdata(EBakaLDAPAttributes.NAME_FIRST) + "].");
            }

            if (repair) {

                if (Settings.getInstance().beVerbose()) {
                    ReportManager.log(EBakaLogType.LOG_INFO, "Proběhne pokus o opravu jména.");
                }

                result &= setLDAPdata(EBakaLDAPAttributes.NAME_FIRST, getSQLdata(EBakaSQL.F_GUA_BK_GIVENNAME));
                result &= setLDAPdata(EBakaLDAPAttributes.NAME_DISPLAY, getSQLdata(EBakaSQL.F_GUA_BK_SURNAME) + " " + getSQLdata(EBakaSQL.F_GUA_BK_GIVENNAME));
            } else {
                result = false;
            }
        }

        // e-mail
        if (getSQLdata(EBakaSQL.F_GUA_BK_MAIL) != null) {
            if (Settings.getInstance().beVerbose()) {
                ReportManager.logWait(EBakaLogType.LOG_TEST, "E-mail zákonného zástupce");
            }
            if (getLDAPdata(EBakaLDAPAttributes.MAIL) != null && getLDAPdata(EBakaLDAPAttributes.MAIL).equals(BakaUtils.validateEmail(getSQLdata(EBakaSQL.F_GUA_BK_MAIL)))) {

                if (Settings.getInstance().beVerbose()) {
                    ReportManager.logResult(EBakaLogType.LOG_OK);
                }

            } else {

                if (Settings.getInstance().beVerbose()) {
                    ReportManager.logResult(EBakaLogType.LOG_ERR_VERBOSE);
                }

                if (Settings.getInstance().debugMode()) {
                    ReportManager.log(EBakaLogType.LOG_ERR_DEBUG, "Očekáváno [" + BakaUtils.validateEmail(getSQLdata(EBakaSQL.F_GUA_BK_MAIL)) + "], získaná hodnota [" + getLDAPdata(EBakaLDAPAttributes.MAIL) + "].");
                }

                if (repair) {

                    if (Settings.getInstance().beVerbose()) {
                        ReportManager.log(EBakaLogType.LOG_INFO, "Proběhne pokus o opravu e-mailu.");
                    }

                    result &= setLDAPdata(EBakaLDAPAttributes.MAIL, BakaUtils.validateEmail(getSQLdata(EBakaSQL.F_GUA_BK_MAIL)));
                } else {
                    result = false;
                }
            }

            // mail v doméně školy
            if (BakaUtils.validateEmail(getSQLdata(EBakaSQL.F_GUA_BK_MAIL)).contains(Settings.getInstance().getMailDomain())) {
                if (repair) {
                    result &= setLDAPdata(EBakaLDAPAttributes.MSXCH_REQ_AUTH, EBakaLDAPAttributes.BK_LITERAL_FALSE.value());
                    result &= setLDAPdata(EBakaLDAPAttributes.MSXCH_GAL_HIDDEN, EBakaLDAPAttributes.BK_LITERAL_FALSE.value());
                }
            }
        }

        // telefon
        if (getSQLdata(EBakaSQL.F_GUA_BK_MOBILE) != null) {
            if (Settings.getInstance().beVerbose()) {
                ReportManager.logWait(EBakaLogType.LOG_TEST, "Telefon zákonného zástupce");
            }
            if (getLDAPdata(EBakaLDAPAttributes.MOBILE) != null && getLDAPdata(EBakaLDAPAttributes.MOBILE).equals(BakaUtils.validatePhone(getSQLdata(EBakaSQL.F_GUA_BK_MOBILE)))) {

                if (Settings.getInstance().beVerbose()) {
                    ReportManager.logResult(EBakaLogType.LOG_OK);
                }

            } else {

                if (Settings.getInstance().beVerbose()) {
                    ReportManager.logResult(EBakaLogType.LOG_ERR_VERBOSE);
                }

                if (Settings.getInstance().debugMode()) {
                    ReportManager.log(EBakaLogType.LOG_ERR_DEBUG, "Očekáváno [" + BakaUtils.validatePhone(getSQLdata(EBakaSQL.F_GUA_BK_MOBILE)) + "], získaná hodnota [" + getLDAPdata(EBakaLDAPAttributes.MOBILE) + "].");
                }

                if (repair) {

                    if (Settings.getInstance().beVerbose()) {
                        ReportManager.log(EBakaLogType.LOG_INFO, "Proběhne pokus o opravu telefonu.");
                    }

                    result &= setLDAPdata(EBakaLDAPAttributes.MOBILE, BakaUtils.validatePhone(getSQLdata(EBakaSQL.F_GUA_BK_MOBILE)));
                } else {
                    result = false;
                }
            }
        }

        return result;
    }

    /**
     * Získání všech současných distribučních skupin zákonného zástupce.
     *
     * @return seznam plných DN současných skupin
     */
    public ArrayList<String> getCurrentDistributionLists() {
        return BakaADAuthenticator.getInstance().listMembership(this.getDN());
    }

    /**
     * Přepsání členství kontaktu v distribučních skupinách.
     *
     * @param distributionLists pole plných DN distribučních skupin
     * @return úspěch operace
     */
    public Boolean replaceDistributionLists(ArrayList<String> distributionLists) {

        if (dataLDAP == null) {
            return false;
        }

        if (distributionLists.equals(getCurrentDistributionLists())) {
            if (Settings.getInstance().debugMode()) {
                ReportManager.log(EBakaLogType.LOG_DEBUG, "Distribuční skupiny zákonného zástupce " + getSurname() + " " + getGivenName() + " jsou v pořádku.");
            }
            return true;
        }

        Boolean result = true;

        // smazání všech současných skupin
        result &= BakaADAuthenticator.getInstance().removeObjectFromAllGroups(this.getDN());

        // přidání do zadaných skupin
        result &= BakaADAuthenticator.getInstance().addObjectToGroup(this.getDN(), distributionLists);

        if (result) {
            if (Settings.getInstance().beVerbose()) {
                ReportManager.log(EBakaLogType.LOG_OK, "Distribuční skupiny zákonného zástupce " + getSurname() + " " + getGivenName() + " byly aktulizovány.");
            }
        } else {
            ReportManager.log(EBakaLogType.LOG_ERR, "Nebylo možné aktualizovat distribuční skupiny zákonného zástupce " + getSurname() + " " + getGivenName() + ".");
        }

        return result;
    }

    /**
     * Příjmení zákonného zástupce.
     *
     * @return příjmení zákonného zástupce žáka
     */
    public String getSurname() {
        if (this.dataSQL == null) {
            return this.dataLDAP.get(EBakaLDAPAttributes.NAME_LAST.attribute()).toString();
        }

        return this.dataSQL.get(EBakaSQL.F_GUA_BK_SURNAME.basename());
    }

    /**
     * Jméno zákonného zástupce.
     *
     * @return jméno zákonného zástupce žáka
     */
    public String getGivenName() {
        if (this.dataSQL == null) {
            return this.dataLDAP.get(EBakaLDAPAttributes.NAME_FIRST.attribute()).toString();
        }

        return this.dataSQL.get(EBakaSQL.F_GUA_BK_GIVENNAME.basename());
    }

    @Override
    public String getDN() {

        if (this.dataLDAP != null) {
            return this.dataLDAP.get(EBakaLDAPAttributes.DN.attribute()).toString();
        }

        return null;
    }

    @Override
    public String getLDAPdata(EBakaLDAPAttributes attr) {

        if (this.dataLDAP != null) {
            return (this.dataLDAP.containsKey(attr.attribute())) ? this.dataLDAP.get(attr.attribute()).toString() : "";
        }

        return "";
    }

    @Override
    public Boolean setLDAPdata(EBakaLDAPAttributes attr, String value) {

        if (this.dataLDAP != null) {
            if (value != null) {
                return BakaADAuthenticator.getInstance().replaceAttribute(getDN(), attr, value);
            } else {
                return BakaADAuthenticator.getInstance().replaceAttribute(getDN(), attr, "");
            }
        }

        return false;
    }

    @Override
    public String getExtensionAttribute(Integer attrNum) {

        if (attrNum != 1) {
            return null;
        }

        if (this.dataLDAP != null) {
            return this.dataLDAP.get(EBakaLDAPAttributes.EXT01.attribute()).toString();
        }

        return null;
    }

    @Override
    public Boolean setExtensionAttribute(Integer attrNum, String value) {

        if (attrNum != 1) {
            return false;
        }

        if (this.dataLDAP != null) {
            return BakaADAuthenticator.getInstance().replaceAttribute(this.getDN(), EBakaLDAPAttributes.EXT01, value);
        }

        return false;
    }

    @Override
    public String getInternalID() {

        if (this.dataSQL != null) {
            return this.dataSQL.get(EBakaSQL.F_GUA_BK_ID.basename());
        }

        if (this.dataLDAP != null) {
            return getExtensionAttribute(1);
        }

        return null;
    }

    @Override
    public String getSQLdata(EBakaSQL field) {

        if (this.dataSQL != null) {
            return (this.dataSQL.get(field.basename()).equals(EBakaSQL.NULL)) ? "" : this.dataSQL.get(field.basename());
        }

        return "";
    }

}
