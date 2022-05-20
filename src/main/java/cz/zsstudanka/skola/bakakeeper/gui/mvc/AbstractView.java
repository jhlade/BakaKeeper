package cz.zsstudanka.skola.bakakeeper.gui.mvc;

import javax.swing.*;

/**
 * Generický pohled
 *
 * @author Jan Hladěna
 *
 * @param <ViewClass> zobrazitelná komponenta
 */
public abstract class AbstractView<ViewClass extends JComponent>  {

    private final AbstractFrame mainFrame;
    private final ViewClass contentPane;

    public AbstractView(AbstractFrame mainFrame) {
        this.mainFrame = mainFrame;
        this.contentPane = layout();
    }
    protected abstract ViewClass layout();

    protected AbstractFrame getMainFrame() {
        return mainFrame;
    }

    public ViewClass getContentPane() {
        return contentPane;
    }

}
