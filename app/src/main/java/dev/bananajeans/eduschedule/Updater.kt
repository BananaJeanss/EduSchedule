package dev.bananajeans.eduschedule

import android.app.PendingIntent
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import android.Manifest
import java.lang.ref.WeakReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection

object UpdateInstaller {
    private const val MAX_APK_BYTES = 100L * 1024 * 1024
    private const val MAX_CHECKSUM_BYTES = 1024L * 1024
    private val redirectHosts = setOf(
        "release-assets.githubusercontent.com",
        "objects.githubusercontent.com",
        "github-releases.githubusercontent.com"
    )

    suspend fun downloadVerifyAndInstall(
        context: Context,
        release: AppRelease,
        onProgress: (Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        check(context.packageManager.canRequestPackageInstalls()) {
            "Allow installs from EduSchedule before downloading the update."
        }

        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        directory.listFiles()?.forEach { it.delete() }
        val apk = File(directory, release.apkName)
        val partial = File(directory, "${release.apkName}.part")

        try {
            val checksums = downloadText(Updates.assetUri(release, "SHA256SUMS"), MAX_CHECKSUM_BYTES)
            val expected = Updates.checksumFor(checksums, release.apkName)
                ?: error("Release checksum is missing or invalid.")

            downloadFile(Updates.assetUri(release, release.apkName), partial, MAX_APK_BYTES, onProgress)
            val actual = sha256(partial)
            check(actual.equals(expected, ignoreCase = true)) { "Downloaded update failed checksum verification." }
            check(partial.renameTo(apk)) { "Could not prepare the downloaded update." }

            verifyApk(context, apk, release)
            commit(context, apk, release)
        } finally {
            partial.delete()
            apk.delete()
        }
    }

    private fun downloadText(uri: URI, limit: Long): String {
        val connection = openReleaseConnection(uri)
        return try {
            val contentLength = connection.contentLengthLong
            check(contentLength < 0 || contentLength <= limit) { "Release metadata is too large." }
            connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    check(total <= limit) { "Release metadata is too large." }
                    output.write(buffer, 0, read)
                }
                output.toString(Charsets.UTF_8.name())
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadFile(uri: URI, file: File, limit: Long, onProgress: (Int) -> Unit) {
        val connection = openReleaseConnection(uri)
        try {
            val contentLength = connection.contentLengthLong
            check(contentLength < 0 || contentLength <= limit) { "Update APK is unexpectedly large." }
            file.outputStream().buffered().use { output ->
                connection.inputStream.use { input ->
                    val buffer = ByteArray(32 * 1024)
                    var total = 0L
                    var lastProgress = -1
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        check(total <= limit) { "Update APK is unexpectedly large." }
                        output.write(buffer, 0, read)
                        if (contentLength > 0) {
                            val progress = ((total * 100) / contentLength).toInt().coerceIn(0, 100)
                            if (progress != lastProgress) {
                                lastProgress = progress
                                onProgress(progress)
                            }
                        }
                    }
                }
            }
            onProgress(100)
        } finally {
            connection.disconnect()
        }
    }

    private fun openReleaseConnection(initial: URI): HttpsURLConnection {
        var uri = initial
        repeat(6) {
            validateReleaseUri(uri, initial = it == 0)
            val connection = uri.toURL().openConnection() as HttpsURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 45_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", "EduSchedule/${BuildConfig.VERSION_NAME} (self updater)")
            connection.setRequestProperty("Accept", "application/octet-stream")
            val code = connection.responseCode
            if (code in setOf(301, 302, 303, 307, 308)) {
                val location = connection.getHeaderField("Location")
                    ?: error("Release download redirect had no destination.")
                val next = uri.resolve(location)
                connection.disconnect()
                uri = next
            } else {
                check(code in 200..299) { "Release download returned $code." }
                return connection
            }
        }
        error("Too many release download redirects.")
    }

    private fun validateReleaseUri(uri: URI, initial: Boolean) {
        val host = uri.host?.lowercase().orEmpty()
        require(uri.scheme == "https" && uri.userInfo == null && uri.port == -1) {
            "Refusing an unsafe update download."
        }
        if (initial) {
            require(host == "github.com" &&
                uri.path.startsWith("/${Updates.REPOSITORY}/releases/download/")) {
                "Refusing an unexpected update source."
            }
        } else {
            require(host in redirectHosts) { "Refusing an unexpected update redirect." }
        }
    }

    private fun verifyApk(context: Context, apk: File, release: AppRelease) {
        val manager = context.packageManager
        val archive = archiveInfo(manager, apk)
            ?: error("Downloaded update is not a readable APK.")
        val installed = installedInfo(manager, context.packageName)

        check(archive.packageName == context.packageName) { "Update package name does not match EduSchedule." }
        check(archive.versionName == release.plainVersion) { "Update APK version does not match the release." }
        check(longVersionCode(archive) > longVersionCode(installed)) { "Update APK is not newer than the installed app." }

        val archiveSigners = signerDigests(archive)
        val installedSigners = signerDigests(installed)
        check(archiveSigners.isNotEmpty() && archiveSigners == installedSigners) {
            "Update APK signing certificate does not match the installed app."
        }
    }

    @Suppress("DEPRECATION")
    private fun archiveInfo(manager: PackageManager, apk: File): PackageInfo? {
        val flags = if (Build.VERSION.SDK_INT >= 28) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        return if (Build.VERSION.SDK_INT >= 33) {
            manager.getPackageArchiveInfo(apk.absolutePath, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            manager.getPackageArchiveInfo(apk.absolutePath, flags)
        }
    }

    @Suppress("DEPRECATION")
    private fun installedInfo(manager: PackageManager, packageName: String): PackageInfo {
        val flags = if (Build.VERSION.SDK_INT >= 28) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        return if (Build.VERSION.SDK_INT >= 33) {
            manager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            manager.getPackageInfo(packageName, flags)
        }
    }

    @Suppress("DEPRECATION")
    private fun signerDigests(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= 28) {
            info.signingInfo?.apkContentsSigners.orEmpty().toList()
        } else {
            info.signatures.orEmpty().toList()
        }
        return signatures.map { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }.toSet()
    }

    @Suppress("DEPRECATION")
    private fun longVersionCode(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(32 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun commit(context: Context, apk: File, release: AppRelease) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= 31) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
            }
            if (Build.VERSION.SDK_INT >= 33) {
                setPackageSource(PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE)
            }
        }
        val sessionId = installer.createSession(params)
        val session = installer.openSession(sessionId)
        try {
            apk.inputStream().buffered().use { input ->
                session.openWrite("base.apk", 0, apk.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }
            session.commit(statusReceiver(context, sessionId, release.version).intentSender)
        } catch (e: Exception) {
            session.abandon()
            throw e
        } finally {
            session.close()
        }
    }

    /** The OS delivers status via a broadcast, even when an Activity launch is restricted. */
    internal fun statusReceiver(context: Context, sessionId: Int, version: String): PendingIntent {
        val intent = Intent(context, InstallResultReceiver::class.java)
            .putExtra(InstallResultActivity.EXTRA_VERSION, version)
            .putExtra(PackageInstaller.EXTRA_SESSION_ID, sessionId)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        return PendingIntent.getBroadcast(context, sessionId, intent, flags)
    }
}

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        InstallResultRouter.handle(context, intent)
    }
}

/** Keep the installer callback independent of background Activity launch permissions. */
internal object InstallResultRouter {
    private const val CHANNEL = "install_confirmation"
    private var foreground: WeakReference<MainActivity>? = null
    private var pendingConfirmation: Intent? = null

    fun resumed(activity: MainActivity) {
        foreground = WeakReference(activity)
        pendingConfirmation?.let { confirmation ->
            pendingConfirmation = null
            open(activity, confirmation)
        }
    }

    fun paused(activity: MainActivity) {
        if (foreground?.get() === activity) foreground = null
    }

    fun clearPending() { pendingConfirmation = null }

    fun handle(context: Context, result: Intent) {
        if (result.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE) !=
            PackageInstaller.STATUS_PENDING_USER_ACTION) {
            foreground?.get()?.let { activity ->
                // Surface the final outcome only while the app is visible.
                activity.runOnUiThread { InstallResultActivity.showResult(activity, result) }
            }
            return
        }
        val confirmation = InstallResultActivity.confirmationIntent(result)
        if (confirmation == null) {
            Toast.makeText(context, localized(context, R.string.update_confirmation_failed), Toast.LENGTH_LONG).show()
            return
        }
        val activity = foreground?.get()
        if (activity != null && !activity.isFinishing) {
            open(activity, confirmation)
        } else {
            pendingConfirmation = confirmation
            notifyForConfirmation(context, result)
        }
    }

    private fun open(activity: Activity, confirmation: Intent) {
        runCatching { activity.startActivity(confirmation) }.onFailure {
            Toast.makeText(activity, localized(activity, R.string.update_confirmation_failed), Toast.LENGTH_LONG).show()
        }
    }

    private fun notifyForConfirmation(context: Context, result: Intent) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, localized(context, R.string.update_action_needed), NotificationManager.IMPORTANCE_DEFAULT)
        )
        val sessionId = result.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, 0)
        val intent = Intent(context, InstallResultActivity::class.java)
            .putExtra(Intent.EXTRA_INTENT, InstallResultActivity.confirmationIntent(result))
            .putExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_PENDING_USER_ACTION)
        val pending = PendingIntent.getActivity(
            context, sessionId, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(localized(context, R.string.update_action_needed))
            .setContentText(localized(context, R.string.update_tap_to_confirm))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            NotificationManagerCompat.from(context).notify(50_000 + sessionId, notification)
        }
    }

    private fun localized(context: Context, resource: Int) =
        AppLocale.string(context, Preferences(context).language, resource)
}

class InstallResultActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    private fun handle(result: Intent) {
        InstallResultRouter.clearPending()
        when (result.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmation = confirmationIntent(result)
                if (confirmation == null) {
                    Toast.makeText(this, localized(R.string.update_confirmation_failed), Toast.LENGTH_LONG).show()
                } else {
                    runCatching { startActivity(confirmation) }.onFailure {
                        Toast.makeText(this, localized(R.string.update_confirmation_failed), Toast.LENGTH_LONG).show()
                    }
                }
            }
            PackageInstaller.STATUS_SUCCESS ->
                Toast.makeText(this, localized(R.string.update_installed), Toast.LENGTH_SHORT).show()
            PackageInstaller.STATUS_FAILURE_ABORTED ->
                Toast.makeText(this, localized(R.string.update_cancelled), Toast.LENGTH_SHORT).show()
            else -> {
                val message = result.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    ?.takeIf(String::isNotBlank)
                    ?: localized(R.string.update_install_failed)
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        }
        finish()
    }

    private fun localized(resource: Int): String =
        AppLocale.string(this, Preferences(this).language, resource)

    companion object {
        const val EXTRA_VERSION = "update_version"

        fun showResult(context: Context, result: Intent) {
            val resource = when (result.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                PackageInstaller.STATUS_SUCCESS -> R.string.update_installed
                PackageInstaller.STATUS_FAILURE_ABORTED -> R.string.update_cancelled
                else -> R.string.update_install_failed
            }
            Toast.makeText(context, AppLocale.string(context, Preferences(context).language, resource), Toast.LENGTH_LONG).show()
        }

    @Suppress("DEPRECATION")
    fun confirmationIntent(source: Intent): Intent? {
        val confirmation = if (Build.VERSION.SDK_INT >= 33) {
            source.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            source.getParcelableExtra(Intent.EXTRA_INTENT)
        }

        // PackageInstaller supplies the system confirmation UI as a nested intent. Android 16+
        // protects nested-intent launches by default, which can otherwise turn a successful
        // download/session commit into a notification with no installer dialog. This activity is
        // non-exported and is only reached through our PackageInstaller status PendingIntent, so
        // this is a narrow, trusted use of the platform opt-out.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            confirmation?.removeLaunchSecurityProtection()
        }
        return confirmation
    }
    }
}
