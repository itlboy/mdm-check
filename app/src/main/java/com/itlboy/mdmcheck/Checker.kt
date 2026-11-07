package com.itlboy.mdmcheck

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.os.UserManager
import android.provider.Settings
import java.io.File

/** Đọc dữ liệu thật từ máy thành [Facts]. Mọi lời gọi hệ thống đều bọc runCatching: máy lạ không được làm app crash. */
object Collector {
    /** Agent MDM / khoá trả góp hay gặp. Phải khai báo cùng tên trong <queries> của manifest. */
    val KNOWN = mapOf(
        "com.microsoft.windowsintune.companyportal" to "Microsoft Intune",
        "com.microsoft.intune" to "Microsoft Intune",
        "com.airwatch.androidagent" to "Workspace ONE (AirWatch)",
        "com.mobileiron" to "MobileIron",
        "com.fiberlink.maas360.android.control" to "IBM MaaS360",
        "com.manageengine.mdm.android" to "ManageEngine MDM",
        "com.hexnode.hexnodemdm" to "Hexnode MDM",
        "com.promobitech.mobilock.pro" to "Scalefusion",
        "com.sophos.mobilecontrol.client.android" to "Sophos Mobile",
        "com.payjoy.access" to "PayJoy (khoá trả góp)",
    )

    private val SU_PATHS = listOf(
        "/system/bin/su", "/system/xbin/su", "/sbin/su", "/system/app/Superuser.apk",
        "/data/adb/magisk", "/data/local/bin/su", "/data/local/xbin/su",
    )

    /** Chỉ chặn những gì quản lý MDM hay dùng; giá trị là chữ hiện cho người dùng. */
    private val RESTRICTIONS = listOf(
        UserManager.DISALLOW_FACTORY_RESET to "khôi phục gốc",
        UserManager.DISALLOW_UNINSTALL_APPS to "gỡ app",
        UserManager.DISALLOW_DEBUGGING_FEATURES to "gỡ lỗi USB",
        UserManager.DISALLOW_USB_FILE_TRANSFER to "chép file qua USB",
    )

    fun deviceName(ctx: Context): String {
        val n = runCatching { Settings.Global.getString(ctx.contentResolver, Settings.Global.DEVICE_NAME) }.getOrNull()
        return Rules.deviceName(n, Build.MANUFACTURER, Build.MODEL)
    }

    /**
     * Ngày thiết lập máy ≈ lần đầu bật máy hoặc lần khôi phục cài đặt gốc gần nhất:
     * gói hệ thống được "cài" đúng lúc máy khởi động lần đầu sau khi xoá sạch.
     */
    fun setupTime(ctx: Context): Long? {
        val pm = ctx.packageManager
        val times = listOf("android", "com.android.providers.settings", "com.android.settings")
            .mapNotNull { runCatching { pm.getPackageInfo(it, 0).firstInstallTime }.getOrNull() }
        return Rules.setupTime(times)
    }

    fun summary(ctx: Context) = DeviceSummary(deviceName(ctx), Build.VERSION.RELEASE, setupTime(ctx))

    fun collect(ctx: Context): Facts {
        val pm = ctx.packageManager
        val dpm = ctx.getSystemService(DevicePolicyManager::class.java)
        val um = ctx.getSystemService(UserManager::class.java)

        val admins = runCatching { dpm.activeAdmins.orEmpty().map { it.packageName } }
            .getOrDefault(emptyList()).distinct().filter { it != ctx.packageName }
        val (sysAdmins, userAdmins) = admins.partition { isSystem(pm, it) }
        val owner = admins.firstOrNull { runCatching { dpm.isDeviceOwnerApp(it) }.getOrDefault(false) }
        val profileOwner = admins.firstOrNull { runCatching { dpm.isProfileOwnerApp(it) }.getOrDefault(false) }
        val orgOwned = Build.VERSION.SDK_INT >= 30 &&
            runCatching { dpm.isOrganizationOwnedDeviceWithManagedProfile }.getOrDefault(false)

        fun global(key: String) =
            runCatching { Settings.Global.getInt(ctx.contentResolver, key, 0) }.getOrDefault(0) == 1

        return Facts(
            deviceName = deviceName(ctx),
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            release = Build.VERSION.RELEASE,
            sdk = Build.VERSION.SDK_INT,
            securityPatch = Build.VERSION.SECURITY_PATCH,
            display = Build.DISPLAY,
            buildTime = Build.TIME,
            setupTime = setupTime(ctx),
            uptimeMs = SystemClock.elapsedRealtime(),
            now = System.currentTimeMillis(),
            userAdmins = userAdmins.map { label(pm, it) },
            systemAdmins = sysAdmins.map { label(pm, it) },
            deviceOwner = owner?.let { label(pm, it) },
            orgOwned = orgOwned,
            profileOwner = profileOwner?.let { label(pm, it) },
            profiles = runCatching { um.userProfiles.size }.getOrDefault(1),
            knownApps = KNOWN.keys.filter { installed(pm, it) }.map { KNOWN.getValue(it) }.distinct(),
            blocked = RESTRICTIONS.filter { runCatching { um.hasUserRestriction(it.first) }.getOrDefault(false) }
                .map { it.second },
            bootState = prop("ro.boot.verifiedbootstate"),
            rooted = SU_PATHS.any { runCatching { File(it).exists() }.getOrDefault(false) },
            devOptions = global(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED),
            adb = global(Settings.Global.ADB_ENABLED),
        )
    }

    fun run(ctx: Context): Report = Rules.report(collect(ctx))

    private fun label(pm: PackageManager, pkg: String): String =
        KNOWN[pkg] ?: runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)

    private fun isSystem(pm: PackageManager, pkg: String): Boolean = runCatching {
        (pm.getApplicationInfo(pkg, 0).flags and ApplicationInfo.FLAG_SYSTEM) != 0
    }.getOrDefault(false)

    private fun installed(pm: PackageManager, pkg: String): Boolean =
        runCatching { pm.getPackageInfo(pkg, 0); true }.getOrDefault(false)

    private fun prop(key: String): String = runCatching {
        ProcessBuilder("getprop", key).redirectErrorStream(true).start()
            .inputStream.bufferedReader().use { it.readText().trim() }
    }.getOrDefault("")
}
