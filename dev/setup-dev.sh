#!/bin/bash
# =============================================================================
# BakaKeeper – inicializace vývojového prostředí v Podmanu
#
# Postup:
#   1. Sestaví Docker image a spustí kontejnery
#   2. Čeká na Samba4 AD DC (LDAP port)
#   3. Vytvoří OU strukturu a servisní účet bakalari
#   4. Vytvoří servisní účet mssql-svc a zaregistruje SPN
#   5. Exportuje Kerberos keytab pro MSSQL
#   6. Čeká na MSSQL a inicializuje databázi
#   7. Restartuje MSSQL (aby načetl keytab)
#   8. Přidá záznamy do /etc/hosts (vyžaduje sudo)
#   9. Vypíše instrukce pro použití
#
# Použití:
#   cd dev && ./setup-dev.sh
#
# Požadavky: podman, podman-compose (nebo docker-compose)
# =============================================================================

set -euo pipefail

# Barevný výstup
OK="\033[0;32m[OK]\033[0m"
INFO="\033[0;34m[INFO]\033[0m"
WARN="\033[0;33m[WARN]\033[0m"
ERR="\033[0;31m[ERR]\033[0m"

# Přesuneme se do adresáře dev/ (skript lze spustit odkudkoli)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${SCRIPT_DIR}"

# Načtení proměnných prostředí
if [ ! -f .env ]; then
    echo -e "${ERR} Soubor .env nenalezen. Spusťte skript z adresáře dev/."
    exit 1
fi
source .env

# Krok 0: Příprava datových adresářů pro bind-mount volumes
# Adresáře musí existovat před spuštěním compose.
# Poznámka: mssql data jsou v pojmenovaném Podman svazku (ne bind-mount).
mkdir -p .data/samba .data/keytabs

# Ujistíme se, že DOCKER_HOST ukazuje na Podman socket.
# podman-compose / docker-compose hledají Docker socket; na macOS bez Docker Desktopu
# je správný socket na /var/run/docker.sock (symlink → Podman socket).
if [ -z "${DOCKER_HOST:-}" ]; then
    if [ -S /var/run/docker.sock ]; then
        export DOCKER_HOST="unix:///var/run/docker.sock"
    else
        echo -e "${ERR} Podman socket nenalezen. Spusťte Podman Desktop nebo 'podman machine start'."
        exit 1
    fi
fi

# Zjistíme, zda máme podman-compose nebo docker-compose
if command -v podman-compose &>/dev/null; then
    COMPOSE="podman-compose"
elif command -v docker-compose &>/dev/null; then
    COMPOSE="docker-compose"
else
    echo -e "${ERR} Nenalezen podman-compose ani docker-compose."
    echo "Instalace: pip install podman-compose"
    exit 1
fi

# Compose příkaz vždy s explicitním názvem souboru
# (docker-compose hledá docker-compose.yml, náš soubor se jmenuje jinak)
COMPOSE="${COMPOSE} -f podman-compose.yml"

echo ""
echo "============================================================"
echo " BakaKeeper – vývojové prostředí (Podman)"
echo " Doména:  ${REALM}"
echo " Compose: ${COMPOSE}"
echo "============================================================"
echo ""

# ---------------------------------------------------------------------------
# Funkce – čekání na dostupnost TCP portu
# ---------------------------------------------------------------------------
wait_for_port() {
    local host="$1"
    local port="$2"
    local desc="$3"
    local max_wait="${4:-120}"
    local elapsed=0

    echo -e "${INFO} Čekám na ${desc} (${host}:${port})..."
    while ! nc -z -w3 "${host}" "${port}" 2>/dev/null; do
        sleep 3
        elapsed=$((elapsed + 3))
        if [ ${elapsed} -ge ${max_wait} ]; then
            echo -e "${ERR} Timeout! ${desc} není dostupná po ${max_wait} sekundách."
            echo "Zkontrolujte logy: ${COMPOSE} logs"
            exit 1
        fi
        echo -e "  ... ${elapsed}s"
    done
    echo -e "${OK} ${desc} dostupná."
}

# ---------------------------------------------------------------------------
# Funkce – čekání na zdravý stav kontejneru
# ---------------------------------------------------------------------------
wait_for_healthy() {
    local container="$1"
    local max_wait="${2:-180}"
    local elapsed=0

    echo -e "${INFO} Čekám na health check kontejneru ${container}..."
    while [ "$(podman inspect --format='{{.State.Health.Status}}' "${container}" 2>/dev/null)" != "healthy" ]; do
        sleep 5
        elapsed=$((elapsed + 5))
        if [ ${elapsed} -ge ${max_wait} ]; then
            echo -e "${ERR} Kontejner ${container} není healthy po ${max_wait} sekundách."
            podman logs --tail=20 "${container}"
            exit 1
        fi
        echo -e "  ... ${elapsed}s (status: $(podman inspect --format='{{.State.Health.Status}}' "${container}" 2>/dev/null || echo 'neznámý'))"
    done
    echo -e "${OK} Kontejner ${container} je healthy."
}

# ---------------------------------------------------------------------------
# Krok 1: Sestavení a spuštění kontejnerů
# ---------------------------------------------------------------------------
echo -e "${INFO} Sestavuji image a spouštím kontejnery..."
${COMPOSE} up -d --build

echo ""

# ---------------------------------------------------------------------------
# Krok 2: Čekání na Samba4
# ---------------------------------------------------------------------------
wait_for_healthy "bakadev-samba4"

# ---------------------------------------------------------------------------
# Krok 3: OU struktura a servisní účet bakalari
# ---------------------------------------------------------------------------
echo -e "${INFO} Vytvářím OU strukturu a účet 'bakalari' v AD..."
podman exec \
    -e DOMAIN="${DOMAIN}" \
    -e REALM="${REALM}" \
    -e ADMIN_PASSWORD="${ADMIN_PASSWORD}" \
    -e BAKALARI_PASSWORD="${BAKALARI_PASSWORD}" \
    bakadev-samba4 \
    /provision-users.sh

echo ""

# ---------------------------------------------------------------------------
# Krok 4: Servisní účet MSSQL a Kerberos SPN
# ---------------------------------------------------------------------------
echo -e "${INFO} Vytvářím servisní účet 'mssql-svc' pro SQL Server..."

# Vytvoření účtu mssql-svc
podman exec bakadev-samba4 \
    samba-tool user create mssql-svc "${MSSQL_SVC_PASSWORD}" \
        --given-name="MSSQL" \
        --surname="Service" 2>/dev/null \
    && echo -e "  [+] uživatel mssql-svc" \
    || echo -e "  [=] uživatel mssql-svc (již existuje)"

# Registrace SPN (Service Principal Name) pro MSSQL
# Formát: MSSQLSvc/<hostname>:<port>
podman exec bakadev-samba4 \
    samba-tool spn add "${SQL_SPN}" mssql-svc 2>/dev/null \
    && echo -e "  [+] SPN: ${SQL_SPN}" \
    || echo -e "  [=] SPN: ${SQL_SPN} (již existuje)"

# ---------------------------------------------------------------------------
# Krok 5: Export Kerberos keytabu pro MSSQL
# ---------------------------------------------------------------------------
echo -e "${INFO} Exportuji Kerberos keytab pro MSSQL..."

podman exec bakadev-samba4 \
    samba-tool domain exportkeytab /keytabs/mssql.keytab \
        --principal="mssql-svc@${REALM}"

echo -e "${OK} Keytab uložen do sdíleného svazku (keytabs:/keytabs/mssql.keytab)."

# ---------------------------------------------------------------------------
# Krok 6: Čekání na MSSQL a inicializace databáze
# ---------------------------------------------------------------------------
# Azure SQL Edge při prvním startu inicializuje systémové databáze – dáme mu 6 minut
wait_for_healthy "bakadev-mssql" 360

echo -e "${INFO} Inicializuji databázi '${SQL_DB}'..."
# go-sqlcmd v /usr/local/bin/sqlcmd (mssql-tools není pro ARM64 dostupný přes apt)
podman exec bakadev-mssql \
    /usr/local/bin/sqlcmd \
        -S localhost \
        -U sa \
        -P "${SQL_SA_PASSWORD}" \
        -i /init-db.sql \
        -b

echo -e "${OK} Databáze '${SQL_DB}' inicializována."
echo ""

# ---------------------------------------------------------------------------
# Krok 7: Restart MSSQL kvůli načtení keytabu
# ---------------------------------------------------------------------------
echo -e "${INFO} Restartuji MSSQL pro načtení Kerberos keytabu..."
podman restart bakadev-mssql
# Po restartu je DB již inicializována, stačí 2 minuty
wait_for_healthy "bakadev-mssql" 120

# ---------------------------------------------------------------------------
# Krok 8: Záznamy v /etc/hosts
# ---------------------------------------------------------------------------
echo ""
echo -e "${INFO} Pro přístup z hostitele je nutné přidat záznamy do /etc/hosts."
echo -e "${WARN} Tato operace vyžaduje sudo."
echo ""

# Zkontrolujeme, zda záznamy již existují
if grep -q "dc01.zsstu.local" /etc/hosts 2>/dev/null; then
    echo -e "  [=] Záznamy v /etc/hosts již existují – přeskakuji."
else
    read -r -p "Přidat záznamy do /etc/hosts? (sudo) [y/N] " response
    if [[ "${response}" =~ ^[Yy]$ ]]; then
        sudo tee -a /etc/hosts <<EOF

# BakaKeeper vývojové prostředí – přidáno setup-dev.sh
127.0.0.1  dc01.zsstu.local zsstu.local
127.0.0.1  mssql.zsstu.local
127.0.0.1  mail.zsstu.local
EOF
        echo -e "${OK} Záznamy přidány do /etc/hosts."
    else
        echo -e "${WARN} Záznamy nebyly přidány. Kerberos autentizace nebude funkční."
        echo "Přidejte ručně do /etc/hosts:"
        echo "  127.0.0.1  dc01.zsstu.local zsstu.local"
        echo "  127.0.0.1  mssql.zsstu.local"
        echo "  127.0.0.1  mail.zsstu.local"
    fi
fi

# ---------------------------------------------------------------------------
# Hotovo – instrukce pro použití
# ---------------------------------------------------------------------------
echo ""
echo "============================================================"
echo -e " ${OK} Vývojové prostředí je připraveno!"
echo "============================================================"
echo ""
echo " Služby:"
echo "   LDAP:     ldap://dc01.zsstu.local:389"
echo "   Kerberos: dc01.zsstu.local:88"
echo "   MSSQL:    mssql.zsstu.local:1433  (SA: ${SQL_SA_PASSWORD})"
echo "   Mailpit:  http://localhost:8025"
echo ""
echo " Kerberos autentizace pro aplikaci:"
echo "   export KRB5_CONFIG=$(pwd)/krb5.conf.dev"
echo "   kinit bakalari@${REALM}"
echo "   # Heslo: ${BAKALARI_PASSWORD}"
echo ""
echo " LDAP dotaz (ověření AD):"
echo "   ldapsearch -x -H ldap://dc01.zsstu.local \\"
echo "     -D 'bakalari@${DOMAIN}' -w '${BAKALARI_PASSWORD}' \\"
echo "     -b 'OU=Zaci,OU=Uzivatele,OU=Skola,DC=zsstu,DC=local' '(objectClass=user)'"
echo ""
echo " Zastavení prostředí:"
echo "   ./teardown-dev.sh"
echo ""
