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

/** Reads real device data into [Facts]. Every system call is wrapped in runCatching: odd devices must never crash the app. */
object Collector {
    /** Common MDM agents / payment-lock apps. Each must also be listed in the manifest <queries>. */
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
        "com.payjoy.access" to "PayJoy",
    )

    /** AOSP core app that enrolls devices into MDM. Every normal phone has it enabled. */
    private val PROVISIONING_CORE = mapOf("com.android.managedprovisioning" to "Managed Provisioning")

    /** Google management apps preloaded on some ROMs only, so only "uninstalled for this user" counts. */
    private val PROVISIONING_OTHER = mapOf(
        "com.google.android.apps.work.clouddpc" to "Android Device Policy",
        "com.google.android.devicelockcontroller" to "Device Lock Controller",
    )

    private val SU_PATHS = listOf(
        "/system/bin/su", "/system/xbin/su", "/sbin/su", "/system/app/Superuser.apk",
        "/data/adb/magisk", "/data/local/bin/su", "/data/local/xbin/su",
    )

    /** Restrictions MDM commonly applies. Stored as keys; [Texts.restriction] turns them into words. */
    private val RESTRICTIONS = listOf(
        UserManager.DISALLOW_FACTORY_RESET,
        UserManager.DISALLOW_UNINSTALL_APPS,
        UserManager.DISALLOW_DEBUGGING_FEATURES,
        UserManager.DISALLOW_USB_FILE_TRANSFER,
    )

    /** Marketing name the manufacturer stores in system properties (Xiaomi, OPPO/realme/OnePlus, Huawei…). */
    private val MARKET_PROPS = listOf(
        "ro.product.marketname", "ro.product.vendor.marketname", "ro.product.odm.marketname",
        "ro.vendor.oplus.market.name", "ro.oppo.market.name", "ro.config.marketing_name",
    )

    fun deviceName(ctx: Context): String {
        val market = MARKET_PROPS.map { prop(it) }.firstOrNull { it.isNotBlank() }
        val setting = runCatching { Settings.Global.getString(ctx.contentResolver, Settings.Global.DEVICE_NAME) }.getOrNull()
        return Rules.deviceName(market, setting, Build.BRAND, Build.MANUFACTURER, Build.MODEL)
    }

    /**
     * Setup date ≈ first boot or most recent factory reset.
     * Two sources: system packages (some devices stamp the first boot, others 2009, which is dropped)
     * and preloaded non-system apps (installed by the OEM during setup).
     */
    fun setupTime(ctx: Context): Long? {
        val pm = ctx.packageManager
        val system = listOf("android", "com.android.providers.settings", "com.android.settings")
            .mapNotNull { runCatching { pm.getPackageInfo(it, 0).firstInstallTime }.getOrNull() }
        val preloaded = runCatching { pm.getInstalledPackages(0) }.getOrDefault(emptyList())
            .filter { p -> p.packageName != ctx.packageName && (p.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_SYSTEM == 0 }
            .map { it.firstInstallTime }
        return Rules.setupTime(system + preloaded, System.currentTimeMillis())
    }

    /**
     * OEM app-cloning spaces live at fixed user ids and are not work profiles, even though some
     * (MIUI Second Space / Dual Apps) are built as managed profiles and carry the "Work" badge:
     * 999 = MIUI/HyperOS + ColorOS Dual Apps, 128 = Huawei App Twin, 95 = Samsung Dual Messenger,
     * 150 = Samsung Secure Folder. Real work profiles get the next free id from 10 upwards.
     */
    private val CLONE_USER_IDS = setOf(95, 128, 150, 999)

    /** UserHandle.hashCode() is the user id (public API exposes no getter). */
    private fun userId(u: android.os.UserHandle): Int = u.hashCode()

    /**
     * Counts extra profiles that are WORK profiles: Android badges app labels in them as "Work …".
     * OEM clone spaces are skipped first. Returns -1 when it cannot tell.
     */
    private fun workProfiles(ctx: Context, um: UserManager): Int {
        val me = android.os.Process.myUserHandle()
        val others = runCatching { um.userProfiles }.getOrDefault(emptyList())
            .filter { it != me && userId(it) !in CLONE_USER_IDS }
        if (others.isEmpty()) return 0
        val res = android.content.res.Resources.getSystem()
        val probe = "MDMCHECK"
        fun badge(name: String) = res.getIdentifier(name, "string", "android")
            .takeIf { it != 0 }?.let { runCatching { res.getString(it, probe) }.getOrNull() }
        val work = badge("managed_profile_label_badge")
        val clone = badge("clone_profile_label_badge")
        var count = 0
        for (u in others) {
            val label = runCatching { ctx.packageManager.getUserBadgedLabel(probe, u).toString() }.getOrNull() ?: return -1
            when {
                label == probe || label == clone -> Unit
                work != null && label == work -> count++
                else -> return -1
            }
        }
        return count
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
            workProfiles = workProfiles(ctx, um),
            knownApps = KNOWN.keys.filter { installed(pm, it) }.map { KNOWN.getValue(it) }.distinct(),
            blocked = RESTRICTIONS.filter { runCatching { um.hasUserRestriction(it) }.getOrDefault(false) },
            removedCore = PROVISIONING_CORE.filter { (pkg, _) -> tampered(pm, pkg, disabledCounts = true) }.values.toList(),
            removedOther = PROVISIONING_OTHER.filter { (pkg, _) -> tampered(pm, pkg, disabledCounts = false) }.values.toList(),
            bootState = prop("ro.boot.verifiedbootstate"),
            rooted = SU_PATHS.any { runCatching { File(it).exists() }.getOrDefault(false) },
            devOptions = global(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED),
            adb = global(Settings.Global.ADB_ENABLED),
        )
    }

    fun run(ctx: Context, t: Texts = Texts.current()): Report = Rules.report(collect(ctx), t)

    private fun label(pm: PackageManager, pkg: String): String =
        KNOWN[pkg] ?: runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)

    private fun isSystem(pm: PackageManager, pkg: String): Boolean = runCatching {
        (pm.getApplicationInfo(pkg, 0).flags and ApplicationInfo.FLAG_SYSTEM) != 0
    }.getOrDefault(false)

    /**
     * True when a system app that ships on this ROM has been uninstalled for the user
     * (`pm uninstall --user 0`) or, if [disabledCounts], disabled. That is what "MDM fix" tools do.
     * An app that is simply not on this ROM is not tampering.
     */
    private fun tampered(pm: PackageManager, pkg: String, disabledCounts: Boolean): Boolean {
        ("DEPRECATION")
        val flags = PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.MATCH_DISABLED_COMPONENTS
        val ai = runCatching { pm.getApplicationInfo(pkg, flags) }.getOrNull() ?: return false
        if (ai.flags and ApplicationInfo.FLAG_SYSTEM == 0) return false
        if (ai.flags and ApplicationInfo.FLAG_INSTALLED == 0) return true
        if (!disabledCounts) return false
        val state = runCatching { pm.getApplicationEnabledSetting(pkg) }.getOrNull()
        return !ai.enabled ||
            state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
            state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
    }

    private fun installed(pm: PackageManager, pkg: String): Boolean =
        runCatching { pm.getPackageInfo(pkg, 0); true }.getOrDefault(false)

    private fun prop(key: String): String = runCatching {
        ProcessBuilder("getprop", key).redirectErrorStream(true).start()
            .inputStream.bufferedReader().use { it.readText().trim() }
    }.getOrDefault("")
}
