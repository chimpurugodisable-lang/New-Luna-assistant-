# Luna 🌙

A hands-free voice assistant for Android. Say **"Hey Luna"** and tell it what to do, in English, Yorùbá, Igbo or Hausa.

## What Luna can do (version 0.1)

| Say | Luna does |
|---|---|
| "Hey Luna… open WhatsApp" | Opens any installed app by name |
| "Hey Luna… call Mummy" | Calls a contact |
| "Hey Luna… text Tunde saying I'm on my way" | Sends an SMS |
| "Hey Luna… open camera" | Opens the camera |

Yorùbá: *ṣí WhatsApp*, *pe Mummy* · Igbo: *mepee WhatsApp*, *kpọọ Mummy* · Hausa: *buɗe WhatsApp*, *kira Mummy*.
English commands always work too, whichever language is chosen.

## Getting the app onto your phone

1. Put these files in a GitHub repository (branch `main`).
2. GitHub builds the app automatically (Actions tab, about 5–8 minutes).
3. Open the repository's **Releases** page and tap **Luna.apk** to download.
4. Install it. Android will ask you to allow installing from your browser; allow it.

## First-time setup (inside Luna)

1. **Grant permissions**: microphone, contacts, calls, SMS and notifications.
2. **Allow Luna to open apps**: turn on "Display over other apps". Without it, Android blocks Luna from opening apps while you're on another screen.
3. **Keep Luna running**: turns off battery saving for Luna. Very important on Tecno, Infinix, itel and Xiaomi phones, which close background apps aggressively.
4. Tap **Start Luna**. A small notification shows while Luna is listening.

## How it works

- **"Hey Luna"** is detected fully offline with [Vosk](https://alphacephei.com/vosk/), so no data is used while waiting.
- **Your command** is understood by the phone's built-in speech recogniser (usually Google's), in the language you picked.
- **Luna's reply** is spoken with the phone's text-to-speech voice.

## Known limits of this first version

- Yorùbá, Igbo and Hausa command words and replies are a first draft and need checking by native speakers. They live in `Language.kt` and `CommandParser.kt`.
- Understanding commands in Yorùbá, Igbo or Hausa depends on the phone's speech recogniser supporting that language; many phones need the Google app and an internet connection for it.
- Most phones have no Yorùbá, Igbo or Hausa voice yet, so Luna speaks the reply in English while showing it in your language.
- The wake word can occasionally trigger on similar-sounding words. If "Hey Luna" isn't heard, use the **Talk** button in the notification.
- Luna must be started from the app once after each phone restart.
