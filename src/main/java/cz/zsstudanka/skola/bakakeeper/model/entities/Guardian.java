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

    private Boolean partial = false;

    private DataLDAP dataLDAP;
    private DataSQL dataSQL;

    protected Guardian(DataSQL dataSQL, DataLDAP dataLDAP) {

        if (dataSQL == null || dataLDAP == null) {
            this.partial = true;
        }

        this.dataSQL = dataSQL;
        this.dataLDAP = dataLDAP;
    }

    /**
     * Validace dat.
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
        //isValid &= (BakaUtils.validateEmail(dataSQL.get(EBakaSQL.F_GUA_BK_MAIL.basename())) != null);

        return isValid;
    }

    protected void initializeContact() {
        // TODO SQL->LDAP
    }

    /**
     * TODO návratová hodnota
     * @return
     */
    public Boolean deleteContact() {
        BakaADAuthenticator.getInstance().deleteRecord(this.getDN());
        return true;
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

        Boolean result = true;

        // smazání všech současných skupin
        result &= BakaADAuthenticator.getInstance().removeObjectFromAllGroups(this.getDN());

        // přidání do zadaných skupin
        result &= BakaADAuthenticator.getInstance().addObjectToGroup(this.getDN(), distributionLists);

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
            return this.dataSQL.get(EBakaSQL.F_GUA_ID.basename());
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
