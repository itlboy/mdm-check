package com.itlboy.mdmcheck

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Runs on an emulator or real device: Collector must read data and never throw. */
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
        assertNotNull("Could not read the setup date", t)
        assertTrue(t!! <= System.currentTimeMillis())
    }

    @Test fun emulator_isNotManaged() {
        // A clean CI emulator must never be reported as managed.
        val r = Collector.run(ctx)
        assertEquals(4, r.sections.size)
        assertTrue("False MDM report: ${r.sub}", r.status != Status.BAD)
    }
}
