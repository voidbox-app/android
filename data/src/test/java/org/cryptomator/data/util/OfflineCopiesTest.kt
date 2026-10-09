package org.cryptomator.data.util

import org.cryptomator.domain.Cloud
import org.cryptomator.domain.CloudFile
import org.cryptomator.domain.CloudFolder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException
import java.util.Date
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class OfflineCopiesTest {

	@TempDir
	lateinit var tmpDir: File

	private lateinit var directory: File
	private lateinit var copies: OfflineCopies

	private val d = folder("/d")
	private val file = file(d, "a.c9r", 11, 1000)

	@BeforeEach
	fun setup() {
		directory = File(tmpDir, "offline")
		copies = OfflineCopies(directory)
	}

	private val vault1 get() = copies.vault(1)

	private fun keep(ciphertext: CloudFile, content: String = "x".repeat(ciphertext.size!!.toInt()), vaultId: Long = 1): File {
		return copies.vault(vaultId).store(ciphertext) { part -> part.writeText(content) }
	}

	private fun vaultFolder(vaultId: Long = 1) = File(directory, vaultId.toString())

	private fun copiesOnDisk(vaultId: Long = 1) = vaultFolder(vaultId).listFiles()?.map { it.name }?.sorted() ?: emptyList()

	@Test
	fun aKeptFileIsFoundAndReadBack() {
		val copy = keep(file, "hello world")

		assertTrue(vault1.isKept(file))
		assertEquals(copy, vault1.find(file))
		assertEquals("hello world", copy.readText())
		assertEquals(vaultFolder(), copy.parentFile)
		assertEquals(listOf(copy.name, copy.name + ".meta"), copiesOnDisk())
	}

	@Test
	fun aFileThatWasNeverKeptIsNotFound() {
		assertFalse(vault1.isKept(file))
		assertNull(vault1.find(file))
	}

	@Test
	fun aChangedSizeOrDateMakesTheCopyStaleAndDeletesIt() {
		keep(file)

		assertFalse(vault1.isKept(file(d, "a.c9r", 12, 1000)))
		assertFalse(vault1.isKept(file))
		awaitEmpty(vaultFolder())

		keep(file)
		assertNull(vault1.find(file(d, "a.c9r", 11, 2000)))
		assertEquals(emptyList<String>(), copiesOnDisk())
	}

	@Test
	fun aLookupFromAnOlderListingDoesNotDropANewerCopy() {
		val newer = file(d, "a.c9r", 12, 2000)
		keep(newer)

		assertFalse(vault1.isKept(file))
		assertNull(vault1.find(file))
		assertTrue(vault1.isKept(newer))
		assertEquals(2, copiesOnDisk().size)
	}

	@Test
	fun aLookupWithoutSizeOrDateMatchesTheCopy() {
		keep(file)

		assertTrue(vault1.isKept(file(d, "a.c9r", null, null)))
		assertTrue(vault1.isKept(file(d, "a.c9r", 11, null)))
		assertTrue(vault1.isKept(file(d, "a.c9r", null, 1000)))
		assertNotNull(vault1.find(file(d, "a.c9r", null, null)))
		assertFalse(vault1.isKept(file(d, "a.c9r", 12, null)))
	}

	@Test
	fun aCopyThatVanishedFromDiskIsNotFound() {
		keep(file).delete()

		assertNull(vault1.find(file))
		assertFalse(vault1.isKept(file))
		assertEquals(emptyList<String>(), copiesOnDisk())
	}

	@Test
	fun aCopyWithAnotherLengthThanTheFileIsDropped() {
		keep(file, "too short")

		assertNull(vault1.find(file))
		assertEquals(emptyList<String>(), copiesOnDisk())
	}

	@Test
	fun copiesAreLoadedFromDisk() {
		val other = file(d, "b.c9r", 3, 1000)
		keep(file)
		keep(other, vaultId = 2)

		val reloaded = OfflineCopies(directory)

		assertTrue(reloaded.vault(1).isKept(file))
		assertTrue(reloaded.vault(2).isKept(other))
		assertFalse(reloaded.vault(2).isKept(file))
		assertEquals(vault1.find(file), reloaded.vault(1).find(file))
		assertFalse(reloaded.vault(1).isKept(file(d, "a.c9r", 11, 1001)))
	}

	@Test
	fun copiesWithoutMetadataLeftoverPartsAndOrphanedMetadataAreDroppedOnLoad() {
		val kept = keep(file)
		vaultFolder().mkdirs()
		File(vaultFolder(), "0123abcd").writeText("copy from before the metadata")
		File(vaultFolder(), "0123abcd1234.part").writeText("interrupted")
		File(vaultFolder(), "ffff.meta").writeText("/d/gone.c9r\n1\n1\n")
		File(vaultFolder(), "broken").writeText("copy")
		File(vaultFolder(), "broken.meta").writeText("")

		OfflineCopies(directory).also { it.vault(1).isKept(file) }

		assertEquals(listOf(kept.name, kept.name + ".meta"), copiesOnDisk())
	}

	@Test
	fun aFailedWriteLeavesNoCopyAndKeepsTheOldOne() {
		val old = keep(file, "old version")

		assertThrows(IOException::class.java) {
			vault1.store(file) { part ->
				part.writeText("half")
				throw IOException("connection lost")
			}
		}

		assertEquals(old, vault1.find(file))
		assertEquals("old version", old.readText())
		assertEquals(listOf(old.name, old.name + ".meta"), copiesOnDisk())
	}

	@Test
	fun removeDeletesTheCopyAndItsMetadata() {
		keep(file)

		vault1.remove(file)

		assertFalse(vault1.isKept(file))
		assertEquals(emptyList<String>(), copiesOnDisk())
	}

	@Test
	fun removeBelowDropsTheCopiesInTheFolderOnly() {
		val e = folder("/e")
		val inD = file(d, "b.c9r", 2, 1000)
		val inE = file(e, "c.c9r", 2, 1000)
		keep(file)
		keep(inD)
		keep(inE)

		vault1.removeBelow(d)

		assertFalse(vault1.isKept(file))
		assertFalse(vault1.isKept(inD))
		assertTrue(vault1.isKept(inE))
		assertEquals(2, copiesOnDisk().size)
	}

	@Test
	fun theCopyFollowsAMovedFile() {
		val moved = file(folder("/e"), "z.c9r", 11, 1000)
		keep(file, "hello world")

		vault1.move(file, moved)

		assertFalse(vault1.isKept(file))
		assertTrue(vault1.isKept(moved))
		assertEquals("hello world", vault1.find(moved)!!.readText())
		assertEquals(2, copiesOnDisk().size)
		assertTrue(OfflineCopies(directory).vault(1).isKept(moved))
	}

	@Test
	fun aMoveReportedWithoutSizeAndDateKeepsTheCopysOwn() {
		val listed = file(folder("/e"), "z.c9r", 11, 1000)
		keep(file, "hello world")

		vault1.move(file, file(folder("/e"), "z.c9r", null, null))

		assertTrue(vault1.isKept(listed))
		assertEquals("hello world", vault1.find(listed)!!.readText())
		assertTrue(OfflineCopies(directory).vault(1).isKept(listed))
	}

	@Test
	fun movingAFileThatIsNotKeptDoesNothing() {
		vault1.move(file, file(d, "z.c9r", 11, 1000))

		assertEquals(emptyList<String>(), copiesOnDisk())
	}

	@Test
	fun replaceKeepsTheNewCiphertextOfAKeptFile() {
		val written = file(d, "a.c9r", 7, 3000)
		val ciphertext = File(tmpDir, "upload").also { it.writeText("new one") }
		keep(file, "old version")

		vault1.replace(file, written, ciphertext)

		assertFalse(vault1.isKept(file))
		assertTrue(vault1.isKept(written))
		assertEquals("new one", vault1.find(written)!!.readText())
		assertEquals(2, copiesOnDisk().size)
	}

	@Test
	fun replaceIgnoresAFileThatIsNotKept() {
		val ciphertext = File(tmpDir, "upload").also { it.writeText("new one") }

		vault1.replace(file, file(d, "a.c9r", 7, 3000), ciphertext)

		assertEquals(emptyList<String>(), copiesOnDisk())
	}

	@Test
	fun replaceThatFailsDropsTheStaleCopy() {
		keep(file)

		assertThrows(IOException::class.java) { vault1.replace(file, file(d, "a.c9r", 7, 3000), File(tmpDir, "missing")) }

		assertFalse(vault1.isKept(file))
		assertEquals(emptyList<String>(), copiesOnDisk())
	}

	@Test
	fun deleteVaultDropsThatVaultOnly() {
		val other = file(d, "b.c9r", 3, 1000)
		keep(file, vaultId = 1)
		keep(other, vaultId = 2)

		copies.deleteVault(1)

		assertFalse(vault1.isKept(file))
		assertTrue(copies.vault(2).isKept(other))
		assertFalse(vaultFolder(1).exists())
		assertEquals(2, copiesOnDisk(2).size)
	}

	@Test
	fun usageCountsBytesAndFilesPerVault() {
		keep(file, vaultId = 1)
		keep(file(d, "b.c9r", 5, 1000), vaultId = 1)
		keep(file(d, "c.c9r", 7, 1000), vaultId = 2)

		val usage = copies.usage().sortedBy { it.vaultId }

		assertEquals(listOf(1L, 2L), usage.map { it.vaultId })
		assertEquals(listOf(16L, 7L), usage.map { it.bytes })
		assertEquals(listOf(2, 1), usage.map { it.files })
		assertEquals(emptyList<Long>(), OfflineCopies(File(tmpDir, "empty")).usage().map { it.vaultId })
	}

	@Test
	fun aFileOfUnknownSizeRecordsTheCopysLength() {
		val unsized = file(d, "u.c9r", null, 1000)

		keep(unsized, "hello world")

		assertEquals(11L, copies.usage().single().bytes)
		assertTrue(vault1.isKept(unsized))
		assertTrue(vault1.isKept(file(d, "u.c9r", 11, 1000)))
		assertNotNull(vault1.find(unsized))
	}

	@Test
	fun deleteAllDropsEveryVault() {
		keep(file, vaultId = 1)
		keep(file(d, "c.c9r", 7, 1000), vaultId = 2)

		copies.deleteAll()

		assertFalse(vault1.isKept(file))
		assertEquals(emptyList<String>(), directory.listFiles()?.map { it.name } ?: emptyList<String>())
		assertEquals(0, copies.usage().size)
	}

	@Test
	fun theSamePathInTwoVaultsIsTwoCopies() {
		keep(file, "first vault", vaultId = 1)
		keep(file, "other vault", vaultId = 2)

		assertEquals("first vault", vault1.find(file)!!.readText())
		assertEquals("other vault", copies.vault(2).find(file)!!.readText())
		assertEquals(listOf(1L, 2L), copies.usage().map { it.vaultId }.sorted())

		copies.deleteVault(2)

		assertTrue(vault1.isKept(file))
		assertFalse(copies.vault(2).isKept(file))
		assertTrue(OfflineCopies(directory).vault(1).isKept(file))
		assertFalse(OfflineCopies(directory).vault(2).isKept(file))
	}

	@Test
	fun aFolderThatIsNotAVaultIdIsIgnoredOnLoad() {
		keep(file)
		File(directory, "stray").mkdirs()
		File(directory, "stray/" + vault1.find(file)!!.name).writeText("copy")

		val reloaded = OfflineCopies(directory)

		assertTrue(reloaded.vault(1).isKept(file))
		assertEquals(listOf(1L), reloaded.usage().map { it.vaultId })
	}

	@Test
	fun withoutStorageNothingIsKept() {
		val disabled = OfflineCopies(null)

		assertFalse(disabled.vault(1).isKept(file))
		assertNull(disabled.vault(1).find(file))
		assertThrows(IOException::class.java) { disabled.vault(1).store(file) { } }
		disabled.vault(1).remove(file)
		disabled.deleteVault(1)
		disabled.deleteAll()
		assertEquals(0, disabled.usage().size)
	}

	@Test
	fun parallelKeepsOfTheSameFileLeaveOneCompleteCopy() {
		val threads = 4
		val start = CountDownLatch(1)
		val executor = Executors.newFixedThreadPool(threads)
		val results = (1..threads).map {
			executor.submit<File> {
				start.await()
				keep(file, "hello world")
			}
		}
		start.countDown()
		results.forEach { it.get(10, TimeUnit.SECONDS) }
		executor.shutdown()

		val copy = vault1.find(file)
		assertNotNull(copy)
		assertEquals("hello world", copy!!.readText())
		assertEquals(listOf(copy.name, copy.name + ".meta"), copiesOnDisk())
	}

	@Test
	fun theNameOfACopyDependsOnThePathOnly() {
		val renamed = file(d, "a.c9r", 12, 2000)
		val first = keep(file)
		vault1.remove(file)
		val second = keep(renamed)

		assertEquals(first.name, second.name)
		assertNotEquals(first.name, keep(file(d, "b.c9r", 11, 1000)).name)
	}

	private fun assertNotEquals(unexpected: Any, actual: Any) {
		assertTrue(unexpected != actual)
	}

	private fun awaitEmpty(folder: File) {
		val deadline = System.currentTimeMillis() + 5000
		while (folder.listFiles()?.isNotEmpty() == true && System.currentTimeMillis() < deadline) {
			Thread.sleep(10)
		}
		assertEquals(emptyList<String>(), folder.listFiles()?.map { it.name } ?: emptyList<String>())
	}

	private fun folder(path: String): CloudFolder = object : CloudFolder {
		override val cloud: Cloud? = null
		override val name: String = path.substringAfterLast('/')
		override val path: String = path
		override val parent: CloudFolder? = null
		override fun withCloud(cloud: Cloud?): CloudFolder = this
	}

	private fun file(parent: CloudFolder, name: String, size: Long?, modified: Long?): CloudFile = object : CloudFile {
		override val cloud: Cloud? = null
		override val name: String = name
		override val path: String = parent.path + "/" + name
		override val parent: CloudFolder = parent
		override val size: Long? = size
		override val modified: Date? = modified?.let { Date(it) }
	}
}
