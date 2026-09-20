# URL Blocker - install guide (Samsung)

## Ki banano hoise
- Full-device block: VPN (DNS filter) diye Chrome/YouTube/sob browser e block hobe
- In-App Browser: app er vitore WebView teo block hobe
- PIN lock: app open, Stop, change e PIN lagbe

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
