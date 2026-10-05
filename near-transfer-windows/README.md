# Near Transfer for Windows

Ứng dụng Windows 7 SP1 trở lên để gửi và nhận file hoặc văn bản với Near Transfer trên Android qua cùng mạng nội bộ.

## Tính năng

- Lần đầu mở ứng dụng sẽ yêu cầu chọn thư mục lưu file nhận; lựa chọn được ghi nhớ và có thể đổi trong ứng dụng.
- Chọn file hoặc ảnh/video, dán văn bản từ clipboard, nhập văn bản, hoặc kéo file từ File Explorer vào cửa sổ. Các lần chọn file nối vào hàng chờ; file trùng đường dẫn không được thêm lần thứ hai.
- Mỗi lượt gửi chỉ chứa file hoặc một tin nhắn văn bản. Tin nhắn được giới hạn ở 256 KiB theo số byte UTF-8.
- Tin nhắn nhận được có thể sao chép vào clipboard hoặc lưu thành file `.txt`; màn hình tin nhắn tự trở về trang chính sau thao tác thành công.
- Sau khi gửi thành công, hàng chờ được làm trống; nếu gửi thất bại hoặc bị từ chối, file vẫn còn để thử lại.
- Gửi và nhận dùng chung một màn hình tiến độ/kết quả trong cửa sổ chính; yêu cầu nhận vẫn cần được chấp thuận trước khi bắt đầu. Phần tổng quan và vòng tiến độ được giữ cố định; toàn bộ danh sách file cuộn độc lập bên dưới.
- Tìm thiết bị Near Transfer qua UDP broadcast và dò các địa chỉ trong subnet LAN nhỏ; vẫn trả lời yêu cầu tìm kiếm đến kể cả khi ứng dụng đang chờ.
- Hiển thị địa chỉ IPv4 của máy cạnh trạng thái nhận file trên thanh tiêu đề. Làm mới tìm kiếm, kết nối thủ công bằng nút **+** và một ô địa chỉ IPv4 (dùng cổng mặc định), lưu/gỡ thiết bị thường dùng.
- Gửi tối đa 20 file mỗi lượt và nhận file sau khi người dùng chấp thuận yêu cầu.
- Tạo tên file nhận an toàn trên Windows và không ghi đè file đã có.
- Giao diện hai cột, tông tối và màu lavender được đồng bộ với Near Transfer trên Android. Ứng dụng vẫn nhận kéo-thả file nhưng không hiển thị hướng dẫn kéo-thả.
- Khu vực **Thiết bị quanh đây** và **Đã lưu** có chiều cao bằng nhau, cuộn độc lập; danh sách file cũng cuộn mượt khi tràn. Không hiện thanh cuộn và không dành dòng riêng để đếm thiết bị đang hoạt động.
- Ô nhập được làm tròn và đổi viền lavender khi focus, đồng bộ với giao diện Android. Nút lưu thiết bị dùng biểu tượng bookmark riêng; trạng thái chưa có thiết bị đã lưu không có nền riêng.
- Giao diện không hiện tooltip chỉ dẫn khi rê chuột. Chữ trên mọi nút nền lavender, kể cả khi bị vô hiệu hóa, luôn có màu tối dễ đọc.
- Cửa sổ có kích thước cố định 1080×720, không cho đổi kích thước hoặc phóng to. Các danh sách dùng phần chiều cao còn lại và cuộn mượt khi quá nhiều nội dung; không hiện thanh cuộn.

## Cài đặt

1. Chạy `v1.0.0-NearTransfer-Windows-Setup.exe` trên Windows 7 SP1 trở lên, 32-bit hoặc 64-bit.
2. Setup cần quyền quản trị để cài .NET Framework 4.8 nếu máy chưa có; khi đó cần kết nối Internet. Ứng dụng dùng runtime đã cài, không đóng gói runtime vào setup.
3. Mở Near Transfer; lần đầu chạy sẽ yêu cầu chọn thư mục lưu file nhận.
4. Kết nối máy tính và Android vào cùng Wi-Fi, mở Near Transfer trên Android, rồi thử gửi file theo cả hai chiều.
5. Thử dán hoặc nhập một tin nhắn tiếng Việt có nhiều dòng/emoji, gửi sang Android, rồi gửi văn bản ngược lại. Kiểm tra sao chép và lưu `.txt` trên máy nhận.
6. Nếu Windows Firewall hỏi, chỉ cho phép trên mạng riêng.

Bộ cài đã được kiểm tra thủ công và xác nhận hoạt động trên Windows. Chưa ghi nhận phiên bản Windows và kiến trúc cụ thể của máy kiểm thử, nên thông tin này không thay thế kiểm thử riêng trên mọi cấu hình. Bộ cài chưa được ký mã; SmartScreen có thể cảnh báo.

## Giao thức

Client giữ nguyên giao thức LAN hiện tại:

- UDP `45322`: `NWS1|DISCOVER` / `NWS1|PEER`.
- HTTP `45321`: `POST /offer`, chờ người nhận chấp thuận, rồi `POST /upload` cho từng file hoặc `POST /upload-text` cho một tin nhắn UTF-8.
- Giới hạn mỗi file và mỗi lượt gửi là dưới 2 GiB.
- Tin nhắn văn bản giới hạn 256 KiB.

Giao thức dùng HTTP không mã hóa. Chỉ dùng trên mạng nội bộ đáng tin cậy; người nhận cần xác nhận từng lượt gửi. Nếu Windows Firewall hỏi, chỉ cho phép ứng dụng trên mạng riêng.

Phiên bản này hỗ trợ gửi và nhận file cùng tin nhắn văn bản giữa Windows và Android.

## Build và kiểm tra

Yêu cầu .NET 8 SDK:

```powershell
dotnet build .\NearTransfer.Windows.sln -p:EnableWindowsTargeting=true
dotnet run --project .\tests\NearTransfer.ProtocolChecks\NearTransfer.ProtocolChecks.csproj
```

Các kiểm tra giao thức chạy được trên Linux lẫn Windows. Giao diện WPF chỉ chạy trên Windows.

## Tạo installer Windows x86/x64

Cài đặt framework-dependent AnyCPU giúp ứng dụng chạy trên Windows 7 SP1 trở lên, 32-bit và 64-bit mà không đóng gói runtime .NET vào app. Bộ cài hiện tại có kích thước dưới 1 MB; nếu thiếu .NET Framework 4.8, setup tải web installer nhỏ của Microsoft rồi cài runtime.

Trên Windows, cài .NET 8 SDK và Inno Setup 6, sau đó chạy:

```powershell
.\installer\build-installer.ps1
```

Script publish ứng dụng cho .NET Framework 4.8 (AnyCPU) và dùng Inno Setup để tạo installer trong `release\`.

Trong môi trường Linux/Replit, cài .NET 8 SDK và NSIS 3, sau đó chạy:

```bash
bash installer/build-installer-linux.sh
```

Script Linux dùng `installer/NearTransfer.nsi` để tạo `v1.0.0-NearTransfer-Windows-Setup.exe` trong `release/`. Runtime chỉ được tải từ Microsoft khi thiếu. Installer chưa được ký mã; Windows có thể hiển thị cảnh báo SmartScreen.