package cz.zsstudanka.skola.bakakeeper.gui;

import cz.zsstudanka.skola.bakakeeper.gui.mvc.AbstractFrame;
import cz.zsstudanka.skola.bakakeeper.settings.Version;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import static cz.zsstudanka.skola.bakakeeper.gui.mvc.JavaComponentFactory.frame;
import static cz.zsstudanka.skola.bakakeeper.gui.mvc.JavaComponentFactory.menuItem;
import static javax.swing.JOptionPane.INFORMATION_MESSAGE;

/**
 * Hlavní okno GUI.
 *
 * @author Jan Hladěna
 */
public class MainWindowFrame extends AbstractFrame {

    public MainWindowFrame() {
        super();

        // stavový řádek
        this.frame.add(getView(StatusBarView.class).getContentPane(), BorderLayout.SOUTH);
        getController(StatusBarController.class).setMessage("Spuštěno: " + Version.getInstance().getName() + " " + Version.getInstance().getVersion());
        getView(StatusBarView.class).showStatus();
    }

    @Override
    protected void registerViews() {
        // stavový řádek
        views.put(StatusBarView.class, new StatusBarView(this));
    }

    @Override
    protected void registerControllers() {
        // hlavní okno
        controllers.put(MainWindowController.class, new MainWindowController(this));
        // stavový řádek
        controllers.put(StatusBarController.class, new StatusBarController(this));
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

        // menu Nápověda
        JMenu menuHelp = new JMenu("Nápověda");
        JMenuItem menuHelpAbout = menuItem(menuHelp, "O programu", "Informace o programu", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JOptionPane.showMessageDialog(frame, Version.getInstance().getInfo(true), "O programu " + Version.getInstance().getName(), INFORMATION_MESSAGE, new ImageIcon(getClass().getResource("/gui/studanka.64x64.png")));
            }
        });

        menuBar.add(test);
        menuBar.add(menuHelp);
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
