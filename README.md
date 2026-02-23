# BakaKeeper

Synchronizační nástroj evidence žáků v programu Bakaláři
s uživatelskými účty vedenými v Active Directory. Předpokládá
se celoškolní použití Office365, ale není to nezbytně nutné.
Nástroj byl původně vytvořen během pandemie Covid-19 a počítá se správou
žákovských hesel v předdefinovaném tvaru a automaticky aktivovanými účty.

### Vlastnosti

* Automatická údržba účtů žáků v Active Directory podle dat
získaných v evidenci Bakalářů (jména, zařazení do ročníku/třídy
v OU a skupinách včetně povýšení školního roku, tvorba nového
přihlašovacího jména, e-mailu a počátečního hesla, vyřazení
účtů po ukončení vzdělávání, správa účtu podle stanovených politik).
* Atomický, transakční postup.
* Tvorba distribučních skupin třídních učitelů.
* Tvorba anonymizovaných distribučních skupin s kontakty
na zákonné zástupce žáků třídy/ročníku/stupně/školy.
* Generování sestav pro třídní učitele.
* Navrženo pro neinteraktivní periodický běh.

### Co je výhledově v plánu

* Sjednocený generátor sestav (iTextPDF).
* Grafické uživatelské rozhraní (JavaFX), webová aplikace.
* YAML konfigurace s deklarativními pravidly pro AD atributy.
* Granulární workflows (jednotlivec, třída, ročník, stupeň, škola).
* Zálohování a obnova interních uživatelů v případě nežádoucího zásahu.
* Možnost specifikace jiných výchozích tvarů hesel a režimu aktivace účtů.

### Struktura projektu

Projekt je organizován jako multi-modulární Maven build:

```
bakakeeper-parent/          (rodičovský POM)
├── bakakeeper-core/        (jádro – model, služby, konektory)
└── bakakeeper-cli/         (příkazový řádek)
```

### Prerekvizity

* LDAP server (Microsoft Active Directory) s rozšířenými
  atributy Microsoft Exchange (správce s oprávněním skupiny Schema
  Admins je snadno doinstaluje na řadič AD z balíčku pro Exchange Server).
* Microsoft SQL Server (nebo MS SQL kompatibilní server)
  s daty aplikace Bakaláři s doménovým ověřováním uživatele (NTLM nebo Kerberos).
* Dedikovaný neinteraktivní doménový účet s přístupem k SMTP,
  právy minimálně Account Operator v AD nad žáky, právy ke čtení i zápisu
  v SQL databázi s Bakaláři.
* JVM kompatibilní s Java 25 se síťovým přístupem k serverům AD a SQL.
* *Nepovinně* – v případě použití O365 je možné nastavit poštovní
  filtrovací pravidlo na základě hodnoty `CustomAttribute2:TRUE`
  a odesílatele v doméně mimo organizaci. Na AD se lokálně ukládá
  do atributu `ExtensionAttribute2` a má význam podobný jako
  `msExchRequireAuthToSendTo`.
* *Nepovinně* – `cron` nebo podobný plánovač pro periodické spouštění.
* *Nepovinně* – pro ověřování přístupu k SQL Serveru pomocí
  protokolu Kerberos namísto integrovaného NTLM musí být
  manuálně delegována oprávnění výše
  zmíněnému účtu (`setspn -s MSSQLSvc/sql-server.domena.local domena\bakalari`).

### Sestavení

```bash
./mvnw clean compile test        # kompilace a testy
./mvnw package                   # sestavení JAR (fat jar v bakakeeper-cli/target/)
```

### Použití

1) Rychlá inicializace – vytvoření persistentního nastavení:
   `java -jar BakaKeeper-2.0-SNAPSHOT.jar --init --interactive [-passphrase <heslo>]`

2) Kontrola synchronizace:
   `java -jar BakaKeeper-2.0-SNAPSHOT.jar --status [-passphrase <heslo>]`

3) Provedení synchronizace:
   `java -jar BakaKeeper-2.0-SNAPSHOT.jar --sync [-passphrase <heslo>]`

4) Identifikace účtu:
   `java -jar BakaKeeper-2.0-SNAPSHOT.jar -id <login> [-passphrase <heslo>]`

5) Reset hesla žáka:
   `java -jar BakaKeeper-2.0-SNAPSHOT.jar -reset <login> [-passphrase <heslo>]`

6) Více viz `java -jar BakaKeeper-2.0-SNAPSHOT.jar --help`

### Závislosti

Spravovány přes Maven (`bakakeeper-core/pom.xml`):
* `com.sun.mail:javax.mail` 1.6.2
* `com.microsoft.sqlserver:mssql-jdbc` 10.2.4
* `net.sourceforge.jtds:jtds` 1.3.1
* `net.tirasa:adsddl` 1.9
* `org.projectlombok:lombok` 1.18.42
* `org.yaml:snakeyaml` 2.3
* `org.junit.jupiter:junit-jupiter` 5.11.4 (testy)
* `org.mockito:mockito-core` 5.14.2 (testy)

### Vývojové prostředí

Podman-based dev environment se Samba4 AD, MSSQL a Mailpit:
```bash
cd dev && ./setup-dev.sh
```

Viz `dev/README.md` pro detaily.

---
2019-2026 [ZS Pardubice - Studanka](https://www.zs-studanka.cz/)
