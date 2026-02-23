#!/bin/bash
# =============================================================================
# Samba4 AD DC – vstupní bod kontejneru
#
# Při prvním spuštění provede provisioning domény.
# Při dalších spuštěních obnoví existující doménu.
# =============================================================================

set -e

DOMAIN="${DOMAIN:-skola.local}"
REALM="${REALM:-SKOLA.LOCAL}"
NETBIOS="${NETBIOS_DOMAIN:-SKOLA}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-BakaSync#Dev2024!}"
DC_HOSTNAME="${DC_HOSTNAME:-dc01}"

# Zjistíme, zda doména je již provisionována (existuje databáze secrets.ldb)
if [ ! -f /var/lib/samba/private/secrets.ldb ]; then
    echo "[BakaDev] === Prvotní inicializace domény ${REALM} ==="
    echo "[BakaDev] Toto může trvat až 60 sekund..."

    # Vytvoření potřebných adresářů
    mkdir -p /var/lib/samba /var/log/samba /run/samba /keytabs

    # Provisioning nové domény Samba4 AD
    samba-tool domain provision \
        --use-rfc2307 \
        --realm="${REALM}" \
        --domain="${NETBIOS}" \
        --server-role=dc \
        --dns-backend=SAMBA_INTERNAL \
        --adminpass="${ADMIN_PASSWORD}" \
        --host-name="${DC_HOSTNAME}" \
        --option="dns forwarder=8.8.8.8" \
        --option="interfaces=lo eth0" \
        --option="bind interfaces only=yes" \
        --option="log level=1" \
        --option="ldap server require strong auth=No"

    # Kerberos konfigurace vygenerovaná provisoningem
    cp /var/lib/samba/private/krb5.conf /etc/krb5.conf

    # smb.conf zapíše samba-tool do /etc/samba/smb.conf (v zapisovatelné vrstvě
    # kontejneru). Uložíme ho do bind-mount svazku, aby přežil rebuild image.
    cp /etc/samba/smb.conf /var/lib/samba/smb.conf

    echo "[BakaDev] Doména ${REALM} úspěšně inicializována."
    echo "[BakaDev] Spusťte 'podman exec bakadev-samba4 /provision-users.sh' pro vytvoření OU struktury."
else
    echo "[BakaDev] Obnovení existující domény ${REALM}..."
    # Aktualizace Kerberos konfigurace (může být přepsána při rebuildu image)
    [ -f /var/lib/samba/private/krb5.conf ] && cp /var/lib/samba/private/krb5.conf /etc/krb5.conf
    # smb.conf je v zapisovatelné vrstvě kontejneru – po rebuildu image je pryč.
    # Obnovíme ho ze svazku, kde ho uložila první inicializace.
    if [ -f /var/lib/samba/smb.conf ]; then
        cp /var/lib/samba/smb.conf /etc/samba/smb.conf
    else
        echo "[BakaDev] VAROVÁNÍ: smb.conf ve svazku nenalezen – doména může být poškozená."
    fi
    mkdir -p /run/samba /keytabs
fi

echo "[BakaDev] Spouštím Samba4 AD DC pro ${REALM}..."

# Přesměrujeme DNS na interní Samba4 DNS.
# samba_dnsupdate ověřuje záznamy domény přes /etc/resolv.conf.
# Bez tohoto nastavení selhává s 17s timeoutem, protože Podman DNS
# (výchozí resolver kontejneru) doménu skola.local nezná → fatal exit.
# Samba4 přeposílá neznámé dotazy na DNS forwarder 8.8.8.8.
echo "nameserver 127.0.0.1" > /etc/resolv.conf
echo "search skola.local" >> /etc/resolv.conf

# Spuštění Samby v popředí (Podman sleduje tento proces)
# --debug-stdout: logy jdou na stdout (viditelné přes 'podman logs bakadev-samba4')
# -d 1: úroveň ladění 1 (standardní provozní hlášení)
exec samba --foreground --no-process-group --debug-stdout -d 1
