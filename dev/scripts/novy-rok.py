#!/usr/bin/env python3
# =============================================================================
# BakaKeeper – testovací scénář: Nový školní rok
#
# Simuluje chování aplikace Bakaláři při povýšení školního roku:
#
#   1. Smaže všechny aktivní žáky 9. ročníku a jejich zákonné zástupce
#   2. Provede kaskádu přesunů ročníků (shora dolů):
#      8→9, 7→8, 6→7, 5→6 (shuffle), 4→5, 3→4, 2→3, 1→2
#      – při přechodu 5→6 se třídy náhodně přerozdělí (4 třídy → 3)
#      – při přechodu 2→3 se žáci z přebývající 2.E přerozdělí do 3.A–3.D
#   3. Vytvoří nové žáky 1. ročníku s prázdným E_MAIL a jejich zákonné zástupce
#   4. Přečísluje třídní výkazy (C_TR_VYK) abecedně v každé třídě
#   5. Aktualizuje třídní učitele:
#      – učitelé starého 9. a 5. ročníku (a 2.E) ztratí třídnictví
#      – do nového 6. a 1. ročníku jsou náhodně přiřazeni netřídní učitelé
#
# Očekávané chování BakaKeeperu po synchronizaci:
#   – Neexistující záznamy (původní 9. ročník) → přesun do
#     OU=<rok>,OU=StudiumUkonceno,OU=Zaci,...; deaktivace, odebrání ze skupin
#   – Kaskáda přesunů žáků do odpovídajících OU a skupin
#   – Nový 1. ročník: spárování záznamů, vytvoření v AD, zápis E_MAIL do SQL
#
# Použití:
#   cd dev && python3 scripts/novy-rok.py                  # dry-run (vypíše SQL)
#   cd dev && python3 scripts/novy-rok.py --apply          # aplikuje na kontejner
#   cd dev && python3 scripts/novy-rok.py --per-class 8    # více prvňáčků/třídu
#   cd dev && python3 scripts/novy-rok.py --seed 67        # reprodukovatelný běh
# =============================================================================

import argparse
import os
import random
import subprocess
import sys
import tempfile

# Import z generátoru testovacích dat (dev/seed/testdata.py)
_SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
_DEV_DIR = os.path.dirname(_SCRIPT_DIR)
sys.path.insert(0, os.path.join(_DEV_DIR, "seed"))
import testdata

# =============================================================================
# Konfigurace
# =============================================================================

NOVE_ZACI_NA_TRIDU = 5       # výchozí počet nových prvňáčků na třídu
NOVE_ZAK_START_IDX = 900     # INTERN_KOD nových žáků: Z00901, Z00902, ...
NOVE_ZZD_START_IDX = 900     # ID nových zákonných zástupců: ZZD00901, ...

# Barevný výstup (odpovídá setup-dev.sh)
_OK   = "\033[0;32m[OK]\033[0m"
_INFO = "\033[0;34m[INFO]\033[0m"
_ERR  = "\033[0;31m[ERR]\033[0m"

# =============================================================================
# Načtení prostředí
# =============================================================================

def load_env() -> dict:
    """Načte proměnné z dev/.env."""
    env = {}
    env_path = os.path.join(_DEV_DIR, ".env")
    if not os.path.isfile(env_path):
        print(f"{_ERR} Soubor .env nenalezen: {env_path}", file=sys.stderr)
        sys.exit(1)
    with open(env_path) as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            if "=" in line:
                k, v = line.split("=", 1)
                env[k.strip()] = v.strip()
    return env


# =============================================================================
# Generátor SQL
# =============================================================================

def build_sql(domain: str, sql_db: str, per_class: int, seed: int) -> str:
    """Vygeneruje kompletní SQL pro scénář nového školního roku."""

    rng = random.Random(seed)
    tridy = testdata.load_tridy()
    zamestnanci = testdata.load_zamestnanci(domain=domain)

    # Struktura tříd podle ročníku: {1: ['1.A','1.B',...], 2: [...], ...}
    tridy_by_rocnik: dict[int, list[str]] = {}
    for rocnik, zkratka in tridy:
        tridy_by_rocnik.setdefault(rocnik, []).append(zkratka)
    for r in tridy_by_rocnik:
        tridy_by_rocnik[r].sort()

    # Mapování: zkratka třídy → INTERN_KOD třídního učitele
    tridni_map: dict[str, str] = {
        u["TRIDNI_PRO"]: u["INTERN_KOD"]
        for u in zamestnanci if u.get("TRIDNI_PRO")
    }

    lines: list[str] = []

    def emit(*args: str) -> None:
        lines.extend(args)

    def emit_section(title: str) -> None:
        emit("", f"-- {'=' * 61}",
             f"-- {title}",
             f"-- {'=' * 61}", "")

    emit(f"USE {sql_db};", "GO")

    # -----------------------------------------------------------------
    # 1. Smazání aktivních žáků 9. ročníku a jejich zákonných zástupců
    # -----------------------------------------------------------------
    emit_section("1. Smazat aktivní žáky 9. ročníku a jejich zákonné zástupce")

    emit("-- Vazby žák–ZZ pro 9. ročník")
    emit("DELETE FROM dbo.zaci_zzr")
    emit("WHERE INTERN_KOD IN (")
    emit("    SELECT INTERN_KOD FROM dbo.zaci")
    emit("    WHERE TRIDA LIKE '9.%' AND EVID_DO IS NULL")
    emit(");")
    emit("")

    emit("-- Osiřelí zákonní zástupci (bez vazby na žádného žáka)")
    emit("DELETE FROM dbo.zaci_zzd")
    emit("WHERE ID NOT IN (SELECT DISTINCT ID_ZZ FROM dbo.zaci_zzr);")
    emit("")

    emit("-- Webové přístupy žáků 9. ročníku")
    emit("DELETE FROM dbo.webuser")
    emit("WHERE INTERN_KOD IN (")
    emit("    SELECT INTERN_KOD FROM dbo.zaci")
    emit("    WHERE TRIDA LIKE '9.%' AND EVID_DO IS NULL")
    emit(");")
    emit("")

    emit("-- Webové přístupy osiřelých zákonných zástupců")
    emit("DELETE FROM dbo.webuser")
    emit("WHERE KOD1 = 'R' AND INTERN_KOD NOT IN (SELECT ID FROM dbo.zaci_zzd);")
    emit("")

    emit("-- Samotní žáci 9. ročníku")
    emit("DELETE FROM dbo.zaci")
    emit("WHERE TRIDA LIKE '9.%' AND EVID_DO IS NULL;")
    emit("")
    emit("GO")

    # -----------------------------------------------------------------
    # 2. Kaskáda přesunů ročníků (shora dolů)
    # -----------------------------------------------------------------
    emit_section("2. Kaskáda přesunů ročníků (shora dolů)")

    # Přímé přesuny (stejný počet tříd, písmena se nemění)
    direct_moves = [
        (8, 9, "8→9"),
        (7, 8, "7→8"),
        (6, 7, "6→7"),
    ]
    for src_r, tgt_r, label in direct_moves:
        letters = sorted(z.split(".")[1] for z in tridy_by_rocnik.get(tgt_r, []))
        emit(f"-- {label}: přímé mapování písmen ({len(letters)}→{len(letters)})")
        for letter in letters:
            emit(f"UPDATE dbo.zaci SET TRIDA = '{tgt_r}.{letter}' "
                 f"WHERE TRIDA = '{src_r}.{letter}' AND EVID_DO IS NULL;")
        emit("")

    # 5→6: náhodné přerozdělení (4 třídy → 3 třídy)
    target_6 = sorted(tridy_by_rocnik.get(6, []))
    n6 = len(target_6)
    emit(f"-- 5→6: náhodné přerozdělení ({len(tridy_by_rocnik.get(5, []))} tříd → {n6} tříd)")
    emit(";WITH ranked AS (")
    emit("    SELECT INTERN_KOD,")
    emit("           (ROW_NUMBER() OVER (ORDER BY NEWID())) - 1 AS rn")
    emit("    FROM dbo.zaci")
    emit("    WHERE TRIDA LIKE '5.%' AND EVID_DO IS NULL")
    emit(")")
    emit("UPDATE z")
    emit(f"SET TRIDA = CASE rn % {n6}")
    for i, zkr in enumerate(target_6):
        emit(f"    WHEN {i} THEN '{zkr}'")
    emit("END")
    emit("FROM dbo.zaci z")
    emit("JOIN ranked r ON z.INTERN_KOD = r.INTERN_KOD;")
    emit("")

    # 4→5, 3→4: přímé mapování (4→4)
    for src_r, tgt_r, label in [(4, 5, "4→5"), (3, 4, "3→4")]:
        letters = sorted(z.split(".")[1] for z in tridy_by_rocnik.get(tgt_r, []))
        emit(f"-- {label}: přímé mapování písmen ({len(letters)}→{len(letters)})")
        for letter in letters:
            emit(f"UPDATE dbo.zaci SET TRIDA = '{tgt_r}.{letter}' "
                 f"WHERE TRIDA = '{src_r}.{letter}' AND EVID_DO IS NULL;")
        emit("")

    # 2→3: přímé pro společná písmena, přerozdělení přebývajících
    target_3_letters = sorted(z.split(".")[1] for z in tridy_by_rocnik.get(3, []))
    source_2_letters = sorted(z.split(".")[1] for z in tridy_by_rocnik.get(2, []))
    common_letters = sorted(set(target_3_letters) & set(source_2_letters))
    extra_letters = sorted(set(source_2_letters) - set(target_3_letters))
    n3 = len(target_3_letters)

    emit(f"-- 2→3: přímé mapování A–{common_letters[-1] if common_letters else '?'}, "
         f"přerozdělení {', '.join('2.' + e for e in extra_letters)} "
         f"({len(source_2_letters)} tříd → {n3} tříd)")
    for letter in common_letters:
        emit(f"UPDATE dbo.zaci SET TRIDA = '3.{letter}' "
             f"WHERE TRIDA = '2.{letter}' AND EVID_DO IS NULL;")

    for extra in extra_letters:
        emit(f"-- Přerozdělení žáků z 2.{extra}")
        emit(";WITH ranked AS (")
        emit("    SELECT INTERN_KOD,")
        emit("           (ROW_NUMBER() OVER (ORDER BY NEWID())) - 1 AS rn")
        emit("    FROM dbo.zaci")
        emit(f"    WHERE TRIDA = '2.{extra}' AND EVID_DO IS NULL")
        emit(")")
        emit("UPDATE z")
        emit(f"SET TRIDA = CASE rn % {n3}")
        for i, letter in enumerate(target_3_letters):
            emit(f"    WHEN {i} THEN '3.{letter}'")
        emit("END")
        emit("FROM dbo.zaci z")
        emit("JOIN ranked r ON z.INTERN_KOD = r.INTERN_KOD;")
    emit("")

    # 1→2: přímé mapování (5→5)
    letters_2 = sorted(z.split(".")[1] for z in tridy_by_rocnik.get(2, []))
    emit(f"-- 1→2: přímé mapování písmen ({len(letters_2)}→{len(letters_2)})")
    for letter in letters_2:
        emit(f"UPDATE dbo.zaci SET TRIDA = '2.{letter}' "
             f"WHERE TRIDA = '1.{letter}' AND EVID_DO IS NULL;")
    emit("")
    emit("GO")

    # -----------------------------------------------------------------
    # 3. Noví žáci 1. ročníku (s prázdným E_MAIL)
    # -----------------------------------------------------------------
    emit_section("3. Noví žáci 1. ročníku (s prázdným E_MAIL)")

    tridy_1 = sorted(tridy_by_rocnik.get(1, []))
    zak_idx = NOVE_ZAK_START_IDX
    zzd_idx = NOVE_ZZD_START_IDX
    novi_zaci: list[dict] = []
    novi_zzd: list[dict] = []

    for zkratka in tridy_1:
        for poradi in range(1, per_class + 1):
            zak_idx += 1
            pohlar_m = (poradi % 2 == 1)

            # Jméno žáka – náhodný výběr z českých zásobníků
            if pohlar_m:
                jmeno = testdata.pick(testdata.JMENA_ZAK_M, rng.randint(0, 9999))
                prijmeni = testdata.pick(testdata.PRIJMENI_ZAK_M, rng.randint(0, 9999))
            else:
                jmeno = testdata.pick(testdata.JMENA_ZAK_F, rng.randint(0, 9999))
                prijmeni = testdata.pick(testdata.PRIJMENI_ZAK_F, rng.randint(0, 9999))

            kod = f"Z{zak_idx:05d}"
            novi_zaci.append({
                "INTERN_KOD": kod,
                "C_TR_VYK":   poradi,
                "PRIJMENI":   prijmeni,
                "JMENO":      jmeno,
                "TRIDA":      zkratka,
            })

            # Zákonný zástupce
            zzd_idx += 1
            pohlar_zzd_m = (zzd_idx % 3 != 0)  # 2/3 otcové, 1/3 matky
            if pohlar_zzd_m:
                jmeno_zzd = testdata.pick(testdata.JMENA_ZZD_M, rng.randint(0, 9999))
                prijmeni_zzd = testdata.pick(testdata.PRIJMENI_ZZD_M, rng.randint(0, 9999))
            else:
                jmeno_zzd = testdata.pick(testdata.JMENA_ZZD_F, rng.randint(0, 9999))
                prijmeni_zzd = testdata.pick(testdata.PRIJMENI_ZZD_F, rng.randint(0, 9999))

            zzd_kod = f"ZZD{zzd_idx:05d}"
            local_zzd = (f"{testdata.ascii_slug(prijmeni_zzd)}"
                         f".{testdata.ascii_slug(jmeno_zzd)}")
            email_zzd = f"{local_zzd}@example.com"

            novi_zzd.append({
                "ID":        zzd_kod,
                "JMENO":     jmeno_zzd,
                "PRIJMENI":  prijmeni_zzd,
                "TEL_MOBIL": testdata.telefon(zzd_idx + 9000),
                "E_MAIL":    email_zzd,
                "ZAK_KOD":   kod,
            })

    # SQL – žáci
    esc = testdata.sql_escape
    emit(f"-- {len(novi_zaci)} nových prvňáčků ({per_class}/třídu × {len(tridy_1)} tříd)")
    for z in novi_zaci:
        emit(
            f"INSERT INTO dbo.zaci "
            f"(INTERN_KOD, C_TR_VYK, PRIJMENI, JMENO, TRIDA, E_MAIL, EVID_DO) "
            f"VALUES ({esc(z['INTERN_KOD'])}, {z['C_TR_VYK']}, "
            f"{esc(z['PRIJMENI'])}, {esc(z['JMENO'])}, "
            f"{esc(z['TRIDA'])}, NULL, NULL);"
        )
    emit("")

    # SQL – zákonní zástupci
    emit(f"-- {len(novi_zzd)} zákonných zástupců nových prvňáčků")
    for z in novi_zzd:
        emit(
            f"INSERT INTO dbo.zaci_zzd (ID, JMENO, PRIJMENI, TEL_MOBIL, E_MAIL) "
            f"VALUES ({esc(z['ID'])}, {esc(z['JMENO'])}, "
            f"{esc(z['PRIJMENI'])}, {esc(z['TEL_MOBIL'])}, "
            f"{esc(z['E_MAIL'])});"
        )
    emit("")

    # SQL – vazby žák–ZZ
    emit("-- Vazby žák–zákonný zástupce")
    for z in novi_zzd:
        emit(
            f"INSERT INTO dbo.zaci_zzr (INTERN_KOD, ID_ZZ, JE_ZZ, PRIMARNI) "
            f"VALUES ({esc(z['ZAK_KOD'])}, {esc(z['ID'])}, 1, 1);"
        )
    emit("")
    emit("GO")

    # -----------------------------------------------------------------
    # 4. Přečíslování třídních výkazů (C_TR_VYK)
    # -----------------------------------------------------------------
    emit_section("4. Přečíslování třídních výkazů (abecedně v každé třídě)")

    emit(";WITH renum AS (")
    emit("    SELECT INTERN_KOD,")
    emit("           ROW_NUMBER() OVER (")
    emit("               PARTITION BY TRIDA")
    emit("               ORDER BY PRIJMENI, JMENO")
    emit("           ) AS new_num")
    emit("    FROM dbo.zaci")
    emit("    WHERE EVID_DO IS NULL")
    emit(")")
    emit("UPDATE z")
    emit("SET C_TR_VYK = r.new_num")
    emit("FROM dbo.zaci z")
    emit("JOIN renum r ON z.INTERN_KOD = r.INTERN_KOD;")
    emit("")
    emit("GO")

    # -----------------------------------------------------------------
    # 5. Aktualizace třídních učitelů
    # -----------------------------------------------------------------
    emit_section("5. Aktualizace třídních učitelů")

    # Kaskáda: nový N.X ← učitel starého (N-1).X
    # Výjimky: ročníky 1 a 6 dostávají nové učitele (staří uvolněni)
    new_assignment: dict[str, str] = {}

    # Přímá kaskáda: učitel následuje studenty
    cascade_pairs = {
        # target_rocnik: source_rocnik
        9: 8, 8: 7, 7: 6,        # 6→7, 7→8, 8→9
        5: 4, 4: 3, 3: 2, 2: 1,  # 1→2, 2→3, 3→4, 4→5
    }

    for target_r, source_r in cascade_pairs.items():
        for zkr in tridy_by_rocnik.get(target_r, []):
            letter = zkr.split(".")[1]
            source_zkr = f"{source_r}.{letter}"
            if source_zkr in tridni_map:
                new_assignment[zkr] = tridni_map[source_zkr]

    # Zjistíme, kteří učitelé zůstali bez třídnictví
    assigned_set = set(new_assignment.values())
    non_form_pool = [
        u["INTERN_KOD"] for u in zamestnanci
        if u["OU"] == "Ucitele"
        and u["UCI_LETOS"] == 1
        and u["INTERN_KOD"] not in assigned_set
    ]
    rng.shuffle(non_form_pool)
    pool_iter = iter(non_form_pool)

    # Nový 6. ročník – nově přiřazení učitelé
    for zkr in sorted(tridy_by_rocnik.get(6, [])):
        teacher = next(pool_iter, None)
        if teacher:
            new_assignment[zkr] = teacher

    # Nový 1. ročník – nově přiřazení učitelé
    for zkr in sorted(tridy_by_rocnik.get(1, [])):
        teacher = next(pool_iter, None)
        if teacher:
            new_assignment[zkr] = teacher

    # Generujeme SQL
    emit("-- Vynulování všech třídnictví")
    emit("UPDATE dbo.tridy SET TRIDNICTVI = NULL;")
    emit("")

    # Vytvoříme lookup pro komentáře (jméno učitele)
    ucitel_by_kod = {u["INTERN_KOD"]: u for u in zamestnanci}

    emit("-- Nové přiřazení třídních učitelů")
    for zkr in sorted(new_assignment.keys()):
        ucitel_kod = new_assignment[zkr]
        u = ucitel_by_kod.get(ucitel_kod)
        komentar = ""
        if u:
            komentar = f"  -- {u['PRIJMENI']} {u['JMENO']}"
        emit(
            f"UPDATE dbo.tridy SET TRIDNICTVI = {esc(ucitel_kod)} "
            f"WHERE ZKRATKA = {esc(zkr)};{komentar}"
        )
    emit("")
    emit("GO")

    # -----------------------------------------------------------------
    # Shrnutí
    # -----------------------------------------------------------------
    freed_count = len(non_form_pool)
    new_6_count = len(tridy_by_rocnik.get(6, []))
    new_1_count = len(tridy_by_rocnik.get(1, []))

    emit("")
    emit(f"PRINT 'Nový školní rok – hotovo (seed {seed}).';")
    emit(f"PRINT '  Smazáni aktivní žáci 9. ročníku a jejich ZZ.';")
    emit(f"PRINT '  Kaskáda: 8→9, 7→8, 6→7, 5→6 (shuffle), 4→5, 3→4, 2→3, 1→2.';")
    emit(f"PRINT '  Nových prvňáčků: {len(novi_zaci)} "
         f"({per_class}/třídu × {len(tridy_1)} tříd).';")
    emit(f"PRINT '  Nových zákonných zástupců: {len(novi_zzd)}.';")
    emit(f"PRINT '  Uvolněno učitelů: {freed_count}, "
         f"nově přiřazeno: {new_6_count + new_1_count} "
         f"(6. roč.: {new_6_count}, 1. roč.: {new_1_count}).';")
    emit("GO")

    return "\n".join(lines)


# =============================================================================
# Aplikace SQL na kontejner
# =============================================================================

def apply_sql(sql_content: str, sql_db: str, sa_password: str) -> None:
    """Aplikuje SQL na MSSQL kontejner přes podman exec + sqlcmd."""

    # Zkontrolujeme, zda kontejner běží
    result = subprocess.run(
        ["podman", "inspect", "--format={{.State.Status}}", "bakadev-mssql"],
        capture_output=True, text=True,
    )
    if "running" not in result.stdout:
        print(f"{_ERR} Kontejner bakadev-mssql není spuštěn.", file=sys.stderr)
        print("  Spusťte nejprve: cd dev && ./setup-dev.sh", file=sys.stderr)
        sys.exit(1)

    # Zapíšeme SQL do dočasného souboru
    with tempfile.NamedTemporaryFile(
        mode="w", suffix=".sql", delete=False, encoding="utf-8"
    ) as f:
        f.write(sql_content)
        tmp_path = f.name

    try:
        # Zkopírujeme do kontejneru
        subprocess.run(
            ["podman", "cp", tmp_path, "bakadev-mssql:/tmp/novy-rok.sql"],
            check=True,
        )

        # Spustíme sqlcmd
        result = subprocess.run(
            [
                "podman", "exec",
                "-e", "SQLCMDENCRYPT=false",
                "bakadev-mssql",
                "/usr/local/bin/sqlcmd",
                "-S", "localhost",
                "-U", "sa",
                "-P", sa_password,
                "-d", sql_db,
                "-i", "/tmp/novy-rok.sql",
                "-b",
            ],
            capture_output=True, text=True,
        )

        if result.stdout:
            for line in result.stdout.strip().splitlines():
                print(f"  {line}")
        if result.returncode != 0:
            print(f"\n{_ERR} Chyba při aplikaci SQL (kód {result.returncode}):",
                  file=sys.stderr)
            if result.stderr:
                print(result.stderr, file=sys.stderr)
            sys.exit(1)

        print(f"\n{_OK} Scénář 'nový školní rok' úspěšně aplikován.")
    finally:
        os.unlink(tmp_path)


# =============================================================================
# main
# =============================================================================

def main() -> None:
    parser = argparse.ArgumentParser(
        description="BakaKeeper – testovací scénář: Nový školní rok",
    )
    parser.add_argument(
        "--apply", action="store_true",
        help="Aplikovat SQL na běžící MSSQL kontejner (jinak dry-run – vypíše SQL)",
    )
    parser.add_argument(
        "--per-class", type=int, default=NOVE_ZACI_NA_TRIDU, metavar="N",
        help=f"Počet nových prvňáčků na třídu (výchozí: {NOVE_ZACI_NA_TRIDU})",
    )
    parser.add_argument(
        "--seed", type=int, default=None,
        help="Seed pro generátor náhodných jmen (výchozí: náhodný)",
    )
    parser.add_argument(
        "--domain", default="skola.local",
        help="Interní AD doména (výchozí: skola.local)",
    )
    args = parser.parse_args()

    if not (1 <= args.per_class <= 50):
        print(f"Chyba: --per-class musí být 1–50 (zadáno: {args.per_class})",
              file=sys.stderr)
        sys.exit(1)

    env = load_env()
    sql_db = env.get("SQL_DB", "bakalari")
    sa_password = env.get("SQL_SA_PASSWORD", "SqlServer.Dev2026")
    seed = args.seed if args.seed is not None else random.randint(0, 2**31)

    print("============================================================")
    print(" BakaKeeper – scénář: Nový školní rok")
    print(f" Doména:     {args.domain}")
    print(f" Databáze:   {sql_db}")
    print(f" Prvňáčků:   {args.per_class}/třídu")
    print(f" Seed:       {seed}")
    print(f" Režim:      {'APPLY' if args.apply else 'DRY-RUN'}")
    print("============================================================")
    print()

    sql = build_sql(args.domain, sql_db, args.per_class, seed)

    if args.apply:
        apply_sql(sql, sql_db, sa_password)
    else:
        print(sql)
        print()
        print("-- =========================================================")
        print("-- Dry-run: SQL nebyl aplikován.")
        print("-- Pro aplikaci na kontejner použijte: --apply")
        print("-- =========================================================")


if __name__ == "__main__":
    main()
