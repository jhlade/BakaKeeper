#!/usr/bin/env python3
# =============================================================================
# BakaKeeper – testovací scénář: Změna tříd
#
# Náhodně přeháže všechny aktivní žáky mezi třídami bez ohledu na ročník.
# Každý žák dostane novou náhodnou třídu z celkového seznamu 34 tříd.
# Po přeřazení se přečíslují třídní výkazy (C_TR_VYK) abecedně v každé třídě.
#
# Očekávané chování BakaKeeperu po synchronizaci:
#   – Masivní kaskáda přesunů žáků do odpovídajících OU a skupin
#   – Žáci se přesunou mezi libovolnými ročníky (1→9, 9→1, …)
#   – Skupiny členství se aktualizují, UAC/politiky se přepočítají dle ročníku
#
# Použití:
#   cd dev && python3 scripts/zmena-trid.py                  # dry-run
#   cd dev && python3 scripts/zmena-trid.py --apply          # aplikuje na kontejner
#   cd dev && python3 scripts/zmena-trid.py --seed 42        # reprodukovatelný běh
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

def build_sql(sql_db: str, seed: int) -> str:
    """Vygeneruje SQL pro náhodné přeházení žáků mezi třídami."""

    tridy = testdata.load_tridy()
    # Seřazený seznam všech zkratek tříd
    all_classes = sorted([zkratka for _, zkratka in tridy])
    n = len(all_classes)

    lines: list[str] = []

    def emit(*args: str) -> None:
        lines.extend(args)

    def emit_section(title: str) -> None:
        emit("", f"-- {'=' * 61}",
             f"-- {title}",
             f"-- {'=' * 61}", "")

    emit(f"USE {sql_db};", "GO")

    # -----------------------------------------------------------------
    # 1. Náhodné přeřazení žáků do tříd
    # -----------------------------------------------------------------
    emit_section("1. Náhodné přeřazení všech aktivních žáků do tříd")

    emit(f"-- Celkem {n} tříd: {', '.join(all_classes)}")
    emit(f"-- Seed: {seed} (NEWID() – seed ovlivňuje jen Python výstup, ne SQL řazení)")
    emit("")

    # Využijeme CTE s ROW_NUMBER() OVER (ORDER BY NEWID()) pro náhodné
    # pořadí a modulární aritmetiku pro rovnoměrné rozdělení do tříd.
    emit(";WITH shuffled AS (")
    emit("    SELECT INTERN_KOD,")
    emit("           (ROW_NUMBER() OVER (ORDER BY NEWID())) - 1 AS rn")
    emit("    FROM dbo.zaci")
    emit("    WHERE EVID_DO IS NULL")
    emit(")")
    emit("UPDATE z")
    emit(f"SET TRIDA = CASE rn % {n}")
    for i, zkr in enumerate(all_classes):
        emit(f"    WHEN {i} THEN '{zkr}'")
    emit("END")
    emit("FROM dbo.zaci z")
    emit("JOIN shuffled s ON z.INTERN_KOD = s.INTERN_KOD;")
    emit("")
    emit("GO")

    # -----------------------------------------------------------------
    # 2. Přečíslování třídních výkazů (C_TR_VYK)
    # -----------------------------------------------------------------
    emit_section("2. Přečíslování třídních výkazů (abecedně v každé třídě)")

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
    emit(f"PRINT 'Změna tříd – hotovo (seed {seed}).';")
    emit(f"PRINT '  Všichni aktivní žáci náhodně přeřazeni do {n} tříd.';")
    emit(f"PRINT '  Třídní výkazy přečíslovány abecedně.';")
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
            ["podman", "cp", tmp_path, "bakadev-mssql:/tmp/zmena-trid.sql"],
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
                "-i", "/tmp/zmena-trid.sql",
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

        print(f"\n{_OK} Scénář 'změna tříd' úspěšně aplikován.")
    finally:
        os.unlink(tmp_path)


# =============================================================================
# main
# =============================================================================

def main() -> None:
    parser = argparse.ArgumentParser(
        description="BakaKeeper – testovací scénář: Změna tříd",
    )
    parser.add_argument(
        "--apply", action="store_true",
        help="Aplikovat SQL na běžící MSSQL kontejner (jinak dry-run – vypíše SQL)",
    )
    parser.add_argument(
        "--seed", type=int, default=None,
        help="Seed pro generátor náhodných čísel (výchozí: náhodný)",
    )
    args = parser.parse_args()

    env = load_env()
    sql_db = env.get("SQL_DB", "bakalari")
    sa_password = env.get("SQL_SA_PASSWORD", "SqlServer.Dev2026")
    seed = args.seed if args.seed is not None else random.randint(0, 2**31)

    print("============================================================")
    print(" BakaKeeper – scénář: Změna tříd")
    print(f" Databáze:   {sql_db}")
    print(f" Seed:       {seed}")
    print(f" Režim:      {'APPLY' if args.apply else 'DRY-RUN'}")
    print("============================================================")
    print()

    sql = build_sql(sql_db, seed)

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
