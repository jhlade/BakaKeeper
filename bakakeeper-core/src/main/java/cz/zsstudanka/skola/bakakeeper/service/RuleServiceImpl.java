package cz.zsstudanka.skola.bakakeeper.service;

import cz.zsstudanka.skola.bakakeeper.config.SyncRule;
import cz.zsstudanka.skola.bakakeeper.config.SyncRuleAttribute;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLDAPAttributes;
import cz.zsstudanka.skola.bakakeeper.model.StudentRecord;
import cz.zsstudanka.skola.bakakeeper.model.SyncScope;
import cz.zsstudanka.skola.bakakeeper.repository.LDAPUserRepository;

import java.util.*;

/**
 * Konvergentní implementace aplikace deklarativních pravidel.
 *
 * <p>Pravidla definují CÍLOVÝ stav – jaké atributy a skupinové členství má každý
 * uživatel mít. Služba zajistí konvergenci k cílovému stavu:</p>
 * <ol>
 *   <li><b>Sestavení cílového stavu</b> – pro každého uživatele: které atributy
 *       a hodnoty, do jakých skupin patří</li>
 *   <li><b>Aplikace</b> – nastavení atributů, přidání do skupin</li>
 *   <li><b>Rekonciliace atributů</b> – vyčištění hodnot, které žádné pravidlo nepřiřazuje</li>
 *   <li><b>Rekonciliace skupin</b> – odebrání členů, kteří do skupiny dle pravidel nepatří</li>
 * </ol>
 *
 * <h3>Bezstavová rekonciliace</h3>
 * <p>Atributy extensionAttribute5–15 jsou výhradně spravovány pravidly a rekoncilují
 * se VŽDY – i když je seznam pravidel zcela prázdný ({@code rules: []}).
 * Nepotřebujeme žádnou persistentní paměť: stačí porovnat aktuální hodnoty
 * z LDAP (v {@code ruleAttributes} mapy na {@link StudentRecord}) s cílovým stavem.</p>
 *
 * <h3>Speciální hodnota CLEAR</h3>
 * <p>V pravidle lze použít hodnotu {@code "CLEAR"} pro explicitní vymazání atributu.</p>
 *
 * <h3>Chráněné atributy</h3>
 * <p>extensionAttribute1–4 se nerekoncilují
 * (ext1/ext2 jsou spravovány jinými sync fázemi, ext3/ext4 jsou rezervovány).</p>
 *
 * <p>Podporované scopes:
 * USER, INDIVIDUAL, CATEGORY, CLASS, GRADE, LEVEL, LEVEL_1, LEVEL_2,
 * ALL_STUDENTS, TEACHERS, WHOLE_SCHOOL</p>
 *
 * @author Jan Hladěna
 */
public class RuleServiceImpl implements RuleService {

    /** Speciální hodnota v pravidle – explicitní smazání atributu. */
    public static final String CLEAR = "CLEAR";

    /**
     * Atributy výhradně spravované pravidly – VŽDY se rekoncilují (ext5–ext15).
     * Pokud žádné pravidlo nepřiřazuje hodnotu, atribut se vyčistí,
     * i když je seznam pravidel zcela prázdný.
     */
    static final Set<String> RULE_EXCLUSIVE_ATTRS = Set.of(
            EBakaLDAPAttributes.EXT05.attribute(),
            EBakaLDAPAttributes.EXT06.attribute(),
            EBakaLDAPAttributes.EXT07.attribute(),
            EBakaLDAPAttributes.EXT08.attribute(),
            EBakaLDAPAttributes.EXT09.attribute(),
            EBakaLDAPAttributes.EXT10.attribute(),
            EBakaLDAPAttributes.EXT11.attribute(),
            EBakaLDAPAttributes.EXT12.attribute(),
            EBakaLDAPAttributes.EXT13.attribute(),
            EBakaLDAPAttributes.EXT14.attribute(),
            EBakaLDAPAttributes.EXT15.attribute()
    );

    /** Atributy spravované jinými sync fázemi – NIKDY se nerekoncilují přes pravidla. */
    private static final Set<String> PROTECTED_ATTRIBUTES = Set.of(
            EBakaLDAPAttributes.EXT01.attribute(),  // INTERN_KOD – StudentService, FacultyService
            EBakaLDAPAttributes.EXT02.attribute(),  // mail restriction – StudentService
            EBakaLDAPAttributes.EXT03.attribute(),  // rezervováno
            EBakaLDAPAttributes.EXT04.attribute()   // rezervováno
    );

    private final LDAPUserRepository ldapRepo;

    public RuleServiceImpl(LDAPUserRepository ldapRepo) {
        this.ldapRepo = ldapRepo;
    }

    @Override
    public List<SyncResult> applyRules(List<SyncRule> rules, List<StudentRecord> users,
                                        boolean repair, SyncProgressListener listener) {
        listener.onPhaseStart("Aplikace deklarativních pravidel");
        List<SyncResult> results = new ArrayList<>();

        // 1. Sbírat spravované atributy a skupiny ze VŠECH pravidel
        Set<String> managedAttrsByRules = collectManagedAttributes(rules);
        Set<String> managedGroups = collectManagedGroups(rules);

        listener.onProgress("Spravované atributy: " + managedAttrsByRules);
        if (!managedGroups.isEmpty()) {
            listener.onProgress("Spravované skupiny: " + managedGroups.size());
        }

        // 2. Sestavit požadovaný (cílový) stav pro každého uživatele
        //    desiredAttrs: dn → (attrName → value) – poslední pravidlo v pořadí vyhrává
        //    desiredGroupMembers: groupDn → Set<memberDn>
        Map<String, Map<String, String>> desiredAttrs = new HashMap<>();
        Map<String, Set<String>> desiredGroupMembers = new HashMap<>();

        // inicializovat skupiny (i prázdné – pro rekonciliaci)
        for (String groupDn : managedGroups) {
            desiredGroupMembers.put(groupDn, new HashSet<>());
        }

        for (SyncRule rule : rules) {
            List<StudentRecord> matching = filterByRule(rule, users);
            listener.onProgress("Pravidlo " + rule + " → " + matching.size() + " záznamů.");

            for (StudentRecord user : matching) {
                if (user.getDn() == null) continue;
                String dn = user.getDn();

                // atributy – poslední pravidlo v pořadí vyhrává při konfliktu
                if (rule.getAttributes() != null) {
                    for (SyncRuleAttribute attr : rule.getAttributes()) {
                        desiredAttrs.computeIfAbsent(dn, k -> new HashMap<>())
                                .put(attr.attribute(), attr.value());
                    }
                }

                // skupiny
                if (rule.getGroups() != null) {
                    for (String groupDn : rule.getGroups()) {
                        desiredGroupMembers.computeIfAbsent(groupDn, k -> new HashSet<>())
                                .add(dn);
                    }
                }
            }
        }

        // 3. Rekonciliace atributů – aplikace cílového stavu + čištění
        results.addAll(reconcileAttributes(users, managedAttrsByRules, desiredAttrs,
                repair, listener));

        // 4. Rekonciliace skupin
        results.addAll(reconcileGroups(managedGroups, desiredGroupMembers, repair, listener));

        // souhrn
        int ok = (int) results.stream().filter(SyncResult::isSuccess).count();
        int err = (int) results.stream().filter(r -> !r.isSuccess()).count();
        listener.onPhaseEnd("Aplikace deklarativních pravidel", ok, err);
        return results;
    }

    // ---- Rekonciliace atributů ----

    /**
     * Dvouprůchodová rekonciliace atributů pro každého uživatele:
     * <ol>
     *   <li><b>Průchod A – aplikace:</b> nastaví požadované hodnoty z pravidel
     *       (včetně CLEAR pro explicitní smazání)</li>
     *   <li><b>Průchod B – čištění:</b> projde aktuální hodnoty z LDAP (ruleAttributes)
     *       a vyčistí atributy, které žádné pravidlo nepřiřazuje:
     *       <ul>
     *         <li>extensionAttribute5–15 se čistí VŽDY (výhradně pro pravidla)</li>
     *         <li>Ostatní atributy (title) se čistí, jen pokud je nějaké pravidlo zmiňuje</li>
     *         <li>Chráněné atributy (ext1, ext2) se nečistí nikdy</li>
     *       </ul>
     *   </li>
     * </ol>
     */
    private List<SyncResult> reconcileAttributes(List<StudentRecord> users,
                                                   Set<String> managedAttrsByRules,
                                                   Map<String, Map<String, String>> desiredAttrs,
                                                   boolean repair,
                                                   SyncProgressListener listener) {
        List<SyncResult> results = new ArrayList<>();

        for (StudentRecord user : users) {
            if (user.getDn() == null) continue;
            String dn = user.getDn();
            String id = user.getInternalId() != null ? user.getInternalId() : dn;

            Map<String, String> userDesired = desiredAttrs.getOrDefault(dn, Map.of());

            // Průchod A – nastavit požadované hodnoty z pravidel
            for (var entry : userDesired.entrySet()) {
                String attrName = entry.getKey();
                String desiredValue = entry.getValue();
                if (PROTECTED_ATTRIBUTES.contains(attrName)) continue;

                EBakaLDAPAttributes resolved = resolveAttribute(attrName);
                if (resolved == null) {
                    results.add(SyncResult.error(id, "Neznámý atribut: " + attrName));
                    continue;
                }

                String currentValue = getCurrentValue(user, attrName);

                if (CLEAR.equals(desiredValue)) {
                    // explicitní smazání atributu hodnotou CLEAR
                    if (currentValue != null && !currentValue.isEmpty()) {
                        if (repair) {
                            ldapRepo.updateAttribute(dn, resolved, "");
                        }
                        results.add(SyncResult.updated(id,
                                "Pravidlo CLEAR: " + attrName
                                        + " vyčištěn (bylo: " + currentValue + ")"));
                    }
                } else if (!desiredValue.equals(currentValue)) {
                    // nastavit novou hodnotu
                    if (repair) {
                        ldapRepo.updateAttribute(dn, resolved, desiredValue);
                    }
                    results.add(SyncResult.updated(id,
                            "Pravidlo: " + attrName + " = " + desiredValue
                                    + (currentValue != null
                                    ? " (bylo: " + currentValue + ")" : "")));
                }
                // shodná hodnota → žádná akce
            }

            // Průchod B – vyčistit atributy, které žádné pravidlo nepřiřazuje
            if (user.getRuleAttributes() != null) {
                for (var entry : user.getRuleAttributes().entrySet()) {
                    String attrName = entry.getKey();
                    String currentValue = entry.getValue();

                    // přeskočit chráněné, již zpracované (desired) a prázdné
                    if (PROTECTED_ATTRIBUTES.contains(attrName)) continue;
                    if (userDesired.containsKey(attrName)) continue;
                    if (currentValue == null || currentValue.isEmpty()) continue;

                    // rekonciliovat, pokud:
                    // a) atribut je výhradně spravovaný pravidly (ext5–15), NEBO
                    // b) atribut je zmíněn v nějakém aktuálním pravidle (např. title)
                    if (!RULE_EXCLUSIVE_ATTRS.contains(attrName)
                            && !managedAttrsByRules.contains(attrName)) {
                        continue;
                    }

                    EBakaLDAPAttributes resolved = resolveAttribute(attrName);
                    if (resolved == null) continue;

                    if (repair) {
                        ldapRepo.updateAttribute(dn, resolved, "");
                    }
                    results.add(SyncResult.updated(id,
                            "Rekonciliace: " + attrName
                                    + " vyčištěn (bylo: " + currentValue + ")"));
                }
            }
        }

        return results;
    }

    // ---- Rekonciliace skupin ----

    /**
     * Porovná požadované členství ve spravovaných skupinách s aktuálním a přidá/odebere členy.
     */
    private List<SyncResult> reconcileGroups(Set<String> managedGroups,
                                               Map<String, Set<String>> desiredGroupMembers,
                                               boolean repair,
                                               SyncProgressListener listener) {
        List<SyncResult> results = new ArrayList<>();

        for (String groupDn : managedGroups) {
            Set<String> desired = desiredGroupMembers.getOrDefault(groupDn, Set.of());

            // načíst aktuální členy (i v suchém běhu – pro reportování)
            List<String> currentMembers = ldapRepo.listDirectMembers(groupDn);

            // přidat chybějící členy
            for (String memberDn : desired) {
                boolean present = currentMembers.stream()
                        .anyMatch(m -> m.equalsIgnoreCase(memberDn));
                if (!present) {
                    if (repair) {
                        ldapRepo.addToGroup(memberDn, groupDn);
                    }
                    results.add(SyncResult.updated(memberDn,
                            "Přidán do skupiny: " + shortGroupName(groupDn)));
                }
            }

            // odebrat přebytečné členy
            for (String member : currentMembers) {
                boolean shouldBeMember = desired.stream()
                        .anyMatch(d -> d.equalsIgnoreCase(member));
                if (!shouldBeMember) {
                    if (repair) {
                        ldapRepo.removeFromGroup(member, groupDn);
                    }
                    results.add(SyncResult.updated(member,
                            "Odebrán ze skupiny: " + shortGroupName(groupDn)));
                }
            }
        }

        return results;
    }

    // ---- Sběr spravovaných atributů a skupin ----

    /**
     * Vrátí množinu názvů atributů zmíněných v jakémkoli pravidle.
     */
    private Set<String> collectManagedAttributes(List<SyncRule> rules) {
        Set<String> attrs = new LinkedHashSet<>();
        for (SyncRule rule : rules) {
            if (rule.getAttributes() != null) {
                for (SyncRuleAttribute attr : rule.getAttributes()) {
                    attrs.add(attr.attribute());
                }
            }
        }
        return attrs;
    }

    /**
     * Vrátí množinu DN skupin zmíněných v jakémkoli pravidle.
     */
    private Set<String> collectManagedGroups(List<SyncRule> rules) {
        Set<String> groups = new LinkedHashSet<>();
        for (SyncRule rule : rules) {
            if (rule.getGroups() != null) {
                groups.addAll(rule.getGroups());
            }
        }
        return groups;
    }

    // ---- Pomocné metody ----

    /**
     * Zjistí aktuální hodnotu atributu z LDAP dat uloženého v StudentRecord.
     * Pro title a extensionAttribute1/2 vrací hodnoty ze standardních polí,
     * pro extensionAttribute3-15 z mapy ruleAttributes.
     */
    private String getCurrentValue(StudentRecord user, String attrName) {
        if ("extensionAttribute1".equalsIgnoreCase(attrName)) {
            return user.getInternalId();
        }
        if ("extensionAttribute2".equalsIgnoreCase(attrName)) {
            return user.isExtMailRestricted() ? "TRUE" : null;
        }
        if ("title".equalsIgnoreCase(attrName)) {
            return user.getTitle();
        }
        return user.getRuleAttribute(attrName);
    }

    /**
     * Filtruje uživatele podle pravidla.
     */
    private List<StudentRecord> filterByRule(SyncRule rule, List<StudentRecord> users) {
        return users.stream()
                .filter(u -> matchesRule(rule, u))
                .toList();
    }

    /**
     * Ověří, zda uživatel odpovídá pravidlu.
     */
    private boolean matchesRule(SyncRule rule, StudentRecord user) {
        SyncScope scope = rule.getScope();
        String match = rule.getMatch();

        return switch (scope) {
            case USER -> {
                // shoda přes sAMAccountName nebo UPN prefix (bez domény)
                if (match == null) yield false;
                String login = match.toLowerCase();
                yield (user.getSamAccountName() != null
                            && user.getSamAccountName().toLowerCase().equals(login))
                        || (user.getUpn() != null
                            && user.getUpn().toLowerCase().startsWith(login + "@"));
            }

            case INDIVIDUAL -> match != null && match.equals(user.getInternalId());

            case CATEGORY -> matchesCategory(match, user);

            case CLASS -> match != null && match.equals(user.getClassName());

            case GRADE -> {
                try {
                    yield user.getClassYear() == Integer.parseInt(match);
                } catch (NumberFormatException e) {
                    yield false;
                }
            }

            case LEVEL -> {
                // "1" = 1. stupeň (roč. 1–5), "2" = 2. stupeň (roč. 6–9)
                if ("1".equals(match)) {
                    yield user.getClassYear() >= 1 && user.getClassYear() <= 5;
                } else if ("2".equals(match)) {
                    yield user.getClassYear() >= 6 && user.getClassYear() <= 9;
                }
                yield false;
            }

            case LEVEL_1 -> user.getClassYear() >= 1 && user.getClassYear() <= 5;
            case LEVEL_2 -> user.getClassYear() >= 6 && user.getClassYear() <= 9;

            case ALL_STUDENTS -> {
                // pouze žáci (DN obsahuje OU=Zaci nebo OU=Rocnik-)
                String dn = user.getDn();
                yield dn != null && (dn.contains("OU=Zaci") || dn.contains("OU=Rocnik-"));
            }

            case TEACHERS -> {
                // učitelé (DN obsahuje OU=Ucitele)
                String dn = user.getDn();
                yield dn != null && dn.contains("OU=Ucitele");
            }

            case WHOLE_SCHOOL -> true;
        };
    }

    /**
     * Ověří, zda uživatel odpovídá kategorii podle OU v DN.
     *
     * Podporované kategorie:
     * ZAK, UCITEL, VEDENI, PROVOZ, ASISTENT, VYCHOVATELKA
     */
    private boolean matchesCategory(String match, StudentRecord user) {
        if (match == null || user.getDn() == null) return false;
        String dn = user.getDn();

        return switch (match.toUpperCase()) {
            case "ZAK" -> dn.contains("OU=Zaci") || dn.contains("OU=Rocnik-");
            case "UCITEL" -> dn.contains("OU=Ucitele");
            case "VEDENI" -> dn.contains("OU=Vedeni");
            case "PROVOZ" -> dn.contains("OU=Provoz");
            case "ASISTENT" -> dn.contains("OU=Asistenti");
            case "VYCHOVATELKA" -> dn.contains("OU=Vychovatelky");
            default -> false;
        };
    }

    /**
     * Převede název atributu na enum (pokud existuje).
     */
    private EBakaLDAPAttributes resolveAttribute(String name) {
        for (EBakaLDAPAttributes attr : EBakaLDAPAttributes.values()) {
            if (attr.attribute() != null && attr.attribute().equalsIgnoreCase(name)) {
                return attr;
            }
        }
        return null;
    }

    /**
     * Zkrátí DN skupiny na CN pro čitelnější výpis.
     */
    private String shortGroupName(String groupDn) {
        if (groupDn != null && groupDn.startsWith("CN=")) {
            int comma = groupDn.indexOf(',');
            return comma > 0 ? groupDn.substring(3, comma) : groupDn.substring(3);
        }
        return groupDn;
    }
}
