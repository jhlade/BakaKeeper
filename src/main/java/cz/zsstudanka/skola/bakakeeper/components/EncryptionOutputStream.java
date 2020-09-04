package cz.zsstudanka.skola.bakakeeper.components;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Filtr pro proudové šifrování paketů pomocí AES Galois/Counter s použitím hesla.
 *
 * @author Jan Hladěna
 */
public class EncryptionOutputStream extends FilterOutputStream {

    /** velikost vnitřního bufferu */
    private final static int BUFFER_SIZE = 4096;

    /** interní buffer */
    private byte[] buffer;

    /** ukazatel do interního bufferu */
    private int bIndex;

    /** heslo */
    private final String PASSPHRASE;

    /** jeden paket */
    private byte[] CHUNK;

    /** aktuální ukazatel do paketu */
    private int cIndex;

    /**
     * Výchozí konstruktor.
     *
     * @param out nižší vrstva
     * @param passphrase tajná fráze
     */
    public EncryptionOutputStream(OutputStream out, String passphrase) {
        super(out);

        this.buffer = null;
        this.bIndex = 0;

        this.PASSPHRASE = passphrase;

        this.CHUNK = new byte[ EncryptionManager.CHUNK_SIZE ];
        this.cIndex = 0;
    }

    /**
     * Alternativní konstruktor s prázdným heslem.
     *
     * @param out nižší vrstva
     */
    public EncryptionOutputStream(OutputStream out) {
        this(out, "");
    }

    /**
     * Zápis jednoho bajtu.
     *
     * @param b požadovaný bajt
     * @throws IOException
     */
    @Override
    public void write(int b) throws IOException {

        if (buffer == null) {
            buffer = new byte[ BUFFER_SIZE ];
        }

        // naplnění interního bufferu
        if (bIndex >= buffer.length) {
            internalWrite(buffer, 0, bIndex);
            bIndex = 0;
        }

        buffer[bIndex++] = (byte) b;
    }

    /**
     * Zápis pole bajtů.
     *
     * @param b bajtové pole
     * @param off offset
     * @param len celková délka
     * @throws IOException
     */
    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (len <= 0) {
            return;
        }

        flushBuffer();
        internalWrite(b, off, len);
    }

    /**
     * Zápis celého pole bajtů.
     *
     * @param b pole batjů
     * @throws IOException
     */
    @Override
    public void write(byte[] b) throws IOException {
        write(b, 0, b.length);
    }

    /**
     * Uzavření výstupu.
     *
     * @throws IOException
     */
    @Override
    public void close() throws IOException {

        if (cIndex != 0) {
            writeChunk(cIndex);
        }

        out.close();
    }

    /**
     * Interní provedení zašifrovaného zápisu po dosažení hranice paketu.
     *
     * @param b data k zašifrování
     * @param off offset
     * @param len celková délka
     * @throws IOException
     */
    private void internalWrite(byte[] b, int off, int len) throws IOException {

        int fill;
        for (fill = off; fill < len; fill++) {

            CHUNK[cIndex] = b[fill];
            cIndex++;

            // dosažena hranice paketu
            if (cIndex == EncryptionManager.CHUNK_SIZE) {
                writeChunk(cIndex);
            }
        }
    }

    /**
     * Zápis zašifrovaného paketu na podřízený výstup.
     *
     * @param size požadovaná délka dat
     * @throws IOException
     */
    private void writeChunk(int size) throws IOException {

        // přípava dat k zašifrování
        byte[] dataToEncrypt = new byte[size];
        System.arraycopy(CHUNK, 0, dataToEncrypt, 0, size);

        try {
            // provedení zašifrování paketu a odeslání výsledných dat do nižší vrstvy
            out.write(EncryptionManager.encrypt(dataToEncrypt, PASSPHRASE), 0,
                    size // počet bajtů čistých dat
                            + EncryptionManager.IV_BYTES // velikost inicializačního vektoru
                            + EncryptionManager.SALT_BYTES // délka soli
                            + EncryptionManager.TAG_BITS/8); // autentizační značka

        } catch (Exception e) {
            throw new IOException(e.getLocalizedMessage());
        } finally {
            // resetování ukazatele do interního paketu
            cIndex = 0;
        }
    }

    /**
     * Vyprázdní načtený interní buffer.
     *
     * @throws IOException
     */
    private void flushBuffer() throws IOException {
        if (bIndex > 0) {
            internalWrite(buffer, 0, bIndex);
            bIndex = 0;
        }
    }
}
