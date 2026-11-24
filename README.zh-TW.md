# Universal Video Downloader

[English](README.md) | **繁體中文**

[![CI](https://github.com/kevin77688/video-downloader-app/actions/workflows/ci.yml/badge.svg)](https://github.com/kevin77688/video-downloader-app/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/kevin77688/video-downloader-app?include_prereleases&sort=semver)](https://github.com/kevin77688/video-downloader-app/releases)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

一個輕量的 Android App，可以從多個平台的**公開**貼文下載影片和圖片。
從任何 App 分享連結給它（或直接貼上），媒體就會存進手機上的同一個資料夾。

- **不需帳號、不用登入、沒有廣告、不追蹤。** 只支援公開內容。
- **很小：** 約 2–3 MB。Kotlin + Jetpack Compose，不使用大型函式庫。
- **穩定下載：** 檔案切成多段同時下載，失敗的分段會自動續傳重試，最後核對檔案大小。

## 支援平台

| 平台 | 連結 | 狀態 |
|---|---|---|
| Threads | `threads.com` / `threads.net` 貼文與分享連結 — 圖片、影片、輪播 | ✅ |
| YouTube | 影片、Shorts、`youtu.be` 連結 | 🚧 開發中 |
| Bilibili | `bilibili.com/video/…`、`b23.tv` 短網址 | 🚧 開發中 |

新平台以獨立的 *extractor* 加入，請見[新增平台](#新增平台)。

## 功能

- **分享選單：** 在 Threads／YouTube／Bilibili／瀏覽器點「分享」，選 *Universal Video Downloader*。下載會在背景執行並顯示通知，不會開啟 App 畫面。
- **剪貼簿偵測：** 複製連結後打開 App，會自動填入。
- **圖庫：** 可依平台篩選、排序（最新、最舊、依平台分組），長按可刪除。
- **所有檔案放在同一個資料夾**（預設 `內部儲存空間/UniversalDownloader`，可在「設定」變更）。檔名為 `<平台>_<yyyyMMdd_HHmmss>.<副檔名>`，例如 `threads_20261124_153012.mp4`；多個項目的貼文會加上 `_1`、`_2`…。
- 淺色／深色／跟隨系統主題、Material You 配色、英文與繁體中文介面。

## 安裝

1. 從 [Releases](https://github.com/kevin77688/video-downloader-app/releases) 下載最新的 `UniversalVideoDownloader-vX.Y.Z.apk`。
2. 在手機上開啟，並允許安裝此來源的 App。
3. 第一次下載時，App 會請你選擇資料夾：在內部儲存空間建立 `UniversalDownloader`，再點「使用此資料夾」。

需要 Android 10（API 29）以上。

每個版本都用同一把金鑰簽章，新版可以直接覆蓋安裝舊版。可以用
`apksigner verify --print-certs <apk>` 檢查簽章，憑證 SHA-256 必須是
`44c0235db6c00e987ccdc283e94c940d8f03f045f8e42ecc0dfa73d91a8e02c0`。

## 從原始碼建置

需求：JDK 21、Android SDK（`platforms;android-37.2`、`build-tools;37.0.0`）。

```bash
./gradlew testDebugUnitTest   # 單元測試
./gradlew assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease     # 經過 R8 縮減的 release APK
```

簽章是選用的。沒有金鑰時，debug 版使用預設 debug key，release 版不簽章。
若要用自己的金鑰簽章，在專案根目錄建立 `keystore.properties`（已被 git 忽略）：

```properties
storeFile=keystore/your-key.jks
storePassword=…
keyAlias=…
keyPassword=…
```

## 專案結構

```
app/src/main/java/io/github/kevin77688/uvd/
├── extractor/          # 每個平台一個子套件；只有這裡知道網站的網址與頁面結構
│   ├── Extractor.kt    # 介面 + MediaItem / Extraction 資料模型
│   ├── Extractors.kt   # 所有 extractor 的註冊表
│   ├── LinkFinder.kt   # 從分享文字中找出網址
│   └── threads/
├── download/           # 前景服務、下載流程、分段下載器
├── storage/            # SAF 下載資料夾、檔名規則
├── settings/           # 偏好設定
├── core/               # HTTP 工具、給使用者看的錯誤訊息
└── ui/                 # Compose 畫面（下載、圖庫、設定）
```

### 新增平台

1. 在 `extractor/Platform.kt` 新增一個項目（它的 `id` 會成為檔名前綴，之後絕對不能改）。
2. 建立 `extractor/<平台>/<Name>Extractor.kt` 實作 `Extractor`：
   `accept(url)` 離線辨識並正規化連結；`extract(url)` 回傳要下載的媒體。
3. 在 `Extractors.all` 註冊，並在 `app/src/test/…/ExtractorsTest.kt` 加上連結測試。

## 發布版本（維護者）

版本號遵循 [SemVer](https://semver.org)。`gradle.properties` 中的 `appVersion` 是唯一的版本來源。

```bash
scripts/release.sh 2.1.0-rc.1 --push   # 預發布版
scripts/release.sh 2.1.0 --push        # 正式版
```

腳本會更新 `appVersion`、commit、建立 `vX.Y.Z` 標籤並推送。接著
[Release workflow](.github/workflows/release.yml) 會執行測試、建置並簽章 APK、核對憑證，
最後發布附有 APK 與 SHA-256 校驗碼的 GitHub Release。

## 免責聲明

本專案僅供個人使用，請只下載你有權下載的內容。請尊重著作權與各平台的服務條款 —
部分平台（例如 YouTube）的條款不允許下載。
本 App 與 Meta、Threads、Google、YouTube、Bilibili 無任何關聯，也未獲其認可。所有商標皆屬於其擁有者。

## 授權

[GNU General Public License v3.0](LICENSE)。
