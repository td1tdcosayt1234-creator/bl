# URL Blocker - install guide (Samsung)

## Ki banano hoise (v1.2)
- Full-device block: VPN (DNS filter) diye Chrome/YouTube/sob browser e block hobe
- FULL LOCK: switch ON + Start chaple full mobile internet off (kill-switch, sob app er net dead)
- App Lock: package name diye app lock, khulle PIN chaibe (Accessibility service)
- DoH seed: porichito DoH provider domain age thekei blocklist e thake
- In-App Browser: app er vitore WebView teo block hobe
- PIN lock: app open, Stop, Full Lock change e PIN lagbe

## Permission (must, 1 bar kore)
1. App open -> PIN set
2. URL add (ex: facebook.com) / FULL LOCK switch / App Lock e package add
3. Start Block -> VPN permission Allow
4. App Lock use korte: Enable AppLock button -> Accessibility list e URL Blocker ON
5. Samsung e sideload APK hole age: Settings > Apps > URL Blocker > 3 dot > Allow restricted settings > Allow

## Sotter limit (jana dorkar)
- Root/device-owner chara 100% tamper-proof somvob NA: phone hate thakle jekeo Settings theke VPN/Accessibility off ba app uninstall korte pare. Eta Android security, kono normal app atkaite pare na.
- IP-literal DoH (https://1.1.1.1/...) DNS filter e dhora pore na; FULL LOCK mode eta bondho kore (net tai off).
- Chrome Secure DNS custom provider thakle hoito bypass hobe; Private DNS Off rakho test er somoy.

## Permission (must, 1 bar)
1. App open -> PIN set
2. URL add (ex: facebook.com, tiktok.com, youtube.com)
3. Start Block -> VPN permission Allow
4. Samsung e sideload APK hole age: Settings > Apps > URL Blocker > 3 dot > Allow restricted settings > Allow

## Block test
- Full device: Chrome e facebook.com likhe dekho, khulbe na (DNS NXDOMAIN)
- In-App: "In-App Browser khulo" button -> url likho -> Blocked page dekhabe

## Build
Android Studio Ladybug+ e folder open -> Gradle sync -> Run (app-debug.apk)
- minSdk 26 (Android 8+), target 34

## Limit
- VPN ON thakte hobe status bar e key icon thakbe, etai normal
- VPN OFF korle block off (PIN chara off hobe na)
- Private DNS (dns.adguard) ON thakle conflict hote pare, test er somoy Private DNS Off rakho: Settings > Connections > More > Private DNS > Off
- Khub smart user VPN off kore dite pare, tai PIN + device nijer kache rakho
