package eu.hughkennedy.pinvault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LocaleStringsTest {

    private fun parseStringsXml(file: File): Map<String, String> {
        val pattern = Regex("""<string\s+name="([^"]+)">([\s\S]*?)</string>""")
        val text = file.readText()
        val result = mutableMapOf<String, String>()
        for (match in pattern.findAll(text)) {
            val name = match.groupValues[1]
            val value = match.groupValues[2]
            result[name] = value
        }
        return result
    }

    @Test
    fun testBritishEnglishStringParityAndUkSpellings() {
        val baseDir = File("src/main/res")
        val defaultStringsFile = File(baseDir, "values/strings.xml")
        val ukStringsFile = File(baseDir, "values-en-rGB/strings.xml")
        val localesConfigFile = File(baseDir, "xml/locales_config.xml")

        assertTrue("Default strings.xml must exist", defaultStringsFile.exists())
        assertTrue("values-en-rGB/strings.xml must exist", ukStringsFile.exists())
        assertTrue("locales_config.xml must exist", localesConfigFile.exists())

        val defaultStrings = parseStringsXml(defaultStringsFile)
        val ukStrings = parseStringsXml(ukStringsFile)

        // 1. Verify 100% key parity (no missing strings)
        for (key in defaultStrings.keys) {
            assertTrue("UK strings must contain key: $key", ukStrings.containsKey(key))
        }
        assertEquals("UK strings count must match default strings count", defaultStrings.size, ukStrings.size)

        // 2. Verify UK alternative spellings
        // Colour vs Color
        val secretColorTitle = ukStrings["editor_secret_color_title"]
        assertNotNull(secretColorTitle)
        assertEquals("Secret Token Colour", secretColorTitle)

        val secretColorSubtitle = ukStrings["editor_secret_color_subtitle"]
        assertNotNull(secretColorSubtitle)
        assertTrue(secretColorSubtitle!!.contains("secret colour"))
        assertFalse(secretColorSubtitle.contains("secret color"))

        val helpKey1Title = ukStrings["help_key1_title"]
        assertEquals("Secret Colour", helpKey1Title)

        val helpKey1Desc = ukStrings["help_key1_desc"]
        assertNotNull(helpKey1Desc)
        assertTrue(helpKey1Desc!!.contains("secret colour"))
        assertFalse(helpKey1Desc.contains("secret color"))

        val helpDecoyP3 = ukStrings["help_decoy_p3"]
        assertNotNull(helpDecoyP3)
        assertTrue(helpDecoyP3!!.contains("Colour Concealment"))
        assertTrue(helpDecoyP3.contains("true PIN colour"))

        // Randomise vs Randomize
        val randomizeAction = ukStrings["action_randomize_decoys"]
        assertNotNull(randomizeAction)
        assertTrue(randomizeAction!!.contains("Randomise"))
        assertFalse(randomizeAction.contains("Randomize"))

        // Colours vs Colors
        val unassignedNote = ukStrings["editor_unassigned_tiles_note"]
        assertNotNull(unassignedNote)
        assertTrue(unassignedNote!!.contains("non-adjacent colours"))
        assertFalse(unassignedNote.contains("non-adjacent colors"))

        val helpHeroDesc = ukStrings["help_hero_desc"]
        assertNotNull(helpHeroDesc)
        assertTrue(helpHeroDesc!!.contains("randomised"))
        assertTrue(helpHeroDesc.contains("coloured"))
        assertFalse(helpHeroDesc.contains("randomized"))
        assertFalse(helpHeroDesc.contains("colored"))

        // Centre vs Center
        val helpKey2Desc = ukStrings["help_key2_desc"]
        assertNotNull(helpKey2Desc)
        assertTrue(helpKey2Desc!!.contains("centre tile"))
        assertFalse(helpKey2Desc.contains("center tile"))

        // Organise vs Organize
        val helpSection4Subtitle = ukStrings["help_section4_subtitle"]
        assertNotNull(helpSection4Subtitle)
        assertEquals("Organise matrices by type or custom group", helpSection4Subtitle)

        // Licence vs License (noun)
        val aboutOpenSourceDesc = ukStrings["about_opensource_desc"]
        assertNotNull(aboutOpenSourceDesc)
        assertTrue(aboutOpenSourceDesc!!.contains("General Public Licence"))
        assertFalse(aboutOpenSourceDesc.contains("General Public License"))

        val aboutLicenseFooter = ukStrings["about_license_footer"]
        assertNotNull(aboutLicenseFooter)
        assertTrue(aboutLicenseFooter!!.contains("General Public Licence"))
        assertFalse(aboutLicenseFooter.contains("General Public License"))

        // 3. Verify locales_config.xml contains en-GB
        val localesConfigText = localesConfigFile.readText()
        assertTrue("locales_config.xml must declare en-GB", localesConfigText.contains("""<locale android:name="en-GB"/>"""))
    }
}
