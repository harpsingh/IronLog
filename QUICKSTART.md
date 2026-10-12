# IronLog – Quick Start (building and running on your Android phone)

No Android experience needed. This takes roughly 30–45 minutes the first time, mostly downloads.

## 1. Install Android Studio
1. Download it from <https://developer.android.com/studio> and install it (Windows, Mac or Linux).
2. Start it and accept the defaults in the setup wizard. It downloads the Android SDK for you.

## 2. Get the project
Pick one:

**Option A – Git (recommended)**
1. In Android Studio's welcome screen choose **Clone Repository**.
2. URL: `https://github.com/harpsingh/IronLog`
3. Click **Clone**, then switch to the branch `ccr-34af3ea3-bnewri`
   (bottom-right branch menu → *origin/ccr-34af3ea3-bnewri* → *Checkout*).

**Option B – ZIP**
On GitHub open the repo, switch to branch `ccr-34af3ea3-bnewri`, click **Code → Download ZIP**,
unzip it, then in Android Studio choose **Open** and select the unzipped `IronLog` folder.

## 3. Let it sync
Android Studio opens the project and runs a **Gradle sync** (progress bar at the bottom).
- The first sync downloads a lot, so wait until it finishes.
- If it asks to install a missing SDK ("Android 15 / API 35") or Build Tools, click the
  blue **Install** link and accept the licences.
- If it asks to upgrade Gradle/AGP, choose **Don't remind me** (the versions are already pinned).

## 4. Prepare your phone
1. **Settings → About phone** → tap **Build number** 7 times ("You are now a developer").
2. **Settings → System → Developer options** → turn on **USB debugging**
   (on Samsung: Settings → Developer options).
3. Plug the phone into your computer with a USB cable. Choose **File transfer / Android Auto**
   mode if the phone asks what the USB connection is for.
4. Accept the **Allow USB debugging?** prompt on the phone (tick *Always allow*).
   Windows users may need the phone maker's USB driver (Samsung, Xiaomi, etc.).

Prefer no cable? On Android 11+ use **Developer options → Wireless debugging** and pair it via
the *Device Manager → Pair devices using Wi-Fi* button in Android Studio.

## 5. Run
1. In the top toolbar, the device dropdown should show your phone's name.
2. Click the green ▶ **Run** button (or press `Shift+F10`).
3. The app builds, installs, and opens on your phone. The first build takes a few minutes.

## No phone? Use an emulator
**Tools → Device Manager → Create Device**, pick a Pixel, download a system image (API 34/35),
then press ▶ with that virtual device selected.

## Install without Android Studio (optional)
After step 3, from the project folder run:

    ./gradlew assembleDebug        # Windows: gradlew.bat assembleDebug

The APK is at `app/build/outputs/apk/debug/app-debug.apk`. Copy it to your phone and open it
(you must allow *Install unknown apps* for your file manager/browser). You still need the
Android SDK installed for this to build.

## Troubleshooting
| Problem | Fix |
|---|---|
| Phone not in the device list | Re-plug the cable, try another cable/port, re-accept the debugging prompt, check USB mode |
| "SDK location not found" | **File → Settings → Languages & Frameworks → Android SDK**, note the path; Android Studio normally writes `local.properties` for you |
| "Unsupported Java / JDK" error | **File → Settings → Build Tools → Gradle → Gradle JDK**, choose the bundled **jbr-17** (or newer) |
| Red compile errors after first sync | This project was written without being compiled. Copy the exact error text from the **Build** panel and send it to Claude to fix |
| Notification for rest timer never shows | On Android 13+ allow notifications when the app asks (or Settings → Apps → IronLog → Notifications) |
| App is slow in debug | Normal; a release build is much faster |

## Using the app
1. **Workouts** tab → pick *Full Body – Workout A* → **Start workout**.
2. Enter weight and reps, tap the circle to complete each set; the rest timer starts automatically.
3. Tap the ⓘ icon on an exercise to see how to do it.
4. **Finish** to save. Next time, the weights are pre-filled from this session.
5. **Settings** (gear icon, top-right of Workouts) → export `ironlog_backup.json` regularly.
   Uninstalling the app deletes your data, so keep a backup.
