# AnimLevel

A lightweight Android utility for ColorOS, OxygenOS, and Realme UI to switch the system-wide animation tier (`persist.sys.oplus.anim_level`) on unrooted or rooted devices.

Developed by **@UnkNwn019**.

## Features

- **No Root Required:** Supports **Shizuku** (executes commands via ADB shell privileges).
- **Root Support:** Direct `su` fallback for rooted devices running KernelSU, APatch, or Magisk.
- **Accurate ColorOS Animation Tiers:**
  - **Level 1 — Flagship Tier:** Top-tier profile with full Gaussian blur layers, complex fluid spring physics, and iOS-style stacked recents.
  - **Level 2 — Balanced Tier:** Standard mid-range transitions & balanced GPU performance.
  - **Level 3 — Low-end Tier:** Budget device profile with simplified animations & lowest GPU overhead.
- **Automatic Reboot Prompt:** Prompts to reboot the device (or soft-restart UI processes) immediately after applying so the changes take full visual effect.
- **Persistent:** Changes are written directly to Android's `persist.*` database so they persist across phone reboots.

## Tech Stack

- **UI:** Jetpack Compose + Material 3 Dynamic Theming
- **Target SDK:** Android 15 (API 35) / Minimum SDK: Android 12 (API 31)
- **Privilege API:** [Rikka Shizuku API](https://github.com/RikkaApps/Shizuku-API)

## License

MIT License
