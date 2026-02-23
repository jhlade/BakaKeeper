<?php
// =============================================================================
// phpLDAPadmin – konfigurace pro BakaKeeper vývojové prostředí
//
// LDAP server: Samba4 AD DC (dc.skola.local, 172.20.0.10)
// Přihlášení v UI:
//   Login DN:  CN=Administrator,CN=Users,DC=skola,DC=local
//   Heslo:     BakaKeeper.2026
// =============================================================================

$servers = new Datastore();
$servers->newServer('ldap_pla');

// Název serveru zobrazený v UI
$servers->setValue('server', 'name', 'Samba4 AD – skola.local');

// Adresa a port LDAP serveru (plain LDAP, port 389)
$servers->setValue('server', 'host', 'dc.skola.local');
$servers->setValue('server', 'port', 389);

// Base DN domény
$servers->setValue('server', 'base', array('DC=skola,DC=local'));

// LDAP protokol verze 3 (Samba4 vyžaduje)
$servers->setValue('server', 'ldap_version', 3);

// Bez TLS – Samba4 dev má ldap server require strong auth = No
$servers->setValue('server', 'tls', false);

// Přihlašování přes session – uživatel zadá DN + heslo ručně v UI
$servers->setValue('login', 'auth_type', 'session');

// Zakázat anonymní přístup (Samba4 ho stejně nepovoluje)
$servers->setValue('login', 'anon_bind', false);
