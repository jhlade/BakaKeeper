# BakaKeeper
Synchronizační nástroj evidence žáků v programu Bakaláři
s uživatelskými účty vedenými v Active Directory. Předpokládá
se celoškolní použití Office365, ale není to nezbytně nutné.

### Vlastnosti
* Automatická údržba účtů žáků v Active Directory podle dat
získaných v evidenci Bakalářů (jména, zařazení do ročníku/třídy
v OU a skupinách včetně povýšení školního roku, tvorba nového
přihlašovacího jména, e-mailu a počátečního hesla, vyřazení
účtů po ukončení vzdělávání). Jednovláknově, atomicky, transakčně.
* ~~Automatická definice přístupů k webové aplikaci pro žáky
a jejich zákonné zástupce (metoda vyžádání nového hesla
na základě ověřené e-mailové adresy).~~
* Tvorba anonymizovaných distribučních skupin s kontakty
na zákonné zástupce žáků třídy/ročníku/stupně/školy.
* Navrženo pro periodický běh pomocí UNIXového cronu.

### Prerekvizity
* Server Active Directory s rozšířenými atributy
Microsoft Exchange (správce s oprávněním skupiny Schema
Admins je snadno nainstaluje na AD z balíčku pro Exchange Server).
* Microsoft SQL Server (nebo MS SQL kompatibilní server)
s daty aplikace Bakaláři s doménovým ověřováním uživatele.
* Dedikovaný neinteraktivní účet s přístupem k SMTP,
právy Domain Admin v AD, právy ke čtení i zápisu v SQL databázi
s Bakaláři.
* JVM kompatibilní s Java 8 se síťovým přístupem k
serverům AD a SQL.
* Nepovinně - `cron` pro periodické spouštění.
* Nepovinně - pro ověřování přístupu k SQL Serveru pomocí
protokolu Kerberos namísto integrovaného NTLM musí být
manuálně delegována oprávnění výše zmíněnému účtu.

### Použití
1) Rychlá inicializace - vytvoření persistentního nastavení:<br>
`% java -jar BakaKeeper.jar --init --interactive -passphrase He$lo*321`<br>
spustí interaktivní dotazník s nastavením parametrů, které budou
uloženy ve výchozím souboru `settings.dat`.<br>Přepínač `-passphrase {heslo}`
není povinný, nicméně provede zašifrování vložených nastavení.
V případě jeho absence bude vytvořen soubor `settings.conf`
s čitelnými údaji. Idea za šifrováním nastavení je taková,
že nástroj spolu s nastavením může být uložen na sdíleném
prostředku a není žádoucí, aby údaje použitého správcovského
účtu byly veřejně dostupné. Nástroj pak může být spouštěn
z důveryhodného stroje automaticky pomocí plánovače
(např. `cron`).<br>
V rámci inicializace bude také získán důvěryhodný certifikát
AD serveru a zapsán v úložišti `ssl.jks`.

2) Kontrola synchronziace:<br>
`% java -jar BakaKeeper.jar --checksync [-passphrase He$lo*321]`<br>
zkontroluje současný stav a vytvoří hlášení.

3) Provedení synchronziace:<br>
`% java -jar BakaKeeper.jar --sync [-passphrase He$lo*321]`<br>
podle vytvořených nastavení provede synchronizační operace
a případně zašle hlášení e-mailem.

4) Více viz<br>
`% java -jar BakaKeeper.jar --help`

### Závislosti použité pro sestavení
(ve výchozím stavu jsou vyžadovány v externím adresáři `./lib/`)
* `javax.mail` >= 1.6.2
* `mssql-jdbc` >= 8.2.0
* `jtds` >= 1.3.1
* (`junit` >= 4.12)

2019-2020 [ZŠ Pardubice - Studánka](https://www.zs-studanka.cz/)
