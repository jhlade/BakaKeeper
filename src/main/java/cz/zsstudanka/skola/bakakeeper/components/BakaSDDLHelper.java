package cz.zsstudanka.skola.bakakeeper.components;

import net.tirasa.adsddl.ntsd.ACE;
import net.tirasa.adsddl.ntsd.SDDL;
import net.tirasa.adsddl.ntsd.data.AceObjectFlags;
import net.tirasa.adsddl.ntsd.data.AceType;
import net.tirasa.adsddl.ntsd.utils.GUID;
import net.tirasa.adsddl.ntsd.utils.SDDLHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Rozšířená sbírka pro zjednodušenou práci se SDDL.
 *
 * @author Jan Hladěna
 */
public class BakaSDDLHelper extends SDDLHelper {

    private static final String SID_EVERYONE = "S-1-1-0";
    private static final String SID_SELF = "S-1-5-10";

    /**
     * Atribut: uživatel nemůže měnit heslo.
     * Zjednodušené ověřování pro MS AD 2016 počítá s prezencí ACE pro SELF měnit heslo. Absence povolení je automaticky
     * považována za znemožnění měnit heslo.
     *
     * @param sddl zpracovaný NT Security Descriptor
     * @return uživatel nemůže měnit heslo
     */
    public static boolean isUserCannotChangePassword(SDDL sddl) {

        // pole současný přístupových záznamů
        final List<ACE> currentObjectAllowAces = new ArrayList<>();

        for (ACE ace : sddl.getDacl().getAces()) {
            // existující ACE je allow/deny (AO, DO) + ACE je na objektu + UCP
            if (
                    (ace.getType() == AceType.ACCESS_ALLOWED_OBJECT_ACE_TYPE || ace.getType() == AceType.ACCESS_DENIED_OBJECT_ACE_TYPE)
                            && ace.getObjectFlags().getFlags().contains(AceObjectFlags.Flag.ACE_OBJECT_TYPE_PRESENT)
                            && GUID.getGuidAsString(ace.getObjectType()).equals(SDDLHelper.UCP_OBJECT_GUID)
            ) {
                if (ace.getSid().getSubAuthorities().size() == 1) {
                    if (
                        // Everyone
                            (ace.getSid().toString().equals(SID_EVERYONE) && ace.getType().equals(AceType.ACCESS_ALLOWED_OBJECT_ACE_TYPE))
                            ||
                            // NT AUTHORITY\Self
                            (ace.getSid().toString().equals(SID_SELF) && ace.getType().equals(AceType.ACCESS_ALLOWED_OBJECT_ACE_TYPE))
                    ) {
                        currentObjectAllowAces.add(ace);
                    }
                } // sub-size = 1
            } // existuje ACE
        } // for-aces

        // ve výsledku neexistují právě dva ACE s povolením změny
        return (currentObjectAllowAces.size() != 2);
    }

}
