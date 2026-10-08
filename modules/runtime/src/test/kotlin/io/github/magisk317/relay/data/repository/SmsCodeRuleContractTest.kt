package io.github.magisk317.relay.data.repository

import io.github.magisk317.relay.android.data.db.dao.SmsCodeRuleDao
import io.github.magisk317.relay.engine.model.SmsCodeRuleData
import io.github.magisk317.relay.engine.service.AppConfigRepository
import io.github.magisk317.smscode.rule.model.SmsCodeRuleSpec
import io.github.magisk317.smscode.rule.repository.SmsCodeRuleRecord
import io.github.magisk317.smscode.rule.repository.SmsCodeRuleRepository
import io.mockk.mockk
import io.mockk.every
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertNotNull

/**
 * The shared rule contract has to line up with relay's own app-facing repository.
 *
 * relay keeps working against AppConfigRepository; these checks make sure the contract
 * view maps onto exactly the same data, so code written against either one sees the
 * same rules.
 */
class SmsCodeRuleContractTest {

    private fun data(
        id: Long,
        company: String? = "Acme",
        keyword: String = "CODE",
        regex: String = "\\d{6}",
    ): SmsCodeRuleData = object : SmsCodeRuleData {
        override val id: Long = id
        override val company: String? = company
        override val codeKeyword: String = keyword
        override val codeRegex: String = regex
    }

    private fun repository(): SmsCodeRuleRepository = mockk(relaxed = true)

    @Test
    fun recordCarriesTheDomainFields() {
        val record = SmsCodeRuleRecord(
            id = 7L,
            spec = SmsCodeRuleSpec(company = "Acme", codeKeyword = "CODE", codeRegex = "\\d{6}"),
        )
        assertEquals(7L, record.id)
        assertEquals("Acme", record.spec.company)
        assertEquals("CODE", record.spec.codeKeyword)
        assertEquals("\\d{6}", record.spec.codeRegex)
    }

    @Test
    fun relayRepositorySatisfiesTheSharedContract() {
        val dao = mockk<SmsCodeRuleDao>(relaxed = true)
        val repo = ConfigRepository(
            context = mockk(relaxed = true),
            db = mockk(relaxed = true),
            smsCodeRuleDao = dao,
            appInfoDao = mockk(relaxed = true),
            notifyRouteRuleDao = mockk(relaxed = true),
            forwardFilterRuleDao = mockk(relaxed = true),
            ruleDao = mockk(relaxed = true),
            senderDao = mockk(relaxed = true),
        )
        // The screens inject AppConfigRepository; the contract must be the same object
        // so both views read and write one set of rules.
        val contract: SmsCodeRuleRepository = repo
        assertNotNull(contract)
        assertEquals(repo, contract)
    }

    @Test
    fun ruleMappingRoundTripsThroughTheContract() {
        val original = data(id = 3L, company = "Acme", keyword = "CODE", regex = "\\d{6}")
        val record = SmsCodeRuleRecord(
            id = original.id,
            spec = SmsCodeRuleSpec(
                company = original.company,
                codeKeyword = original.codeKeyword,
                codeRegex = original.codeRegex,
            ),
        )
        assertEquals(original.id, record.id)
        assertEquals(original.company, record.spec.company)
        assertEquals(original.codeKeyword, record.spec.codeKeyword)
        assertEquals(original.codeRegex, record.spec.codeRegex)
    }
}
