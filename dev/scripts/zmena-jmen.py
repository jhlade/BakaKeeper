#!/usr/bin/env python3
# =============================================================================
# BakaKeeper – testovací scénář: Změna jmen
#
# U náhodného N % aktivních žáků změní jméno, příjmení a zákonné zástupce.
# E_MAIL se vynuluje (NULL) – signál pro BakaKeeper k regeneraci adresy.
#
# Očekávané chování BakaKeeperu po synchronizaci:
#   – Změna identifikace: sn, givenName, displayName, CN → nová hodnota
#   – Generace nového e-mailu z nového jména:
#       1) zápis do SQL (E_MAIL)
#       2) nastavení jako primární mail atribut v LDAP
#       3) přidání do proxyAddresses jako "SMTP:<nový>" (primární)
#       4) degradace starého mailu na "smtp:<starý>" (sekundární)
#       5) při opakovaných změnách zůstane celá historie v proxyAddresses
#   – Nový e-mail nesmí kolidovat s žádným existujícím mail ani proxyAddresses
#   – Zákonní zástupci: staří odebráni, noví přidáni
#
# Použití:
#   cd dev && python3 scripts/zmena-jmen.py                  # dry-run
#   cd dev && python3 scripts/zmena-jmen.py --apply          # aplikuje na kontejner
#   cd dev && python3 scripts/zmena-jmen.py --pct 10         # 10 % žáků
#   cd dev && python3 scripts/zmena-jmen.py --seed 42        # reprodukovatelný běh
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

# Výchozí konfigurace
DEFAULT_PCT = 5               # výchozí procento žáků ke změně
NOVE_ZZD_START_IDX = 1900     # nové ZZD ID (ZZD01901, ...)

# Barevný výstup
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

def build_sql(sql_db: str, pct: int, seed: int) -> str:
    """Vygeneruje SQL pro změnu jmen u N % žáků."""

    rng = random.Random(seed)
    esc = testdata.sql_escape

    lines: list[str] = []

    def emit(*args: str) -> None:
        lines.extend(args)

    def emit_section(title: str) -> None:
        emit("", f"-- {'=' * 61}",
             f"-- {title}",
             f"-- {'=' * 61}", "")

    emit(f"USE {sql_db};", "GO")

    # -----------------------------------------------------------------
    # 1. Výběr N % aktivních žáků (deterministicky přes HASHBYTES)
    # -----------------------------------------------------------------
    emit_section(f"1. Výběr {pct} % aktivních žáků ke změně jmen")

    # SQL CTE vybere žáky deterministicky podle seeded hash
    emit(f"-- Seed: {seed}, procento: {pct} %")
    emit(f"-- Výběr přes ABS(CHECKSUM(HASHBYTES('MD5', INTERN_KOD + '{seed}'))) % 100 < {pct}")
    emit("")

    # -----------------------------------------------------------------
    # 2. Změna jmen – vygenerujeme Python-side (potřebujeme nová jména)
    #    Načteme aktuální žáky z SQL a vybereme N %
    # -----------------------------------------------------------------
    # Protože nemáme přístup k aktuálním datům v SQL, generujeme SQL
    # s podmínkou výběru přímo v databázi. Nová jména přiřadíme
    # deterministicky podle INTERN_KOD a seedu.
    #
    # Strategie: UPDATE s CASE WHEN pro vybrané žáky
    # -----------------------------------------------------------------

    # Vygenerujeme dostatečný pool nových jmen (pro max ~200 žáků)
    pool_size = 200
    new_names_m: list[tuple[str, str]] = []
    new_names_f: list[tuple[str, str]] = []

    for i in range(pool_size):
        jmeno_m = testdata.pick(testdata.JMENA_ZAK_M, rng.randint(0, 9999))
        prijmeni_m = testdata.pick(testdata.PRIJMENI_ZAK_M, rng.randint(0, 9999))
        new_names_m.append((jmeno_m, prijmeni_m))

        jmeno_f = testdata.pick(testdata.JMENA_ZAK_F, rng.randint(0, 9999))
        prijmeni_f = testdata.pick(testdata.PRIJMENI_ZAK_F, rng.randint(0, 9999))
        new_names_f.append((jmeno_f, prijmeni_f))

    emit_section("2. Změna jmen vybraných žáků a vynulování E_MAIL")

    # Výběrová CTE: vybereme N % žáků (bez statických ZFIX)
    emit(";WITH selected AS (")
    emit("    SELECT INTERN_KOD,")
    emit(f"           ROW_NUMBER() OVER (ORDER BY ABS(CHECKSUM(HASHBYTES('MD5', INTERN_KOD + '{seed}'))) ) AS rn")
    emit("    FROM dbo.zaci")
    emit("    WHERE EVID_DO IS NULL")
    emit(f"      AND INTERN_KOD NOT LIKE 'ZFIX%'")
    emit(f"      AND ABS(CHECKSUM(HASHBYTES('MD5', INTERN_KOD + '{seed}'))) % 100 < {pct}")
    emit(")")

    # Generujeme UPDATE s CASE – mužská jména (lichá rn), ženská (sudá rn)
    emit("UPDATE z SET")
    emit("    JMENO = CASE")
    for i in range(pool_size):
        jm, _ = new_names_m[i]
        jf, _ = new_names_f[i]
        emit(f"        WHEN s.rn = {i+1} AND s.rn % 2 = 1 THEN {esc(jm)}")
        emit(f"        WHEN s.rn = {i+1} AND s.rn % 2 = 0 THEN {esc(jf)}")
    emit("        ELSE z.JMENO END,")
    emit("    PRIJMENI = CASE")
    for i in range(pool_size):
        _, pm = new_names_m[i]
        _, pf = new_names_f[i]
        emit(f"        WHEN s.rn = {i+1} AND s.rn % 2 = 1 THEN {esc(pm)}")
        emit(f"        WHEN s.rn = {i+1} AND s.rn % 2 = 0 THEN {esc(pf)}")
    emit("        ELSE z.PRIJMENI END,")
    emit("    E_MAIL = NULL")
    emit("FROM dbo.zaci z")
    emit("JOIN selected s ON z.INTERN_KOD = s.INTERN_KOD;")
    emit("")
    emit("GO")

    # -----------------------------------------------------------------
    # 3. Zákonní zástupci: smazat staré, vytvořit nové
    # -----------------------------------------------------------------
    emit_section("3. Výměna zákonných zástupců u změněných žáků")

    # Smazat vazby
    emit("-- Odebrat vazby žák–ZZ pro dotčené žáky")
    emit("DELETE FROM dbo.zaci_zzr")
    emit("WHERE INTERN_KOD IN (")
    emit("    SELECT INTERN_KOD FROM dbo.zaci")
    emit("    WHERE EVID_DO IS NULL")
    emit(f"      AND INTERN_KOD NOT LIKE 'ZFIX%'")
    emit(f"      AND ABS(CHECKSUM(HASHBYTES('MD5', INTERN_KOD + '{seed}'))) % 100 < {pct}")
    emit(");")
    emit("")

    # Smazat osiřelé ZZ
    emit("-- Smazat osiřelé zákonné zástupce")
    emit("DELETE FROM dbo.zaci_zzd")
    emit("WHERE ID NOT IN (SELECT DISTINCT ID_ZZ FROM dbo.zaci_zzr);")
    emit("")

    # Smazat osiřelé webuser záznamy
    emit("-- Smazat osiřelé webuser záznamy zákonných zástupců")
    emit("DELETE FROM dbo.webuser")
    emit("WHERE KOD1 = 'R' AND INTERN_KOD NOT IN (SELECT ID FROM dbo.zaci_zzd);")
    emit("")
    emit("GO")

    # Noví zákonní zástupci – insertujeme přes CTE s výběrem
    emit_section("4. Noví zákonní zástupci pro dotčené žáky")

    zzd_idx = NOVE_ZZD_START_IDX
    novi_zzd: list[dict] = []

    for i in range(pool_size):
        zzd_idx += 1
        is_male = (zzd_idx % 3 != 0)
        if is_male:
            jmeno = testdata.pick(testdata.JMENA_ZZD_M, rng.randint(0, 9999))
            prijmeni = testdata.pick(testdata.PRIJMENI_ZZD_M, rng.randint(0, 9999))
        else:
            jmeno = testdata.pick(testdata.JMENA_ZZD_F, rng.randint(0, 9999))
            prijmeni = testdata.pick(testdata.PRIJMENI_ZZD_F, rng.randint(0, 9999))

        local = f"{testdata.ascii_slug(prijmeni)}.{testdata.ascii_slug(jmeno)}"
        email = f"{local}@example.com"

        novi_zzd.append({
            "ID":        f"ZZD{zzd_idx:05d}",
            "JMENO":     jmeno,
            "PRIJMENI":  prijmeni,
            "TEL_MOBIL": testdata.telefon(zzd_idx + 9000),
            "E_MAIL":    email,
            "RN":        i + 1,  # odpovídá rn z výběru
        })

    # Insertujeme ZZ a vazby přes podmíněný INSERT na základě existence žáka
    emit("-- Dočasná tabulka s vybranými žáky")
    emit("SELECT INTERN_KOD, ROW_NUMBER() OVER (")
    emit(f"    ORDER BY ABS(CHECKSUM(HASHBYTES('MD5', INTERN_KOD + '{seed}')))")
    emit(") AS rn")
    emit("INTO #selected")
    emit("FROM dbo.zaci")
    emit("WHERE EVID_DO IS NULL")
    emit(f"  AND INTERN_KOD NOT LIKE 'ZFIX%'")
    emit(f"  AND ABS(CHECKSUM(HASHBYTES('MD5', INTERN_KOD + '{seed}'))) % 100 < {pct};")
    emit("")
    emit("GO")

    emit("-- Inserty zákonných zástupců (pouze pro existující rn)")
    for z in novi_zzd:
        emit(
            f"IF EXISTS (SELECT 1 FROM #selected WHERE rn = {z['RN']})")
        emit(
            f"    INSERT INTO dbo.zaci_zzd (ID, JMENO, PRIJMENI, TEL_MOBIL, E_MAIL) "
            f"VALUES ({esc(z['ID'])}, {esc(z['JMENO'])}, "
            f"{esc(z['PRIJMENI'])}, {esc(z['TEL_MOBIL'])}, "
            f"{esc(z['E_MAIL'])});")
    emit("")

    emit("-- Vazby žák–ZZ")
    for z in novi_zzd:
        emit(
            f"INSERT INTO dbo.zaci_zzr (INTERN_KOD, ID_ZZ, JE_ZZ, PRIMARNI) "
            f"SELECT s.INTERN_KOD, {esc(z['ID'])}, 1, 1 "
            f"FROM #selected s WHERE s.rn = {z['RN']};")
    emit("")

    emit("DROP TABLE #selected;")
    emit("")
    emit("GO")

    # -----------------------------------------------------------------
    # 5. Přečíslování třídních výkazů
    # -----------------------------------------------------------------
    emit_section("5. Přečíslování třídních výkazů (abecedně v každé třídě)")

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
    # Shrnutí
    # -----------------------------------------------------------------
    emit("")
    emit(f"PRINT 'Změna jmen – hotovo (seed {seed}).';")
    emit(f"PRINT '  {pct} %% aktivních žáků dostalo nová jména.';")
    emit(f"PRINT '  E_MAIL vynulován → BakaKeeper vygeneruje nový.';")
    emit(f"PRINT '  Zákonní zástupci vyměněni.';")
    emit("GO")

    return "\n".join(lines)


# =============================================================================
# Aplikace SQL na kontejner
# =============================================================================

def apply_sql(sql_content: str, sql_db: str, sa_password: str) -> None:
    """Aplikuje SQL na MSSQL kontejner přes podman exec + sqlcmd."""

    result = subprocess.run(
        ["podman", "inspect", "--format={{.State.Status}}", "bakadev-mssql"],
        capture_output=True, text=True,
    )
    if "running" not in result.stdout:
        print(f"{_ERR} Kontejner bakadev-mssql není spuštěn.", file=sys.stderr)
        print("  Spusťte nejprve: cd dev && ./setup-dev.sh", file=sys.stderr)
        sys.exit(1)

    with tempfile.NamedTemporaryFile(
        mode="w", suffix=".sql", delete=False, encoding="utf-8"
    ) as f:
        f.write(sql_content)
        tmp_path = f.name

    try:
        subprocess.run(
            ["podman", "cp", tmp_path, "bakadev-mssql:/tmp/zmena-jmen.sql"],
            check=True,
        )

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
                "-i", "/tmp/zmena-jmen.sql",
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

        print(f"\n{_OK} Scénář 'změna jmen' úspěšně aplikován.")
    finally:
        os.unlink(tmp_path)


# =============================================================================
# main
# =============================================================================

def main() -> None:
    parser = argparse.ArgumentParser(
        description="BakaKeeper – testovací scénář: Změna jmen",
    )
    parser.add_argument(
        "--apply", action="store_true",
        help="Aplikovat SQL na běžící MSSQL kontejner (jinak dry-run – vypíše SQL)",
    )
    parser.add_argument(
        "--pct", type=int, default=DEFAULT_PCT, metavar="N",
        help=f"Procento aktivních žáků ke změně jmen (výchozí: {DEFAULT_PCT})",
    )
    parser.add_argument(
        "--seed", type=int, default=None,
        help="Seed pro generátor náhodných jmen (výchozí: náhodný)",
    )
    args = parser.parse_args()

    if not (1 <= args.pct <= 50):
        print(f"Chyba: --pct musí být 1–50 (zadáno: {args.pct})",
              file=sys.stderr)
        sys.exit(1)

    env = load_env()
    sql_db = env.get("SQL_DB", "bakalari")
    sa_password = env.get("SQL_SA_PASSWORD", "SqlServer.Dev2026")
    seed = args.seed if args.seed is not None else random.randint(0, 2**31)

    print("============================================================")
    print(" BakaKeeper – scénář: Změna jmen")
    print(f" Databáze:   {sql_db}")
    print(f" Procento:   {args.pct} %")
    print(f" Seed:       {seed}")
    print(f" Režim:      {'APPLY' if args.apply else 'DRY-RUN'}")
    print("============================================================")
    print()

    sql = build_sql(sql_db, args.pct, seed)

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
