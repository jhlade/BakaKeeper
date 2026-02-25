package cz.zsstudanka.skola.bakakeeper.service;

import cz.zsstudanka.skola.bakakeeper.model.FacultyRecord;
import cz.zsstudanka.skola.bakakeeper.model.StudentRecord;
import cz.zsstudanka.skola.bakakeeper.repository.FacultyRepository;
import cz.zsstudanka.skola.bakakeeper.repository.StudentRepository;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Parsování a vyhodnocení rozsahu výběru žáků.
 * Podporuje vzory: {@code *} (vše), {@code 5} (ročník), {@code 5.A} (třída),
 * {@code novak.tomas@skola.cz} (individuální žák). Vzory lze kombinovat čárkou.
 *
 * <p>Extrahováno z {@code Export.genericReport()} pro znovupoužitelnost
 * v příkazech {@code --report}, {@code --resetreport} a {@code --export}.</p>
 *
 * @author Jan Hladěna
 */
public class RangeSelector {

    /** zparsované celé třídy (např. "5.A") – seřazené */
    private final List<String> classes;

    /** zparsované individuální identifikátory (UPN / e-mail) – seřazené */
    private final List<String> individuals;

    private RangeSelector(List<String> classes, List<String> individuals) {
        this.classes = classes;
        this.individuals = individuals;
    }

    /**
     * Zparsuje query string na strukturovaný výběr.
     *
     * <p>Podporované vzory (oddělené čárkou):
     * <ul>
     *     <li>{@code *} – všechny třídy (1.A–9.E)</li>
     *     <li>{@code [1-9]} – všechny třídy v ročníku</li>
     *     <li>{@code [1-9].[a-eA-E]} – konkrétní třída</li>
     *     <li>vše ostatní s {@code .} a délkou &gt;3 – individuální žák (UPN)</li>
     * </ul>
     *
     * @param query query string (např. "5,6.B,novak.tomas@skola.cz")
     * @return rozparsovaný selektor
     * @throws IllegalArgumentException pokud je query null nebo prázdný
     */
    public static RangeSelector parse(String query) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Query nesmí být prázdný.");
        }

        List<String> classes = new ArrayList<>();
        List<String> individuals = new ArrayList<>();

        String[] queries = query.split(",");

        for (String q : queries) {
            q = q.trim();
            if (q.isEmpty()) {
                continue;
            }

            // vše
            if (q.equals("*")) {
                for (int yr = 1; yr <= 9; yr++) {
                    for (char letter = 'A'; letter <= 'E'; letter++) {
                        String cls = yr + "." + letter;
                        if (!classes.contains(cls)) {
                            classes.add(cls);
                        }
                    }
                }
                break;
            }

            // ročník
            if (q.length() == 1 && q.matches("[1-9]")) {
                for (char letter = 'A'; letter <= 'E'; letter++) {
                    String cls = q + "." + letter;
                    if (!classes.contains(cls)) {
                        classes.add(cls);
                    }
                }
                continue;
            }

            // třída
            if (q.length() == 3 && q.matches("[1-9]\\.[a-eA-E]")) {
                String cls = q.toUpperCase();
                if (!classes.contains(cls)) {
                    classes.add(cls);
                }
                continue;
            }

            // individuální žák (UPN / e-mail)
            if (q.length() > 3 && q.contains(".")) {
                String id = q.toLowerCase();
                if (!individuals.contains(id)) {
                    individuals.add(id);
                }
            }
        }

        Collections.sort(classes);
        Collections.sort(individuals);

        return new RangeSelector(classes, individuals);
    }

    /**
     * Vyhodnotí výběr – načte žáky z repozitáře a seskupí je podle tříd.
     * Varianta bez e-mailové domény – individuální identifikátory bez {@code @} nebudou doplněny.
     *
     * @param studentRepo  repozitář žáků
     * @param facultyRepo  repozitář vyučujících (pro třídní učitele)
     * @return vyhodnocený výběr se žáky seskupenými podle tříd
     */
    public ResolvedSelection resolve(StudentRepository studentRepo,
                                      FacultyRepository facultyRepo) {
        return resolve(studentRepo, facultyRepo, null);
    }

    /**
     * Vyhodnotí výběr – načte žáky z repozitáře a seskupí je podle tříd.
     * Individuální žáci se vyhledají podle e-mailu a zařadí do příslušné třídy.
     * Deduplikace zajistí, že se žák nevyskytne dvakrát (např. při zadání třídy i konkrétního UPN).
     *
     * <p>Pokud je zadán {@code mailDomain} a individuální identifikátor neobsahuje {@code @},
     * automaticky se doplní jako {@code identifikátor@mailDomain}.</p>
     *
     * @param studentRepo  repozitář žáků
     * @param facultyRepo  repozitář vyučujících (pro třídní učitele)
     * @param mailDomain   e-mailová doména pro doplnění bare loginů (může být null)
     * @return vyhodnocený výběr se žáky seskupenými podle tříd
     */
    public ResolvedSelection resolve(StudentRepository studentRepo,
                                      FacultyRepository facultyRepo,
                                      String mailDomain) {

        // třídní učitelé – indexováni podle classLabel
        Map<String, FacultyRecord> teachersByClass = facultyRepo.findActive(true).stream()
                .filter(f -> f.getClassLabel() != null)
                .collect(Collectors.toMap(FacultyRecord::getClassLabel, f -> f, (a, b) -> a));

        // výsledná mapa: třída → seznam žáků
        Map<String, List<StudentRecord>> studentsByClass = new LinkedHashMap<>();
        // množina interních ID pro deduplikaci
        Set<String> seenIds = new HashSet<>();

        // načtení žáků pro celé třídy
        for (String cls : classes) {
            // třída musí existovat v evidenci (má třídního učitele)
            if (!teachersByClass.containsKey(cls)) {
                continue;
            }

            // parsování ročníku a písmena z "5.A"
            int year = Character.getNumericValue(cls.charAt(0));
            String letter = String.valueOf(cls.charAt(2));

            List<StudentRecord> classStudents = studentRepo.findActive(year, letter);
            for (StudentRecord s : classStudents) {
                seenIds.add(s.getInternalId());
            }

            studentsByClass.put(cls, new ArrayList<>(classStudents));
        }

        // vyhledání individuálních žáků
        List<String> notFound = new ArrayList<>();

        for (String id : individuals) {
            // doplnění domény pro bare login (bez @)
            String email = id;
            if (!id.contains("@") && mailDomain != null && !mailDomain.isEmpty()) {
                email = id + "@" + mailDomain;
            }

            StudentRecord student = studentRepo.findByEmail(email);
            if (student == null) {
                notFound.add(id);
                continue;
            }

            // deduplikace – žák už může být načtený v rámci třídy
            if (seenIds.contains(student.getInternalId())) {
                continue;
            }
            seenIds.add(student.getInternalId());

            // zařazení do příslušné třídy
            String cls = student.getClassName();
            if (cls != null) {
                studentsByClass.computeIfAbsent(cls, k -> new ArrayList<>()).add(student);
            }
        }

        // omezit mapu třídních učitelů jen na třídy, které máme ve výsledku
        Map<String, FacultyRecord> relevantTeachers = new LinkedHashMap<>();
        for (String cls : studentsByClass.keySet()) {
            FacultyRecord teacher = teachersByClass.get(cls);
            if (teacher != null) {
                relevantTeachers.put(cls, teacher);
            }
        }

        return new ResolvedSelection(studentsByClass, relevantTeachers, notFound);
    }

    /**
     * Vrátí zparsované třídy (pro účely testování a logování).
     *
     * @return nemodifikovatelný seznam tříd
     */
    public List<String> getClasses() {
        return Collections.unmodifiableList(classes);
    }

    /**
     * Vrátí zparsované individuální identifikátory (pro účely testování a logování).
     *
     * @return nemodifikovatelný seznam individuálních identifikátorů
     */
    public List<String> getIndividuals() {
        return Collections.unmodifiableList(individuals);
    }
}
