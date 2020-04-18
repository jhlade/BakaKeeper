package cz.zsstudanka.skola.bakakeeper.model.interfaces;

import java.util.ArrayList;

/**
 * Interface obecného uživatele v Active Directory.
 *
 * @author Jan Hladěna
 *
 * @deprecated
 */
public interface IUzivatelAD {

    /**
     * Základní přihlašovací jméno bez specifikace domémy.
     *
     * @return řetězec atributu sAMAccountName
     */
    String getADLogin();

    /**
     * Primární e-mail uživatele.
     *
     * @return řetězec atributu mail
     */
    String getADEmail();

    /**
     * Celé zobrazované jméno uživatele.
     *
     * @return řetězec atributu displayName
     */
    String getDisplayName();

    /**
     * Ladící zprávy spojené s daným uživatelem.
     *
     * @return pole ladících zpráv
     */
    ArrayList<String> getDebugMessages();

}