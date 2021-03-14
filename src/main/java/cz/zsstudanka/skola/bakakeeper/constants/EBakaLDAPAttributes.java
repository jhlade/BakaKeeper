package cz.zsstudanka.skola.bakakeeper.constants;

/**
 * Atributy LDAP objektu.
 *
 * @author Jan Hladěna
 */
public enum EBakaLDAPAttributes {

    BK_FLAG("baka_flag", null), // pomocný virtuální parametr příznaku
    BK_FLAG_TRUE("baka_flag", "1"),
    BK_FLAG_FALSE("baka_flag", "0"),

    BK_SYMBOL_ROOTDSE("RootDSE", null), // pomocný literál "RootDSE"

    BK_LITERAL_TRUE("boolean_literal", "TRUE"),
    BK_LITERAL_FALSE("boolean_literal", "FALSE"),

    CN ("cn", null), // common name, hlavní název objektu
    NAME_LAST("sn", null), // příjmení
    NAME_FIRST("givenName", null), // křestní jméno
    LOGIN ("sAMAccountName", null), // přihlašovací jméno pro pre-WIN2000 (<=20 znaků)
    UPN ("userPrincipalName", null), // přihlašovací jméno s primární doménou
    UID ("uid", null), // unikátní UID
    UAC ("userAccountControl", null), // řídící atributy účtu
    MEMBER_OF ("memberOf", null), // seznam skupin, kterých je objekt členem
    MEMBER ("member", null), // člen skupiny

    SRV_VENDOR("vendorName", null),
    SRV_VERSION("vendorVersion", null),
    SRV_AD_CATALOG_READY("isGlobalCatalogReady", BK_LITERAL_TRUE.value),
    SRV_AD_DOMAIN_LEVEL("domainFunctionality", "7"), // MS AD 2016 = 7
    SRV_AD_FOREST_LEVEL("forestFunctionality", "7"), // MS AD 2016 = 7

    MAIL ("mail", null), // primární e-mail
    PROXY_ADDR("proxyAddresses", null), // seznam proxy-adres
    TARGET_ADDR("targetAddress", null), // cílová e-mailová adresa
    MOBILE ("mobile", null), // mobilní telefon
    NAME_DISPLAY("displayName", null), // zobrazované jméno
    DESCRIPTION ("description", null), // popisek
    TITLE ("title", null), // pracovní pozice - zobrazuje se v O365

    PW_UNICODE ("unicodePwd", null), // Active Directory heslo
    PW_LASTSET ("pwdLastSet", "-1"), // žádný příznak
    PW_REQCHANGE ("pwdLastSet", "0"), // uživatel si musí změnit heslo

    MSXCH_GAL_HIDDEN("msExchHideFromAddressLists", "TRUE"), // skrytí kontaktu v GAL
    MSXCH_REQ_AUTH ("msExchRequireAuthToSendTo", "TRUE"), // maily pro skupiny pouze z domény

    EXT01 ("extensionAttribute1", null), // rozšířený atribut 1
    EXT02 ("extensionAttribute2", null), // rozšířený atribut 2
    EXT03 ("extensionAttribute3", null), // rozšířený atribut 3
    EXT04 ("extensionAttribute4", null), // rozšířený atribut 4
    EXT05 ("extensionAttribute5", null), // rozšířený atribut 5
    EXT06 ("extensionAttribute6", null), // rozšířený atribut 6
    EXT07 ("extensionAttribute7", null), // rozšířený atribut 7
    EXT08 ("extensionAttribute8", null), // rozšířený atribut 8
    EXT09 ("extensionAttribute9", null), // rozšířený atribut 9
    EXT10 ("extensionAttribute10", null), // rozšířený atribut 10
    EXT11 ("extensionAttribute11", null), // rozšířený atribut 11
    EXT12 ("extensionAttribute12", null), // rozšířený atribut 12
    EXT13 ("extensionAttribute13", null), // rozšířený atribut 13
    EXT14 ("extensionAttribute14", null), // rozšířený atribut 14
    EXT15 ("extensionAttribute15", null), // rozšířený atribut 15

    OC_GENERAL ("objectClass", null), // třída objektu
    OC_TOP ("objectClass", "top"), // vrcholná třída objektu
    OC_USER ("objectClass", "user"), // objekt = uživatel
    OC_ORG_PERSON ("objectClass", "organizationalPerson"), // objekt = osoba
    OC_PERSON ("objectClass", "person"), // objekt = osoba
    OC_INET_PERSON("objectClass", "inetOrgPerson"), // objekt - osoba
    OC_CONTACT ("objectClass", "contact"), // objekt = kontakt
    OC_GROUP ("objectClass", "group"), // objekt = obecná skupina
    OC_OU ("objectClass", "organizationalUnit"), // objekt = organizační jednotka

    ST_USER ("sAMAccountType", "805306368"), // objekt = uživatel

    GT_GENERAL("groupType", null), // výchozí typ skupiny pro dotaz
    GT_DISTRIBUTION ("groupType", "8"), // univerzální distribuční skupina
    GT_SECURITY ("groupType", "-2147483646"), // globální skupina se zabezpečením

    NT_SECURITY_DESCRIPTOR("nTSecurityDescriptor", null), // NT security descriptor

    DN ("distinguishedName", null); // plné jméno objektu s cestou

    private final String attribute;
    private final String value;

    EBakaLDAPAttributes(String attribute, String defaultValue) {
        this.attribute = attribute;
        this.value = defaultValue;
    }

    public String attribute() {
        return this.attribute;
    }

    public String value() {
        return (this.value != null) ? this.value : "";
    }

    public String toString() {
        return attribute();
    }
}
