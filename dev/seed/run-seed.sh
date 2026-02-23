#!/bin/bash
# =============================================================================
# BakaKeeper – spuštění seed generátoru testovacích dat
#
# Postup:
#   1. Vygeneruje SQL a AD shell skript pomocí testdata.py
#   2. Aplikuje SQL INSERT data do MSSQL kontejneru (bakadev-mssql)
#   3. Vytvoří AD účty zaměstnanců v Samba4 kontejneru (bakadev-samba4)
#
# Použití:
#   ./run-seed.sh                   # standardní spuštění
#   ./run-seed.sh --sql-only        # pouze SQL část
#   ./run-seed.sh --ad-only         # pouze AD část
#   ./run-seed.sh --dry-run         # pouze zobrazí, co by provedl
#
# Požadavky:
#   - python3 (stdlib, bez externích závislostí)
#   - běžící kontejnery: bakadev-mssql, bakadev-samba4
#   - soubor .env v nadřazeném adresáři (dev/.env)
# =============================================================================

set -euo pipefail

# Barevný výstup (odpovídá setup-dev.sh)
OK="\\033[0;32m[OK]\\033[0m"
INFO="\\033[0;34m[INFO]\\033[0m"
WARN="\\033[0;33m[WARN]\\033[0m"
ERR="\\033[0;31m[ERR]\\033[0m"

# Přesuneme do adresáře seed/ (skript lze spustit odkudkoli)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEV_DIR="$(dirname "${SCRIPT_DIR}")"

# Načtení proměnných z dev/.env
ENV_FILE="${DEV_DIR}/.env"
if [ ! -f "${ENV_FILE}" ]; then
    echo -e "${ERR} Soubor .env nenalezen: ${ENV_FILE}"
    exit 1
fi
source "${ENV_FILE}"

DOMAIN="${DOMAIN:-skola.local}"
REALM="${REALM:-SKOLA.LOCAL}"
SQL_DB="${SQL_DB:-bakalari}"
SQL_SA_PASSWORD="${SQL_SA_PASSWORD:-SqlServer.Dev2026}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-BakaKeeper.2026}"

# Sestavení base DN z domény (skola.local → DC=skola,DC=local)
IFS='.' read -ra _PARTS <<< "${DOMAIN}"
BASE_DN="DC=${_PARTS[0]},DC=${_PARTS[1]}"

# Parsování argumentů
DO_SQL=true
DO_AD=true
DRY_RUN=false

for arg in "$@"; do
    case "${arg}" in
        --sql-only) DO_AD=false ;;
        --ad-only)  DO_SQL=false ;;
        --dry-run)  DRY_RUN=true ;;
        *) echo -e "${WARN} Neznámý přepínač: ${arg}" ;;
    esac
done

# Dočasné soubory
TMP_SQL=$(mktemp /tmp/bakadev-seed-XXXXXX.sql)
TMP_SH=$(mktemp /tmp/bakadev-seed-XXXXXX.sh)
trap 'rm -f "${TMP_SQL}" "${TMP_SH}"' EXIT

echo ""
echo "============================================================"
echo " BakaKeeper – seed testovacích dat"
echo " Doména:  ${DOMAIN}  (${BASE_DN})"
echo " SQL DB:  ${SQL_DB}"
echo "============================================================"
echo ""

# ---------------------------------------------------------------------------
# Krok 1: Generování dat pomocí testdata.py
# ---------------------------------------------------------------------------
echo -e "${INFO} Generuji testovací data (testdata.py)..."

python3 "${SCRIPT_DIR}/testdata.py" \
    --sql-file "${TMP_SQL}" \
    --sh-file  "${TMP_SH}" \
    --domain   "${DOMAIN}" \
    --base-dn  "${BASE_DN}" \
    --ad-pass  "${ADMIN_PASSWORD}" \
    --sql-db   "${SQL_DB}" 2>&1 | sed 's/^/  /'

echo -e "${OK} Data vygenerována."

if ${DRY_RUN}; then
    echo ""
    echo -e "${WARN} DRY-RUN: data nebyla aplikována."
    echo "  SQL:  ${TMP_SQL}"
    echo "  SH:   ${TMP_SH}"
    echo ""
    python3 "${SCRIPT_DIR}/testdata.py" --stats --domain "${DOMAIN}"
    exit 0
fi

echo ""

# ---------------------------------------------------------------------------
# Krok 2: Aplikace SQL dat do MSSQL
# ---------------------------------------------------------------------------
if ${DO_SQL}; then
    echo -e "${INFO} Aplikuji SQL seed data do bakadev-mssql..."

    # Zkontrolujeme, zda kontejner běží
    if ! podman inspect --format='{{.State.Status}}' "bakadev-mssql" 2>/dev/null | grep -q "running"; then
        echo -e "${ERR} Kontejner bakadev-mssql není spuštěn. Spusťte nejprve setup-dev.sh."
        exit 1
    fi

    podman cp "${TMP_SQL}" bakadev-mssql:/tmp/seed.sql

    # SQLCMDENCRYPT=false: úplně vypne TLS (disable) – go-sqlcmd odmítá
    # self-signed certifikát s negativním sér. číslem (x509: negative serial number)
    podman exec \
        -e SQLCMDENCRYPT=false \
        bakadev-mssql \
        /usr/local/bin/sqlcmd \
            -S localhost \
            -U sa \
            -P "${SQL_SA_PASSWORD}" \
            -d "${SQL_DB}" \
            -i /tmp/seed.sql \
            -b 2>&1 | sed 's/^/  /'

    echo -e "${OK} SQL seed hotov."
    echo ""
fi

# ---------------------------------------------------------------------------
# Krok 3: Vytvoření AD účtů zaměstnanců v Samba4
# ---------------------------------------------------------------------------
if ${DO_AD}; then
    echo -e "${INFO} Vytvářím AD účty zaměstnanců v bakadev-samba4..."

    # Zkontrolujeme, zda kontejner běží
    if ! podman inspect --format='{{.State.Status}}' "bakadev-samba4" 2>/dev/null | grep -q "running"; then
        echo -e "${ERR} Kontejner bakadev-samba4 není spuštěn. Spusťte nejprve setup-dev.sh."
        exit 1
    fi

    podman cp "${TMP_SH}" bakadev-samba4:/tmp/seed-ad.sh
    podman exec bakadev-samba4 bash /tmp/seed-ad.sh 2>&1 | sed 's/^/  /'

    echo -e "${OK} AD seed hotov."
    echo ""
fi

# ---------------------------------------------------------------------------
# Shrnutí
# ---------------------------------------------------------------------------
echo "============================================================"
echo -e " ${OK} Seed testovacích dat dokončen!"
echo "============================================================"
echo ""
python3 "${SCRIPT_DIR}/testdata.py" --stats --domain "${DOMAIN}"
echo ""
