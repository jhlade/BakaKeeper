# BakaKeeper -- Vyvojove prostredi (Podman)

Lokalni vyvojove prostredi simulujici skolni infrastrukturu
pomoci Podman kontejneru. Umoznuje vyvoj a testovani BakaKeeperu
bez pristupu k produkcimu AD, SQL nebo mailovemu serveru.

## Architektura

```
                  +-----------------------+
                  |   hostitelsky stroj   |
                  |                       |
                  |  BakaKeeper (Java 25) |
                  +--+------+------+------+
                     |      |      |
           LDAP/KDC  |  SQL |      | SMTP
                     v      v      v
   +------------+  +------+  +----------+
   |  Samba4 AD |  | MSSQL|  |  Mailpit |
   |            |  |      |  |          |
   | dc.skola.  |  | sql. |  | mail.    |
   |   local    |  | skola|  | skola.ext|
   |            |  | .local|  |          |
   | 172.20.0.10|  |.0.20 |  | .0.30    |
   +------------+  +------+  +----------+
        bakadev-net 172.20.0.0/24
```

### Sluzby

| Sluzba   | Kontejner        | Hostname          | Porty                                    |
|----------|------------------|-------------------|------------------------------------------|
| Samba4   | bakadev-samba4   | dc.skola.local    | LDAP 389, LDAPS 636, KDC 88, GC 3268/3269, DNS 15353 |
| MSSQL    | bakadev-mssql    | sql.skola.local   | 1433                                     |
| Mailpit  | bakadev-mailpit  | mail.skola.ext    | SMTP 1025, Web UI 8025                   |

### Domeny

- **skola.local** -- interni AD domena (Samba4 DC, Kerberos realm `SKOLA.LOCAL`)
- **skola.ext** -- externi/mailova domena (Mailpit)

## Pozadavky

- Podman (nebo Docker) + podman-compose (nebo docker-compose)
- Python 3 (pro seed testovacich dat)
- Doporuceno: macOS nebo Linux

## Spusteni

```bash
cd dev
./setup-dev.sh
```

Skript automaticky:
1. Sestavi image a spusti kontejnery
2. Pocka na zdravy stav Samba4 AD (LDAP health check)
3. Vytvori OU strukturu a servisni ucet `bakalari` v AD
4. Vytvori servisni ucet `mssql-svc` a zaregistruje Kerberos SPN
5. Exportuje Kerberos keytab pro MSSQL
6. Pocka na MSSQL a inicializuje databazi `bakalari`
7. Restartuje MSSQL (nacte keytab)
8. Spusti seed testovacich dat (zaci, ucitele, zakonni zastupci)
9. Nabidne pridani zaznamu do `/etc/hosts` (vyzaduje sudo)

## Zastaveni

```bash
# Zastavit kontejnery, zachovat data
./teardown-dev.sh

# Uplne smazat vse (kontejnery, image, data, /etc/hosts)
./teardown-dev.sh --clean
```

## Pristupove udaje

Definovane v `.env`:

| Ucet        | Uzivatel              | Heslo              | Pouziti                  |
|-------------|-----------------------|--------------------|--------------------------|
| AD Admin    | Administrator@SKOLA.LOCAL | BakaKeeper.2026 | Samba4 sprava            |
| Bakalari    | bakalari@skola.local  | Baka1234.2026      | BakaKeeper servisni ucet |
| MSSQL SA    | sa                    | SqlServer.Dev2026  | SQL Server admin         |
| MSSQL SVC   | mssql-svc@SKOLA.LOCAL | Mssql1234.2026     | SQL Kerberos SPN         |

## Overeni funkcnosti

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
podman exec -e SQLCMDENCRYPT=false bakadev-mssql \
  /usr/local/bin/sqlcmd -S localhost -U sa -P 'SqlServer.Dev2026' \
  -Q "SELECT TOP 5 INTERN_KOD, PRIJMENI, JMENO FROM bakalari.dbo.zaci"
```

### Mailpit (web UI)

http://localhost:8025

## OU struktura v AD

```
DC=skola,DC=local
  OU=Skola
    OU=Uzivatele
      OU=Zaci
        OU=Rocnik-1 ... OU=Rocnik-9
          OU=Trida-A ... OU=Trida-E
      OU=StudiumUkonceno
      OU=Zamestnanci
        OU=Ucitele
        OU=Vedeni
      OU=Kontakty
    OU=Skupiny
      OU=Zaci          (bezpecnostni skupiny trida)
      OU=Distribucni   (distribucni listy)
      OU=Uzivatele     (globalni skupiny)
```

## Testovaci data

Seed skripty v `seed/` vytvarej realisticka testovaci data:
- Zaci ve tridach 1.A az 9.B
- Ucitele s tridnictvim
- Zakonni zastupci

Data se generuji z `seed/testdata.py` a nahraji do SQL i AD.

## Dulezite poznamky

- Samba4 vyzaduje `ldap server require strong auth = No` v smb.conf
  (plain LDAP bind). Toto je nastaveno v kontejneru automaticky.
- `samba-tool user setpassword` je nutny po `create` (jinak NTLM hash
  neni platny a LDAP bind selhava).
- DNS port je presmerovan na 15353 (macOS mDNSResponder obsazuje 53).
- MSSQL pouziva Azure SQL Edge (ARM64 nativni, T-SQL kompatibilni).
  Klasicky SQL Server 2022 nema ARM64 build a pada na Apple Silicon.
- MSSQL data jsou v pojmenovanem Podman svazku (ne bind-mount),
  protoze sqlservr (UID 10001) potrzebuje zapisova prava a virtiofs
  bind-mount z macOS toto nesplnuje.

## Soubory

```
dev/
  .env                  -- promenne prostredi (domeny, hesla)
  .data/                -- perzistentni data kontejneru
    samba/              -- AD data (bind-mount)
    keytabs/            -- Kerberos keytaby (bind-mount)
  krb5.conf.dev         -- Kerberos konfigurace pro hostitele
  podman-compose.yml    -- definice sluzeb
  setup-dev.sh          -- inicializace prostredi
  teardown-dev.sh       -- zastaveni/uklid prostredi
  samba4/               -- Containerfile a skripty pro Samba4
  mssql/                -- Containerfile, init-db.sql, mssql.conf
  seed/                 -- testovaci data
    testdata.py         -- generator testovacich dat
    run-seed.sh         -- spousteni seedu
    data/               -- vygenerovane SQL/LDIF soubory
```
