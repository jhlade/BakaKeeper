package cz.zsstudanka.skola.bakakeeper.gui.settings;

import cz.zsstudanka.skola.bakakeeper.gui.MainWindowController;
import cz.zsstudanka.skola.bakakeeper.gui.mvc.AbstractFrame;
import cz.zsstudanka.skola.bakakeeper.gui.mvc.AbstractView;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

import static cz.zsstudanka.skola.bakakeeper.gui.mvc.JavaComponentFactory.*;

/**
 * Okno pro otevření nastavení programu.
 * TODO
 * - soubor nebyl nalezen
 * - dialog pro otevření dalšího -> LoadData
 * - možnost inicializovat znovu -> Init
 *
 * @author Jan Hladěna
 */
public class SettingsOpenFileView extends AbstractView<JPanel> {

    public SettingsOpenFileView(AbstractFrame mainFrame) {
        super(mainFrame);
    }

    @Override
    protected JPanel layout() {

        // panel sám o sobě
        JPanel settingsLoadPanel = panel(null);
        settingsLoadPanel.setLayout(new BoxLayout(settingsLoadPanel, BoxLayout.Y_AXIS));

        // informace
        File fileDataInfo = new File(getMainFrame().getController(SettingsController.class).getDataFileName());
        JLabel labelNoDefaultSettingsFile = label("<html><center>Nebyl nalezen soubor<br>s nastavením (<code>" + fileDataInfo.getName() + "</code>).</center></html>", true);
        settingsLoadPanel.add(labelNoDefaultSettingsFile);

        // tlačítka
        JButton buttonOpen = button("📂 Otevřít jiný soubor nastavení...", new ActionOpenDataFile());
        settingsLoadPanel.add(buttonOpen);

        JButton buttonInitialize = button("🪄 Nová inicializace", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // změna okna na inicializaci
                getMainFrame().setContent(getMainFrame().getView(SettingsInitView.class).getContentPane());
                getMainFrame().getController(MainWindowController.class).updateGlobalStatusMessage("Vyžádána nová inicializace.");
            }
        });
        settingsLoadPanel.add(buttonInitialize);

        return settingsLoadPanel;
    }


    /**
     * Obsluha + dialog pro načtení jiného datového souboru.
     *
     */
    protected class ActionOpenDataFile implements ActionListener {

        final JFileChooser dataFileChooser = new JFileChooser();

        @Override
        public void actionPerformed(ActionEvent e) {

            dataFileChooser.setFileFilter(new FileFilter() {
                @Override
                public boolean accept(File f) {
                    if (f.isDirectory()) {
                        return true;
                    } else {
                        // TODO dynamicky upravit příponu z nastavení - Settings.java
                        return f.getName().toLowerCase().endsWith(".dat");
                    }
                }

                @Override
                public String getDescription() {
                    return "Šifrovaná nastavení BakaKeeper";
                }
            });
            int ret = dataFileChooser.showOpenDialog(getMainFrame().getView(SettingsOpenFileView.class).getContentPane());

            if (ret == JFileChooser.APPROVE_OPTION) {
                File file = dataFileChooser.getSelectedFile();
                getMainFrame().getController(SettingsController.class).setDataFileName(file.getAbsolutePath());
                getMainFrame().setContent(getMainFrame().getView(SettingsLoadDataView.class).getContentPane());
                getMainFrame().getController(MainWindowController.class).updateGlobalStatusMessage("Byl zvolen datový soubor " + file.getName() + ".");
                getMainFrame().getController(SettingsController.class).loadDataFirstResponder();
            } else {
                // TODO akce?
            }

        }
    }

}
