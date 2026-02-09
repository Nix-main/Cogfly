# Cogfly 

[![discord](https://img.shields.io/discord/879125729936298015?label=discord)](https://discord.gg/VDsg3HmWuB)
[![GitHub all releases](https://img.shields.io/github/downloads/nix-main/Cogfly/total)](https://github.com/nix-main/Cogfly/releases)

A cross-platform mod manager for [Hollow Knight: Silksong](https://hollowknightsilksong.com/). Currently only supports English.

## Usage
- Requires an internet connection
- Download the latest version [here](https://github.com/nix-main/Cogfly/releases/latest)
  - Pick your installation based on operating system and architecture
    - Windows: MSI or EXE (does not matter, both are **installers**, running them will not open the app)
    - Linux:
      - deb (debian or debian-based)
      - rpm (Fedora/Redhat)
      - AppImage (everything else)
    - MacOS:
      - aarch64 (Silicon)
      - amd64 (Intel)
- Select game path if not automatically found
  - Linux users will need to change their file type to "All Files" in the file dialog
  - Xbox Games users will need to change their file type to "All Files" in the file dialog if the game is not automatically found
    - They must also select a **non-exe** file in their game folder to avoid file permission issues
    - The game installs by default to `C:\XboxGames\Hollow Knight Silksong\Content`. If your game is installed here, it *should* auto-detect.
- Confirm profile path
- Create a profile (onboarding should guide you)
- Click the profile's icon to manage it and install mods to it
- Click 'Launch' to launch the modded profile

## Additional info
- If you rely on Steam controller compatability, or would like to keep your save files, you can either:
  - Launch with steam enabled
  - Set `Launch With Steam` in `Settings` to true
- Linux users must have Zenity or Kdialog installed for file and folder pickers to work properly. Cogfly will fall back to manual input fields if neither is found.
  - The "Copy Log To Clipboard" button is currently broken for Linux.


## Contributions & Bug Reports
Contributions can be submitted here:    
https://github.com/nix-main/Cogfly/pulls    
All contributions must be written in either Java or Kotlin, as Cogfly is a Java program. It's preferred that pull requests do not add additional libraries/dependencies, but doing so does not immediately disqualify them.

Bug reports can be submitted here:  
https://github.com/nix-main/Cogfly/issues   
Please submit actual information about the bug experienced. Please also submit your log file. There is an "Open Logs Folder" button on the info page.


<details>
<summary><h3>Credits</h3></summary>

- Art
    - [Jngo](https://github.com/jngo102) - Main icon on the info page
- Contributions
    - [Hien Ngo](https://github.com/hien-ngo29) - RPM build in the workflow
    - [FabBeyond](https://github.com/FabBeyond) - Profile icon switching
</details>