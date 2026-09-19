# Paravel Video — experimental tablet fork

Based on NewPipe v0.29.1, commit 00acf2f318b0af7ffacbd687bba011b642cc01d0.
Modified September 18, 2026 by Paravel contributors; GPL-3.0-or-later.

## Changes

- Separate Paravel Video name, icon and application ID (`com.paravel.video.debug`
  for the prototype, `com.paravel.video` for release).
- Leaving the playing app uses Android's native picture-in-picture window on
  supported devices. Android 12+ auto-entry, source-rectangle hints and seamless
  resizing animate the existing activity and SurfaceView into the floating window.
  Expanding it restores the previous fullscreen state without recreating playback.
  Paused playback does not auto-enter PiP; dismissing PiP pauses playback without
  spawning a second popup. The minimize-on-exit preference remains configurable.
  NewPipe's legacy popup remains available explicitly and as a fallback; that
  legacy overlay requires Android's display-over-other-apps permission.
- An explicit screengrab request can read the visible video's ID, playback time
  and on-screen bounds through a signature-protected Android provider.
- tldraw pen consumes that provider, saves a timestamped backlink on the cropped
  image, and opens it at the saved time in this app's native PiP window. The same
  tldraw canvas stays visible and interactive underneath the system player.
  Crops that miss the popup do not receive a video backlink.

No playback history is exposed through the capture provider. It returns nothing
for background audio, off-screen main playback, buffering or an
unavailable player. Capture requests never start playback or the player service. Requests run
on the player thread and time out after 700 ms. Calls require the signature
permission `com.paravel.video.CAPTURE_POSITION`; both apps must be signed with the
same appropriate key. Do not change this permission to normal for OS deployment.

## Capture contract

Authority: `${applicationId}.positions`. Method: `capture_position`.
Version-1 Bundle: `version` (int), `video_id` (string), `seconds` (double),
`captured_at` (Unix milliseconds), `is_live` (boolean, absent means false),
`video_bounds` (int array: left, top, right,
bottom in screen pixels). No rows or other provider operations are exposed.
Clients must compare fresh samples around capture and validate the crop bounds.
For `is_live=true`, clients must omit the URL timestamp: the player's position
belongs to a moving live window, not a permanent offset from the video's start.
These captures still provide the stream's YouTube button.

The same provider accepts `presentation`, returning only a `native_pip` boolean.
The canvas uses it to choose an activity window below system PiP when reopening
the drawing overlay. This call has the same signature permission as capture.

## Timestamped backlink entry

The explicit activity alias `org.schabi.newpipe.capture.FloatingVideo` accepts
action `com.paravel.video.PLAY_IN_PIP`, `video_id` (11-character YouTube ID) and
`start_seconds` (long; -1 or absent opens the default position/live edge). It
requires `com.paravel.video.CAPTURE_POSITION` and is the
only accepted component for this action. Reopening the current video seeks the
existing player; another video loads a single-item queue at the requested time.
When PiP is already open, the signature-protected `play_backlink` provider method
accepts those same extras and returns `accepted=true` after handing them to the
existing activity. If no PiP activity exists, it returns false and the client uses
the activity alias. This avoids a task transition when jumping between backlinks.
Android 13+ clients use `ActivityOptions.makeLaunchIntoPip`; older supported
systems enter PiP once playback is ready. No external-player preference or saved
playback position should override an explicit backlink's requested time.

## Build and distribution

Use JDK 21 and the Android SDK; upstream requires compile SDK 37 and downloads
Gradle through its checked wrapper. Build with `./gradlew :app:assembleDebug`.
NewPipe's code-style checks are part of the build. The matching tldraw pen build
is maintained separately; its source does not incorporate NewPipe player code.

An opt-in live device test, `CaptureIntegrationTest`, opens a public sample video
on a device with system PiP, validates timestamp and screen coordinates, then
repeats Home/expand transitions. It checks continued playback, decoder identity,
zero SurfaceView destruction, and that paused playback does not auto-enter PiP.
Build it with `./gradlew :app:assembleDebugAndroidTest` and run its class with
AndroidJUnitRunner. The test targets Android 12+; older fallback behavior is not
covered by this device test.

PiP transition reference: https://developer.android.com/develop/ui/views/picture-in-picture

The app retains upstream's license/about screens. Its upstream updater already
rejects non-upstream signatures. This fork's APKs are distributed separately.
Keep the corresponding source and build instructions available with every APK.
This prototype does not complete the signing, update, attribution/trademark and
installation-information review needed for a commercial OS release.

Upstream development: https://github.com/TeamNewPipe/NewPipe
Fork: https://github.com/mattparavel/paravel-video
