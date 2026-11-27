package com.hazia.tesoriere.data

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/*
 * Ported from Sci-Fin native/shared/src/jvmCommon/kotlin/app/scifin/data/Integrity.kt's `copies`,
 * with app/LocalCopies.kt and app/AppStore.kt's eraseEverything, at 2892110.
 *
 * Changed from Sci-Fin:
 * - One call erases the store and then the files, in that order (SPEC R147): a store that cannot be
 *   erased leaves every file as it was. Sci-Fin's AppStore made the two calls itself.
 * - The `snapshots` folder goes whole, its subfolders included; Sci-Fin deleted its top-level
 *   files. Nothing past the store's folder is reached: a link or a junction in it, or standing as
 *   `snapshots`, is removed as the entry alone, a folder whose real place lies outside is not
 *   entered, and a file whose real folder lies outside is refused, named (FILE_NOT_DELETED).
 * - Once begun it runs to its end, even when the caller is cancelled (NonCancellable). Beside the store, the notice file, its part
 *   file and the notices kept aside as unreadable go too (SPEC R144); Sci-Fin's one marker did.
 * - Nothing else is touched: the store file itself stays, emptied, and every other file beside it
 *   and every folder the user chose, exported backups and automatic copies, are kept (owner,
 *   2026-11-27).
 * - Each file that cannot be deleted is named, with FILE_NOT_DELETED; Sci-Fin gave a count.
 * - Section references are replaced by SPEC rule numbers.
 */

/**
 * What the forgotten-PIN erase did (SPEC R147): no [problems] when everything went; [filesLeft]
 * names each file that could not be deleted, as a path from the store's folder with `/`.
 */
data class Erased(val problems: Set<EraseProblem>, val filesLeft: List<String>)

/** SPEC R147: the forgotten-PIN erase, the store and then the files the app keeps beside it. */
object Erase {

    /**
     * Erases [repo]'s store ([ProfileRepository.eraseStore]) and then, when its rows are gone, the
     * `snapshots` folder beside [db] and the damage notices kept beside it. Once begun it runs to
     * its end, even when the caller is cancelled, which it learns on return; it never throws
     * otherwise. When a file cannot be deleted, the store stays erased, every other file goes, and
     * the erase can be run again.
     */
    suspend fun everything(repo: ProfileRepository, db: File): Erased = everything(repo, db, ::deleteFile)

    /** [everything] with the delete given, so a test can make one fail. */
    internal suspend fun everything(repo: ProfileRepository, db: File, delete: (File) -> Boolean): Erased =
        withContext(NonCancellable) { erase(repo, db, delete) }

    private suspend fun erase(repo: ProfileRepository, db: File, delete: (File) -> Boolean): Erased {
        val store = repo.eraseStore()
        if (store == EraseProblem.STORE_NOT_ERASED) return Erased(setOf(store), emptyList())
        val left = files(db, delete)
        val problems = buildSet {
            store?.let { add(it) }
            if (left.isNotEmpty()) add(EraseProblem.FILE_NOT_DELETED)
        }
        return Erased(problems, left)
    }

    /**
     * Deletes the `snapshots` folder beside [db] whole and the notice files beside [db]; the names
     * of the files that could not be deleted, from [db]'s folder. Never throws.
     */
    internal fun files(db: File, delete: (File) -> Boolean = ::deleteFile): List<String> {
        val dir = db.absoluteFile.parentFile ?: return emptyList()
        if (!dir.isDirectory) return emptyList()
        val base = dir.toPath()
        // with no real place for the store's folder nothing can be shown to lie inside it: delete nothing
        val home = real(base) ?: return listOf(Snapshot.FOLDER)
        val left = mutableListOf<String>()
        fun name(p: Path) = base.relativize(p).toString().replace(File.separatorChar, '/')
        fun gone(p: Path) = try {
            delete(p.toFile())
        } catch (_: Exception) {
            false
        }

        /** [p] deleted when its real folder lies under [root], else refused; named when it stays. */
        fun deleteUnder(p: Path, root: Path) {
            val parent = p.parent?.let(::real)
            if (parent == null || !parent.startsWith(root) || !gone(p)) left += name(p)
        }

        val snapshots = base.resolve(Snapshot.FOLDER)
        val attrs = attributes(snapshots)
        if (attrs != null) {
            val own = if (plainFolder(attrs)) real(snapshots)?.takeIf { it.startsWith(home) } else null
            if (own == null) {
                // a link, a junction or no folder at all: the entry alone, never what it leads to
                deleteUnder(snapshots, home)
            } else {
                walk(snapshots, own, ::deleteUnder, ::gone) { left += name(it) }
            }
        }

        val notices = dir.listFiles { f ->
            f.name == Integrity.MARKER || f.name == Integrity.MARKER + ".part" || f.name.startsWith(Integrity.UNREADABLE_MARKER)
        }.orEmpty().sortedBy { it.name }
        for (f in notices) deleteUnder(f.toPath(), home)
        return left
    }

    /**
     * Everything in the folder [top], whose real place is [own], deleted bottom up through
     * [deleteUnder], without following a link: a folder in it that is a link or a junction, or whose
     * real place lies outside [own], is deleted as the entry alone and not entered. Windows reports a
     * junction as a folder, not a link, so each folder's attributes and real place are checked here.
     */
    private fun walk(top: Path, own: Path, deleteUnder: (Path, Path) -> Unit, gone: (Path) -> Boolean, failed: (Path) -> Unit) {
        try {
            Files.walkFileTree(top, object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(d: Path, a: BasicFileAttributes): FileVisitResult {
                    if (d == top) return FileVisitResult.CONTINUE
                    val at = attributes(d)
                    val inside = at != null && plainFolder(at) && real(d)?.startsWith(own) == true
                    if (inside) return FileVisitResult.CONTINUE
                    deleteUnder(d, own)
                    return FileVisitResult.SKIP_SUBTREE
                }

                override fun visitFile(f: Path, a: BasicFileAttributes): FileVisitResult {
                    deleteUnder(f, own)
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(f: Path, e: IOException): FileVisitResult {
                    failed(f)
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(d: Path, e: IOException?): FileVisitResult {
                    // a folder still holding a file left is not named again: that file is
                    gone(d)
                    return FileVisitResult.CONTINUE
                }
            })
        } catch (_: Exception) {
            failed(top)
        }
    }

    /** A folder that is neither a symbolic link nor anything else the system marks, such as a junction. */
    private fun plainFolder(a: BasicFileAttributes): Boolean = a.isDirectory && !a.isSymbolicLink && !a.isOther

    /** [p]'s attributes, not following a link; null when it is absent or cannot be read. */
    private fun attributes(p: Path): BasicFileAttributes? = try {
        Files.readAttributes(p, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
    } catch (_: Exception) {
        null
    }

    /** [p]'s real place, every link followed; null when it cannot be found. */
    private fun real(p: Path): Path? = try {
        p.toRealPath()
    } catch (_: Exception) {
        null
    }

    /** [f] deleted, or not there; false when it is there and cannot be deleted. */
    private fun deleteFile(f: File): Boolean = try {
        Files.deleteIfExists(f.toPath())
        true
    } catch (_: Exception) {
        false
    }
}
