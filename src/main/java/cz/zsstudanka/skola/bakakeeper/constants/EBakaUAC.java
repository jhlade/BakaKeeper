package cz.zsstudanka.skola.bakakeeper.constants;

/**
 * Výčet vlastností UserAccountControls.
 *
 * @author Jan Hladěna
 */
public enum EBakaUAC {

    /**
     * Zde jsou vypsány všechny vlastnosti objektu uživatele podporované současným MS AD,
     * pro účel zpracování účtů žáků a případně personálu budou potřeba pouze některé.
     */
    SCRIPT (0x0001),
    ACCOUNTDISABLE (0x0002), // účet je uzamčen
    HOMEDIR_REQUIRED (0x0008),
    LOCKOUT (0x0010),
    PASSWD_NOTREQD (0x0020),
    PASSWD_CANT_CHANGE (0x0040), // uživatel nemůže změnit heslo
    ENCRYPTED_TEXT_PWD_ALLOWED (0x0080),
    TEMP_DUPLICATE_ACCOUNT (0x0100),
    NORMAL_ACCOUNT (0x0200),
    INTERDOMAIN_TRUST_ACCOUNT (0x0800),
    WORKSTATION_TRUST_ACCOUNT (0x1000),
    SERVER_TRUST_ACCOUNT (0x2000),
    DONT_EXPIRE_PASSWORD (0x10000), // platnost hesla nikdy nevyprší
    MNS_LOGON_ACCOUNT (0x20000),
    SMARTCARD_REQUIRED (0x40000),
    TRUSTED_FOR_DELEGATION (0x80000),
    NOT_DELEGATED (0x100000),
    USE_DES_KEY_ONLY (0x200000),
    DONT_REQ_PREAUTH (0x400000),
    PASSWORD_EXPIRED (0x800000), // platnost hesla již vypršela
    TRUSTED_TO_AUTH_FOR_DELEGATION (0x1000000),
    PARTIAL_SECRETS_ACCOUNT (0x04000000);

    private final int value;

    EBakaUAC(int value) {
        this.value = value;
    }

    /**
     * Provede kontrolu prezence příznaku v informacích o stavu účtu.
     *
     * @param userAccountControls informace o stavu účtu
     * @return příznak byl nalezen
     */
    public boolean checkFlag(Integer userAccountControls) {
        return ( (userAccountControls & this.value) != 0 ) ? true : false;
    }

    public boolean checkFlag(String userAccountControls) {
        return checkFlag(Integer.parseInt(userAccountControls));
    }

    /**
     * Provede nastavení příznaku k předaným informacím o stavu účtu.
     *
     * @param userAccountControls stávající informace
     * @return informace s nastaveným příznakem
     */
    public Integer setFlag(Integer userAccountControls) {
        return (userAccountControls | this.value);
    }

    public Integer setFlag(String userAccountControls) {
        return setFlag(Integer.parseInt(userAccountControls));
    }

    /**
     * Provede odebrání příznaku od předaných informací o stavu účtu.
     *
     * @param userAccountControls stávající informace
     * @return informace se odebraným příznakem
     */
    public Integer clearFlag(Integer userAccountControls) {
        return (userAccountControls & ~this.value);
    }

    public Integer clearFlag(String userAccountControls) {
        return clearFlag(Integer.parseInt(userAccountControls));
    }

    /**
     * Hodnota příznaku.
     *
     * @return číselaná hodnota příznaku
     */
    public int value() {
        return this.value;
    }

    @Override
    public String toString() {
        return String.format("%d", value);
    }

}
