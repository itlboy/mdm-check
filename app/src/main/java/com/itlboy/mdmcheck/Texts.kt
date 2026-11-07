package com.itlboy.mdmcheck

import java.util.Locale

/**
 * Every user-visible string. Plain Kotlin (not Android resources) so [Rules] stays testable on the JVM.
 * The app follows the phone language: Vietnamese phones get [Vi], everything else [En].
 */
interface Texts {
    val datePattern: String
    val dateLocale: Locale

    // Relative time
    val today: String
    fun daysAgo(d: Long): String
    fun uptimeDays(d: Long, h: Long): String
    fun uptimeHours(h: Long, m: Long): String
    fun items(n: Int): String

    // Verdict
    val managed: String
    val review: String
    fun reviewSub(n: Int): String
    val clean: String
    val noMdm: String

    // Management signs
    val mdmTitle: String
    fun mdmIssues(bad: Int, of: Int): String
    fun mdmReview(warn: Int, of: Int): String
    fun mdmClear(of: Int): String
    val admins: String
    fun adminsActive(names: String): String
    fun adminsSystemOnly(names: String): String
    val adminsNone: String
    val orgOwned: String
    fun managedBy(name: String): String
    val ownedByOrg: String
    val noOrg: String
    val workProfile: String
    val workProfilePresent: String
    fun unknownProfiles(n: Int): String
    val dualApps: String
    val personalOnly: String
    val knownApps: String
    fun installed(names: String): String
    val noneFound: String
    val blockedFeatures: String
    fun blocked(names: String): String
    val allAllowed: String
    fun restriction(key: String): String

    // Device info
    val deviceTitle: String
    val name: String
    val model: String
    val modelDetail: String
    val version: String
    val patch: String
    val patchDetail: String
    val build: String

    // History
    val historyTitle: String
    val setupDate: String
    fun setupDetail(ago: String): String
    val notReadable: String
    val unknown: String
    val buildDate: String
    val buildDateDetail: String
    val uptime: String
    val uptimeDetail: String
    fun setUpAgo(ago: String): String
    val setupUnknown: String

    // Other security
    val securityTitle: String
    val bootStock: String
    val bootLocked: String
    val bootModified: String
    val bootUnlocked: String
    val bootCustom: String
    val bootCustomValue: String
    val bootHidden: String
    val securityOk: String
    fun toReview(n: Int): String
    val root: String
    val rootFound: String
    val rootLikely: String
    val rootNone: String
    val no: String
    val devOptions: String
    val usbDebugging: String
    val turnOff: String
    val turnedOff: String
    val on: String
    val off: String

    // Screens
    val check: String
    val checkAgain: String
    val back: String
    fun homeSetup(date: String): String
    fun lineSetup(date: String): String
    val homeSetupUnknown: String
    val infoTitle: String
    val infoBody1: String
    val infoBody2: String
    val gotIt: String
    val zeroTouchNote: String
    val badgeClear: String
    val badgeIssue: String
    val badgeReview: String
    fun pillChecking(v: String): String
    fun pillOffline(v: String): String
    fun pillUpdate(v: String, latest: String): String
    fun pillUpToDate(v: String): String

    companion object {
        fun forLocale(l: Locale): Texts = if (l.language == "vi") Vi else En
        fun current(): Texts = forLocale(Locale.getDefault())
    }
}

object En : Texts {
    override val datePattern = "d MMM yyyy"
    override val dateLocale: Locale = Locale.US
    override val today = "today"
    override fun daysAgo(d: Long) = if (d == 1L) "1 day ago" else "$d days ago"
    override fun uptimeDays(d: Long, h: Long) = "${d}d ${h}h"
    override fun uptimeHours(h: Long, m: Long) = "${h}h ${m}m"
    override fun items(n: Int) = if (n == 1) "1 item" else "$n items"

    override val managed = "Device is managed"
    override val review = "Worth a closer look"
    override fun reviewSub(n: Int) = "${items(n)} to review"
    override val clean = "Device is clean"
    override val noMdm = "No MDM detected"

    override val mdmTitle = "Management signs"
    override fun mdmIssues(bad: Int, of: Int) = "$bad/$of with issues"
    override fun mdmReview(warn: Int, of: Int) = "$warn/$of to review"
    override fun mdmClear(of: Int) = "$of/$of clear"
    override val admins = "Device admin apps"
    override fun adminsActive(names: String) = "$names active"
    override fun adminsSystemOnly(names: String) = "System apps only: $names"
    override val adminsNone = "None active"
    override val orgOwned = "Organization-owned"
    override fun managedBy(name: String) = "Managed by $name"
    override val ownedByOrg = "Owned by an organization"
    override val noOrg = "No organization"
    override val workProfile = "Work profile"
    override val workProfilePresent = "A work profile is present"
    override fun unknownProfiles(n: Int) = (if (n == 1) "1 extra profile" else "$n extra profiles") + " of unknown type"
    override val dualApps = "None. Extra profile is Dual Apps / Second Space"
    override val personalOnly = "Personal profile only"
    override val knownApps = "MDM / payment-lock apps"
    override fun installed(names: String) = "Installed: $names"
    override val noneFound = "None found"
    override val blockedFeatures = "Blocked features"
    override fun blocked(names: String) = "Blocked: $names"
    override val allAllowed = "Reset, uninstall and USB all allowed"
    override fun restriction(key: String) = when (key) {
        "no_factory_reset" -> "factory reset"
        "no_uninstall_apps" -> "uninstalling apps"
        "no_debugging_features" -> "USB debugging"
        "no_usb_file_transfer" -> "USB file transfer"
        else -> key
    }

    override val deviceTitle = "Device info"
    override val name = "Name"
    override val model = "Model"
    override val modelDetail = "Manufacturer code"
    override val version = "Version"
    override val patch = "Security patch"
    override val patchDetail = "Newer is safer"
    override val build = "Build"

    override val historyTitle = "Device history"
    override val setupDate = "Setup date"
    override fun setupDetail(ago: String) = "First boot or last factory reset · $ago"
    override val notReadable = "Not readable on this device"
    override val unknown = "Unknown"
    override val buildDate = "OS build date"
    override val buildDateDetail = "Installed system version"
    override val uptime = "Uptime"
    override val uptimeDetail = "Since last restart"
    override fun setUpAgo(ago: String) = "Set up $ago"
    override val setupUnknown = "Setup date unknown"

    override val securityTitle = "Other security"
    override val bootStock = "Stock manufacturer software"
    override val bootLocked = "Locked"
    override val bootModified = "Software may be modified"
    override val bootUnlocked = "Unlocked"
    override val bootCustom = "Signed with a custom key"
    override val bootCustomValue = "Custom key"
    override val bootHidden = "Device doesn't expose this"
    override val securityOk = "No risks found"
    override fun toReview(n: Int) = "${items(n)} to review"
    override val root = "Root"
    override val rootFound = "Root traces found"
    override val rootLikely = "Likely"
    override val rootNone = "No root traces"
    override val no = "No"
    override val devOptions = "Developer options"
    override val usbDebugging = "USB debugging"
    override val turnOff = "Turn off if unused"
    override val turnedOff = "Turned off"
    override val on = "On"
    override val off = "Off"

    override val check = "Check"
    override val checkAgain = "Check again"
    override val back = "Back"
    override fun homeSetup(date: String) = "Setup date $date"
    override fun lineSetup(date: String) = "Set up $date"
    override val homeSetupUnknown = "Setup date unknown"
    override val infoTitle = "What is the setup date?"
    override val infoBody1 = "The day this phone was last set up: its first boot as a new phone, or its most recent factory reset."
    override val infoBody2 = "If it is much earlier than the day you bought it, the phone has probably been used before."
    override val gotIt = "Got it"
    override val zeroTouchNote = "Can't detect IMEI-based MDM (zero-touch). To be sure: factory reset, then set up on Wi‑Fi."
    override val badgeClear = "Clear"
    override val badgeIssue = "Issue"
    override val badgeReview = "Review"
    override fun pillChecking(v: String) = "v$v · Checking…"
    override fun pillOffline(v: String) = "v$v · Offline"
    override fun pillUpdate(v: String, latest: String) = "v$v · Update to $latest"
    override fun pillUpToDate(v: String) = "v$v · Up to date"
}

object Vi : Texts {
    override val datePattern = "dd/MM/yyyy"
    override val dateLocale: Locale = Locale.ROOT
    override val today = "hôm nay"
    override fun daysAgo(d: Long) = "$d ngày trước"
    override fun uptimeDays(d: Long, h: Long) = "$d ngày $h giờ"
    override fun uptimeHours(h: Long, m: Long) = "$h giờ $m phút"
    override fun items(n: Int) = "$n mục"

    override val managed = "Máy đang bị quản lý"
    override val review = "Cần xem thêm"
    override fun reviewSub(n: Int) = "${items(n)} nên kiểm tra lại"
    override val clean = "Máy sạch"
    override val noMdm = "Không phát hiện MDM"

    override val mdmTitle = "Dấu hiệu quản lý"
    override fun mdmIssues(bad: Int, of: Int) = "$bad/$of mục có vấn đề"
    override fun mdmReview(warn: Int, of: Int) = "$warn/$of mục cần xem"
    override fun mdmClear(of: Int) = "$of/$of mục sạch"
    override val admins = "Ứng dụng quản trị"
    override fun adminsActive(names: String) = "$names đang bật"
    override fun adminsSystemOnly(names: String) = "Chỉ có app hệ thống: $names"
    override val adminsNone = "Không có app nào đang bật"
    override val orgOwned = "Máy do tổ chức quản lý"
    override fun managedBy(name: String) = "Quản lý bởi $name"
    override val ownedByOrg = "Máy thuộc sở hữu của tổ chức"
    override val noOrg = "Không có tổ chức nào"
    override val workProfile = "Hồ sơ công việc"
    override val workProfilePresent = "Máy đang có hồ sơ công việc"
    override fun unknownProfiles(n: Int) = "Có $n hồ sơ phụ, không xác định được loại"
    override val dualApps = "Không có. Hồ sơ phụ là Ứng dụng kép / Không gian thứ hai"
    override val personalOnly = "Chỉ có hồ sơ cá nhân"
    override val knownApps = "App MDM / khoá trả góp"
    override fun installed(names: String) = "Đã cài: $names"
    override val noneFound = "Không thấy app nào"
    override val blockedFeatures = "Tính năng bị chặn"
    override fun blocked(names: String) = "Bị chặn: $names"
    override val allAllowed = "Reset, gỡ app, USB đều được"
    override fun restriction(key: String) = when (key) {
        "no_factory_reset" -> "khôi phục gốc"
        "no_uninstall_apps" -> "gỡ app"
        "no_debugging_features" -> "gỡ lỗi USB"
        "no_usb_file_transfer" -> "chép file qua USB"
        else -> key
    }

    override val deviceTitle = "Thông tin máy"
    override val name = "Tên máy"
    override val model = "Mã model"
    override val modelDetail = "Mã nhà sản xuất đặt"
    override val version = "Phiên bản"
    override val patch = "Bản vá bảo mật"
    override val patchDetail = "Càng mới càng an toàn"
    override val build = "Bản phần mềm"

    override val historyTitle = "Lịch sử máy"
    override val setupDate = "Thiết lập máy"
    override fun setupDetail(ago: String) = "Lần đầu bật máy hoặc lần reset gần nhất · $ago"
    override val notReadable = "Không đọc được trên máy này"
    override val unknown = "Không rõ"
    override val buildDate = "Ngày build phần mềm"
    override val buildDateDetail = "Bản hệ điều hành đang cài"
    override val uptime = "Chạy liên tục"
    override val uptimeDetail = "Từ lần khởi động gần nhất"
    override fun setUpAgo(ago: String) = "Thiết lập $ago"
    override val setupUnknown = "Không rõ ngày thiết lập"

    override val securityTitle = "Bảo mật khác"
    override val bootStock = "Phần mềm gốc của hãng"
    override val bootLocked = "Đã khoá"
    override val bootModified = "Phần mềm có thể đã bị sửa"
    override val bootUnlocked = "Đã mở khoá"
    override val bootCustom = "Đang dùng khoá ký riêng"
    override val bootCustomValue = "Khoá riêng"
    override val bootHidden = "Máy không cho đọc trạng thái"
    override val securityOk = "Không thấy rủi ro"
    override fun toReview(n: Int) = "$n mục nên xem"
    override val root = "Root"
    override val rootFound = "Có dấu vết root trên máy"
    override val rootLikely = "Có dấu hiệu"
    override val rootNone = "Không tìm thấy dấu vết root"
    override val no = "Không"
    override val devOptions = "Tuỳ chọn nhà phát triển"
    override val usbDebugging = "Gỡ lỗi USB"
    override val turnOff = "Nên tắt nếu không dùng"
    override val turnedOff = "Đang tắt"
    override val on = "Đang bật"
    override val off = "Tắt"

    override val check = "Kiểm tra"
    override val checkAgain = "Kiểm tra lại"
    override val back = "Quay lại"
    override fun homeSetup(date: String) = "Thiết lập máy $date"
    override fun lineSetup(date: String) = "Thiết lập $date"
    override val homeSetupUnknown = "Thiết lập máy không rõ"
    override val infoTitle = "Thiết lập máy là gì?"
    override val infoBody1 = "Là ngày máy được cài đặt lần gần nhất: lần đầu bật máy mới, hoặc lần khôi phục cài đặt gốc gần nhất."
    override val infoBody2 = "Nếu ngày này sớm hơn nhiều so với ngày bạn mua, máy có thể đã qua tay người khác."
    override val gotIt = "Đã hiểu"
    override val zeroTouchNote = "Chưa kiểm được MDM gắn theo IMEI (zero-touch). Muốn chắc: reset máy rồi thiết lập lại có Wi‑Fi."
    override val badgeClear = "Sạch"
    override val badgeIssue = "Có vấn đề"
    override val badgeReview = "Cần xem"
    override fun pillChecking(v: String) = "v$v · Đang kiểm tra…"
    override fun pillOffline(v: String) = "v$v · Không có mạng"
    override fun pillUpdate(v: String, latest: String) = "v$v · Cập nhật lên $latest"
    override fun pillUpToDate(v: String) = "v$v · Mới nhất"
}
