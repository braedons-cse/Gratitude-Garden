package com.gratitudegarden.app

import com.gratitudegarden.app.data.PHOTO_MAX_EDGE
import com.gratitudegarden.app.data.isPhotoPath
import com.gratitudegarden.app.data.photoPathFor
import com.gratitudegarden.app.data.sampleSizeFor
import com.gratitudegarden.app.data.targetSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * How an entry photo is sized and named. The name has to pass the server's check on
 * `gratitude_entries.photo_path` (migration 20260929120000), or set_entry_photo refuses it.
 */
class EntryPhotosTest {

    @Test
    fun theLongerEdgeComesDownToTheLimitAndTheShapeIsKept() {
        assertEquals(1600 to 1200, targetSize(4000, 3000))
        assertEquals(1200 to 1600, targetSize(3000, 4000))
        assertEquals(1600 to 900, targetSize(4032, 2268))
    }

    @Test
    fun aSmallPhotoIsNeverScaledUp() {
        assertEquals(800 to 600, targetSize(800, 600))
        assertEquals(PHOTO_MAX_EDGE to 10, targetSize(PHOTO_MAX_EDGE, 10))
    }

    @Test
    fun aVeryThinImageKeepsAtLeastOnePixel() {
        assertEquals(1600 to 1, targetSize(100_000, 2))
    }

    @Test
    fun subsamplingStopsBeforeTheLimit() {
        assertEquals(1, sampleSizeFor(3000))
        assertEquals(2, sampleSizeFor(3200))
        assertEquals(4, sampleSizeFor(8160)) // a 50 MP phone photo decodes at 2040 px
        assertEquals(1, sampleSizeFor(200))
    }

    @Test
    fun theNameFitsTheServersCheck() {
        val serverCheck = Regex("""^[0-9a-f-]{36}/[0-9a-f-]{36}/[0-9a-f-]{36}\.jpg$""")
        val path = photoPathFor(UUID.randomUUID().toString(), UUID.randomUUID().toString())
        assertTrue(path, serverCheck.matches(path))
        assertTrue(isPhotoPath(path))
    }

    @Test
    fun eachPhotoGetsANameOfItsOwn() {
        assertTrue(photoPathFor("u", "e") != photoPathFor("u", "e"))
    }

    @Test
    fun aPathThatCouldLeaveThePhotoFolderIsRefused() {
        assertFalse(isPhotoPath("../u/e/p.jpg"))
        assertFalse(isPhotoPath("u/../../p.jpg"))
        assertFalse(isPhotoPath("/u/e/p.jpg"))
        assertFalse(isPhotoPath("u/e/p.png"))
        assertFalse(isPhotoPath("u/e/f/p.jpg"))
    }
}
