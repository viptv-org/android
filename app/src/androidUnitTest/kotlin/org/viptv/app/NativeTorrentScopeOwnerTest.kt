package org.viptv.app

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

class NativeTorrentScopeOwnerTest {
    private fun facts(profile: String = "profile_fixture", epoch: String = "epoch_fixture") = JSONObject()
        .put("serverOrigin", "https://fixture.invalid").put("accountId", "account_fixture")
        .put("profileId", profile).put("deviceAuthorizationEpoch", epoch)

    private fun owner(events: MutableList<String>): NativeTorrentScopeOwner {
        val root = Files.createTempDirectory("native-scope-owner").toFile()
        return NativeTorrentScopeOwner({
            NativeTorrentCache.open(root, { _, _ ->
                events.add("manager.open")
                object : NativeTorrentCacheManager {
                    override fun hasFailedSettlement() = false
                    override fun closeAfterSettlement() { events.add("manager.close") }
                }
            })
        }, { cache -> NativeTorrentCoordinator(cache, System::nanoTime, { true }, { events.add("player.reads.off") },
            main = Dispatchers.Unconfined, io = Dispatchers.IO) })
    }

    @Test fun sameAuthorityKeepsRandomEpochAndProfileReplacementClosesBeforeOpening() = runBlocking {
        val events = mutableListOf<String>()
        val owner = owner(events)
        val first = requireNotNull(owner.adopt(facts()))
        assertTrue(owner.adopt(facts()) === first)
        assertEquals(1, events.count { it == "manager.open" })
        assertFalse(first.scope.contains("account_fixture"))
        val next = requireNotNull(owner.adopt(facts(profile = "other_profile")))
        assertNotEquals(first.scope, next.scope)
        assertTrue(events.indexOf("manager.close") < events.lastIndexOf("manager.open"))
        assertFalse(first.coordinator.cache.isAvailable)
        assertNull(owner.adopt(null))
        assertEquals(2, events.count { it == "manager.close" })
    }

    @Test fun revocationAndMalformedAuthorityDisposeBeforeAnyNewAdmission() = runBlocking {
        for (revoked in listOf(true, false)) {
            val events = mutableListOf<String>()
            val owner = owner(events)
            val first = requireNotNull(owner.adopt(facts()))
            val next = if (revoked) facts() else facts().put("accessToken", "synthetic_secret")
            assertNull(owner.adopt(next, revoked))
            assertFalse(first.coordinator.cache.isAvailable)
            assertEquals(1, events.count { it == "manager.open" })
            assertEquals(1, events.count { it == "manager.close" })
        }
    }

    @Test fun failedScopeSettlementRetainsOwnerAndPreventsReplacementForever() = runBlocking {
        val events = mutableListOf<String>()
        val owner = owner(events)
        val first = requireNotNull(owner.adopt(facts()))
        val work = object : NativeTorrentCacheWork {
            override fun preventReads() { events.add("work.reads.off") }
            override fun cancel() { events.add("work.cancel") }
            override fun join(deadlineNanos: Long) = false
            override fun closeAfterSettlement() { throw AssertionError("Unsettled work disposed") }
        }
        first.coordinator.cache.register(work, first.coordinator.cache.reserveControl(1))
        assertFailsWith<NativeTorrentCoordinatorUnavailable> { owner.adopt(facts(epoch = "other_epoch")) }
        assertFailsWith<NativeTorrentCoordinatorUnavailable> { owner.adopt(facts()) }
        assertFalse(first.coordinator.cache.isAvailable)
        assertEquals(1L, first.coordinator.cache.reservedControlBytes)
        assertEquals(1, events.count { it == "manager.open" })
        assertEquals(0, events.count { it == "manager.close" })
    }
}
