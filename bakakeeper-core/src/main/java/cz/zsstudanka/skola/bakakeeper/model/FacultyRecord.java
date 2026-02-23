package cz.zsstudanka.skola.bakakeeper.model;

import lombok.Getter;
import lombok.Setter;

/**
 * Záznam zaměstnance (vyučujícího) – typovaná entita.
 *
 * @author Jan Hladěna
 */
@Getter
@Setter
public class FacultyRecord extends Person {

    /** označení třídy, pokud je třídní učitel (null = není třídní) */
    private String classLabel;

    /** příznak aktivního vyučujícího v aktuálním školním roce */
    private boolean activeThisYear;
}
