package app.maw629.homerelay.ui

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat

interface AppVersionProvider {
    val versionName: String
    val versionCode: Long
}

class PackageManagerAppVersionProvider(private val context: Context) : AppVersionProvider {
    override val versionName: String
        get() = packageInfo().versionName.orEmpty()

    override val versionCode: Long
        get() = PackageInfoCompat.getLongVersionCode(packageInfo())

    private fun packageInfo() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
}
