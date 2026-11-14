package app.cleanroom.streamlab

import org.junit.Assert.assertEquals
import org.junit.Test

class PassengerSectionRequestTest {
    @Test fun initialSectionIsValidated() {
        assertEquals("Videos" to 0, PassengerSectionRequest.initial("Videos"))
        assertEquals("Apps" to 0, PassengerSectionRequest.initial(null))
        assertEquals("Apps" to 0, PassengerSectionRequest.initial("Unknown"))
    }

    @Test fun everyPassengerIntentAdvancesNavigationEvenForTheSameSection() {
        val videos = PassengerSectionRequest.next("Apps" to 0, "Videos")
        assertEquals("Videos" to 1, videos)
        assertEquals("Favorites" to 2, PassengerSectionRequest.next(videos, "Favorites"))
        val again = PassengerSectionRequest.next("Favorites" to 2, "Favorites")
        assertEquals("Favorites" to 3, again)
    }

    @Test fun invalidIntentCannotMoveOrResetTheCurrentScreen() {
        val current = "Favorites" to 5
        assertEquals(current, PassengerSectionRequest.next(current, null))
        assertEquals(current, PassengerSectionRequest.next(current, "Danger"))
    }
}
