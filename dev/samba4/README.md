# Samba4 AD DC – rozšíření schématu pro BakaKeeper

## Obsah

Soubor `exchange-schema.ldif` definuje LDAP atributy, které BakaKeeper
používá a které nejsou součástí výchozího AD schématu (bez Exchange):

- **extensionAttribute1–15** – rozšířené atributy (ID uživatele v Bakalářích,
  sync flagy, členství v Google Workspace OU, metadata, ...)
- **proxyAddresses** – seznam proxy-adres (SMTP, smtp, X400)
- **msExchHideFromAddressLists** – skrytí kontaktu v GAL
- **msExchRequireAuthToSendTo** – omezení příjmu pošty skupiny

V organizacích s nasazeným Exchange (on-premises nebo hybrid) jsou tyto
atributy již součástí schématu a import **není potřeba**.

## Import do Samba4 (vývojové prostředí)

Import probíhá automaticky při spuštění `setup-dev.sh` → `provision-users.sh`.
Manuálně:

```bash
# 1) Přidání atributů do schématu
ldapadd -H ldap://dc.skola.local \
    -D "CN=Administrator,CN=Users,DC=skola,DC=local" \
    -w "$ADMIN_PASSWORD" \
    -c -f exchange-schema.ldif

# 2) Reload schématu
ldapmodify -H ldap://dc.skola.local \
    -D "CN=Administrator,CN=Users,DC=skola,DC=local" \
    -w "$ADMIN_PASSWORD" <<EOF
dn:
changetype: modify
add: schemaUpdateNow
schemaUpdateNow: 1
EOF

# 3) Přidání atributů do tříd User, Contact a Group (mayContain)
#    → viz provision-users.sh pro kompletní příklad
```

## Import do Windows Server AD (produkce bez Exchange)

Stejný LDIF soubor lze importovat i do reálného Windows Server Active
Directory, pokud v organizaci není nasazen Exchange a atributy tedy chybí.

### Požadavky

- Členství ve skupině **Schema Admins**
- Schema Master FSMO role musí být dostupná
- Na doménovém řadiči musí být povoleny změny schématu:
  `reg add "HKLM\SYSTEM\CurrentControlSet\Services\NTDS\Parameters" /v "Schema Update Allowed" /t REG_DWORD /d 1 /f`

### Postup

1. **Upravte DN** v LDIF souboru – nahraďte `DC=skola,DC=local` za skutečné
   DN vaší domény (např. `DC=zsstu,DC=local`):

   ```powershell
   (Get-Content exchange-schema.ldif) -replace 'DC=skola,DC=local','DC=zsstu,DC=local' |
       Set-Content exchange-schema-prod.ldif
   ```

2. **Importujte atributy** pomocí `ldifde` (nativní Windows nástroj):

   ```cmd
   ldifde -i -f exchange-schema-prod.ldif -s dc.zsstu.local -c "DC=skola,DC=local" "DC=zsstu,DC=local"
   ```

   Alternativně přes `ldapadd` (pokud máte ldap-utils / OpenLDAP klienta):

   ```bash
   ldapadd -H ldap://dc.zsstu.local \
       -D "CN=Administrator,CN=Users,DC=zsstu,DC=local" \
       -W -c -f exchange-schema-prod.ldif
   ```

3. **Vynuťte reload schématu** (PowerShell na DC):

   ```powershell
   $rootDSE = [ADSI]"LDAP://RootDSE"
   $rootDSE.Put("schemaUpdateNow", 1)
   $rootDSE.SetInfo()
   ```

4. **Přidejte atributy do tříd** User, Contact a Group.
   Na Windows AD to lze provést přes AD Schema snap-in (`schmmgmt.dll`)
   nebo přes `ldapmodify` / PowerShell:

   ```powershell
   # Příklad: přidání extensionAttribute1 do třídy User
   $schema = [ADSI]"LDAP://CN=User,CN=Schema,CN=Configuration,DC=zsstu,DC=local"
   $schema.PutEx(3, "mayContain", @("extensionAttribute1","extensionAttribute2",...))
   $schema.SetInfo()
   ```

   Kompletní seznam atributů viz `provision-users.sh` (sekce `MAYCONTAIN_USER`,
   `MAYCONTAIN_CONTACT`, `MAYCONTAIN_GROUP`).

5. **Zakažte změny schématu** (volitelné, doporučené):

   ```cmd
   reg add "HKLM\SYSTEM\CurrentControlSet\Services\NTDS\Parameters" /v "Schema Update Allowed" /t REG_DWORD /d 0 /f
   ```

### Ověření

```powershell
# Kontrola, že atribut existuje ve schématu
Get-ADObject -SearchBase (Get-ADRootDSE).schemaNamingContext `
    -Filter {lDAPDisplayName -eq 'extensionAttribute1'}

# Kontrola, že atribut jde nastavit na uživateli
Set-ADUser testuser -Add @{extensionAttribute1="TEST123"}
Get-ADUser testuser -Properties extensionAttribute1
```

### Poznámky

- OID atributů odpovídají oficiálním Microsoft OID z MS-ADSC specifikace.
  Pokud bude v budoucnu nasazen Exchange, nedojde ke konfliktu – Exchange
  schema extension definuje totožné atributy se stejnými OID.
- `proxyAddresses` je ve Windows AD obvykle již k dispozici (součást
  base schématu přes třídu `mail-Recipient`). Pokud `ldifde` hlásí
  "Already exists", je to v pořádku.
- Změny schématu AD jsou **nevratné** – atribut nelze ze schématu odstranit,
  pouze deaktivovat (`isDefunct: TRUE`). V produkci proto doporučujeme
  nejprve otestovat v izolovaném prostředí.
