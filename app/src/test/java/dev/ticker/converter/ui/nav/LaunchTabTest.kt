package dev.ticker.converter.ui.nav

import dev.ticker.converter.ui.components.Tab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class LaunchTabTest {
    @Test fun knownRoutesMap() {
        assertEquals(Tab.CAMERA, Tab.fromLaunchExtra("camera"))
        assertEquals(Tab.CHARTS, Tab.fromLaunchExtra("charts"))
        assertEquals(Tab.CONVERT, Tab.fromLaunchExtra("convert"))
    }

    @Test fun unknownFallsBackToConvert() {
        assertEquals(Tab.CONVERT, Tab.fromLaunchExtra(null))
        assertEquals(Tab.CONVERT, Tab.fromLaunchExtra("bogus"))
        assertEquals(Tab.CONVERT, Tab.fromLaunchExtra("CAMERA"))
    }

    @Test fun shortcutsXmlTabsAreReal() {
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(File("src/main/res/xml/shortcuts.xml"))
        val ns = "http://schemas.android.com/apk/res/android"
        val shortcuts = doc.getElementsByTagName("shortcut")
        val ids = (0 until shortcuts.length).map { (shortcuts.item(it) as org.w3c.dom.Element).getAttributeNS(ns, "shortcutId") }
        assertEquals(listOf("scan", "charts"), ids)
        val extras = doc.getElementsByTagName("extra")
        assertEquals(2, extras.length)
        for (i in 0 until extras.length) {
            val e = extras.item(i) as org.w3c.dom.Element
            assertEquals("dev.ticker.converter.TAB", e.getAttributeNS(ns, "name"))
            val v = e.getAttributeNS(ns, "value")
            assertTrue("bad tab $v", Tab.entries.any { it.route == v } && v != "convert")
        }
    }
}
