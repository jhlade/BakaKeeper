package cz.zsstudanka.skola.bakakeeper.components;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Base64;

/**
 * Statické služby pro poskytování AES Galois/Counter šifrování
 * na základě zvolené fráze.
 *
 * @author Jan Hladěna
 */
public class EncryptionManager {

    /** velikost jednoho paketu [B] - hypoteticky může být už od 1 B */
    protected static final int CHUNK_SIZE = 1024;

    /** AES transformace Galois/Counter */
    private static final String ENC_TRANSFORMATION = "AES/GCM/NoPadding";

    /** délka autentizačních dat [b] */
    protected static final int TAG_BITS = 128; // 16 B

    /** velikost inicializačního vektoru [B] */
    protected static final int IV_BYTES = 12;

    /** délka náhodné soli [B] */
    protected static final int SALT_BYTES = 16;

    /** délka AES šifry [b] */
    private static final int AES_BITS = 256;

    /** počet iterací */
    private static final int AES_ITER = 65536;

    /**
     * Poskytovaná šifra.
     *
     * @param aesKey AES klíč
     * @param iv inicializační vektor
     * @param mode režim (šifrování/desšifrování)
     * @return šifra
     * @throws Exception
     */
    private static Cipher getCipher(SecretKey aesKey, byte[] iv, int mode) throws Exception {
        Cipher cipher = Cipher.getInstance(ENC_TRANSFORMATION);
        cipher.init(mode, aesKey, new GCMParameterSpec(TAG_BITS, iv));
        return cipher;
    }

    /**
     * Vytvoření náhodného vektoru.
     *
     * @param count počet bajtů
     * @return náhodný vektor
     */
    private static byte[] getRandomBytes(int count) {
        byte[] randomBytes = new byte[count];

        new SecureRandom().nextBytes(randomBytes);
        return randomBytes;
    }

    /**
     * Získání klíče z dané fráze.
     *
     * @param passphrase tajná fráze (heslo)
     * @param salt sůl
     * @return odvozený klíč
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeySpecException
     */
    private static SecretKey getKeyFromPassphrase(String passphrase, byte[] salt) throws NoSuchAlgorithmException, InvalidKeySpecException {

        // továrna na tajné klíče odvozené z hesla
        // typ Password-Based Key Derivation Function 2, HMAC SHA-256
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");

        // specifikace klíče (heslo, sůl, počet iterací, délka v bitech)
        KeySpec spec = new PBEKeySpec(passphrase.toCharArray(), salt, AES_ITER, AES_BITS);

        // odvozený klíč
        return new SecretKeySpec(factory.generateSecret(spec).getEncoded(), "AES");
    }

    /**
     * Základní zašifrování vstupních dat.
     *
     * @param plainData vstupní data
     * @param passphrase tajná fráze
     * @return zašifrovaná data s IV, solí a autentizační značkou
     * @throws Exception
     */
    public static byte[] encrypt(byte[] plainData, String passphrase) throws Exception {

        // vytvoření náhodné soli
        byte[] salt = getRandomBytes(SALT_BYTES);

        // inicializační vektor
        byte[] iv = getRandomBytes(IV_BYTES);

        // získání klíče
        SecretKey aesKey = getKeyFromPassphrase(passphrase, salt);

        // použitá šifra
        Cipher cipher = getCipher(aesKey, iv, Cipher.ENCRYPT_MODE);

        // základní šifrovaný text
        byte[] cipherData = cipher.doFinal(plainData);

        // složení pole IV, sůl, zašifrovaný text
        byte[] result = ByteBuffer.allocate(iv.length + salt.length + cipherData.length)
                .put(iv)
                .put(salt)
                .put(cipherData)
                .array();

        return result;
    }

    /**
     * Základní dešifrování dat.
     *
     * @param cipherInput šifrovaná vstupní data ve formátu IV, sůl, cD, autentizační značka
     * @param passphrase tajná fráze
     * @return dešifrovaná data
     */
    public static byte[] decrypt(byte[] cipherInput, String passphrase) throws Exception {
        // buffer - rozbalení IV + sůl + šifrovaná data
        ByteBuffer cipherBuffer = ByteBuffer.wrap(cipherInput);

        // inicializační vektor
        byte[] iv = new byte[IV_BYTES];
        cipherBuffer.get(iv);

        // sůl
        byte[] salt = new byte[SALT_BYTES];
        cipherBuffer.get(salt);

        // zbytek - šifrovaná data
        byte[] cipherData = new byte[cipherBuffer.remaining()];
        cipherBuffer.get(cipherData);

        // získání klíče
        SecretKey aesKey = getKeyFromPassphrase(passphrase, salt);

        // použitá šifra
        Cipher cipher = getCipher(aesKey, iv, Cipher.DECRYPT_MODE);

        // dešifrování
        return cipher.doFinal(cipherData);
    }

    /**
     * Zašifrování vstupního textového řetězce pomocí dané fráze. Výsledek je zakódován v B64.
     *
     * @param plainText čistý text
     * @param passphrase tajná fráze
     * @return zašifrovaný text kódovaný v Base64
     * @throws Exception výjimka při šifrování
     */
    public static String encrypt(String plainText, String passphrase) throws Exception {
        // B64 výsledek
        return Base64.getEncoder().encodeToString(encrypt(plainText.getBytes(), passphrase));
    }

    /**
     * Dešifrování vstupního textového řetězce kódovaného v Base64 pomocí dané fráze.
     *
     * @param cipherText zašifrovaný text kódovaný v Base64
     * @param passphrase tajná fráze
     * @return čistý dešifrovaný text
     * @throws Exception výjimka při dešiforvání
     */
    public static String decrypt(String cipherText, String passphrase) throws Exception {
        return new String(decrypt(Base64.getDecoder().decode(cipherText.getBytes(StandardCharsets.UTF_8)), passphrase), StandardCharsets.UTF_8);
    }

}
