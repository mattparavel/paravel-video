/* SPDX-FileCopyrightText: 2026 Paravel contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.capture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.SurfaceHolder;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.player.PlayerType;
import org.schabi.newpipe.player.helper.PlayerHolder;
import org.schabi.newpipe.player.ui.MainPlayerUi;
import org.schabi.newpipe.util.NavigationHelper;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Opt-in network/device smoke test. Opens a public sample video. Requires system PiP. */
public class CaptureIntegrationTest {
    private static <T> T onMain(final Supplier<T> action) {
        final AtomicReference<T> result = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> result.set(action.get()));
        return result.get();
    }

    private static void await(final String message, final BooleanSupplier condition) {
        final long deadline = SystemClock.elapsedRealtime() + 45000;
        while (!condition.getAsBoolean() && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(100);
        }
        assertTrue(message, condition.getAsBoolean());
    }

    @Test
    public void visibleVideoKeepsItsDecoderAndSurfaceThroughPip() {
        final var instrumentation = InstrumentationRegistry.getInstrumentation();
        final Context context = instrumentation.getTargetContext();
        final Intent intent = NavigationHelper.getStreamIntent(context, 0,
                "https://www.youtube.com/watch?v=M7lc1UVf-VE", "Capture integration test");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        final MainActivity activity = (MainActivity) instrumentation.startActivitySync(intent);
        final Uri uri = Uri.parse("content://" + context.getPackageName() + ".positions");
        final Supplier<Bundle> capture = () -> context.getContentResolver()
                .call(uri, "capture_position", null, null);
        await("Video must expose a visible player", () -> capture.get() != null);
        final Bundle result = capture.get();
        assertNotNull(result);
        assertEquals(1, result.getInt("version"));
        assertEquals("M7lc1UVf-VE", result.getString("video_id"));
        assertTrue(result.getDouble("seconds") >= 0);
        assertTrue(Math.abs(System.currentTimeMillis() - result.getLong("captured_at")) < 1500);
        final int[] bounds = result.getIntArray("video_bounds");
        assertNotNull(bounds);
        assertEquals(4, bounds.length);
        assertTrue(bounds[2] > bounds[0] && bounds[3] > bounds[1]);
        await("Playback must settle before testing the transition", () -> {
            final Bundle next = capture.get();
            return next != null && next.getDouble("seconds") > result.getDouble("seconds") + 2;
        });

        final var player = onMain(() -> PlayerHolder.getInstance().getPlayer().orElseThrow());
        final var decoder = onMain(player::getExoPlayer);
        final var ui = onMain(() -> player.UIs().get(MainPlayerUi.class).orElseThrow());
        final var holder = ui.getBinding().surfaceView.getHolder();
        final AtomicInteger surfaceLosses = new AtomicInteger();
        final SurfaceHolder.Callback callback = new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(final SurfaceHolder value) { }
            @Override
            public void surfaceChanged(final SurfaceHolder value, final int format,
                                       final int width, final int height) { }
            @Override
            public void surfaceDestroyed(final SurfaceHolder value) {
                surfaceLosses.incrementAndGet();
            }
        };
        instrumentation.runOnMainSync(() -> holder.addCallback(callback));
        for (int cycle = 0; cycle < 2; cycle++) {
            context.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            await("Home must enter system PiP", () -> onMain(activity::isInPictureInPictureMode));
            await("PiP must settle into floating bounds", () -> {
                final Bundle next = capture.get();
                return next != null && next.getIntArray("video_bounds")[2]
                        - next.getIntArray("video_bounds")[0] < bounds[2] - bounds[0];
            });
            final Bundle pip = capture.get();
            assertNotNull(pip);
            assertTrue(pip.getIntArray("video_bounds")[2] - pip.getIntArray("video_bounds")[0]
                    < bounds[2] - bounds[0]);
            await("Capture coordinates must lie inside the system PiP window", () -> onMain(() -> {
                final Bundle next = capture.get();
                if (next == null) {
                    return false;
                }
                final int[] rect = next.getIntArray("video_bounds");
                return activity.getWindowManager().getCurrentWindowMetrics().getBounds()
                        .contains(rect[0], rect[1], rect[2], rect[3]);
            }));
            final double pipTime = pip.getDouble("seconds");
            await("PiP playback must continue", () -> {
                final Bundle next = capture.get();
                return next != null && next.getDouble("seconds") > pipTime + 1;
            });
            assertEquals(PlayerType.MAIN, onMain(player::getPlayerType));
            assertSame(decoder, onMain(player::getExoPlayer));
            assertEquals("PiP must not detach the video surface", 0, surfaceLosses.get());
            context.startActivity(new Intent(context, MainActivity.class)
                    .setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            await("Returning must expand the same activity", () -> onMain(() ->
                    !activity.isInPictureInPictureMode() && activity.hasWindowFocus()));
            await("Main playback must resume capture", () -> capture.get() != null);
            assertSame(decoder, onMain(player::getExoPlayer));
            assertEquals("Expanding PiP must keep the surface", 0, surfaceLosses.get());
        }
        instrumentation.runOnMainSync(() -> holder.removeCallback(callback));
        instrumentation.runOnMainSync(player::pause);
        context.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        SystemClock.sleep(1500);
        assertFalse("Paused playback must not auto-enter PiP",
                onMain(activity::isInPictureInPictureMode));
        assertEquals(PlayerType.MAIN, onMain(player::getPlayerType));
        context.startActivity(new Intent(context, MainActivity.class)
                .setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        await("Return from paused background", () -> onMain(activity::hasWindowFocus));
    }
}
