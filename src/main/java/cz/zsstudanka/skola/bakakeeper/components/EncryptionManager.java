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

public class EncryptionManager {

    /** AES transformace Galois/Counter */
    public static final String ENC_TRANSFORMATION = "AES/GCM/NoPadding";

    /** délka autentizačních dat [b] */
    public static final int TAG_BITS = 128;

    /** velikost inicializačního vektoru [B] */
    public static final int IV_BYTES = 12;

    /** délka náhodné soli [B] */
    public static final int SALT_BYTES = 16;

    /** délka AES šifry [b] */
    private static final int AES_BITS = 256;

    /** počet iterací */
    private static final int AES_ITER = 65536;

    /**
     * TODO
     *
     * @param aesKey klíč
     * @param iv inicializační vektor
     * @param mode režim
     * @return manažer šifrování
     * @throws Exception
     */
    public static Cipher getCipher(SecretKey aesKey, byte[] iv, int mode) throws Exception {
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
    public static byte[] getRandomBytes(int count) {
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
    public static SecretKey getKeyFromPassphrase(String passphrase, byte[] salt) throws NoSuchAlgorithmException, InvalidKeySpecException {

        // továrna na tajné klíče
        // typ Password-Based Key Derivation Function 2, HMAC SHA-256
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");

        // specifikace klíče (heslo, sůl, počet iterací, délka v bitech)
        KeySpec spec = new PBEKeySpec(passphrase.toCharArray(), salt, AES_ITER, AES_BITS);

        // odvozený klíč
        return new SecretKeySpec(factory.generateSecret(spec).getEncoded(), "AES");
    }

    /**
     * TODO
     *
     * @param plainData
     * @param passphrase
     * @return
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
     * TODO
     *
     * @param cipherInput
     * @param passphrase
     * @return
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
     * Zašifrování vstupního řetězce pomocí dané fráze. Výsledek je zakódován v B64.
     *
     * @param plainText čistý text
     * @param passphrase heslo
     * @return zašifrovaný text
     * @throws Exception výjimka při šifrování
     */
    public static String encrypt(String plainText, String passphrase) throws Exception {
        // B64 výsledek
        return Base64.getEncoder().encodeToString(encrypt(plainText.getBytes(), passphrase));
    }

    /**
     * Dešifrování vstupního řetězce pomocí dané fráze.
     *
     * @param cipherText zašifrovaný text
     * @param passphrase heslo
     * @return čistý text
     * @throws Exception výjimka při dešiforvání
     */
    public static String decrypt(String cipherText, String passphrase) throws Exception {
        return new String(decrypt(Base64.getDecoder().decode(cipherText.getBytes(StandardCharsets.UTF_8)), passphrase), StandardCharsets.UTF_8);
    }

}
