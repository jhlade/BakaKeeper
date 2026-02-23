package cz.zsstudanka.skola.bakakeeper.model.entities;

import cz.zsstudanka.skola.bakakeeper.components.ReportManager;
import cz.zsstudanka.skola.bakakeeper.connectors.BakaSQL;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLogType;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaSQL;
import cz.zsstudanka.skola.bakakeeper.exceptions.IncomparableInternalUserException;
import cz.zsstudanka.skola.bakakeeper.settings.Settings;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Date;

/**
 * Reprezentace interního uživatele systému.
 * Pro 2024-09-26 uvažován pouze správce pro účely zálohy a obnovy.
 *
 * @author Jan Hladěna
 */
public class BakaInternalUser implements Comparable<BakaInternalUser>, Serializable {

    static final long serialVersionUID = 1001L;

    // interní pole
    private String f_id;
    private String f_login;
    private String f_type;
    private String f_acl;
    private String f_upd_type;
    private String f_kodf;
    private String f_pwd;
    private String f_pwd_method;
    private String f_pwd_salt;
    private Date f_pwd_modified;
    private String f_modifiedby;

    private Boolean isValid = false;
    private Boolean isModified = false;

    /**
     * Konstruktor interního uživatele.
     *
     * @param login definice uživatele pomocí položky LOGIN
     */
    public BakaInternalUser(String login) {

        StringBuilder selectBuilder = new StringBuilder();

        selectBuilder.append("SELECT");
        selectBuilder.append(" ");

        selectBuilder.append(EBakaSQL.F_LOGIN_ID.basename());
        selectBuilder.append(",");
        selectBuilder.append(EBakaSQL.F_LOGIN_LOGIN.basename());
        selectBuilder.append(",");
        selectBuilder.append(EBakaSQL.F_LOGIN_TYPE.basename());
        selectBuilder.append(",");
        selectBuilder.append(EBakaSQL.F_LOGIN_ACL.basename());
        selectBuilder.append(",");
        selectBuilder.append(EBakaSQL.F_LOGIN_UPDTYPE.basename());
        selectBuilder.append(",");
        selectBuilder.append(EBakaSQL.F_LOGIN_PWD.basename());
        selectBuilder.append(",");
        selectBuilder.append(EBakaSQL.F_LOGIN_PWD_MET.basename());
        selectBuilder.append(",");
        selectBuilder.append(EBakaSQL.F_LOGIN_PWD_SALT.basename());
        selectBuilder.append(",");
        selectBuilder.append(EBakaSQL.F_LOGIN_KODF.basename());
        selectBuilder.append(",");
        selectBuilder.append(EBakaSQL.F_LOGIN_MODIFIED.basename());
        selectBuilder.append(",");
        selectBuilder.append(EBakaSQL.F_LOGIN_MODIFIEDBY.basename());

        selectBuilder.append(" ");
        selectBuilder.append("FROM " + EBakaSQL.TBL_LOGIN.field() + " WHERE LOGIN = '" + login.replace(EBakaSQL.LIT_DUMMY_ADMIN.field(), EBakaSQL.LIT_LOGIN_ADMIN.field()) + "'");

        // připojení k databázi
        BakaSQL.getInstance().connect();

        try {
            ResultSet rs = BakaSQL.getInstance().select(selectBuilder.toString());

            while (rs.next()) {

                this.f_id = rs.getString(EBakaSQL.F_LOGIN_ID.basename());
                this.f_login = rs.getString(EBakaSQL.F_LOGIN_LOGIN.basename());
                this.f_type = rs.getString(EBakaSQL.F_LOGIN_TYPE.basename());
                this.f_acl = rs.getString(EBakaSQL.F_LOGIN_ACL.basename());
                this.f_upd_type = rs.getString(EBakaSQL.F_LOGIN_UPDTYPE.basename());
                this.f_kodf = rs.getString(EBakaSQL.F_LOGIN_KODF.basename());
                this.f_pwd = rs.getString(EBakaSQL.F_LOGIN_PWD.basename());
                this.f_pwd_method = rs.getString(EBakaSQL.F_LOGIN_PWD_MET.basename());
                this.f_pwd_salt = rs.getString(EBakaSQL.F_LOGIN_PWD_SALT.basename());
                this.f_pwd_modified = rs.getDate(EBakaSQL.F_LOGIN_MODIFIED.basename());
                this.f_modifiedby = rs.getString(EBakaSQL.F_LOGIN_MODIFIEDBY.basename());

                this.isValid = true;
            }

        } catch (Exception e) {
            ReportManager.handleException("Chyba při vyhledávání interního uživatele.", e, true);
            this.isValid = false;
        }
    }

    /**
     * Stav validity záznamu.
     *
     * @return validita záznamu
     */
    public Boolean isValid() {
        return this.isValid;
    }

    /**
     * Získání data poslední modifikace.
     *
     * @return datum poslední změny
     */
    public Date getModifiedDate() {
        return (isValid) ? this.f_pwd_modified : null;
    }

    /**
     * Získání interního ID
     *
     * @return pole INTERN_KOD
     */
    public String getID() {
        return (isValid) ? this.f_id : null;
    }

    /**
     * Získání loginu.
     *
     * @return pole LOGIN
     */
    public String getLogin() {
        return (isValid) ? this.f_login : null;
    }

    /**
     * Získání B64 hashe hesla.
     *
     * @return pole HESLO
     */
    public String getPasswordHash() {
        return (isValid) ? this.f_pwd : null;
    }

    public boolean writeBack() {

        // dotaz pro UPDATE
        StringBuilder update = new StringBuilder();
        update.append(EBakaSQL.F_LOGIN_PWD.basename() + " = ?"); // 1
        update.append(", ");
        update.append(EBakaSQL.F_LOGIN_TYPE.basename() + " = ?"); // 2
        update.append(", ");
        update.append(EBakaSQL.F_LOGIN_ACL.basename() + " = ?"); // 3
        update.append(", ");
        update.append(EBakaSQL.F_LOGIN_UPDTYPE.basename() + " = ?"); // 4
        update.append(", ");
        update.append(EBakaSQL.F_LOGIN_PWD_MET.basename() + " = ?"); // 5
        update.append(", ");
        update.append(EBakaSQL.F_LOGIN_PWD_SALT.basename() + " = ?"); // 6
        update.append(", ");
        update.append(EBakaSQL.F_LOGIN_MODIFIED.basename() + " = ?"); // 7
        update.append(", ");
        update.append(EBakaSQL.F_LOGIN_MODIFIEDBY.basename() + " = ?"); // 8
        update.append(", ");
        update.append(EBakaSQL.F_LOGIN_KODF.basename() + " = ?"); // 9
        update.append("");

        String updateStatement = "UPDATE " + EBakaSQL.TBL_LOGIN.field()
                + " SET " + update.toString()
                + " WHERE " + EBakaSQL.F_LOGIN_LOGIN.basename() + " = ?"; // 10

        Boolean result = true;

        if (Settings.getInstance().develMode()) {
            ReportManager.log(EBakaLogType.LOG_DEVEL, "Vývojářský režim je aktivní. Nebude proveden zápis do produkčních dat.");
            ReportManager.log(EBakaLogType.LOG_SQL, updateStatement);
        }

        try {
            // vypnutí autocommitu
            BakaSQL.getInstance().getConnection().setAutoCommit(false);
            PreparedStatement ps = BakaSQL.getInstance().getConnection().prepareStatement(updateStatement);

            ps.setString(1, this.f_pwd);
            ps.setString(2, this.f_type);
            ps.setString(3, this.f_acl);
            ps.setString(4, this.f_upd_type);
            ps.setString(5, this.f_pwd_method);
            ps.setString(6, this.f_pwd_salt);
            ps.setDate(7, new java.sql.Date(this.f_pwd_modified.getTime()));
            ps.setString(8, this.f_modifiedby);
            ps.setString(9, this.f_kodf);
            // where
            ps.setString(10, this.f_login);

            int execute = ps.executeUpdate();

            if (execute == 1) {
                // OK
                if (!Settings.getInstance().develMode()) {
                    BakaSQL.getInstance().getConnection().commit();
                    ReportManager.log(EBakaLogType.LOG_VERBOSE, "Záloha byla obnovena.");
                } else {
                    ReportManager.log(EBakaLogType.LOG_DEVEL, "Zde by proběhl zápis do produkčních dat.");
                }
            } else {
                result = false;

                // rollback
                try {
                    BakaSQL.getInstance().getConnection().rollback();
                    ReportManager.log(EBakaLogType.LOG_ERR, "Nezdařilo se vykonat zápis do produkčních dat, byl proveden rollback.");
                } catch (Exception e) {
                    ReportManager.handleException("Nezdařilo se vykonat rollback po neúspěšném pokusu o úpravu tabulky interních uživatelů.", e);
                }
            }

        } catch (Exception e) {
            result = false;
            ReportManager.handleException("Nebylo možné uložit změny v tabulce interních uživatelů.", e);
        }

        return result;
    }

    /**
     * Debug výpis objektu interního uživatele.
     *
     * @return interní uživatel
     */
    @Override
    public String toString() {
        StringBuilder debugString = new StringBuilder();

        if (this.isValid) {
            debugString.append("[interní uživatel] '" + this.f_login.replace(" ", "") + "' (" + this.f_id.replace(" ", "") + ")\n");
            debugString.append("Oprávnění: " + this.f_acl.replace(" ", "") + "/" + this.f_type.replace(" ", "") + "\n");
            debugString.append("Metoda hashe: " + this.f_pwd_method + ", modifikace " + this.f_pwd_modified.toString() + " (" + ((this.f_modifiedby != null) ? this.f_modifiedby.replace(" ", "") : "---") + ")");
        } else {
            debugString.append("[neplatný interní uživatel]\n");
        }

        return debugString.toString();
    }

    /**
     * Porovnání záznamu,
     *
     * @param compareUser jiný záznam
     * @return výsledek
     */
    @lombok.SneakyThrows
    @Override
    public int compareTo(BakaInternalUser compareUser) {

        // jiný uživatel
        if (!this.getID().equals(compareUser.getID())) {
            throw new IncomparableInternalUserException("[" + this.getID() + " : " + compareUser.getID() + "]");
        }

        // heslo je změněno, porovná se podle data
        if (!this.getPasswordHash().equals(compareUser.getPasswordHash())) {
            return this.getModifiedDate().compareTo(compareUser.getModifiedDate());
        }

        // vše v pořádku, účty jsou shodné
        return 0;
    }
}
