package cz.zsstudanka.skola.bakakeeper.connectors;

import cz.zsstudanka.skola.bakakeeper.components.ReportManager;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaPorts;
import cz.zsstudanka.skola.bakakeeper.settings.Settings;
import cz.zsstudanka.skola.bakakeeper.settings.Version;
import cz.zsstudanka.skola.bakakeeper.utils.BakaUtils;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import java.util.Date;
import java.util.Properties;

/**
 * E-mailový konektor a klient.
 * Komponuje reporty a odesílá je na požadované adresy.
 *
 * Očekává se použití Office 365, nebo obdobné nastavení lokálního Microsoft Exchange serveru.
 *
 * @author Jan Hladěna
 */
public class BakaMailer {

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
     * Port, autentizace a STARTTLS jsou konfigurovatelné v settings.yml.
     */
    private void authenticate() {

        String smtpPort = Integer.toString(Settings.getInstance().getSmtpPort());
        boolean smtpAuth = Settings.getInstance().isSmtpAuth();
        boolean smtpStarttls = Settings.getInstance().isSmtpStarttls();

        Properties props = new Properties();

        props.put("mail.smtp.host", Settings.getInstance().getSMTP_host());
        props.put("mail.smtp.port", smtpPort);
        props.put("mail.smtp.auth", Boolean.toString(smtpAuth));
        props.put("mail.smtp.starttls.enable", Boolean.toString(smtpStarttls));

        if (smtpAuth) {
            Authenticator auth = new Authenticator() {
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(Settings.getInstance().getSMTP_user(), Settings.getInstance().getSMTP_pass());
                }
            };
            this.session = Session.getInstance(props, auth);
        } else {
            this.session = Session.getInstance(props);
        }
    }


    /**
     * Odeslání informační zprávy správci systému.
     *
     * @param subject předmět zprávy
     * @param message zpráva
     */
    public void mail(String subject, String message) {
        this.mail(new String[]{Settings.getInstance().getAdminMail()}, subject, message, null);
    }

    /**
     * Obecné odeslání zprávy.
     *
     * @param recipients pole se seznamem příjemců
     * @param subject předmět zprávy
     * @param message tělo zprávy
     * @param attachments pole se seznamem cest k souborům, které budou vloženy jako přílohy
     */
    public void mail(String[] recipients, String subject, String message, String[] attachments) {

        // TLS autentizace
        this.authenticate();

        StringBuilder messageBuilder = new StringBuilder();
        messageBuilder.append(message);
        // podpis
        messageBuilder.append("\n\n---\n");
        messageBuilder.append(Version.getInstance().getTag() + "\n" + Settings.getInstance().systemInfoTag() + "\n\n");

        if (Settings.getInstance().beVerbose()) {
            System.out.println("[ INFO ] Začíná odesílání e-mailu.");
        }

        try {
            MimeMessage msg = new MimeMessage(this.session);

            msg.addHeader("Content-Transfer-Encoding", "8bit");
            msg.setHeader("X-Mailer", Version.getInstance().getTag());

            msg.setFrom(new InternetAddress(Settings.getInstance().getSMTP_user(), Version.getInstance().getName()));
            msg.setSentDate(new Date());
            for (String rcpt : recipients) {
                msg.addRecipients(Message.RecipientType.TO, InternetAddress.parse(rcpt, false));
            }

            msg.setSubject(subject, "UTF-8");

            // přílohy
            if (attachments == null) {

                msg.addHeader("format", "flowed");
                msg.addHeader("Content-type", "text/plain; charset=UTF-8");

                msg.setText(messageBuilder.toString(), "UTF-8");
            } else {
                System.setProperty("mail.mime.charset", "utf-8");
                msg.addHeader("Conent-type", "multipart/mixed");

                // tělo zprávy
                BodyPart messageBodyPart = new MimeBodyPart();
                messageBodyPart.setHeader("Content-type", "text/plain; charset=UTF-8");
                messageBodyPart.setText(messageBuilder.toString());

                Multipart multipart = new MimeMultipart();
                multipart.addBodyPart(messageBodyPart);

                // přílohy
                for (String att : attachments) {
                    MimeBodyPart filePart = new MimeBodyPart();
                    DataSource source = new FileDataSource(att);

                    filePart.setDataHandler(new DataHandler(source));
                    filePart.setHeader("Content-type", source.getContentType());
                    filePart.setFileName(BakaUtils.fileBaseName(att));

                    multipart.addBodyPart(filePart);
                }

                msg.setContent(multipart);
            }

            Transport.send(msg);

        } catch (Exception e) {
            ReportManager.handleException("Došlo k chybě při odesílání e-mailu.", e);
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
            int port = Settings.getInstance().getSmtpPort();
            if (Settings.getInstance().isSmtpAuth()) {
                transport.connect(Settings.getInstance().getSMTP_host(), port, Settings.getInstance().getSMTP_user(), Settings.getInstance().getSMTP_pass());
            } else {
                transport.connect(Settings.getInstance().getSMTP_host(), port, null, null);
            }
            transport.close();

            test = true;
        } catch (Exception e) {
            ReportManager.handleException("Došlo k chybě během připojování k SMTP serveru.", e, true);
            test = false;
        }

        return test;
    }

}
