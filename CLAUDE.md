# CLAUDE.md

## 溝通規則（必須遵守）
- **一律使用繁體中文（台灣用語）回覆使用者。** 程式碼、指令、識別字、commit message 可用英文。
- 規格不清楚時先問，不要自行假設重大決策（套件名稱、權限、檔案位置等）。

## 專案概要
Threads Downloader：Android App，下載 Threads（threads.com / threads.net）**公開貼文**中的圖片與影片。
applicationId：`dev.threadsdl.app`（定案，不可更改，與簽章綁定）。
詳細規劃與媒體擷取方式（Spike 結果）見 [PLAN.md](PLAN.md)。

## 技術棧（以 APK 小為優先）
- Kotlin + Jetpack Compose (Material 3)，單一 module `app`
- 依賴只有 Compose BOM + activity-compose。不使用 Room / Hilt / Retrofit / media3 / WorkManager / Coil；網路用 `HttpURLConnection`，JSON 用 `org.json`，設定用 SharedPreferences
- Release build 開啟 R8 minify + resource shrinking
- 版本號（AGP / Kotlin / Compose BOM / compileSdk）以實際 `sdkmanager --list` 與 AGP 相容表為準，不要憑記憶填

## 本機環境
裝置序號、本機路徑、帳號見 `CLAUDE.local.md`（gitignored，若存在）。

## 建置 / 安裝指令
```bash
./gradlew assembleDebug
./gradlew assembleRelease
adb install -r --user 0 app/build/outputs/apk/release/app-release.apk
```
（`--user 0`：避免裝進 Samsung 雙開 App 設定檔 user 95）
模擬分享（Share intent）測試：
```bash
adb shell am start -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT "https://www.threads.com/@user/post/XXXX" -n dev.threadsdl.app/.ShareActivity
```
簽章檢查：`apksigner verify --print-certs <apk>`（SHA-256 應為 `44c0235d…02c0`）
分段下載 log：`adb logcat -s ThreadsDL:I`
UI 檢查：`adb exec-out screencap -p > shot.png`、`adb shell uiautomator dump`

## 簽章規則（重要）
- 專案根目錄的 `keystore/threads-downloader.jks` 為**唯一**簽章金鑰；debug 與 release 都用它簽，applicationId 不加 suffix，確保 `adb install -r` 永遠可覆蓋安裝、不需解除安裝。
- 密碼放在 `keystore.properties`（gitignored），keystore 本身也 gitignored —— **請自行備份這兩個檔案**，遺失就只能解除安裝重裝。
- 絕對不要重新產生或替換 keystore。

## 平台注意事項
- 剪貼簿只能在視窗取得焦點時讀取：以 window focus gained 觸發偵測（涵蓋從背景切回前景），不要用 `onResume`。
- 每次取得焦點時，若剪貼簿有 Threads 連結且與輸入框不同就填入；但只在剪貼簿內容變更（`ClipDescription.timestamp`）時才實際讀取，避免系統「已貼上」提示一直跳出。
- 連結解析需支援 `threads.com` 與 `threads.net`、去除 query 參數、能從分享文字中抽出 URL。
- 檔名使用**下載當下時間** `yyyyMMdd_HHmmss`；輪播（carousel）多媒體加序號 `_1`、`_2` 避免衝突。
- 下載位置：內部儲存根目錄 `threads_downloader/`（透過 SAF 授權），圖片與影片放同一資料夾，不可拆到 Pictures / Movies。
- 分享進來的連結：背景下載＋通知，不開啟主畫面。
- 下載一律走 `SegmentedDownloader`（多執行緒分段＋續傳重試＋大小核對），穩定性優先於速度。
- Threads 頁面解析只放在 `core/ThreadsExtractor.kt`；Threads 改版時只修這裡。
- Logo 必須是原創設計，不可使用 Threads 官方圖示。
