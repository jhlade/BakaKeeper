package cz.zsstudanka.skola.bakakeeper.service;

import cz.zsstudanka.skola.bakakeeper.config.AppConfig;
import cz.zsstudanka.skola.bakakeeper.connectors.LDAPConnector;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLDAPAttributes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementace kontroly a opravy základní AD struktury.
 *
 * Zajišťuje existenci organizačních jednotek, bezpečnostních skupin žáků
 * a distribučních seznamů rodičů/třídních učitelů nutných pro synchronizaci.
 *
 * Hierarchie skupin odpovídá produkční struktuře (zsstu.local):
 * <pre>
 *   OU=Skupiny,OU=Skola
 *   ├── OU=Uzivatele
 *   │   └── CN=Skupina-Zaci                    [security]
 *   ├── OU=Zaci
 *   │   ├── CN=Zaci-Vsichni     → Skupina-Zaci [security]
 *   │   ├── CN=Zaci-Stupen-1/2  → Zaci-Vsichni [security]
 *   │   ├── CN=Zaci-Rocnik-1..9 → Stupen       [security]
 *   │   └── CN=Zaci-Trida-*     → Rocnik       [security]
 *   └── OU=Distribucni
 *       ├── CN=Rodice-Vsichni / Stupen / Rocnik / Trida [distribution]
 *       └── CN=Ucitele-Tridni / Stupen / Rocnik / Trida [distribution]
 * </pre>
 *
 * Převzato z odstraněné třídy {@code routines.Structure} (commit 77a3135).
 *
 * @author Jan Hladěna
 */
public class StructureServiceImpl implements StructureService {

    private static final String LIT_TRUE = EBakaLDAPAttributes.BK_LITERAL_TRUE.value();
    private static final String LIT_FALSE = EBakaLDAPAttributes.BK_LITERAL_FALSE.value();

    private final AppConfig config;
    private final LDAPConnector ldap;

    public StructureServiceImpl(AppConfig config, LDAPConnector ldap) {
        this.config = config;
        this.ldap = ldap;
    }

    @Override
    public List<SyncResult> checkAndRepairStructure(boolean repair, SyncProgressListener listener) {
        listener.onPhaseStart("Kontrola AD struktury");
        List<SyncResult> results = new ArrayList<>();

        // 1) Kontrola/oprava organizačních jednotek
        results.addAll(checkOUs(repair, listener));

        // 2) Kontrola/oprava skupin a distribučních seznamů
        results.addAll(checkGroups(repair, listener));

        int ok = (int) results.stream().filter(SyncResult::isSuccess).count();
        int err = (int) results.stream().filter(r -> !r.isSuccess()).count();
        listener.onPhaseEnd("Kontrola AD struktury", ok, err);

        return results;
    }

    // ========================================================================
    // Kontrola organizačních jednotek
    // ========================================================================

    /**
     * Kontrola a oprava existence všech OU kontejnerů.
     */
    private List<SyncResult> checkOUs(boolean repair, SyncProgressListener listener) {
        List<SyncResult> results = new ArrayList<>();

        // definice OU – pořadí odpovídá hierarchii (rodiče před potomky)
        List<String[]> ous = buildOUDefinitions();

        for (String[] ou : ous) {
            String ouDn = ou[0];
            String label = ou[1];

            listener.onProgress("Kontrola OU: " + label);
            int count = ldap.checkOU(ouDn);

            if (count >= 0) {
                // OU existuje
                results.add(SyncResult.noChange(ouDn));
            } else if (repair) {
                // OU neexistuje – pokus o vytvoření
                String name = ouDn.split(",")[0]; // "OU=Xyz"
                String base = ouDn.substring(name.length() + 1); // zbytek DN
                // extrakce samotného jména z "OU=Xyz"
                String ouName = name.substring(3); // odstraní "OU="

                listener.onProgress("Vytvářím OU: " + label);
                ldap.createOU(ouName, base);

                // ověření
                if (ldap.checkOU(ouDn) >= 0) {
                    SyncResult r = SyncResult.created(ouDn, "Vytvořena OU " + label);
                    results.add(r);
                    listener.onResult(r);
                } else {
                    SyncResult r = SyncResult.error(ouDn, "Nepodařilo se vytvořit OU " + label);
                    results.add(r);
                    listener.onResult(r);
                }
            } else {
                // OU neexistuje, jen kontrola
                SyncResult r = SyncResult.error(ouDn, "Chybí OU " + label);
                results.add(r);
                listener.onResult(r);
            }
        }

        return results;
    }

    /**
     * Definice všech OU kontejnerů v pořadí hierarchie.
     *
     * @return seznam dvojic [DN, popisek]
     */
    private List<String[]> buildOUDefinitions() {
        List<String[]> ous = new ArrayList<>();

        ous.add(new String[]{config.getLdapBaseFaculty(), "Zaměstnanci (globální)"});
        ous.add(new String[]{config.getLdapBaseStudents(), "Žáci (globální)"});

        // ročníky 1–9 s třídami A–E
        for (int r = 1; r <= 9; r++) {
            String rocnikOu = "OU=Rocnik-" + r + "," + config.getLdapBaseStudents();
            ous.add(new String[]{rocnikOu, "Žáci " + r + ". ročníku"});
            for (char t = 'A'; t <= 'E'; t++) {
                ous.add(new String[]{"OU=Trida-" + t + "," + rocnikOu,
                        "Žáci třídy " + r + "." + t});
            }
        }

        ous.add(new String[]{config.getLdapBaseAlumni(), "Vyřazení žáci"});
        ous.add(new String[]{config.getLdapBaseStudentGroups(), "Skupiny žáků"});
        ous.add(new String[]{config.getLdapBaseGlobalGroups(), "Globální skupiny uživatelů"});
        ous.add(new String[]{config.getLdapBaseContacts(), "Kontakty (zákonní zástupci)"});
        ous.add(new String[]{config.getLdapBaseDistributionLists(), "Distribuční seznamy"});

        return ous;
    }

    // ========================================================================
    // Kontrola skupin a distribučních seznamů
    // ========================================================================

    /**
     * Kontrola a oprava existence všech bezpečnostních skupin a distribučních seznamů.
     */
    private List<SyncResult> checkGroups(boolean repair, SyncProgressListener listener) {
        List<SyncResult> results = new ArrayList<>();

        // definice skupin – pořadí respektuje hierarchii (rodiče před potomky)
        List<GroupDef> groups = buildGroupDefinitions();

        for (GroupDef gr : groups) {
            String groupId = "CN=" + gr.cn + "," + gr.ou;

            listener.onProgress("Kontrola skupiny: " + gr.displayName);

            @SuppressWarnings("rawtypes")
            Map info = ldap.getGroupInfo(gr.cn, gr.ou);

            if (info != null && !info.isEmpty()) {
                // skupina existuje
                results.add(SyncResult.noChange(groupId));

                // podrobná kontrola atributů
                if (repair) {
                    results.addAll(repairGroupAttributes(gr, info, listener));
                }
            } else if (repair) {
                // skupina neexistuje – vytvoření
                listener.onProgress("Vytvářím skupinu: " + gr.displayName);

                HashMap<String, String> data = new HashMap<>();
                data.put(EBakaLDAPAttributes.GT_GENERAL.attribute(), gr.groupType);
                data.put(EBakaLDAPAttributes.MAIL.attribute(), gr.mail);
                data.put(EBakaLDAPAttributes.NAME_DISPLAY.attribute(), gr.displayName);
                data.put(EBakaLDAPAttributes.DESCRIPTION.attribute(), gr.description);
                data.put(EBakaLDAPAttributes.MSXCH_GAL_HIDDEN.attribute(), gr.hideFromGal);
                data.put(EBakaLDAPAttributes.MSXCH_REQ_AUTH.attribute(), gr.requireAuth);

                String[] memberOf = (gr.parentGroupDn != null)
                        ? new String[]{gr.parentGroupDn}
                        : new String[]{};

                ldap.createGroup(gr.cn, gr.ou, memberOf, data);

                // ověření
                @SuppressWarnings("rawtypes")
                Map verify = ldap.getGroupInfo(gr.cn, gr.ou);
                if (verify != null && !verify.isEmpty()) {
                    SyncResult r = SyncResult.created(groupId, "Vytvořena skupina " + gr.displayName);
                    results.add(r);
                    listener.onResult(r);
                } else {
                    SyncResult r = SyncResult.error(groupId,
                            "Nepodařilo se vytvořit skupinu " + gr.displayName);
                    results.add(r);
                    listener.onResult(r);
                }
            } else {
                // skupina neexistuje, jen kontrola
                SyncResult r = SyncResult.error(groupId, "Chybí skupina " + gr.displayName);
                results.add(r);
                listener.onResult(r);
            }
        }

        return results;
    }

    /**
     * Kontrola a oprava atributů existující skupiny.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private List<SyncResult> repairGroupAttributes(GroupDef gr, Map info, SyncProgressListener listener) {
        List<SyncResult> results = new ArrayList<>();

        Map<String, Object> attrs = (Map<String, Object>) info.get(0);
        if (attrs == null) return results;

        String groupId = "CN=" + gr.cn + "," + gr.ou;

        // atributy ke kontrole: [enum, očekávaná hodnota]
        Object[][] checks = {
                {EBakaLDAPAttributes.MAIL, gr.mail},
                {EBakaLDAPAttributes.NAME_DISPLAY, gr.displayName},
                {EBakaLDAPAttributes.MSXCH_GAL_HIDDEN, gr.hideFromGal},
                {EBakaLDAPAttributes.MSXCH_REQ_AUTH, gr.requireAuth},
                {EBakaLDAPAttributes.GT_GENERAL, gr.groupType},
                {EBakaLDAPAttributes.DESCRIPTION, gr.description},
        };

        for (Object[] check : checks) {
            EBakaLDAPAttributes attr = (EBakaLDAPAttributes) check[0];
            String expected = (String) check[1];

            if (expected == null) continue;

            Object current = attrs.get(attr.attribute());
            String currentStr = (current != null) ? current.toString() : null;

            if (!expected.equalsIgnoreCase(currentStr)) {
                ldap.setGroupInfo(gr.ou, gr.cn, attr, expected);
                SyncResult r = SyncResult.updated(groupId,
                        "Opraven atribut " + attr.attribute());
                results.add(r);
                listener.onResult(r);
            }
        }

        // kontrola nadřazené skupiny (memberOf)
        if (gr.parentGroupDn != null) {
            Object memberOf = attrs.get(EBakaLDAPAttributes.MEMBER_OF.attribute());
            String memberOfStr = (memberOf != null) ? memberOf.toString() : "";

            if (!memberOfStr.toLowerCase().contains(gr.parentGroupDn.toLowerCase())) {
                // přidat do nadřazené skupiny
                String dn = (String) attrs.get(EBakaLDAPAttributes.DN.attribute());
                if (dn != null) {
                    ldap.addObjectToGroup(dn, gr.parentGroupDn);
                    SyncResult r = SyncResult.updated(groupId,
                            "Přidáno do nadřazené skupiny " + gr.parentGroupDn);
                    results.add(r);
                    listener.onResult(r);
                }
            }
        }

        return results;
    }

    /**
     * Definice všech skupin a distribučních seznamů v pořadí hierarchie.
     * Pořadí je klíčové – rodičovské skupiny musí být definovány dříve než potomci.
     *
     * @return uspořádaný seznam definic skupin
     */
    private List<GroupDef> buildGroupDefinitions() {
        List<GroupDef> groups = new ArrayList<>();

        String studentGroups = config.getLdapBaseStudentGroups();
        String globalGroups = config.getLdapBaseGlobalGroups();
        String dl = config.getLdapBaseDistributionLists();
        String mailDomain = config.getMailDomain();
        String sec = EBakaLDAPAttributes.GT_SECURITY.value();
        String dist = EBakaLDAPAttributes.GT_DISTRIBUTION.value();

        // --- Globální skupina žáků (kořen hierarchie) ---
        groups.add(new GroupDef("Skupina-Zaci", globalGroups,
                null,
                "zaci-vsichni@" + mailDomain, "Skupina – všichni žáci",
                LIT_TRUE, LIT_TRUE, sec,
                "Globální skupina všech žáků školy"));

        // --- Bezpečnostní skupina všech žáků ---
        groups.add(new GroupDef("Zaci-Vsichni", studentGroups,
                "CN=Skupina-Zaci," + globalGroups,
                "zaci@" + mailDomain, "Všichni žáci školy",
                LIT_TRUE, LIT_TRUE, sec,
                "Všichni žáci školy"));

        // --- Distribuční seznamy – kořenové ---
        groups.add(new GroupDef("Rodice-Vsichni", dl,
                null,
                "rodice-cela-skola@" + mailDomain, "Všichni rodiče",
                LIT_TRUE, LIT_TRUE, dist,
                "Všichni rodiče všech žáků"));

        groups.add(new GroupDef("Ucitele-Tridni", dl,
                null,
                "ucitele-tridni@" + mailDomain, "Všichni třídní učitelé",
                LIT_TRUE, LIT_TRUE, dist,
                "Všichni třídní učitelé"));

        // --- Stupně 1 a 2 ---
        for (int s = 1; s <= 2; s++) {
            groups.add(new GroupDef("Zaci-Stupen-" + s, studentGroups,
                    "CN=Zaci-Vsichni," + studentGroups,
                    "zaci-stupen-" + s + "@" + mailDomain,
                    "Žáci - " + s + ". stupeň",
                    LIT_TRUE, LIT_TRUE, sec,
                    "Žáci - " + s + ". stupeň"));

            groups.add(new GroupDef("Rodice-Stupen-" + s, dl,
                    "CN=Rodice-Vsichni," + dl,
                    "rodice-stupen-" + s + "@" + mailDomain,
                    "Rodiče žáků " + s + ". stupně",
                    LIT_TRUE, LIT_TRUE, dist,
                    "Rodiče žáků - " + s + ". stupeň"));

            groups.add(new GroupDef("Ucitele-Tridni-Stupen-" + s, dl,
                    "CN=Ucitele-Tridni," + dl,
                    "tridni-stupen-" + s + "@" + mailDomain,
                    "Třídní učitelé " + s + ". stupně",
                    LIT_TRUE, LIT_TRUE, dist,
                    "Třídní učitelé - " + s + ". stupeň"));
        }

        // --- Ročníky 1–9 ---
        for (int r = 1; r <= 9; r++) {
            String stupen = (r <= 5) ? "1" : "2";

            groups.add(new GroupDef("Zaci-Rocnik-" + r, studentGroups,
                    "CN=Zaci-Stupen-" + stupen + "," + studentGroups,
                    "zaci-rocnik-" + r + "@" + mailDomain,
                    "Žáci - " + r + ". ročník",
                    LIT_FALSE, LIT_TRUE, sec,
                    "Žáci - " + r + ". ročníku"));

            groups.add(new GroupDef("Rodice-Rocnik-" + r, dl,
                    "CN=Rodice-Stupen-" + stupen + "," + dl,
                    "rodice-rocnik-" + r + "@" + mailDomain,
                    "Rodiče žáků " + r + ". ročníku",
                    LIT_TRUE, LIT_TRUE, dist,
                    "Rodiče žáků " + r + ". ročníku"));

            groups.add(new GroupDef("Ucitele-Tridni-Rocnik-" + r, dl,
                    "CN=Ucitele-Tridni-Stupen-" + stupen + "," + dl,
                    "tridni-rocnik-" + r + "@" + mailDomain,
                    "Třídní učitelé " + r + ". ročníku",
                    LIT_TRUE, LIT_TRUE, dist,
                    "Třídní učitelé " + r + ". ročníku"));

            // --- Třídy A–E v rámci ročníku ---
            for (char t = 'A'; t <= 'E'; t++) {
                String classId = "" + r + t;

                groups.add(new GroupDef("Zaci-Trida-" + classId, studentGroups,
                        "CN=Zaci-Rocnik-" + r + "," + studentGroups,
                        ("zaci-" + classId + "@" + mailDomain).toLowerCase(),
                        "Žáci " + r + "." + t,
                        LIT_FALSE, LIT_TRUE, sec,
                        "Žáci třídy " + r + "." + t));

                groups.add(new GroupDef("Rodice-Trida-" + classId, dl,
                        "CN=Rodice-Rocnik-" + r + "," + dl,
                        ("rodice-trida-" + classId + "@" + mailDomain).toLowerCase(),
                        "Rodiče " + r + "." + t,
                        LIT_TRUE, LIT_TRUE, dist,
                        "Rodiče žáků třídy " + r + "." + t));

                groups.add(new GroupDef("Ucitele-Tridni-" + classId, dl,
                        "CN=Ucitele-Tridni-Rocnik-" + r + "," + dl,
                        ("tridni-" + classId + "@" + mailDomain).toLowerCase(),
                        "Třídní učitel " + r + "." + t,
                        LIT_TRUE, LIT_TRUE, dist,
                        "Třídní učitel " + r + "." + t));
            }
        }

        return groups;
    }

    // ========================================================================
    // Datový model pro definici skupiny
    // ========================================================================

    /**
     * Definice jedné skupiny/distribučního seznamu.
     *
     * @param cn           common name skupiny
     * @param ou           cílová OU (plné DN)
     * @param parentGroupDn DN nadřazené skupiny (null = žádná)
     * @param mail         e-mailová adresa skupiny
     * @param displayName  zobrazovaný název
     * @param hideFromGal  skrytí v Global Address List (TRUE/FALSE)
     * @param requireAuth  vyžadovat autentizaci při odesílání (TRUE/FALSE)
     * @param groupType    typ skupiny (GT_SECURITY / GT_DISTRIBUTION hodnota)
     * @param description  popis skupiny
     */
    private record GroupDef(
            String cn,
            String ou,
            String parentGroupDn,
            String mail,
            String displayName,
            String hideFromGal,
            String requireAuth,
            String groupType,
            String description
    ) {}
}
