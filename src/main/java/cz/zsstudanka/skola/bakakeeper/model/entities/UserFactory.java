package cz.zsstudanka.skola.bakakeeper.model.entities;

import cz.zsstudanka.skola.bakakeeper.model.collections.SQLrecords;

/**
 * Statická továrna na uživatelské účty.
 *
 * @author Jan Hladěna
 */
public class UserFactory {

    public static Student getStudentByUPN(String upn) {
        // TODO
        return null;
    }

    public static Student getStudentByID(String studentID) {

        SQLrecords catalog = new SQLrecords(null, null);

        // TODO
        return null;
    }

    public static Student getStudentByPair(String studentID, String upn) {
        return null;
    }

    /**
     * Vytvoření nového žáka v LDAP podle jeho interního kódu v Bakalářích.
     *
     * @param studentID identifikátor INTERN_KOD v Bakalářích
     * @return instance nového žáka
     */
    public static Student newStudent(String studentID) {

        // získání dat
        // vytvoření účtu
        // vytvoření instance studenta

        // TODO
        return null;
    }

}
