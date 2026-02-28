package cz.zsstudanka.skola.bakakeeper.gui;

/**
 * Vstupní bod GUI aplikace BakaKeeper.
 * Samostatná třída bez dědičnosti Application – obchází omezení
 * modulového systému JavaFX při spuštění z fat JARu.
 */
public class Launcher {

    public static void main(String[] args) {
        BakaKeeperApp.main(args);
    }
}
