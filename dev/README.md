# BakaKeeper – vývojové prostředí (Podman)

Lokální vývojové prostředí simulující školní infrastrukturu pomocí Podman kontejnerů.
Umožňuje vývoj a testování BakaKeeperu bez přístupu k produkčnímu AD, SQL nebo mailovému serveru.

## Architektura

```
                  +-----------------------+
                  |   hostitelský stroj   |
                  |                       |
                  |  BakaKeeper (Java 25) |
                  +--+------+------+------+
                     |      |      |
           LDAP/KDC  |  SQL |      | SMTP
                     v      v      v
   +------------+  +-------+  +----------+
   |  Samba4 AD |  | MSSQL |  |  Mailpit |
   |            |  |       |  |          |
   | dc.skola.  |  | sql.  |  | mail.    |
   |   local    |  | skola |  | skola.ext|
   |            |  | .local|  |          |
   | 172.20.0.10|  | .0.20 |  |  .0.30   |
   +------------+  +-------+  +----------+
        bakadev-net 172.20.0.0/24
```

### Služby

| Služba  | Kontejner        | Hostname          | Porty                                                    |
|---------|------------------|-------------------|----------------------------------------------------------|
| Samba4  | bakadev-samba4   | dc.skola.local    | LDAP 389, LDAPS 636, KDC 88, GC 3268/3269, DNS 15353   |
| MSSQL   | bakadev-mssql    | sql.skola.local   | 1433                                                     |
| Mailpit | bakadev-mailpit  | mail.skola.ext    | SMTP 1025, Web UI 8025                                   |

### Domény

- **skola.local** – interní AD doména (Samba4 DC, Kerberos realm `SKOLA.LOCAL`)
- **skola.ext** – externí/mailová doména (Mailpit)

## Požadavky

- Podman (nebo Docker) + podman-compose (nebo docker-compose)
- Python 3 (pro seed testovacích dat)
- openssl (generování TLS certifikátu pro MSSQL, standardně dostupný na macOS/Linux)
- Doporučeno: macOS nebo Linux

## Spuštění

```bash
cd dev
./setup-dev.sh
```

Skript automaticky:

1. Vygeneruje TLS certifikát pro MSSQL (`.data/mssql-tls/`)
2. Sestaví image a spustí kontejnery
3. Počká na zdravý stav Samba4 AD
4. Vytvoří OU strukturu a servisní účet `bakalari` v AD
5. Vytvoří servisní účet `mssql-svc` a zaregistruje Kerberos SPN
6. Exportuje Kerberos keytab pro MSSQL
7. Počká na MSSQL a inicializuje databázi `bakalari` (schéma + SQL login `bakalari`)
8. Restartuje MSSQL (načte keytab)
9. Spustí seed testovacích dat (žáci, učitelé, zákonní zástupci)
10. Nabídne přidání záznamů do `/etc/hosts` (vyžaduje sudo)

## Zastavení

```bash
# Zastavit kontejnery, zachovat data
./teardown-dev.sh

# Úplně smazat vše (kontejnery, image, data, /etc/hosts)
./teardown-dev.sh --clean
```

## Přístupové údaje

Definované v `.env`:

| Účet        | Uživatel                  | Heslo               | Použití                             |
|-------------|---------------------------|---------------------|-------------------------------------|
| AD Admin    | Administrator@SKOLA.LOCAL | BakaKeeper.2026     | Samba4 správa                       |
| Bakalari    | bakalari@skola.local      | Baka1234.2026       | BakaKeeper – LDAP (servisní účet)   |
| SQL bakalari| bakalari                  | Baka1234.2026       | SQL Server – db_owner na `bakalari` |
| MSSQL SA    | sa                        | SqlServer.Dev2026   | SQL Server admin                    |
| MSSQL SVC   | mssql-svc@SKOLA.LOCAL     | Mssql1234.2026      | SQL Kerberos SPN                    |

## Použití s BakaKeeperem

```bash
cd dev

# Inicializace (vytvoří local/settings.dat ze settings.yml)
java -jar bakakeeper.jar --init -f settings.yml -passphrase heslo

# Ověření konektivity
java -jar bakakeeper.jar --check -passphrase heslo --debug

# Synchronizace
java -jar bakakeeper.jar --sync -passphrase heslo --verbose
```

`settings.yml` v tomto adresáři je předkonfigurovaný pro dev prostředí:
- LDAP: `bakalari@skola.local` přes LDAPS (port 636)
- SQL: `sa` přes plain SQL auth (`method: sql`) – Kerberos s Azure SQL Edge nefunguje
- SMTP: Mailpit na portu 1025, bez autentizace

## Ověření funkčnosti

### LDAP

```bash
ldapsearch -x -H ldap://dc.skola.local \
  -D 'bakalari@skola.local' -w 'Baka1234.2026' \
  -b 'OU=Zaci,OU=Uzivatele,OU=Skola,DC=skola,DC=local' \
  '(objectClass=user)'
```

### Kerberos

```bash
export KRB5_CONFIG=$(pwd)/krb5.conf.dev
kinit bakalari@SKOLA.LOCAL
# Heslo: Baka1234.2026
```

### SQL

```bash
# sqlcmd využívá náš TLS certifikát – funguje i po restartu kontejneru
podman exec bakadev-mssql \
  /usr/local/bin/sqlcmd -S localhost -U sa -P 'SqlServer.Dev2026' \
  -Q "SELECT TOP 5 INTERN_KOD, PRIJMENI, JMENO FROM bakalari.dbo.zaci"
```

### Mailpit (web UI)

<http://localhost:8025>

## OU struktura v AD

Odpovídá produkčnímu AD (`zsstu.local`):

```
DC=skola,DC=local
  OU=Skola
    OU=Uzivatele
      OU=Zaci
        OU=Rocnik-1 ... OU=Rocnik-9   (seed vytvoří OU=Trida-A..E uvnitř)
        OU=StudiumUkonceno
      OU=Zamestnanci
        OU=Ucitele
        OU=Vedeni
        OU=Asistenti
        OU=Vychovatelky
        OU=Provoz
          OU=ICT
            OU=GlobalniSystemoveUcty
              CN=Bakaláři Sync        ← servisní účet bakalari
    OU=Skupiny
      OU=Zaci          (bezpečnostní skupiny tříd)
      OU=Uzivatele     (globální skupiny)
      OU=Distribucni   (distribuční listy)
    OU=Kontakty        (dynamické kontakty zákonných zástupců)
```

## Testovací data

Seed skripty v `seed/` vytváří realistická testovací data:

- Žáci ve třídách 1.A až 9.E (cca 174 aktivních + 6 absolventů)
- Učitelé s třídnictvím (34 třídních + 6 nett.)
- Vedení (3), asistenti (3), vychovatelky (3), provoz (3)
- Zákonní zástupci (175)

Data se generují z `seed/testdata.py` a nahrají do SQL i AD.

## Důležité poznámky

### TLS certifikát pro MSSQL
Azure SQL Edge generuje self-signed certifikát s **negativním sériovým číslem**.
Go 1.23+ (go-sqlcmd v1.9.0) odmítá takový certifikát při parsování (`x509: negative serial number`),
a to i při `SQLCMDENCRYPT=false`. `setup-dev.sh` proto vygeneruje vlastní certifikát
přes `openssl` a uloží ho do `.data/mssql-tls/`. MSSQL ho načte přes `mssql.conf`
(`tlscert`/`tlskey`).

### SQL autentizace v dev prostředí
Kerberos autentizace (`method: kerberos`) pro SQL nefunguje s kombinací Azure SQL Edge
a Samba4 KDC – Samba4 vrací prázdné `nameStrings` v TGS-REP a Azure SQL Edge nemá
plnohodnotnou Kerberos podporu. Dev prostředí proto používá `method: sql` (plain SQL Server
autentizace), v produkci je `method: kerberos` resp. `method: ntlm`.

### Samba4 plain LDAP
`ldap server require strong auth = No` v `smb.conf` – nutné pro plain LDAP bind
(BakaKeeper se přihlašuje jménem a heslem přes LDAP, ne TLS). LDAPS (port 636)
funguje vždy. Nastaveno automaticky v kontejneru.

### samba-tool user setpassword
Po `samba-tool user create` je vždy nutné zavolat `setpassword` – jinak NTLM hash
nemusí být platný a LDAP bind selže.

### MSSQL data na pojmenovaném svazku
`sqlservr` (UID 10001) potřebuje zápisová práva na datové soubory.
Bind-mount z macOS přes virtiofs toto nesplňuje → data jsou v pojmenovaném Podman
svazku `mssql-data`.

### DNS port 15353
macOS `mDNSResponder` obsazuje port 53 → DNS Samba4 je přesměrován na 15353.

## Soubory

```
dev/
  .env                   – proměnné prostředí (domény, hesla)
  .data/                 – perzistentní data kontejnerů (v .gitignore)
    samba/               – AD data Samba4 (bind-mount, vlastní root)
    keytabs/             – Kerberos keytaby (bind-mount)
    mssql-tls/           – TLS certifikát pro MSSQL (generuje setup-dev.sh)
  settings.yml           – šablona konfigurace BakaKeeperu pro dev
  krb5.conf.dev          – Kerberos konfigurace pro hostitele
  podman-compose.yml     – definice služeb
  setup-dev.sh           – inicializace prostředí
  teardown-dev.sh        – zastavení/úklid prostředí
  samba4/
    Containerfile        – image Samba4 AD DC
    entrypoint.sh        – provisioning AD při prvním startu
    provision-users.sh   – OU struktura a servisní účty
    exchange-schema.ldif – Exchange atributy pro Samba4
  mssql/
    Containerfile        – image Azure SQL Edge + go-sqlcmd
    mssql.conf           – konfigurace MSSQL (TLS, Kerberos, paměť)
    init-db.sql          – schéma databáze + SQL login bakalari
  seed/
    testdata.py          – generátor testovacích dat
    run-seed.sh          – spouštění seedu
```
