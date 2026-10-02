# Nearby Share LAN protocol, version 2

The protocol is deliberately small so that a constrained MIDP 2.0 / CLDC 1.1
client can use it without JSON, TLS, a cloud service, or a modern discovery
framework. Discovery retains its version 1 message format; file transfers use
the offer-and-approval flow described below.

## Discovery

- UDP broadcast port: `45322`
- HTTP transfer port: `45321`
- Discovery request: `NWS1|DISCOVER|<http-port>|<url-encoded-name>`
- Reply: `NWS1|PEER|<http-port>|<url-encoded-name>`
- A peer is considered stale after 10 seconds without a reply.

## Offer and upload

- The sender starts with `POST /offer`; its body is empty.
- Offer headers:
  - `X-Transfer-Id`: 32 lowercase hexadecimal characters, unique per batch.
  - `X-Sender-Name`: UTF-8 sender name encoded with
    `application/x-www-form-urlencoded`.
- `X-Transfer-Type` is required and is either `files` or `text`. A transfer
  contains files or one text message, never both.
- For `X-Transfer-Type: files`, `X-File-Count` is the number of files, from 1
  to 20. For every zero-based index `i`, `X-File-i-Name` and `X-File-i-Size`
  describe the sanitized file name and size in bytes. Names use the same
  form-url-encoding as `X-Sender-Name`.
- For `X-Transfer-Type: text`, `X-Text-Size` is the UTF-8 body length in bytes,
  from 1 to 262144. The offer represents exactly one text message.
- The receiver displays the sender and offered content and waits for the user
  to accept or decline. It returns HTTP `200` only after acceptance,
  `403` after decline or timeout, `400` for an invalid offer, and `503` when
  its approved-transfer queue is full. No content bytes are sent before HTTP
  `200`.
- After acceptance, the sender makes one `POST /upload` per file. Each request
  contains `X-Transfer-Id`, zero-based `X-File-Index`, `X-File-Name`, and
  `Content-Length`; its body contains the raw bytes for that file.
- For text, the sender makes one `POST /upload-text` with `X-Transfer-Id` and
  `Content-Length`; the body contains the UTF-8 message. The receiver returns
  HTTP `201` after receiving it.
- The receiver accepts an upload only when its transfer ID, sender IP, index,
  file name, and size match an approved offer. A successful upload returns
  HTTP `201`.
- The receiver writes files under its public Downloads directory. The batch
  limit is 20 files and 2 GiB minus 1 byte total; each file is also limited to
  2 GiB minus 1 byte for old `HttpURLConnection` implementations.

## Security and limits

- File transfer is for trusted private LANs only. It uses cleartext HTTP;
  receiver approval is not encryption or cryptographic authentication.
- Clients must sanitize received file names and must not accept path components.
- Text messages are limited to 256 KiB; the receiver offers copy and `.txt`
  save actions after receipt.
- A future J2ME client should first verify local HTTP access and file APIs on
  the target handset; MIDP network/file capabilities vary by manufacturer.