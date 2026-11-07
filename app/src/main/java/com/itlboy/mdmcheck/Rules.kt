package com.itlboy.mdmcheck

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

enum class Status { OK, BAD, WARN, INFO }
enum class Kind { MDM, DEVICE, HISTORY, SECURITY }

/** Một dòng trong trang con. `value` có thì hiện chữ bên phải, không thì hiện dấu ✓/✗. */
data class CheckRow(val name: String, val detail: String, val status: Status = Status.INFO, val value: String? = null)
data class Section(val kind: Kind, val title: String, val summary: String, val status: Status, val rows: List<CheckRow>)
data class Report(val status: Status, val headline: String, val sub: String, val sections: List<Section>)
data class DeviceSummary(val name: String, val android: String, val setup: Long?)

/** Dữ liệu thô đọc từ máy. Tách riêng để [Rules] test được mà không cần Android. */
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
    /** Tên app quản trị KHÔNG phải app hệ thống (agent MDM thường thuộc loại này). */
    val userAdmins: List<String>,
    /** Tên app quản trị là app hệ thống (Tìm thiết bị của Google/OPPO…): bình thường. */
    val systemAdmins: List<String>,
    val deviceOwner: String?,
    val orgOwned: Boolean,
    val profileOwner: String?,
    val profiles: Int,
    /** Số hồ sơ phụ là hồ sơ công việc; -1 = không phân định được. */
    val workProfiles: Int,
    val knownApps: List<String>,
    val blocked: List<String>,
    val bootState: String,
    val rooted: Boolean,
    val devOptions: Boolean,
    val adb: Boolean,
)

/** Toàn bộ logic kết luận. Hàm thuần: cùng [Facts] → cùng [Report]. */
object Rules {
    private const val YEAR_2015 = 1_420_070_400_000L

    fun fmtDate(t: Long, tz: TimeZone = TimeZone.getDefault()): String =
        SimpleDateFormat("dd/MM/yyyy", Locale.ROOT).apply { timeZone = tz }.format(Date(t))

    fun agoText(t: Long, now: Long): String {
        val d = TimeUnit.MILLISECONDS.toDays(now - t)
        return if (d <= 0) "hôm nay" else "$d ngày trước"
    }

    fun uptimeText(ms: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(ms)
        return if (hours >= 24) "${hours / 24} ngày ${hours % 24} giờ"
        else "$hours giờ ${TimeUnit.MILLISECONDS.toMinutes(ms) % 60} phút"
    }

    /** "2026-10-01" → "01/10/2026"; chuỗi lạ giữ nguyên. */
    fun formatPatch(p: String): String {
        val parts = p.split("-")
        return if (parts.size == 3 && parts.all { it.isNotEmpty() && it.all(Char::isDigit) })
            "${parts[2]}/${parts[1]}/${parts[0]}" else p
    }

    /**
     * Ưu tiên: tên thương mại của hãng ("Mi 10S" → "Xiaomi Mi 10S") → tên máy trong Cài đặt
     * (nếu không chỉ là mã model) → "Hãng + mã model".
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

    /** Lấy thời điểm sớm nhất hợp lệ; bỏ giá trị trước 2015 (ngày giả 2009 của ảnh hệ thống) và sau [now]. */
    fun setupTime(times: List<Long>, now: Long = Long.MAX_VALUE): Long? =
        times.filter { it > YEAR_2015 && it <= now }.minOrNull()

    /** "v0.1.12" → Latest("0.1.12", url). Tag rỗng hoặc không có số → null. */
    fun parseRelease(tag: String?, url: String?): Latest? {
        val v = tag?.trim()?.removePrefix("v")?.removePrefix("V") ?: return null
        if (v.isEmpty() || !v.first().isDigit()) return null
        return Latest(v, if (url.isNullOrBlank()) Updater.RELEASES_URL else url)
    }

    /** true nếu [latest] mới hơn [current]; so từng số, thiếu thì coi là 0 ("0.1" == "0.1.0"). */
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

    fun report(f: Facts): Report {
        val mdm = mdm(f)
        val sections = listOf(mdm, device(f), history(f), security(f))
        val bad = mdm.rows.filter { it.status == Status.BAD }
        val warn = mdm.rows.count { it.status == Status.WARN }
        return when {
            bad.isNotEmpty() -> Report(Status.BAD, "Máy đang bị quản lý", bad.first().detail, sections)
            warn > 0 -> Report(Status.WARN, "Cần xem thêm", "$warn mục nên kiểm tra lại", sections)
            else -> Report(Status.OK, "Máy sạch", "Không phát hiện MDM", sections)
        }
    }

    fun mdm(f: Facts): Section {
        val admin = when {
            f.userAdmins.isNotEmpty() ->
                CheckRow("Ứng dụng quản trị", f.userAdmins.joinToString() + " đang bật", Status.BAD)
            f.systemAdmins.isNotEmpty() ->
                CheckRow("Ứng dụng quản trị", "Chỉ có app hệ thống: " + f.systemAdmins.joinToString(), Status.OK)
            else -> CheckRow("Ứng dụng quản trị", "Không có app nào đang bật", Status.OK)
        }
        val owner = when {
            f.deviceOwner != null -> CheckRow("Máy do tổ chức quản lý", "Quản lý bởi ${f.deviceOwner}", Status.BAD)
            f.orgOwned -> CheckRow("Máy do tổ chức quản lý", "Máy thuộc sở hữu của tổ chức", Status.BAD)
            else -> CheckRow("Máy do tổ chức quản lý", "Không có tổ chức nào", Status.OK)
        }
        // Hồ sơ phụ thường là Ứng dụng kép / Không gian thứ hai: chỉ báo đỏ khi chắc là hồ sơ công việc.
        val extra = f.profiles - 1
        val profile = when {
            f.profileOwner != null -> CheckRow("Hồ sơ công việc", "Quản lý bởi ${f.profileOwner}", Status.BAD)
            f.workProfiles > 0 -> CheckRow("Hồ sơ công việc", "Máy đang có hồ sơ công việc", Status.BAD)
            extra > 0 && f.workProfiles < 0 -> CheckRow("Hồ sơ công việc",
                "Có $extra hồ sơ phụ, không xác định được loại", Status.WARN)
            extra > 0 -> CheckRow("Hồ sơ công việc",
                "Không có. Hồ sơ phụ là Ứng dụng kép / Không gian thứ hai", Status.OK)
            else -> CheckRow("Hồ sơ công việc", "Chỉ có hồ sơ cá nhân", Status.OK)
        }
        val known = if (f.knownApps.isNotEmpty())
            CheckRow("App MDM / khoá trả góp", "Đã cài: " + f.knownApps.joinToString(), Status.BAD)
        else CheckRow("App MDM / khoá trả góp", "Không thấy app nào", Status.OK)
        val blocked = if (f.blocked.isNotEmpty())
            CheckRow("Tính năng bị chặn", "Bị chặn: " + f.blocked.joinToString(), Status.BAD)
        else CheckRow("Tính năng bị chặn", "Reset, gỡ app, USB đều được", Status.OK)

        val rows = listOf(admin, owner, profile, known, blocked)
        val bad = rows.count { it.status == Status.BAD }
        val warn = rows.count { it.status == Status.WARN }
        val (status, summary) = when {
            bad > 0 -> Status.BAD to "$bad/${rows.size} mục có vấn đề"
            warn > 0 -> Status.WARN to "$warn/${rows.size} mục cần xem"
            else -> Status.OK to "${rows.size}/${rows.size} mục sạch"
        }
        return Section(Kind.MDM, "Dấu hiệu quản lý", summary, status, rows)
    }

    fun device(f: Facts): Section {
        val rows = listOf(
            CheckRow("Tên máy", f.manufacturer, value = f.deviceName),
            CheckRow("Mã model", "Mã nhà sản xuất đặt", value = f.model),
            CheckRow("Phiên bản", "API ${f.sdk}", value = "Android ${f.release}"),
            CheckRow("Bản vá bảo mật", "Càng mới càng an toàn", value = formatPatch(f.securityPatch)),
            CheckRow("Bản phần mềm", f.display),
        )
        return Section(Kind.DEVICE, "Thông tin máy", "${f.deviceName} · Android ${f.release}", Status.INFO, rows)
    }

    fun history(f: Facts): Section {
        val setup = f.setupTime
        val rows = listOf(
            if (setup != null) CheckRow("Thiết lập máy",
                "Lần đầu bật máy hoặc lần reset gần nhất · ${agoText(setup, f.now)}", value = fmtDate(setup))
            else CheckRow("Thiết lập máy", "Không đọc được trên máy này", value = "Không rõ"),
            CheckRow("Ngày build phần mềm", "Bản hệ điều hành đang cài", value = fmtDate(f.buildTime)),
            CheckRow("Chạy liên tục", "Từ lần khởi động gần nhất", value = uptimeText(f.uptimeMs)),
        )
        val summary = setup?.let { "Thiết lập ${agoText(it, f.now)}" } ?: "Không rõ ngày thiết lập"
        return Section(Kind.HISTORY, "Lịch sử máy", summary, Status.INFO, rows)
    }

    fun bootRow(state: String): CheckRow = when (state) {
        "green" -> CheckRow("Bootloader", "Phần mềm gốc của hãng", Status.OK, "Đã khoá")
        "orange" -> CheckRow("Bootloader", "Phần mềm có thể đã bị sửa", Status.WARN, "Đã mở khoá")
        "yellow" -> CheckRow("Bootloader", "Đang dùng khoá ký riêng", Status.WARN, "Khoá riêng")
        else -> CheckRow("Bootloader", "Máy không cho đọc trạng thái", Status.INFO, "Không rõ")
    }

    fun security(f: Facts): Section {
        val boot = bootRow(f.bootState)
        val root = if (f.rooted) CheckRow("Root", "Có dấu vết root trên máy", Status.WARN, "Có dấu hiệu")
        else CheckRow("Root", "Không tìm thấy dấu vết root", Status.OK, "Không")
        val dev = if (f.devOptions) CheckRow("Tuỳ chọn nhà phát triển", "Nên tắt nếu không dùng", Status.WARN, "Đang bật")
        else CheckRow("Tuỳ chọn nhà phát triển", "Đang tắt", Status.OK, "Tắt")
        val adb = if (f.adb) CheckRow("Gỡ lỗi USB", "Nên tắt nếu không dùng", Status.WARN, "Đang bật")
        else CheckRow("Gỡ lỗi USB", "Đang tắt", Status.OK, "Tắt")
        val rows = listOf(boot, root, dev, adb)
        val bootShort = when (boot.status) {
            Status.OK -> "Bootloader khoá"
            Status.WARN -> "Bootloader mở"
            else -> "Bootloader không rõ"
        }
        val summary = "$bootShort · " + if (f.rooted) "có root" else "không root"
        val status = if (rows.any { it.status == Status.WARN }) Status.WARN else Status.OK
        return Section(Kind.SECURITY, "Bảo mật khác", summary, status, rows)
    }
}
