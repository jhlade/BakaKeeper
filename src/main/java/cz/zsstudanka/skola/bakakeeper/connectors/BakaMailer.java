package cz.zsstudanka.skola.bakakeeper.connectors;

import cz.zsstudanka.skola.bakakeeper.settings.Settings;
import cz.zsstudanka.skola.bakakeeper.settings.Version;

import java.util.Date;
import java.util.Properties;
import javax.mail.Message;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.Authenticator;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;

/**
 * E-mailový konektor a klient.
 * Komponuje reporty a odesílá je na adresu IT oddělení.
 *
 * Očekává se použití Office 365, nebo obdobné nastavení lokálního Microsoft Exchange serveru.
 *
 * @author Jan Hladěna
 */
public class BakaMailer {

    /** TCP port SMTP serveru */
    static final String SMTP_PORT = "587";

    /** singleton mailového klienta */
    private static BakaMailer instance = null;

    private Session session;

    public BakaMailer() {
    }

    /**
     * Singleton mailového klienta
     *
     * @return instance mailového klienta
     */
    public static BakaMailer getInstance() {
        if (BakaMailer.instance == null) {
            BakaMailer.instance = new BakaMailer();
        }

        return BakaMailer.instance;
    }

    /**
     * Autentizace proti SMTP serveru.
     * Očekává se O365, nebo obdobné nastavení Microsoft Exchange serveru.
     */
    private void authenticate() {

        Properties props = new Properties();

        props.put("mail.smtp.host", Settings.getInstance().getSMTP_host());
        props.put("mail.smtp.port", SMTP_PORT);
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");

        Authenticator auth = new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(Settings.getInstance().getSMTP_user(), Settings.getInstance().getSMTP_pass());
            }
        };

        this.session = Session.getInstance(props, auth);
    }

    /**
     * Odeslání zprávy správci systému.
     *
     * @param subject předmět zprávy
     * @param message zpráva
     */
    public void mail(String subject, String message) {

        // TLS autentizace
        this.authenticate();

        if (Settings.getInstance().beVerbose()) {
            System.out.println("[ INFO ] Začíná odesílání e-mailu.");
        }

        try {
            MimeMessage msg = new MimeMessage(this.session);

            msg.addHeader("format", "flowed");
            msg.addHeader("Content-type", "text/plain; charset=UTF-8");
            msg.addHeader("Content-Transfer-Encoding", "8bit");
            msg.setHeader("X-Mailer", Version.getInstance().getTag());

            msg.setFrom(new InternetAddress(Settings.getInstance().getSMTP_user(), Version.getInstance().getName()));
            msg.setSentDate(new Date());
            msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(Settings.getInstance().getAdminMail(), false));

            msg.setSubject(subject, "UTF-8");
            msg.setText(message, "UTF-8");

            Transport.send(msg);

        } catch (Exception e) {

            if (Settings.getInstance().beVerbose()) {
                System.err.println("[ CHYBA ] Došlo k chybě při odesílání informačního e-mailu.");
            }

            if (Settings.getInstance().debugMode()) {
                System.err.println("[ CHYBA ] " + e.getLocalizedMessage());
                e.printStackTrace(System.err);
            }
        }
    }

    /**
     * Ověření funkčnosti SMTP spojení.
     *
     * @return výsledek testu
     */
    public Boolean testSMTP() {

        this.authenticate();

        boolean test;

        try {
            Transport transport = session.getTransport("smtp");
            transport.connect(Settings.getInstance().getSMTP_host(), Integer.parseInt(SMTP_PORT), Settings.getInstance().getSMTP_user(), Settings.getInstance().getSMTP_pass());
            transport.close();

            test = true;
        } catch (Exception e) {

            if (Settings.getInstance().beVerbose()) {
                System.err.println("[ CHYBA ] " + e.getLocalizedMessage());
            }

            if (Settings.getInstance().debugMode()) {
                e.printStackTrace(System.err);
            }

            test = false;
        }

        return test;
    }

}
