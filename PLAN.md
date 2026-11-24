# Universal Video Downloader — 開發計畫

> 狀態：
> - v1.0.0（Threads Downloader，`dev.threadsdl.app`）已完成並在實機驗證（2026-11-20）
> - v2（Universal Video Downloader，`io.github.kevin77688.uvd`）：公開 repo、重構為多平台架構、加入 YouTube / Bilibili 等平台（進行中，2026-11-24）

## v2 規劃：多平台
### 已確認決策（2026-11-24）
| 項目 | 決定 |
|---|---|
| App 名稱 | Universal Video Downloader |
| applicationId / namespace | `io.github.kevin77688.uvd`（新 id，與舊版並存；簽章金鑰不變） |
| 授權 | GPL-3.0（因為使用 NewPipeExtractor） |
| YouTube | NewPipeExtractor（JitPack，社群維護；minSdk < 33 需 core library desugaring） |
| Bilibili 及其他平台 | 純 Kotlin extractor（`HttpURLConnection` + `org.json`） |
| 檔名 | `<platform>_<yyyyMMdd_HHmmss>[_n].<ext>`；平台前綴供圖庫篩選；無前綴舊檔視為 Threads |
| 預設資料夾 | `內部儲存空間/UniversalDownloader`，所有平台同一資料夾 |
| 圖庫 | 依平台篩選（含數量）、排序：最新／最舊／依平台分組 |
| 版本與發布 | `appVersion`（gradle.properties）＋ `scripts/release.sh` 建 tag，推 tag 觸發 GitHub Actions 簽章發布 |

### 架構（v2 重構後）
- `extractor/`：`Extractor` 介面（`accept()` 離線辨識、`extract()` 回傳 `MediaItem` 清單）、`Extractors` 註冊表、`LinkFinder`
- `MediaItem` 可帶 `headers`（如 Referer）與分離的 `audio` 串流（DASH，需合併）
- `download/`：`DownloadService` → `DownloadJob` → `SegmentedDownloader`（→ 合併影音）→ SAF
- `storage/`：`MediaFolder`（SAF）、`FileNames`（命名與平台判斷）

### 待辦
- [x] 公開前安全檢查、git 身分、gitignore、文件清理
- [x] 重構：套件更名、extractor 架構、字串資源（英文＋繁中）、單元測試
- [x] 圖庫平台篩選與排序
- [x] README（英／繁中）、LICENSE、Dependabot、CI、tag 觸發的 Release workflow
- [x] YouTube extractor（NewPipeExtractor）＋影音合併（MediaMuxer）— 實機：1080p60 h264＋aac，ffprobe／完整解碼無誤
- [x] Bilibili extractor（b23.tv 短網址、DASH 影音分離）— 實機：網頁路徑回 412，改走公開 API（WBI 簽章）成功；
  未登入只拿到 480p（API 列出最高 112）。412／`/zh-Hans/` 轉址／480p 可能與測試時的網路出口 IP 有關，尚未在其他網路驗證
- [x] 平行下載：共用分段佇列、影音同時下載、多項目同時下載（最多 4 個），整個工作共用連線上限（預設 8）。
  實測 16 條連線時 Bilibili 音訊明顯變慢（3 秒 → 21 秒，無錯誤），故上限維持 8
- [x] 圖庫每個項目加刪除按鈕（附確認對話框）
- [ ] 嘗試更多平台：條件為不需新依賴、未登入可用、`accept()` 能離線以網域辨識，且在 release APK 實機驗證通過才加入
  （候選：NewPipe 其他服務；X/Twitter、Reddit、Instagram、TikTok 等需先由使用者提供公開測試連結）

---

# v1 紀錄（Threads Downloader）

## 目標功能
1. **剪貼簿自動偵測**：App 開啟、或從背景切回前景時，偵測剪貼簿最新內容；若是新的 Threads 連結就自動填入輸入框。
2. **分享選單整合**：在 Threads 按「分享」可選擇本 App，收到連結後直接下載圖片/影片。
3. **主畫面**：一個連結輸入框 +「Paste from Clipboard」按鈕 +「Download Media」按鈕。
4. **設定**：主題（淺色 / 深色 / 跟隨系統）。
5. **圖庫**：檢視已下載的檔案（縮圖格狀排列，點擊以系統 App 開啟）。
6. **預設下載資料夾**：可在設定中變更；檔名以時間戳命名。
7. **固定簽章**：專案內產生一把 keystore，所有安裝都用它簽章。
8. **原創 Logo**：Adaptive icon（前景 / 背景 / monochrome）。

範圍限制：只支援**公開貼文**，不做登入。

## 階段

### Phase 0 — 開發環境（目前 Mac 上沒有 JDK / Android SDK / Gradle）
- `brew install openjdk@21`（或 17，依 AGP 需求）
- `brew install --cask android-commandlinetools` → `sdkmanager` 安裝 `platform-tools`、`platforms;android-XX`、`build-tools;XX`
- 用 Gradle wrapper（不全域安裝 Gradle，或只為產生 wrapper 暫時安裝）
- 版本以 `sdkmanager --list` 與 AGP 相容表決定；targetSdk 取 AGP 支援的最新版，並在 API 37 實機上驗證

### Phase 1 — 媒體擷取 Spike（最高風險，先做）
- 在 Mac 上用 curl 測試 3–4 種公開連結：單圖、單影片、輪播、圖片影片混合
- 方案 A：未登入抓貼文頁面（瀏覽器 UA），解析內嵌 JSON 中的 image / video URL
- 方案 B：`/embed` 頁面
- 方案 C（備援）：隱藏的系統 WebView 載入頁面後讀取 DOM（不增加 APK 大小）
- 輸出：確定可行方案 + 一份 parser 規格（貼文發布時間、作者、媒體清單）

### Phase 2 — 專案骨架
- Kotlin + Compose M3 單 module；R8 + shrinkResources
- 產生 `keystore/threads-downloader.jks` + `keystore.properties`（gitignored），debug / release 共用 signingConfig
- `git init` + `.gitignore`

### Phase 3 — 核心功能
- `LinkParser`：從任意文字抽出 threads.com / threads.net 貼文 URL、正規化
- `ThreadsExtractor`：Phase 1 的方案實作
- `SegmentedDownloader` + `PostDownloader`：分段下載到暫存檔，再寫入 SAF 資料夾
- 下載在 `dataSync` 前景服務中依序執行，通知顯示進度/結果（`POST_NOTIFICATIONS`）
- 圖庫直接列出資料夾內容，不另存下載紀錄

### Phase 4 — UI
- 主畫面：輸入框、兩個按鈕、下載狀態
- 剪貼簿：`LocalWindowInfo.isWindowFocused` 變為 true 時讀取，與上次值比對
- `ShareActivity`：接收 `ACTION_SEND text/plain`，排入下載後立即結束（透明 trampoline）
- 設定頁：主題、下載資料夾、（可選）檔名格式
- 圖庫頁：縮圖格狀（ImageDecoder / MediaMetadataRetriever 自製縮圖），點擊 `ACTION_VIEW`
- 底部導覽：下載 / 圖庫 / 設定

### Phase 5 — Logo
- 原創 adaptive icon（向量 XML），含 monochrome 層支援 Android 13+ 主題圖示

### Phase 6 — 實機測試
- `adb install -r`、`am start` 模擬分享、`screencap` / `uiautomator dump` 檢查畫面
- 測試淺色/深色、背景切回前景的剪貼簿偵測、四種貼文型態
- 確認 release APK 大小

## 已確認決策
| 項目 | 決定 |
|---|---|
| 開發環境 | Homebrew 安裝 openjdk@21 + android-commandlinetools |
| 檔名 | **下載當下時間**，例：`20261120_153012.jpg`；輪播多張加序號 `20261120_153012_1.jpg`、`_2.mp4` |
| 下載資料夾 | 內部儲存根目錄 `/storage/emulated/0/threads_downloader/`，**圖片與影片放同一個資料夾**（不分 Pictures / Movies） |
| 分享行為 | 背景直接下載＋通知，不開啟 App 畫面 |
| 下載方式 | IDM 式多執行緒分段下載（HTTP Range），設定頁可調 1–8 條（預設 4），穩定優先 |
| applicationId | `dev.threadsdl.app` |
| Logo | 黑色背景＋白色「波浪線流入下載匣」原創圖示 |

### 下載資料夾實作方式
- Android 10+ 的 MediaStore 不允許寫入根目錄的自訂資料夾，因此改用 **SAF（`ACTION_OPEN_DOCUMENT_TREE`）**：
  - 首次啟動 / 首次下載時開啟資料夾選擇器，`EXTRA_INITIAL_URI` 指向內部儲存根目錄，引導使用者建立並選擇 `threads_downloader`
  - `takePersistableUriPermission` 永久保存授權；設定頁可重新選擇其他資料夾
  - 寫入 SAF 後呼叫 `MediaScannerConnection.scanFile`，讓系統相簿也能看到
- 不使用 `MANAGE_EXTERNAL_STORAGE`（權限過大）
- 圖庫頁以 `DocumentsContract` 列出該資料夾內容

## Threads spike 結果（2026-11-20，已驗證）
純 HTTP 即可，**不需要 WebView**。流程：
1. **解析 share 短網址** `https://www.threads.com/share/<id>`：以**非瀏覽器 UA**（如 `curl/8`）請求、不跟隨轉址 → `302 Location: https://www.threads.com/@<user>/post/<code>?xmt=...`
   （瀏覽器 UA 會拿到 200 的 JS 空殼，沒有資料）
2. **抓貼文頁** `https://www.threads.com/@<user>/post/<code>`：必須帶**完整瀏覽器 header**：行動版 Chrome UA + `Accept`、`Accept-Language`、`Sec-Fetch-Mode: navigate`、`Sec-Fetch-Dest: document`、`Sec-Fetch-Site: none`、`Sec-Fetch-User: ?1`、`Upgrade-Insecure-Requests: 1`。少了 `Sec-Fetch-*` 就拿不到 JSON
   - `/embed` 頁面沒有媒體資料，不使用
   - 爬蟲 UA 的 `og:image` 是 Threads 產生的預覽卡片，**不是**原始媒體，不使用
3. **解析** `<script type="application/json">` 區塊，遞迴找 `code == <code>` 且含 `media_type` 的物件（頁面也有回覆與推薦貼文，一定要比對 code）
4. **媒體**：
   - `media_type`：1 = 圖片、2 = 影片、8 = 輪播（看 `carousel_media[]`）、19 = 純文字（沒有可下載的媒體）
   - 影片：`video_versions[0].url`（type 101，實測 1280×720 原始畫質）
   - 圖片：`image_versions2.candidates` 中 `width*height` 最大者（實測拿到原始解析度 1029×801）
   - CDN 網址（`scontent-*.cdninstagram.com`）直接 GET 即可下載，不需要 cookie
5. `taken_at` 為貼文發布時間（Unix 秒），目前檔名不使用

| 測試案例 | share 連結 | 結果 |
|---|---|---|
| 純文字 | `/share/<text-post>` | media_type 19，0 個檔案 → 應顯示「此貼文沒有圖片或影片」 |
| 純圖片 | `/share/<image-post>` | media_type 1，1 張 jpg 1029×801 ✅ |
| 純影片 | `/share/<video-post>` | media_type 2，1 支 mp4 1280×720 ✅ |
| 輪播（附帶發現） | `@<user>/post/<code>` | media_type 8，3 張圖 ✅（實機存成 `_1` `_2` `_3`） |

風險：這是非官方的網頁解析，Threads 改版就可能失效。parser 要集中在 `ThreadsExtractor` 一個類別，錯誤訊息要清楚。

## 分段下載設計（`SegmentedDownloader`）
- 先送 `Range: bytes=0-0` 探測：回 206 → 取得總大小並分段；回 200 → 退回單線下載
- 段數 = min(設定值, 檔案大小 / 512 KB)，小檔不會被切太碎
- 每段各自 `Range: bytes=pos-end` 寫入暫存檔（`RandomAccessFile` 定位寫入）；I/O 失敗時從已寫到的位置續傳，指數退避（1/2/4/8 秒），連續失敗 5 次才放棄，只要有收到資料重試次數就歸零
- 4xx（408/429 除外）視為永久錯誤，不重試
- 完成後核對實際收到的位元組數（不是預先配置的檔案長度），再複製到 SAF 資料夾，最後觸發 MediaScanner
- 實機驗證：1.28 MB 影片分 2 段，MD5 與單線下載完全一致

## 實作紀錄
- 版本：AGP 9.5.1（內建 Kotlin）、Kotlin Compose plugin 2.4.21、Gradle 9.9.2、Compose BOM 2026.11.01、activity-compose 1.13.0
- compileSdk 37.2（Compose 1.13 要求 ≥ 37.1）、targetSdk 37、minSdk 29
- 依賴只有 Compose + activity-compose；不用 WorkManager（會帶入 Room）、不用 Coil（縮圖用 ImageDecoder / MediaMetadataRetriever 自己做）
- 分享下載用 `dataSync` 前景服務，依序處理佇列
- 剪貼簿以 `ClipDescription.timestamp` 判斷是否變更，避免每次切回 App 都跳出系統「已貼上」提示
- Release APK 約 2.2 MB

## 已驗證（S23 / Android 17）
- [x] 開啟 App 自動填入剪貼簿連結；清空後切到背景再回來會重新填入
- [x] Share intent：純文字 → 通知「沒有圖片或影片」；純圖片、純影片 → 背景下載成功並通知
- [x] 主畫面 Download Media 流程、進度卡片
- [x] SAF 資料夾：在根目錄建立 `threads_downloader` 並授權，圖片影片同一資料夾
- [x] 圖庫縮圖（含影片播放圖示）
- [x] 淺色 / 深色 / 跟隨系統
- [x] 覆蓋安裝（`adb install -r`）保留設定與授權
- [x] 輪播貼文：3 個檔案 `_1` `_2` `_3`；連結夾在文字中、帶 query 參數也能解析
- [x] 檔案有被 MediaStore 索引（系統相簿看得到）
- [x] Paste from Clipboard 按鈕
- [x] Launcher icon（黑底）在 App 抽屜中顯示正常
- [ ] 從 Threads App 實際點分享按鈕（目前以 adb share intent 模擬）
- [ ] 斷線重試 / 續傳：尚未實際中斷下載測試；測試檔案太小，最多只切 2 段
- [ ] 圖片＋影片混合輪播（使用者表示不需測）

## 已知事項
- `adb install` 預設會裝到所有使用者，包含 Samsung 雙開 App 設定檔（user 95）→ 安裝指令一律加 `--user 0`
- App 名稱維持「Threads Downloader」
