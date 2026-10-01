package io.github.magisk317.relay.engine.model

interface SmsCodeRuleData {
    val id: Long
    val company: String?
    val codeKeyword: String
    val codeRegex: String
}
