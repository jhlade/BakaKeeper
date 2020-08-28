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

    /** heslo */
    private final String PASSPHRASE;

    /** vstupní data o maximální velikosti jednoho paketu */
    private byte[] BUFFER = new byte[ EncryptionManager.CHUNK_SIZE ];

    /** aktuální počet vstupních bajtů */
    private int count;

    /**
     * Výchozí konstruktor.
     *
     * @param out nižší vrstva
     * @param passphrase tajná fráze
     */
    public EncryptionOutputStream(OutputStream out, String passphrase) {
        super(out);
        this.PASSPHRASE = passphrase;
    }

    /**
     * Aleternativní konstruktor s prázdným heslem.
     *
     * @param out nižší vrstva
     */
    public EncryptionOutputStream(OutputStream out) {
        this(out, "");
    }

    /**
     * Zápis jednoho bajtu do bufferu. Pokud je překročena velikost bufferu (maximální velikost paketu),
     * je buffer vyprázdněn a data zašifrována.
     *
     * @param b vstupní bajt
     * @throws IOException
     */
    @Override
    public void write(int b) throws IOException {

        if (count == BUFFER.length) {
            flush();
        }

        BUFFER[count] = (byte)(b & 0xff);
        ++count;
    }

    /**
     * Zápis pole bajtů.
     *
     * @param b pole bajtů
     * @throws IOException
     */
    @Override
    public void write(byte[] b) throws IOException {
        // zápis po jednom bajtu
        for (int i = 0; i < b.length; i++) {
            write(b[i]);
        }
    }

    /**
     * Základní zápis dat.
     *
     * @param b pole bajtů
     * @param off offset
     * @param len délka dat
     * @throws IOException
     */
    @Override
    public void write(byte[] b, int off, int len) throws IOException {

        // pokud je volno, překopíruje se obsah do bufferu
        if (len < (this.BUFFER.length - count) ) {
            System.arraycopy(b, off, this.BUFFER, count, len);
            count += len;
        } else {
            // pokud už není volno, provede se vyprázdnění bufferu
            flush();
        }
    }

    /**
     * Okamžité vyprázdnění bufferu a provedení zápisu zašifrovného paketu do další vrstvy.
     *
     * @throws IOException
     */
    @Override
    public void flush() throws IOException {

        // žádná data
        if (count == 0) {
            return;
        }

        // příprava dat k zašifrování
        byte[] dataToEncrypt = new byte[ count ];
        // pokud čítač označuje celý paket, současný buffer se okamžitě použije
        if (count == EncryptionManager.CHUNK_SIZE) {
            dataToEncrypt = BUFFER;
        } else {
            // zkopírování zbytku
            System.arraycopy(BUFFER, 0, dataToEncrypt, 0, count);
        }

        try {
            // provedení zašifrování paketu a odeslání výsledných dat do nižší vrstvy
            out.write(EncryptionManager.encrypt(dataToEncrypt, PASSPHRASE), 0,
                    count // počet bajtů čistých dat
                            + EncryptionManager.IV_BYTES // velikost inicializačního vektoru
                            + EncryptionManager.SALT_BYTES // délka soli
                            + EncryptionManager.TAG_BITS/8); // autentizační značka

        } catch (Exception e) {
            throw new IOException(e.getLocalizedMessage());
        }

        // nastavení počítadla zpátky na začátek bufferu
        count = 0;
        // vyprázdnění nižší vrstvy
        out.flush();
    }

    /**
     * Ukončení výstupních operací.
     *
     * @throws IOException
     */
    @Override
    public void close() throws IOException {
        flush();
        out.close();
    }
}
