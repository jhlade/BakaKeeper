-- =============================================================================
-- BakaKeeper – inicializace vývojové databáze Bakalářů
--
-- Vytvoří databázi, tabulky odpovídající EBakaSQL konstantám
-- a testovací data pro vývoj a ladění synchronizace.
--
-- Spouští setup-dev.sh přes sqlcmd po startu MSSQL kontejneru.
-- =============================================================================

USE master;
GO

-- Vytvoření databáze (pokud ještě neexistuje)
IF NOT EXISTS (SELECT name FROM sys.databases WHERE name = N'bakalari')
BEGIN
    CREATE DATABASE bakalari;
END
GO

USE bakalari;
GO

-- =============================================================================
-- Tabulky – schéma odpovídá konstantám EBakaSQL.java
-- =============================================================================

-- Třídy (EBakaSQL.TBL_CLASS = dbo.tridy)
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[tridy]'))
BEGIN
    CREATE TABLE dbo.tridy (
        -- EBakaSQL.F_CLASS_YEAR (ROCNIK) – ročník (1–9)
        ROCNIK          INT             NOT NULL,
        -- EBakaSQL.F_CLASS_LABEL (ZKRATKA) – označení třídy ve tvaru "1.A"
        ZKRATKA         NVARCHAR(10)    NOT NULL,
        -- EBakaSQL.F_CLASS_TEACHER (TRIDNICTVI) – FK na INTERN_KOD učitele
        TRIDNICTVI      NVARCHAR(20)    NULL,
        PRIMARY KEY (ZKRATKA)
    );
END
GO

-- Žáci (EBakaSQL.TBL_STU = dbo.zaci)
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[zaci]'))
BEGIN
    CREATE TABLE dbo.zaci (
        -- EBakaSQL.F_STU_ID (INTERN_KOD) – primární klíč
        INTERN_KOD      NVARCHAR(20)    NOT NULL,
        -- EBakaSQL.F_STU_CLASS_ID (C_TR_VYK) – číslo žáka v třídním výkazu
        C_TR_VYK        INT             NULL,
        -- EBakaSQL.F_STU_SURNAME (PRIJMENI)
        PRIJMENI        NVARCHAR(50)    NULL,
        -- EBakaSQL.F_STU_GIVENNAME (JMENO)
        JMENO           NVARCHAR(50)    NULL,
        -- EBakaSQL.F_STU_CLASS (TRIDA) – třída ve tvaru "1.A"
        TRIDA           NVARCHAR(10)    NULL,
        -- EBakaSQL.F_STU_MAIL (E_MAIL) – interní e-mail žáka
        E_MAIL          NVARCHAR(100)   NULL,
        -- EBakaSQL.F_STU_EXPIRED (EVID_DO) – konec platnosti evidence
        EVID_DO         DATE            NULL,
        PRIMARY KEY (INTERN_KOD)
    );
END
GO

-- Učitelé (EBakaSQL.TBL_FAC = dbo.ucitele)
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[ucitele]'))
BEGIN
    CREATE TABLE dbo.ucitele (
        -- EBakaSQL.F_FAC_ID (INTERN_KOD) – primární klíč
        INTERN_KOD      NVARCHAR(20)    NOT NULL,
        -- EBakaSQL.F_FAC_GIVENNAME (JMENO)
        JMENO           NVARCHAR(50)    NULL,
        -- EBakaSQL.F_FAC_SURNAME (PRIJMENI)
        PRIJMENI        NVARCHAR(50)    NULL,
        -- EBakaSQL.F_FAC_EMAIL (E_MAIL)
        E_MAIL          NVARCHAR(100)   NULL,
        -- EBakaSQL.F_FAC_ACTIVE (UCI_LETOS) – příznak aktivního v aktuálním roce (1/0)
        UCI_LETOS       BIT             NOT NULL DEFAULT 1,
        PRIMARY KEY (INTERN_KOD)
    );
END
GO

-- Zákonní zástupci (EBakaSQL.TBL_GUA = dbo.zaci_zzd)
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[zaci_zzd]'))
BEGIN
    CREATE TABLE dbo.zaci_zzd (
        -- EBakaSQL.F_GUA_ID (ID) – primární klíč zákonného zástupce
        ID              NVARCHAR(20)    NOT NULL,
        -- EBakaSQL.F_GUA_GIVENNAME (JMENO)
        JMENO           NVARCHAR(50)    NULL,
        -- EBakaSQL.F_GUA_SURNAME (PRIJMENI)
        PRIJMENI        NVARCHAR(50)    NULL,
        -- EBakaSQL.F_GUA_MOBILE (TEL_MOBIL)
        TEL_MOBIL       NVARCHAR(30)    NULL,
        -- EBakaSQL.F_GUA_MAIL (E_MAIL)
        E_MAIL          NVARCHAR(100)   NULL,
        PRIMARY KEY (ID)
    );
END
GO

-- Vazby žák–zákonný zástupce (EBakaSQL.TBL_STU_GUA = dbo.zaci_zzr)
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[zaci_zzr]'))
BEGIN
    CREATE TABLE dbo.zaci_zzr (
        -- EBakaSQL.F_GS_STUID (INTERN_KOD) – ID žáka
        INTERN_KOD      NVARCHAR(20)    NOT NULL,
        -- EBakaSQL.F_GS_GUAID (ID_ZZ) – ID zákonného zástupce
        ID_ZZ           NVARCHAR(20)    NOT NULL,
        -- EBakaSQL.FS_GS_IS_GUA (JE_ZZ) – příznak zákonného zástupce (1/0)
        JE_ZZ           BIT             NOT NULL DEFAULT 1,
        -- EBakaSQL.FS_GS_IS_PRI (PRIMARNI) – příznak primárního kontaktu (1/0)
        PRIMARNI        BIT             NOT NULL DEFAULT 0,
        PRIMARY KEY (INTERN_KOD, ID_ZZ)
    );
END
GO

-- Webové přístupy a interní uživatelé (EBakaSQL.TBL_WEB_LOGIN = EBakaSQL.TBL_LOGIN = dbo.webuser)
-- Tabulka slouží zároveň jako TBL_WEB_LOGIN (žáci/učitelé/rodiče) i TBL_LOGIN (správci).
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[webuser]'))
BEGIN
    CREATE TABLE dbo.webuser (
        -- EBakaSQL.F_WEB_USRID / F_LOGIN_ID (INTERN_KOD) – ID uživatele
        INTERN_KOD      NVARCHAR(20)    NOT NULL,
        -- EBakaSQL.F_WEB_LOGIN / F_LOGIN_LOGIN (LOGIN) – přihlašovací jméno
        -- Správce systému má LOGIN = '*' (LIT_LOGIN_ADMIN), dotazuje se jako 'ADMIN'
        LOGIN           NVARCHAR(50)    NULL,
        -- EBakaSQL.F_WEB_TYPE / F_LOGIN_TYPE (KOD1) – typ: Z=žák, V=vyučující, R=rodič, S=správce
        KOD1            NVARCHAR(10)    NULL,
        -- EBakaSQL.F_LOGIN_ACL (PRAVA) – uživatelská oprávnění
        PRAVA           NVARCHAR(10)    NULL,
        -- EBakaSQL.F_LOGIN_UPDTYPE (UPD_TYP) – typ aktualizace (lit. 'H' = LIT_LOGIN_UPD)
        UPD_TYP         NVARCHAR(10)    NULL,
        -- EBakaSQL.F_LOGIN_KODF (KODF) – interní kód (TODO: dokumentace chybí)
        KODF            NVARCHAR(50)    NULL,
        -- EBakaSQL.F_LOGIN_PWD (HESLO) – B64 hash hesla uživatele
        HESLO           NVARCHAR(200)   NULL,
        -- EBakaSQL.F_LOGIN_PWD_MET (METODA) – hashovací metoda
        METODA          NVARCHAR(20)    NULL,
        -- EBakaSQL.F_LOGIN_PWD_SALT (SALT) – sůl hashe
        SALT            NVARCHAR(100)   NULL,
        -- EBakaSQL.F_WEB_MODIFIED / F_LOGIN_MODIFIED (MODIFIED) – datum poslední modifikace
        MODIFIED        DATETIME        NULL,
        -- EBakaSQL.F_WEB_MODIFIEDBY / F_LOGIN_MODIFIEDBY (MODIFIEDBY) – odpovědnost za modifikaci
        MODIFIEDBY      NVARCHAR(50)    NULL,
        PRIMARY KEY (INTERN_KOD)
    );
END
GO

PRINT 'Databáze bakalari inicializována – tabulky připraveny pro seed generator.';
PRINT 'Testovací data se vkládají separátně přes dev/seed/run-seed.sh.';
GO
