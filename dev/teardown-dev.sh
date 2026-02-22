#!/bin/bash
# =============================================================================
# BakaKeeper – zastavení a úklid vývojového prostředí
#
# Možnosti:
#   ./teardown-dev.sh           – zastaví kontejnery, zachová data
#   ./teardown-dev.sh --clean   – zastaví kontejnery A smaže všechna data
#
# Co je kde:
#   .data/samba/   – AD data Samba4 (bind-mount, soubory vlastní root)
#   .data/keytabs/ – Kerberos keytaby (bind-mount, soubory vlastní root)
#   mssql-data     – pojmenovaný Podman svazek (MSSQL databázové soubory)
#
# Záznamy v /etc/hosts se smažou pouze s volbou --clean (vyžaduje sudo).
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${SCRIPT_DIR}"

CLEAN=false
if [[ "${1:-}" == "--clean" ]]; then
    CLEAN=true
fi

# Nastavení Podman socketu (stejná logika jako v setup-dev.sh)
if [ -z "${DOCKER_HOST:-}" ] && [ -S /var/run/docker.sock ]; then
    export DOCKER_HOST="unix:///var/run/docker.sock"
fi

if command -v podman-compose &>/dev/null; then
    COMPOSE="podman-compose"
else
    COMPOSE="docker-compose"
fi

COMPOSE="${COMPOSE} -f podman-compose.yml"

echo ""
if ${CLEAN}; then
    echo "============================================================"
    echo " BakaKeeper – ÚPLNÉ smazání vývojového prostředí"
    echo " Budou smazána: kontejnery, image, .data/ a MSSQL svazek!"
    echo "============================================================"
    read -r -p "Opravdu smazat vše? [y/N] " response
    if [[ ! "${response}" =~ ^[Yy]$ ]]; then
        echo "Zrušeno."
        exit 0
    fi
    # Zastavení kontejnerů, odebrání lokálních images a pojmenovaných svazků
    # --volumes odstraní pojmenovaný svazek mssql-data
    ${COMPOSE} down --volumes --rmi local
    # Čištění bind-mount adresářů .data/
    # Samba data vlastní root:root → vyžaduje sudo
    echo "Mažu data z .data/ (vyžaduje sudo pro soubory vlastněné rootem)..."
    sudo find .data/samba   -mindepth 1 -delete 2>/dev/null || true
    sudo find .data/keytabs -mindepth 1 -delete 2>/dev/null || true
    # Obnova prázdných adresářů a .gitkeep (zachování struktury v gitu)
    mkdir -p .data/samba .data/keytabs
    touch .data/samba/.gitkeep .data/keytabs/.gitkeep
    # Odebrání záznamů z /etc/hosts (vyžaduje sudo)
    if grep -q "BakaKeeper vývojové prostředí" /etc/hosts 2>/dev/null; then
        echo "Odstraňuji záznamy z /etc/hosts (vyžaduje sudo)..."
        sudo sed -i.bak '/BakaKeeper vývojové prostředí/,/mail\.skola\.ext/d' /etc/hosts
        echo "[OK] Záznamy odstraněny."
    fi
    echo "[OK] Vývojové prostředí smazáno."
else
    echo "============================================================"
    echo " BakaKeeper – zastavení vývojového prostředí"
    echo " Data jsou zachována pro příští spuštění:"
    echo "   AD data:  .data/samba/"
    echo "   Keytaby:  .data/keytabs/"
    echo "   MSSQL DB: podman volume inspect dev_mssql-data"
    echo "============================================================"
    ${COMPOSE} down
    echo "[OK] Kontejnery zastaveny. Data zachována."
fi
echo ""
