# 0.4.0 real-device result (CI19)

CI19 passed policy tests and APK build. Installed versionCode 4 matches release SHA-256
`c7b9638970d12128cfe67d58c93203f57c31d89f9f3c116baf304c0b7bda60c1`.

After a controlled SystemUI restart, the exact Seedling APK and RUS schema passed
runtime verification. A real navigation session hit the targeted hooks:

- Native type corrected to 1.
- Missing AMap RUS profile restored with lockImmersiveEnable=1 and default=1.
- SystemUI actually bound AMapImmerseNaviService. Service dump reports requested,
  received and hasBound, all true, with SystemUI as the client.
- Native IntentMessenger sends the host token (message 11).

**Not a functional pass:** the captured lockscreen still shows the ordinary navigation
card, not AMap's native map. The host's child SurfacePackage is absent. Renderer-side
initialization must now be traced, rather than repeating whitelist/scope-only tests.

0.4.1 adds read-only diagnostics inside the AMap scope, including host-token receipt,
display dimensions and whether the native renderer has an AJX context and config.
It logs only presence/length, not route/config content. It also recognizes untagged
AMap surface replies by the messenger's exact service URI. No renderer behavior,
route, configuration or authentication is overridden by these diagnostic additions.

For 0.4.1 enable both SystemUI and AMap scopes. AMap is a diagnostic scope; SystemUI
contains the compatibility implementation. Real rendering remains unvalidated.
