package com.itlboy.mdmcheck

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class RulesTest {
    private val day = 86_400_000L
    private val now = 1_793_000_000_000L // Nov 2026

    /** Clean device: no admins, no owner, one profile, locked bootloader. */
    private val clean = Facts(
        deviceName = "OPPO Find X9 Ultra", manufacturer = "OPPO", model = "PHZ110", release = "16", sdk = 36,
        securityPatch = "2026-10-01", display = "PHZ110_16.0.0.100", buildTime = now - 60 * day,
        setupTime = now - 2 * day, uptimeMs = 3 * 3_600_000L, now = now,
        userAdmins = emptyList(), systemAdmins = emptyList(), deviceOwner = null, orgOwned = false,
        profileOwner = null, profiles = 1, workProfiles = 0, knownApps = emptyList(), blocked = emptyList(),
        bootState = "green", rooted = false, devOptions = false, adb = false,
    )

    private fun mdmRow(f: Facts, name: String) = Rules.mdm(f).rows.first { it.name == name }

    // ---- Overall verdict ----

    @Test fun cleanDevice_isClean() {
        val r = Rules.report(clean)
        assertEquals(Status.OK, r.status)
        assertEquals("Device is clean", r.headline)
        assertEquals("No MDM detected", r.sub)
        assertEquals("5/5 clear", r.sections[0].summary)
        assertEquals(listOf(Kind.MDM, Kind.DEVICE, Kind.HISTORY, Kind.SECURITY), r.sections.map { it.kind })
    }

    @Test fun systemAdminOnly_isStillClean() {
        val f = clean.copy(systemAdmins = listOf("Google Play services"))
        assertEquals(Status.OK, Rules.report(f).status)
        assertEquals("System apps only: Google Play services", mdmRow(f, "Device admin apps").detail)
    }

    @Test fun userAdmin_isManaged() {
        val r = Rules.report(clean.copy(userAdmins = listOf("Microsoft Intune")))
        assertEquals(Status.BAD, r.status)
        assertEquals("Device is managed", r.headline)
        assertEquals("Microsoft Intune active", r.sub)
    }

    @Test fun deviceOwner_isManaged() {
        val f = clean.copy(deviceOwner = "Hexnode MDM")
        assertEquals(Status.BAD, mdmRow(f, "Organization-owned").status)
        assertEquals("Managed by Hexnode MDM", Rules.report(f).sub)
    }

    @Test fun orgOwned_isManaged() {
        assertEquals(Status.BAD, mdmRow(clean.copy(orgOwned = true), "Organization-owned").status)
    }

    @Test fun profileOwner_isManaged() {
        assertEquals(Status.BAD, mdmRow(clean.copy(profileOwner = "Intune"), "Work profile").status)
    }

    @Test fun dualAppsProfile_isClean() {
        // Dual Apps / Second Space (MIUI, ColorOS) create an extra profile that is not a work profile.
        val f = clean.copy(profiles = 2, workProfiles = 0)
        val r = Rules.report(f)
        assertEquals(Status.OK, r.status)
        assertEquals("None. Extra profile is Dual Apps / Second Space", mdmRow(f, "Work profile").detail)
    }

    @Test fun workProfile_isManaged() {
        val f = clean.copy(profiles = 2, workProfiles = 1)
        assertEquals(Status.BAD, mdmRow(f, "Work profile").status)
        assertEquals(Status.BAD, Rules.report(f).status)
    }

    @Test fun unknownProfile_isOnlyWarning() {
        val r = Rules.report(clean.copy(profiles = 2, workProfiles = -1))
        assertEquals(Status.WARN, r.status)
        assertEquals("Worth a closer look", r.headline)
        assertEquals("1 item to review", r.sub)
        assertEquals("1/5 to review", r.sections[0].summary)
        assertEquals("2 extra profiles of unknown type",
            mdmRow(clean.copy(profiles = 3, workProfiles = -1), "Work profile").detail)
    }

    @Test fun singleProfile_unknownBadge_isClean() {
        // -1 only matters when an extra profile exists.
        assertEquals(Status.OK, mdmRow(clean.copy(workProfiles = -1), "Work profile").status)
    }

    @Test fun knownApp_isManaged() {
        val f = clean.copy(knownApps = listOf("PayJoy"))
        assertEquals(Status.BAD, mdmRow(f, "MDM / payment-lock apps").status)
    }

    @Test fun blockedFeatures_isManaged() {
        val row = mdmRow(clean.copy(blocked = listOf("no_factory_reset", "no_uninstall_apps")), "Blocked features")
        assertEquals(Status.BAD, row.status)
        assertEquals("Blocked: factory reset, uninstalling apps", row.detail)
    }

    @Test fun badBeatsWarn_andCountsAll() {
        val r = Rules.report(clean.copy(deviceOwner = "X", blocked = listOf("no_uninstall_apps"), profiles = 3, workProfiles = -1))
        assertEquals(Status.BAD, r.status)
        assertEquals("2/5 with issues", r.sections[0].summary)
    }

    @Test fun securityWarnings_doNotChangeMdmVerdict() {
        val r = Rules.report(clean.copy(rooted = true, adb = true, devOptions = true, bootState = "orange"))
        assertEquals(Status.OK, r.status)
        assertEquals(Status.WARN, r.sections[3].status)
        assertEquals("4 items to review", r.sections[3].summary)
    }

    // ---- Security ----

    @Test fun bootStates() {
        assertEquals("Locked", Rules.bootRow("green").value)
        assertEquals(Status.WARN, Rules.bootRow("orange").status)
        assertEquals(Status.WARN, Rules.bootRow("yellow").status)
        assertEquals(Status.INFO, Rules.bootRow("").status)
        assertEquals("No risks found", Rules.security(clean).summary)
        // Unknown bootloader state is not a risk on its own.
        assertEquals("No risks found", Rules.security(clean.copy(bootState = "")).summary)
        assertEquals("1 item to review", Rules.security(clean.copy(adb = true)).summary)
    }

    // ---- Device info / history ----

    @Test fun deviceSection() {
        val s = Rules.device(clean)
        assertEquals("Android 16", s.summary)
        assertEquals("1 Oct 2026", s.rows.first { it.name == "Security patch" }.value)
    }

    @Test fun historySection() {
        val s = Rules.history(clean)
        assertEquals("Set up 2 days ago", s.summary)
        assertEquals("3h 0m", s.rows.first { it.name == "Uptime" }.value)
    }

    @Test fun historyWithoutSetupTime() {
        val s = Rules.history(clean.copy(setupTime = null))
        assertEquals("Setup date unknown", s.summary)
        assertEquals("Unknown", s.rows.first().value)
    }

    @Test fun deviceName_fallbacks() {
        // The manufacturer's marketing name wins; the brand is added when missing.
        assertEquals("Xiaomi Mi 10S", Rules.deviceName("Mi 10S", "M2102J2SC", "Xiaomi", "Xiaomi", "M2102J2SC"))
        assertEquals("Redmi Note 13", Rules.deviceName("Redmi Note 13", null, "Redmi", "Xiaomi", "23129RAA4G"))
        assertEquals("OPPO Find X9 Ultra", Rules.deviceName("OPPO Find X9 Ultra", null, "OPPO", "OPPO", "PHZ110"))
        // A Settings name that is just the model code (MIUI) is ignored in favour of brand + code.
        assertEquals("Xiaomi M2102J2SC", Rules.deviceName(null, "M2102J2SC", "Xiaomi", "Xiaomi", "M2102J2SC"))
        assertEquals("OPPO Find X9 Ultra", Rules.deviceName(null, " OPPO Find X9 Ultra ", "OPPO", "OPPO", "PHZ110"))
        assertEquals("OPPO PHZ110", Rules.deviceName("", null, "OPPO", "OPPO", "PHZ110"))
        assertEquals("Google Pixel 9", Rules.deviceName(null, "", "google", "Google", "Pixel 9"))
        assertEquals("Samsung SM-S928B", Rules.deviceName(null, "  ", "samsung", "samsung", "SM-S928B"))
        assertEquals("OPPO CPH2581", Rules.deviceName(null, null, "OPPO", "OPPO", "OPPO CPH2581"))
    }

    @Test fun setupTime_picksEarliestValid() {
        assertEquals(1_600_000_000_000L, Rules.setupTime(listOf(0L, 1_700_000_000_000L, 1_600_000_000_000L)))
        assertNull(Rules.setupTime(listOf(0L, 1_000L)))
        assertNull(Rules.setupTime(emptyList()))
        // The fake 2009 stamp of system images is dropped; preloaded apps give the real date.
        assertEquals(1_620_000_000_000L, Rules.setupTime(listOf(1_230_768_000_000L, 1_620_000_000_000L, 1_700_000_000_000L)))
        // Future times (wrong clock) are dropped.
        assertEquals(1_600_000_000_000L, Rules.setupTime(listOf(1_600_000_000_000L, 1_900_000_000_000L), 1_800_000_000_000L))
        assertNull(Rules.setupTime(listOf(1_900_000_000_000L), 1_800_000_000_000L))
    }

    // ---- Formatting ----

    @Test fun formatting() {
        val utc = TimeZone.getTimeZone("UTC")
        assertEquals("1 Jan 2026", Rules.fmtDate(1_767_225_600_000L, En, utc))
        assertEquals("today", Rules.agoText(now - 1_000L, now))
        assertEquals("today", Rules.agoText(now + day, now))
        assertEquals("1 day ago", Rules.agoText(now - day, now))
        assertEquals("214 days ago", Rules.agoText(now - 214 * day, now))
        assertEquals("0h 5m", Rules.uptimeText(5 * 60_000L))
        assertEquals("2d 3h", Rules.uptimeText(51 * 3_600_000L))
        assertEquals("1 Oct 2026", Rules.formatPatch("2026-10-01"))
        assertEquals("", Rules.formatPatch(""))
        assertEquals("odd", Rules.formatPatch("odd"))
        assertEquals("2026-13-45", Rules.formatPatch("2026-13-45"))
    }

    // ---- Versions ----

    @Test fun isNewer() {
        assertTrue(Rules.isNewer("1.0", "1.1"))
        assertTrue(Rules.isNewer("1.9", "1.10"))
        assertTrue(Rules.isNewer("1.0", "2.0"))
        assertTrue(Rules.isNewer("v1.0", "1.0.1"))
        assertFalse(Rules.isNewer("1.1", "1.1"))
        assertFalse(Rules.isNewer("1.0", "1.0.0"))
        assertFalse(Rules.isNewer("1.2", "1.1"))
        assertFalse(Rules.isNewer("1.10", "1.9"))
        assertTrue(Rules.isNewer("1.0-dev", "1.1"))
    }

    @Test fun parseRelease() {
        assertEquals(Latest("1.2", "https://x"), Rules.parseRelease("v1.2", "https://x"))
        assertEquals(Latest("1.0", Updater.RELEASES_URL), Rules.parseRelease("1.0", ""))
        assertNull(Rules.parseRelease("", "u"))
        assertNull(Rules.parseRelease(null, "u"))
        assertNull(Rules.parseRelease("latest", "u"))
    }
}
