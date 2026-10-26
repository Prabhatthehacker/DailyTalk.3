# Dailytalk: step-by-step setup guide

Dailytalk is a chat app with two smiling speech bubbles for a logo. (The app shows its name as **Daily Talk**, matching the logo. File, folder and repository names use the one-word form `dailytalk`.) You get two things from this project:

- a **website** (it opens in any browser), and
- an **Android app** (an APK file you install on a phone).

Both use the same free Firebase backend (**Authentication + Realtime Database**), so an account made on one works on the other.

**No credit card is needed anywhere in this guide.** Dailytalk uses Firebase's free **Spark** plan. Do not click any **Upgrade**, **Blaze** or **Billing** button. It does not use Firestore or Cloud Storage, which are the products that ask for a card.

You do **not** need to install Node, Android Studio or any other program. You only need a web browser, and GitHub builds everything for you. Plan for about 45 to 60 minutes the first time.

> **Be honest with yourself about one thing:** this code was written without being able to compile or run it. Your first build may fail with a small error. That is normal. Part 10 shows how to get it fixed.

---

## Checklist

Tick these off in order.

- [ ] Part 1. Make a Google account and a GitHub account
- [ ] Part 2. Create the Firebase project (the backend)
- [ ] Part 3. Upload Dailytalk to GitHub
- [ ] Part 4. Give GitHub your Firebase values
- [ ] Part 5. Turn on GitHub Pages and build the website
- [ ] Part 6. Build the Android app (APK) and install it
- [ ] Part 7. Test with two accounts
- [ ] Part 8. Share Dailytalk with friends
- [ ] Part 9. Optional extras (GIF search, calls on every network, photo uploads from the website)
- [ ] Part 10. If something goes red

Words you will see:

- **Repository (repo):** your project's folder on GitHub.
- **Workflow:** a robot on GitHub that builds your website or APK.
- **Variable / Secret:** a setting you store in GitHub so the robot can use it.

---

## Part 1. Make the accounts

1. **Google account.** Use an existing Gmail. You need it for Firebase.
2. **GitHub account.** Go to https://github.com/signup and sign up (free).
3. Write down your GitHub **username**. Use it in lowercase everywhere below. This guide calls it `YOURUSERNAME`.

---

## Part 2. Create the Firebase project

Firebase menu names change now and then. If a label looks slightly different, pick the closest one. Some items have moved under headings like **Security** or **Databases & Storage**.

### 2.1 Create the project

1. Go to https://console.firebase.google.com
2. Click **Create a project**.
3. Name it `dailytalk`.
4. You can turn Google Analytics **off**.
5. Click **Create project**, then **Continue**.
6. Check the plan label at the bottom left of the console. It should say **Spark**. Leave it that way.

### 2.2 Turn on sign-in

1. In the left menu find **Authentication**. It may be under **Security** or **Build**.
2. Click **Get started**.
3. Click **Email/Password**.
4. Switch **Enable** on and click **Save**.

### 2.2b Optional: turn on "Continue with Google" (website only)

The sign-in screen has a **Continue with Google** button on the website. It stays free and needs no card.

1. Go to **Authentication**, then **Sign-in method**.
2. Click **Add new provider**, then **Google**.
3. Switch **Enable** on, pick your email as the **Public-facing name / support email**, and click **Save**.
4. Do step 2.7 (authorized domains) before you test it.

The Android app does **not** have the Google button. Google sign-in on Android needs a fixed signing key registered with Firebase, and the debug APK that GitHub builds gets a new key every time. People sign in to the app with email and password. A website account made with Google has no password, so on the phone use **Forgot password?** once to set one.

### 2.3 Create the Realtime Database

Use **Realtime Database**, not Firestore.

1. In the left menu find **Realtime Database**. It may be under **Build** or **Databases & Storage**.
2. Click **Create Database**.
3. Choose a location. Only three are offered: United States, Belgium and Singapore. For India, choose **Singapore (asia-southeast1)**, because it is the closest.
4. Choose **Start in locked mode**. (You will paste the real rules in the next step.)
5. Click **Enable**.
6. On the **Data** tab, look at the top. You will see an address like `https://dailytalk-12345-default-rtdb.asia-southeast1.firebasedatabase.app`. **Copy it into a notepad.** This is your **Database URL**.

### 2.4 Paste in the security rules (do not skip this)

The rules keep private chats private and make sure every Dailytalk number is unique.

1. Unzip `Dailytalk.zip` on your computer.
2. Open the file `database.rules.json` from the unzipped folder in any text editor (Notepad is fine).
3. Select **all** the text and copy it.
4. In Firebase go to **Realtime Database**, then the **Rules** tab.
5. Select everything in the box, delete it, and paste what you copied.
6. Click **Publish**.

### 2.5 Register the website app

1. Click the gear icon at the top left, then **Project settings**.
2. On the **General** tab scroll to **Your apps**.
3. Click the **`</>`** (Web) icon.
4. App nickname: `web`. Leave "Firebase Hosting" **unticked**. Click **Register app**.
5. You will see a block of code called `firebaseConfig`. **Keep this page open**, or copy it into a notepad. You need these values:
   - `apiKey`
   - `authDomain`
   - `projectId`
   - `messagingSenderId`
   - `appId`
   - `databaseURL` (if it is missing, use the Database URL from step 2.3)
6. Click **Continue to console**.

### 2.6 Register the Android app

1. In **Project settings**, **General**, **Your apps**, click **Add app**, then the **Android** icon.
2. Android package name: **exactly** `com.dailytalk.app`
3. Click **Register app**.
4. Click **Next**, **Next**, **Continue to console**. Do **not** download anything.
5. Back in **Your apps**, click the Android app. Copy its **App ID**. It looks like `1:123456789:android:abcdef123456`. This is different from the web `appId`.

### 2.7 Allow your website to sign in

1. Go to **Authentication**, then **Settings**, then **Authorized domains**.
2. Click **Add domain**.
3. Type `YOURUSERNAME.github.io` (your username in lowercase, no `https://`, no `/dailytalk`).
4. Click **Add**.

---

## Part 3. Upload Dailytalk to GitHub

### 3.1 Create the repository

1. On GitHub click the **+** at the top right, then **New repository**.
2. Repository name: `dailytalk`.
3. Choose **Public**. Free GitHub Pages hosting needs a public repository.
4. Do **not** tick "Add a README".
5. Click **Create repository**.

**Your website address will be:** `https://YOURUSERNAME.github.io/dailytalk/`

> Want the shorter address `https://YOURUSERNAME.github.io/` with no `/dailytalk`? Name the repository exactly `YOURUSERNAME.github.io` instead. Everything else in this guide stays the same.

### 3.2 Upload the files

**Important:** upload what is *inside* the `Dailytalk` folder, not the `Dailytalk` folder itself.

1. Open the unzipped `Dailytalk` folder until you can see `README.md`, `android`, `web` and `firebase.json`.
2. Select **everything inside it**: press Ctrl+A on Windows, Cmd+A on Mac.
3. Choose one of these two ways to upload.

**Option A (easiest): GitHub Desktop**

1. Install GitHub Desktop from https://desktop.github.com and sign in.
2. Click **File**, then **Clone repository**, pick `dailytalk`, and clone it.
3. Copy everything from inside the `Dailytalk` folder into the cloned folder. Hidden folders come along automatically.
4. In GitHub Desktop type a summary like `first upload`, click **Commit to main**, then **Push origin**.

**Option B: browser only**

1. On your empty repository page click **uploading an existing file**.
2. Drag everything from inside the `Dailytalk` folder into the page.
3. Click **Commit changes**.
4. On Mac, first press Cmd+Shift+. in Finder so the hidden `.github` folder shows up. Windows shows it normally.

### 3.3 Check the upload

The top of your repository must show exactly these:

`.github`, `android`, `backend`, `scripts`, `web`, `worker`, `README.md`, `database.rules.json`, `firebase.json`

- If you see a single folder called `Dailytalk` instead, delete the upload and redo it one level deeper.
- Open `.github`, then `workflows`. You must see `web.yml` and `android.yml`.
- If `.github` is missing, click **Add file**, then **Create new file**. Type `.github/workflows/web.yml` as the name, paste in the contents of that file from your unzipped folder, and click **Commit changes**. Repeat for `android.yml`.

---

## Part 4. Give GitHub your Firebase values

1. In your repository click **Settings**.
2. Click **Secrets and variables**, then **Actions**.
3. You will see two tabs: **Variables** and **Secrets**. For each row below click **New repository variable** (or **New repository secret**), type the name **exactly** as shown, paste the value, and save.

### Variables tab

| Name | Value |
|---|---|
| `VITE_FIREBASE_API_KEY` | `apiKey` from step 2.5 |
| `VITE_FIREBASE_AUTH_DOMAIN` | `authDomain` |
| `VITE_FIREBASE_PROJECT_ID` | `projectId` |
| `VITE_FIREBASE_DATABASE_URL` | your **Database URL** (step 2.3) |
| `VITE_FIREBASE_MESSAGING_SENDER_ID` | `messagingSenderId` |
| `VITE_FIREBASE_APP_ID` | `appId` (the one containing `:web:`) |
| `FIREBASE_PROJECT_ID` | the same `projectId` |
| `FIREBASE_DATABASE_URL` | the same Database URL |
| `DEPLOY_PAGES` | `true` |

### Secrets tab

| Name | Value |
|---|---|
| `FIREBASE_API_KEY` | the same `apiKey` |
| `FIREBASE_APP_ID` | the **Android** App ID from step 2.6 (it contains `:android:`) |

A misspelled name is the most common mistake. Check each one letter by letter. The Database URL must start with `https://` and have no space or slash at the end.

---

## Part 5. Turn on GitHub Pages and build the website

1. In your repository click **Settings**, then **Pages**.
2. Under **Build and deployment**, set **Source** to **GitHub Actions**.
3. Leave **Custom domain** empty. The free `github.io` address is what you want.
4. Click the **Actions** tab.
5. If you see a green button saying "I understand my workflows, go ahead and enable them", click it.
6. In the list on the left click **Website**.
7. Click **Run workflow**, then **Run workflow** again.
8. Wait 3 to 5 minutes until you see a green tick.
9. Open `https://YOURUSERNAME.github.io/dailytalk/`.
10. Click **Create account** and sign up.

Notes:

- The build job and the deploy job are what matter. A separate job called **Database rules tests** may show red. That does not stop your website from deploying.
- The address is also shown in **Settings**, then **Pages**.

---

## Part 6. Build the Android app (APK)

1. Click the **Actions** tab.
2. Click **Android** in the list on the left.
3. Click **Run workflow**, then **Run workflow** again.
4. The first run takes 8 to 15 minutes. Wait for a green tick.
5. Click the finished run, scroll down to **Artifacts**, and download **Dailytalk-debug-apk**. It is a zip file.
6. Unzip it. Inside is `app-debug.apk`.
7. Send `app-debug.apk` to your Android phone (USB cable, Google Drive, WhatsApp to yourself, and so on).
8. On the phone tap the file. If asked, allow **Install unknown apps** for the app you opened it with. You need Android 8.0 or newer.
9. Open **Dailytalk** and sign in. An account made on the website works in the app, and the other way round.

---

## Part 7. Test with two accounts

1. Make two accounts: for example the website on your laptop and the app on your phone.
2. When you sign up, each account is **given its own 10-digit Dailytalk number automatically**.
3. Open **Settings** (tap your name or the gear). **Write your number down.** It cannot be changed.
   - To **log out**: on the home screen (the chat list), on the website and in the app, tap the **Log out** button at the top. You can also use **Settings**, **Log out**. You are asked to confirm, then you are back on the sign-in screen.
   - To **sign in again**: type your email and password and tap **Sign In**. Forgot it? Tap **Forgot password?**
4. In account A: **Add**, then **Add a chat**. Type account B's number and send a message. Account B should see it appear.
5. Try each of these:
   - a group: **Add**, **Add a group**, one number per line
   - a status: open the **Status** tab
   - a photo or video: the paperclip button on the website, **Media** in the app
   - a voice or video call: the phone and camera buttons, or the three-dot menu. Allow microphone and camera when asked.
   - a GIF (needs the optional key in Part 9)

Dailytalk numbers are not real phone numbers. They only work inside Dailytalk.

---

## Part 8. Share Dailytalk with friends

1. **Website:** send them `https://YOURUSERNAME.github.io/dailytalk/`. It works in any browser.
2. **Android app:** send them `app-debug.apk`. This is a test build, not on Google Play. They must allow **Install unknown apps** once. Android may show a warning screen. That is normal for a debug APK.
3. **How friends find you:** tell them your Dailytalk number. They type it under **Add**, then **Add a chat**.
4. Tell them that photos and videos go to a free public host. Anyone with the link can open them, and they cannot be deleted from Dailytalk. The app warns people the first time they send one.
5. **Size limit:** the free database allows about 100 people connected at the same moment. That is plenty for friends and family.

### Changing the app later

- **Edit a file:** open it on GitHub, click the pencil icon, edit, then **Commit changes**. The workflows rebuild by themselves. You can also use **Run workflow**. A new APK appears under **Actions**, **Android**, **Artifacts**.
- **If you change `database.rules.json`:** also paste the new text into Firebase under **Realtime Database**, **Rules**, then click **Publish**. GitHub does not do this for you.
- **A new APK:** install it over the old one. Nothing is lost, because accounts and chats live in Firebase.
- **Change the logo:** replace these image files with new ones of the same name and size, then commit them:
  - `web/src/assets/dailytalk-logo.png` (the two bubbles only, 512 by 512, transparent background)
  - `web/src/assets/dailytalk-lockup.png` (bubbles + name + tagline for the sign-in screen, 640 wide, transparent background)
  - `web/public/favicon.png` (96 by 96)
  - `android/app/src/main/res/drawable-nodpi/dailytalk_logo.png` (512 by 512) and `dailytalk_lockup.png` (640 wide)
  - the five `ic_launcher_foreground.png` files in `android/app/src/main/res/drawable-mdpi` up to `drawable-xxxhdpi` (108, 162, 216, 324 and 432 pixels square, with the logo centred and about 60% of the width)

---

## Part 9. Optional extras

You can skip all of these and Dailytalk still works.

### GIF search (Giphy)

1. Create a free key at https://developers.giphy.com (Create an App, then choose API).
2. In GitHub add a **Variable** `VITE_GIPHY_API_KEY` and a **Secret** `GIPHY_API_KEY`, both with your key.
3. Re-run the **Website** and **Android** workflows.

### Calls that work on every network (TURN)

Voice and video calls work on many home networks without this, but fail on some mobile or office networks.

1. Get a TURN server from a provider and check its current free limits.
2. In GitHub add **Variables** `VITE_TURN_URL` and `TURN_URL` (the address looks like `turn:host:3478`).
3. Add **Secrets** `VITE_TURN_USERNAME`, `VITE_TURN_CREDENTIAL`, `TURN_USERNAME` and `TURN_CREDENTIAL`.
4. Re-run both workflows.

These values can be seen by anyone who inspects the site or app.

### If photo and video uploads fail on the website

Photos and videos go straight from the app to the free host catbox.moe (up to 200 MB each), and only the link is saved in the chat. The Android app should just work. I could not check whether the host allows uploads from a web page. If the website says the file host "could not be reached, or it blocked this website" while your internet works, use the proxy:

1. Create a free Cloudflare account.
2. Click **Workers & Pages**, **Create**, **Create Worker**. Name it `dailytalk-upload`, then **Deploy**.
3. Click **Edit code**, replace everything with the contents of `worker/catbox-proxy.js`, then **Deploy**.
4. Open the Worker's **Settings**, then **Variables and Secrets**. Add `ALLOWED_ORIGIN` with the value `https://YOURUSERNAME.github.io` (no path, no trailing slash). Optionally add `MAX_BYTES` with `100000000`.
5. Copy the Worker address, which looks like `https://dailytalk-upload.YOURNAME.workers.dev`.
6. In GitHub add **Variables** `VITE_UPLOAD_URL` (that address) and `VITE_MAX_UPLOAD_MB` (`100`). Cloudflare limits request sizes on its free plan, so check the current limit and match it.
7. Re-run the **Website** workflow.

---

## Part 10. If something goes red

Because the code was never compiled before, a first-build error is possible.

1. Open the **Actions** tab and click the red run.
2. Click the red step.
3. Copy the red error lines.
4. Paste them to Claude together with the file name. A one-line fix usually solves it.
5. To change a file: open it on GitHub, click the pencil icon, edit, then **Commit changes**.
6. Re-run the workflow with **Run workflow**.

### Common problems

| What you see | Fix |
|---|---|
| Website says "Almost there" | A `VITE_FIREBASE_*` variable is missing or misspelled, most often `VITE_FIREBASE_DATABASE_URL`. Fix it and re-run **Website**. |
| App says "Almost there" | `FIREBASE_API_KEY` or `FIREBASE_APP_ID` is missing from Secrets. Fix it and re-run **Android**. |
| "Permission denied" in the app or site | The rules are not published, or an old version is. Paste `database.rules.json` into Firebase under **Realtime Database**, **Rules** and click **Publish**. |
| Nothing loads and there is no error | The Database URL is wrong. It must be exactly the address on the **Data** tab of Realtime Database. |
| Sign-in says "operation not allowed" | Turn on Email/Password in step 2.2. |
| Sign-in fails only on the website | Add `YOURUSERNAME.github.io` in step 2.7. |
| "Continue with Google" says the method is not enabled | Do step 2.2b. |
| "Continue with Google" window does not open | Allow pop-ups for the site. Also check step 2.7. |
| Website shows a blank page | Check **Settings**, **Pages**: Source must be **GitHub Actions**. Also check that `DEPLOY_PAGES` is `true`. |
| "No Dailytalk account has this number" | Check the 10 digits. Numbers are shown in the other person's Settings. |
| Call stays on "Connecting…" | Allow microphone and camera. Both people must be signed in with the app or page open. Add TURN from Part 9. |
| GIFs do not appear | Add the Giphy key from Part 9. |
| Photos or videos fail | See the Part 9 section on upload failures. Also check the file: only JPEG, PNG, WebP, GIF, MP4, WebM and MOV, up to 200 MB. |
| Realtime Database also asks for a credit card | Stop and tell Claude. The backend would then have to be rebuilt on another free service. That is a larger change. |

---

## How it works (short technical notes)

- **Website:** React and TypeScript, hosted free on GitHub Pages. It is an ordinary website, not a PWA (there is no manifest, service worker or install prompt, and a build check enforces that).
- **Android app:** a native Kotlin app built with Jetpack Compose. It is not a WebView wrapper.
- **Sign-in:** email and password on the website and in the app, plus Google on the website. Log out is a button at the top of the home screen and is also in Settings.
- **Backend:** Firebase Authentication and the Realtime Database. Both clients read and write the same data. All access is controlled by `database.rules.json`.
- **Data layout:** `users`, `phoneIndex` (number to account, create-only so a number can never be taken over), `contacts`, `cleared`, `userChats`, `conversations` (with `meta`, `members`, `admins`, `messages`), `statuses`, `statusFeed`, `statusViews`, `calls` and `callInbox`.
- **Dailytalk numbers:** at sign-up the app picks a random 10-digit number (first digit 1 to 9). The database refuses a number that is already taken, and the app tries another. The number can be set once and never edited. It is not a telephone number.
- **Statuses:** text only, last 24 hours. The rules refuse to show a status after it expires. The app deletes expired ones when someone opens it.
- **Calls:** one-to-one WebRTC voice and video. The two phones or browsers exchange connection details through the database. TURN is optional (Part 9).
- **Photos and videos:** uploaded to catbox.moe. Only a link is saved, and the rules accept only `https://files.catbox.moe/...` links.
- **Tests:** `backend/tests/rules.test.ts` tests the database rules in the Firebase emulator. Unit tests for numbers and media checks live in `web/src/lib` and `android/app/src/test`. **None of these have been run yet.** The workflows run them for you.
- **Updating rules:** changing `database.rules.json` on GitHub does not change Firebase. Paste it in the console too (Part 8).

## Good to know (limits)

- **No notifications when the app or tab is closed.** Messages and calls only notify while Dailytalk is open or running in the background. Real push needs a server that this project does not have.
- **Calls are one-to-one.** Group calls are not supported.
- **Photos and videos are public links** on a free outside host. You cannot delete them or moderate them from Dailytalk, and the host can change its rules or go offline. Do not use it for private material.
- **Free database limits.** Firebase's free plan allows about 100 people connected at once and 1 GB stored. Text chats use very little space. Firebase can change these limits, so check them before relying on them.
- **Statuses** are text only and last 24 hours.
- **Account deletion** removes the profile, number, saved contacts and chat-list entries. Messages already sent stay in other people's chats, and the deleted number cannot be recovered.
- **Messages are not end-to-end encrypted.** They are protected in transit and by the security rules, but they are readable in your Firebase project.
- **Anyone can start a chat with you** if they know your number, and anyone can add you to a group. There is no block button yet.
- **Nothing here was tested against real devices or a real Firebase project.** Expect to fix small things.
- Free-tier limits for Firebase, GitHub, Giphy and TURN providers change, so check their current pricing before relying on them.
