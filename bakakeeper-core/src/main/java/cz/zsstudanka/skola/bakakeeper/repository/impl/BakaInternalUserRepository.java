package cz.zsstudanka.skola.bakakeeper.repository.impl;

import cz.zsstudanka.skola.bakakeeper.components.ReportManager;
import cz.zsstudanka.skola.bakakeeper.connectors.SQLConnector;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLogType;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaSQL;
import cz.zsstudanka.skola.bakakeeper.model.InternalUserSnapshot;
import cz.zsstudanka.skola.bakakeeper.repository.InternalUserRepository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.Optional;

/**
 * Implementace InternalUserRepository nad SQL konektorem Bakaláři.
 * Čte a zapisuje záznamy z tabulky {@code dbo.webuser}.
 *
 * @author Jan Hladěna
 */
public class BakaInternalUserRepository implements InternalUserRepository {

    private final SQLConnector sql;

    public BakaInternalUserRepository(SQLConnector sql) {
        this.sql = sql;
    }

    @Override
    public Optional<InternalUserSnapshot> findByLogin(String login) {
        sql.connect();

        // admin login se v DB ukládá jako '*'
        String dbLogin = "ADMIN".equalsIgnoreCase(login) ? "*" : login;

        String query = "SELECT " +
                "INTERN_KOD, LOGIN, KOD1, PRAVA, UPD_TYP, KODF, " +
                "HESLO, METODA, SALT, MODIFIED, MODIFIEDBY " +
                "FROM " + EBakaSQL.TBL_LOGIN.field() +
                " WHERE LOGIN = ?";

        try {
            PreparedStatement ps = sql.getConnection().prepareStatement(query);
            ps.setString(1, dbLogin);
            ResultSet rs = ps.executeQuery();

            if (rs != null && rs.next()) {
                InternalUserSnapshot snapshot = new InternalUserSnapshot(
                        rs.getString("INTERN_KOD"),
                        rs.getString("LOGIN"),
                        rs.getString("KOD1"),
                        rs.getString("PRAVA"),
                        rs.getString("UPD_TYP"),
                        rs.getString("KODF"),
                        rs.getString("HESLO"),
                        rs.getString("METODA"),
                        rs.getString("SALT"),
                        rs.getTimestamp("MODIFIED") != null
                                ? new Date(rs.getTimestamp("MODIFIED").getTime()) : null,
                        rs.getString("MODIFIEDBY")
                );
                return Optional.of(snapshot);
            }
        } catch (SQLException e) {
            ReportManager.log(EBakaLogType.LOG_ERR,
                    "Chyba při čtení interního uživatele '" + login + "': " + e.getMessage());
        }

        return Optional.empty();
    }

    @Override
    public void writeBack(InternalUserSnapshot snapshot) {
        sql.connect();

        String update = "UPDATE " + EBakaSQL.TBL_LOGIN.field() +
                " SET HESLO = ?, METODA = ?, SALT = ?, MODIFIED = ?, MODIFIEDBY = ? " +
                "WHERE LOGIN = ?";

        try {
            PreparedStatement ps = sql.getConnection().prepareStatement(update);
            ps.setString(1, snapshot.pwdHash());
            ps.setString(2, snapshot.pwdMethod());
            ps.setString(3, snapshot.pwdSalt());
            ps.setTimestamp(4, snapshot.modified() != null
                    ? new java.sql.Timestamp(snapshot.modified().getTime()) : null);
            ps.setString(5, snapshot.modifiedBy());
            ps.setString(6, snapshot.login());

            int rows = ps.executeUpdate();
            if (rows == 0) {
                ReportManager.log(EBakaLogType.LOG_ERR,
                        "Zápis interního uživatele '" + snapshot.login() + "' neaktualizoval žádný řádek.");
            }
        } catch (SQLException e) {
            ReportManager.log(EBakaLogType.LOG_ERR,
                    "Chyba při zápisu interního uživatele '" + snapshot.login() + "': " + e.getMessage());
        }
    }
}
