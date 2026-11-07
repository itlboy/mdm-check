package com.itlboy.mdmcheck

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

enum class Status { OK, BAD, WARN, INFO }
enum class Kind { MDM, DEVICE, HISTORY, SECURITY }

/** One row on a detail screen. With `value`, the text shows on the right; otherwise a ✓/✗ badge. */
data class CheckRow(val name: String, val detail: String, val status: Status = Status.INFO, val value: String? = null)
data class Section(val kind: Kind, val title: String, val summary: String, val status: Status, val rows: List<CheckRow>)
data class Report(val status: Status, val headline: String, val sub: String, val sections: List<Section>)
data class DeviceSummary(val name: String, val android: String, val setup: Long?)

/** Raw data read from the device. Kept separate so [Rules] can be tested without Android. */
data class Facts(
    val deviceName: String,
    val manufacturer: String,
    val model: String,
    val release: String,
    val sdk: Int,
    val securityPatch: String,
    val display: String,
    val buildTime: Long,
    val setupTime: Long?,
    val uptimeMs: Long,
    val now: Long,
    /** Active device admins that are NOT system apps (MDM agents usually are this kind). */
    val userAdmins: List<String>,
    /** Active device admins that are system apps (Google/OEM Find My Device…): normal. */
    val systemAdmins: List<String>,
    val deviceOwner: String?,
    val orgOwned: Boolean,
    val profileOwner: String?,
    val profiles: Int,
    /** Number of extra profiles that are work profiles; -1 = could not tell. */
    val workProfiles: Int,
    val knownApps: List<String>,
    val blocked: List<String>,
    val bootState: String,
    val rooted: Boolean,
    val devOptions: Boolean,
    val adb: Boolean,
)

/** All verdict logic. Pure functions: same [Facts] → same [Report]. */
object Rules {
    private const val YEAR_2015 = 1_420_070_400_000L

    /** "5 Nov 2026": unambiguous whatever the reader's date habits. */
    fun fmtDate(t: Long, tz: TimeZone = TimeZone.getDefault()): String =
        SimpleDateFormat("d MMM yyyy", Locale.US).apply { timeZone = tz }.format(Date(t))

    fun agoText(t: Long, now: Long): String {
        val d = TimeUnit.MILLISECONDS.toDays(now - t)
        return when {
            d <= 0 -> "today"
            d == 1L -> "1 day ago"
            else -> "$d days ago"
        }
    }

    fun uptimeText(ms: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(ms)
        return if (hours >= 24) "${hours / 24}d ${hours % 24}h"
        else "${hours}h ${TimeUnit.MILLISECONDS.toMinutes(ms) % 60}m"
    }

    /** "2026-10-01" → "1 Oct 2026"; anything unexpected is returned as is. */
    fun formatPatch(p: String): String {
        if (!Regex("""\d{4}-\d{2}-\d{2}""").matches(p)) return p
        val utc = TimeZone.getTimeZone("UTC")
        val parsed = runCatching {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = utc; isLenient = false }.parse(p)
        }.getOrNull() ?: return p
        return fmtDate(parsed.time, utc)
    }

    /**
     * Priority: the manufacturer's marketing name ("Mi 10S" → "Xiaomi Mi 10S") → the device name
     * from Settings (unless it is just the model code) → "Brand + model code".
     */
    fun deviceName(market: String?, setting: String?, brand: String, manufacturer: String, model: String): String {
        fun cap(s: String) = s.replaceFirstChar { it.titlecase(Locale.ROOT) }
        fun withBrand(name: String): String {
            val n = name.trim()
            val prefixed = listOf(brand, manufacturer).any { it.isNotBlank() && n.startsWith(it, ignoreCase = true) }
            val b = cap(brand.ifBlank { manufacturer })
            return if (prefixed || b.isBlank()) n else "$b $n"
        }
        if (!market.isNullOrBlank()) return withBrand(market)
        val s = setting?.trim()
        if (!s.isNullOrEmpty() && !s.equals(model.trim(), ignoreCase = true)) return s
        return withBrand(model)
    }

    /** Earliest valid time; drops anything before 2015 (system images stamp 2009) or after [now]. */
    fun setupTime(times: List<Long>, now: Long = Long.MAX_VALUE): Long? =
        times.filter { it > YEAR_2015 && it <= now }.minOrNull()

    /** "v1.2" → Latest("1.2", url). Empty or non-numeric tag → null. */
    fun parseRelease(tag: String?, url: String?): Latest? {
        val v = tag?.trim()?.removePrefix("v")?.removePrefix("V") ?: return null
        if (v.isEmpty() || !v.first().isDigit()) return null
        return Latest(v, if (url.isNullOrBlank()) Updater.RELEASES_URL else url)
    }

    /** True if [latest] is newer than [current]; compares number by number, missing parts are 0 ("1.0" == "1.0.0"). */
    fun isNewer(current: String, latest: String): Boolean {
        fun parts(v: String) = v.removePrefix("v").split(".", "-").map { p -> p.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val a = parts(current)
        val b = parts(latest)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return y > x
        }
        return false
    }

    private fun plural(n: Int, one: String, many: String) = if (n == 1) "1 $one" else "$n $many"

    fun report(f: Facts): Report {
        val mdm = mdm(f)
        val sections = listOf(mdm, device(f), history(f), security(f))
        val bad = mdm.rows.filter { it.status == Status.BAD }
        val warn = mdm.rows.count { it.status == Status.WARN }
        return when {
            bad.isNotEmpty() -> Report(Status.BAD, "Device is managed", bad.first().detail, sections)
            warn > 0 -> Report(Status.WARN, "Worth a closer look", plural(warn, "item", "items") + " to review", sections)
            else -> Report(Status.OK, "Device is clean", "No MDM detected", sections)
        }
    }

    fun mdm(f: Facts): Section {
        val admin = when {
            f.userAdmins.isNotEmpty() ->
                CheckRow("Device admin apps", f.userAdmins.joinToString() + " active", Status.BAD)
            f.systemAdmins.isNotEmpty() ->
                CheckRow("Device admin apps", "System apps only: " + f.systemAdmins.joinToString(), Status.OK)
            else -> CheckRow("Device admin apps", "None active", Status.OK)
        }
        val owner = when {
            f.deviceOwner != null -> CheckRow("Organization-owned", "Managed by ${f.deviceOwner}", Status.BAD)
            f.orgOwned -> CheckRow("Organization-owned", "Owned by an organization", Status.BAD)
            else -> CheckRow("Organization-owned", "No organization", Status.OK)
        }
        // Extra profiles are usually Dual Apps / Second Space: only red when it is surely a work profile.
        val extra = f.profiles - 1
        val profile = when {
            f.profileOwner != null -> CheckRow("Work profile", "Managed by ${f.profileOwner}", Status.BAD)
            f.workProfiles > 0 -> CheckRow("Work profile", "A work profile is present", Status.BAD)
            extra > 0 && f.workProfiles < 0 -> CheckRow("Work profile",
                plural(extra, "extra profile", "extra profiles") + " of unknown type", Status.WARN)
            extra > 0 -> CheckRow("Work profile", "None. Extra profile is Dual Apps / Second Space", Status.OK)
            else -> CheckRow("Work profile", "Personal profile only", Status.OK)
        }
        val known = if (f.knownApps.isNotEmpty())
            CheckRow("MDM / payment-lock apps", "Installed: " + f.knownApps.joinToString(), Status.BAD)
        else CheckRow("MDM / payment-lock apps", "None found", Status.OK)
        val blocked = if (f.blocked.isNotEmpty())
            CheckRow("Blocked features", "Blocked: " + f.blocked.joinToString(), Status.BAD)
        else CheckRow("Blocked features", "Reset, uninstall and USB all allowed", Status.OK)

        val rows = listOf(admin, owner, profile, known, blocked)
        val bad = rows.count { it.status == Status.BAD }
        val warn = rows.count { it.status == Status.WARN }
        val (status, summary) = when {
            bad > 0 -> Status.BAD to "$bad/${rows.size} with issues"
            warn > 0 -> Status.WARN to "$warn/${rows.size} to review"
            else -> Status.OK to "${rows.size}/${rows.size} clear"
        }
        return Section(Kind.MDM, "Management signs", summary, status, rows)
    }

    fun device(f: Facts): Section {
        val rows = listOf(
            CheckRow("Name", f.manufacturer, value = f.deviceName),
            CheckRow("Model", "Manufacturer code", value = f.model),
            CheckRow("Version", "API ${f.sdk}", value = "Android ${f.release}"),
            CheckRow("Security patch", "Newer is safer", value = formatPatch(f.securityPatch)),
            CheckRow("Build", f.display),
        )
        return Section(Kind.DEVICE, "Device info", "${f.deviceName} · Android ${f.release}", Status.INFO, rows)
    }

    fun history(f: Facts): Section {
        val setup = f.setupTime
        val rows = listOf(
            if (setup != null) CheckRow("Setup date",
                "First boot or last factory reset · ${agoText(setup, f.now)}", value = fmtDate(setup))
            else CheckRow("Setup date", "Not readable on this device", value = "Unknown"),
            CheckRow("OS build date", "Installed system version", value = fmtDate(f.buildTime)),
            CheckRow("Uptime", "Since last restart", value = uptimeText(f.uptimeMs)),
        )
        val summary = setup?.let { "Set up ${agoText(it, f.now)}" } ?: "Setup date unknown"
        return Section(Kind.HISTORY, "Device history", summary, Status.INFO, rows)
    }

    fun bootRow(state: String): CheckRow = when (state) {
        "green" -> CheckRow("Bootloader", "Stock manufacturer software", Status.OK, "Locked")
        "orange" -> CheckRow("Bootloader", "Software may be modified", Status.WARN, "Unlocked")
        "yellow" -> CheckRow("Bootloader", "Signed with a custom key", Status.WARN, "Custom key")
        else -> CheckRow("Bootloader", "Device doesn't expose this", Status.INFO, "Unknown")
    }

    fun security(f: Facts): Section {
        val boot = bootRow(f.bootState)
        val root = if (f.rooted) CheckRow("Root", "Root traces found", Status.WARN, "Likely")
        else CheckRow("Root", "No root traces", Status.OK, "No")
        val dev = if (f.devOptions) CheckRow("Developer options", "Turn off if unused", Status.WARN, "On")
        else CheckRow("Developer options", "Turned off", Status.OK, "Off")
        val adb = if (f.adb) CheckRow("USB debugging", "Turn off if unused", Status.WARN, "On")
        else CheckRow("USB debugging", "Turned off", Status.OK, "Off")
        val rows = listOf(boot, root, dev, adb)
        val bootShort = when (boot.status) {
            Status.OK -> "Bootloader locked"
            Status.WARN -> "Bootloader unlocked"
            else -> "Bootloader unknown"
        }
        val summary = "$bootShort · " + if (f.rooted) "rooted" else "not rooted"
        val status = if (rows.any { it.status == Status.WARN }) Status.WARN else Status.OK
        return Section(Kind.SECURITY, "Other security", summary, status, rows)
    }
}
