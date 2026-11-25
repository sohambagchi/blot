package app.blot.core

import org.junit.Assert.assertTrue
import org.junit.Test

class VersionTest {
    @Test
    fun formatVersionIsPositive() {
        assertTrue(FORMAT_VERSION > 0)
    }
}
