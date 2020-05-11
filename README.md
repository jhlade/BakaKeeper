# BakaKeeper
Synchronizační nástroj evidence žáků v programu Bakaláři
s uživatelskými účty vedenými v Active Directory. Předpokládá
se použití Office365, ale není nutné.

### Prerekvizity
* Server Active Directory s rozšířenými atributy
Microsoft Exchange (správce s oprávněním skupiny Schema
Admins je nainstaluje na AD z balíčku pro Exchange Server).
* Microsoft SQL Server s daty aplikace Bakaláři
s doménovým ověřováním uživatele.
* Dedikovaný neinteraktivní účet s přístupem k SMTP,
právy Domain Admin v AD, právy ke čtení i zápisu v SQL databázi.

### Závislosti použité pro sestavení
* javax.mail >= 1.6.2
* mssql-jdbc >= 8.2.0
* jtds >= 1.3.1
* (junit >= 4.12)

2019-2020 [ZŠ Pardubice - Studánka](https://www.zs-studanka.cz/)
