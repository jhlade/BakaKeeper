package cz.zsstudanka.skola.bakakeeper;

/**
 * Runtime příznaky aplikace – sdílený kontext mezi core a CLI modulem.
 * Dočasné řešení nahrazující statické flagy v App – v budoucnu bude
 * nahrazeno injektovaným AppConfig.
 *
 * @author Jan Hladěna
 */
public class RuntimeContext {

    /** příznak vývojářského režimu - neprobíhá zápis do ostrých dat evidence */
    public static Boolean FLAG_DEVEL = false;

    /** příznak pro nezapisování žádných ostrých dat */
    public static Boolean FLAG_DRYRUN = false;

    /** příznak inicializace */
    public static Boolean FLAG_INIT = false;

    /** globální heslo pro sezení */
    public static String PASSPHRASE = "";

    /** příznak podrobností */
    public static Boolean FLAG_VERBOSE = false;

    /** příznak ladění */
    public static Boolean FLAG_DEBUG = false;
}
