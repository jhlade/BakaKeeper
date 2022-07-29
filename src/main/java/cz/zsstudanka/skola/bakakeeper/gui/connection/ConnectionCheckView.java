package cz.zsstudanka.skola.bakakeeper.gui.connection;

import cz.zsstudanka.skola.bakakeeper.gui.mvc.AbstractFrame;
import cz.zsstudanka.skola.bakakeeper.gui.mvc.AbstractView;

import javax.swing.*;
import java.awt.*;

import static cz.zsstudanka.skola.bakakeeper.gui.mvc.JavaComponentFactory.label;

/**
 * Pohled testování nastavení/spojení.
 *
 * @author Jan Hladěna
 */
public class ConnectionCheckView extends AbstractView<JPanel> {

    public ConnectionCheckView(AbstractFrame mainFrame) {
        super(mainFrame);
    }

    @Override
    protected JPanel layout() {

        JPanel checkPanel = new JPanel(new BorderLayout());

        JLabel infoLabel = label("Testování nastavení a spojení");
        checkPanel.add(infoLabel);

        return checkPanel;
    }
}
