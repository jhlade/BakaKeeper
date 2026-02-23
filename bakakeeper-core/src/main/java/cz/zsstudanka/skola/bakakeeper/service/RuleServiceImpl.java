package cz.zsstudanka.skola.bakakeeper.service;

import cz.zsstudanka.skola.bakakeeper.config.SyncRule;
import cz.zsstudanka.skola.bakakeeper.config.SyncRuleAttribute;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLDAPAttributes;
import cz.zsstudanka.skola.bakakeeper.model.StudentRecord;
import cz.zsstudanka.skola.bakakeeper.model.SyncScope;
import cz.zsstudanka.skola.bakakeeper.repository.LDAPUserRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementace aplikace deklarativních pravidel.
 *
 * Podporované scopes:
 * <ul>
 *   <li>USER – jednotlivec dle přihlašovacího jména (sAMAccountName / UPN prefix)</li>
 *   <li>INDIVIDUAL – jednotlivec dle interního ID</li>
 *   <li>CATEGORY – kategorie uživatele (ZAK, UCITEL, VEDENI, PROVOZ, ASISTENT, VYCHOVATELKA)</li>
 *   <li>CLASS – třída (např. "6.A")</li>
 *   <li>GRADE – ročník (např. "6")</li>
 *   <li>LEVEL – stupeň ("1" = ročníky 1–5, "2" = ročníky 6–9)</li>
 *   <li>LEVEL_1, LEVEL_2 – stupeň (explicitní, zpětná kompatibilita)</li>
 *   <li>ALL_STUDENTS – všichni žáci</li>
 *   <li>TEACHERS – učitelé</li>
 *   <li>WHOLE_SCHOOL – celá škola</li>
 * </ul>
 *
 * Pravidlo může nastavit více atributů a přidat uživatele do více skupin najednou.
 *
 * @author Jan Hladěna
 */
public class RuleServiceImpl implements RuleService {

    private final LDAPUserRepository ldapRepo;

    public RuleServiceImpl(LDAPUserRepository ldapRepo) {
        this.ldapRepo = ldapRepo;
    }

    @Override
    public List<SyncResult> applyRules(List<SyncRule> rules, List<StudentRecord> users,
                                        boolean repair, SyncProgressListener listener) {
        listener.onPhaseStart("Aplikace deklarativních pravidel");
        List<SyncResult> results = new ArrayList<>();

        for (SyncRule rule : rules) {
            List<StudentRecord> matching = filterByRule(rule, users);
            listener.onProgress("Pravidlo " + rule + " → " + matching.size() + " záznamů.");

            for (StudentRecord user : matching) {
                if (user.getDn() == null) continue;

                if (repair) {
                    results.addAll(applyRuleToUser(rule, user));
                } else {
                    // suchý běh – reportovat co by se stalo
                    results.add(SyncResult.skipped(user.getInternalId(),
                            "Pravidlo by nastavilo: " + rule));
                }
            }
        }

        int ok = (int) results.stream().filter(SyncResult::isSuccess).count();
        int err = (int) results.stream().filter(r -> !r.isSuccess()).count();
        listener.onPhaseEnd("Aplikace deklarativních pravidel", ok, err);
        return results;
    }

    // ---- Interní metody ----

    /**
     * Aplikuje pravidlo na jednoho uživatele – nastaví atributy a přidá do skupin.
     */
    private List<SyncResult> applyRuleToUser(SyncRule rule, StudentRecord user) {
        List<SyncResult> results = new ArrayList<>();
        String dn = user.getDn();
        String id = user.getInternalId() != null ? user.getInternalId() : dn;

        // nastavení atributů
        if (rule.getAttributes() != null) {
            for (SyncRuleAttribute attr : rule.getAttributes()) {
                EBakaLDAPAttributes resolved = resolveAttribute(attr.attribute());
                if (resolved != null) {
                    ldapRepo.updateAttribute(dn, resolved, attr.value());
                    results.add(SyncResult.updated(id,
                            "Pravidlo: " + attr.attribute() + " = " + attr.value()));
                } else {
                    results.add(SyncResult.error(id,
                            "Neznámý atribut: " + attr.attribute()));
                }
            }
        }

        // přidání do skupin
        if (rule.getGroups() != null) {
            for (String groupDn : rule.getGroups()) {
                ldapRepo.addToGroup(dn, groupDn);
                results.add(SyncResult.updated(id,
                        "Přidán do skupiny: " + groupDn));
            }
        }

        return results;
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
}
