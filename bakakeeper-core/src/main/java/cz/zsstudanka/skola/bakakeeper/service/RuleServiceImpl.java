package cz.zsstudanka.skola.bakakeeper.service;

import cz.zsstudanka.skola.bakakeeper.config.SyncRule;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLDAPAttributes;
import cz.zsstudanka.skola.bakakeeper.model.StudentRecord;
import cz.zsstudanka.skola.bakakeeper.model.SyncScope;
import cz.zsstudanka.skola.bakakeeper.repository.LDAPUserRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementace aplikace deklarativních pravidel.
 * Nová funkcionalita – pravidla z YAML konfigurace.
 *
 * @author Jan Hladěna
 */
public class RuleServiceImpl implements RuleService {

    private final LDAPUserRepository ldapRepo;

    public RuleServiceImpl(LDAPUserRepository ldapRepo) {
        this.ldapRepo = ldapRepo;
    }

    @Override
    public List<SyncResult> applyRules(List<SyncRule> rules, List<StudentRecord> students,
                                        boolean repair, SyncProgressListener listener) {
        listener.onPhaseStart("Aplikace deklarativních pravidel");
        List<SyncResult> results = new ArrayList<>();

        for (SyncRule rule : rules) {
            List<StudentRecord> matching = filterByRule(rule, students);
            listener.onProgress("Pravidlo " + rule + " → " + matching.size() + " záznamů.");

            for (StudentRecord student : matching) {
                if (student.getDn() == null) continue;

                if (repair) {
                    EBakaLDAPAttributes attr = resolveAttribute(rule.getAttribute());
                    if (attr != null) {
                        ldapRepo.updateAttribute(student.getDn(), attr, rule.getValue());
                        results.add(SyncResult.updated(student.getInternalId(),
                                "Pravidlo: " + rule.getAttribute() + " = " + rule.getValue()));
                    } else {
                        results.add(SyncResult.error(student.getInternalId(),
                                "Neznámý atribut: " + rule.getAttribute()));
                    }
                } else {
                    results.add(SyncResult.skipped(student.getInternalId(),
                            "Pravidlo by nastavilo " + rule.getAttribute() + " = " + rule.getValue()));
                }
            }
        }

        int ok = (int) results.stream().filter(SyncResult::isSuccess).count();
        int err = (int) results.stream().filter(r -> !r.isSuccess()).count();
        listener.onPhaseEnd("Aplikace deklarativních pravidel", ok, err);
        return results;
    }

    /**
     * Filtruje žáky podle pravidla.
     */
    private List<StudentRecord> filterByRule(SyncRule rule, List<StudentRecord> students) {
        return students.stream()
                .filter(s -> matchesRule(rule, s))
                .toList();
    }

    /**
     * Ověří, zda žák odpovídá pravidlu.
     */
    private boolean matchesRule(SyncRule rule, StudentRecord student) {
        SyncScope scope = rule.getScope();
        String match = rule.getMatch();

        return switch (scope) {
            case CLASS -> match != null && match.equals(student.getClassName());
            case GRADE -> {
                try {
                    yield student.getClassYear() == Integer.parseInt(match);
                } catch (NumberFormatException e) {
                    yield false;
                }
            }
            case LEVEL_1 -> student.getClassYear() >= 1 && student.getClassYear() <= 5;
            case LEVEL_2 -> student.getClassYear() >= 6 && student.getClassYear() <= 9;
            case ALL_STUDENTS -> true;
            case INDIVIDUAL -> match != null && match.equals(student.getInternalId());
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
