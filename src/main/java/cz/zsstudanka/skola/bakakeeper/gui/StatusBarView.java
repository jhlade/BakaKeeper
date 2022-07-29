package cz.zsstudanka.skola.bakakeeper.gui;

import cz.zsstudanka.skola.bakakeeper.gui.mvc.AbstractFrame;
import cz.zsstudanka.skola.bakakeeper.gui.mvc.AbstractView;
import cz.zsstudanka.skola.bakakeeper.gui.settings.SettingsController;

import javax.swing.*;
import javax.swing.border.BevelBorder;
import javax.swing.border.Border;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import static cz.zsstudanka.skola.bakakeeper.gui.mvc.JavaComponentFactory.label;
import static cz.zsstudanka.skola.bakakeeper.gui.mvc.JavaComponentFactory.panel;

/**
 * Stavový řádek programu.
 *
 * @author Jan Hladěna
 */
public class StatusBarView extends AbstractView<JPanel> {

    /** Stavový popisek */
    private JLabel statusLabel;

    /** Stavová ikona (true = odemčeno, false = zamčeno) */
    private JLabel statusLockIcon;

    public StatusBarView(AbstractFrame mainFrame) {
        super(mainFrame);
        showStatus();
    }

    @Override
    protected JPanel layout() {
        JPanel statusBar = panel(null);

        statusBar.setLayout(new BoxLayout(statusBar, BoxLayout.X_AXIS));
        statusBar.setBorder((Border) new BevelBorder(BevelBorder.LOWERED));
        statusBar.setPreferredSize(new Dimension(640, 22));

        this.statusLockIcon = label(" X ");
        statusLockIcon.setBounds(0, 0, 32, 22);
        statusBar.add(statusLockIcon);

        this.statusLockIcon.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {

                // dvojklik
                if (e.getClickCount() == 2) {

                    if (!getMainFrame().getController(StatusBarController.class).isLocked()) {
                        // odemčeno, zobrazit dialog
                        int lockDialog = JOptionPane.showConfirmDialog(getMainFrame().getContent(), "Nastavení je nyní odemčeno. Přejete si ho uzamknout?");
                        if (lockDialog == 0) {
                            getMainFrame().getController(StatusBarController.class).settingsLock();
                            getMainFrame().getController(SettingsController.class).unloadSettings();

                            getMainFrame().getController(MainWindowController.class).updateGlobalStatusMessage("Nastavení bylo uzamčeno.");
                            // přesměrování
                            getMainFrame().getController(SettingsController.class).loadDataFirstResponder();
                        }
                    }

                    // aktualizace
                    getMainFrame().getView(StatusBarView.class).showLockIconState();
                }
            }
        });

        this.statusLabel = label("");
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

    /**
     * Zobrazení stavové ikony uzamčení nastavení.
     */
    public void showLockIconState() {
        String lockIcon = (getMainFrame().getController(StatusBarController.class).isLocked()) ? "🔒" : "🔑";

        this.statusLockIcon.setText(" " + lockIcon + " ");
        this.statusLockIcon.repaint();
    }
}
