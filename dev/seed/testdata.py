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
# Velikosti zásobníků jsou navzájem prvočíselné → LCM >> počet generovaných
# entit, takže systematické opakování kombinací je minimální:
#   žáci:  JMENA 31 (prvočíslo) × PRIJMENI 59 (prvočíslo) → LCM = 1 829
#   ZZD:   JMENA 23 (prvočíslo) × PRIJMENI 41 (prvočíslo) → LCM = 943
# =============================================================================

# --- Žáci – mužská příjmení (59): inspirována zvířaty, přírodou, barvami ---
PRIJMENI_ZAK_M = [
    # zvířata
    "Liščák",    "Bobřík",    "Ježura",     "Vydří",     "Vlkoun",
    "Srnec",     "Krtek",     "Jelen",      "Tchoř",     "Jezevec",
    "Krkavec",   "Sovák",     "Čáp",        "Ledňák",    "Drozd",
    "Losos",     "Kapr",      "Štičák",     "Rak",       "Šnek",
    # barvy a světlo
    "Modrák",    "Zeleník",   "Červín",     "Fialák",    "Žlutín",
    "Blankytnák","Šedivák",   "Béžák",      "Rumín",     "Zlatník",
    "Stříbrník", "Perleťák",  "Duhovec",    "Soumrák",   "Svítilník",
    # příroda a počasí
    "Dubový",    "Jehličník", "Kůrovec",    "Lupínek",   "Kořínek",
    "Oblačník",  "Bouřák",    "Mrazík",     "Sněhulák",  "Kapkoun",
    "Vítrník",   "Mlžák",     "Úsvit",      "Zářínek",   "Polárník",
    # vymyšlené
    "Vrtulník",  "Klouzák",   "Pírček",     "Bublánek",  "Šuškánek",
    "Trpaslik",  "Skřítkov",  "Mlsáček",    "Bubloun",
]
assert len(PRIJMENI_ZAK_M) == 59

# Ženská příjmení odvozena z mužských
def _f(s: str) -> str:
    if s.endswith("cký"): return s[:-3] + "cká"
    if s.endswith("ský"): return s[:-3] + "ská"
    if s.endswith("ký"):  return s[:-2] + "ká"
    if s.endswith("ý"):   return s[:-1] + "á"
    if s.endswith("a"):   return s[:-1] + "ová"
    return s + "ová"

PRIJMENI_ZAK_F = [_f(p) for p in PRIJMENI_ZAK_M]

# --- Žáci – křestní jména (31): kaledářové variace + vymyšlené přezdívky ---
JMENA_ZAK_M = [
    # kaledářové variace (–oslav, –mir, –boj atd.)
    "Radoušek",  "Světlomír",  "Kvítoslav",  "Zoroslav",   "Bouřeslav",
    "Mlžimír",   "Duhomil",    "Zlatoslav",  "Oblakoslav", "Rákosslav",
    # z přírody
    "Duboslav",  "Jehličmír",  "Kapkoslav",  "Mrazimír",   "Sněžoslav",
    # zkráceniny a přezdívky
    "Ríša",      "Fikus",      "Kubi",       "Bóbi",       "Cílek",
    "Krteček",   "Šupin",      "Pírko",      "Dubin",      "Brouk",
    # zcela vymyšlená
    "Zlumek",    "Vrkos",      "Bublík",     "Šmudla",     "Chroust",
    "Hoblík",
]
assert len(JMENA_ZAK_M) == 31

JMENA_ZAK_F = [
    # kaledářové variace (–slava, –mila, –na)
    "Duhoslava",  "Mlžena",     "Kvítoslava",  "Zorena",     "Ryboslava",
    "Bublinava",  "Kapkoslava",  "Sněžena",     "Zlatoslava",  "Oblačena",
    # z přírody
    "Jehličena",  "Rákosena",   "Mrazivá",     "Švestena",    "Lipena",
    # zkráceniny a přezdívky
    "Vyky",       "Krustýna",   "Zuběna",      "Bubka",       "Šnečka",
    "Motýlena",   "Broukata",   "Rybena",      "Lupínka",     "Drobka",
    # zcela vymyšlená
    "Šmudlenka",  "Lišejka",    "Pírečka",     "Mlsnička",    "Bublinka",
    "Chroústka",
]
assert len(JMENA_ZAK_F) == 31

# --- Zákonní zástupci – příjmení (41): starší, serióznější variace ---
PRIJMENI_ZZD_M = [
    # zvířata (dospělejší formy)
    "Liščí",     "Bobří",     "Ježkový",    "Vydří",     "Vlčí",
    "Srní",      "Krtí",      "Jelení",     "Tchoří",    "Jezevčí",
    "Krkavčí",   "Soví",      "Čapí",       "Lososí",    "Kaprový",
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
    "Modroslav",  "Zelenomír", "Červobor",  "Zlatoslav",  "Stříbromil",
    "Duhomír",   "Oblakoslav", "Bouřomír",  "Mrazobor",  "Sněžomil",
    "Kapkoslav",  "Vítroslav",  "Mlžomír",
]
assert len(JMENA_ZZD_M) == 23

JMENA_ZZD_F = [
    "Lišomíra",  "Bobřislava",  "Ježobora",   "Vlkomíra",   "Srnomila",
    "Krkavoslava","Sovomíra",   "Čapibora",   "Lososlava",  "Kaproslava",
    "Modroslava", "Zelenomíra", "Červobora",  "Zlatoslava",  "Stříbromila",
    "Duhomíra",  "Oblakoslava", "Bouřomíra",  "Mrazobora",  "Sněžomila",
    "Kapkoslava", "Vítroslava",  "Mlžomíra",
]
assert len(JMENA_ZZD_F) == 23

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

def pick(lst: list, idx: int):
    """Cyklicky vybere prvek ze seznamu dle indexu."""
    return lst[idx % len(lst)]

def telefon(n: int) -> str:
    """Vygeneruje vzorové telefonní číslo ve formátu +420 6xx xxx xxx."""
    base = 600_000_000 + n
    s    = str(base)
    return f"+420 {s[:3]} {s[3:6]} {s[6:]}"

def sql_escape(s) -> str:
    """Escapuje single-quote pro T-SQL string; None → NULL."""
    if s is None:
        return "NULL"
    return "'" + str(s).replace("'", "''") + "'"

def _assign_email(local: str, seen: dict, domain: str) -> str:
    """
    Přiřadí e-mail ve tvaru local@domain.
    Při druhém výskytu stejného local → local2@domain, třetí → local3@domain atd.
    'seen' je slovník {local: počet_výskytů} – mění se in-place.
    """
    n = seen.get(local, 0) + 1
    seen[local] = n
    return f"{local}@{domain}" if n == 1 else f"{local}{n}@{domain}"

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
      INTERN_KOD, JMENO, PRIJMENI, OU, FUNKCE, UCI_LETOS, TRIDNI_PRO, E_MAIL
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
    return zamestnanci

# =============================================================================
# Generátor žáků a zákonných zástupců
# =============================================================================

def gen_zaci(
    tridy: list[tuple[int, str]],
    domain: str,
    per_class: int = ZACI_NA_TRIDU_DEFAULT,
) -> tuple[list[dict], list[dict]]:
    """
    Generuje žáky a zákonné zástupce.

    Statičtí žáci (z data/zaci_staticti.txt) jsou vždy zahrnuti a prepend-ováni
    před algoritmické žáky ve výsledném listu.

    Postup přiřazení e-mailů (tři kroky):
      1. Statičtí žáci s explicitním e-mailem: e-mail se použije přímo
         a zaregistruje se do dedup slovníku (blokuje přiřazení stejné adresy
         dalším žákům).
      2. Statičtí žáci bez explicitního e-mailu: auto-generovaný e-mail
         s předností před všemi algoritmickými žáky (stejný ročník → statický vyhraje).
      3. Algoritmičtí žáci sestupně dle ročníku – starší žák (vyšší ročník)
         dostane základní tvar (jan.novak@domain), mladší se stejným jménem
         dostane příponu (jan.novak2@domain).

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

    for rocnik, zkratka in tridy:
        for poradi in range(1, per_class + 1):
            zak_idx += 1
            pohlar_m = (poradi % 2 == 1)
            if pohlar_m:
                jmeno    = pick(JMENA_ZAK_M, zak_idx)
                prijmeni = pick(PRIJMENI_ZAK_M, zak_idx)
            else:
                jmeno    = pick(JMENA_ZAK_F, zak_idx)
                prijmeni = pick(PRIJMENI_ZAK_F, zak_idx)
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
        local = f"{ascii_slug(s['PRIJMENI'])}.{ascii_slug(s['JMENO'])}"
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


def build_sql(domain: str, sql_db: str, per_class: int) -> str:
    tridy       = load_tridy()
    zamestnanci = load_zamestnanci(domain=domain)
    zaci, zzd   = gen_zaci(tridy, domain, per_class)

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
    """Sestaví plné DN cílové OU zaměstnance v AD."""
    return f"OU={ou},OU=Zamestnanci,OU=Uzivatele,OU=Skola,{base_dn}"


def build_ad_sh(domain: str, base_dn: str, ad_password: str) -> str:
    """
    Generuje shell skript se samba-tool příkazy pro AD účty zaměstnanců.
    Žáky vytváří BakaKeeper synchronizací – zde je nevytváříme.
    """
    zamestnanci = load_zamestnanci(domain=domain)

    lines = [
        "#!/bin/bash",
        "# Automaticky generováno skriptem testdata.py – NEUPRAVUJTE RUČNĚ",
        "# Vytváří AD účty zaměstnanců v Samba4 AD DC",
        "set -euo pipefail",
        "",
        f'BASE_DN="{base_dn}"',
        f'AD_PASSWORD="{ad_password}"',
        "",
    ]

    for u in zamestnanci:
        ou_path  = ou_dn(u["OU"], base_dn)
        kod      = u["INTERN_KOD"]
        jmeno    = u["JMENO"]
        prijmeni = u["PRIJMENI"]
        mail     = u["E_MAIL"]
        funkce   = u["FUNKCE"].upper()
        lines += [
            f"# {funkce}: {jmeno} {prijmeni} ({kod})",
            f"samba-tool user create '{kod}' \"${{AD_PASSWORD}}\" \\",
            f"    --given-name='{jmeno}' \\",
            f"    --surname='{prijmeni}' \\",
            f"    --mail-address='{mail}' \\",
            f"    --userou='{ou_path}' 2>/dev/null \\",
            f"    && echo '  [+] {kod}' \\",
            f"    || echo '  [=] {kod} (již existuje)'",
            f"samba-tool user setpassword '{kod}'"
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
                        metavar="N",    help=f"Počet žáků ve třídě (výchozí: {ZACI_NA_TRIDU_DEFAULT})")
    parser.add_argument("--stats",      action="store_true",          help="Vypíše statistiku a skončí")
    args = parser.parse_args()

    if not (1 <= args.per_class <= 20):
        print(f"Chyba: --per-class musí být 1–20 (zadáno: {args.per_class})", file=sys.stderr)
        sys.exit(1)

    if args.stats:
        tridy       = load_tridy()
        zamestnanci = load_zamestnanci(domain=args.domain)
        zaci, zzd   = gen_zaci(tridy, args.domain, args.per_class)
        vedeni      = [z for z in zamestnanci if z["OU"] == "Vedeni"]
        ucitele_tr  = [z for z in zamestnanci if z["OU"] == "Ucitele" and z["TRIDNI_PRO"]]
        ucitele_ntr = [z for z in zamestnanci if z["OU"] == "Ucitele" and not z["TRIDNI_PRO"]]
        asistenti   = [z for z in zamestnanci if z["OU"] == "Asistenti"]
        vychovatelky= [z for z in zamestnanci if z["OU"] == "Vychovatelky"]
        provoz      = [z for z in zamestnanci if z["OU"] == "Provoz"]
        staticti    = [z for z in zaci if z.get("_static")]
        aktivni     = [z for z in zaci if z["EVID_DO"] is None]
        absolventi  = [z for z in zaci if z["EVID_DO"] is not None]
        # Zjistíme, kolik e-mailů je s číselnou příponou (duplicitní jméno)
        duplic = [z for z in zaci if z["E_MAIL"][0].isalpha() and
                  any(c.isdigit() for c in z["E_MAIL"].split("@")[0])]
        print(f"Třídy:          {len(tridy)}  (× {args.per_class} žáků/třídu)")
        print(f"Vedení:         {len(vedeni)}")
        print(f"Učitelé tř.:    {len(ucitele_tr)}")
        print(f"Učitelé ntr.:   {len(ucitele_ntr)}")
        print(f"Asistenti:      {len(asistenti)}")
        print(f"Vychovatelky:   {len(vychovatelky)}")
        print(f"Provoz:         {len(provoz)}")
        print(f"Zaměstnanci Σ:  {len(zamestnanci)}")
        print(f"Statičtí žáci:  {len(staticti)}")
        print(f"Žáci aktivní:   {len(aktivni)}  (vč. {sum(1 for z in staticti if z['EVID_DO'] is None)} statických)")
        print(f"Žáci absol.:    {len(absolventi)}  (vč. {sum(1 for z in staticti if z['EVID_DO'] is not None)} statických)")
        print(f"ZZ celkem:      {len(zzd)}  (jen algoritmičtí žáci)")
        print(f"Duplik. e-mail: {len(duplic)}  (dostaly číslo)")
        return

    sql_content = build_sql(args.domain, args.sql_db, args.per_class)
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
