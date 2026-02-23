# =============================================================================
# BakaKeeper – dotaz na Exchange schema atributy v produkčním AD
#
# Spusťte na doménovém řadiči (nebo stanici s RSAT-AD-Tools):
#   powershell -ExecutionPolicy Bypass -File query-exchange-schema.ps1
#
# Výstup: tabulka s OID, syntaxí a dalšími vlastnostmi atributů,
#         které BakaKeeper používá a které potřebujeme přidat do Samba4 dev.
# =============================================================================

Import-Module ActiveDirectory -ErrorAction Stop

$schemaDN = (Get-ADRootDSE).schemaNamingContext
Write-Host "Schema DN: $schemaDN" -ForegroundColor Cyan
Write-Host ""

# Seznam atributů, které BakaKeeper používá
$attributes = @(
    "extensionAttribute1",
    "extensionAttribute2",
    "extensionAttribute3",
    "extensionAttribute4",
    "extensionAttribute5",
    "extensionAttribute6",
    "extensionAttribute7",
    "extensionAttribute8",
    "extensionAttribute9",
    "extensionAttribute10",
    "extensionAttribute11",
    "extensionAttribute12",
    "extensionAttribute13",
    "extensionAttribute14",
    "extensionAttribute15",
    "proxyAddresses",
    "targetAddress",
    "msExchHideFromAddressLists",
    "msExchRequireAuthToSendTo"
)

$results = @()

foreach ($attr in $attributes) {
    $obj = Get-ADObject -SearchBase $schemaDN `
        -Filter { lDAPDisplayName -eq $attr } `
        -Properties cn, lDAPDisplayName, attributeID, attributeSyntax, `
                    oMSyntax, isSingleValued, searchFlags, `
                    rangeLower, rangeUpper, isMemberOfPartialAttributeSet `
        -ErrorAction SilentlyContinue

    if ($obj) {
        $results += [PSCustomObject]@{
            lDAPDisplayName    = $obj.lDAPDisplayName
            cn                 = $obj.cn
            attributeID        = $obj.attributeID
            attributeSyntax    = $obj.attributeSyntax
            oMSyntax           = $obj.oMSyntax
            isSingleValued     = $obj.isSingleValued
            searchFlags        = $obj.searchFlags
            rangeLower         = $obj.rangeLower
            rangeUpper         = $obj.rangeUpper
            partialAttributeSet = $obj.isMemberOfPartialAttributeSet
        }
    } else {
        Write-Warning "Atribut '$attr' nenalezen ve schematu!"
    }
}

# Výstup jako tabulka
$results | Format-Table -AutoSize lDAPDisplayName, cn, attributeID, `
    attributeSyntax, oMSyntax, isSingleValued, searchFlags

# Výstup jako CSV pro snadné zpracování
$csvPath = Join-Path $PSScriptRoot "exchange-schema-oids.csv"
$results | Export-Csv -Path $csvPath -NoTypeInformation -Encoding UTF8
Write-Host ""
Write-Host "CSV exportován do: $csvPath" -ForegroundColor Green
Write-Host ""

# Výstup jako LDIF komentáře (pro snadné kopírování do exchange-schema.ldif)
Write-Host "# ---- LDIF reference ----" -ForegroundColor Yellow
foreach ($r in $results) {
    Write-Host ("# {0}: attributeID={1}  syntax={2}  oMSyntax={3}  singleValued={4}" -f `
        $r.lDAPDisplayName, $r.attributeID, $r.attributeSyntax, $r.oMSyntax, $r.isSingleValued)
}
