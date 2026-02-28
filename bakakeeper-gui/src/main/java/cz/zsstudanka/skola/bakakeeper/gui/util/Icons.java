package cz.zsstudanka.skola.bakakeeper.gui.util;

import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.InputStream;
import java.util.List;

/**
 * Správa ikon aplikace – načítání PNG ikon z resources.
 */
public final class Icons {

    private static final String ICON_PATH = "/cz/zsstudanka/skola/bakakeeper/gui/icons/";
    private static final List<Integer> SIZES = List.of(16, 32, 48, 64, 128, 256);

    private Icons() {
    }

    /**
     * Nastaví ikony okna ve všech dostupných velikostech.
     */
    public static void loadWindowIcons(Stage stage) {
        for (int size : SIZES) {
            Image icon = getIcon(size);
            if (icon != null) {
                stage.getIcons().add(icon);
            }
        }
    }

    /**
     * Vrátí ikonu v dané velikosti (pro použití v ImageView apod.).
     */
    public static Image getIcon(int size) {
        InputStream stream = Icons.class.getResourceAsStream(
                ICON_PATH + "bakakeeper-" + size + ".png");
        return stream != null ? new Image(stream) : null;
    }
}
