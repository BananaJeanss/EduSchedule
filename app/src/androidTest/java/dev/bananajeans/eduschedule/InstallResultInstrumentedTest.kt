package dev.bananajeans.eduschedule

import android.app.Instrumentation
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InstallResultInstrumentedTest {
    @Test fun pendingUserActionFromInstallerCallbackOpensConfirmationWhileAppIsVisible() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        ActivityScenario.launch(MainActivity::class.java).use {
            val monitor = Instrumentation.ActivityMonitor(MainActivity::class.java.name, null, false)
            instrumentation.addMonitor(monitor)
            try {
                val status = UpdateInstaller.statusReceiver(context, 901_234, "test")
                assertTrue("PackageInstaller callback must be a broadcast", status.isBroadcast)
                val confirmation = Intent(context, MainActivity::class.java)
                    .putExtra("installer_confirmation_test", true)
                val callback = Intent()
                    .putExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_PENDING_USER_ACTION)
                    .putExtra(Intent.EXTRA_INTENT, confirmation)
                status.send(context, 0, callback)

                val launched = instrumentation.waitForMonitorWithTimeout(monitor, 10_000)
                assertNotNull("Confirmation was not opened from the visible Activity", launched)
                assertTrue(launched!!.intent.getBooleanExtra("installer_confirmation_test", false))
            } finally {
                instrumentation.removeMonitor(monitor)
            }
        }
    }
}
