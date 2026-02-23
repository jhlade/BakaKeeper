package cz.zsstudanka.skola.bakakeeper.components;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Filtr pro proudové dešifrování paketů pomocí AES Galois/Counter s použitím hesla.
 *
 *
 * @author Jan Hladěna
 */
public class EncryptionInputStream extends FilterInputStream {

    /** velikost vnitřního bufferu */
    private final static int BUFFER_SIZE = 4096; // Minimální velikost = n * ENCRYPTED_CHUNK_SIZE

    /** interní buffer */
    private byte[] buffer;

    /** offsety při děleném čtení z bufferu */
    private int offsetIn;
    private int offsetOut;

    /** příznak konce proudu */
    private boolean eof;

    /** začátek výstupu */
    private int start;

    /** konec výstupu */
    private int end;

    /** heslo */
    private final char[] PASSPHRASE;

    /** velikost jednoho paketu */
    private final static int ENCRYPTED_CHUNK_SIZE = EncryptionManager.IV_BYTES
            + EncryptionManager.SALT_BYTES
            + EncryptionManager.CHUNK_SIZE
            + EncryptionManager.TAG_BITS / 8;

    /** buffer pro jeden paket o maximální velikosti IV + sůl + cipherData + tag */
    private byte[] CHUNK;

    /** dočasná schránka pro uchování dat z nedokončeného paketu napříč buffery */
    private byte[] previousData;

    /** počítadlo paketů */
    private int chunkCounter;

    /** ukazatel do aktuálního paketu */
    private int chunkIndex;

    /** zbývající data */
    private int chunkDataRemaining;

    /**
     * Výchozí konstruktor.
     *
     * @param in nižší vrstva
     * @param passphrase tajná fráze
     */
    public EncryptionInputStream(InputStream in, char[] passphrase) {
        super(in);

        this.buffer = new byte[ BUFFER_SIZE ];

        this.PASSPHRASE = passphrase;
        this.CHUNK = new byte[ ENCRYPTED_CHUNK_SIZE ];

        this.previousData = null;

        this.chunkCounter = 0;
        this.chunkIndex = 0;
        this.chunkDataRemaining = 0;
        this.offsetIn = 0;
        this.offsetOut = 0;

        this.eof = false;
        this.start = 0;
        this.end = 0;
    }

    /**
     * Alternativní konstruktor s prázdným heslem.
     *
     * @param in nižší vrstva
     */
    protected EncryptionInputStream(InputStream in) {
        this(in, new char[]{0});
    }

    /**
     * Čtení po jednom bajtu.
     *
     * @return načtený bajt nebo EOF
     * @throws IOException
     */
    @Override
    public int read() throws IOException {

        // nový buffer
        if (start >= end) {
            refill();
        }

        // přesto bylo dosaženo konce souboru
        if (start >= end) {
            return -1;
        }

        return buffer[start++] & 0xff;
    }

    /**
     * Přečtení celého pole bajtů.
     *
     * @param b vstupní šifrované pole bajtů
     * @return délka přečtených/dešifrovaných dat
     * @throws IOException
     */
    @Override
    public int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    /**
     * Přečtení částečného pole bajtů.
     *
     * @param b vstupní šifrované pole bajtů
     * @param off offset
     * @param len požadovaná délka čtení
     * @return délka přečtených/dešifrovaných dat
     * @throws IOException
     */
    @Override
    public int read(byte[] b, int off, int len) throws IOException {

        // nový buffer
        if (start >= end) {
            refill();
        }

        // dosaženo konce souboru
        if (start >= end) {
            return -1;
        }

        int bytes = Math.min(len, (end - start));
        System.arraycopy(buffer, start, b, off, bytes);
        start += bytes;

        return bytes;
    }

    /**
     * Znovunaplnění bufferu.
     *
     * @throws IOException
     */
    private void refill() throws IOException {
        // dosaženo konce souboru - žádná činnost nebude provedena
        if (eof) {
            return;
        }

        // načtení dat do nového interního bufferu
        int bytesRead = in.read(buffer);

        // nalezen konec souboru
        if (bytesRead == -1) {
            eof = true;

            // konec souboru - dekryptování případného lichého zbytku
            if (this.chunkIndex > 0) {
                finalizeChunk();
                start = 0;
            }

            // žádná nová data - nepokračovat na start = 0
            return;
        } else {
            // začíná se číst nový buffer
            this.chunkCounter = 0;
            this.offsetIn = 0;
            this.offsetOut = 0;
            this.end = 0;

            // rozdělení načtených dat na jednotlivé pakety
            int chunksExpected = bytesRead / ENCRYPTED_CHUNK_SIZE;
            int encryptedBytesRemaining = bytesRead % ENCRYPTED_CHUNK_SIZE;

            // používá se nový buffer, ale ještě není zcela zpracován minulý paket
            if (this.chunkDataRemaining != 0) {
                // odečtení části od zbytku
                encryptedBytesRemaining -= this.chunkDataRemaining;

                // přepočítání
                if (encryptedBytesRemaining <= 0) {
                    encryptedBytesRemaining += ENCRYPTED_CHUNK_SIZE;
                    chunksExpected--;
                }

                // provedení dešifrování rozpracovaného paketu
                System.arraycopy(buffer, chunkCounter * ENCRYPTED_CHUNK_SIZE + this.offsetIn, CHUNK, this.chunkIndex + 1, this.chunkDataRemaining);
                byte[] decrypted = decryptChunk(ENCRYPTED_CHUNK_SIZE);

                // výstup do dočasné paměti
                this.previousData = new byte[decrypted.length];
                System.arraycopy(decrypted, 0, this.previousData, 0, decrypted.length);

                this.offsetOut += decrypted.length;
                this.end += decrypted.length;
            }


            // zpracování všech celých paketů aktuálního bufferu
            if (chunksExpected > 0) {
                while (this.chunkCounter < chunksExpected) {
                    processChunk();
                }
            }

            // lichý zbytek v aktuálním bufferu
            if (encryptedBytesRemaining > 0) {
                // zkopírování načtené části
                System.arraycopy(buffer, chunkCounter * ENCRYPTED_CHUNK_SIZE + this.offsetIn, CHUNK, 0, encryptedBytesRemaining);

                // ukazatel do aktuálního paketu
                this.chunkIndex = encryptedBytesRemaining - 1;

                // do celého paketu zbývá zapsat
                this.chunkDataRemaining = (ENCRYPTED_CHUNK_SIZE - encryptedBytesRemaining);

                // přičtení ke globálnímu offsetu
                this.offsetIn += encryptedBytesRemaining;

                // žádná další vstupní data, současný buffer je poslední
                if (in.available() == 0) {
                    finalizeChunk();
                }
            }

        }

        start = 0;
    }

    /**
     * Dešifrování celého paketu.
     *
     * @return dešifrovaná data
     * @throws IOException
     */
    private byte[] decryptChunk() throws  IOException {
        return decryptChunk(ENCRYPTED_CHUNK_SIZE);
    }

    /**
     * Dešifrování paketu požadované délky, typicky závěrečného paketu.
     *
     * @param size požadovaná velikost
     * @return dešifrovaná data
     * @throws IOException
     */
    private byte[] decryptChunk(int size) throws IOException {

        byte[] dataToDecrypt = new byte[ size ];
        System.arraycopy(CHUNK, 0, dataToDecrypt, 0, dataToDecrypt.length);

        byte decrypted[] = null;

        try {
            // provedení dešifrování
            decrypted = EncryptionManager.decrypt(dataToDecrypt, PASSPHRASE);
        } catch (Exception e) {
            throw new IOException(e);
        }

        if (this.chunkDataRemaining != 0) {
            this.offsetIn += this.chunkDataRemaining;
            this.chunkDataRemaining = 0;
        } else {
            this.chunkCounter++;
        }

        this.chunkIndex = 0;

        return decrypted;
    }

    /**
     * Zpracování paketu.
     *
     * @param positionInChunk počáteční pozice v paketu
     * @throws IOException
     */
    private void processChunk(int positionInChunk) throws IOException {
        // provedení dekryptování chunku
        System.arraycopy(buffer, chunkCounter * ENCRYPTED_CHUNK_SIZE + this.offsetIn, CHUNK, positionInChunk, ENCRYPTED_CHUNK_SIZE - positionInChunk);
        byte[] decrypted = decryptChunk((positionInChunk == 0) ? ENCRYPTED_CHUNK_SIZE : positionInChunk);

        // výstup
        System.arraycopy(decrypted, 0, buffer, (this.chunkCounter - 1) * EncryptionManager.CHUNK_SIZE + this.offsetOut, decrypted.length);

        // předchozí data
        if (this.offsetOut != 0 && this.previousData != null) {
            System.arraycopy(this.previousData, 0, buffer, (this.chunkCounter - 1) * EncryptionManager.CHUNK_SIZE + this.offsetOut - this.previousData.length, this.previousData.length);
            this.previousData = null;
        }

        this.end += decrypted.length;
    }

    /**
     * Zpracování celého paketu.
     *
     * @throws IOException
     */
    private void processChunk() throws IOException {
        processChunk(0);
    }

    /**
     * Dokončení zpracování otevřeného paketu z minulého bufferu.
     *
     */
    private void finalizeChunk() throws IOException {
        this.offsetOut += EncryptionManager.CHUNK_SIZE;
        processChunk(this.chunkIndex + 1);
    }

    /**
     * Celková délka dostupných dat.
     *
     * @return dostupná délka dat
     * @throws IOException
     */
    @Override
    public int available() throws IOException {
        return end - start;
    }

    /**
     * Uzavření proudu.
     *
     * @throws IOException
     */
    @Override
    public void close() throws IOException {
        in.close();
        buffer = null;
    }

    @Override
    public boolean markSupported() {
        return false;
    }

    @Override
    public void mark(int readlimit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void reset() {
        throw new UnsupportedOperationException();
    }
}
