package cz.zsstudanka.skola.bakakeeper.model.interfaces;

/**
 * Interface LDAP záznamu uživatele.
 *
 * @author Jan Hladěna
 */
public interface IUser {

    /**
     * Získání podrobných informací o uživatelském účtu na LDAP.
     *
     * @return podrobné informace o účtu - UserAccountControls
     */
    String getUAC();

}
