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

	private fun keep(ciphertext: CloudFile, content: String = "x".repeat(ciphertext.size!!.toInt()), vaultId: Long = 1): File {
		return copies.store(vaultId, ciphertext) { part -> part.writeText(content) }
	}

	private fun vaultFolder(vaultId: Long = 1) = File(directory, vaultId.toString())

	private fun copiesOnDisk(vaultId: Long = 1) = vaultFolder(vaultId).listFiles()?.map { it.name }?.sorted() ?: emptyList()

	@Test
	fun aKeptFileIsFoundAndReadBack() {
		val copy = keep(file, "hello world")

		assertTrue(copies.isKept(file))
		assertEquals(copy, copies.find(file))
		assertEquals("hello world", copy.readText())
		assertEquals(vaultFolder(), copy.parentFile)
		assertEquals(listOf(copy.name, copy.name + ".meta"), copiesOnDisk())
	}

	@Test
	fun aFileThatWasNeverKeptIsNotFound() {
		assertFalse(copies.isKept(file))
		assertNull(copies.find(file))
	}

	@Test
	fun aChangedSizeOrDateMakesTheCopyStaleAndDeletesIt() {
		keep(file)

		assertFalse(copies.isKept(file(d, "a.c9r", 12, 1000)))
		assertFalse(copies.isKept(file))
		awaitEmpty(vaultFolder())

		keep(file)
		assertNull(copies.find(file(d, "a.c9r", 11, 2000)))
		assertEquals(emptyList<String>(), copiesOnDisk())
	}

	@Test
	fun aLookupFromAnOlderListingDoesNotDropANewerCopy() {
		val newer = file(d, "a.c9r", 12, 2000)
		keep(newer)

		assertFalse(copies.isKept(file))
		assertNull(copies.find(file))
		assertTrue(copies.isKept(newer))
		assertEquals(2, copiesOnDisk().size)
	}

	@Test
	fun aCopyThatVanishedFromDiskIsNotFound() {
		keep(file).delete()

		assertNull(copies.find(file))
		assertFalse(copies.isKept(file))
		assertEquals(emptyList<String>(), copiesOnDisk())
	}

	@Test
	fun aCopyWithAnotherLengthThanTheFileIsDropped() {
		keep(file, "too short")

		assertNull(copies.find(file))
		assertEquals(emptyList<String>(), copiesOnDisk())
	}

	@Test
	fun copiesAreLoadedFromDisk() {
		val other = file(d, "b.c9r", 3, 1000)
		keep(file)
		keep(other, vaultId = 2)

		val reloaded = OfflineCopies(directory)

		assertTrue(reloaded.isKept(file))
		assertTrue(reloaded.isKept(other))
		assertEquals(copies.find(file), reloaded.find(file))
		assertFalse(reloaded.isKept(file(d, "a.c9r", 11, 1001)))
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

		OfflineCopies(directory).also { it.isKept(file) }

		assertEquals(listOf(kept.name, kept.name + ".meta"), copiesOnDisk())
	}

	@Test
	fun aFailedWriteLeavesNoCopyAndKeepsTheOldOne() {
		val old = keep(file, "old version")

		assertThrows(IOException::class.java) {
			copies.store(1, file) { part ->
				part.writeText("half")
				throw IOException("connection lost")
			}
		}

		assertEquals(old, copies.find(file))
		assertEquals("old version", old.readText())
		assertEquals(listOf(old.name, old.name + ".meta"), copiesOnDisk())
	}

	@Test
	fun removeDeletesTheCopyAndItsMetadata() {
		keep(file)

		copies.remove(file)

		assertFalse(copies.isKept(file))
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

		copies.removeBelow(d)

		assertFalse(copies.isKept(file))
		assertFalse(copies.isKept(inD))
		assertTrue(copies.isKept(inE))
		assertEquals(2, copiesOnDisk().size)
	}

	@Test
	fun theCopyFollowsAMovedFile() {
		val moved = file(folder("/e"), "z.c9r", 11, 1000)
		keep(file, "hello world")

		copies.move(file, moved)

		assertFalse(copies.isKept(file))
		assertTrue(copies.isKept(moved))
		assertEquals("hello world", copies.find(moved)!!.readText())
		assertEquals(2, copiesOnDisk().size)
		assertTrue(OfflineCopies(directory).isKept(moved))
	}

	@Test
	fun aMoveReportedWithoutSizeAndDateKeepsTheCopysOwn() {
		val listed = file(folder("/e"), "z.c9r", 11, 1000)
		keep(file, "hello world")

		copies.move(file, file(folder("/e"), "z.c9r", null, null))

		assertTrue(copies.isKept(listed))
		assertEquals("hello world", copies.find(listed)!!.readText())
		assertTrue(OfflineCopies(directory).isKept(listed))
	}

	@Test
	fun movingAFileThatIsNotKeptDoesNothing() {
		copies.move(file, file(d, "z.c9r", 11, 1000))

		assertEquals(emptyList<String>(), copiesOnDisk())
	}

	@Test
	fun replaceKeepsTheNewCiphertextOfAKeptFile() {
		val written = file(d, "a.c9r", 7, 3000)
		val ciphertext = File(tmpDir, "upload").also { it.writeText("new one") }
		keep(file, "old version")

		copies.replace(file, written, ciphertext)

		assertFalse(copies.isKept(file))
		assertTrue(copies.isKept(written))
		assertEquals("new one", copies.find(written)!!.readText())
		assertEquals(2, copiesOnDisk().size)
	}

	@Test
	fun replaceIgnoresAFileThatIsNotKept() {
		val ciphertext = File(tmpDir, "upload").also { it.writeText("new one") }

		copies.replace(file, file(d, "a.c9r", 7, 3000), ciphertext)

		assertEquals(emptyList<String>(), copiesOnDisk())
	}

	@Test
	fun replaceThatFailsDropsTheStaleCopy() {
		keep(file)

		assertThrows(IOException::class.java) { copies.replace(file, file(d, "a.c9r", 7, 3000), File(tmpDir, "missing")) }

		assertFalse(copies.isKept(file))
		assertEquals(emptyList<String>(), copiesOnDisk())
	}

	@Test
	fun deleteVaultDropsThatVaultOnly() {
		val other = file(d, "b.c9r", 3, 1000)
		keep(file, vaultId = 1)
		keep(other, vaultId = 2)

		copies.deleteVault(1)

		assertFalse(copies.isKept(file))
		assertTrue(copies.isKept(other))
		assertFalse(vaultFolder(1).exists())
		assertEquals(2, copiesOnDisk(2).size)
	}

	@Test
	fun withoutStorageNothingIsKept() {
		val disabled = OfflineCopies(null)

		assertFalse(disabled.isKept(file))
		assertNull(disabled.find(file))
		assertThrows(IOException::class.java) { disabled.store(1, file) { } }
		disabled.remove(file)
		disabled.deleteVault(1)
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

		val copy = copies.find(file)
		assertNotNull(copy)
		assertEquals("hello world", copy!!.readText())
		assertEquals(listOf(copy.name, copy.name + ".meta"), copiesOnDisk())
	}

	@Test
	fun theNameOfACopyDependsOnThePathOnly() {
		val renamed = file(d, "a.c9r", 12, 2000)
		val first = keep(file)
		copies.remove(file)
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
