package cz.zsstudanka.skola.bakakeeper.components;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Filtr pro proudové dešifrování paketů pomocí AES Galois/Counter s použitím hesla.
 *
 * @author Jan Hladěna
 */
public class EncryptionInputStream extends FilterInputStream {

    /** heslo */
    private final String PASSPHRASE;

    /** velikost jednoho paketu */
    private final int ENCRYPTED_CHUNK_SIZE = EncryptionManager.IV_BYTES
            + EncryptionManager.SALT_BYTES
            + EncryptionManager.CHUNK_SIZE
            + EncryptionManager.TAG_BITS / 8;

    /** buffer pro jeden paket o maximální velikosti IV + sůl + cipherData + tag */
    private byte[] CHUNK = new byte[ ENCRYPTED_CHUNK_SIZE ];

    /** počítadlo paketů */
    private int counter;

    /** ukazatel do aktuálního bufferu */
    private int index;

    /**
     * Výchozí konstruktor.
     *
     * @param in nižší vrstva
     * @param passphrase tajná fráze
     */
    public EncryptionInputStream(InputStream in, String passphrase) {
        super(in);

        this.PASSPHRASE = passphrase;
        this.index = 0;
        this.counter = 0;
    }

    /**
     * Aleternativní konstruktor s prázdným heslem.
     *
     * @param in nižší vrstva
     */
    protected EncryptionInputStream(InputStream in) {
        this(in, "");
    }

    /**
     * TODO // [(iv1)(s1)(c1)(t1) = 556][(iv2)(s2)(c2)(t2) = 114]
     *
     * @param b
     * @param off
     * @param len
     * @return
     * @throws IOException
     */
    @Override
    public int read(byte[] b, int off, int len) throws IOException {

        // počítadlo vstupních dat
        int inputBytes = super.read(b, off, len);
        // počítadlo výstupních dat
        int outputBytes = 0;

        byte[] data = null;
        int i;
        for (i = off; i < off + inputBytes; i++) {

            // načítání dat do paketového bufferu
            CHUNK[index] = (byte) b[i];
            index++;

            // pokud bylo dosaženo konce vstupního proudu, nebo pokud se paketový buffer již naplnil
            if (i == off + inputBytes - 1 || index == ENCRYPTED_CHUNK_SIZE) {

                try {
                    // provede se dešifrování paketu
                    data = decryptChunk();
                    outputBytes += data.length;

                    // proudový filtr; každý dešifrovaný paket je postupně přidán na odpovídající pozici
                    System.arraycopy(data, 0, b, off + ((counter - 1) * EncryptionManager.CHUNK_SIZE), data.length);
                } catch (Exception e) {
                    throw new IOException(e.getLocalizedMessage());
                }

            }
        }

        // výstupní velikost dešifrovaných paketů
        return (inputBytes != -1) ? (outputBytes) : -1;
    }

    /**
     * Dešifrování aktuálního paketu.
     *
     * @return dešifrovaný paket
     * @throws Exception
     */
    private byte[] decryptChunk() throws Exception {

        if (index <= 0) {
            return null;
        }

        byte[] dataToDecrypt = new byte[index];
        System.arraycopy(CHUNK, 0, dataToDecrypt, 0, index);

        // dešifrování dat
        byte[] decrypted = EncryptionManager.decrypt(dataToDecrypt, PASSPHRASE);

        // reset ukazatele a inkrementace počítadla
        index = 0;
        counter++;

        return decrypted;
    }

    @Override
    public void close() throws IOException {
        in.close();
    }

    @Override
    public boolean markSupported() {
        return false;
    }

}
