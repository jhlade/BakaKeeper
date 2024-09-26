package cz.zsstudanka.skola.bakakeeper.exceptions;

/**
 * Výjimka - interní uživatelský účet nelze porovnat.
 *
 * @author Jan Hladěna
 */
public class IncomparableInternalUserException extends Exception {

    public IncomparableInternalUserException(String msg) {
        super("Interní uživatelský účet nebylo možné porovnat." + msg);
    }

}
