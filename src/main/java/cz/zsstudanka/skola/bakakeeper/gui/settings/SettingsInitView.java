package cz.zsstudanka.skola.bakakeeper.gui.settings;

import cz.zsstudanka.skola.bakakeeper.gui.mvc.AbstractFrame;
import cz.zsstudanka.skola.bakakeeper.gui.mvc.AbstractView;

import javax.swing.*;

import java.awt.*;

import static cz.zsstudanka.skola.bakakeeper.gui.mvc.JavaComponentFactory.label;
import static cz.zsstudanka.skola.bakakeeper.gui.mvc.JavaComponentFactory.panel;

/**
 * Okno s grafickou inicializací programu.
 *
 * @author Jan Hladěna
 */
public class SettingsInitView extends AbstractView<JPanel> {

    public SettingsInitView(AbstractFrame mainFrame) {
        super(mainFrame);
    }

    @Override
    protected JPanel layout() {
        // okno samo o sobě
        JPanel settingsInitPanel = panel(new BorderLayout());

        // TODO
        JLabel infoLabel = label("Formulář - inicializace nastavení");
        settingsInitPanel.add(infoLabel);

        return settingsInitPanel;
    }
}
