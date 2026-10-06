Unicode true

!include "MUI2.nsh"
!include "LogicLib.nsh"
!include "WinVer.nsh"
!include "x64.nsh"

!ifndef APP_VERSION
  !define APP_VERSION "1.0.0"
!endif
!ifndef OUTPUT_FILE
  !define OUTPUT_FILE "..\release\v1.0.0-NearTransfer-Windows-Setup.exe"
!endif
!ifndef PUBLISH_DIR
  !define PUBLISH_DIR "..\publish\legacy-anycpu"
!endif
!ifndef ICON_FILE
  !define ICON_FILE "..\src\NearTransfer.Windows\Assets\NearTransfer.ico"
!endif

!define APP_NAME "Near Transfer"
!define NET48_KEY "SOFTWARE\Microsoft\NET Framework Setup\NDP\v4\Full"
!define NET48_RELEASE 528040
!define NET48_WEB_URL "https://go.microsoft.com/fwlink/?LinkId=2085155"

!define MUI_ICON "${ICON_FILE}"
!define MUI_UNICON "${ICON_FILE}"
!define MUI_WELCOMEPAGE_TITLE "Cài đặt Near Transfer"
!define MUI_WELCOMEPAGE_TEXT "Trình cài đặt sẽ cài Near Transfer. Nếu .NET Framework 4.8 chưa có trên máy, setup sẽ tải bộ cài web từ Microsoft."
!define MUI_FINISHPAGE_RUN "$INSTDIR\NearTransfer.exe"
!define MUI_FINISHPAGE_RUN_TEXT "Mở Near Transfer"
!define MUI_ABORTWARNING

Name "${APP_NAME} ${APP_VERSION}"
OutFile "${OUTPUT_FILE}"
InstallDir "$PROGRAMFILES\Near Transfer"
InstallDirRegKey HKLM "Software\Near Transfer" "InstallDir"
RequestExecutionLevel admin
ShowInstDetails show
SetCompressor /SOLID lzma
SetCompressorDictSize 16

VIProductVersion "${APP_VERSION}.0"
VIAddVersionKey /LANG=1066 "ProductName" "${APP_NAME}"
VIAddVersionKey /LANG=1066 "ProductVersion" "${APP_VERSION}"
VIAddVersionKey /LANG=1066 "FileVersion" "${APP_VERSION}"
VIAddVersionKey /LANG=1066 "FileDescription" "Near Transfer for Windows installer"
VIAddVersionKey /LANG=1066 "LegalCopyright" "Near Transfer"

!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH

!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES

!insertmacro MUI_LANGUAGE "Vietnamese"

Function .onInit
  ${If} ${AtLeastWin7}
  ${Else}
    MessageBox MB_ICONSTOP "Near Transfer cần Windows 7 SP1 trở lên."
    Abort
  ${EndIf}
  ${If} ${AtLeastWin7}
    ${If} ${AtMostWin7}
      ${If} ${AtLeastServicePack} 1
      ${Else}
        MessageBox MB_ICONSTOP "Near Transfer cần Windows 7 Service Pack 1."
        Abort
      ${EndIf}
    ${EndIf}
  ${EndIf}
FunctionEnd

Function EnsureNetFramework48
  Call CheckNetFramework48
  Pop $R0
  ${If} $R0 == 0
    MessageBox MB_ICONINFORMATION|MB_OKCANCEL "Máy chưa có .NET Framework 4.8. Setup sẽ tải và cài runtime từ Microsoft. Cần kết nối Internet." IDOK download_runtime
    Abort

    download_runtime:
    Delete "$TEMP\NDP48-Web.exe"
    DetailPrint "Đang tải .NET Framework 4.8 từ Microsoft..."
    System::Call 'urlmon::URLDownloadToFileW(p 0, w "${NET48_WEB_URL}", w "$TEMP\NDP48-Web.exe", i 0, p 0) i.r1'
    ${If} $R1 != 0
      MessageBox MB_ICONSTOP "Không tải được .NET Framework 4.8 (mã lỗi $R1). Hãy kiểm tra kết nối Internet rồi chạy lại setup."
      Abort
    ${EndIf}

    DetailPrint "Đang cài .NET Framework 4.8..."
    ExecWait '"$TEMP\NDP48-Web.exe" /passive /norestart' $R1
    Delete "$TEMP\NDP48-Web.exe"
    ${If} $R1 != 0
    ${AndIf} $R1 != 3010
      MessageBox MB_ICONSTOP "Cài .NET Framework 4.8 thất bại (mã $R1). Hãy cập nhật Windows 7 SP1 rồi chạy lại setup."
      Abort
    ${EndIf}

    Call CheckNetFramework48
    Pop $R0
    ${If} $R0 == 0
      MessageBox MB_ICONSTOP "Máy chưa xác nhận được .NET Framework 4.8 sau khi cài đặt."
      Abort
    ${EndIf}
    ${If} $R1 == 3010
      MessageBox MB_ICONINFORMATION|MB_OK "Windows cần khởi động lại để hoàn tất cài .NET Framework 4.8."
    ${EndIf}
  ${EndIf}
FunctionEnd

Function CheckNetFramework48
  Push $R0
  Push $R1
  StrCpy $R0 0
  ${If} ${RunningX64}
    SetRegView 64
  ${Else}
    SetRegView 32
  ${EndIf}
  ClearErrors
  ReadRegDWORD $R1 HKLM "${NET48_KEY}" "Release"
  ${IfNot} ${Errors}
  ${AndIf} $R1 >= ${NET48_RELEASE}
    StrCpy $R0 1
  ${EndIf}
  SetRegView 32
  Pop $R1
  Exch $R0
FunctionEnd

Section "Near Transfer" MainSection
  Call EnsureNetFramework48
  SetShellVarContext all
  SetOutPath "$INSTDIR"
  File /r "${PUBLISH_DIR}/*"
  WriteUninstaller "$INSTDIR\Uninstall.exe"

  CreateDirectory "$SMPROGRAMS\Near Transfer"
  CreateShortcut "$SMPROGRAMS\Near Transfer\Near Transfer.lnk" "$INSTDIR\NearTransfer.exe" "" "$INSTDIR\NearTransfer.exe" 0
  CreateShortcut "$SMPROGRAMS\Near Transfer\Gỡ cài đặt Near Transfer.lnk" "$INSTDIR\Uninstall.exe"

  ${If} ${RunningX64}
    SetRegView 64
  ${Else}
    SetRegView 32
  ${EndIf}
  WriteRegStr HKLM "Software\Near Transfer" "InstallDir" "$INSTDIR"
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\Near Transfer" "DisplayName" "${APP_NAME}"
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\Near Transfer" "DisplayVersion" "${APP_VERSION}"
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\Near Transfer" "Publisher" "Near Transfer"
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\Near Transfer" "InstallLocation" "$INSTDIR"
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\Near Transfer" "UninstallString" '"$INSTDIR\Uninstall.exe"'
  WriteRegDWORD HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\Near Transfer" "NoModify" 1
  WriteRegDWORD HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\Near Transfer" "NoRepair" 1
  SetRegView 32
SectionEnd

Section "Uninstall"
  SetShellVarContext all
  Delete "$SMPROGRAMS\Near Transfer\Near Transfer.lnk"
  Delete "$SMPROGRAMS\Near Transfer\Gỡ cài đặt Near Transfer.lnk"
  RMDir "$SMPROGRAMS\Near Transfer"

  ${If} ${RunningX64}
    SetRegView 64
  ${Else}
    SetRegView 32
  ${EndIf}
  DeleteRegKey HKLM "Software\Near Transfer"
  DeleteRegKey HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\Near Transfer"
  SetRegView 32

  Delete "$INSTDIR\NearTransfer.exe"
  Delete "$INSTDIR\NearTransfer.Core.dll"
  Delete "$INSTDIR\NearTransfer.exe.config"
  Delete "$INSTDIR\Uninstall.exe"
  RMDir "$INSTDIR"
SectionEnd