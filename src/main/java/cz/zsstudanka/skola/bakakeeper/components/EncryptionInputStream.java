package cz.zsstudanka.skola.bakakeeper.components;

import java.io.FilterInputStream;
import java.io.InputStream;

public class EncryptionInputStream extends FilterInputStream {


    /**
     * Creates a <code>FilterInputStream</code>
     * by assigning the  argument <code>in</code>
     * to the field <code>this.in</code> so as
     * to remember it for later use.
     *
     * @param in the underlying input stream, or <code>null</code> if
     *           this instance is to be created without an underlying stream.
     */
    protected EncryptionInputStream(InputStream in) {
        super(in);
    }
}
