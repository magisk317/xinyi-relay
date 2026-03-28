package io.github.magisk317.relay.xpbridge.bridge

data class XpCapabilities(
    val frameworkName: String,
    val frameworkVersion: String,
    val frameworkApiVersion: Int? = null,
    val frameworkPrivilege: Int? = null,
    val frameworkProperties: Long? = null,
    val supportsRemotePrefs: Boolean = false,
    val supportsRemoteFile: Boolean = false,
    val supportsDeopt: Boolean = false,
) {
    fun hasFrameworkProperty(flag: Long): Boolean = frameworkProperties?.let { props ->
        props and flag != 0L
    } ?: false

    companion object {
        const val PROP_CAP_SYSTEM: Long = 1L
        const val PROP_CAP_REMOTE: Long = 1L shl 1
        const val PROP_RT_API_PROTECTION: Long = 1L shl 2
    }
}
