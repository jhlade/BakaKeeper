package cz.zsstudanka.skola.bakakeeper.gui;

import cz.zsstudanka.skola.bakakeeper.gui.mvc.AbstractFrame;
import cz.zsstudanka.skola.bakakeeper.settings.Settings;
import cz.zsstudanka.skola.bakakeeper.settings.Version;

import javax.swing.*;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import static cz.zsstudanka.skola.bakakeeper.gui.mvc.JCFactory.frame;
import static cz.zsstudanka.skola.bakakeeper.gui.mvc.JCFactory.menuItem;

public class MainWindowFrame extends AbstractFrame {

    public MainWindowFrame() {
        super();
    }

    @Override
    protected void registerViews() {

    }

    @Override
    protected void registerControllers() {
        controllers.put(MainWindowController.class, new MainWindowController(this));
    }

    @Override
    protected JFrame layout() {

        // stvoření základního okna
        JFrame window = frame(Version.getInstance().getName() + " " + Version.getInstance().getVersion(), this.getContent());

        // nastavení velikosti
        window.setSize(980, 650);

        // menu
        JMenuBar menuBar = new JMenuBar();

        JMenu test = new JMenu("BakaKeeper");
        JMenuItem konec = menuItem(test, "Konec", "Ukončení grafické aplikace", new ExitApp());

        menuBar.add(test);
        window.setJMenuBar(menuBar);

        return window;
    }

    @Override
    public JComponent getContent() {
        return null;
    }

    @Override
    public void setContent(JComponent content) {

    }

    // Ukončení aplikace
    private class ExitApp implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            getController(MainWindowController.class).exitApp();
        }
    }

}
