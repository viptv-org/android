package org.viptv.app

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceTimeBy
import org.viptv.core.wire.Account
import org.viptv.core.wire.Identity
import org.viptv.core.wire.Profile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class ForegroundValidationTest {
    private fun identity(account: String = "account-one", profile: String = "profile-one") =
        Identity(Account(account, "fixture", "Fixture", "member"),
            listOf(Profile(id = profile, name = "One", kid = false, setupComplete = true)), profile, false, false)

    @Test fun `same profile gaining child restrictions cannot keep cached adult presentation`() = runTest {
        val previous = identity()
        for (current in listOf(
            previous.copy(profiles = previous.profiles.map { it.copy(kid = true) }, restricted = true),
            previous.copy(account = previous.account.copy(role = "owner")),
            previous.copy(profileSetupRequired = true, profiles = previous.profiles.map { it.copy(setupComplete = false) }),
        )) {
            val lifecycle = ForegroundValidation(this) { current }
            lifecycle.onForeground(previous, "profile-one"); runCurrent()
            assertEquals(ForegroundValidationResult.ProfileUnavailable(current), lifecycle.result.value)
            lifecycle.close()
        }
    }
    @Test fun `return validates once silently and preserves matching authorization`() = runTest {
        val response = CompletableDeferred<Identity>()
        var requests = 0
        val lifecycle = ForegroundValidation(this) { requests++; response.await() }
        lifecycle.onForeground(identity(), "profile-one")
        lifecycle.onForeground(identity(), "profile-one")
        runCurrent()
        assertEquals(1, requests)
        assertNull(lifecycle.result.value)
        response.complete(identity())
        runCurrent()
        assertEquals(ForegroundValidationResult.Valid(identity()), lifecycle.result.value)
        lifecycle.close()
    }
    @Test fun `verified profile display facts are returned without treating them as lost authority`() = runTest {
        val previous = identity()
        val current = previous.copy(profiles = previous.profiles.map { it.copy(name = "Updated name", avatarChoice = 2.0) })
        val lifecycle = ForegroundValidation(this) { current }
        lifecycle.onForeground(previous, "profile-one"); runCurrent()
        assertEquals(ForegroundValidationResult.Valid(current), lifecycle.result.value)
        lifecycle.close()
    }
    @Test fun `offline return fails after thirty seconds and retry reuses the grant`() = runTest {
        var offline = true
        var requests = 0
        val lifecycle = ForegroundValidation(this) {
            requests++
            if (offline) awaitCancellation()
            identity()
        }
        lifecycle.onForeground(identity(), "profile-one")
        advanceTimeBy(29_999); runCurrent()
        assertNull(lifecycle.result.value)
        advanceTimeBy(1); runCurrent()
        assertEquals(ForegroundValidationResult.Failed("Could not reconnect to VIPTV. Try again."), lifecycle.result.value)
        offline = false
        lifecycle.onForeground(identity(), "profile-one"); runCurrent()
        assertEquals(ForegroundValidationResult.Valid(identity()), lifecycle.result.value)
        assertEquals(2, requests)
        lifecycle.close()
    }
    @Test fun `background discards a late response while replacement authorization remains valid`() = runTest {
        val old = CompletableDeferred<Identity>()
        val replacement = identity("account-two", "profile-two")
        var requests = 0
        val lifecycle = ForegroundValidation(this) {
            if (++requests == 1) withContext(NonCancellable) { old.await() }
            else replacement
        }
        lifecycle.onForeground(identity(), "profile-one"); runCurrent()
        lifecycle.onBackground()
        lifecycle.onForeground(replacement, "profile-two"); runCurrent()
        old.complete(identity("account-wrong")); runCurrent()
        assertEquals(ForegroundValidationResult.Valid(replacement), lifecycle.result.value)
        lifecycle.close()
    }
    @Test fun `explicit rejection and changed account revoke while profile loss never selects a substitute`() = runTest {
        val replacement = identity(profile = "profile-two")
        for ((current, expected) in listOf(
            identity("account-wrong") to ForegroundValidationResult.Revoked,
            replacement to ForegroundValidationResult.ProfileUnavailable(replacement),
        )) {
            val lifecycle = ForegroundValidation(this) { current }
            lifecycle.onForeground(identity(), "profile-one"); runCurrent()
            assertEquals(expected, lifecycle.result.value)
            lifecycle.close()
        }
        val lifecycle = ForegroundValidation(this) { throw GatewayError(401, "Synthetic revoked session") }
        lifecycle.onForeground(identity(), "profile-one"); runCurrent()
        assertEquals(ForegroundValidationResult.Revoked, lifecycle.result.value)
        lifecycle.close()
    }
}
