package cz.zsstudanka.skola.bakakeeper.model.collections;

import cz.zsstudanka.skola.bakakeeper.connectors.BakaSQL;
import cz.zsstudanka.skola.bakakeeper.model.entities.Zak;
import cz.zsstudanka.skola.bakakeeper.model.entities.ZakonnyZastupce;
import cz.zsstudanka.skola.bakakeeper.model.interfaces.IKolekceAD;
import cz.zsstudanka.skola.bakakeeper.model.collections.Zaci;
import cz.zsstudanka.skola.bakakeeper.model.collections.ZakonniZastupci;
import cz.zsstudanka.skola.bakakeeper.model.interfaces.IUzivatelAD;

import java.sql.ResultSet;
import java.util.Iterator;

/**
 * Třída - kolekce žáků.
 * Dynamická kolekce.
 *
 * Žák je objekt uživatel Active Directory.
 * Žák je záznam na SQL Serveru.
 *@deprecated
 */
public class Trida implements IKolekceAD {

    private Integer cisloRocniku;
    private String pismenoTridy;

    private Zaci zaciTridy;

    private ZakonniZastupci zakonniZastupciTridy;

    /**
     * Konstruktor třídy pro automatizované iterace.
     *
     * @param rocnik primitivum int pro číslo ročníku
     * @param pismeno primitivum char pro písmeno třídy
     */
    public Trida(int rocnik, char pismeno) {
        this(Integer.valueOf(rocnik), String.valueOf(pismeno).toUpperCase());
    }

    public Trida(Integer rocnik, String pismeno) {
        this.cisloRocniku = rocnik;
        this.pismenoTridy = pismeno;
    }

    /**
     * Naplnění třídy žáky.
     */
    public void populate() {

        this.zaciTridy = new Zaci();
        this.zakonniZastupciTridy = new ZakonniZastupci();

        BakaSQL.getInstance().connect();

        // SQL dotaz
        String select = "SELECT dbo.zaci.INTERN_KOD,dbo.zaci.C_TR_VYK,dbo.zaci.PRIJMENI,dbo.zaci.JMENO,dbo.zaci.TRIDA,dbo.zaci.E_MAIL," // data žáka
                + "dbo.zaci_zzd.ID AS ZZ_KOD,dbo.zaci_zzd.PRIJMENI AS ZZ_PRIJMENI,dbo.zaci_zzd.JMENO AS ZZ_JMENO,dbo.zaci_zzd.TEL_MOBIL AS ZZ_TELEFON,dbo.zaci_zzd.E_MAIL AS ZZ_MAIL " // data ZZ
                + "FROM dbo.zaci LEFT JOIN dbo.zaci_zzd ON (dbo.zaci_zzd.ID = (SELECT TOP 1 dbo.zaci_zzr.ID_ZZ FROM dbo.zaci_zzr WHERE dbo.zaci_zzr.INTERN_KOD = dbo.zaci.INTERN_KOD AND (dbo.zaci_zzr.JE_ZZ = '1' AND dbo.zaci_zzr.PRIMARNI = '1'))) " // detekce primárního ZZ
                + "WHERE dbo.zaci.TRIDA LIKE '%.%' AND dbo.zaci.EVID_DO IS NULL " // žák existuje
                + "AND dbo.zaci.TRIDA = '" + this.getCisloRocniku() + "." + this.getPismeno() + "' " // jedna konkrétní třída
                + "ORDER BY dbo.zaci.PRIJMENI ASC, dbo.zaci.JMENO ASC;"; // seřazení podle abecedy

        try {
            ResultSet rs = BakaSQL.getInstance().select(select);
            while (rs.next()) {

                ZakonnyZastupce zz;

                if (rs.getString("ZZ_KOD") != null) {

                    zz = new ZakonnyZastupce(
                            rs.getString("ZZ_KOD"),
                            rs.getString("ZZ_JMENO"),
                            rs.getString("ZZ_PRIJMENI"),
                            rs.getString("ZZ_TELEFON"),
                            rs.getString("ZZ_MAIL"),
                            null
                    );
                } else {
                    zz = new ZakonnyZastupce();
                }

                this.zakonniZastupciTridy.add(zz);

                // String intern_kod, String e_mail, String jmeno, String prijmeni, String trida
                Zak zak = new Zak(
                        rs.getString("INTERN_KOD"),
                        rs.getString("C_TR_VYK"),
                        rs.getString("E_MAIL"),
                        rs.getString("JMENO"),
                        rs.getString("PRIJMENI"),
                        rs.getString("TRIDA"),
                        zz
                );

                zak.setIsSQLrecord(true);

                // přiřazení třídy k žákovi
                zak.setTrida(this);
                // přiřazení žáka do třídy
                this.zaciTridy.add(zak);

            }
        } catch (Exception e) {
            // TODO
            e.printStackTrace(System.err);
        }
    }

    /**
     * Jednoduché písmeno třídy.
     * A,B,C,D,E
     *
     * @return písmeno třídy
     */
    public String getPismeno() {
        return this.pismenoTridy.toUpperCase();
    }

    /**
     * Číslo ročníku třídy.
     * 1,2,3,4,5,6,7,8,9
     *
     * @return číslo ročníku
     */
    public String getCisloRocniku() {
        return this.cisloRocniku.toString();
    }

    /**
     * Seznam žáků třídy.
     *
     * @return kolekce žáků třídy
     */
    public Zaci getZaci() {
        return this.zaciTridy;
    }

    /**
     * Seznam zákonných zástupců žáků třídy.
     *
     * @return kolekce zákonných zástupců žáků třídy
     */
    public ZakonniZastupci getZakonniZastupci() {
        return this.zakonniZastupciTridy;
    }

    /**
     * Vygenerovaná cesta organziační jednotky v Active Directory
     *
     * @return suffix ve tvaru OU=Trida-A,OU=Rocnik-1
     */
    public String getOU() {
        return "OU=Trida-" + this.getPismeno() + ",OU=Rocnik-" + this.getCisloRocniku();
    }

    /**
     * Zobrazované jméno třídy.
     *
     * @return jméno třídy ve formátu 1.A
     */
    public String getDisplayName() {
        return this.cisloRocniku.toString() + "." + this.getPismeno();
    }

    /**
     * Jméno odpovídající bezpečnostní skupiny AD pro třídu.
     *
     * @return řetězec ve formátu Trida-1A
     */
    private String getADGroupName() {
        return "Trida-" + this.getCisloRocniku() + this.getPismeno();
    }

    /**
     * Název distribuční skupiny s kontakty zákonných zástupců.
     *
     * @return název DL zákonných zástupců v Active Directory
     */
    private String getZZDLGroupName() {
        return "DL-Rodice-" + this.getCisloRocniku() + this.getPismeno();
    }

    /**
     * Vyhledání žáka v kolekci podle sAMAccountName.
     *
     * @param ad_login kolizní řetězec
     * @return instance žáka nebo null
     */
    @Override
    public IUzivatelAD findCollisions(String ad_login) {
        return this.zaciTridy.findCollisions(ad_login);
    }

    @Override
    public Iterator<IUzivatelAD> iterator() {
        return this.zaciTridy.iterator();
    }

}
