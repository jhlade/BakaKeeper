package cz.zsstudanka.skola.bakakeeper.service;

/**
 * Služba pro správu hesel uživatelských účtů.
 *
 * @author Jan Hladěna
 */
public interface PasswordService {

    /**
     * Resetuje heslo žáka na vygenerované počáteční heslo.
     *
     * @param dn distinguished name žákovského účtu
     * @param surname příjmení
     * @param givenName jméno
     * @param classYear ročník
     * @param classId ID třídy
     * @return výsledek operace
     */
    SyncResult resetStudentPassword(String dn, String surname, String givenName,
                                     Integer classYear, Integer classId);

    /**
     * Nastaví heslo uživateli.
     *
     * @param dn distinguished name účtu
     * @param password nové heslo
     * @param mustChange vyžadovat změnu při příštím přihlášení
     * @return úspěch operace
     */
    boolean setPassword(String dn, String password, boolean mustChange);
}
