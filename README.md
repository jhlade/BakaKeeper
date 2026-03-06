# BakaKeeper

Synchronizační nástroj evidence žáků v programu Bakaláři
s uživatelskými účty vedenými v Active Directory. Předpokládá
se celoškolní použití Office365, ale není to nezbytně nutné.
Nástroj byl původně vytvořen během pandemie Covid-19
při prvotním nasazování Office365 a počítá se správou
žákovských hesel v předdefinovaném tvaru
a automaticky aktivovanými účty.

### Vlastnosti

* Automatická údržba účtů žáků v Active Directory podle dat
získaných v evidenci Bakalářů (jména, zařazení do ročníku/třídy
v OU a skupinách včetně povýšení školního roku, tvorba nového
přihlašovacího jména, e-mailu a počátečního hesla, vyřazení
účtů po ukončení vzdělávání, správa účtu podle stanovených politik).
* Tvorba distribučních skupin třídních učitelů.
* Tvorba anonymizovaných distribučních skupin s kontakty
na zákonné zástupce žáků třídy/ročníku/stupně/školy.
* Generování sestav pro třídní učitele.
* Grafické uživatelské rozhraní (JavaFX) pro interaktivní správu.
* Navrženo pro neinteraktivní periodický běh i interaktivní provoz.

### Co je výhledově v plánu

* Možnost specifikace jiných výchozích tvarů hesel a režimu aktivace účtů.

### Struktura projektu

📚 Projekt je organizován jako multi-modulární Maven build:

```
bakakeeper-parent/          (rodičovský POM)
├── bakakeeper-core/        (jádro – model, služby, konektory)
├── bakakeeper-cli/         (příkazový řádek)
└── bakakeeper-gui/         (grafické rozhraní – JavaFX)
```

### Prerekvizity

* LDAP server (on-premise řadič Microsoft Active Directory) s rozšířenými
  atributy Microsoft Exchange (správce s oprávněním skupiny Schema
  Admins je snadno doinstaluje na řadič AD z balíčku pro Exchange Server, nebo pomocí `.ldif` schématu, viz [dev/samba4](dev/samba4/)).
* Microsoft SQL Server (nebo MS SQL kompatibilní server)
  s daty aplikace Bakaláři s doménovým ověřováním uživatele (NTLM nebo Kerberos),
* nebo účtem správce `sa` (případně aplikačním účtem s právy čtení a zápisu
  do databáze používané aplikací Bakaláři).
* Dedikovaný neinteraktivní doménový účet s přístupem k SMTP,
  právy minimálně Account Operator v AD nad žáky (příapdně učiteli).
* JVM kompatibilní s Java 25 se síťovým přístupem k serverům AD a SQL.
* Pro GUI – JRE s podporou JavaFX 25 (např. Azul Zulu FX).
* *Nepovinně* – v případě použití O365 je možné nastavit poštovní
  filtrovací pravidlo na základě hodnoty `CustomAttribute2:TRUE`
  a odesílatele v doméně mimo organizaci. Na AD se lokálně ukládá
  do atributu `ExtensionAttribute2` a má význam podobný jako
  `msExchRequireAuthToSendTo`.
* *Nepovinně* – `cron` nebo podobný plánovač pro periodické spouštění.
* *Nepovinně* – pro ověřování přístupu k SQL Serveru pomocí
  protokolu Kerberos namísto integrovaného NTLMv2 musí být
  manuálně delegována oprávnění výše
  zmíněnému účtu (`setspn -s MSSQLSvc/sql-server.domena.local domena\bakalari`).

### Sestavení

```bash
./mvnw clean compile test        # kompilace a testy
./mvnw package                   # sestavení JAR
                                 #   CLI: bakakeeper-cli/target/
                                 #   GUI: bakakeeper-gui/target/
```

### Použití

**Příkazový řádek:**

```
bakakeeper check -p heslo            Kontrola konektivity
bakakeeper sync --verbose            Synchronizace s podrobným výstupem
bakakeeper report 5.A                Odeslání sestavy s výchozími přihlašovacími údaji
bakakeeper reset 5.A --report        Reset hesel a odeslání sestavy pro třídu 5.A
bakakeeper reset *                   Reset hesel všech žáků celé školy
bakakeeper suspend 5.A               Zakázání všech účtů třídy 5.A
bakakeeper unsuspend 5.A             Povolení účtů třídy 5.A
bakakeeper export 5 -o seznam.csv    CSV export celého 5. ročníku
bakakeeper init -f settings.yml      Inicializace z plain-text konfiguračního souboru
```

Více viz `bakakeeper --help`.

**Grafické rozhraní:**

```
java -jar bakakeeper-gui.jar
```

![Grafické rozhraní](https://github.com/jhlade/BakaKeeper/blob/assets/assets/bakacli-26-3.png?raw=true)

### Závislosti pro sestavení

🪶 Spravovány přes Maven (`bakakeeper-core/pom.xml`, `bakakeeper-cli/pom.xml`, `bakakeeper-gui/pom.xml`):
* `com.sun.mail:javax.mail` 1.6.2
* `com.microsoft.sqlserver:mssql-jdbc` 13.2.1.jre11
* `net.sourceforge.jtds:jtds` 1.3.1
* `net.tirasa:adsddl` 1.9
* `org.projectlombok:lombok` 1.18.42
* `org.yaml:snakeyaml` 2.3
* `com.itextpdf:layout` 8.0.5 (PDF sestavy)
* `info.picocli:picocli` 4.7.6 (CLI)
* `org.openjfx:javafx-controls` 25 (GUI)

### Vývojové prostředí

🦭 Podman-based dev environment se
- Samba4 AD (simulace doménového řadiče),
- phpLDAPAdmin (nahlížení do simulovaného řadiče),
- MSSQL (Edge varianta Microsoft SQL Server),
- Mailpit (simulace SMTP serveru s webovým rozhraním pro testování mailů)
```bash
cd dev && ./setup-dev.sh
```

Viz [`dev/README.md`](dev/README.md) pro detaily.

---
2019-2026 [ZŠ Pardubice – Studánka](https://www.zs-studanka.cz/)
