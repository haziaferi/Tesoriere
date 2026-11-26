package com.hazia.tesoriere.receipts

/*
 * Tesoriere's own: Sci-Fin at 2892110 had no receipts. The receipt folder as the engine sees it:
 * read only, by design (SPEC R27). Each platform gives its own; the desktop and Android file
 * system one is FileReceiptFolder in jvmCommon.
 */

/**
 * The folder Chatelaine writes receipt files into (SPEC R27, R28). It offers no way to write, move
 * or delete: Tesoriere only ever reads it, and the folder's files stay where they are.
 */
interface ReceiptFolder {
    /** The names of the files in the folder, in any order; never a sub-folder. */
    fun names(): List<String>

    /** The bytes of file [name], or null when it cannot be read or is not a file of this folder. */
    fun read(name: String): ByteArray?
}
