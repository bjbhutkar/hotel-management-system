#define AppName "Rasoi"
#define AppVersion "1.0.0"
#define AppPublisher "Rasoi"
#define AppJar "rasoi.jar"

[Setup]
AppId={{B3F4C5D6-E7F8-9012-BCDE-F23456789012}}
AppName={#AppName}
AppVersion={#AppVersion}
AppPublisher={#AppPublisher}
DefaultDirName={autopf}\{#AppName}
DefaultGroupName={#AppName}
OutputDir=..\dist
OutputBaseFilename=RasoiSetup
Compression=lzma
SolidCompression=yes
WizardStyle=modern
ArchitecturesInstallIn64BitMode=x64
PrivilegesRequired=admin
SetupIconFile=rasoi.ico
UninstallDisplayIcon={app}\rasoi.ico

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "Create desktop shortcut"

[Files]
Source: "..\target\hotel-management-system-1.0.0.jar"; DestDir: "{app}"; DestName: "{#AppJar}"
Source: "rasoi.ico"; DestDir: "{app}"
Source: "jre\*"; DestDir: "{app}\jre"; Flags: recursesubdirs createallsubdirs

[Icons]
Name: "{group}\{#AppName}"; Filename: "{app}\jre\bin\javaw.exe"; Parameters: "-jar ""{app}\{#AppJar}"""; WorkingDir: "{app}"

Name: "{autodesktop}\{#AppName}"; Filename: "{app}\jre\bin\javaw.exe"; Parameters: "-jar ""{app}\{#AppJar}"""; WorkingDir: "{app}"; Tasks: desktopicon

[Run]
Filename: "{app}\jre\bin\javaw.exe"; Parameters: "-jar ""{app}\{#AppJar}"""; WorkingDir: "{app}"; Flags: nowait postinstall