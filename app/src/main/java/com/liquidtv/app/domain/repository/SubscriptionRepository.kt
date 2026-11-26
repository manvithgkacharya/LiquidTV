package com.liquidtv.app.domain.repository

import com.liquidtv.app.domain.model.PlaylistInfo
import com.liquidtv.app.domain.model.Subscription
import com.liquidtv.app.domain.model.SubscriptionOrigin
import kotlinx.coroutines.flow.Flow

interface SubscriptionRepository {
    val allSubscriptions: Flow<List<Subscription>>
    val activeSubscription: Flow<Subscription?>
    val syncInfo: Flow<PlaylistInfo>

    suspend fun getActiveSubscriptionOnce(): Subscription?
    suspend fun getSubscriptionById(id: String): Subscription?
    suspend fun hasAnySubscription(): Boolean

    suspend fun addOrUpdateXtreamSubscription(
        existingId: String? = null,
        displayName: String,
        serverUrl: String,
        username: String,
        password: String,
        origin: SubscriptionOrigin = SubscriptionOrigin.NORMAL_URL,
        panelServerId: String? = null,
        panelServerName: String? = null,
        activate: Boolean = true,
    ): Result<Subscription>

    suspend fun activateSubscription(subscriptionId: String): Result<Unit>
    suspend fun syncSubscription(subscriptionId: String): Result<Unit>
    suspend fun syncActiveSubscription(): Result<Unit>
    suspend fun syncActiveEpg(): Result<Unit>
    suspend fun deleteSubscription(subscriptionId: String): Result<Unit>

    /**
     * Returns decrypted password for an Xtream subscription (for internal API/player calls only).
     */
    suspend fun getDecryptedXtreamPassword(subscriptionId: String): String?
}
