package com.itlboy.mdmcheck

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class RulesTest {
    private val day = 86_400_000L
    private val now = 1_793_000_000_000L // 11/2026

    /** Máy sạch: không admin, không chủ quản, một hồ sơ, bootloader khoá. */
    private val clean = Facts(
        deviceName = "OPPO Find X9 Ultra", manufacturer = "OPPO", model = "PHZ110", release = "16", sdk = 36,
        securityPatch = "2026-10-01", display = "PHZ110_16.0.0.100", buildTime = now - 60 * day,
        setupTime = now - 2 * day, uptimeMs = 3 * 3_600_000L, now = now,
        userAdmins = emptyList(), systemAdmins = emptyList(), deviceOwner = null, orgOwned = false,
        profileOwner = null, profiles = 1, knownApps = emptyList(), blocked = emptyList(),
        bootState = "green", rooted = false, devOptions = false, adb = false,
    )

    private fun mdmRow(f: Facts, name: String) = Rules.mdm(f).rows.first { it.name == name }

    // ---- Kết luận chung ----

    @Test fun cleanDevice_isClean() {
        val r = Rules.report(clean)
        assertEquals(Status.OK, r.status)
        assertEquals("Máy sạch", r.headline)
        assertEquals("5/5 mục sạch", r.sections[0].summary)
        assertEquals(listOf(Kind.MDM, Kind.DEVICE, Kind.HISTORY, Kind.SECURITY), r.sections.map { it.kind })
    }

    @Test fun systemAdminOnly_isStillClean() {
        val f = clean.copy(systemAdmins = listOf("Google Play services"))
        assertEquals(Status.OK, Rules.report(f).status)
        assertTrue(mdmRow(f, "Ứng dụng quản trị").detail.contains("Google Play services"))
    }

    @Test fun userAdmin_isManaged() {
        val f = clean.copy(userAdmins = listOf("Microsoft Intune"))
        val r = Rules.report(f)
        assertEquals(Status.BAD, r.status)
        assertEquals("Máy đang bị quản lý", r.headline)
        assertEquals("Microsoft Intune đang bật", r.sub)
    }

    @Test fun deviceOwner_isManaged() {
        val f = clean.copy(deviceOwner = "Hexnode MDM")
        assertEquals(Status.BAD, mdmRow(f, "Máy do tổ chức quản lý").status)
        assertEquals(Status.BAD, Rules.report(f).status)
    }

    @Test fun orgOwned_isManaged() {
        val f = clean.copy(orgOwned = true)
        assertEquals(Status.BAD, mdmRow(f, "Máy do tổ chức quản lý").status)
    }

    @Test fun profileOwner_isManaged() {
        val f = clean.copy(profileOwner = "Intune")
        assertEquals(Status.BAD, mdmRow(f, "Hồ sơ công việc").status)
    }

    @Test fun extraProfile_isOnlyWarning() {
        // ColorOS "nhân bản app" cũng tạo hồ sơ phụ: không được báo đỏ.
        val f = clean.copy(profiles = 2)
        val r = Rules.report(f)
        assertEquals(Status.WARN, r.status)
        assertEquals("Cần xem thêm", r.headline)
        assertEquals("1/5 mục cần xem", r.sections[0].summary)
    }

    @Test fun knownApp_isManaged() {
        val f = clean.copy(knownApps = listOf("PayJoy (khoá trả góp)"))
        assertEquals(Status.BAD, mdmRow(f, "App MDM / khoá trả góp").status)
    }

    @Test fun blockedFeatures_isManaged() {
        val f = clean.copy(blocked = listOf("khôi phục gốc", "gỡ app"))
        val row = mdmRow(f, "Tính năng bị chặn")
        assertEquals(Status.BAD, row.status)
        assertEquals("Bị chặn: khôi phục gốc, gỡ app", row.detail)
    }

    @Test fun badBeatsWarn_andCountsAll() {
        val f = clean.copy(deviceOwner = "X", blocked = listOf("gỡ app"), profiles = 3)
        val r = Rules.report(f)
        assertEquals(Status.BAD, r.status)
        assertEquals("2/5 mục có vấn đề", r.sections[0].summary)
    }

    @Test fun securityWarnings_doNotChangeMdmVerdict() {
        val f = clean.copy(rooted = true, adb = true, devOptions = true, bootState = "orange")
        val r = Rules.report(f)
        assertEquals(Status.OK, r.status)
        assertEquals(Status.WARN, r.sections[3].status)
        assertEquals("Bootloader mở · có root", r.sections[3].summary)
    }

    // ---- Bảo mật ----

    @Test fun bootStates() {
        assertEquals("Đã khoá", Rules.bootRow("green").value)
        assertEquals(Status.WARN, Rules.bootRow("orange").status)
        assertEquals(Status.WARN, Rules.bootRow("yellow").status)
        assertEquals(Status.INFO, Rules.bootRow("").status)
        assertEquals("Bootloader khoá · không root", Rules.security(clean).summary)
        assertEquals("Bootloader không rõ · không root", Rules.security(clean.copy(bootState = "")).summary)
    }

    // ---- Thông tin máy / lịch sử ----

    @Test fun deviceSection() {
        val s = Rules.device(clean)
        assertEquals("OPPO Find X9 Ultra · Android 16", s.summary)
        assertEquals("01/10/2026", s.rows.first { it.name == "Bản vá bảo mật" }.value)
    }

    @Test fun historySection() {
        val s = Rules.history(clean)
        assertEquals("Thiết lập 2 ngày trước", s.summary)
        assertEquals("3 giờ 0 phút", s.rows.first { it.name == "Chạy liên tục" }.value)
    }

    @Test fun historyWithoutSetupTime() {
        val s = Rules.history(clean.copy(setupTime = null))
        assertEquals("Không rõ ngày thiết lập", s.summary)
        assertEquals("Không rõ", s.rows.first().value)
    }

    @Test fun deviceName_fallbacks() {
        assertEquals("OPPO Find X9 Ultra", Rules.deviceName(" OPPO Find X9 Ultra ", "OPPO", "PHZ110"))
        assertEquals("OPPO PHZ110", Rules.deviceName(null, "OPPO", "PHZ110"))
        assertEquals("Google Pixel 9", Rules.deviceName("", "google", "Pixel 9"))
        assertEquals("Google Pixel 9", Rules.deviceName(null, "Google", "Pixel 9"))
        assertEquals("Samsung SM-S928B", Rules.deviceName("  ", "samsung", "SM-S928B"))
        assertEquals("OPPO CPH2581", Rules.deviceName(null, "OPPO", "OPPO CPH2581"))
    }

    @Test fun setupTime_picksEarliestValid() {
        assertEquals(1_600_000_000_000L, Rules.setupTime(listOf(0L, 1_700_000_000_000L, 1_600_000_000_000L)))
        assertNull(Rules.setupTime(listOf(0L, 1_000L)))
        assertNull(Rules.setupTime(emptyList()))
    }

    // ---- Định dạng ----

    @Test fun formatting() {
        val utc = TimeZone.getTimeZone("UTC")
        assertEquals("01/01/2026", Rules.fmtDate(1_767_225_600_000L, utc))
        assertEquals("hôm nay", Rules.agoText(now - 1_000L, now))
        assertEquals("hôm nay", Rules.agoText(now + day, now))
        assertEquals("214 ngày trước", Rules.agoText(now - 214 * day, now))
        assertEquals("0 giờ 5 phút", Rules.uptimeText(5 * 60_000L))
        assertEquals("2 ngày 3 giờ", Rules.uptimeText((51 * 3_600_000L)))
        assertEquals("01/10/2026", Rules.formatPatch("2026-10-01"))
        assertEquals("", Rules.formatPatch(""))
        assertEquals("lạ", Rules.formatPatch("lạ"))
        assertEquals("a-b-c", Rules.formatPatch("a-b-c"))
    }

    // ---- Phiên bản ----

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
