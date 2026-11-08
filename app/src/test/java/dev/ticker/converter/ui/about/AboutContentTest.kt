package dev.ticker.converter.ui.about

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AboutContentTest {
    private val assets = File("src/main/assets")

    @Test fun everyLicenceTextIsBundled() {
        for (c in AboutContent.components) {
            val f = File(assets, c.licenceAsset)
            assertTrue("${c.name}: missing ${c.licenceAsset}", f.isFile && f.length() > 500)
        }
    }

    @Test fun bundledLicenceFilesAreAllListed() {
        val listed = AboutContent.components.map { it.licenceAsset }.toSet()
        val bundled = File(assets, "licenses").listFiles()!!.map { "licenses/${it.name}" }
        assertTrue("not listed: ${bundled - listed}", bundled.all { it in listed })
    }

    // The IJG licence requires this exact sentence in the documentation of binary builds.
    @Test fun ijgSentenceIsShown() =
        assertTrue(AboutContent.components.any { "based in part on the work of the Independent JPEG Group" in it.note })

    // ECB terms: cite the ECB, and say when figures are modified (cross rates, daily changes).
    @Test fun ecbIsCitedAndCalculationsAreLabelled() {
        val text = AboutContent.dataSources.joinToString(" ") { it.name + " " + it.detail }
        assertTrue("European Central Bank" in text)
        assertTrue("calculat" in text)
        assertTrue("fawazahmed0" in text && "CC0" in text)
    }

    @Test fun noWarrantyIsStated() = assertTrue("NO WARRANTY" in AboutContent.LEGAL.uppercase())

    @Test fun ownLicenceIsGpl() = assertTrue(AboutContent.components.first().licenceAsset == "licenses/GPL-3.0.txt")
}
