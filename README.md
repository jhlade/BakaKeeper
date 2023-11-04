# 🕸 BakaKeeper

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
* Navrženo pro neinteraktivní periodický běh.

### Co je výhledově v plánu
 
* Sjednocený generátor sestav.
* Možnost specifikace jiných výchozích tvarů hesel a režimu aktivace účtů.
* Dříve odloženo z legislativních důvodů: Automatická definice
přístupů k webové aplikaci pro žáky a jejich zákonné zástupce (metoda
vyžádání nového hesla na základě ověřené e-mailové adresy).
* Grafické uživatelské rozhraní.

### Prerekvizity
 
* LDAP server (Microsoft Active Directory) s rozšířenými
  atributy Microsoft Exchange (správce s oprávněním skupiny Schema
Admins je snadno doinstaluje na řadič AD z balíčku pro Exchange Server).
* Microsoft SQL Server (nebo MS SQL kompatibilní server)
s daty aplikace Bakaláři s doménovým ověřováním uživatele (NTLM nebo Kerberos).
* Dedikovaný neinteraktivní doménový účet s přístupem k SMTP,
právy minimálně Account Operator v AD nad žáky, právy ke čtení i zápisu
v SQL databázi s Bakaláři.
* JVM kompatibilní s Java 18 se síťovým přístupem k
serverům AD a SQL.
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

### Použití
 
1) Rychlá inicializace – vytvoření persistentního nastavení:<br>
`% java -jar BakaKeeper.jar --init --interactive [-passphrase <heslo>]`<br>
spustí interaktivní dotazník s nastavením parametrů, které budou
uloženy ve výchozím souboru `./settings.dat`.<br>Přepínač `[-passphrase <heslo>]`
není povinný, nicméně provede zašifrování vložených nastavení.
Idea za šifrováním nastavení je taková,
že nástroj spolu s&nbsp;nastavením může být uložen na sdíleném
prostředku a není žádoucí, aby údaje použitého správcovského
účtu byly veřejně dostupné. Nástroj pak může být spouštěn
z&nbsp;důveryhodného stroje automaticky pomocí plánovače
(např. `cron`).<br>
V&nbsp;rámci inicializace bude také získán certifikát pro
připojení AD serveru a zapsán v úložišti `./ssl.jks`.

2) Kontrola synchronizace:<br>
`% java -jar BakaKeeper.jar --status [-passphrase <heslo>]`<br>
zkontroluje současný stav a vytvoří hlášení.

3) Provedení synchronizace:<br>
`% java -jar BakaKeeper.jar --sync [-passphrase <heslo>]`<br>
podle vytvořených nastavení provede synchronizační operace
a případně zašle hlášení e-mailem.

4) Identifikace účtu:<br>
`% java -jar BakaKeeper.jar -id <login> [-passphrase <heslo>]`<br>
vypíše informace o uživateli s přihlašovacím jménem `<login>` bez domény

5) Reset hesla žáka:<br>
`% java -jar BakaKeeper.jar -reset <login> [-passphrase <heslo>]`<br>
provede nastavení hesla žáka s přihlašovacím jménem `<login>` bez domény
na výchozí hodnotu 

6) Více viz<br>
`% java -jar BakaKeeper.jar --help`

### Závislosti pro rychlé sestavení a&nbsp;spuštění

*ve výchozím stavu jsou vyžadovány v&nbsp;externím
adresáři `./lib/`, ale je možné odkomentovat assembly
plugin Mavenu  a&nbsp;sestavit archiv i&nbsp;se závislostmi,
nebo rovnou použít shade plugin*
* `com.sun.mail.javax.mail` >= 1.6.2
* `com.microsoft.sqlserver.mssql-jdbc` >= 8.2.0
* `net.sourceforge.jtds.jtds` >= 1.3.1
* `net.tirasa.adsddl` >= 1.9 (+`slf4j-api`, `activation`)
* (`junit` >= 4.13.1)

2019-2023 [ZŠ Pardubice - Studánka](https://www.zs-studanka.cz/)
