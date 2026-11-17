# Download source

A dual-channel Android download manager, packaged in one repository.

- **Zeta** — test channel, `com.downloadsource.zeta`
- **Omega** — release channel, `com.downloadsource.omega`

## What it does

- Multi-threaded segmented downloader, native merge step (Zero-core, NDK)
- Custom download directory (SAF)
- Built-in browser with ad / redirect filtering for common Baidu jump schemes
- GitHub mirror acceleration, user-editable source list
- Persistent tasks, foreground service keeps them alive in background
- Light / dark themes, gradient UI

## Build

```bash
gradle :app:assembleZetaDebug :app:assembleOmegaDebug
```

Artifacts land in app/build/outputs/apk/<flavor>/debug/.

## License
Copyright (C) 2026 Maxx-EX. Licensed under the Apache License, Version 2.0.

### Derivatives
The following projects are derivative works based on this repository:
- [LM Downloader](https://github.com/exchangeq/download-source-lm) by exchangeq
License

Apache-2.0
