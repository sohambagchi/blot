package app.blot.net

import android.security.NetworkSecurityPolicy
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
/**
 * depot's client sets the network security config: cleartext is refused except to hosts named
 * as `http://` in DEPOT_URLS or in DEPOT_CLEARTEXT_HOSTS. Pass those as `-e cleartextHosts a,b`.
 */
class CleartextPolicyTest {
    @Test
    fun cleartextIsRefusedExceptToNamedHosts() {
        val policy = NetworkSecurityPolicy.getInstance()
        assertFalse(policy.isCleartextTrafficPermitted)
        assertFalse(policy.isCleartextTrafficPermitted("example.com"))
        val hosts = InstrumentationRegistry.getArguments().getString("cleartextHosts").orEmpty()
            .split(',').filter { it.isNotBlank() }
        for (host in hosts) assertTrue(host, policy.isCleartextTrafficPermitted(host))
    }
}
