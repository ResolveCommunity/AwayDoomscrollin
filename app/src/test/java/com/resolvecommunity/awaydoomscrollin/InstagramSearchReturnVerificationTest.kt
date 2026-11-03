package com.resolvecommunity.awaydoomscrollin

import org.junit.Assert.*
import org.junit.Test

class InstagramSearchReturnVerificationTest {
    @Test fun closingKeyboardKeepsCheckingUntilTheClosingAnimationFinishes() {
        val verification = InstagramSearchReturnVerification()
        verification.begin(true, 100)
        assertTrue(verification.needsAnotherObservation(InstagramSearchSurface.SEARCH, true, 120))
        assertTrue(verification.needsAnotherObservation(InstagramSearchSurface.SEARCH, true, 300))
        assertFalse(verification.needsAnotherObservation(InstagramSearchSurface.SEARCH, false, 400))
        assertFalse(verification.needsAnotherObservation(InstagramSearchSurface.SEARCH, true, 500))
    }
    @Test fun returningToExploreDoesNotStopAtTheOldSearchFrame() {
        val verification = InstagramSearchReturnVerification()
        verification.begin(false, 0)
        assertTrue(verification.needsAnotherObservation(InstagramSearchSurface.SEARCH, false, 50))
        assertFalse(verification.needsAnotherObservation(InstagramSearchSurface.EXPLORE, false, 150))
    }
    @Test fun missingFinalEventsCannotStartAnEndlessPollingLoop() {
        val verification = InstagramSearchReturnVerification(500)
        verification.begin(true, 0)
        assertTrue(verification.needsAnotherObservation(InstagramSearchSurface.UNKNOWN, true, 499))
        assertFalse(verification.needsAnotherObservation(InstagramSearchSurface.UNKNOWN, true, 500))
        assertFalse(verification.needsAnotherObservation(InstagramSearchSurface.UNKNOWN, true, 501))
    }
    @Test fun anotherDestinationOrCancellationStopsVerification() {
        val verification = InstagramSearchReturnVerification()
        for (surface in listOf(InstagramSearchSurface.OTHER, InstagramSearchSurface.HOME, InstagramSearchSurface.INBOX)) {
            verification.begin(false, 0)
            assertFalse(verification.needsAnotherObservation(surface, false, 10))
        }
        verification.begin(true, 20)
        verification.clear()
        assertFalse(verification.needsAnotherObservation(InstagramSearchSurface.SEARCH, true, 30))
    }
}
