package cz.zsstudanka.skola.bakakeeper.model.interfaces;

/**
 * Interface záznamu v LDAP.
 *
 * @author Jan Hladěna
 */
public interface IRecordLDAP {

    /**
     * Získání plného dn atributu pro další operace.
     *
     * @return plné dn objektu
     */
    String getDN();

    /**
     * Získání současné hodnoty rozšířeného atributu č. attrNum.
     *
     * @param attrNum číslo rozšířeného atributu
     * @return hodnota
     */
    String getExtensionAttribute(Integer attrNum);

    /**
     * Okamžité nastavení rozšířeného atributu č. attrNum na hodnotu value.
     *
     * @param attrNum číslo atributu
     * @param value nová hodnota
     * @return úspěch operace
     */
    Boolean setExtensionAttribute(Integer attrNum, String value);

}
