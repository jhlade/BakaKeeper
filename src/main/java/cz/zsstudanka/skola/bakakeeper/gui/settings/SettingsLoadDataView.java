package cz.zsstudanka.skola.bakakeeper.gui.settings;

import cz.zsstudanka.skola.bakakeeper.gui.MainWindowController;
import cz.zsstudanka.skola.bakakeeper.gui.StatusBarController;
import cz.zsstudanka.skola.bakakeeper.gui.StatusBarView;
import cz.zsstudanka.skola.bakakeeper.gui.connection.ConnectionCheckView;
import cz.zsstudanka.skola.bakakeeper.gui.mvc.AbstractFrame;
import cz.zsstudanka.skola.bakakeeper.gui.mvc.AbstractView;

import javax.swing.*;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

import static cz.zsstudanka.skola.bakakeeper.gui.mvc.JavaComponentFactory.*;
import static javax.swing.JOptionPane.ERROR_MESSAGE;

/**
 * Otevření souboru nastavení aplikace.
 *
 * @author Jan Hladěna
 */
public class SettingsLoadDataView extends AbstractView<JPanel> {

    public SettingsLoadDataView(AbstractFrame mainFrame) {
        super(mainFrame);
    }

    @Override
    protected JPanel layout() {
        JPanel openDataFilePanel = panel(null);
        openDataFilePanel.setLayout(new BoxLayout(openDataFilePanel, BoxLayout.PAGE_AXIS));

        JPanel openDataFileForm = panel(null);
        openDataFileForm.setLayout(new BoxLayout(openDataFileForm, BoxLayout.Y_AXIS));

        // soubor s nastavením
        File dataFile = new File(getMainFrame().getController(SettingsController.class).getDataFileName());

        openDataFileForm.setAlignmentX(Component.CENTER_ALIGNMENT);
        openDataFileForm.setPreferredSize(new Dimension(400, 110));
        openDataFileForm.setMaximumSize(new Dimension(400, 110));
        openDataFileForm.setBorder(BorderFactory.createTitledBorder("<html>Heslo k souboru <code>" + dataFile.getName() + "</code>:</html>"));

        JPasswordField passphraseSettings = new JPasswordField(32);
        passphraseSettings.setBounds(0, 0, 50, 32);
        openDataFileForm.add(passphraseSettings);

        JButton openDataButton = button("Odemknout a otevřít", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                getMainFrame().getController(SettingsController.class).setPassphrase(passphraseSettings.getPassword());

                if (getMainFrame().getController(SettingsController.class).loadSettings()) {
                    getMainFrame().setContent(getMainFrame().getView(ConnectionCheckView.class).getContentPane());

                    getMainFrame().getController(StatusBarController.class).settingsUnlock();
                    getMainFrame().getController(MainWindowController.class).updateGlobalStatusMessage("Datový soubor byl otevřen.");
                } else {
                    // chybné heslo
                    JOptionPane.showMessageDialog(getMainFrame().getContent(), "Chybně zadané heslo, nebo chybný formát datového souboru.", "Datový soubor nebylo možné otevřít", ERROR_MESSAGE);
                }

                // aktualizace ikony
                getMainFrame().getView(StatusBarView.class).showLockIconState();
            }
        });
        openDataFileForm.add(openDataButton);

        // TODO
        JButton openAnotherFileButton = button("Zvolit jiný soubor...", null);
        openDataFileForm.add(openAnotherFileButton);

        openDataFilePanel.add(openDataFileForm);

        return openDataFilePanel;
    }

}
