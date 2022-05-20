package cz.zsstudanka.skola.bakakeeper.gui.mvc;

import javax.swing.JComponent;
import javax.swing.JFrame;
import java.util.HashMap;
import java.util.Map;

import static cz.zsstudanka.skola.bakakeeper.gui.mvc.JCFactory.showFrame;

/**
 * Generické okno.
 *
 * @author Jan Hladěna
 */
public abstract class AbstractFrame {

    /** okno */
    protected final JFrame frame;

    /** obsah */
    protected JComponent cont;

    /** pohledy */
    protected final Map<Class<? extends AbstractView<? extends JComponent>>, AbstractView<? extends JComponent>> views = new HashMap<Class<? extends AbstractView<? extends JComponent>>, AbstractView<? extends JComponent>>();

    /** řadiče */
    protected final Map<Class<? extends AbstractController>, AbstractController> controllers = new HashMap<Class<? extends AbstractController>, AbstractController>();

    /**
     * Generický konstruktor.
     */
    public AbstractFrame() {
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

    protected abstract void registerViews();

    protected abstract void registerControllers();

    protected abstract JFrame layout();

    public <View extends AbstractView<? extends JComponent>> View getView(Class<View> viewClass) {
        return (View) views.get(viewClass);
    }

    public <Controller extends AbstractController> Controller getController(Class<Controller> controllerClass) {
        return (Controller) controllers.get(controllerClass);
    }

    public abstract JComponent getContent();

    public abstract void setContent(JComponent content);

}
