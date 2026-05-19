; ============================================================
;  Hotel Management System — Inno Setup 6 Script
;  Produces: HotelManagementSetup.exe
; ============================================================

#define MyAppName      "Hotel Management System"
#define MyAppVersion   "1.0.0"
#define MyAppPublisher "Your Company Name"
#define MyAppExeName   "HotelManagement.bat"
#define MyJarName      "hotel-management-system-1.0.0.jar"

[Setup]
AppId={{A1B2C3D4-E5F6-7890-ABCD-EF1234567890}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
DefaultDirName={autopf}\{#MyAppName}
DefaultGroupName={#MyAppName}
OutputDir=..\dist
OutputBaseFilename=HotelManagementSetup
Compression=lzma
SolidCompression=yes
WizardStyle=modern
//LicenseFile=..\LICENSE
MinVersion=10.0
ArchitecturesInstallIn64BitMode=x64

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; \
    GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked

[Files]
; Main JAR
Source: "..\target\{#MyJarName}"; DestDir: "{app}"; Flags: ignoreversion

; Launcher batch script
Source: "..\scripts\run.bat"; DestName: "HotelManagement.bat"; \
    DestDir: "{app}"; Flags: ignoreversion

; Backup script
Source: "..\scripts\backup.bat"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{group}\{#MyAppName}";        Filename: "{app}\{#MyAppExeName}"
Name: "{group}\Backup Database";      Filename: "{app}\backup.bat"
Name: "{group}\{cm:UninstallProgram,{#MyAppName}}"; Filename: "{uninstallexe}"
Name: "{autodesktop}\{#MyAppName}";   Filename: "{app}\{#MyAppExeName}"; \
    Tasks: desktopicon

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "{cm:LaunchProgram,{#MyAppName}}"; \
    Flags: nowait postinstall skipifsilent

[Code]
// Check Java 21 is installed
function InitializeSetup(): Boolean;
var
  JavaVersion: String;
begin
  Result := True;
  if not RegQueryStringValue(HKLM, 'SOFTWARE\JavaSoft\JDK', 'CurrentVersion', JavaVersion) then
  begin
    if MsgBox('Java 21 JDK does not appear to be installed.' + #13#10 +
              'Download from https://adoptium.net/' + #13#10#13#10 +
              'Continue installation anyway?',
              mbConfirmation, MB_YESNO) = IDNO then
      Result := False;
  end;
end;
