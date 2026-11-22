package cz.zsstudanka.skola.bakakeeper.config;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Konvence pojmenování organizačních jednotek a skupin v Active Directory.
 *
 * Popisuje, jak se pod bázemi z konfigurace ({@code ldap.*}) jmenují OU ročníků,
 * tříd a ročníků vyřazení, podle kterých OU se rozpoznávají kategorie uživatelů
 * a jak se jmenují (CN a e-mail) automaticky spravované skupiny a distribuční seznamy.
 *
 * Hodnoty lze přepsat v nepovinné sekci {@code structure} konfiguračního souboru;
 * chybějící klíče mají výchozí hodnoty odpovídající původním konvencím BakaKeeperu.
 *
 * <pre>
 * structure:
 *   ou:
 *     grade: "Rocnik-{rocnik}"
 *     class: "Trida-{trida}"
 *     alumni_year: "{rok}"
 *   categories:
 *     ZAK: "Zaci"
 *     UCITEL: "Ucitele"
 *   groups:
 *     students_class: { cn: "Zaci-Trida-{rocnik}{trida}", mail: "zaci-{rocnik}{trida}" }
 * </pre>
 *
 * Zástupné symboly: {@code {rocnik}} – číslo ročníku, {@code {trida}} – písmeno třídy,
 * {@code {stupen}} – číslo stupně (1/2), {@code {rok}} – rok vyřazení.
 *
 * Instance je neměnná.
 *
 * @author Jan Hladěna
 */
public final class DirectoryLayout {

    // zástupné symboly
    public static final String PH_GRADE = "{rocnik}";
    public static final String PH_CLASS = "{trida}";
    public static final String PH_LEVEL = "{stupen}";
    public static final String PH_YEAR = "{rok}";

    private static final Set<String> PLACEHOLDERS = Set.of(PH_GRADE, PH_CLASS, PH_LEVEL, PH_YEAR);
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{[^}]*}");

    /** znaky, které nesmí být v názvu OU/CN (porušily by DN) */
    private static final Pattern FORBIDDEN_RDN_CHARS = Pattern.compile("[,=+\"\\\\<>;#]");

    /**
     * Druhy automaticky spravovaných skupin a distribučních seznamů.
     * Pořadí odpovídá hierarchii (rodiče před potomky).
     */
    public enum GroupKind {
        /** globální skupina všech žáků (OU globálních skupin) */
        STUDENTS_GLOBAL("students_global", "Skupina-Zaci", "zaci-vsichni"),
        /** bezpečnostní skupina všech žáků */
        STUDENTS_ALL("students_all", "Zaci-Vsichni", "zaci"),
        /** žáci stupně */
        STUDENTS_LEVEL("students_level", "Zaci-Stupen-" + PH_LEVEL, "zaci-stupen-" + PH_LEVEL),
        /** žáci ročníku */
        STUDENTS_GRADE("students_grade", "Zaci-Rocnik-" + PH_GRADE, "zaci-rocnik-" + PH_GRADE),
        /** žáci třídy */
        STUDENTS_CLASS("students_class", "Zaci-Trida-" + PH_GRADE + PH_CLASS, "zaci-" + PH_GRADE + PH_CLASS),
        /** rodiče všech žáků */
        PARENTS_ALL("parents_all", "Rodice-Vsichni", "rodice-cela-skola"),
        /** rodiče žáků stupně */
        PARENTS_LEVEL("parents_level", "Rodice-Stupen-" + PH_LEVEL, "rodice-stupen-" + PH_LEVEL),
        /** rodiče žáků ročníku */
        PARENTS_GRADE("parents_grade", "Rodice-Rocnik-" + PH_GRADE, "rodice-rocnik-" + PH_GRADE),
        /** rodiče žáků třídy */
        PARENTS_CLASS("parents_class", "Rodice-Trida-" + PH_GRADE + PH_CLASS, "rodice-trida-" + PH_GRADE + PH_CLASS),
        /** všichni třídní učitelé */
        CLASS_TEACHERS_ALL("class_teachers_all", "Ucitele-Tridni", "ucitele-tridni"),
        /** třídní učitelé stupně */
        CLASS_TEACHERS_LEVEL("class_teachers_level", "Ucitele-Tridni-Stupen-" + PH_LEVEL, "tridni-stupen-" + PH_LEVEL),
        /** třídní učitelé ročníku */
        CLASS_TEACHERS_GRADE("class_teachers_grade", "Ucitele-Tridni-Rocnik-" + PH_GRADE, "tridni-rocnik-" + PH_GRADE),
        /** třídní učitel třídy */
        CLASS_TEACHERS_CLASS("class_teachers_class", "Ucitele-Tridni-" + PH_GRADE + PH_CLASS, "tridni-" + PH_GRADE + PH_CLASS);

        private final String key;
        private final String defaultCn;
        private final String defaultMail;

        GroupKind(String key, String defaultCn, String defaultMail) {
            this.key = key;
            this.defaultCn = defaultCn;
            this.defaultMail = defaultMail;
        }

        /** klíč v sekci {@code structure.groups} */
        public String key() { return key; }

        /** zástupné symboly, které musí šablona obsahovat (jinak by názvy kolidovaly) */
        Set<String> requiredPlaceholders() {
            return switch (this) {
                case STUDENTS_LEVEL, PARENTS_LEVEL, CLASS_TEACHERS_LEVEL -> Set.of(PH_LEVEL);
                case STUDENTS_GRADE, PARENTS_GRADE, CLASS_TEACHERS_GRADE -> Set.of(PH_GRADE);
                case STUDENTS_CLASS, PARENTS_CLASS, CLASS_TEACHERS_CLASS -> Set.of(PH_GRADE, PH_CLASS);
                default -> Set.of();
            };
        }

        /** zástupné symboly, které šablona smí obsahovat */
        Set<String> allowedPlaceholders() {
            return switch (this) {
                case STUDENTS_LEVEL, PARENTS_LEVEL, CLASS_TEACHERS_LEVEL -> Set.of(PH_LEVEL);
                case STUDENTS_GRADE, PARENTS_GRADE, CLASS_TEACHERS_GRADE -> Set.of(PH_GRADE, PH_LEVEL);
                case STUDENTS_CLASS, PARENTS_CLASS, CLASS_TEACHERS_CLASS -> Set.of(PH_GRADE, PH_CLASS, PH_LEVEL);
                default -> Set.of();
            };
        }
    }

    /**
     * Šablona jména skupiny.
     *
     * @param cn   šablona common name
     * @param mail šablona lokální části e-mailové adresy (před @)
     */
    public record GroupTemplate(String cn, String mail) {}

    /**
     * Třída žáka odvozená z DN.
     *
     * @param year   ročník
     * @param letter písmeno třídy (tak, jak je uvedeno v DN)
     */
    public record ClassRef(int year, String letter) {
        /** označení třídy ve tvaru „5.A“ */
        public String label() { return year + "." + letter; }
    }

    /**
     * Výsledek načtení konvencí z konfigurace.
     *
     * @param layout načtené konvence (nevalidní položky nahrazeny výchozími)
     * @param errors popis chyb (prázdný seznam = vše v pořádku)
     */
    public record ParseResult(DirectoryLayout layout, List<String> errors) {}

    /** výchozí kategorie uživatelů – název OU, jejíž přítomnost v DN kategorii určuje */
    private static final Map<String, String> DEFAULT_CATEGORIES;
    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("ZAK", "Zaci");
        m.put("UCITEL", "Ucitele");
        m.put("VEDENI", "Vedeni");
        m.put("PROVOZ", "Provoz");
        m.put("ASISTENT", "Asistenti");
        m.put("VYCHOVATELKA", "Vychovatelky");
        DEFAULT_CATEGORIES = Collections.unmodifiableMap(m);
    }

    private static final DirectoryLayout DEFAULTS = new DirectoryLayout(
            "Rocnik-" + PH_GRADE, "Trida-" + PH_CLASS, PH_YEAR,
            DEFAULT_CATEGORIES, defaultGroups());

    private final String gradeOu;
    private final String classOu;
    private final String alumniYearOu;
    private final Map<String, String> categories;
    private final Map<GroupKind, GroupTemplate> groups;

    /** regulární výraz pro rozpoznání OU třídy pod OU ročníku */
    private final Pattern classInGradePattern;
    /** regulární výraz pro rozpoznání OU ročníku v DN */
    private final Pattern gradePattern;

    private DirectoryLayout(String gradeOu, String classOu, String alumniYearOu,
                            Map<String, String> categories, Map<GroupKind, GroupTemplate> groups) {
        this.gradeOu = gradeOu;
        this.classOu = classOu;
        this.alumniYearOu = alumniYearOu;
        this.categories = Collections.unmodifiableMap(new LinkedHashMap<>(categories));
        this.groups = Collections.unmodifiableMap(new EnumMap<>(groups));

        // OU=<třída>,OU=<ročník> – kdekoli v DN (ne nutně hned pod CN)
        this.classInGradePattern = Pattern.compile(
                "(?:^|,)OU=" + toRegex(classOu) + ",OU=" + toRegex(gradeOu) + "(?:,|$)",
                Pattern.CASE_INSENSITIVE);
        // OU=<ročník> – rozlišuje velikost písmen stejně jako původní kontrola kategorií
        this.gradePattern = Pattern.compile("(?:^|,)OU=" + toRegex(gradeOu) + "(?:,|$)");
    }

    /**
     * Výchozí konvence (shodné s původními pevně danými názvy).
     *
     * @return výchozí konvence
     */
    public static DirectoryLayout defaults() {
        return DEFAULTS;
    }

    /**
     * Konvence z dané konfigurace; pokud je konfigurace neposkytuje (null),
     * vrací výchozí konvence.
     *
     * @param config konfigurace aplikace (může být null)
     * @return konvence pojmenování
     */
    public static DirectoryLayout of(AppConfig config) {
        DirectoryLayout layout = (config != null) ? config.getDirectoryLayout() : null;
        return (layout != null) ? layout : DEFAULTS;
    }

    /**
     * Načte konvence z obsahu sekce {@code structure}.
     *
     * @param raw obsah sekce (mapa) nebo null, pokud sekce chybí
     * @return načtené konvence a případné chyby
     */
    public static ParseResult parse(Object raw) {
        List<String> errors = new ArrayList<>();
        if (raw == null) {
            return new ParseResult(DEFAULTS, List.of());
        }
        if (!(raw instanceof Map<?, ?> root)) {
            errors.add("structure: očekávána mapa");
            return new ParseResult(DEFAULTS, List.copyOf(errors));
        }

        String gradeOu = DEFAULTS.gradeOu;
        String classOu = DEFAULTS.classOu;
        String alumniYearOu = DEFAULTS.alumniYearOu;
        Map<String, String> categories = new LinkedHashMap<>(DEFAULT_CATEGORIES);
        Map<GroupKind, GroupTemplate> groups = new EnumMap<>(DEFAULTS.groups);

        for (Map.Entry<?, ?> e : root.entrySet()) {
            String key = String.valueOf(e.getKey());
            switch (key) {
                case "ou" -> {
                    Map<?, ?> ou = asMap(e.getValue(), "structure.ou", errors);
                    for (Map.Entry<?, ?> oe : ou.entrySet()) {
                        String ouKey = String.valueOf(oe.getKey());
                        String path = "structure.ou." + ouKey;
                        switch (ouKey) {
                            case "grade" -> gradeOu = template(oe.getValue(), path,
                                    Set.of(PH_GRADE), Set.of(PH_GRADE), true, DEFAULTS.gradeOu, errors);
                            case "class" -> classOu = template(oe.getValue(), path,
                                    Set.of(PH_CLASS), Set.of(PH_CLASS, PH_GRADE), true, DEFAULTS.classOu, errors);
                            case "alumni_year" -> alumniYearOu = template(oe.getValue(), path,
                                    Set.of(PH_YEAR), Set.of(PH_YEAR), true, DEFAULTS.alumniYearOu, errors);
                            default -> errors.add(path + ": neznámý klíč (povoleno: grade, class, alumni_year)");
                        }
                    }
                }
                case "categories" -> {
                    Map<?, ?> cats = asMap(e.getValue(), "structure.categories", errors);
                    for (Map.Entry<?, ?> ce : cats.entrySet()) {
                        String cat = String.valueOf(ce.getKey()).toUpperCase(Locale.ROOT);
                        String path = "structure.categories." + ce.getKey();
                        String value = template(ce.getValue(), path, Set.of(), Set.of(), true, null, errors);
                        if (value != null) {
                            categories.put(cat, value);
                        }
                    }
                }
                case "groups" -> {
                    Map<?, ?> grs = asMap(e.getValue(), "structure.groups", errors);
                    for (Map.Entry<?, ?> ge : grs.entrySet()) {
                        String grKey = String.valueOf(ge.getKey());
                        String path = "structure.groups." + grKey;
                        GroupKind kind = kindByKey(grKey);
                        if (kind == null) {
                            errors.add(path + ": neznámý druh skupiny");
                            continue;
                        }
                        Map<?, ?> def = asMap(ge.getValue(), path, errors);
                        GroupTemplate current = groups.get(kind);
                        String cn = current.cn();
                        String mail = current.mail();
                        for (Map.Entry<?, ?> de : def.entrySet()) {
                            String field = String.valueOf(de.getKey());
                            switch (field) {
                                case "cn" -> cn = template(de.getValue(), path + ".cn",
                                        kind.requiredPlaceholders(), kind.allowedPlaceholders(), true, cn, errors);
                                case "mail" -> mail = template(de.getValue(), path + ".mail",
                                        kind.requiredPlaceholders(), kind.allowedPlaceholders(), false, mail, errors);
                                default -> errors.add(path + "." + field + ": neznámý klíč (povoleno: cn, mail)");
                            }
                        }
                        groups.put(kind, new GroupTemplate(cn, mail));
                    }
                }
                default -> errors.add("structure." + key + ": neznámý klíč (povoleno: ou, categories, groups)");
            }
        }

        DirectoryLayout layout = new DirectoryLayout(gradeOu, classOu, alumniYearOu, categories, groups);
        return new ParseResult(layout, List.copyOf(errors));
    }

    // ========================================================================
    // Organizační jednotky
    // ========================================================================

    /** Šablona názvu OU ročníku. */
    public String gradeOuTemplate() { return gradeOu; }

    /** Šablona názvu OU třídy. */
    public String classOuTemplate() { return classOu; }

    /** Šablona názvu OU ročníku vyřazení. */
    public String alumniYearOuTemplate() { return alumniYearOu; }

    /**
     * DN organizační jednotky ročníku, např. {@code OU=Rocnik-5,<báze žáků>}.
     *
     * @param year         ročník
     * @param studentsBase DN báze žáků
     * @return DN OU ročníku
     */
    public String gradeOuDn(int year, String studentsBase) {
        return "OU=" + fill(gradeOu, year, null, null, null) + "," + studentsBase;
    }

    /**
     * DN organizační jednotky třídy, např. {@code OU=Trida-A,OU=Rocnik-5,<báze žáků>}.
     * Písmeno třídy se převádí na velká písmena.
     *
     * @param year         ročník
     * @param letter       písmeno třídy
     * @param studentsBase DN báze žáků
     * @return DN OU třídy
     */
    public String classOuDn(int year, String letter, String studentsBase) {
        String l = letter.toUpperCase(Locale.ROOT);
        return "OU=" + fill(classOu, year, l, null, null) + "," + gradeOuDn(year, studentsBase);
    }

    /**
     * DN organizační jednotky ročníku vyřazení, např. {@code OU=2026,<báze vyřazených>}.
     *
     * @param year       rok vyřazení
     * @param alumniBase DN báze vyřazených žáků
     * @return DN OU ročníku vyřazení
     */
    public String alumniYearOuDn(String year, String alumniBase) {
        return "OU=" + alumniYearOu.replace(PH_YEAR, year) + "," + alumniBase;
    }

    /**
     * Zjistí třídu žáka z DN (OU třídy přímo pod OU ročníku).
     *
     * @param dn plné DN objektu
     * @return třída, nebo prázdná hodnota, pokud DN neobsahuje OU třídy
     */
    public Optional<ClassRef> parseClass(String dn) {
        if (dn == null) return Optional.empty();
        Matcher m = classInGradePattern.matcher(dn);
        if (!m.find()) return Optional.empty();

        String letter = null;
        Integer year = null;
        // pořadí skupin: nejdříve zástupné symboly OU třídy, pak OU ročníku
        List<String> order = new ArrayList<>(placeholderOrder(classOu));
        order.addAll(placeholderOrder(gradeOu));
        for (int i = 0; i < order.size(); i++) {
            String value = m.group(i + 1);
            switch (order.get(i)) {
                case PH_CLASS -> letter = value;
                case PH_GRADE -> {
                    int y = Integer.parseInt(value);
                    if (year != null && year != y) return Optional.empty();
                    year = y;
                }
                default -> { /* jiné symboly se v OU třídy/ročníku nevyskytují */ }
            }
        }
        if (year == null || letter == null) return Optional.empty();
        return Optional.of(new ClassRef(year, letter));
    }

    /**
     * Ročník žáka z DN.
     *
     * @param dn plné DN objektu žáka
     * @return ročník
     * @throws NumberFormatException pokud DN neobsahuje OU třídy
     */
    public Integer classYear(String dn) {
        return parseClass(dn).map(ClassRef::year)
                .orElseThrow(() -> new NumberFormatException("DN neobsahuje OU třídy: " + dn));
    }

    /**
     * Písmeno třídy žáka z DN.
     *
     * @param dn plné DN objektu žáka
     * @return písmeno třídy, nebo null, pokud DN neobsahuje OU třídy
     */
    public String classLetter(String dn) {
        return parseClass(dn).map(ClassRef::letter).orElse(null);
    }

    /**
     * Označení třídy ve tvaru „5.A“ z DN.
     *
     * @param dn plné DN objektu žáka
     * @return označení třídy
     * @throws NumberFormatException pokud DN neobsahuje OU třídy
     */
    public String classLabel(String dn) {
        return parseClass(dn).map(ClassRef::label)
                .orElseThrow(() -> new NumberFormatException("DN neobsahuje OU třídy: " + dn));
    }

    // ========================================================================
    // Kategorie
    // ========================================================================

    /**
     * Kategorie uživatelů a názvy OU, které je určují.
     *
     * @return neměnná mapa kategorie → název OU
     */
    public Map<String, String> categories() {
        return categories;
    }

    /**
     * Ověří, zda DN obsahuje OU ročníku.
     *
     * @param dn plné DN
     * @return true, pokud DN obsahuje OU ročníku
     */
    public boolean isInGradeOu(String dn) {
        return dn != null && gradePattern.matcher(dn).find();
    }

    /**
     * Ověří, zda DN patří žákovi (obsahuje OU kategorie ZAK nebo OU ročníku).
     *
     * @param dn plné DN
     * @return true pro DN žáka
     */
    public boolean isStudentDn(String dn) {
        return matchesCategory(dn, "ZAK");
    }

    /**
     * Ověří, zda DN odpovídá kategorii (obsahuje příslušnou OU).
     * Kategorie ZAK zahrnuje i DN v OU ročníku.
     *
     * @param dn       plné DN
     * @param category název kategorie (nerozlišuje velikost písmen)
     * @return true, pokud DN kategorii odpovídá
     */
    public boolean matchesCategory(String dn, String category) {
        if (dn == null || category == null) return false;
        String cat = category.toUpperCase(Locale.ROOT);
        String ou = categories.get(cat);
        boolean inOu = ou != null && dn.contains("OU=" + ou);
        if ("ZAK".equals(cat)) {
            return inOu || isInGradeOu(dn);
        }
        return inOu;
    }

    // ========================================================================
    // Skupiny
    // ========================================================================

    /**
     * Šablona skupiny daného druhu.
     *
     * @param kind druh skupiny
     * @return šablona CN a e-mailu
     */
    public GroupTemplate group(GroupKind kind) {
        return groups.get(kind);
    }

    /** CN skupiny bez parametrů (celoškolní skupiny). */
    public String groupCn(GroupKind kind) {
        return fill(groups.get(kind).cn(), null, null, null, null);
    }

    /** CN skupiny stupně. */
    public String levelGroupCn(GroupKind kind, int level) {
        return fill(groups.get(kind).cn(), null, null, level, null);
    }

    /** CN skupiny ročníku. */
    public String gradeGroupCn(GroupKind kind, int year) {
        return fill(groups.get(kind).cn(), year, null, levelOf(year), null);
    }

    /** CN skupiny třídy (písmeno se převádí na velká písmena). */
    public String classGroupCn(GroupKind kind, int year, String letter) {
        return fill(groups.get(kind).cn(), year, letter.toUpperCase(Locale.ROOT), levelOf(year), null);
    }

    /** E-mail celoškolní skupiny (malými písmeny). */
    public String groupMail(GroupKind kind, String mailDomain) {
        return mail(fill(groups.get(kind).mail(), null, null, null, null), mailDomain);
    }

    /** E-mail skupiny stupně (malými písmeny). */
    public String levelGroupMail(GroupKind kind, int level, String mailDomain) {
        return mail(fill(groups.get(kind).mail(), null, null, level, null), mailDomain);
    }

    /** E-mail skupiny ročníku (malými písmeny). */
    public String gradeGroupMail(GroupKind kind, int year, String mailDomain) {
        return mail(fill(groups.get(kind).mail(), year, null, levelOf(year), null), mailDomain);
    }

    /** E-mail skupiny třídy (malými písmeny). */
    public String classGroupMail(GroupKind kind, int year, String letter, String mailDomain) {
        return mail(fill(groups.get(kind).mail(), year, letter, levelOf(year), null), mailDomain);
    }

    /**
     * Stupeň podle ročníku (1–5 → 1, 6–9 → 2).
     *
     * @param year ročník
     * @return číslo stupně
     */
    public static int levelOf(int year) {
        return (year <= 5) ? 1 : 2;
    }

    // ========================================================================
    // Serializace
    // ========================================================================

    /**
     * Zda jde o výchozí konvence.
     *
     * @return true, pokud se konvence shodují s výchozími
     */
    public boolean isDefault() {
        return this.equals(DEFAULTS);
    }

    /**
     * Převede konvence na mapu ve formátu sekce {@code structure}
     * (použitelné pro YAML serializaci).
     *
     * @return mapa s klíči ou, categories, groups
     */
    public Map<String, Object> toMap() {
        Map<String, Object> ou = new LinkedHashMap<>();
        ou.put("grade", gradeOu);
        ou.put("class", classOu);
        ou.put("alumni_year", alumniYearOu);

        Map<String, Object> grs = new LinkedHashMap<>();
        for (GroupKind k : GroupKind.values()) {
            Map<String, Object> g = new LinkedHashMap<>();
            g.put("cn", groups.get(k).cn());
            g.put("mail", groups.get(k).mail());
            grs.put(k.key(), g);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ou", ou);
        result.put("categories", new LinkedHashMap<>(categories));
        result.put("groups", grs);
        return result;
    }

    // ========================================================================
    // Interní pomocné metody
    // ========================================================================

    private static Map<GroupKind, GroupTemplate> defaultGroups() {
        Map<GroupKind, GroupTemplate> m = new EnumMap<>(GroupKind.class);
        for (GroupKind k : GroupKind.values()) {
            m.put(k, new GroupTemplate(k.defaultCn, k.defaultMail));
        }
        return m;
    }

    private static GroupKind kindByKey(String key) {
        for (GroupKind k : GroupKind.values()) {
            if (k.key.equals(key)) return k;
        }
        return null;
    }

    private static Map<?, ?> asMap(Object value, String path, List<String> errors) {
        if (value instanceof Map<?, ?> m) return m;
        if (value != null) errors.add(path + ": očekávána mapa");
        return Map.of();
    }

    /**
     * Ověří šablonu názvu.
     *
     * @return platná šablona, nebo {@code fallback} při chybě
     */
    private static String template(Object value, String path, Set<String> required, Set<String> allowed,
                                   boolean rdn, String fallback, List<String> errors) {
        if (!(value instanceof String s) || s.isBlank()) {
            errors.add(path + ": očekáván neprázdný řetězec");
            return fallback;
        }
        Matcher m = PLACEHOLDER.matcher(s);
        while (m.find()) {
            String ph = m.group();
            if (!PLACEHOLDERS.contains(ph) || !allowed.contains(ph)) {
                errors.add(path + ": nepovolený zástupný symbol " + ph);
                return fallback;
            }
        }
        for (String ph : required) {
            if (!s.contains(ph)) {
                errors.add(path + ": chybí zástupný symbol " + ph);
                return fallback;
            }
        }
        if (rdn && FORBIDDEN_RDN_CHARS.matcher(s).find()) {
            errors.add(path + ": obsahuje nepovolený znak (, = + \" \\ < > ; #)");
            return fallback;
        }
        if (!rdn && (s.contains("@") || s.chars().anyMatch(Character::isWhitespace))) {
            errors.add(path + ": e-mail se zadává bez domény a bez mezer");
            return fallback;
        }
        return s;
    }

    private static String fill(String template, Integer year, String letter, Integer level, String alumniYear) {
        String r = template;
        if (year != null) r = r.replace(PH_GRADE, year.toString());
        if (letter != null) r = r.replace(PH_CLASS, letter);
        if (level != null) r = r.replace(PH_LEVEL, level.toString());
        if (alumniYear != null) r = r.replace(PH_YEAR, alumniYear);
        return r;
    }

    private static String mail(String localPart, String mailDomain) {
        return (localPart + "@" + mailDomain).toLowerCase(Locale.ROOT);
    }

    /** Pořadí zástupných symbolů v šabloně (pro mapování skupin regulárního výrazu). */
    private static List<String> placeholderOrder(String template) {
        List<String> result = new ArrayList<>();
        Matcher m = PLACEHOLDER.matcher(template);
        while (m.find()) result.add(m.group());
        return result;
    }

    /** Převede šablonu na regulární výraz se zachytávajícími skupinami. */
    private static String toRegex(String template) {
        StringBuilder sb = new StringBuilder();
        Matcher m = PLACEHOLDER.matcher(template);
        int last = 0;
        while (m.find()) {
            if (m.start() > last) sb.append(Pattern.quote(template.substring(last, m.start())));
            sb.append(switch (m.group()) {
                case PH_GRADE, PH_LEVEL -> "(\\d+)";
                case PH_YEAR -> "(\\d{4})";
                default -> "([^,]+?)";
            });
            last = m.end();
        }
        if (last < template.length()) sb.append(Pattern.quote(template.substring(last)));
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DirectoryLayout that)) return false;
        return gradeOu.equals(that.gradeOu) && classOu.equals(that.classOu)
                && alumniYearOu.equals(that.alumniYearOu)
                && categories.equals(that.categories) && groups.equals(that.groups);
    }

    @Override
    public int hashCode() {
        return Objects.hash(gradeOu, classOu, alumniYearOu, categories, groups);
    }
}
