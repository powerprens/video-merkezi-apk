# Video Merkezi (Android)

Video uretim panelini WebView ile acan saf Java uygulama (AndroidX yok).

- Varsayilan sunucu: `https://video.yusaa.com.tr` (menu → Sunucu Adresi'nden degistirilebilir)
- Acilista `/app/version.json` kontrolu: `versionCode` buyukse APK'yi indirip
  `PackageInstaller` oturumu ile kurulumu baslatir (in-app update).
- Derleme: GitHub Actions (`APK derle` workflow'u) → artifact `video-merkezi-apk`.
