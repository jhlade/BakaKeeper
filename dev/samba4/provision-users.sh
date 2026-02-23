#!/bin/bash
# =============================================================================
# Samba4 AD DC – inicializace struktury a testovacích dat
#
# Vytvoří strukturu organizačních jednotek odpovídající settings.conf šabloně
# a testovací účty pro vývoj a ladění BakaKeeperu.
#
# Volá se ze setup-dev.sh pomocí:
#   podman exec bakadev-samba4 /provision-users.sh
# =============================================================================

set -e

DOMAIN="${DOMAIN:-skola.local}"
REALM="${REALM:-SKOLA.LOCAL}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-BakaSync#Dev2024!}"
BAKALARI_PASSWORD="${BAKALARI_PASSWORD:-Baka1234!}"

# Sestavení základního DN z doménového jména (skola.local → DC=skola,DC=local)
IFS='.' read -ra PARTS <<< "${DOMAIN}"
BASE_DN="DC=${PARTS[0]},DC=${PARTS[1]}"

# Pomocná funkce – vytvoří OU, pokud ještě neexistuje
create_ou() {
    local ou_name="$1"
    local parent_dn="$2"
    samba-tool ou create "OU=${ou_name},${parent_dn}" 2>/dev/null \
        && echo "  [+] OU=${ou_name}" \
        || echo "  [=] OU=${ou_name} (již existuje)"
}

echo "[BakaDev] =========================================="
echo "[BakaDev] Provisioning OU struktury pro ${DOMAIN}"
echo "[BakaDev] =========================================="

# ------------------------------------------
# OU struktura odpovídající settings.conf:
#
#   OU=Skola,DC=skola,DC=local
#   ├── OU=Uzivatele
#   │   ├── OU=Zaci
#   │   │   └── OU=StudiumUkonceno
#   │   └── OU=Zamestnanci
#   │       ├── OU=Ucitele
#   │       ├── OU=Vedeni       ← ředitel, zástupci
#   │       ├── OU=Asistenti    ← asistenti pedagoga
#   │       ├── OU=Vychovatelky ← vychovatelé školní družiny
#   │       └── OU=Provoz       ← hospodářský/provozní personál
#   ├── OU=Skupiny
#   │   ├── OU=Zaci
#   │   ├── OU=Uzivatele
#   │   └── OU=Distribucni
#   └── OU=Kontakty
# ------------------------------------------

SKOLA_BASE="OU=Skola,${BASE_DN}"

# Kořenová OU
create_ou "Skola" "${BASE_DN}"

# Uživatelé
create_ou "Uzivatele"   "${SKOLA_BASE}"
UZIV_BASE="OU=Uzivatele,${SKOLA_BASE}"

create_ou "Zaci"        "${UZIV_BASE}"
ZACI_BASE="OU=Zaci,${UZIV_BASE}"
create_ou "StudiumUkonceno" "${ZACI_BASE}"

create_ou "Zamestnanci"   "${UZIV_BASE}"
ZAMEST_BASE="OU=Zamestnanci,${UZIV_BASE}"
create_ou "Ucitele"       "${ZAMEST_BASE}"
create_ou "Vedeni"        "${ZAMEST_BASE}"
create_ou "Asistenti"     "${ZAMEST_BASE}"
create_ou "Vychovatelky"  "${ZAMEST_BASE}"
create_ou "Provoz"        "${ZAMEST_BASE}"

# Skupiny
create_ou "Skupiny"     "${SKOLA_BASE}"
SKUPINY_BASE="OU=Skupiny,${SKOLA_BASE}"
create_ou "Zaci"        "${SKUPINY_BASE}"
create_ou "Uzivatele"   "${SKUPINY_BASE}"
create_ou "Distribucni" "${SKUPINY_BASE}"

# Kontakty zákonných zástupců
create_ou "Kontakty"    "${SKOLA_BASE}"

echo ""
echo "[BakaDev] OU struktura vytvořena."

# ---------------------------------------------------------------------------
# Exchange schema extension
#
# Přidáme atributy msExchHideFromAddressLists a msExchRequireAuthToSendTo,
# které Samba4 standardně neobsahuje, ale BakaKeeper je používá.
# Přidání se provede jako schema modification – pokud atributy již existují,
# ldapadd s -c je přeskočí a pokračuje dál (exit kód ignorujeme).
# ---------------------------------------------------------------------------
echo ""
echo "[BakaDev] Importuji Exchange schema extension..."

# Dynamicky doplníme Base DN do LDIF šablony a importujeme
sed "s/DC=skola,DC=local/${BASE_DN}/g" /exchange-schema.ldif \
    | ldapadd -H ldap://localhost \
              -D "CN=Administrator,CN=Users,${BASE_DN}" \
              -w "${ADMIN_PASSWORD}" \
              -c 2>&1 \
    | grep -v "^$" \
    | sed 's/^/  /' \
    || true    # chyba "Already exists" je v pořádku

echo "[BakaDev] Exchange schema extension hotova."
echo ""
echo "[BakaDev] Vytváření servisního účtu 'bakalari'..."

# Servisní účet pro synchronizaci (odpovídá user= v settings.conf)
samba-tool user create bakalari "${BAKALARI_PASSWORD}" \
    --given-name="Bakalari" \
    --surname="Sync" \
    --mail-address="bakalari@${DOMAIN}" 2>/dev/null \
    && echo "  [+] uživatel bakalari" \
    || echo "  [=] uživatel bakalari (již existuje)"

# Explicitní reset hesla – samba-tool user create může nastavit hash v nekompatibilním
# stavu (pwdLastSet=0 nebo chybný formát). setpassword ho správně inicializuje vždy.
samba-tool user setpassword bakalari --newpassword="${BAKALARI_PASSWORD}" 2>/dev/null \
    && echo "  [=] heslo bakalari nastaveno" \
    || echo "  [!] VAROVÁNÍ: nepodařilo se nastavit heslo bakalari"

# Přidání do skupin potřebných pro správu účtů
samba-tool group addmembers "Domain Admins"    bakalari 2>/dev/null || true
samba-tool group addmembers "Account Operators" bakalari 2>/dev/null || true

echo ""
echo "[BakaDev] =========================================="
echo "[BakaDev] AD provisioning dokončen."
echo "[BakaDev]"
echo "[BakaDev] Přihlašovací údaje:"
echo "[BakaDev]   Admin:    Administrator / ${ADMIN_PASSWORD}"
echo "[BakaDev]   Sync:     bakalari / ${BAKALARI_PASSWORD}"
echo "[BakaDev]"
echo "[BakaDev] Base DN:  ${BASE_DN}"
echo "[BakaDev] Zaci:     OU=Zaci,OU=Uzivatele,OU=Skola,${BASE_DN}"
echo "[BakaDev] =========================================="
