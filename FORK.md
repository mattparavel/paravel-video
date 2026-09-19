# Paravel Video — experimental tablet fork

Based on NewPipe v0.29.1, commit 00acf2f318b0af7ffacbd687bba011b642cc01d0.
Modified September 18, 2026 by Paravel contributors; GPL-3.0-or-later.

## Changes

- Separate Paravel Video name, icon and application ID (`com.paravel.video.debug`
  for the prototype, `com.paravel.video` for release).
- Minimize-on-exit defaults to NewPipe's floating popup player. The first use
  requires Android's “display over other apps” permission. The setting remains
  user-configurable. This uses NewPipe's existing popup, not Android's native PiP.
- An explicit screengrab request can read the visible video's ID, playback time
  and on-screen bounds through a signature-protected Android provider.
- tldraw pen consumes that provider, saves a timestamped backlink on the cropped
  image, and opens it in its existing movable/resizable/rotatable in-app player.
  Crops that miss the popup do not receive a video backlink.

No playback history is exposed through the capture provider. It returns nothing
for background audio, off-screen main playback, live streams, buffering or an
unavailable player. It never starts playback or the player service. Requests run
on the player thread and time out after 700 ms. Calls require the signature
permission `com.paravel.video.CAPTURE_POSITION`; both apps must be signed with the
same appropriate key. Do not change this permission to normal for OS deployment.

## Capture contract

Authority: `${applicationId}.positions`. Method: `capture_position`.
Version-1 Bundle: `version` (int), `video_id` (string), `seconds` (double),
`captured_at` (Unix milliseconds), `video_bounds` (int array: left, top, right,
bottom in screen pixels). No rows or other provider operations are exposed.
Clients must compare fresh samples around capture and validate the crop bounds.

## Build and distribution

Use JDK 21 and the Android SDK; upstream requires compile SDK 37 and downloads
Gradle through its checked wrapper. Build with `./gradlew :app:assembleDebug`.
NewPipe's code-style checks are part of the build. The matching tldraw pen build
is maintained separately; its source does not incorporate NewPipe player code.

An opt-in live device test, `CaptureIntegrationTest`, opens a public sample video,
checks the capture contract, then goes home and verifies that the floating player
keeps playing. Build it with `./gradlew :app:assembleDebugAndroidTest` and run it
with AndroidJUnitRunner after granting the app's overlay permission. Playback,
position capture and automatic floating playback passed on the Daylight tablet.

The app retains upstream's license/about screens. Its upstream updater already
rejects non-upstream signatures. This fork's APKs are distributed separately.
Keep the corresponding source and build instructions available with every APK.
This prototype does not complete the signing, update, attribution/trademark and
installation-information review needed for a commercial OS release.

Upstream development: https://github.com/TeamNewPipe/NewPipe
Fork: https://github.com/mattparavel/paravel-video
