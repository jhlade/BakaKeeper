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

-- =============================================================================
-- Testovací data
-- (formát TRIDA: "1.A" – odpovídá EBakaSQL.F_STU_CLASS a F_CLASS_LABEL)
-- =============================================================================

-- Třídy
INSERT INTO dbo.tridy (ROCNIK, ZKRATKA, TRIDNICTVI)
SELECT 1, '1.A', 'UCT001' WHERE NOT EXISTS (SELECT 1 FROM dbo.tridy WHERE ZKRATKA = '1.A');

INSERT INTO dbo.tridy (ROCNIK, ZKRATKA, TRIDNICTVI)
SELECT 5, '5.B', 'UCT002' WHERE NOT EXISTS (SELECT 1 FROM dbo.tridy WHERE ZKRATKA = '5.B');

INSERT INTO dbo.tridy (ROCNIK, ZKRATKA, TRIDNICTVI)
SELECT 9, '9.A', 'UCT001' WHERE NOT EXISTS (SELECT 1 FROM dbo.tridy WHERE ZKRATKA = '9.A');
GO

-- Učitelé
INSERT INTO dbo.ucitele (INTERN_KOD, JMENO, PRIJMENI, E_MAIL, UCI_LETOS)
SELECT 'UCT001', 'Marie', 'Ředitelová', 'marie.redittelova@zsstu.local', 1
WHERE NOT EXISTS (SELECT 1 FROM dbo.ucitele WHERE INTERN_KOD = 'UCT001');

INSERT INTO dbo.ucitele (INTERN_KOD, JMENO, PRIJMENI, E_MAIL, UCI_LETOS)
SELECT 'UCT002', 'Pavel', 'Učitelský', 'pavel.ucitelsky@zsstu.local', 1
WHERE NOT EXISTS (SELECT 1 FROM dbo.ucitele WHERE INTERN_KOD = 'UCT002');
GO

-- Žáci
-- 9.A – starší žáci (ročník 9, mají externí mail dle nastavení ext_mail)
INSERT INTO dbo.zaci (INTERN_KOD, C_TR_VYK, PRIJMENI, JMENO, TRIDA, E_MAIL, EVID_DO)
SELECT 'ZAK001', 1, 'Novák', 'Jan', '9.A', 'jan.novak@zsstu.local', NULL
WHERE NOT EXISTS (SELECT 1 FROM dbo.zaci WHERE INTERN_KOD = 'ZAK001');

INSERT INTO dbo.zaci (INTERN_KOD, C_TR_VYK, PRIJMENI, JMENO, TRIDA, E_MAIL, EVID_DO)
SELECT 'ZAK002', 2, 'Svobodová', 'Jana', '9.A', 'jana.svobodova@zsstu.local', NULL
WHERE NOT EXISTS (SELECT 1 FROM dbo.zaci WHERE INTERN_KOD = 'ZAK002');

-- 5.B – střední ročník
INSERT INTO dbo.zaci (INTERN_KOD, C_TR_VYK, PRIJMENI, JMENO, TRIDA, E_MAIL, EVID_DO)
SELECT 'ZAK003', 1, 'Procházka', 'Tomáš', '5.B', 'tomas.prochazka@zsstu.local', NULL
WHERE NOT EXISTS (SELECT 1 FROM dbo.zaci WHERE INTERN_KOD = 'ZAK003');

-- 1.A – malí žáci (bez změny hesla, bez expirace dle pwd_nochange / pwd_noexpire)
INSERT INTO dbo.zaci (INTERN_KOD, C_TR_VYK, PRIJMENI, JMENO, TRIDA, E_MAIL, EVID_DO)
SELECT 'ZAK004', 1, 'Horáková', 'Lucie', '1.A', 'lucie.horakova@zsstu.local', NULL
WHERE NOT EXISTS (SELECT 1 FROM dbo.zaci WHERE INTERN_KOD = 'ZAK004');

-- Ukončená evidence – pro testování přesunu do StudiumUkonceno OU
INSERT INTO dbo.zaci (INTERN_KOD, C_TR_VYK, PRIJMENI, JMENO, TRIDA, E_MAIL, EVID_DO)
SELECT 'ZAK099', 99, 'Absolvent', 'Starý', '9.A', 'stary.absolvent@zsstu.local', '2024-08-31'
WHERE NOT EXISTS (SELECT 1 FROM dbo.zaci WHERE INTERN_KOD = 'ZAK099');
GO

-- Zákonní zástupci
INSERT INTO dbo.zaci_zzd (ID, JMENO, PRIJMENI, TEL_MOBIL, E_MAIL)
SELECT 'ZZD001', 'Petr', 'Novák', '+420601234567', 'petr.novak@example.com'
WHERE NOT EXISTS (SELECT 1 FROM dbo.zaci_zzd WHERE ID = 'ZZD001');

INSERT INTO dbo.zaci_zzd (ID, JMENO, PRIJMENI, TEL_MOBIL, E_MAIL)
SELECT 'ZZD002', 'Alena', 'Procházková', '+420602345678', 'alena.prochazkova@example.com'
WHERE NOT EXISTS (SELECT 1 FROM dbo.zaci_zzd WHERE ID = 'ZZD002');

INSERT INTO dbo.zaci_zzd (ID, JMENO, PRIJMENI, TEL_MOBIL, E_MAIL)
SELECT 'ZZD003', 'Eva', 'Horáková', '+420603456789', 'eva.horakova@example.com'
WHERE NOT EXISTS (SELECT 1 FROM dbo.zaci_zzd WHERE ID = 'ZZD003');
GO

-- Vazby žák–zákonný zástupce
-- JE_ZZ=1 → zákonný zástupce, PRIMARNI=1 → primární kontaktní osoba
INSERT INTO dbo.zaci_zzr (INTERN_KOD, ID_ZZ, JE_ZZ, PRIMARNI)
SELECT 'ZAK001', 'ZZD001', 1, 1 WHERE NOT EXISTS (SELECT 1 FROM dbo.zaci_zzr WHERE INTERN_KOD='ZAK001' AND ID_ZZ='ZZD001');

INSERT INTO dbo.zaci_zzr (INTERN_KOD, ID_ZZ, JE_ZZ, PRIMARNI)
SELECT 'ZAK003', 'ZZD002', 1, 1 WHERE NOT EXISTS (SELECT 1 FROM dbo.zaci_zzr WHERE INTERN_KOD='ZAK003' AND ID_ZZ='ZZD002');

INSERT INTO dbo.zaci_zzr (INTERN_KOD, ID_ZZ, JE_ZZ, PRIMARNI)
SELECT 'ZAK004', 'ZZD003', 1, 1 WHERE NOT EXISTS (SELECT 1 FROM dbo.zaci_zzr WHERE INTERN_KOD='ZAK004' AND ID_ZZ='ZZD003');
GO

PRINT 'Databáze bakalari úspěšně inicializována.';
PRINT 'Třídy: 1.A, 5.B, 9.A';
PRINT 'Žáci:  ZAK001-ZAK004 (aktivní), ZAK099 (ukončená evidence)';
PRINT 'Učitelé: UCT001, UCT002';
PRINT 'Zákonní zástupci: ZZD001-ZZD003';
GO
