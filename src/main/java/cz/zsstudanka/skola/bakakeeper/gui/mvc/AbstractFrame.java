package cz.zsstudanka.skola.bakakeeper.gui.mvc;

import javax.swing.JComponent;
import javax.swing.JFrame;
import java.util.HashMap;
import java.util.Map;

import static cz.zsstudanka.skola.bakakeeper.gui.mvc.JavaComponentFactory.showFrame;

/**
 * Generické okno.
 *
 * @author Jan Hladěna
 */
public abstract class AbstractFrame {

    /** okno */
    protected final JFrame frame;

    /** obsah okna */
    protected JComponent content;

    /** pohledy */
    protected final Map<Class<? extends AbstractView<? extends JComponent>>, AbstractView<? extends JComponent>> views = new HashMap<Class<? extends AbstractView<? extends JComponent>>, AbstractView<? extends JComponent>>();

    /** řadiče */
    protected final Map<Class<? extends AbstractController>, AbstractController> controllers = new HashMap<Class<? extends AbstractController>, AbstractController>();

    /**
     * Generický konstruktor.
     */
    public AbstractFrame() {
        // registrace řízení a zobrazení
        registerControllers();
        registerViews();

        // nastavení prvků okna
        this.frame = layout();
    }

    /**
     * Zobrazení okna.
     */
    public void show() {
        showFrame(this.frame);
    }

    /**
     * Registrace pohledů. Implementuje se vložením do mapy "views".
     */
    protected abstract void registerViews();

    /**
     * Registrace řadičů. Implementuje se vložením do mapy "controllers".
     */
    protected abstract void registerControllers();

    /**
     * Provedení vykreslení okna.
     *
     * @return obsah okno
     */
    protected abstract JFrame layout();

    /**
     * Získání konkrétního pohledu z registrovaných pohledů.
     *
     * @param viewClass třída pohledu
     * @return pohled
     * @param <View> instance pohledu
     */
    public <View extends AbstractView<? extends JComponent>> View getView(Class<View> viewClass) {
        return (View) views.get(viewClass);
    }

    /**
     * Získání konkrétního řadiče z registrovaných řadičů.
     *
     * @param controllerClass třída řadiče
     * @return řadič
     * @param <Controller> instance řadiče
     */
    public <Controller extends AbstractController> Controller getController(Class<Controller> controllerClass) {
        return (Controller) controllers.get(controllerClass);
    }

    /**
     * Získání obsahu hlavní komponenty okna.
     *
     * @return obsah okna
     */
    public abstract JComponent getContent();

    /**
     * Nastavení obsahu hlavní komponenty okna.
     *
     * @param content obsah
     */
    public abstract void setContent(JComponent content);

}
