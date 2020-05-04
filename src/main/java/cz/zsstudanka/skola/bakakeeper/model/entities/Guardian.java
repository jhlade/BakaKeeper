package cz.zsstudanka.skola.bakakeeper.model.entities;

import cz.zsstudanka.skola.bakakeeper.connectors.BakaADAuthenticator;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLDAPAttributes;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaSQL;
import cz.zsstudanka.skola.bakakeeper.model.interfaces.IRecordLDAP;
import cz.zsstudanka.skola.bakakeeper.model.interfaces.IRecordSQL;

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

    private String internalID;

    private Boolean partial;

    private DataLDAP dataLDAP;
    private DataSQL dataSQL;

    protected Guardian(DataSQL dataSQL, DataLDAP dataLDAP) {

        if (dataSQL == null || dataLDAP == null) {
            this.partial = true;
        }

        this.dataSQL = dataSQL;
        this.dataLDAP = dataLDAP;
    }

    protected void initialize() {
        // TODO SQL->LDAP
    }

    protected void delete() {
        // TODO LDAP->
    }

    //public ArrayList<String> get

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
            return this.dataLDAP.get(attr.attribute()).toString();
        }

        return null;
    }

    @Override
    public Boolean setLDAPdata(EBakaLDAPAttributes attr, String value) {

        if (this.dataLDAP != null) {
            return BakaADAuthenticator.getInstance().replaceAttribute(getDN(), attr, value);
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
            return this.dataSQL.get(field.basename());
        }

        return null;
    }

}
