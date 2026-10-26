package org.nikoresu.transcare

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.nikoresu.transcare.data.ProfileJson
import org.nikoresu.transcare.domain.model.Profile

class ProfileTest {

    @Test
    fun initialsUseFirstAndLastName() {
        assertEquals("AL", Profile(name = "Ada Lovelace").initials)
    }

    @Test
    fun initialsUseSingleName() {
        assertEquals("A", Profile(name = "Ada").initials)
    }

    @Test
    fun initialsFallbackForBlankName() {
        assertEquals("?", Profile(name = "   ").initials)
    }

    @Test
    fun normalizedTrimsNameAndEmail() {
        val profile = Profile(name = "  Ada  ", email = "  ada@example.com ").normalized()
        assertEquals("Ada", profile.name)
        assertEquals("ada@example.com", profile.email)
    }

    @Test
    fun unsafePhotoFileNameIsRejected() {
        assertNull(
            Profile(photoFileName = "../secret.jpg").normalized().photoFileName,
        )
        assertNull(
            Profile(photoFileName = "profile/photo.jpg").normalized().photoFileName,
        )
    }

    @Test
    fun safePhotoFileNameIsKept() {
        assertEquals(
            "profile_1_abcd.jpg",
            Profile(photoFileName = "profile_1_abcd.jpg").normalized().photoFileName,
        )
    }

    @Test
    fun jsonRoundTrip() {
        val profile = Profile(
            name = "Ada Lovelace",
            email = "ada@example.com",
            photoFileName = "profile_1_abcd.jpg",
        )
        assertEquals(profile, ProfileJson.decode(ProfileJson.encode(profile)))
    }

    @Test
    fun jsonWithoutFieldsUsesDefaults() {
        assertEquals(Profile(), ProfileJson.decode("{}"))
    }

    @Test
    fun jsonDropsUnsafePhotoFileName() {
        val profile = ProfileJson.decode(
            """{"name":"Ada","email":"ada@example.com","photoFileName":"../secret.jpg"}""",
        )
        assertEquals("Ada", profile.name)
        assertEquals("ada@example.com", profile.email)
        assertNull(profile.photoFileName)
    }

    @Test
    fun jsonTrimsWhitespace() {
        val profile = ProfileJson.decode(
            """{"name":"  Ada  ","email":"  ada@example.com  "}""",
        )
        assertEquals("Ada", profile.name)
        assertEquals("ada@example.com", profile.email)
    }
}
