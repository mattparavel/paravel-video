/* SPDX-FileCopyrightText: 2026 Paravel contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.capture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.schabi.newpipe.util.NavigationHelper;

/** Opt-in network/device smoke test. Opens a public sample video. */
public class CaptureIntegrationTest {
    @Test
    public void visibleVideoProvidesFreshPositionAndBounds() {
        final var instrumentation = InstrumentationRegistry.getInstrumentation();
        final Context context = instrumentation.getTargetContext();
        final Intent intent = NavigationHelper.getStreamIntent(context, 0,
                "https://www.youtube.com/watch?v=M7lc1UVf-VE", "Capture integration test");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        instrumentation.startActivitySync(intent);
        final Uri uri = Uri.parse("content://" + context.getPackageName() + ".positions");
        final long deadline = SystemClock.elapsedRealtime() + 45000;
        Bundle result = null;
        while (result == null && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(500);
            result = context.getContentResolver().call(uri, "capture_position", null, null);
        }
        assertNotNull("Video must load and expose a visible player", result);
        assertEquals(1, result.getInt("version"));
        assertEquals("M7lc1UVf-VE", result.getString("video_id"));
        assertTrue(result.getDouble("seconds") >= 0);
        assertTrue(Math.abs(System.currentTimeMillis() - result.getLong("captured_at")) < 1500);
        final int[] bounds = result.getIntArray("video_bounds");
        assertNotNull(bounds);
        assertEquals(4, bounds.length);
        assertTrue(bounds[2] > bounds[0] && bounds[3] > bounds[1]);

        // Leaving the activity must keep the video visible in the configured popup.
        context.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        final long popupDeadline = SystemClock.elapsedRealtime() + 10000;
        Bundle popup = null;
        while (popup == null && SystemClock.elapsedRealtime() < popupDeadline) {
            SystemClock.sleep(500);
            final Bundle next = context.getContentResolver().call(uri,
                    "capture_position", null, null);
            if (next != null && next.getIntArray("video_bounds")[2]
                    - next.getIntArray("video_bounds")[0] < bounds[2] - bounds[0]) {
                popup = next;
            }
        }
        assertNotNull("Home must create a visible floating player", popup);
        final double popupTime = popup.getDouble("seconds");
        SystemClock.sleep(2000);
        final Bundle later = context.getContentResolver().call(uri,
                "capture_position", null, null);
        assertNotNull(later);
        assertTrue("Popup playback must continue", later.getDouble("seconds") > popupTime + 0.5);

    }
}
