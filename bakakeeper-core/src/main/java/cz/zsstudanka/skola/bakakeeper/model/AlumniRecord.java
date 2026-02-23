package cz.zsstudanka.skola.bakakeeper.model;

import lombok.Getter;
import lombok.Setter;

/**
 * Záznam absolventa – typovaná entita pro bývalé žáky.
 *
 * @author Jan Hladěna
 */
@Getter
@Setter
public class AlumniRecord extends Person {

    /** rok ukončení studia */
    private int graduationYear;
}
