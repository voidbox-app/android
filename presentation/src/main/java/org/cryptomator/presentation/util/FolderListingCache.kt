package org.cryptomator.presentation.util

import org.cryptomator.data.cloud.crypto.CryptoCloud
import org.cryptomator.data.cloud.crypto.Cryptors
import org.cryptomator.domain.Vault
import org.cryptomator.presentation.model.CloudFolderModel
import org.cryptomator.presentation.model.CloudNodeModel
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The last listing shown for each vault folder, kept in memory only. Opening a folder again shows
 * this at once while the fresh listing loads behind it. Entries vanish with the vault's cryptor,
 * so nothing outlives a lock, and a folder that was changed from the app is dropped so the next
 * visit loads it anew.
 */
@Singleton
class FolderListingCache @Inject constructor(private val cryptors: Cryptors) {

	private val listings = ConcurrentHashMap<String, List<CloudNodeModel<*>>>()
	private val knownVaults = ConcurrentHashMap<Long, Vault>()

	fun get(folder: CloudFolderModel): List<CloudNodeModel<*>>? = key(folder)?.let { listings[it] }

	fun put(folder: CloudFolderModel, nodes: List<CloudNodeModel<*>>) {
		key(folder)?.let { listings[it] = ArrayList(nodes) }
	}

	/** The folder was changed from the app; forget its listing so the next visit loads it. */
	fun invalidate(folder: CloudFolderModel?) {
		folder?.let { f -> key(f)?.let { listings.remove(it) } }
	}

	fun clear() {
		listings.clear()
		knownVaults.clear()
	}

	fun evictLockedVaults() {
		val locked = knownVaults.values.filter { !isUnlocked(it) }.map { it.id }
		if (locked.isEmpty()) {
			return
		}
		listings.keys.filter { key -> locked.any { key.startsWith("$it/") } }.forEach { listings.remove(it) }
		locked.forEach { knownVaults.remove(it) }
	}

	private fun isUnlocked(vault: Vault): Boolean = try {
		cryptors[vault].get()
		true
	} catch (e: Exception) {
		false
	}

	private fun key(folder: CloudFolderModel): String? {
		val vault = (folder.toCloudNode().cloud as? CryptoCloud)?.vault ?: return null
		knownVaults[vault.id] = vault
		return "${vault.id}/${folder.path}"
	}
}
