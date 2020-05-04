package cz.zsstudanka.skola.bakakeeper.model.entities;

/**
 * Statická továrna na uživatelské účty.
 *
 * @author Jan Hladěna
 */
public class UserFactory {

    /**
     * Získání instance žáka podle předaných interních dat.
     *
     * @param dataSQL data z evidence
     * @param dataLDAP data z AD
     * @return instance žáka
     */
    public static Student getStudentByPair(DataSQL dataSQL, DataLDAP dataLDAP) {
        return new Student(dataSQL, dataLDAP);
    }

    /**
     * Vytvoření nového žáka v LDAP podle jeho interního kódu v Bakalářích.
     *
     * @param studentData data žáka z evidence
     * @return instance nového žáka
     */
    public static Student newStudent(DataSQL studentData) {
        Student create = new Student(studentData, null);
        create.initializeAccount();

        return create;
    }

}
