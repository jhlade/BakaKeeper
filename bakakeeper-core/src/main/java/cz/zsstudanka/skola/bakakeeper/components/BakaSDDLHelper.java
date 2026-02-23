package cz.zsstudanka.skola.bakakeeper.components;

import net.tirasa.adsddl.ntsd.ACE;
import net.tirasa.adsddl.ntsd.SDDL;
import net.tirasa.adsddl.ntsd.SID;
import net.tirasa.adsddl.ntsd.data.AceObjectFlags;
import net.tirasa.adsddl.ntsd.data.AceRights;
import net.tirasa.adsddl.ntsd.data.AceType;
import net.tirasa.adsddl.ntsd.utils.GUID;
import net.tirasa.adsddl.ntsd.utils.NumberFacility;
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
     * Zjednodušené ověřování pro MS AD 2016, empiricky odpozorováno z chování RSAT GUI. Prezence OD ACE pro obě SID
     * zaručuje UPC, nicméně absence OD a prezence OA pouze pro Everyone se zdá být postačující pro !UPC.
     *
     * English note:
     * There seems to be some weird behavior when using AD Users and Computers GUI (tested on Server 2016). Freshly
     * created normal user accounts have both OA ACEs for UPC set. Using checkbox in Account tab for 'User cannot change
     * password' deletes them and creates corresponding OD ACEs. Unchecking this correctly deletes OD ACEs, however
     * only one OA ACE - for SID Everyone - is created. SID Self ACE for UPC is simply not present.
     *
     * @param sddl zpracovaný NT Security Descriptor
     * @return uživatel nemůže měnit heslo
     */
    public static boolean isUserCannotChangePassword(SDDL sddl) {

        // pole současný přístupových záznamů
        final List<ACE> currentObjectAllowAces = new ArrayList<>();
        final List<ACE> currentObjectDenyAces = new ArrayList<>();

        for (ACE ace : sddl.getDacl().getAces()) {
            // existující ACE je allow/deny (AO, DO) + ACE je na objektu + UCP
            if (
                    (ace.getType() == AceType.ACCESS_ALLOWED_OBJECT_ACE_TYPE || ace.getType() == AceType.ACCESS_DENIED_OBJECT_ACE_TYPE) // AO/DO
                            &&
                            ace.getObjectFlags().getFlags().contains(AceObjectFlags.Flag.ACE_OBJECT_TYPE_PRESENT) // ACE present
                            && GUID.getGuidAsString(ace.getObjectType()).equals(SDDLHelper.UCP_OBJECT_GUID) // UCP
                            && ace.getSid().getSubAuthorities().size() == 1 // jedna sub-autorita
            ) {
                if (ace.getType().equals(AceType.ACCESS_ALLOWED_OBJECT_ACE_TYPE)) {
                    currentObjectAllowAces.add(ace);
                }

                if (ace.getType().equals(AceType.ACCESS_DENIED_OBJECT_ACE_TYPE)) {
                    currentObjectDenyAces.add(ace);
                }

            } // existuje ACE
        } // for-aces

        // obecně existuje převaha odepírajících ACE
        return (currentObjectDenyAces.size() > currentObjectAllowAces.size());
    }


    /**
     * Modifikace SDDL pro odepření/povolení změny hesla uživatelem. Použití následujícím způsobem může vyvolat
     * hlášení 'The permissions on <uživatel> are incorrectly ordered'. Model zabezpečení Windows v DACL vyžaduje přesné
     * pořadí bezprostředních oprávnění před zděděnými, pro zachování správného chování by neměly být účty více
     * manuálně modifikovány. Pokud je použita samoobslužná změna hesla pod účtem s oprávněním změn hesla nad
     * celými OU, pak je možné tento proces obejít.
     *
     * Metoda je přepsána, původní SDDLHelper má zaměněné SID. / The original SDDLHelper incorrectly specifies SIDs
     * (assigns S-1-1-0 to Self and S-1-5-10 to Everyone), so new ACE with invalid SID S-1-327680-10 is created instead.
     *
     * @param sddl původní SDDL
     * @param cannot uživatel nemůže měnit heslo
     * @return nové SDDL
     */
    public static SDDL userCannotChangePassword(final SDDL sddl, final Boolean cannot) {

        // typ ACE
        final AceType type = cannot ? AceType.ACCESS_DENIED_OBJECT_ACE_TYPE : AceType.ACCESS_ALLOWED_OBJECT_ACE_TYPE;

        ACE everyone = null;
        ACE self = null;

        // současná ACE
        for (ACE ace : sddl.getDacl().getAces()) {
            // existující ACE je allow/deny (AO, DO) + ACE je na objektu + UCP
            if (
                    (ace.getType() == AceType.ACCESS_ALLOWED_OBJECT_ACE_TYPE || ace.getType() == AceType.ACCESS_DENIED_OBJECT_ACE_TYPE) // AO/DO
                            &&
                            ace.getObjectFlags().getFlags().contains(AceObjectFlags.Flag.ACE_OBJECT_TYPE_PRESENT) // ACE present
                            && GUID.getGuidAsString(ace.getObjectType()).equals(SDDLHelper.UCP_OBJECT_GUID) // UCP
                            && ace.getSid().getSubAuthorities().size() == 1 // jedna sub-autorita
            ) {
                // Everyone
                if (everyone == null && ace.getSid().toString().equals(SID_EVERYONE)) {
                    everyone = ace;
                }

                // Self
                if (self == null && ace.getSid().toString().equals(SID_SELF)) {
                    self = ace;
                }

            } // existuje ACE

        }

        // nové ACE pro Everyone
        if (everyone == null) {
            everyone = ACE.newInstance(type);
            everyone.setObjectFlags(new AceObjectFlags(AceObjectFlags.Flag.ACE_OBJECT_TYPE_PRESENT));
            everyone.setRights(new AceRights().addOjectRight(AceRights.ObjectRight.CR));

            // SID = S-1-1-0
            SID sid = SID.newInstance(NumberFacility.getBytes(0x1, 6)); // S-1-1-
            sid.addSubAuthority(NumberFacility.getBytes(0x0, 4)); // -0
            self.setSid(sid);

            // nastavení ACE v SDDL
            sddl.getDacl().getAces().add(everyone);
        } else {
            // pouze změna typu
            everyone.setType(type);
        }

        // nové ACE pro Self
        if (self == null) {
            self = ACE.newInstance(type);
            self.setObjectFlags(new AceObjectFlags(AceObjectFlags.Flag.ACE_OBJECT_TYPE_PRESENT));
            self.setRights(new AceRights().addOjectRight(AceRights.ObjectRight.CR));

            // SID = S-1-5-10
            SID sid = SID.newInstance(NumberFacility.getBytes(0x5, 6)); // S-1-5-
            sid.addSubAuthority(NumberFacility.getBytes(0xa, 4)); // -10
            self.setSid(sid);

            // nastavení ACE v SDDL
            sddl.getDacl().getAces().add(self);
        } else {
            // pouze změna typu
            self.setType(type);
        }

        return sddl;
    }

}
