package com.itlboy.mdmcheck

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

/** The app follows the phone language: Vietnamese phones read Vietnamese, everyone else English. */
class TextsTest {
    private val day = 86_400_000L
    private val now = 1_793_000_000_000L

    private val clean = Facts(
        deviceName = "Xiaomi Mi 10S", manufacturer = "Xiaomi", model = "M2102J2SC", release = "11", sdk = 30,
        securityPatch = "2026-10-01", display = "RKQ1", buildTime = now - 60 * day,
        setupTime = now - 2 * day, uptimeMs = 51 * 3_600_000L, now = now,
        userAdmins = emptyList(), systemAdmins = emptyList(), deviceOwner = null, orgOwned = false,
        profileOwner = null, profiles = 2, workProfiles = 0, knownApps = emptyList(), blocked = emptyList(),
        bootState = "green", rooted = false, devOptions = false, adb = false,
    )

    @Test fun picksLanguageFromLocale() {
        assertSame(Vi, Texts.forLocale(Locale("vi", "VN")))
        assertSame(Vi, Texts.forLocale(Locale("vi")))
        assertSame(En, Texts.forLocale(Locale.US))
        assertSame(En, Texts.forLocale(Locale.JAPAN))
    }

    @Test fun vietnameseReport() {
        val r = Rules.report(clean, Vi)
        assertEquals("Máy sạch", r.headline)
        assertEquals("Không phát hiện MDM", r.sub)
        assertEquals(listOf("Dấu hiệu quản lý", "Thông tin máy", "Lịch sử máy", "Bảo mật khác"), r.sections.map { it.title })
        assertEquals("5/5 mục sạch", r.sections[0].summary)
        assertEquals("Thiết lập 2 ngày trước", r.sections[2].summary)
        assertEquals("2 ngày 3 giờ", r.sections[2].rows.last().value)
        assertEquals("Không thấy rủi ro", r.sections[3].summary)
        assertEquals("Android 11", r.sections[1].summary)
        // Dual Apps on MIUI must read as clean, in Vietnamese too.
        assertEquals("Không có. Hồ sơ phụ là Ứng dụng kép / Không gian thứ hai", r.sections[0].rows[2].detail)
    }

    @Test fun vietnameseManagedAndBlocked() {
        val r = Rules.report(clean.copy(blocked = listOf("no_factory_reset", "no_usb_file_transfer")), Vi)
        assertEquals("Máy đang bị quản lý", r.headline)
        assertEquals("Bị chặn: khôi phục gốc, chép file qua USB", r.sub)
        assertEquals("1/5 mục có vấn đề", r.sections[0].summary)
    }

    @Test fun vietnameseDates() {
        val utc = TimeZone.getTimeZone("UTC")
        assertEquals("01/01/2026", Rules.fmtDate(1_767_225_600_000L, Vi, utc))
        assertEquals("01/10/2026", Rules.formatPatch("2026-10-01", Vi))
        assertEquals("hôm nay", Rules.agoText(now, now, Vi))
        assertEquals("214 ngày trước", Rules.agoText(now - 214 * day, now, Vi))
    }

    @Test fun unknownRestrictionKeyIsShownAsIs() {
        assertEquals("no_something_new", En.restriction("no_something_new"))
        assertEquals("no_something_new", Vi.restriction("no_something_new"))
    }

    @Test fun pillsAreShort() {
        // The version pill must fit on one line on a narrow phone.
        for (t in listOf(En, Vi)) {
            for (s in listOf(t.pillChecking("1.10"), t.pillOffline("1.10"), t.pillUpdate("1.10", "1.11"), t.pillUpToDate("1.10"))) {
                assertTrue("Too long for one line: \"$s\" (${s.length})", s.length <= 28)
            }
        }
    }
}
