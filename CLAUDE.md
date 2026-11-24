# CLAUDE.md

## 溝通規則（必須遵守）
- **一律使用繁體中文（台灣用語）回覆使用者。** 程式碼、指令、識別字、commit message、README（英文版）可用英文。
- 規格不清楚時先問，不要自行假設重大決策（套件名稱、權限、檔案位置、新依賴、授權等）。
- 本機專屬資訊（裝置序號、本機路徑、帳號、私人測試連結）在 `CLAUDE.local.md`（gitignored，若存在請一併遵守）。

## 公開 repo 安全規則（每次 commit 前檢查）
這是**公開** repo（GitHub `kevin77688/video-downloader-app`，GPL-3.0）。
- **絕不 commit**：keystore／`keystore.properties`／任何密碼與 token、`local.properties`、裝置序號、本機絕對路徑、
  帳號名稱、公司或雇主相關資訊、第三方使用者的帳號與私人測試連結 → 這些只能放 `CLAUDE.local.md`。
- commit 前對 staged 檔案做一次掃描（`git diff --cached`），確認沒有上述內容。
- 不要更改這個 repo 的 git 身分設定（`.git/config` 已設定 noreply email 與專用 SSH key）。
- GitHub Actions 一律以 commit SHA 釘選版本（`uses: owner/action@<sha> # vX.Y.Z`），由 Dependabot 更新。

## 專案概要
Universal Video Downloader：Android App，下載多個平台**公開**內容的影片與圖片（不登入）。
- applicationId / namespace：`io.github.kevin77688.uvd`（**定案，不可更改**）。前身為 Threads Downloader（`dev.threadsdl.app`）。
- 詳細規劃、各平台擷取方式（Spike 結果）見 [PLAN.md](PLAN.md)；使用說明見 [README.md](README.md)／[README.zh-TW.md](README.zh-TW.md)。

## 技術棧（以 APK 小為優先）
- Kotlin + Jetpack Compose (Material 3)，單一 module `app`；minSdk 29、targetSdk 37、compileSdk 37.2
- 依賴：Compose BOM + activity-compose（＋必要的平台擷取函式庫，例如 YouTube 用 NewPipeExtractor）。
  不使用 Room / Hilt / Retrofit / media3 / WorkManager / Coil；網路用 `HttpURLConnection`（`core/Http.kt`），JSON 用 `org.json`，設定用 SharedPreferences。
  **新增任何依賴前先問使用者**，並注意授權必須與 GPL-3.0 相容。
- Release build 開啟 R8 minify + resource shrinking
- 版本號（AGP / Kotlin / Compose BOM / compileSdk）以實際 `sdkmanager --list` 與 AGP 相容表為準，不要憑記憶填

## 架構規則
- **每個平台的網址辨識與頁面／API 解析只放在 `extractor/<platform>/`**，實作 `Extractor` 介面並註冊到 `Extractors.all`。
  網站改版時只修該平台的 extractor。其他層（download / storage / ui）不得出現任何平台專屬邏輯。
- `Platform.id` 會寫進檔名並被圖庫讀回，**一旦發布就不能改**。
- `Extractor.accept()` 必須離線（不做網路請求），並為新平台在 `ExtractorsTest` 加上連結測試。
- 所有使用者看得到的文字放在 `res/values/strings.xml`，**同時**更新 `res/values-zh-rTW/strings.xml`。
- 給使用者的錯誤訊息用 `UserException(R.string.…)`；不要把英文字串寫死在程式碼裡。
- 下載一律走 `download/SegmentedDownloader`（多執行緒分段＋續傳重試＋大小核對），穩定性優先於速度。
  需要特殊 header（如 Referer）時放在 `MediaSource.headers`。

## 平台注意事項
- 剪貼簿只能在視窗取得焦點時讀取：以 window focus gained 觸發偵測（涵蓋從背景切回前景），不要用 `onResume`。
- 每次取得焦點時，若剪貼簿有支援的連結且與輸入框不同就填入；但只在剪貼簿內容變更（`ClipDescription.timestamp`）時才實際讀取，避免系統「已貼上」提示一直跳出。
- 連結解析：`LinkFinder` 從分享文字（含中文標點）中抽出網址；各 extractor 的 `accept()` 負責正規化（例如 Threads 去除 query 參數，YouTube 保留 `v=`）。
- 檔名：`<platform.id>_<yyyyMMdd_HHmmss>`（**下載當下時間**）；多個項目加序號 `_1`、`_2`。無前綴的舊檔名（Threads Downloader 1.x）視為 Threads。
- 下載位置：預設內部儲存根目錄 `UniversalDownloader/`（透過 SAF 授權），所有平台的圖片、影片放同一資料夾，不可拆到 Pictures / Movies 或子資料夾。
- 分享進來的連結：背景下載＋通知，不開啟主畫面。
- Logo 必須是原創設計，不可使用任何平台的官方圖示或商標圖形。

## 建置 / 測試指令
```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew assembleRelease
adb install -r --user 0 app/build/outputs/apk/release/app-release.apk
adb shell am start -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT "<link>" -n io.github.kevin77688.uvd/.ShareActivity
adb logcat -s UVD:I
```
（`--user 0`：避免 Samsung 雙開 App 設定檔。JDK、SDK 路徑與裝置序號見 `CLAUDE.local.md`。）
UI 檢查：`adb exec-out screencap -p > shot.png`、`adb shell uiautomator dump`（產生的檔案不要 commit）。

## 簽章規則（重要）
- `keystore/threads-downloader.jks` 為**唯一**簽章金鑰（憑證 SHA-256 `44c0235db6c00e987ccdc283e94c940d8f03f045f8e42ecc0dfa73d91a8e02c0`）。
  debug 與 release 都用它簽，確保 `adb install -r` 與 GitHub Release 的 APK 永遠可以互相覆蓋安裝。
- keystore 與 `keystore.properties` 都 gitignored —— **請自行備份**。CI 透過 Actions secrets
  （`KEYSTORE_BASE64`、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`，用 `scripts/set-release-secrets.sh` 上傳）取得同一把金鑰。
- 沒有 `keystore.properties` 時（fork、PR、Dependabot）：debug 用預設 debug key、release 不簽章 —— 這是刻意的。
- 絕對不要重新產生或替換 keystore。

## 版本與標籤（Claude 負責維護）
- 版本遵循 SemVer，**唯一來源是 `gradle.properties` 的 `appVersion`**。versionCode 由它自動計算且保證遞增
  （`X.Y.Z-rc.N` → `X*1e6 + Y*1e4 + Z*100 + N`，正式版 N=99）。
- 標籤格式 `vX.Y.Z`（正式版）或 `vX.Y.Z-rc.N`（預發布）。**只能用 `scripts/release.sh` 建立**：它會檢查版本遞增、
  工作目錄乾淨、在 main 上，然後改版本號、搬移 CHANGELOG、commit `chore(release): vX.Y.Z`、建立 annotated tag。
  CI（`.github/workflows/release.yml`）會拒絕與 `appVersion` 不符的標籤。
- 已推送的標籤**永不移動或刪除**；發布有問題就發下一個 patch。
- 版本號怎麼升：
  - **patch**：bug 修正、某平台改版後修 extractor、文字修正
  - **minor**：新增平台、新功能、明顯的 UI 變更
  - **major**：不相容變更（applicationId、儲存位置或檔名規則改變、提高 minSdk 等）
  - 改動 CI／簽章流程、或高風險變更時，先發 `-rc.N` 預發布驗證。
- **Claude 的工作流程**：
  1. 每完成一個使用者看得到的變更，就在 `CHANGELOG.md` 的 `## [Unreleased]` 底下記一筆（Added / Changed / Fixed）。
  2. 一組功能完成並在實機驗證後，主動建議下一個版本號（說明是 patch/minor/major 及原因）。
  3. **推送標籤會對外發布，必須先取得使用者同意**，再執行 `scripts/release.sh <version> --push`。
  4. 推送後確認 Release workflow 成功、Release 頁面有 APK 與 `.sha256`。
