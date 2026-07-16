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
        val document = parseManifest("modules/hook/entry/src/main/AndroidManifest.xml")
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
        assertFalse(projectFile("modules/hook/entry/src/main/assets/xposed_init").exists())
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
    fun `system backup excludes sensitive preferences and message databases`() {
        val manifest = parseManifest("app/src/main/AndroidManifest.xml")
        val application = manifest.getElementsByTagName("application").item(0)
        assertEquals(
            "@xml/backup_rules",
            application.attributes.getNamedItemNS(ANDROID_NS, "fullBackupContent")?.nodeValue,
        )
        assertEquals(
            "@xml/data_extraction_rules",
            application.attributes.getNamedItemNS(ANDROID_NS, "dataExtractionRules")?.nodeValue,
        )

        val requiredExcludes = requiredSensitiveBackupExcludes()
        val legacyRules = parseManifest("app/src/main/res/xml/backup_rules.xml")
        assertTrue(excludesUnder(legacyRules.documentElement).containsAll(requiredExcludes))

        val extractionRules = parseManifest("app/src/main/res/xml/data_extraction_rules.xml")
        listOf("cloud-backup", "device-transfer").forEach { sectionName ->
            val section = extractionRules.getElementsByTagName(sectionName).item(0) as Element
            assertTrue(excludesUnder(section).containsAll(requiredExcludes))
        }
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
        val document = parseManifest("modules/core/src/main/res/xml/shortcuts.xml")
        val targetClasses = document.getElementsByTagName("intent")
            .asElements()
            .mapNotNull { it.attributes.getNamedItemNS(ANDROID_NS, "targetClass")?.nodeValue }
            .toSet()

        assertEquals(setOf("io.github.magisk317.relay.ui.home.LauncherActivity"), targetClasses)
    }

    @Test
    fun `base manifest declares standard mode receive permissions only`() {
        val permissions = permissionNames("app/src/main/AndroidManifest.xml")
        val features = featureNames("app/src/main/AndroidManifest.xml")

        assertFalse("android.permission.SEND_SMS" in permissions)
        assertTrue("android.permission.RECEIVE_SMS" in permissions)
        assertTrue("android.permission.RECEIVE_MMS" in permissions)
        assertTrue("android.permission.READ_PHONE_STATE" in permissions)
        assertTrue("android.permission.READ_CALL_LOG" in permissions)
        assertTrue("android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" in permissions)
        assertTrue("android.hardware.telephony" in features)
    }

    @Test
    fun `play manifest removes standard mode telephony permissions and receivers`() {
        val permissions = permissionNames("app/src/play/AndroidManifest.xml")
        val removedPermissions = removedPermissionNames("app/src/play/AndroidManifest.xml")
        val removedReceivers = removedReceiverNames("app/src/play/AndroidManifest.xml")
        val features = featureNames("app/src/play/AndroidManifest.xml")

        assertFalse("android.permission.SEND_SMS" in permissions)
        assertTrue("android.permission.RECEIVE_SMS" in removedPermissions)
        assertTrue("android.permission.RECEIVE_MMS" in removedPermissions)
        assertTrue("android.permission.READ_SMS" in removedPermissions)
        assertTrue("android.permission.READ_PHONE_STATE" in removedPermissions)
        assertTrue("android.permission.READ_CALL_LOG" in removedPermissions)
        assertTrue("android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" in removedPermissions)
        assertTrue("io.github.magisk317.relay.receiver.StandardSmsReceiver" in removedReceivers)
        assertTrue("io.github.magisk317.relay.receiver.StandardMmsReceiver" in removedReceivers)
        assertFalse("android.hardware.telephony" in features)
    }

    @Test
    fun `non play manifests keep outgoing sms permissions`() {
        listOf("app/src/github/AndroidManifest.xml", "app/src/fdroid/AndroidManifest.xml").forEach { manifest ->
            val permissions = permissionNames(manifest)
            val features = featureNames(manifest)

            assertTrue("android.permission.SEND_SMS" in permissions)
            assertTrue("android.permission.READ_PHONE_STATE" in permissions)
            assertTrue("android.hardware.telephony" in features)
        }
    }

    @Test
    fun `standard mode sms and mms receivers are declared`() {
        val document = parseManifest("app/src/main/AndroidManifest.xml")
        val receivers = document.getElementsByTagName("receiver")
            .asElements()
            .associateBy { it.attributes.getNamedItemNS(ANDROID_NS, "name")?.nodeValue.orEmpty() }

        val smsReceiver = receivers.getValue("io.github.magisk317.relay.receiver.StandardSmsReceiver")
        assertEquals("android.permission.BROADCAST_SMS", smsReceiver.attributes.getNamedItemNS(ANDROID_NS, "permission")?.nodeValue)
        assertReceiverAction(smsReceiver, "android.provider.Telephony.SMS_RECEIVED")

        val mmsReceiver = receivers.getValue("io.github.magisk317.relay.receiver.StandardMmsReceiver")
        assertEquals("android.permission.BROADCAST_WAP_PUSH", mmsReceiver.attributes.getNamedItemNS(ANDROID_NS, "permission")?.nodeValue)
        assertReceiverAction(mmsReceiver, "android.provider.Telephony.WAP_PUSH_RECEIVED")
        assertReceiverAction(mmsReceiver, "android.provider.Telephony.WAP_PUSH_DELIVER")
        val mimeTypes = mmsReceiver.getElementsByTagName("data")
            .asElements()
            .mapNotNull { it.attributes.getNamedItemNS(ANDROID_NS, "mimeType")?.nodeValue }
            .toSet()
        assertTrue("application/vnd.wap.mms-message" in mimeTypes)
    }

    @Test
    fun `libxposed entrypoint and scope metadata remain declared`() {
        assertEquals(
            "io.github.magisk317.relay.xp.LibXposedEntry",
            resolveProjectFile("modules/hook/entry/src/main/resources/META-INF/xposed/java_init.list").readText().trim(),
        )

        val moduleProps = resolveProjectFile("modules/hook/entry/src/main/resources/META-INF/xposed/module.prop").readText()
        assertTrue("minApiVersion=102" in moduleProps)
        assertTrue("targetApiVersion=102" in moduleProps)
        assertTrue("staticScope=true" in moduleProps)
        assertTrue("autoHotReload=true" in moduleProps)

        val scope = resolveProjectFile("modules/hook/entry/src/main/resources/META-INF/xposed/scope.list")
            .readLines()
            .filter { it.isNotBlank() }
            .toSet()
        assertTrue("android" in scope)
        assertTrue("system" in scope)
        assertTrue("com.android.phone" in scope)
        assertTrue("com.xiaomi.phone" in scope)
        assertTrue("com.android.providers.telephony" in scope)
        assertTrue("com.android.mms" in scope)
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

    private fun permissionNames(relativePath: String): Set<String> {
        return parseManifest(relativePath)
            .getElementsByTagName("uses-permission")
            .asElements()
            .mapNotNull { it.attributes.getNamedItemNS(ANDROID_NS, "name")?.nodeValue }
            .toSet()
    }

    private fun removedPermissionNames(relativePath: String): Set<String> {
        return parseManifest(relativePath)
            .getElementsByTagName("uses-permission")
            .asElements()
            .filter { it.attributes.getNamedItemNS(TOOLS_NS, "node")?.nodeValue == "remove" }
            .mapNotNull { it.attributes.getNamedItemNS(ANDROID_NS, "name")?.nodeValue }
            .toSet()
    }

    private fun removedReceiverNames(relativePath: String): Set<String> {
        return parseManifest(relativePath)
            .getElementsByTagName("receiver")
            .asElements()
            .filter { it.attributes.getNamedItemNS(TOOLS_NS, "node")?.nodeValue == "remove" }
            .mapNotNull { it.attributes.getNamedItemNS(ANDROID_NS, "name")?.nodeValue }
            .toSet()
    }

    private fun featureNames(relativePath: String): Set<String> {
        return parseManifest(relativePath)
            .getElementsByTagName("uses-feature")
            .asElements()
            .mapNotNull { it.attributes.getNamedItemNS(ANDROID_NS, "name")?.nodeValue }
            .toSet()
    }

    private fun assertReceiverAction(receiver: Element, expectedAction: String) {
        val actions = receiver.getElementsByTagName("action")
            .asElements()
            .mapNotNull { it.attributes.getNamedItemNS(ANDROID_NS, "name")?.nodeValue }
            .toSet()
        assertTrue(expectedAction in actions)
    }

    private fun requiredSensitiveBackupExcludes(): Set<Pair<String, String>> = buildSet {
        add("root" to "datastore/")
        listOf("relay_room.db", "xrelay_room.db", "xsmscode_room.db").forEach { databaseName ->
            listOf("", "-shm", "-wal", "-journal").forEach { suffix ->
                add("database" to "$databaseName$suffix")
            }
        }
        add("sharedpref" to "internal_secret_prefs.xml")
        add("sharedpref" to "xposed_prefs.xml")
        add("sharedpref" to "webdav_config_prefs.xml")
        add("sharedpref" to "webdav_crypto_prefs.xml")
        add("sharedpref" to "google_drive_backup_config_prefs.xml")
        add("sharedpref" to "cloud_backup_settings_prefs.xml")
    }

    private fun excludesUnder(element: Element): Set<Pair<String, String>> =
        element.getElementsByTagName("exclude")
            .asElements()
            .map { exclude -> exclude.getAttribute("domain") to exclude.getAttribute("path") }
            .toSet()

    private fun org.w3c.dom.NodeList.asElements(): List<Element> {
        return List(length) { index -> item(index) }.filterIsInstance<Element>()
    }

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
        const val TOOLS_NS = "http://schemas.android.com/tools"
    }
}
