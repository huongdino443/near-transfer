#define AppName "Near Transfer"
#define AppVersion "1.0.0"
#ifndef PublishDir
  #define PublishDir "..\publish\legacy-anycpu"
#endif

[Setup]
AppId={{4E894625-891F-49C3-BCFA-C7077D9A671E}
AppName={#AppName}
AppVersion={#AppVersion}
AppPublisher=Near Transfer
DefaultDirName={autopf}\Near Transfer
DefaultGroupName=Near Transfer
OutputDir=..\release
OutputBaseFilename=v1.0.0-NearTransfer-Windows-Setup
SetupIconFile=..\src\NearTransfer.Windows\Assets\NearTransfer.ico
MinVersion=6.1sp1
ArchitecturesAllowed=x86 x64
ArchitecturesInstallIn64BitMode=x64
PrivilegesRequired=admin
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
Uninstallable=yes
CloseApplications=yes
RestartIfNeeded=no

[Tasks]
Name: "desktopicon"; Description: "Tạo biểu tượng trên màn hình nền"; GroupDescription: "Lối tắt:"

[Files]
Source: "{#PublishDir}\*"; Excludes: "*.pdb"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{autoprograms}\Near Transfer"; Filename: "{app}\NearTransfer.exe"
Name: "{autodesktop}\Near Transfer"; Filename: "{app}\NearTransfer.exe"; Tasks: desktopicon

[Run]
Filename: "{app}\NearTransfer.exe"; Description: "Mở Near Transfer"; Flags: nowait postinstall skipifsilent

[Code]
const
  NetFramework48Release = 528040;
  NetFramework48RegistryKey = 'SOFTWARE\Microsoft\NET Framework Setup\NDP\v4\Full';
  NetFramework48WebInstallerUrl = 'https://go.microsoft.com/fwlink/?LinkId=2085155';

function IsNetFramework48Installed: Boolean;
var
  ReleaseNumber: Cardinal;
begin
  Result := RegQueryDWordValue(
    HKLM,
    NetFramework48RegistryKey,
    'Release',
    ReleaseNumber);
  if Result then
    Result := ReleaseNumber >= NetFramework48Release;
end;

function RuntimeDownloadProgress(
  const Url, FileName: String;
  const Progress, ProgressMax: Int64): Boolean;
begin
  Result := True;
  if ProgressMax > 0 then
    WizardForm.StatusLabel.Caption :=
      Format('Đang tải .NET Framework 4.8: %d%%', [Progress * 100 div ProgressMax])
  else
    WizardForm.StatusLabel.Caption := 'Đang tải .NET Framework 4.8...';
end;

function PrepareToInstall(var NeedsRestart: Boolean): String;
var
  InstallerPath: String;
  ResultCode: Integer;
begin
  Result := '';
  if IsNetFramework48Installed then
    Exit;

  WizardForm.StatusLabel.Caption := 'Đang tải .NET Framework 4.8 từ Microsoft...';
  try
    DownloadTemporaryFile(
      NetFramework48WebInstallerUrl,
      'NDP48-Web.exe',
      '',
      @RuntimeDownloadProgress);
  except
    Result := 'Không tải được .NET Framework 4.8. Hãy kiểm tra kết nối Internet rồi chạy lại setup.';
    Exit;
  end;

  InstallerPath := ExpandConstant('{tmp}\NDP48-Web.exe');
  WizardForm.StatusLabel.Caption := 'Đang cài .NET Framework 4.8...';
  if not Exec(
    InstallerPath,
    '/passive /norestart',
    ExpandConstant('{tmp}'),
    SW_SHOW,
    ewWaitUntilTerminated,
    ResultCode) then
  begin
    Result := 'Không khởi chạy được trình cài .NET Framework 4.8.';
    Exit;
  end;

  if (ResultCode <> 0) and (ResultCode <> 3010) then
  begin
    Result := Format(
      'Cài .NET Framework 4.8 thất bại (mã %d). Hãy cập nhật Windows 7 SP1 rồi chạy lại setup.',
      [ResultCode]);
    Exit;
  end;

  NeedsRestart := ResultCode = 3010;
  if not IsNetFramework48Installed then
    Result := 'Máy chưa xác nhận được .NET Framework 4.8 sau khi cài đặt.';
end;