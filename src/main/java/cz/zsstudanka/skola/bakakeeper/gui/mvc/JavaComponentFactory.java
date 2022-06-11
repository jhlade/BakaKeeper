package cz.zsstudanka.skola.bakakeeper.gui.mvc;

import java.awt.*;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;

/**
 * Zjednodušená statická továrna na komponenty.
 *
 * @author Jan Hladěna
 */
public abstract class JavaComponentFactory {

    /**
     * JFrame pro hlavní okno.
     *
     * @return nová instance JFrame
     */
    public static JFrame frame(String title, JComponent contentPane) {
        // základní JFrame
        JFrame frame = new JFrame();
        // automaticky vypínat aplikaci po zavření
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // nastavení titulku okna
        if (title != null) {
            frame.setTitle(title);
        }

        // hlavní kontejner pohledu
        if (contentPane != null) {
            frame.getContentPane().add(contentPane, BorderLayout.CENTER);
        }

        // velikost okna 980x650
        frame.setSize(980, 650);

        // zarovnání okna na střed obrazovky
        frame.setLocationRelativeTo(null);
        frame.pack();

        return frame;
    }

    /**
     * Zobrazení předaného JFrame.
     *
     * @param frame JFrame k zobrazení
     * @return zobrazené JFrame
     */
    public static JFrame showFrame(JFrame frame) {
        // zviditelnění
        frame.setVisible(true);
        return frame;
    }


    /**
     * Továrna na JPanel.
     *
     * @param layoutManager Použitý správce rozložení
     * @return nová instance JPanel
     */
    public static JPanel panel(LayoutManager layoutManager) {
        JPanel panel = new JPanel();

        if (layoutManager != null) {
            panel.setLayout(layoutManager);
        }

        return panel;
    }

    /**
     * Továrna na tlačítko (JButton). Přebírá textový popisek tlačítka a rovnou mapuje jeho actionListener.
     * Tento Listener může být předán i anonymní vnitřní třídou, nebo může být nulový - v takovém případě nebude vůbec
     * použit.
     *
     * @param caption Popisek tlačítka
     * @param actionListener Listener tlačítka
     * @return nová instance JButton
     */
    public static JButton button(String caption, ActionListener actionListener) {
        JButton button = new JButton();

        // popisek tlačítka - není-li žádný, nenastaví se (a předejde se případné null-pointer výjimce)
        if (caption != null) {
            button.setText(caption);
        }

        // nasazení actionListeneru
        if (actionListener != null) {
            button.addActionListener(actionListener);
        }

        // hotovo
        return button;
    }

    /**
     * Továrna na položku nabídky. Přebírá ukazatel na nabídku, ve které má být umístěna, titulek,
     * volitelnou bublinkovou nápovědu a volitelný Listener.
     *
     * @param menu Nabídka, na které má být položka zavěšena
     * @param caption Titulek
     * @param tooltip Bublinková nápověda
     * @param actionListener Listener události
     * @return nová instance JMenuItem
     */
    public static JMenuItem menuItem(JMenu menu, String caption, String tooltip, ActionListener actionListener) {
        JMenuItem menuItem = new JMenuItem(caption);

        // bublinková nápověda
        if (tooltip != null) menuItem.setToolTipText(tooltip);

        // action listener
        if (actionListener != null) menuItem.addActionListener(actionListener);

        // zavěšení na menu
        menu.add(menuItem);

        // vrácení objektu
        return menuItem;
    }

    /**
     * Továrna na popisek.
     *
     * @param caption popisek
     * @param bold použití tučného písma
     * @return nová instance JLabel
     */
    public static JLabel label(String caption, boolean bold) {
        JLabel label = new JLabel();

        // popisek
        if (caption != null) label.setText(caption);

        if (bold && caption != null) {
            label.setFont(label.getFont().deriveFont(label.getFont().getStyle() | Font.BOLD));
        }

        return label;
    }

    /**
     * Továrna na běžný popisek
     *
     * @param caption popisek
     * @return
     */
    public static JLabel label(String caption) {
        return label(caption, false);
    }
}
