package cz.zsstudanka.skola.bakakeeper.gui;

import cz.zsstudanka.skola.bakakeeper.gui.connection.ConnectionCheckController;
import cz.zsstudanka.skola.bakakeeper.gui.connection.ConnectionCheckView;
import cz.zsstudanka.skola.bakakeeper.gui.mvc.AbstractFrame;
import cz.zsstudanka.skola.bakakeeper.gui.settings.*;
import cz.zsstudanka.skola.bakakeeper.settings.Version;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import static cz.zsstudanka.skola.bakakeeper.gui.mvc.JavaComponentFactory.frame;
import static cz.zsstudanka.skola.bakakeeper.gui.mvc.JavaComponentFactory.panel;
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

        // stavový řádek - inicializace
        this.frame.add(getView(StatusBarView.class).getContentPane(), BorderLayout.SOUTH);
        getController(StatusBarController.class).setMessage("Spuštěno: " + Version.getInstance().getName() + " " + Version.getInstance().getVersion());
        getView(StatusBarView.class).showStatus();
        getView(StatusBarView.class).showLockIconState();

        // vstupní bod - grafické načtení nastavení aplikace
        setContent(getView(SettingsLoadDataView.class).getContentPane());
        getController(SettingsController.class).loadDataFirstResponder();
    }

    @Override
    protected void registerViews() {
        // stavový řádek
        views.put(StatusBarView.class, new StatusBarView(this));

        // nastavení aplikace
        views.put(SettingsLoadDataView.class, new SettingsLoadDataView(this));
        views.put(SettingsOpenFileView.class, new SettingsOpenFileView(this));
        views.put(SettingsInitView.class, new SettingsInitView(this));

        // testování spojení
        views.put(ConnectionCheckView.class, new ConnectionCheckView(this));
    }

    @Override
    protected void registerControllers() {
        // hlavní okno
        controllers.put(MainWindowController.class, new MainWindowController(this));
        // stavový řádek
        controllers.put(StatusBarController.class, new StatusBarController(this));

        // nastavení aplikace
        controllers.put(SettingsController.class, new SettingsController(this));

        // testování spojení
        controllers.put(ConnectionCheckController.class, new ConnectionCheckController(this));
    }

    @Override
    protected JFrame layout() {

        // hlavní obsah
        this.content = panel(new BorderLayout());

        // stvoření základního okna
        JFrame window = frame(Version.getInstance().getName() + " " + Version.getInstance().getVersion(), this.getContent());

        // nastavení velikosti
        window.setSize(980, 650);

        // menu
        JMenuBar menuBar = new JMenuBar();

        JMenu menuBakaKeeper = new JMenu("BakaKeeper");
        menuItem(menuBakaKeeper, "Konec", "Ukončení grafické aplikace", new ExitApp());

        // menu Nastavení
        JMenu menuSettings = new JMenu("Nastavení");
        // TODO přeformulovat
        menuItem(menuSettings, "Otevřít nastavení", "Nové otevření nastavení", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                setContent(getView(SettingsLoadDataView.class).getContentPane());
                getController(SettingsController.class).loadDataFirstResponder();
            }
        });
        menuItem(menuSettings, "Nová inicializace", "Provede novou inicializaci nastavení", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                setContent(getView(SettingsInitView.class).getContentPane());
                getController(SettingsController.class).initFirstResponder();
            }
        });
        menuItem(menuSettings, "Test nastavení", "Provede otestování nastavení a spojení.", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                setContent(getView(ConnectionCheckView.class).getContentPane());
                getController(ConnectionCheckController.class).connectionCheckFirstResponder();
            }
        });

        // menu Nápověda
        JMenu menuHelp = new JMenu("Nápověda");
        menuItem(menuHelp, "O programu", "Informace o programu " + Version.getInstance().getName(), new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JOptionPane.showMessageDialog(frame, Version.getInstance().getInfo(true), "O programu " + Version.getInstance().getName(), INFORMATION_MESSAGE, new ImageIcon(getClass().getResource("/gui/studanka.64x64.png")));
            }
        });

        menuBar.add(menuBakaKeeper);
        menuBar.add(menuSettings);
        menuBar.add(menuHelp);
        window.setJMenuBar(menuBar);

        return window;
    }

    @Override
    public JComponent getContent() {
        return (JPanel) this.content;
    }

    @Override
    public void setContent(JComponent content) {
        getContent().removeAll();
        getContent().add(content, BorderLayout.CENTER);

        getContent().revalidate();
        getContent().repaint();
    }

    // Ukončení aplikace
    private class ExitApp implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            getController(MainWindowController.class).exitApp();
        }
    }

}
