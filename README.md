# Cozy MDM Check

App Android nhỏ: bấm một nút để biết máy có đang bị **MDM** (quản lý thiết bị của công ty),
**hồ sơ công việc** hay **khoá trả góp** không. Hữu ích khi mua máy cũ hoặc mua theo lô.

**Tải APK:** [Releases mới nhất](https://github.com/itlboy/mdm-check/releases/latest)

## App kiểm tra gì

| Nhóm | Nội dung |
|---|---|
| Dấu hiệu quản lý | App quản trị thiết bị (bỏ qua app hệ thống như Tìm thiết bị), máy do tổ chức quản lý (device owner), hồ sơ công việc, app MDM/khoá trả góp phổ biến, tính năng bị chặn (reset, gỡ app, USB) |
| Thông tin máy | Tên máy, mã model, Android, bản vá bảo mật, bản phần mềm |
| Lịch sử máy | Ngày thiết lập máy (lần đầu bật hoặc lần reset gần nhất), ngày build phần mềm, thời gian chạy liên tục |
| Bảo mật khác | Bootloader, root, tuỳ chọn nhà phát triển, gỡ lỗi USB |

App chỉ dùng mạng để hỏi GitHub phiên bản mới nhất (chấm xanh/đỏ ở cuối màn hình).
Kết quả kiểm tra không gửi đi đâu.

## Giới hạn cần biết

- **Không thấy được zero-touch** (MDM gắn theo IMEI, chỉ kích hoạt khi máy được reset).
  Muốn chắc chắn: khôi phục cài đặt gốc rồi thiết lập lại có Wi‑Fi.
- **Không đọc được IMEI** — Android 10+ chặn app thường.
- "Ngày thiết lập máy" là ước tính từ hệ thống, không phải ngày kích hoạt bảo hành của hãng.
- Hồ sơ phụ có thể là "nhân bản ứng dụng" của ColorOS, nên chỉ báo vàng, không báo đỏ.

## Phát triển

Kotlin + Jetpack Compose, minSdk 26. Logic kết luận nằm trọn trong `Rules.kt` (hàm thuần, test trên JVM);
`Checker.kt` (`Collector`) chỉ đọc dữ liệu từ máy.

```sh
gradle testDebugUnitTest          # unit test cho Rules
gradle connectedDebugAndroidTest  # test trên máy/giả lập đang cắm
gradle assembleRelease            # APK ở app/build/outputs/apk/release/
```

CI (`.github/workflows/build.yml`): mỗi lần push lên `main` chạy unit test + test trên giả lập Android 14;
qua hết thì build APK và tạo release `v1.0`, `v1.1`, … tự tăng.

APK phát hành được ký bằng một khoá cố định (secret `SIGNING_KEYSTORE_B64` + `SIGNING_PASSWORD`,
PKCS12, alias `mdmcheck`), nên bản mới cài đè được lên bản cũ. Mất khoá này thì người dùng phải gỡ app
cũ mới cài được bản ký bằng khoá mới.
