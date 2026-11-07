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
    /** UserManager restriction keys in force, e.g. "no_factory_reset". */
    val blocked: List<String>,
    /** Core provisioning app (Managed Provisioning) removed or disabled: classic "MDM fix". */
    val removedCore: List<String> = emptyList(),
    /** Optional management apps removed for this user (not on every ROM, so only a warning). */
    val removedOther: List<String> = emptyList(),
    val bootState: String,
    val rooted: Boolean,
    val devOptions: Boolean,
    val adb: Boolean,
)

/** All verdict logic. Pure functions: same [Facts] + [Texts] → same [Report]. */
object Rules {
    private const val YEAR_2015 = 1_420_070_400_000L

    fun fmtDate(t: Long, tx: Texts = En, tz: TimeZone = TimeZone.getDefault()): String =
        SimpleDateFormat(tx.datePattern, tx.dateLocale).apply { timeZone = tz }.format(Date(t))

    fun agoText(t: Long, now: Long, tx: Texts = En): String {
        val d = TimeUnit.MILLISECONDS.toDays(now - t)
        return if (d <= 0) tx.today else tx.daysAgo(d)
    }

    fun uptimeText(ms: Long, tx: Texts = En): String {
        val hours = TimeUnit.MILLISECONDS.toHours(ms)
        return if (hours >= 24) tx.uptimeDays(hours / 24, hours % 24)
        else tx.uptimeHours(hours, TimeUnit.MILLISECONDS.toMinutes(ms) % 60)
    }

    /** "2026-10-01" → the date in the reader's format; anything unexpected is returned as is. */
    fun formatPatch(p: String, tx: Texts = En): String {
        if (!Regex("""\d{4}-\d{2}-\d{2}""").matches(p)) return p
        val utc = TimeZone.getTimeZone("UTC")
        val parsed = runCatching {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = utc; isLenient = false }.parse(p)
        }.getOrNull() ?: return p
        return fmtDate(parsed.time, tx, utc)
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

    fun report(f: Facts, tx: Texts = En): Report {
        val mdm = mdm(f, tx)
        val sections = listOf(mdm, device(f, tx), history(f, tx), security(f, tx))
        val bad = mdm.rows.filter { it.status == Status.BAD }
        val warn = mdm.rows.count { it.status == Status.WARN }
        return when {
            bad.isNotEmpty() -> Report(Status.BAD, tx.managed, bad.first().detail, sections)
            warn > 0 -> Report(Status.WARN, tx.review, tx.reviewSub(warn), sections)
            else -> Report(Status.OK, tx.clean, tx.noMdm, sections)
        }
    }

    fun mdm(f: Facts, tx: Texts = En): Section {
        val admin = when {
            f.userAdmins.isNotEmpty() -> CheckRow(tx.admins, tx.adminsActive(f.userAdmins.joinToString()), Status.BAD)
            f.systemAdmins.isNotEmpty() -> CheckRow(tx.admins, tx.adminsSystemOnly(f.systemAdmins.joinToString()), Status.OK)
            else -> CheckRow(tx.admins, tx.adminsNone, Status.OK)
        }
        val owner = when {
            f.deviceOwner != null -> CheckRow(tx.orgOwned, tx.managedBy(f.deviceOwner), Status.BAD)
            f.orgOwned -> CheckRow(tx.orgOwned, tx.ownedByOrg, Status.BAD)
            else -> CheckRow(tx.orgOwned, tx.noOrg, Status.OK)
        }
        // Extra profiles are usually Dual Apps / Second Space: only red when it is surely a work profile.
        val extra = f.profiles - 1
        val profile = when {
            f.profileOwner != null -> CheckRow(tx.workProfile, tx.managedBy(f.profileOwner), Status.BAD)
            f.workProfiles > 0 -> CheckRow(tx.workProfile, tx.workProfilePresent, Status.BAD)
            extra > 0 && f.workProfiles < 0 -> CheckRow(tx.workProfile, tx.unknownProfiles(extra), Status.WARN)
            extra > 0 -> CheckRow(tx.workProfile, tx.dualApps, Status.OK)
            else -> CheckRow(tx.workProfile, tx.personalOnly, Status.OK)
        }
        val known = if (f.knownApps.isNotEmpty())
            CheckRow(tx.knownApps, tx.installed(f.knownApps.joinToString()), Status.BAD)
        else CheckRow(tx.knownApps, tx.noneFound, Status.OK)
        val blocked = if (f.blocked.isNotEmpty())
            CheckRow(tx.blockedFeatures, tx.blocked(f.blocked.joinToString { tx.restriction(it) }), Status.BAD)
        else CheckRow(tx.blockedFeatures, tx.allAllowed, Status.OK)

        // "MDM fix" services disable or uninstall the provisioning apps; a normal phone never does.
        val traces = when {
            f.removedCore.isNotEmpty() -> CheckRow(tx.traces, tx.tracesFound((f.removedCore + f.removedOther).joinToString()), Status.BAD)
            f.removedOther.isNotEmpty() -> CheckRow(tx.traces, tx.tracesRemoved(f.removedOther.joinToString()), Status.WARN)
            else -> CheckRow(tx.traces, tx.noTraces, Status.OK)
        }

        val rows = listOf(admin, owner, profile, known, blocked, traces)
        val bad = rows.count { it.status == Status.BAD }
        val warn = rows.count { it.status == Status.WARN }
        val (status, summary) = when {
            bad > 0 -> Status.BAD to tx.mdmIssues(bad, rows.size)
            warn > 0 -> Status.WARN to tx.mdmReview(warn, rows.size)
            else -> Status.OK to tx.mdmClear(rows.size)
        }
        return Section(Kind.MDM, tx.mdmTitle, summary, status, rows)
    }

    fun device(f: Facts, tx: Texts = En): Section {
        val rows = listOf(
            CheckRow(tx.name, f.manufacturer, value = f.deviceName),
            CheckRow(tx.model, tx.modelDetail, value = f.model),
            CheckRow(tx.version, "API ${f.sdk}", value = "Android ${f.release}"),
            CheckRow(tx.patch, tx.patchDetail, value = formatPatch(f.securityPatch, tx)),
            CheckRow(tx.build, f.display),
        )
        return Section(Kind.DEVICE, tx.deviceTitle, "Android ${f.release}", Status.INFO, rows)
    }

    fun history(f: Facts, tx: Texts = En): Section {
        val setup = f.setupTime
        val rows = listOf(
            if (setup != null) CheckRow(tx.setupDate, tx.setupDetail(agoText(setup, f.now, tx)), value = fmtDate(setup, tx))
            else CheckRow(tx.setupDate, tx.notReadable, value = tx.unknown),
            CheckRow(tx.buildDate, tx.buildDateDetail, value = fmtDate(f.buildTime, tx)),
            CheckRow(tx.uptime, tx.uptimeDetail, value = uptimeText(f.uptimeMs, tx)),
        )
        val summary = setup?.let { tx.setUpAgo(agoText(it, f.now, tx)) } ?: tx.setupUnknown
        return Section(Kind.HISTORY, tx.historyTitle, summary, Status.INFO, rows)
    }

    fun bootRow(state: String, tx: Texts = En): CheckRow = when (state) {
        "green" -> CheckRow("Bootloader", tx.bootStock, Status.OK, tx.bootLocked)
        "orange" -> CheckRow("Bootloader", tx.bootModified, Status.WARN, tx.bootUnlocked)
        "yellow" -> CheckRow("Bootloader", tx.bootCustom, Status.WARN, tx.bootCustomValue)
        else -> CheckRow("Bootloader", tx.bootHidden, Status.INFO, tx.unknown)
    }

    fun security(f: Facts, tx: Texts = En): Section {
        val boot = bootRow(f.bootState, tx)
        val root = if (f.rooted) CheckRow(tx.root, tx.rootFound, Status.WARN, tx.rootLikely)
        else CheckRow(tx.root, tx.rootNone, Status.OK, tx.no)
        val dev = if (f.devOptions) CheckRow(tx.devOptions, tx.turnOff, Status.WARN, tx.on)
        else CheckRow(tx.devOptions, tx.turnedOff, Status.OK, tx.off)
        val adb = if (f.adb) CheckRow(tx.usbDebugging, tx.turnOff, Status.WARN, tx.on)
        else CheckRow(tx.usbDebugging, tx.turnedOff, Status.OK, tx.off)
        val rows = listOf(boot, root, dev, adb)
        // Short on purpose: the menu shows it on one line.
        val warn = rows.count { it.status == Status.WARN }
        val summary = if (warn == 0) tx.securityOk else tx.toReview(warn)
        val status = if (warn > 0) Status.WARN else Status.OK
        return Section(Kind.SECURITY, tx.securityTitle, summary, status, rows)
    }
}
