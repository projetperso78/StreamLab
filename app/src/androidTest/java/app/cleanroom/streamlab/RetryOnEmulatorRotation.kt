package app.cleanroom.streamlab

import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * One extra attempt for tests that rotate the emulator. On the API 36 emulator a forced
 * rotation sometimes leaves the main looper busy forever (AppNotIdleException / timeout),
 * a known emulator rendering issue seen before any related app change. Real devices are
 * checked by hand. Only rotation tests use this rule; a test failing twice still fails.
 */
class RetryOnEmulatorRotation(private val attempts: Int = 2) : TestRule {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            var last: Throwable? = null
            repeat(attempts) { attempt ->
                try {
                    base.evaluate()
                    return
                } catch (t: Throwable) {
                    last = t
                    android.util.Log.w("RetryOnEmulatorRotation", "${description.methodName} attempt ${attempt + 1} failed: ${t.javaClass.simpleName}")
                }
            }
            throw last!!
        }
    }
}
