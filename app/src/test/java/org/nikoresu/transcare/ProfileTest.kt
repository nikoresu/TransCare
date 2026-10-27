package org.nikoresu.transcare

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.nikoresu.transcare.data.ProfileJson
import org.nikoresu.transcare.domain.model.MedicalProfile
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
            age = 36,
            medicalProfile = MedicalProfile.FEMINIZING_HRT,
        )
        assertEquals(profile, ProfileJson.decode(ProfileJson.encode(profile)))
    }

    @Test
    fun normalizedKeepsValidAge() {
        assertEquals(27, Profile(age = 27).normalized().age)
        assertEquals(Profile.MIN_AGE, Profile(age = Profile.MIN_AGE).normalized().age)
        assertEquals(Profile.MAX_AGE, Profile(age = Profile.MAX_AGE).normalized().age)
    }

    @Test
    fun normalizedDropsAgeOutsideRange() {
        assertNull(Profile(age = Profile.MIN_AGE - 1).normalized().age)
        assertNull(Profile(age = Profile.MAX_AGE + 1).normalized().age)
    }

    @Test
    fun normalizedKeepsAvailableMedicalProfile() {
        assertEquals(
            MedicalProfile.FEMINIZING_HRT,
            Profile(medicalProfile = MedicalProfile.FEMINIZING_HRT).normalized().medicalProfile,
        )
    }

    @Test
    fun normalizedDropsUnavailableMedicalProfile() {
        assertNull(
            Profile(medicalProfile = MedicalProfile.MASCULINIZING_HRT)
                .normalized()
                .medicalProfile,
        )
    }

    @Test
    fun jsonDropsInvalidAge() {
        assertNull(ProfileJson.decode("""{"age":200}""").age)
        assertNull(ProfileJson.decode("""{"age":12}""").age)
        assertNull(ProfileJson.decode("""{"age":"unknown"}""").age)
    }

    @Test
    fun jsonIgnoresUnknownMedicalProfile() {
        assertNull(ProfileJson.decode("""{"medicalProfile":"UNKNOWN"}""").medicalProfile)
        assertNull(
            ProfileJson.decode("""{"medicalProfile":"MASCULINIZING_HRT"}""").medicalProfile,
        )
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
