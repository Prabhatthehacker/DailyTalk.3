package com.dailytalk.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-logic tests only (no Android classes), so they run on the plain JVM. */
class MediaTest {
    @Test fun acceptsSupportedMediaUpToTheLimit() {
        assertNull(Media.validate("image/png", 1000, 200_000_000))
        assertNull(Media.validate("video/mp4", 200_000_000, 200_000_000))
    }
    @Test fun rejectsEmptyOversizedAndUnsupported() {
        assertEquals("This file is empty.", Media.validate("video/mp4", 0, 200_000_000))
        assertNotNull(Media.validate("video/mp4", 200_000_001, 200_000_000))
        assertNotNull(Media.validate("application/pdf", 10, 200_000_000))
        assertNotNull(Media.validate(null, 10, 200_000_000))
    }
    @Test fun onlyTrustsHostedLinks() {
        assertTrue(Media.hostedUrl.matches("https://files.catbox.moe/abc123.png"))
        assertFalse(Media.hostedUrl.matches("https://evil.example/a.png"))
        assertFalse(Media.hostedUrl.matches("http://files.catbox.moe/a.png"))
        assertFalse(Media.hostedUrl.matches("https://files.catbox.moe/../x.png"))
    }
    @Test fun keepsExtensionsOnUploadedNames() {
        assertEquals("my_photo_.jpg", Media.safeName("my photo?.jpg"))
        assertEquals("noext.bin", Media.safeName("noext"))
    }
}
