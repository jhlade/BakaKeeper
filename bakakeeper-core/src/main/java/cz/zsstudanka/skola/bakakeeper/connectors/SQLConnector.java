package cz.zsstudanka.skola.bakakeeper.connectors;

import java.sql.Connection;
import java.sql.ResultSet;

/**
 * Rozhraní pro SQL konektivitu. Umožňuje testovatelnost repository vrstvy
 * bez závislosti na konkrétní implementaci (BakaSQL).
 *
 * @author Jan Hladěna
 */
public interface SQLConnector {

    /** Navázání spojení s databází. */
    void connect();

    /** Provedení SELECT dotazu. */
    ResultSet select(String sql);

    /** Získání aktivního spojení. */
    Connection getConnection();

    /** Stav připojení. */
    Boolean isConnected();
}
