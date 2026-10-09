package org.cryptomator.presentation.model

import java.io.Serializable

/** The offline copies of one vault; [vault] is null when the vault no longer exists but its copies do. */
class OfflineVaultModel(val vaultId: Long, val vault: VaultModel?, val bytes: Long, val files: Int) : Serializable {

	override fun equals(other: Any?): Boolean = other is OfflineVaultModel && other.vaultId == vaultId

	override fun hashCode(): Int = vaultId.hashCode()
}
