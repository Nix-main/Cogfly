#define app "Cogfly"
#define link "https://patreon.com/c/AmberShadowo"
#define exe "Cogfly.exe"

[Setup]
AppId={{CFE1E633-FC42-4E59-A82F-87A66DA6B009}
AppName={#app}
AppVerName={#app}
AppVersion={#ver}
UsePreviousAppDir=yes
DisableDirPage=auto
SetupIconFile=icons\icon.ico
AppPublisher="Ambershadowo"
AppPublisherURL={#link}
AppSupportURL={#link}
AppUpdatesURL={#link}
OutputBaseFilename=Cogfly-{#ver}-installer
DefaultDirName={autopf}\{#app}
UninstallDisplayIcon={app}\{#exe}
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
DisableProgramGroupPage=yes
LicenseFile=LICENSE
PrivilegesRequired=admin
SolidCompression=yes
WizardStyle=modern dark

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked

[Files]
Source: "output\Windows\Cogfly\{#exe}"; DestDir: "{app}"; Flags: ignoreversion
Source: "output\Windows\Cogfly\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "icons\icon.ico"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{autoprograms}\{#app}"; Filename: "{app}\{#exe}"
Name: "{autodesktop}\{#app}"; Filename: "{app}\{#exe}"; Tasks: desktopicon

[Registry]
Root: HKCU; Subkey: "Software\Classes\cogfly"; ValueType: string; ValueName: ""; ValueData: "URL:Cogfly Protocol"; Flags: uninsdeletekey
Root: HKCU; Subkey: "Software\Classes\cogfly"; ValueType: string; ValueName: "URL Protocol"; ValueData: ""
Root: HKCU; Subkey: "Software\Classes\cogfly\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\Cogfly.exe"" ""%1"""

[Run]
Filename: "{app}\{#exe}"; Description: "{cm:LaunchProgram,{#StringChange(app, '&', '&&')}}"; Flags: nowait postinstall skipifsilent


[Code]
function PrepareToInstall(var NeedsRestart: Boolean): String;
var
  UninstallString: String;
  ResultCode: Integer;
  GuidStart: Integer;
  KeyPath: String;
begin
  Result := '';
  KeyPath := 'SOFTWARE\Microsoft\Windows\CurrentVersion\Installer\UserData\S-1-5-18\Products\04002929EE6BF9B378975A69111C6458\InstallProperties';
  if not RegQueryStringValue(HKLM64, KeyPath, 'UninstallString', UninstallString) then
    RegQueryStringValue(HKLM32, KeyPath, 'UninstallString', UninstallString);
  if UninstallString <> '' then
  begin
    GuidStart := Pos('{', UninstallString);
    if GuidStart > 0 then
    begin
      if not Exec('msiexec.exe',
           '/x' + Copy(UninstallString, GuidStart, MaxInt) + ' /qn /norestart',
           '', SW_HIDE, ewWaitUntilTerminated, ResultCode) then
        Result := 'Could not uninstall automatically. Please uninstall Cogfly manually from "Add or Remove Programs" and run this exe again.';
    end;
  end;
end;
