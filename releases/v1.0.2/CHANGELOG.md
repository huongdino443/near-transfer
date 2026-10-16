# Near Transfer 1.0.2 — Android và Windows

## Android

- Trình chọn thư mục nhận mở thành màn hình riêng toàn màn hình; đã xác nhận hoạt động trên Android 2.3.6/API 10.
- Bỏ tiêu đề thừa ở màn hình chọn thư mục.
- Sửa crash `NoSuchMethodError` trên Android 2.3 do gọi `TextView.getMinWidth()`, API không có trên Android 2.3.
- Giữ hỗ trợ từ Android 2.3/API 9.

## Windows

- Đồng bộ phiên bản bộ cài và ứng dụng Windows lên 1.0.2.
- Giữ hỗ trợ Windows 7 SP1 trở lên trên x86/x64; tự tải .NET Framework 4.8 từ Microsoft nếu máy chưa có.
- Bộ cài chưa ký mã; SmartScreen có thể cảnh báo.
