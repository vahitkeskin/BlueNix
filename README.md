---

```markdown
# 💠 BlueNix | Next-Gen Radar & Communication Protocol

![BlueNix Banner](https://capsule-render.vercel.app/api?type=waving&color=00f2ff&height=200&section=header&text=BlueNix&fontSize=80&fontColor=ffffff&fontAlign=50&animation=fadeIn&desc=Kotlin%20Multiplatform%20Cyberpunk%20Radar&descAlign=50)

<div align="center">

[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose Multiplatform](https://img.shields.io/badge/Compose_Multiplatform-1.7.0-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![Android](https://img.shields.io/badge/Android-14%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://www.android.com)
[![iOS](https://img.shields.io/badge/iOS-Native-000000?style=for-the-badge&logo=apple&logoColor=white)](https://www.apple.com/ios/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg?style=for-the-badge)](LICENSE)

**🛰️ Real-time GNSS Tracking • 📡 BLE Radar Scanning • 🧬 Cross-Platform Architecture**

</div>

---

## 🌌 Overview (Genel Bakış)

**BlueNix**, Kotlin Multiplatform (KMP) teknolojisi kullanılarak geliştirilmiş, fütüristik **Cyberpunk** arayüzüne sahip, yeni nesil bir Radar ve İletişim aracıdır.

Proje, internete ihtiyaç duymadan **Salt GPS (Pure GNSS)** verilerini işleyerek kullanıcının konumunu milimetrik hassasiyetle tespit eder ve çevredeki **Bluetooth Low Energy (BLE)** cihazlarını (Akıllı Saatler, Beacon'lar, IoT Cihazları) gerçek zamanlı bir radar üzerinde görselleştirir.

> *"The future is wireless, and we are the radar."*

---

## 📸 Visuals (Görseller)

| **Cyberpunk Radar (Home)** | **Real-time Chat (Upcoming)** | **Device Scanning** |
|:--------------------------:|:-----------------------------:|:-------------------:|
| ![Home Screen](https://via.placeholder.com/250x500/050B14/00F2FF?text=BlueNix+Radar) | ![Chat Screen](https://via.placeholder.com/250x500/050B14/00F2FF?text=Encrypted+Chat) | ![BLE Scan](https://via.placeholder.com/250x500/050B14/00F2FF?text=Device+List) |
| *Active Radar Animation* | *E2E Encryption* | *RSSI Distance Calc* |

---

## 🚀 Key Features (Temel Özellikler)

### 📡 Advanced BLE Radar
* **Low-Latency Scanning:** `BluetoothLeScanner` kullanılarak milisaniyelik gecikmeyle cihaz tespiti.
* **RSSI Distance Calculation:** Sinyal gücünden matematiksel formüllerle (`10^((Tx-RSSI)/20)`) mesafe tahmini.
* **Smart Filtering:** Arka planda gürültü yapan cihazları filtreleme ve anlamlı veri sunumu.

### 🛰️ Offline GNSS Precision
* **Internet-Free Navigation:** İnternet bağlantısı olmadan, doğrudan uydu sinyalleriyle (GPS, GLONASS, Galileo) konum tespiti.
* **High Accuracy Mode:** Android `FusedLocationProvider` ve `PRIORITY_HIGH_ACCURACY` ile 1-3 metre sapma payı.
* **Foreground Service:** Android 14+ uyumlu, uygulama kapalıyken bile kesintisiz takip.

### 🎨 Holographic UI/UX
* **Jetpack Compose:** Tamamen deklaratif, modern ve yüksek performanslı arayüz.
* **Custom Canvas Animations:** Dönen radar halkaları, neon efektler ve dinamik çizimler.
* **Dark/Neon Theme:** Göz yormayan, OLED dostu Cyberpunk renk paleti.

---

## 🛠️ Tech Stack (Teknoloji Yığını)

Proje, endüstri standardı **Clean Architecture** ve **MVVM** prensiplerine sadık kalınarak geliştirilmiştir.

| Category | Technology | Description |
| :--- | :--- | :--- |
| **Language** | ![Kotlin](https://img.shields.io/badge/-Kotlin-7F52FF?logo=kotlin&logoColor=white) | %100 Kotlin (Common, Android, iOS) |
| **UI Framework** | ![Compose](https://img.shields.io/badge/-Jetpack_Compose-4285F4?logo=jetpackcompose&logoColor=white) | Tek kod tabanı, native performanslı UI |
| **Architecture** | ![KMP](https://img.shields.io/badge/-KMP-EF5070?logo=kotlin&logoColor=white) | Kotlin Multiplatform (Share Logic, Native UI) |
| **DI** | ![Koin](https://img.shields.io/badge/-Koin-FF6F00?logo=koin&logoColor=white) | Hafif ve güçlü Dependency Injection |
| **Async** | ![Coroutines](https://img.shields.io/badge/-Coroutines-7F52FF?logo=kotlin&logoColor=white) | Asenkron işlem yönetimi (Flow & Channels) |
| **Permissions** | ![Moko](https://img.shields.io/badge/-Moko_Permissions-000000?logo=apple&logoColor=white) | Cross-platform izin yönetimi |
| **Build Tool** | ![Gradle](https://img.shields.io/badge/-Gradle_KTS-02303A?logo=gradle&logoColor=white) | Type-safe build scripts (libs.versions.toml) |

---

## 🏗️ Architecture (Mimari Yapı)

Proje, **"Common"** modülünde iş mantığını tutarken, platforma özgü (**Android/iOS**) yetenekleri `expect/actual` mekanizması ile sağlar.

```mermaid
graph TD;
    subgraph "Common Main (KMP)"
        A[UI Layer (Compose)] --> B[ViewModels]
        B --> C[Domain Layer (UseCases)]
        C --> D[Data Layer (Repositories)]
        D --> E[Interfaces (Location/Bluetooth)]
    end
    
    subgraph "Android Main"
        F[Android Services] --> E
        G[Activity/Permissions] --> A
    end
    
    subgraph "iOS Main"
        H[CoreLocation/CoreBluetooth] --> E
        I[ViewController] --> A
    end

```

---

## 🛡️ Permissions & Security (İzinler ve Güvenlik)

BlueNix, modern Android güvenlik standartlarına (API 34+) tam uyumludur.

* 🔐 **ACCESS_FINE_LOCATION:** Hassas GPS takibi için.
* 🔐 **BLUETOOTH_SCAN:** Çevresel cihazları taramak için (Android 12+).
* 🔐 **BLUETOOTH_CONNECT:** Cihazlarla iletişim kurmak için (Android 12+).
* 🔐 **FOREGROUND_SERVICE_LOCATION:** Arka planda kesintisiz çalışmak için.
* 🔔 **POST_NOTIFICATIONS:** Servis durumu hakkında kullanıcıyı bilgilendirmek için.

> *Not: Uygulama ilk açılışta tüm gerekli izinleri dinamik olarak talep eder ve kullanıcı reddederse güvenli bir şekilde (Graceful Degradation) çalışmaya devam eder.*

---

## 🔮 Roadmap (Gelecek Planları)

* [x] **v1.0:** Core KMP Setup, Location Service, BLE Scanner, Radar UI.
* [ ] **v1.1:** P2P Mesh Networking (İnternetsiz mesajlaşma).
* [ ] **v1.2:** AR (Augmented Reality) Kamera Modu ile cihazları havada görme.
* [ ] **v2.0:** iOS Native tam entegrasyonu ve App Store yayını.

---

## 👨‍💻 Developer

<div align="center">

**Vahit Keskin**
*Senior Android Developer & KMP Enthusiast*

</div>

---

<div align="center">
Made with ❤️ and lots of ☕ using <b>Kotlin Multiplatform</b>
</div>

```

```