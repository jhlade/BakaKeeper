#!/usr/bin/env python3
# =============================================================================
# BakaKeeper – dev seed generátor testovacích dat
#
# Testovací data jsou definována v souborech v adresáři data/:
#   data/tridy.txt         – seznam tříd (formát: ROCNIK;ZKRATKA)
#   data/zamestnanci.txt   – zaměstnanci (INTERN_KOD;JMENO;PRIJMENI;OU;FUNKCE;UCI_LETOS;TRIDNI_PRO)
#   data/zaci_staticti.txt – stálí žáci (INTERN_KOD;JMENO;PRIJMENI;TRIDA;EVID_DO;E_MAIL)
#
# Statičtí žáci jsou načteni vždy a mají nejvyšší prioritu při přiřazení e-mailů.
# E-mail explicitně zadaný v souboru se použije přímo a zablokuje přiřazení stejné
# adresy algoritmickým žákům. Zákonní zástupci se pro statické žáky negenerují.
#
# Algoritmičtí žáci a jejich zákonní zástupci se generují z českých jmenných zásobníků.
# Jmenné zásoby jsou dimenzovány tak, aby se kombinace jméno+příjmení systematicky
# neopakovala (velikosti zásobníků jsou navzájem prvočíselné → LCM >> počet žáků).
#
# Pokud přesto dojde ke shodě (náhodnou kolizí), dostane mladší žák (nižší ročník)
# číslici za e-mailem: jan.novak@domain vs. jan.novak2@domain.
# Pořadí priority e-mailů: statický s explicitním > statický auto > algoritmický starší > mladší.
#
# Variace jmen algoritmických žáků (deterministické, reprodukovatelné):
#   ~4 % vietnamská, ~4 % ukrajinská (moderní transliterace), ~4 % španělská jména.
#   ~7 % žáků má dvě křestní jména: „Jan Pavel Novák", „Lucie Kateřina Modrá".
#   ~1 % žáků (pouze CZ) má šlechtický partikl: „von Blankytnák", „de Oblačník".
#   ~2 % žáků má hyphenované příjmení (16–18 znaků), aby kombinace jmeno.prijmeni
#        překročila 20 znaků a otestovala zpracování AD sAMAccountName.
#
# Variabilní počet žáků na třídu:
#   --per-class M  nastavuje průměr (výchozí: 5)
#   --variance V   nastavuje rozptyl M±V (výchozí: 0 = všechny třídy stejné)
#
# Výstup:
#   --sql-file  <cesta>   zapíše SQL INSERT příkazy (výchozí: stdout)
#   --sh-file   <cesta>   zapíše shell samba-tool příkazy pro AD
#   --domain    <doména>  interní AD doména (výchozí: skola.local)
#   --base-dn   <dn>      base DN (výchozí: DC=skola,DC=local)
#   --ad-pass   <heslo>   heslo AD účtů zaměstnanců (výchozí: BakaKeeper.2026)
#   --sql-db    <db>      název databáze (výchozí: bakalari)
#   --per-class <n>       počet žáků ve třídě (výchozí: 5, rozsah 1–20)
#   --stats               vypíše statistiku a skončí
# =============================================================================

import argparse
import os
import sys
import unicodedata

# =============================================================================
# Cesty k datovým souborům
# =============================================================================

_SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
DATA_DIR     = os.path.join(_SCRIPT_DIR, "data")

# =============================================================================
# Konfigurace generátoru
# =============================================================================

ZACI_NA_TRIDU_DEFAULT = 5      # výchozí počet žáků na třídu (--per-class)
ABSOLVENTI            = 5      # žáci s ukončenou evidencí (testování StudiumUkonceno)
EVID_DO_ABSOLVENT     = "2024-08-31"

# =============================================================================
# Jmenné zásobníky pro žáky a zákonné zástupce
# (zaměstnanci mají jména přímo v data/zamestnanci.txt)
#
# Zásobníky používají záměrně vymyšlená jména (zvířata, barvy, příroda, kaledářové
# variace) – nelze splést s reálnými osobami.
#
# Velikosti zásobníků jsou zvoleny tak, aby LCM >> počet generovaných entit
# a systematické opakování kombinací bylo minimální:
#   žáci:  JMENA 34 × PRIJMENI 72 → LCM = 1 224 (> 1 000 žáků, tj. škola 34×30)
#   ZZD:   JMENA 23 (prvočíslo) × PRIJMENI 41 (prvočíslo) → LCM = 943
# =============================================================================

# --- Žáci – mužská příjmení (59): inspirována zvířaty, přírodou, barvami ---
PRIJMENI_ZAK_M = [
    # zvířata
    "Liščák",    "Bobřík",    "Ježura",     "Vydrýsek",  "Vlkoun",
    "Srnec",     "Krtek",     "Jelen",      "Tchořík",   "Jezevec",
    "Krkavec",   "Sovák",     "Čáp",        "Ledňák",    "Drozd",
    "Losos",     "Kapr",      "Štičák",     "Rak",       "Šnek",
    "Chrochtal", "Kanec",     "Vrabec",     "Humr",      "Koťák",
    "Kocour",    "Kocourec",  "Mourek",     "Šupina",    "Korýš",
    # barvy a světlo
    "Modrák",    "Zeleník",   "Červín",     "Fialák",    "Žlutín",
    "Blankytnák","Šedivák",   "Béžák",      "Rumín",     "Zlatník",
    "Stříbrník", "Perleťák",  "Duhovec",    "Soumrák",   "Svítilník",
    "Černý",     "Šedivý",
    # příroda a počasí
    "Dubový",    "Jehličník", "Kůrovec",    "Lupínek",   "Kořínek",
    "Oblačník",  "Bouřák",    "Mrazík",     "Sněhulák",  "Kapkoun",
    "Větrník",   "Mlžák",     "Úsvitný",    "Zářínek",   "Polárník",
    # vymyšlené
    "Vrtulník",  "Klouzák",   "Pírček",     "Bublánek",  "Šuškánek",
    "Trpaslik",  "Skřítkov",  "Mlsáček",    "Bubloun",   "Strup",

]
assert len(PRIJMENI_ZAK_M) == 72

# Ženská příjmení odvozena z mužských
def _f(s: str) -> str:
    if s.endswith("cký"): return s[:-3] + "cká"
    if s.endswith("ský"): return s[:-3] + "ská"
    if s.endswith("ký"):  return s[:-2] + "ká"
    if s.endswith("ý"):   return s[:-1] + "á"
    if s.endswith("ek"):  return s[:-2] + "ková"
    if s.endswith("ec"):  return s[:-2] + "cová"
    if s.endswith("a"):   return s[:-1] + "ová"
    return s + "ová"

PRIJMENI_ZAK_F = [_f(p) for p in PRIJMENI_ZAK_M]

# --- Žáci – křestní jména (31): kaledářové variace + vymyšlené přezdívky ---
JMENA_ZAK_M = [
    # kaledářové variace (–oslav, –mir, –boj atd.)
    "Radoušek",  "Světlomír",  "Kvítoslav",  "Zoroslav",   "Buřivoj",
    "Mlžimír",   "Duhomil",    "Zlatoslav",  "Oblakoslav", "Rákoslav",
    "Kociáš",    "Luciáš",     "Uriáš",      "Štěkán",
    # z přírody
    "Duboslav",  "Jehličmír",  "Kapkoslav",  "Mrazimír",   "Sněžoslav",
    "Krutomír",
    # zkráceniny a přezdívky
    "Ríša",      "Fikus",      "Kubi",       "Bobík",      "Cílek",
    "Krteček",   "Šupin",      "Pírko",      "Dubin",      "Brouk",
    "Janek",     "Pačes",
    # zcela vymyšlená
    "Zlumek",    "Vrkos",      "Bublík",     "Šmudla",     "Chroust",
    "Hoblík",    "Milisálek",  "Mates",
]
assert len(JMENA_ZAK_M) == 34

JMENA_ZAK_F = [
    # kaledářové variace (–slava, –mila, –na)
    "Duhoslava",  "Mlžena",     "Kvítkoslava", "Azura",     "Ryboslava",
    "Hoblana",    "Kapkomíra",  "Sněžena",     "Zlatoslava",  "Oblačena",
    "Lycka",      "Micka",
    # z přírody
    "Jehlička",   "Rákosena",   "Mrazivka",    "Švestka",    "Lipěna",
    # zkráceniny a přezdívky
    "Vyky",       "Krustýna",   "Zuběna",      "Bobina",      "Slimka",
    "Motýla",     "Broučena",   "Ryběna",      "Lupínka",     "Drobka",
    "Myška",      "Gábinka",
    # zcela vymyšlená
    "Šmudlenka",  "Lišejka",    "Pírečka",     "Mlsnička",    "Bublinka",
    "Chroustka",  "Cihlena",    "Borka",       "Typka",
]
assert len(JMENA_ZAK_F) == 34

# --- Zákonní zástupci – příjmení (41): starší, serióznější variace ---
PRIJMENI_ZZD_M = [
    # zvířata (dospělejší formy)
    "Liščí",     "Bobr",     "Ježek",    "Vydřík",     "Vlčínek",
    "Srník",      "Krtek",      "Jelínek",     "Tchoříček",    "Jezevčík",
    "Krkavec",   "Sovička",      "Čáp",       "Losos",    "Kapr",
    # barvy (příjmení z adjektiv)
    "Modrý",     "Zelený",    "Červený",    "Fialový",   "Zlatý",
    "Stříbrný",  "Perleťový", "Duhový",     "Šedý",      "Blankytnný",
    # příroda
    "Dubový",    "Jehličový",  "Oblačný",   "Bouřný",    "Mrazivý",
    "Sněžný",    "Kapkový",    "Vítrný",    "Mlžný",     "Úsvitný",
    # vymyšlené
    "Záříkový",  "Polární",    "Soumrakový","Svítilnový","Duhokový",
    "Pramenitý",
]
assert len(PRIJMENI_ZZD_M) == 41

PRIJMENI_ZZD_F = [_f(p) for p in PRIJMENI_ZZD_M]

# --- Zákonní zástupci – křestní jména (23): střízlivější, uvěřitelnější ---
JMENA_ZZD_M = [
    "Lišomír",   "Bobřislav",  "Ježobor",   "Vlkomír",   "Srnomil",
    "Krkavoslav","Sovomír",    "Čapibor",   "Lososlav",  "Kaproslav",
    "Modroslav", "Zelenomír",  "Červobor",  "Zlatoslav", "Stříbromil",
    "Duhomír",   "Oblakoslav", "Bouřomír",  "Mrazobor",  "Sněžomil",
    "Kapkoslav", "Vítroslav",  "Mlžomír",   "Drnohryz",  "Krvohlt"
]
assert len(JMENA_ZZD_M) == 25

JMENA_ZZD_F = [
    "Lišomíra",  "Bobřislava",  "Ježobora",   "Vlkomíra",   "Srnomila",
    "Krkavoslava","Sovomíra",   "Čapibora",   "Lososlava",  "Kaproslava",
    "Modroslava", "Zelenomíra", "Červobora",  "Zlatoslava",  "Stříbromila",
    "Duhomíra",  "Oblakoslava", "Bouřomíra",  "Mrazobora",  "Sněžomila",
    "Kapkoslava", "Větroslava",  "Mlžomíra",
]
assert len(JMENA_ZZD_F) == 23

# =============================================================================
# Variace jmenných zásobníků: cizí jména, partikly, dlouhá příjmení
# =============================================================================

# --- Pravděpodobnostní sloty (modulární aritmetika, bez náhodnosti) ----------
_CIZINEC_MOD  = 25   # každý 25. žák z cizí skupiny (3/25 ≈ 12 % celkem)
_SLOT_VIET    = 3    # slot pro vietnamské jméno
_SLOT_UA      = 11   # slot pro ukrajinské jméno
_SLOT_ES      = 18   # slot pro španělské jméno

_DRUHE_MOD    = 14   # druhé křestní jméno: každý 14. (≈ 7 %)
_PARTICLE_MOD = 97   # šlechtický partikl: každý 97. CZ žák (≈ 1 %)
_DLOUHE_MOD   = 53   # hyphenované příjmení: každý 53. CZ žák (≈ 2 %)

PARTICLES = [
    "von", "Van",                        # BakaKeeper: sloučí → vonlanditz.kristen
    "da", "Da", "de", "De", "di", "Di", # BakaKeeper: sloučí → davinci.leonardo
    "al", "Al",                          # BakaKeeper: sloučí → alrashid.xxx
    "bin", "ibn",                        # arabská patronymika – v reálné DB; BakaKeeper jako příjmení
]
_PARTICLES_SET = frozenset(PARTICLES)   # všechny partikly pro generování jmen

# Pouze partikly, které BakaKeeper slučuje s příjmením do loginu (viz BakaUtils.createBaseNameParts).
_MERGE_PARTICLES_SET = frozenset([
    "von", "Van",
    "da", "Da", "de", "De", "di", "Di",
    "al", "Al",
    "bin", "Bin", "ibn", "Ibn",
])

# --- Vietnamská jména --------------------------------------------------------
# Jednoduché křestní jméno bez rodového partiklu (Thị/Văn) – přímočaré
# uložení v DB a bezproblémová tvorba e-mailů.
JMENA_VIET_M  = ["Minh", "Hung", "Nam", "Duc", "Thang", "Dung", "Long",
                  "Anh", "Tuan", "Dat", "Kien", "Hai"]
JMENA_VIET_F  = ["Lan", "Mai", "Hoa", "Ngoc", "Thu", "Linh", "Huong",
                  "Yen", "Thanh", "Hanh", "Phuong", "Nhi"]
PRIJMENI_VIET = ["Nguyen", "Tran", "Le", "Pham", "Hoang", "Phan", "Vu",
                  "Dang", "Bui", "Do", "Ho", "Ngo", "Duong", "Ly", "Dinh"]

# --- Ukrajinská jména (moderní česká transliterace, nařízení 2021) -----------
# Příjmení se v transliteraci neohýbá podle pohlaví (na rozdíl od češtiny).
JMENA_UA_M  = ["Oleksii", "Mykhailo", "Maksym", "Dmytro", "Bohdan", "Yaroslav",
                "Vasyl", "Ivan", "Vladyslav", "Andriy", "Oleh",
                "Taras", "Serhii", "Fylyp"]
JMENA_UA_F  = ["Anastasiia", "Yuliia", "Nataliia", "Viktoriia", "Oksana",
                "Daryna", "Iryna", "Olha", "Svitlana", "Kateryna",
                "Sofiia", "Mariia"]
PRIJMENI_UA = ["Kovalenko", "Melnyk", "Shevchenko", "Boyko", "Tkachenko",
               "Kravchenko", "Bondarenko", "Marchenko", "Lysenko",
               "Petrenko", "Moroz", "Savchenko", "Semenko",
               "Hrytsenko", "Kovalchuk"]

# --- Španělská jména ---------------------------------------------------------
# Španělé mívají dvě příjmení; druhé přidáme u ~35 % španělských žáků.
JMENA_ES_M  = ["Alejandro", "Carlos", "Diego", "Fernando", "Gabriel",
                "Javier", "Luis", "Miguel", "Pablo", "Rodrigo",
                "Sebastián", "Andrés"]
JMENA_ES_F  = ["Alejandra", "Camila", "Daniela", "Elena", "Fernanda",
                "Isabella", "Lucía", "María", "Natalia", "Sofía",
                "Valentina", "Claudia"]
PRIJMENI_ES = ["García", "Martínez", "López", "Sánchez", "González",
               "Rodríguez", "Fernández", "Torres", "Ramírez", "Cruz",
               "Flores", "Herrera", "Morales", "Ortega", "Silva"]

# --- Hyphenovaná příjmení (16–18 znaků) ---------------------------------------
# Tato příjmení způsobí, že kombinace login.jmeno (sAMAccountName)
# překročí 20 znaků a otestuje příslušnou logiku BakaKeeperu.
# Ženské formy se odvozují automaticky přes _f() výše.
PRIJMENI_DLOUHA_M = [
    "Megadlouhopříjmeník",
    "Krkavoslav-Soumrák",    # 18 znaků → login 22–30 z.
    "Blankytnák-Mlžák",      # 16 znaků → login 22–28 z.
    "Svítilník-Oblačník",    # 18 znaků → login 22–30 z.
    "Jehličník-Šedivák",     # 17 znaků → login 21–29 z.
]
PRIJMENI_DLOUHA_F = [_f(p) for p in PRIJMENI_DLOUHA_M]

# =============================================================================
# Pomocné funkce
# =============================================================================

def ascii_slug(text: str) -> str:
    """Převede 'Dvořáček' → 'dvoracek' (lowercase ASCII, bez diakritiky)."""
    nfkd = unicodedata.normalize("NFKD", text)
    return nfkd.encode("ascii", "ignore").decode("ascii").lower()

def email_zam(jmeno: str, prijmeni: str, domain: str) -> str:
    """Formát pro zaměstnance: jmeno.prijmeni@domain."""
    return f"{ascii_slug(jmeno)}.{ascii_slug(prijmeni)}@{domain}"


def login_4p2(prijmeni: str, jmeno: str) -> str:
    """
    Generuje INDOŠ login 4+2: první 4 znaky příjmení + první 2 znaky jména.
    Bez diakritiky, malými písmeny.

    Projekt INDOŠ z roku 2001 formátoval přihlašovací jméno učitele
    jako první čtyři písmena z příjmení a první dvě písmena z křestního jména.

    Příklady:
      Novák Josef  → novajo
      Novák Jan    → novaja
      Vomáčková Vyky → vomavy

    Speciální případy:
      - Pomlčka v příjmení → odstraní se, první část: Krkavoslav-Soumrák → krkav → krkav[:4]=krka
      - Více křestních jmen → první: Jan Pavel → jan[:2]=ja
      - Krátké příjmení (< 4 znaky) → použijí se všechny: Čáp → cap
      - Krátké jméno (< 2 znaky) → použijí se všechny
    """
    # příjmení: pomlčka → mezera → první část → ASCII → lowercase
    prijmeni_norm = prijmeni.replace("-", " ").split()[0]
    sn = ascii_slug(prijmeni_norm)
    # jméno: první křestní → ASCII → lowercase
    gn = ascii_slug(jmeno.split()[0])
    return sn[:4] + gn[:2]


def assign_logins(zamestnanci: list[dict]) -> None:
    """
    Přiřadí unikátní 4+2 login každému zaměstnanci.
    Kolize se řeší číselnou příponou: novajo, novajo2, novajo3 ...
    Výsledek se zapíše do klíče 'LOGIN' v každém slovníku.
    """
    seen: dict[str, int] = {}
    for u in zamestnanci:
        base = login_4p2(u["PRIJMENI"], u["JMENO"])
        n = seen.get(base, 0) + 1
        seen[base] = n
        u["LOGIN"] = base if n == 1 else f"{base}{n}"

def pick(lst: list, idx: int):
    """Cyklicky vybere prvek ze seznamu dle indexu."""
    return lst[idx % len(lst)]

def telefon(n: int) -> str:
    """Vygeneruje vzorové telefonní číslo ve formátu +420 6xx xxx xxx."""
    base = 600_000_000 + n
    s    = str(base)
    return f"+420 {s[:3]} {s[3:6]} {s[6:]}"

def sql_escape(s) -> str:
    """Escapuje single-quote pro T-SQL NVARCHAR string; None → NULL.

    Prefix N'...' zajistí, že SQL Server interpretuje literál jako Unicode (NVARCHAR),
    nikoli přes výchozí code page 1252 (Latin-1), která neobsahuje české znaky
    č, ř, š, ž, ď, ť, ň, ě, ů – bez N prefixu se ztrácí diakritika (např. č→c).
    """
    if s is None:
        return "NULL"
    return "N'" + str(s).replace("'", "''") + "'"

def _assign_email(local: str, seen: dict, domain: str) -> str:
    """
    Přiřadí e-mail ve tvaru local@domain.
    Při druhém výskytu stejného local → local2@domain, třetí → local3@domain atd.
    'seen' je slovník {local: počet_výskytů} – mění se in-place.
    """
    n = seen.get(local, 0) + 1
    seen[local] = n
    return f"{local}@{domain}" if n == 1 else f"{local}{n}@{domain}"


def _email_local(jmeno: str, prijmeni: str) -> str:
    """
    Sestaví local part e-mailu dle pravidel BakaKeeperu
    (odpovídá BakaUtils.createSAMloginFromName).

    Pravidla:
      - Více křestních jmen → první: „Jan Pavel" → „jan"
      - Hyphenované příjmení → pomlčka → mezera → první část:
          „Krkavoslav-Soumrák" → „krkavoslav"  (stejně jako BakaKeeper)
      - Partikl von/Van, da/de/di, al, bin/ibn → SLOUČÍ se s příjmením:
          „von Liščák" → „vonliscak",  „Da Vinci" → „davinci", „bin Rashid" → „binrashid"
      - Složené španělské příjmení → první část: „García Rodríguez" → „garcia"
    """
    # Pomlčka → mezera (BakaKeeper dělá totéž v createBaseNameParts)
    prijmeni_norm = prijmeni.replace("-", " ")
    p_parts = prijmeni_norm.split()
    if len(p_parts) > 1 and p_parts[0] in _MERGE_PARTICLES_SET:
        # BakaKeeper slučuje partikl s příjmením: „von"+"Liščák" → „vonLiščák" → slug
        prijmeni_slug = ascii_slug(p_parts[0] + p_parts[1])
    else:
        prijmeni_slug = ascii_slug(p_parts[0])   # první (primární) příjmení
    jmeno_slug = ascii_slug(jmeno.split()[0])    # první křestní jméno
    return f"{prijmeni_slug}.{jmeno_slug}"

# =============================================================================
# Načítání dat ze souborů
# =============================================================================

def load_tridy(path: str = None) -> list[tuple[int, str]]:
    """
    Načte seznam tříd ze souboru data/tridy.txt.
    Vrací list dvojic (rocnik: int, zkratka: str).
    """
    if path is None:
        path = os.path.join(DATA_DIR, "tridy.txt")
    tridy = []
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            parts = line.split(";")
            if len(parts) < 2:
                continue
            try:
                tridy.append((int(parts[0].strip()), parts[1].strip()))
            except ValueError:
                continue
    return tridy


def load_zaci_staticti(path: str = None) -> list[dict]:
    """
    Načte statické žáky ze souboru data/zaci_staticti.txt.
    Formát řádku: INTERN_KOD;JMENO;PRIJMENI;TRIDA;EVID_DO;E_MAIL

    Statičtí žáci vždy existují – nezávisí na --per-class ani jmenných zásobnících.
    Jsou určeni pro ruční sledování konkrétních scénářů (starší žák, mladší žák,
    absolvent, explicitní e-mail atd.).

    Soubor nemusí existovat – pak funkce vrátí prázdný seznam.

    Ročník se odvodí automaticky z pole TRIDA (např. '9.A' → 9).

    Vrací list slovníků s klíči:
      INTERN_KOD, C_TR_VYK, PRIJMENI, JMENO, TRIDA, EVID_DO,
      _rocnik (dočasný), _static (příznak), _email_explicit (str nebo None)
    """
    if path is None:
        path = os.path.join(DATA_DIR, "zaci_staticti.txt")
    try:
        fh = open(path, encoding="utf-8")
    except FileNotFoundError:
        return []   # soubor neexistuje → žádní statičtí žáci
    staticti = []
    with fh:
        for line in fh:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            parts = line.split(";")
            if len(parts) < 5:
                continue
            kod      = parts[0].strip()
            jmeno    = parts[1].strip()
            prijmeni = parts[2].strip()
            trida    = parts[3].strip()
            evid_do  = parts[4].strip() or None
            email    = parts[5].strip() if len(parts) > 5 else ""
            # Odvodíme ročník z TRIDA (např. "9.A" → 9) pro řazení priority e-mailů
            try:
                rocnik = int(trida.split(".")[0])
            except (ValueError, IndexError):
                rocnik = 0
            staticti.append({
                "INTERN_KOD":      kod,
                "C_TR_VYK":        0,       # statičtí nemají algoritmické pořadí ve třídě
                "PRIJMENI":        prijmeni,
                "JMENO":           jmeno,
                "TRIDA":           trida,
                "_rocnik":         rocnik,
                "EVID_DO":         evid_do,
                "_static":         True,    # příznak pro callers (statistiky atd.)
                "_email_explicit": email or None,
            })
    return staticti


def load_zamestnanci(path: str = None, domain: str = "skola.local") -> list[dict]:
    """
    Načte seznam zaměstnanců ze souboru data/zamestnanci.txt.
    Vrací list slovníků s klíči:
      INTERN_KOD, JMENO, PRIJMENI, OU, FUNKCE, UCI_LETOS, TRIDNI_PRO, E_MAIL, LOGIN

    LOGIN se generuje automaticky jako 4+2 INDOŠ login (příjmení 4 + jméno 2)
    a slouží jako sAMAccountName / CN v Active Directory.
    """
    if path is None:
        path = os.path.join(DATA_DIR, "zamestnanci.txt")
    zamestnanci = []
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            parts = line.split(";")
            if len(parts) < 7:
                continue
            zamestnanci.append({
                "INTERN_KOD":  parts[0].strip(),
                "JMENO":       parts[1].strip(),
                "PRIJMENI":    parts[2].strip(),
                "OU":          parts[3].strip(),
                "FUNKCE":      parts[4].strip(),
                "UCI_LETOS":   int(parts[5].strip() or "0"),
                "TRIDNI_PRO":  parts[6].strip() or None,
                "E_MAIL":      email_zam(parts[1].strip(), parts[2].strip(), domain),
            })
    # vygenerovat unikátní 4+2 loginy
    assign_logins(zamestnanci)
    return zamestnanci

# =============================================================================
# Generátor žáků a zákonných zástupců
# =============================================================================

def gen_zaci(
    tridy: list[tuple[int, str]],
    domain: str,
    per_class: int = ZACI_NA_TRIDU_DEFAULT,
    variance: int = 0,
) -> tuple[list[dict], list[dict]]:
    """
    Generuje žáky a zákonné zástupce.

    Statičtí žáci (z data/zaci_staticti.txt) jsou vždy zahrnuti a prepend-ováni
    před algoritmické žáky ve výsledném listu.

    Počet žáků na třídu: per_class ± variance (deterministicky – liší se třída od třídy).
    Variance 0 = všechny třídy mají přesně per_class žáků.

    Jmenná diverzita (deterministická, bez random()):
      ~4 % vietnamská, ~4 % ukrajinská, ~4 % španělská jména.
      ~7 % CZ/UA/ES žáků má dvě křestní jména.
      ~1 % CZ žáků má šlechtický partikl před příjmením.
      ~2 % CZ žáků má hyphenované příjmení (16–18 znaků, testy sAMAccountName).

    Postup přiřazení e-mailů (tři kroky):
      1. Statičtí žáci s explicitním e-mailem: použije se přímo a zablokuje dedup.
      2. Statičtí žáci bez e-mailu: auto-generovaný, přednost před algoritmickými.
      3. Algoritmičtí sestupně dle ročníku – starší dostane základní tvar,
         mladší se stejným jménem dostane příponu (jan.novak2@domain).

    Zákonní zástupci se generují pouze pro algoritmické žáky.
    Statičtí žáci jsou v result listu označeni klíčem _static=True.

    INTERN_KOD žáka:  Z{05d} (algoritmičtí), ZFIX… (statičtí)
    INTERN_KOD ZZ:    ZZD{05d}
    """
    # --- Statičtí žáci -------------------------------------------------------
    staticti = load_zaci_staticti()

    # --- Fáze 1: vygeneruj algoritmické žáky bez e-mailu --------------------
    students: list[dict] = []
    zak_idx = 0

    for trida_idx, (rocnik, zkratka) in enumerate(tridy):
        # Počet žáků v této třídě – variuje se dle trida_idx a variance.
        # Cyklus délky (2*variance+1) zaručuje, že průměr je per_class.
        if variance > 0:
            n_zaku = max(1, per_class + (trida_idx % (2 * variance + 1)) - variance)
        else:
            n_zaku = per_class

        for poradi in range(1, n_zaku + 1):
            zak_idx += 1
            pohlar_m = (poradi % 2 == 1)

            # --- Výběr zásobníku jmen dle národnosti ---
            nat = zak_idx % _CIZINEC_MOD

            if nat == _SLOT_VIET:
                # Vietnamská jména – bez dvojitého křestního ani partiklu
                jmeno    = pick(JMENA_VIET_M if pohlar_m else JMENA_VIET_F, zak_idx)
                prijmeni = pick(PRIJMENI_VIET, zak_idx)

            elif nat == _SLOT_UA:
                # Ukrajinská jména – příjmení neohýbáme, dvojité křestní ~7 %
                jmeno    = pick(JMENA_UA_M if pohlar_m else JMENA_UA_F, zak_idx)
                prijmeni = pick(PRIJMENI_UA, zak_idx)
                if zak_idx % _DRUHE_MOD == 7:
                    pool2 = JMENA_UA_M if pohlar_m else JMENA_UA_F
                    j2    = pick(pool2, zak_idx + len(pool2) // 2)
                    if j2 != jmeno:
                        jmeno = f"{jmeno} {j2}"

            elif nat == _SLOT_ES:
                # Španělská jména – dvě příjmení ~35 %, dvojité křestní ~7 %
                jmeno    = pick(JMENA_ES_M if pohlar_m else JMENA_ES_F, zak_idx)
                prijmeni = pick(PRIJMENI_ES, zak_idx)
                if zak_idx % 3 == 0:
                    p2 = pick(PRIJMENI_ES, zak_idx + 111)
                    if p2 != prijmeni:
                        prijmeni = f"{prijmeni} {p2}"
                if zak_idx % _DRUHE_MOD == 9:
                    pool2 = JMENA_ES_M if pohlar_m else JMENA_ES_F
                    j2    = pick(pool2, zak_idx + len(pool2) // 2)
                    if j2 != jmeno:
                        jmeno = f"{jmeno} {j2}"

            else:
                # České fantasy jméno
                if pohlar_m:
                    jmeno    = pick(JMENA_ZAK_M, zak_idx)
                    prijmeni = pick(PRIJMENI_ZAK_M, zak_idx)
                else:
                    jmeno    = pick(JMENA_ZAK_F, zak_idx)
                    prijmeni = pick(PRIJMENI_ZAK_F, zak_idx)

                # Hyphenované příjmení (~2 %) – testuje >20-znakový sAMAccountName
                if zak_idx % _DLOUHE_MOD == 0:
                    dl = PRIJMENI_DLOUHA_M if pohlar_m else PRIJMENI_DLOUHA_F
                    prijmeni = pick(dl, zak_idx)
                # Šlechtický partikl (~1 %) – vzájemně se vylučuje s dlouhým příjmením
                elif zak_idx % _PARTICLE_MOD == 0:
                    prijmeni = f"{pick(PARTICLES, zak_idx)} {prijmeni}"

                # Druhé křestní jméno (~7 %)
                if zak_idx % _DRUHE_MOD == 5:
                    pool2 = JMENA_ZAK_M if pohlar_m else JMENA_ZAK_F
                    j2    = pick(pool2, zak_idx + len(pool2) // 2)
                    if j2 != jmeno:
                        jmeno = f"{jmeno} {j2}"

            students.append({
                "INTERN_KOD": f"Z{zak_idx:05d}",
                "C_TR_VYK":   poradi,
                "PRIJMENI":   prijmeni,
                "JMENO":      jmeno,
                "TRIDA":      zkratka,
                "_rocnik":    rocnik,   # dočasný klíč pro řazení
                "EVID_DO":    None,
            })

    # Absolventi (žáci s ukončenou evidencí) – přiřazeni do poslední třídy ročníku 9
    tridy_9      = [(r, z) for r, z in tridy if r == 9]
    absolv_trida = tridy_9[-1][1] if tridy_9 else (tridy[-1][1] if tridy else "9.A")
    for i in range(ABSOLVENTI):
        zak_idx += 1
        jmeno    = pick(JMENA_ZAK_M, zak_idx + 200)
        prijmeni = pick(PRIJMENI_ZAK_M, zak_idx + 200)
        students.append({
            "INTERN_KOD": f"Z{zak_idx:05d}",
            "C_TR_VYK":   90 + i + 1,
            "PRIJMENI":   prijmeni,
            "JMENO":      jmeno,
            "TRIDA":      absolv_trida,
            "_rocnik":    9,
            "EVID_DO":    EVID_DO_ABSOLVENT,
        })

    # --- Fáze 2: přiřaď e-maily ----------------------------------------------
    email_seen: dict[str, int] = {}

    # 2a) Statičtí žáci s explicitním e-mailem – absolutní přednost.
    #     E-mail se zaregistruje, aby nikdo jiný nezískal stejnou adresu.
    for s in staticti:
        explicit = s.pop("_email_explicit")
        if explicit:
            s["E_MAIL"] = explicit
            local = explicit.split("@")[0]
            email_seen[local] = email_seen.get(local, 0) + 1
        else:
            s["_needs_email"] = True    # zpracuje krok 2b

    # 2b) Statičtí bez e-mailu + všichni algoritmičtí – sestupně dle ročníku.
    #     Statičtí mají vyšší prioritu než algoritmičtí při shodném ročníku
    #     (sort key: vyšší ročník → menší číslo; statický → 0, algoritmický → 1).
    needs_email = (
        [s for s in staticti if s.pop("_needs_email", False)]
        + students
    )
    needs_email.sort(
        key=lambda x: (-x["_rocnik"], 0 if x.get("_static") else 1, x["TRIDA"])
    )
    for s in needs_email:
        local = _email_local(s["JMENO"], s["PRIJMENI"])
        s["E_MAIL"] = _assign_email(local, email_seen, domain)

    # Odstraníme dočasný klíč _rocnik; _static záměrně ponecháme pro callers.
    for s in staticti + students:
        s.pop("_rocnik", None)

    # --- Zákonní zástupci – pouze pro algoritmické žáky ---------------------
    zzd: list[dict] = []
    zzd_idx        = 0
    zzd_email_seen: dict[str, int] = {}

    for s in students:
        zzd_idx += 1
        pohlar_zzd_m = (zzd_idx % 3 != 0)   # 2/3 otcové, 1/3 matky
        if pohlar_zzd_m:
            jmeno_zzd    = pick(JMENA_ZZD_M, zzd_idx)
            prijmeni_zzd = pick(PRIJMENI_ZZD_M, zzd_idx)
        else:
            jmeno_zzd    = pick(JMENA_ZZD_F, zzd_idx)
            prijmeni_zzd = pick(PRIJMENI_ZZD_F, zzd_idx)

        local_zzd = f"{ascii_slug(prijmeni_zzd)}.{ascii_slug(jmeno_zzd)}"
        email_zzd = _assign_email(local_zzd, zzd_email_seen, "example.com")

        zzd.append({
            "ID":        f"ZZD{zzd_idx:05d}",
            "JMENO":     jmeno_zzd,
            "PRIJMENI":  prijmeni_zzd,
            "TEL_MOBIL": telefon(zzd_idx),
            "E_MAIL":    email_zzd,
            "ZAK_KOD":   s["INTERN_KOD"],
        })

    # Statičtí žáci jsou na začátku výsledného listu (zachován _static příznak)
    return staticti + students, zzd

# =============================================================================
# SQL generátory
# =============================================================================

def sql_ucitele(zamestnanci: list[dict]) -> list[str]:
    lines = []
    for u in zamestnanci:
        lines.append(
            f"INSERT INTO dbo.ucitele (INTERN_KOD, JMENO, PRIJMENI, E_MAIL, UCI_LETOS)\n"
            f"SELECT {sql_escape(u['INTERN_KOD'])}, {sql_escape(u['JMENO'])}, "
            f"{sql_escape(u['PRIJMENI'])}, {sql_escape(u['E_MAIL'])}, {u['UCI_LETOS']}\n"
            f"WHERE NOT EXISTS "
            f"(SELECT 1 FROM dbo.ucitele WHERE INTERN_KOD = {sql_escape(u['INTERN_KOD'])});"
        )
    return lines


def sql_tridy(tridy: list[tuple[int, str]], zamestnanci: list[dict]) -> list[str]:
    tridni_map = {
        u["TRIDNI_PRO"]: u["INTERN_KOD"]
        for u in zamestnanci if u.get("TRIDNI_PRO")
    }
    lines = []
    for rocnik, zkratka in tridy:
        tridnictvi = tridni_map.get(zkratka)
        trid_val   = sql_escape(tridnictvi) if tridnictvi else "NULL"
        lines.append(
            f"INSERT INTO dbo.tridy (ROCNIK, ZKRATKA, TRIDNICTVI)\n"
            f"SELECT {rocnik}, {sql_escape(zkratka)}, {trid_val}\n"
            f"WHERE NOT EXISTS "
            f"(SELECT 1 FROM dbo.tridy WHERE ZKRATKA = {sql_escape(zkratka)});"
        )
    return lines


def sql_zaci(zaci: list[dict]) -> list[str]:
    lines = []
    for z in zaci:
        lines.append(
            f"INSERT INTO dbo.zaci (INTERN_KOD, C_TR_VYK, PRIJMENI, JMENO, TRIDA, E_MAIL, EVID_DO)\n"
            f"SELECT {sql_escape(z['INTERN_KOD'])}, {z['C_TR_VYK']}, "
            f"{sql_escape(z['PRIJMENI'])}, {sql_escape(z['JMENO'])}, "
            f"{sql_escape(z['TRIDA'])}, {sql_escape(z['E_MAIL'])}, "
            f"{sql_escape(z['EVID_DO'])}\n"
            f"WHERE NOT EXISTS "
            f"(SELECT 1 FROM dbo.zaci WHERE INTERN_KOD = {sql_escape(z['INTERN_KOD'])});"
        )
    return lines


def sql_zzd(zzd_list: list[dict]) -> list[str]:
    lines = []
    for z in zzd_list:
        lines.append(
            f"INSERT INTO dbo.zaci_zzd (ID, JMENO, PRIJMENI, TEL_MOBIL, E_MAIL)\n"
            f"SELECT {sql_escape(z['ID'])}, {sql_escape(z['JMENO'])}, "
            f"{sql_escape(z['PRIJMENI'])}, {sql_escape(z['TEL_MOBIL'])}, "
            f"{sql_escape(z['E_MAIL'])}\n"
            f"WHERE NOT EXISTS "
            f"(SELECT 1 FROM dbo.zaci_zzd WHERE ID = {sql_escape(z['ID'])});"
        )
    return lines


def sql_zzr(zzd_list: list[dict]) -> list[str]:
    lines = []
    for z in zzd_list:
        lines.append(
            f"INSERT INTO dbo.zaci_zzr (INTERN_KOD, ID_ZZ, JE_ZZ, PRIMARNI)\n"
            f"SELECT {sql_escape(z['ZAK_KOD'])}, {sql_escape(z['ID'])}, 1, 1\n"
            f"WHERE NOT EXISTS (SELECT 1 FROM dbo.zaci_zzr "
            f"WHERE INTERN_KOD = {sql_escape(z['ZAK_KOD'])} "
            f"AND ID_ZZ = {sql_escape(z['ID'])});"
        )
    return lines


def build_sql(domain: str, sql_db: str, per_class: int, variance: int = 0) -> str:
    tridy       = load_tridy()
    zamestnanci = load_zamestnanci(domain=domain)
    zaci, zzd   = gen_zaci(tridy, domain, per_class, variance)

    sections = [f"USE {sql_db};\nGO\n"]

    sections += [
        "-- -------------------------------------------------------\n"
        "-- Zaměstnanci → dbo.ucitele\n"
        "-- (vedení, učitelé, asistenti, vychovatelky, provoz)\n"
        "-- -------------------------------------------------------",
        *sql_ucitele(zamestnanci),
        "GO\n",
    ]
    sections += [
        "-- -------------------------------------------------------\n"
        "-- Třídy s vazbou na třídního učitele\n"
        "-- -------------------------------------------------------",
        *sql_tridy(tridy, zamestnanci),
        "GO\n",
    ]
    sections += [
        "-- -------------------------------------------------------\n"
        "-- Žáci\n"
        "-- -------------------------------------------------------",
        *sql_zaci(zaci),
        "GO\n",
    ]
    sections += [
        "-- -------------------------------------------------------\n"
        "-- Zákonní zástupci\n"
        "-- -------------------------------------------------------",
        *sql_zzd(zzd),
        "GO\n",
    ]
    sections += [
        "-- -------------------------------------------------------\n"
        "-- Vazby žák–zákonný zástupce\n"
        "-- -------------------------------------------------------",
        *sql_zzr(zzd),
        "GO\n",
    ]

    staticti_n = sum(1 for z in zaci if z.get("_static"))
    aktivni    = sum(1 for z in zaci if z["EVID_DO"] is None)
    absolventi = sum(1 for z in zaci if z["EVID_DO"] is not None)
    sections.append(
        f"PRINT 'Seed hotov – zaměstnanci: {len(zamestnanci)}, "
        f"třídy: {len(tridy)}, žáci: {aktivni} aktivní + {absolventi} absolventi "
        f"({staticti_n} statických), ZZ: {len(zzd)}';\nGO"
    )
    return "\n".join(sections)

# =============================================================================
# AD (samba-tool) generátor
# =============================================================================

def ou_dn(ou: str, base_dn: str) -> str:
    """Sestaví relativní cestu k cílové OU zaměstnance v AD.
    samba-tool --userou vyžaduje cestu BEZ DC= části (relativní od base DN)."""
    return f"OU={ou},OU=Zamestnanci,OU=Uzivatele,OU=Skola"


def build_ad_sh(domain: str, base_dn: str, ad_password: str) -> str:
    """
    Generuje shell skript se samba-tool příkazy pro AD účty zaměstnanců.
    Žáky vytváří BakaKeeper synchronizací – zde je nevytváříme.

    Každý zaměstnanec dostane realistický 4+2 INDOŠ login jako sAMAccountName
    (odpovídá praxi českých škol – učitelské účty od roku 2001).
    UPN = login@doména, e-mail = jmeno.prijmeni@doména.
    """
    zamestnanci = load_zamestnanci(domain=domain)

    lines = [
        "#!/bin/bash",
        "# Automaticky generováno skriptem testdata.py – NEUPRAVUJTE RUČNĚ",
        "# Vytváří AD účty zaměstnanců v Samba4 AD DC",
        "# Login = 4+2 INDOŠ (příjmení 4 + jméno 2), e-mail = jmeno.prijmeni@doména",
        "set -euo pipefail",
        "",
        f'BASE_DN="{base_dn}"',
        f'AD_PASSWORD="{ad_password}"',
        "",
    ]

    for u in zamestnanci:
        ou_path  = ou_dn(u["OU"], base_dn)
        login    = u["LOGIN"]
        kod      = u["INTERN_KOD"]
        jmeno    = u["JMENO"]
        prijmeni = u["PRIJMENI"]
        mail     = u["E_MAIL"]
        funkce   = u["FUNKCE"].upper()
        # displayName v českém formátu: Příjmení Jméno
        display  = f"{prijmeni} {jmeno}"
        lines += [
            f"# {funkce}: {display} ({kod}) → login: {login}",
            f"_out=$(samba-tool user create '{login}' \"${{AD_PASSWORD}}\" \\",
            f"    --given-name='{jmeno}' \\",
            f"    --surname='{prijmeni}' \\",
            f"    --mail-address='{mail}' \\",
            f"    --userou='{ou_path}' 2>&1) \\",
            f"    && echo '  [+] {login} ({kod})' \\",
            f"    || {{ if echo \"$_out\" | grep -qi 'already exists'; then "
            f"echo '  [=] {login} ({kod}) (již existuje)'; "
            f"else echo \"  [!] {login} ({kod}): $_out\" >&2; fi; }}",
            f"samba-tool user setpassword '{login}'"
            f" --newpassword=\"${{AD_PASSWORD}}\" 2>/dev/null || true",
            "",
        ]

    lines.append("echo '[BakaDev] AD seed hotov.'")
    return "\n".join(lines)

# =============================================================================
# main
# =============================================================================

def main() -> None:
    parser = argparse.ArgumentParser(
        description="BakaKeeper dev seed generátor testovacích dat"
    )
    parser.add_argument("--sql-file",   metavar="CESTA",   help="Výstupní SQL soubor")
    parser.add_argument("--sh-file",    metavar="CESTA",   help="Výstupní AD shell skript")
    parser.add_argument("--domain",     default="skola.local",       help="Interní AD doména")
    parser.add_argument("--base-dn",    default="DC=skola,DC=local",  help="Base DN")
    parser.add_argument("--ad-pass",    default="BakaKeeper.2026",    help="Heslo AD účtů zaměstnanců")
    parser.add_argument("--sql-db",     default="bakalari",           help="Název SQL databáze")
    parser.add_argument("--per-class",  type=int, default=ZACI_NA_TRIDU_DEFAULT,
                        metavar="N",    help=f"Průměr žáků na třídu (výchozí: {ZACI_NA_TRIDU_DEFAULT})")
    parser.add_argument("--variance",   type=int, default=0,
                        metavar="V",    help="Rozptyl počtu žáků na třídu – každá třída dostane "
                                             "N±V žáků (výchozí: 0 = všechny třídy stejné)")
    parser.add_argument("--stats",      action="store_true",          help="Vypíše statistiku a skončí")
    args = parser.parse_args()

    if not (1 <= args.per_class <= 50):
        print(f"Chyba: --per-class musí být 1–50 (zadáno: {args.per_class})", file=sys.stderr)
        sys.exit(1)
    if not (0 <= args.variance < args.per_class):
        print(f"Chyba: --variance musí být 0 až per-class-1 (zadáno: {args.variance}, per-class: {args.per_class})",
              file=sys.stderr)
        sys.exit(1)

    if args.stats:
        tridy       = load_tridy()
        zamestnanci = load_zamestnanci(domain=args.domain)
        zaci, zzd   = gen_zaci(tridy, args.domain, args.per_class, args.variance)
        vedeni      = [z for z in zamestnanci if z["OU"] == "Vedeni"]
        ucitele_tr  = [z for z in zamestnanci if z["OU"] == "Ucitele" and z["TRIDNI_PRO"]]
        ucitele_ntr = [z for z in zamestnanci if z["OU"] == "Ucitele" and not z["TRIDNI_PRO"]]
        asistenti   = [z for z in zamestnanci if z["OU"] == "Asistenti"]
        vychovatelky= [z for z in zamestnanci if z["OU"] == "Vychovatelky"]
        provoz      = [z for z in zamestnanci if z["OU"] == "Provoz"]
        staticti    = [z for z in zaci if z.get("_static")]
        aktivni     = [z for z in zaci if z["EVID_DO"] is None]
        absolventi  = [z for z in zaci if z["EVID_DO"] is not None]
        algo_zaci   = [z for z in zaci if not z.get("_static")]
        # Kolik e-mailů je s číselnou příponou (duplicitní jméno)
        duplic = [z for z in zaci if z["E_MAIL"][0].isalpha() and
                  any(c.isdigit() for c in z["E_MAIL"].split("@")[0])]
        # Jmenná diverzita – počty dle národnosti (odhad dle INTERN_KOD řady)
        cizinci = sum(1 for z in algo_zaci
                      if not any(c in "áéíóúůýžšřčďťňěĺĽäÄöÖüÜ" for c in z.get("PRIJMENI", ""))
                      and not z["INTERN_KOD"].startswith("ZFIX"))
        per_class_range = (
            f"{args.per_class}±{args.variance}" if args.variance > 0
            else str(args.per_class)
        )
        print(f"Třídy:          {len(tridy)}  (průměr {per_class_range} žáků/třídu)")
        print(f"Vedení:         {len(vedeni)}")
        print(f"Učitelé tř.:    {len(ucitele_tr)}")
        print(f"Učitelé ntr.:   {len(ucitele_ntr)}")
        print(f"Asistenti:      {len(asistenti)}")
        print(f"Vychovatelky:   {len(vychovatelky)}")
        print(f"Provoz:         {len(provoz)}")
        print(f"Zaměstnanci Σ:  {len(zamestnanci)}")
        # loginové kolize (4+2)
        login_kolize = sum(1 for u in zamestnanci if any(c.isdigit() for c in u["LOGIN"]))
        print(f"Login kolize:   {login_kolize}  (4+2 s číselnou příponou)")
        print(f"Statičtí žáci:  {len(staticti)}")
        print(f"Žáci aktivní:   {len(aktivni)}  (vč. {sum(1 for z in staticti if z['EVID_DO'] is None)} statických)")
        print(f"Žáci absol.:    {len(absolventi)}  (vč. {sum(1 for z in staticti if z['EVID_DO'] is not None)} statických)")
        print(f"ZZ celkem:      {len(zzd)}  (jen algoritmičtí žáci)")
        print(f"Duplik. e-mail: {len(duplic)}  (dostaly číslo)")
        return

    sql_content = build_sql(args.domain, args.sql_db, args.per_class, args.variance)
    if args.sql_file:
        with open(args.sql_file, "w", encoding="utf-8") as f:
            f.write(sql_content)
        print(f"[OK] SQL zapsán do: {args.sql_file}", file=sys.stderr)
    else:
        print(sql_content)

    sh_content = build_ad_sh(args.domain, args.base_dn, args.ad_pass)
    if args.sh_file:
        with open(args.sh_file, "w", encoding="utf-8") as f:
            f.write(sh_content)
        print(f"[OK] AD skript zapsán do: {args.sh_file}", file=sys.stderr)


if __name__ == "__main__":
    main()
