package com.itlboy.mdmcheck

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Đi hết luồng như người dùng: bấm Kiểm tra → kết quả → trang con → quay lại → kiểm tra lại. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class AppFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val anyVerdict = hasText("Máy sạch") or hasText("Cần xem thêm") or hasText("Máy đang bị quản lý")

    @Test fun homeShowsDeviceAndVersion() {
        rule.onNodeWithText("Cozy MDM Check").assertIsDisplayed()
        rule.onNodeWithText("Kiểm tra").assertIsDisplayed()
        rule.onNodeWithText("Phiên bản ${BuildConfig.VERSION_NAME}", substring = true).assertIsDisplayed()
    }

    @Test fun infoSheetOpensAndCloses() {
        rule.onNodeWithContentDescription("Thiết lập máy là gì?").performClick()
        rule.onNodeWithText("Đã hiểu").assertIsDisplayed().performClick()
        rule.waitUntilDoesNotExist(hasText("Đã hiểu"), 5_000)
    }

    @Test fun fullFlow() {
        rule.onNodeWithText("Kiểm tra").performClick()
        rule.waitUntilAtLeastOneExists(anyVerdict, 10_000)

        for (title in listOf("Dấu hiệu quản lý", "Thông tin máy", "Lịch sử máy", "Bảo mật khác")) {
            rule.onNodeWithText(title).assertIsDisplayed()
        }

        rule.onNodeWithText("Dấu hiệu quản lý").performClick()
        rule.onNodeWithText("Ứng dụng quản trị").assertIsDisplayed()
        rule.onNodeWithText("Tính năng bị chặn").assertIsDisplayed()
        rule.onNodeWithContentDescription("Quay lại").performClick()

        rule.onNodeWithText("Lịch sử máy").performClick()
        rule.onNodeWithText("Thiết lập máy").assertIsDisplayed()
        rule.onNodeWithContentDescription("Quay lại").performClick()

        rule.onNodeWithText("Kiểm tra lại").performClick()
        rule.waitUntilAtLeastOneExists(anyVerdict, 10_000)
    }
}
