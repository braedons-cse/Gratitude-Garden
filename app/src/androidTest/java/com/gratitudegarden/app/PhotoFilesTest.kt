package com.gratitudegarden.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gratitudegarden.app.data.PhotoPreparer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Leftover staged and captured photos are swept on launch, but not a capture still coming back. */
@RunWith(AndroidJUnit4::class)
class PhotoFilesTest {

    @Test
    fun onlyFilesOverAnHourOldAreSwept() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val now = System.currentTimeMillis()
        val oldCapture = PhotoPreparer.newCaptureFile(context).apply { writeText("x"); setLastModified(now - 2 * 3_600_000) }
        val freshCapture = PhotoPreparer.newCaptureFile(context).apply { writeText("x") }
        val oldStaged = File(PhotoPreparer.stagingDir(context), "old.jpg").apply { writeText("x"); setLastModified(now - 2 * 3_600_000) }

        PhotoPreparer.sweepStale(context, now)

        assertFalse(oldCapture.exists())
        assertFalse(oldStaged.exists())
        assertTrue("a camera trip this launch may still come back for it", freshCapture.exists())
        freshCapture.delete()
    }
}
