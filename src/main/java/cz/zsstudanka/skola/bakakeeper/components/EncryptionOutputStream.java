package cz.zsstudanka.skola.bakakeeper.components;

import java.io.FilterOutputStream;
import java.io.OutputStream;

public class EncryptionOutputStream extends FilterOutputStream {

    /**
     * Creates an output stream filter built on top of the specified
     * underlying output stream.
     *
     * @param out the underlying output stream to be assigned to
     *            the field {@code this.out} for later use, or
     *            <code>null</code> if this instance is to be
     *            created without an underlying stream.
     */
    public EncryptionOutputStream(OutputStream out) {
        super(out);
    }

}
