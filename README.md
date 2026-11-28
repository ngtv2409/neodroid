# Neodroid

A GUI Neovim client for Android over SSH.

Neodroid connects to a remote host over SSH, and spawns `nvim --embed` or attaches to
an existing Neovim instance through a UNIX socket, and renders the Neovim
`ext_linegrid`/`ext_multigrid` UI on a native android canvas.

## Table of Contents

- [Features](#features)
- [Requirements](#requirements)
- [Building](#building)
- [Usage](#usage)
- [Clipboard Integration](#clipboard-integration)
- [Limitations](#limitations)
- [License](#license)

## Features

- Native Compose rendering of the Neovim grid, with bundled Nerd Fonts and configurable
  font size, line height, and theme.
- Password, keyboard-interactive, and stored SSH key authentication.
- Host key verification with trust-on-first-use pinning and an mismatch flow.
- An extra keys bar, and remote clipboard integration.
- You can connect to an ssh server running on the same phone (Termux) as well.
- Smooth scrolling
- Integration (clipboard)

## Requirements

**Android**

- Android 7.0 (API 24) or newer.

**Remote host**

- An SSH server and a Neovim installation reachable from the device.
- For attaching to an existing Neovim instance over a UNIX socket, `socat` must be
  installed on the remote host (Neodroid runs `socat STDIO 'UNIX-CONNECT:<path>'`).

**Building from source**

- JDK 17 and an Android SDK with `compileSdk 37`.
- The Gradle wrapper handles the rest.

> **Note:** Neodroid does not consider Neovim API versions as of now.
Everything are tested against my local nvim installation (v0.12.5).

## Building

```bash
./gradlew :app:assembleDebug        # debug APK
./gradlew :app:assembleReleaseDebug # optimized, signed with the debug key
./gradlew spotlessApply             # format code
```


## Integration

Integration plugin is located in [lua/](lua). You can use it with your favourite plugin manager.
It is in its early stage and has no config yet.

### Lazy
```lua
{
    "ngtv2409/neodroid"
}
```

After you have installed the plugin, do:
```lua
if vim.g.neodroid == 1 then
  require("neodroid").setup()
end
```

Clipboard requires **Settings - Integration - Clipboard - Allow remote clipboard** in the app.
Note that this allows the remote host to read your phone's clipboard at any time.

### Integration features
- Clipboard

## Limitations

- Neovim API versions are not handled.
- External windows (`win_external_pos`) are not rendered; they are intended for
  separate top-level desktop windows.
- Text selection and copy remain tied to the active editor window when splits or
  floating windows are visible.
- SSH agent authentication is not supported.
- Recent connections are in-memory only and reset when the service restarts.

## License

Apache License 2.0. See [LICENSE](LICENSE).

Third-party components and bundled assets are listed in the app under
**Settings - About - Third-party notices**; license texts live in
[`app/src/main/assets/licenses`](app/src/main/assets/licenses).
