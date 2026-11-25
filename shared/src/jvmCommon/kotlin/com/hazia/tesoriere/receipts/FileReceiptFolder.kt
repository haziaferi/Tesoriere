package com.hazia.tesoriere.receipts

import java.io.File
import java.io.IOException

/*
 * Tesoriere's own: Sci-Fin at 2892110 had no receipts. The receipt folder on a file system the
 * JVM reaches through java.io (SPEC R27): it lists and reads, and never opens a file for writing.
 * Android's folder picker hands back a document tree, which gets a ReceiptFolder of its own with
 * the app (phase 1's plan leaves the picker out).
 */

/** The files directly in [dir]; a sub-folder, or a name that leaves [dir], is not one of them. */
class FileReceiptFolder(private val dir: File) : ReceiptFolder {

    /** The files' names in name order: java.io lists them in no set order. */
    override fun names(): List<String> = dir.listFiles()?.filter { it.isFile }?.map { it.name }?.sorted() ?: emptyList()

    override fun read(name: String): ByteArray? {
        if (name.isEmpty() || name.contains('/') || name.contains('\\') || name == "." || name == "..") return null
        val f = File(dir, name)
        if (!f.isFile) return null
        return try { f.readBytes() } catch (_: IOException) { null } catch (_: SecurityException) { null }
    }
}
