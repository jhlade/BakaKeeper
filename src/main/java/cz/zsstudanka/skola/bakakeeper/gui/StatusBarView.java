package cz.zsstudanka.skola.bakakeeper.gui;

import cz.zsstudanka.skola.bakakeeper.gui.mvc.AbstractFrame;
import cz.zsstudanka.skola.bakakeeper.gui.mvc.AbstractView;

import javax.swing.*;
import javax.swing.border.BevelBorder;
import javax.swing.border.Border;

import java.awt.*;

import static cz.zsstudanka.skola.bakakeeper.gui.mvc.JavaComponentFactory.panel;

/**
 * Stavový řádek programu.
 *
 * @author Jan Hladěna
 */
public class StatusBarView extends AbstractView<JPanel> {

    /** Stavový popisek */
    private JLabel statusLabel;

    public StatusBarView(AbstractFrame mainFrame) {
        super(mainFrame);
        showStatus();
    }

    @Override
    protected JPanel layout() {
        JPanel statusBar = panel(null);

        statusBar.setLayout(new BoxLayout(statusBar, BoxLayout.X_AXIS));
        statusBar.setBorder((Border) new BevelBorder(BevelBorder.LOWERED));
        statusBar.setPreferredSize(new Dimension(640, 18));

        this.statusLabel = new JLabel();
        statusBar.add(this.statusLabel);

        return statusBar;
    }

    /**
     * Překreslení hlášení do stavového řádku.
     */
    public void showStatus() {
        statusLabel.setText(getMainFrame().getController(StatusBarController.class).getMessage());
        statusLabel.repaint();
    }
}
