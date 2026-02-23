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
# OU struktura odpovídající produkčnímu AD (zsstu.local):
#
#   OU=Skola,DC=skola,DC=local
#   ├── OU=Uzivatele
#   │   ├── OU=Zaci
#   │   │   ├── OU=Rocnik-1 … Rocnik-9 (každý s OU=Trida-A … Trida-E)
#   │   │   └── OU=StudiumUkonceno
#   │   └── OU=Zamestnanci
#   │       ├── OU=Ucitele
#   │       ├── OU=Vedeni       ← ředitel, zástupci
#   │       ├── OU=Asistenti    ← asistenti pedagoga
#   │       ├── OU=Vychovatelky ← vychovatelé školní družiny
#   │       └── OU=Provoz       ← hospodářský/provozní personál
#   │           └── OU=ICT
#   │               └── OU=GlobalniSystemoveUcty  ← servisní účet bakalari
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

# Ročníky 1–9, každý s třídami A–E (odpovídá produkčnímu AD na zsstu.local)
for ROCNIK in $(seq 1 9); do
    ROCNIK_BASE="OU=Rocnik-${ROCNIK},${ZACI_BASE}"
    create_ou "Rocnik-${ROCNIK}" "${ZACI_BASE}"
    for TRIDA in A B C D E; do
        create_ou "Trida-${TRIDA}" "${ROCNIK_BASE}"
    done
done

create_ou "Zamestnanci"   "${UZIV_BASE}"
ZAMEST_BASE="OU=Zamestnanci,${UZIV_BASE}"
create_ou "Ucitele"       "${ZAMEST_BASE}"
create_ou "Vedeni"        "${ZAMEST_BASE}"
create_ou "Asistenti"     "${ZAMEST_BASE}"
create_ou "Vychovatelky"  "${ZAMEST_BASE}"
create_ou "Provoz"        "${ZAMEST_BASE}"
PROVOZ_BASE="OU=Provoz,${ZAMEST_BASE}"
create_ou "ICT"           "${PROVOZ_BASE}"
ICT_BASE="OU=ICT,${PROVOZ_BASE}"
create_ou "GlobalniSystemoveUcty" "${ICT_BASE}"

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
# Přidáme atributy, které Samba4 standardně neobsahuje, ale BakaKeeper je
# používá (jsou součástí MS Exchange / rozšířeného AD schématu):
#   - extensionAttribute1-15 (ID uživatele, sync flagy, metadata)
#   - proxyAddresses (seznam proxy-adres)
#   - msExchHideFromAddressLists, msExchRequireAuthToSendTo
#
# Po přidání atributů vynutíme reload schématu a přidáme je do mayContain
# tříd User a Contact, aby je bylo možné použít na uživatelských účtech
# i kontaktech zákonných zástupců.
# ---------------------------------------------------------------------------
echo ""
echo "[BakaDev] Importuji Exchange schema extension..."

LDAP_ADMIN_DN="CN=Administrator,CN=Users,${BASE_DN}"

# 1) Přidání attributeSchema definic (ldapadd -c přeskočí existující)
sed "s/DC=skola,DC=local/${BASE_DN}/g" /exchange-schema.ldif \
    | ldapadd -H ldap://localhost \
              -D "${LDAP_ADMIN_DN}" \
              -w "${ADMIN_PASSWORD}" \
              -c 2>&1 \
    | grep -v "^$" \
    | sed 's/^/  /' \
    || true    # chyba "Already exists" je v pořádku

# 2) Vynutíme reload schématu, aby nové atributy byly viditelné
echo "  [*] Reload schématu (schemaUpdateNow)..."
ldapmodify -H ldap://localhost \
           -D "${LDAP_ADMIN_DN}" \
           -w "${ADMIN_PASSWORD}" 2>/dev/null <<SCHEMA_RELOAD || true
dn:
changetype: modify
add: schemaUpdateNow
schemaUpdateNow: 1
SCHEMA_RELOAD

# Krátká pauza pro dokončení reload schématu
sleep 2

# 3) Přidáme nové atributy do mayContain tříd User a Contact,
#    aby je bylo možné nastavovat na uživatelských účtech a kontaktech.
#    ldapmodify -c pokračuje, pokud atribut v mayContain již existuje.
echo "  [*] Přidávám atributy do třídy User..."
ldapmodify -H ldap://localhost \
           -D "${LDAP_ADMIN_DN}" \
           -w "${ADMIN_PASSWORD}" \
           -c 2>/dev/null <<MAYCONTAIN_USER || true
dn: CN=User,CN=Schema,CN=Configuration,${BASE_DN}
changetype: modify
add: mayContain
mayContain: extensionAttribute1
mayContain: extensionAttribute2
mayContain: extensionAttribute3
mayContain: extensionAttribute4
mayContain: extensionAttribute5
mayContain: extensionAttribute6
mayContain: extensionAttribute7
mayContain: extensionAttribute8
mayContain: extensionAttribute9
mayContain: extensionAttribute10
mayContain: extensionAttribute11
mayContain: extensionAttribute12
mayContain: extensionAttribute13
mayContain: extensionAttribute14
mayContain: extensionAttribute15
mayContain: proxyAddresses
mayContain: msExchHideFromAddressLists
mayContain: msExchRequireAuthToSendTo
MAYCONTAIN_USER

echo "  [*] Přidávám atributy do třídy Contact..."
ldapmodify -H ldap://localhost \
           -D "${LDAP_ADMIN_DN}" \
           -w "${ADMIN_PASSWORD}" \
           -c 2>/dev/null <<MAYCONTAIN_CONTACT || true
dn: CN=Contact,CN=Schema,CN=Configuration,${BASE_DN}
changetype: modify
add: mayContain
mayContain: extensionAttribute1
mayContain: extensionAttribute2
mayContain: extensionAttribute3
mayContain: extensionAttribute4
mayContain: extensionAttribute5
mayContain: extensionAttribute6
mayContain: extensionAttribute7
mayContain: extensionAttribute8
mayContain: extensionAttribute9
mayContain: extensionAttribute10
mayContain: extensionAttribute11
mayContain: extensionAttribute12
mayContain: extensionAttribute13
mayContain: extensionAttribute14
mayContain: extensionAttribute15
mayContain: proxyAddresses
mayContain: msExchHideFromAddressLists
mayContain: msExchRequireAuthToSendTo
MAYCONTAIN_CONTACT

echo "  [*] Přidávám atributy do třídy Group..."
ldapmodify -H ldap://localhost \
           -D "${LDAP_ADMIN_DN}" \
           -w "${ADMIN_PASSWORD}" \
           -c 2>/dev/null <<MAYCONTAIN_GROUP || true
dn: CN=Group,CN=Schema,CN=Configuration,${BASE_DN}
changetype: modify
add: mayContain
mayContain: extensionAttribute1
mayContain: extensionAttribute2
mayContain: extensionAttribute3
mayContain: extensionAttribute4
mayContain: extensionAttribute5
mayContain: extensionAttribute6
mayContain: extensionAttribute7
mayContain: extensionAttribute8
mayContain: extensionAttribute9
mayContain: extensionAttribute10
mayContain: extensionAttribute11
mayContain: extensionAttribute12
mayContain: extensionAttribute13
mayContain: extensionAttribute14
mayContain: extensionAttribute15
mayContain: proxyAddresses
mayContain: msExchHideFromAddressLists
mayContain: msExchRequireAuthToSendTo
MAYCONTAIN_GROUP

# Finální reload schématu po úpravě tříd
ldapmodify -H ldap://localhost \
           -D "${LDAP_ADMIN_DN}" \
           -w "${ADMIN_PASSWORD}" 2>/dev/null <<SCHEMA_RELOAD2 || true
dn:
changetype: modify
add: schemaUpdateNow
schemaUpdateNow: 1
SCHEMA_RELOAD2

echo "[BakaDev] Exchange schema extension hotova."
echo ""
echo "[BakaDev] Vytváření servisního účtu 'bakalari'..."

# --userou očekává relativní cestu od base DN (bez DC= části)
# V produkci: OU=GlobalniSystemoveUcty,OU=ICT,OU=Provoz,OU=Zamestnanci,OU=Uzivatele,OU=Skola
BAKA_OU="OU=GlobalniSystemoveUcty,OU=ICT,OU=Provoz,OU=Zamestnanci,OU=Uzivatele,OU=Skola"
BAKA_DN="OU=GlobalniSystemoveUcty,${ICT_BASE}"

# Servisní účet pro synchronizaci – neinteraktivní systémový účet
samba-tool user create bakalari "${BAKALARI_PASSWORD}" \
    --userou="${BAKA_OU}" \
    --given-name="Bakaláři" \
    --surname="Sync" \
    --mail-address="bakalari@${DOMAIN}" 2>/dev/null \
    && echo "  [+] uživatel bakalari (v ${BAKA_DN})" \
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
echo "[BakaDev] Bakalari: ${BAKA_DN}"
echo "[BakaDev] =========================================="
