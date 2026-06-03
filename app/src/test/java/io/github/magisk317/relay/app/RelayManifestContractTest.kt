package io.github.magisk317.relay.app

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

class RelayManifestContractTest {

    @Test
    fun `legacy xposed manifest metadata is removed`() {
        val document = parseManifest("hook/entry/src/main/AndroidManifest.xml")
        val application = document.getElementsByTagName("application").item(0)
        val metaDataNames = buildSet {
            val children = application.childNodes
            for (index in 0 until children.length) {
                val node = children.item(index)
                if (node.nodeName == "meta-data") {
                    node.attributes.getNamedItemNS(ANDROID_NS, "name")?.nodeValue?.let(::add)
                }
            }
        }

        assertFalse("xposedmodule" in metaDataNames)
        assertFalse("xposedminversion" in metaDataNames)
        assertFalse("xposeddescription" in metaDataNames)
        assertFalse("xposedscope" in metaDataNames)
        assertFalse("xposedsharedprefs" in metaDataNames)
        assertFalse(projectFile("app/src/main/assets/xposed_init").exists())
        assertFalse(projectFile("hook/entry/src/main/assets/xposed_init").exists())
    }

    @Test
    fun `settings activity does not expose legacy xposed category`() {
        val document = parseManifest("app/src/main/AndroidManifest.xml")
        val categories = document.getElementsByTagName("category")
        val categoryNames = buildSet {
            for (index in 0 until categories.length) {
                categories.item(index)
                    .attributes
                    .getNamedItemNS(ANDROID_NS, "name")
                    ?.nodeValue
                    ?.let(::add)
            }
        }

        assertFalse("de.robv.android.xposed.category.MODULE_SETTINGS" in categoryNames)
    }

    @Test
    fun `launcher activity remains visible and owns static shortcuts`() {
        val document = parseManifest("app/src/main/AndroidManifest.xml")
        val launcherActivity = document.getElementsByTagName("activity")
            .asElements()
            .single {
                it.attributes.getNamedItemNS(ANDROID_NS, "name")?.nodeValue ==
                    "io.github.magisk317.relay.ui.home.LauncherActivity"
            }

        assertEquals("true", launcherActivity.attributes.getNamedItemNS(ANDROID_NS, "enabled")?.nodeValue)

        val metaData = launcherActivity.getElementsByTagName("meta-data")
            .asElements()
            .single {
                it.attributes.getNamedItemNS(ANDROID_NS, "name")?.nodeValue == "android.app.shortcuts"
            }
        assertEquals("@xml/shortcuts", metaData.attributes.getNamedItemNS(ANDROID_NS, "resource")?.nodeValue)

        val actions = launcherActivity.getElementsByTagName("action")
            .asElements()
            .mapNotNull { it.attributes.getNamedItemNS(ANDROID_NS, "name")?.nodeValue }
            .toSet()
        val categories = launcherActivity.getElementsByTagName("category")
            .asElements()
            .mapNotNull { it.attributes.getNamedItemNS(ANDROID_NS, "name")?.nodeValue }
            .toSet()

        assertTrue("android.intent.action.MAIN" in actions)
        assertTrue("android.intent.category.LAUNCHER" in categories)
    }

    @Test
    fun `static shortcuts route through launcher activity`() {
        val document = parseManifest("core/src/main/res/xml/shortcuts.xml")
        val targetClasses = document.getElementsByTagName("intent")
            .asElements()
            .mapNotNull { it.attributes.getNamedItemNS(ANDROID_NS, "targetClass")?.nodeValue }
            .toSet()

        assertEquals(setOf("io.github.magisk317.relay.ui.home.LauncherActivity"), targetClasses)
    }

    @Test
    fun `libxposed entrypoint and scope metadata remain declared`() {
        assertEquals(
            "io.github.magisk317.relay.xp.LibXposedEntry",
            resolveProjectFile("hook/entry/src/main/resources/META-INF/xposed/java_init.list").readText().trim(),
        )

        val moduleProps = resolveProjectFile("hook/entry/src/main/resources/META-INF/xposed/module.prop").readText()
        assertTrue("minApiVersion=101" in moduleProps)
        assertTrue("targetApiVersion=101" in moduleProps)
        assertTrue("staticScope=true" in moduleProps)

        val scope = resolveProjectFile("hook/entry/src/main/resources/META-INF/xposed/scope.list")
            .readLines()
            .filter { it.isNotBlank() }
            .toSet()
        assertTrue("android" in scope)
        assertTrue("system" in scope)
        assertTrue("com.android.phone" in scope)
        assertTrue("com.android.providers.telephony" in scope)
    }

    private fun parseManifest(relativePath: String) = DocumentBuilderFactory.newInstance()
        .apply { isNamespaceAware = true }
        .newDocumentBuilder()
        .parse(resolveProjectFile(relativePath))

    private fun resolveProjectFile(relativePath: String): File {
        return projectFile(relativePath).also {
            require(it.exists()) { "Cannot resolve file: $relativePath" }
        }
    }

    private fun projectFile(relativePath: String): File {
        val fromRoot = File(relativePath)
        if (fromRoot.exists()) return fromRoot
        return File("../$relativePath")
    }

    private fun org.w3c.dom.NodeList.asElements(): List<Element> {
        return List(length) { index -> item(index) }.filterIsInstance<Element>()
    }

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}
