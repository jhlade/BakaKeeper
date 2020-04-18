package cz.zsstudanka.skola.bakakeeper.model.entities;

import cz.zsstudanka.skola.bakakeeper.connectors.BakaADAuthenticator;
import cz.zsstudanka.skola.bakakeeper.model.collections.Zaci;
import cz.zsstudanka.skola.bakakeeper.settings.Settings;
import cz.zsstudanka.skola.bakakeeper.utils.BakaUtils;

import java.util.ArrayList;

/**
 * Model zákonného zástupce žáka.
 *
 *  Zákonný zástupce je objekt kontakt Active Directory.
 *  Zákonný zástupce je záznam na SQL Serveru.
 *
 *  Základní LDAP parametry:
 *  objectClass=contact
 *  showInAddressBook=FALSE
 *
 * @author Jan Hladěna
 * @deprecated
 */
public class ZakonnyZastupce {

    /** výchozí text nulového pole */
    public static final String ZZ_NULL = "NULL";
    /** výchozí ID nulového zástupce */
    public static final String ZZ_NULL_KOD = "__BEZ_ZZ__";

    /** primární klíč zákonného zástupce */
    private String zz_kod;
    /** md5 hash primárního klíče */
    private String hash_zz_kod;

    /** jméno zákonného zástupce */
    private String jmeno;
    /** příjmení zákonného zástupce */
    private String prijmeni;

    /** kontaktní údaj - telefon */
    private String telefon;
    /** kontaktní údaj - e-mail */
    private String email;

    /** seznam žáků, kterých je tento ZZ primárním zástupcem */
    private Zaci je_zastupcem = null;

    /** ladící zprávy o zákonném zástupci */
    private ArrayList<String> debugMessages;

    /** zz je pouze virtuální/nulový */
    private Boolean dummy = false;

    public ZakonnyZastupce(String zz_kod) {
        // TODO ?
    }

    /**
     * Prázdný konstruktor pro nulového zástupce.
     * Používá se pro děti bez nastavených ZZ.
     * Nemá záznam v LDAP.
     */
    public ZakonnyZastupce() {

        // prázdný zástupce
        this.zz_kod = ZakonnyZastupce.ZZ_NULL_KOD;

        this.jmeno = ZakonnyZastupce.ZZ_NULL;
        this.prijmeni = ZakonnyZastupce.ZZ_NULL;
        this.telefon = ZakonnyZastupce.ZZ_NULL;
        this.email = ZakonnyZastupce.ZZ_NULL;

        this.dummy = true;
    }

    /**
     * Konstruktor z globálního SQL dotazu (žáci LEFT JOIN zákonní zástupci)
     *
     * @param zz_kod ID zákonného zástupce v Bakalářích
     * @param jmeno křestní jméno ZZ
     * @param prijmeni příjmení ZZ
     * @param telefon telefon ZZ
     * @param email e-mail zz
     */
    public ZakonnyZastupce(String zz_kod, String jmeno, String prijmeni, String telefon, String email, Zak zak) {

        this.je_zastupcem = new Zaci();
        if (zak != null) {
            this.je_zastupcem.add(zak);
        }

        this.zz_kod = zz_kod.trim();
        this.jmeno = jmeno.trim();
        this.prijmeni = prijmeni.trim();
        this.telefon = BakaUtils.validatePhone(telefon.trim());
        this.email = BakaUtils.validateEmail(email.trim());
    }

    /**
     * Získání identifikátoru ZZ z primárního klíče Bakalářů.
     *
     * SQL pole ZZ_KOD (dbo.zaci_zzd.ID)
     * LDAP atribut description
     *
     * @return identifikátor zákonného zástupce
     */
    public String getZZ_kod() {
        return this.zz_kod;
    }

    public String getHashZZ_kod() {

        // neplatný ZZ_KOD
        if (this.zz_kod.equals(ZakonnyZastupce.ZZ_NULL_KOD)) {
            return ZakonnyZastupce.ZZ_NULL;
        }

        // prvotní vytvoření hashe
        if (this.hash_zz_kod == null) {
            this.hash_zz_kod = BakaUtils.hashMD5(this.getZZ_kod());
        }

        return this.hash_zz_kod;
    }

    /**
     * Získání křestního jména zákonného zástupce.
     *
     * SQL pole ZZ_JMENO (dbo.zaci_zzd.JMENO)
     * LDAP atribut givenName
     *
     * @return křestní jméno zákonného zástupce
     */
    public String getJmeno() {
        return this.jmeno;
    }

    /**
     * Získání příjmení zákonného zástupce.
     *
     * SQL pole ZZ_PRIJMENI (dbo.zaci_zzd.PRIJMENI)
     * LDAP atribut sn
     *
     * @return příjmení zákonného zástupce
     */
    public String getPrijmeni() {
        return this.prijmeni;
    }

    /**
     * Získání zobrazovaného jména zákonného zástupce.
     *
     * LDAP atribut displayName
     *
     * @return zobrazované jméno zákonného zástupce.
     */
    public String getDisplayName() {
        return this.prijmeni + " " + this.jmeno;
    }

    /**
     * Získání telefonního čísla zákonného zástupce.
     * Zdrojová data odpovídají mobilnímu telefonu v Bakalářích.
     *
     * SQL pole ZZ_MOBIL (dbo.zaci_zzd.TEL_MOBIL)
     * LDAP atribut homephone
     *
     * @return telefonní číslo zákonného zástupce
     */
    public String getTelefon() {
        return BakaUtils.validatePhone(this.telefon);
    }

    /**
     * Získání primární e-mailové adresy zákonného zástupce.
     * LDAP atribut mail
     *
     * @return e-mailová adresa zákonného zástupce
     */
    public String getEmail() {
        return BakaUtils.validateEmail(this.email);
    }

    /**
     * Přiřazení dítěte k zákonnému zástupci.
     *
     * @param zak instance žáka
     */
    public void pridatDite(Zak zak) {
        this.je_zastupcem.add(zak);
        zak.setZakonnyZastupce(this);
    }

    @Override
    public String toString() {

        StringBuilder zz_builder = new StringBuilder();

        zz_builder.append("Zákonný zástupce ID: " + this.getZZ_kod());
        zz_builder.append("\n");
        zz_builder.append(this.getDisplayName());
        zz_builder.append("\n");
        zz_builder.append("E-mail: " + this.getEmail());
        zz_builder.append("\n");
        if (this.getTelefon() != null) {
            zz_builder.append("Tel.: " + this.getTelefon());
            zz_builder.append("\n");
        }

        if (dummy == true) {
            zz_builder.append("[!!] TENTO KONTAKT NENÍ ZÁKONNÝM ZÁSTUPCEM ŽÁDNÉHO ŽÁKA");
        } else {

            // TODO iterátor Zaci
            // TODO využít dummy - děti bez ZZ
            Zaci deti = this.getDeti();
            if (deti != null && this.getDeti().getCount() > 0) {
                zz_builder.append("Je primárním zákonným zástupcem žáků");
                zz_builder.append("\n");

                // TODO seznam ve formě 3.A, Novák Tomáš\n9.A, Nováková Šárka
            }

        }

        return zz_builder.toString();
    }

    /**
     * Seznam žáků, jejichž primárním zákonným zástupcem tento rodič je.
     *
     * @return
     */
    public Zaci getDeti() {
        return this.je_zastupcem;
    }

    /**
     * Zadaný zákonný zástupce již existuje jako objekt třídy Kontakt v AD.
     *
     * @return
     */
    public Boolean existsAsContact() {
        return (BakaADAuthenticator.getInstance().getContactInfo(this.email) != null) ? true : false;
    }

    /**
     * Provede vytvoření objektu typu Kontakt v AD
     */
    public void createContact() {
        // TODO práce s AD
        BakaADAuthenticator.getInstance().createContact(
                Settings.getInstance().getLDAP_baseContacts(),
                this.getPrijmeni(),
                this.getPrijmeni(),
                this.getDisplayName(),
                this.getEmail(),
                this.getTelefon(),
                new String[] {}
                );
    }

    /**
     * Provede aktualizaci údajů Kontaktu v AD.
     */
    public void updateContact() {
        // TODO práce v AD
    }

    /**
     * Odebere objekt typu Kontakt z AD.
     */
    public void removeContact() {
        // TODO práce v AD
    }

    private void listDL() {

    }

    private void updateDL() {

    }

    private void removeFromDL() {

    }

    private void addToDL() {
        // TODO žák -> třída
    }
}
