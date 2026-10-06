# Near Transfer — Java ME

MIDlet này gửi và nhận file hoặc một tin nhắn văn bản qua Wi-Fi LAN bằng giao
thức NWS1, tương thích với Near Transfer trên Android và Windows.

## Build

Cần JDK 8 và Maven. Chạy tại thư mục này:

```sh
./build.sh
```

Build tạo JAR đã preverify theo CLDC 1.1 và JAD trong `dist/`. Mặc định tạo:

- `dist/v34-near-transfer-java-me-auto-folders.jar`
- `dist/v34-near-transfer-java-me-auto-folders.jad`

Để giữ lại các bản build cũ, đặt số phiên bản ở đầu tên file:

```sh
ARTIFACT_STEM=v35-near-transfer-java-me-lan ./build.sh
```

Trình cài đặt có thể hỏi quyền mạng và quyền đọc/ghi bộ nhớ. Cho phép các quyền
này để tìm thiết bị, truyền dữ liệu và lưu nội dung nhận được.

## Điều khiển

- Màn hình cảm ứng có pointer: chạm nút và kéo để cuộn.
- Máy phím cứng: D-pad hoặc `2/4/6/8` để di chuyển, `5` để chọn; phím mềm
  `Chọn`/`Lùi` dùng cho chọn và quay lại.
- `0` làm mới tìm kiếm; `9` chuyển giữa điều khiển tự động và D-pad.
- Dò tìm thiết bị dùng UDP broadcast và quét unicast có giới hạn trong subnet
  `/24` của mạng `192.168.*`; lượt quét subnet chạy định kỳ và khi bấm Refresh.
  Nếu mạng dùng dải khác, nhập địa chỉ IPv4 thủ công; listener nhận vẫn hoạt
  động trên cổng TCP `45321`.

## Giới hạn truyền

- Tối đa 20 file và tổng kích thước mỗi lần gửi 2 GiB.
- Một lần truyền là một lô file hoặc đúng một tin nhắn, không gộp hai loại.
- Tin nhắn tối đa 256 KiB.
- Ảnh, video, âm thanh, văn bản và loại tệp khác được phân vào `Images`,
  `Videos`, `Sounds`, `Documents` và `Others`; JAR được lưu trong `Others`.
- Trên màn hình chính, chọn **Duyệt** cạnh **Vị trí nhận**, mở ổ nhớ rồi chọn
  **Dùng ổ này**. MIDlet kiểm tra khả năng ghi các thư mục phân loại trước khi
  lưu lựa chọn; nếu thư mục chuẩn không dùng được, nó thử thư mục con tương ứng
  trong `Near Transfer`.
- Lựa chọn ổ nhớ được giữ sau khi đóng MIDlet; **Mặc định** khôi phục chế độ tự
  chọn bộ nhớ khả dụng.