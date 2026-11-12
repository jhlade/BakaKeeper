package cz.zsstudanka.skola.bakakeeper.connectors;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testy mailového klienta – režim bez odesílání e-mailů (nedostupné SMTP).
 */
class BakaMailerTest {

    @Test
    void odesilaniJeVychoziPovoleno() {
        assertTrue(new BakaMailer().isEnabled());
    }

    @Test
    void odesilaniLzeVypnoutAZnovuZapnout() {
        BakaMailer mailer = new BakaMailer();
        mailer.setEnabled(false);
        assertFalse(mailer.isEnabled());
        mailer.setEnabled(true);
        assertTrue(mailer.isEnabled());
    }

    @Test
    void vypnutyMailerNeodesilaANeblokuje() {
        BakaMailer mailer = new BakaMailer();
        mailer.setEnabled(false);

        // bez SMTP konfigurace by pokus o odeslání selhal nebo čekal na spojení –
        // vypnutý klient se musí vrátit okamžitě bez výjimky
        assertTimeoutPreemptively(Duration.ofSeconds(2), () ->
                assertDoesNotThrow(() -> mailer.mail(
                        new String[]{"spravce@example.com"},
                        "Test", "Tělo zprávy", null)));
    }
}
