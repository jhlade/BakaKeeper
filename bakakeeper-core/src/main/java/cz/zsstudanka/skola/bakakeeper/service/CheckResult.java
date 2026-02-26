package cz.zsstudanka.skola.bakakeeper.service;

/**
 * Výsledek kontroly jedné služby (AD, SQL, SMTP, konfigurace).
 *
 * @param service název testované služby
 * @param ok      true pokud test prošel
 * @param message popis výsledku nebo chybové hlášení
 *
 * @author Jan Hladěna
 */
public record CheckResult(String service, boolean ok, String message) {

    public static CheckResult success(String service) {
        return new CheckResult(service, true, "OK");
    }

    public static CheckResult failure(String service, String message) {
        return new CheckResult(service, false, message);
    }
}
