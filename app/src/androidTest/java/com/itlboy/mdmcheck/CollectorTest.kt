package com.itlboy.mdmcheck

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Chạy trên giả lập/máy thật: Collector phải đọc được dữ liệu và không bao giờ ném lỗi. */
@RunWith(AndroidJUnit4::class)
class CollectorTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun collect_readsDevice() {
        val f = Collector.collect(ctx)
        assertTrue(f.deviceName.isNotBlank())
        assertTrue(f.model.isNotBlank())
        assertTrue(f.sdk >= 26)
        assertTrue(f.uptimeMs > 0)
        assertTrue(f.profiles >= 1)
    }

    @Test fun setupTime_isReadableAndInThePast() {
        val t = Collector.setupTime(ctx)
        assertNotNull("Không đọc được ngày thiết lập máy", t)
        assertTrue(t!! <= System.currentTimeMillis())
    }

    @Test fun emulator_isNotManaged() {
        // Giả lập sạch trong CI: không được báo nhầm là có MDM.
        val r = Collector.run(ctx)
        assertEquals(4, r.sections.size)
        assertTrue("Báo nhầm có MDM: ${r.sub}", r.status != Status.BAD)
    }
}
