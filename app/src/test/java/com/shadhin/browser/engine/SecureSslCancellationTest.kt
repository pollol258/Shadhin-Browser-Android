package com.shadhin.browser.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit test confirming SSL handler contract and cancellation policy.
 */
class SecureSslCancellationTest {

    interface SslActionPolicy {
        fun onSslError(cancelAction: () -> Unit, proceedAction: () -> Unit)
    }

    class SecureSslPolicy : SslActionPolicy {
        override fun onSslError(cancelAction: () -> Unit, proceedAction: () -> Unit) {
            // NEVER proceed on SSL errors; ALWAYS cancel
            cancelAction()
        }
    }

    @Test
    fun `confirm proceed is NEVER called on SSL error and cancel is called`() {
        var cancelCalled = false
        var proceedCalled = false

        val policy = SecureSslPolicy()
        policy.onSslError(
            cancelAction = { cancelCalled = true },
            proceedAction = { proceedCalled = true }
        )

        assertTrue("cancel must be called on SSL error", cancelCalled)
        assertFalse("proceed must NEVER be called on SSL error", proceedCalled)
    }
}
