package cz.zsstudanka.skola.bakakeeper.components;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Base64;

/**
 * Pomocná třída pro zjednodušené poskytování kryptografie.
 * Používá se AES v Galoisovském režimu s uložením inicializačního vektoru a náhodné soli
 * do prefixu šifrovaných dat.
 *
 * @author Jan Hladěna
 */
public class EncryptionManager {

    /** AES transformace Galois/Counter */
    private static final String ENC_TRANSFORMATION = "AES/GCM/NoPadding";
    /** délka autentizačních dat */
    private static final int TAG_BITS = 128;
    /** délka náhodné soli */
    private static final int SALT_BYTES = 16;
    /** počet bajtů inicializačního vektoru */
    private static final int IV_BYTES = 12;
    /** délka AES šifry */
    private static final int AES_BITS = 256;
    /** počet iterací */
    private static final int AES_ITER = 65536;

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

    /**
     * Zašifrování vstupních dat pomocí dané fráze.
     *
     * @param plainData čistá data
     * @param passphrase heslo
     * @return zašifrovaná data
     * @throws Exception výjimka při šifrování
     */
    public static byte[] encrypt(byte[] plainData, String passphrase) throws Exception {

        // vytvoření náhodné soli
        byte[] salt = getRandomIVbytes(SALT_BYTES);

        // inicializační vektor
        byte[] iv = getRandomIVbytes(IV_BYTES);

        // získání klíče
        SecretKey aesKey = getKeyFromPassphrase(passphrase.toCharArray(), salt);

        // použitá šifra
        Cipher cipher = Cipher.getInstance(ENC_TRANSFORMATION);

        // specifikace parametrů pro AES-GCM
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(TAG_BITS, iv));

        // základní šifrovaný text
        byte[] cipherText = cipher.doFinal(plainData);

        // složení pole IV, sůl, zašifrovaný text
        byte[] cipherTextIVS = ByteBuffer.allocate(iv.length + salt.length + cipherText.length)
                .put(iv)
                .put(salt)
                .put(cipherText)
                .array();

        return cipherTextIVS;
    }

    /**
     * Dešifrování dat pomocí dané fráze.
     *
     * @param cipherData šifrovaná data
     * @param passphrase heslo
     * @return čisrtá data
     * @throws Exception výjimka při dešifrování
     */
    public static byte[] decrypt(byte[] cipherData, String passphrase) throws Exception {

        // buffer - rozdělení IV/sůl/šifrovaná data
        ByteBuffer cipherBuffer = ByteBuffer.wrap(cipherData);

        // inicializační vektor
        byte[] iv = new byte[IV_BYTES];
        cipherBuffer.get(iv);

        // sůl
        byte[] salt = new byte[SALT_BYTES];
        cipherBuffer.get(salt);

        // zbytek - šifrovaný text
        byte[] cipherText = new byte[cipherBuffer.remaining()];
        cipherBuffer.get(cipherText);

        // získání AES klíče z hesla
        SecretKey aesKey = getKeyFromPassphrase(passphrase.toCharArray(), salt);

        // AES transformace
        Cipher cipher = Cipher.getInstance(ENC_TRANSFORMATION);

        // inicializace dešifrování, specifikace parametrů pro GCM
        cipher.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(TAG_BITS, iv));

        return cipher.doFinal(cipherText);
    }

    /**
     * Vytvoření náhodného vektoru.
     *
     * @param length délka v bajtech
     * @return náhodný vektor
     */
    private static byte[] getRandomIVbytes(int length) {

        byte[] ivBytes = new byte[length];

        new SecureRandom().nextBytes(ivBytes);
        return ivBytes;
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
    private static SecretKey getKeyFromPassphrase(char[] passphrase, byte[] salt) throws NoSuchAlgorithmException, InvalidKeySpecException {

        // továrna na tajné klíče
        // typ Password-Based Key Derivation Function 2, HMAC SHA-256
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");

        // specifikace klíče (heslo, sůl, počet iterací, délka v bitech)
        KeySpec spec = new PBEKeySpec(passphrase, salt, AES_ITER, AES_BITS);

        // odvozený klíč
        return new SecretKeySpec(factory.generateSecret(spec).getEncoded(), "AES");
    }

}
