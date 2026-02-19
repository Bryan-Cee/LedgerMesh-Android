package com.example.ledgermesh.ingestion.sms

/**
 * Factory for built-in [SenderProfile] definitions covering common Kenyan
 * mobile money and bank SMS formats.
 *
 * These profiles serve as sensible defaults. Users can disable individual
 * profiles or add custom ones via Settings.
 */
object DefaultProfiles {

    /** Returns all built-in profiles, sorted by [SenderProfile.priority] descending. */
    fun getAll(): List<SenderProfile> = listOf(
        mpesaProfile(),
        equityBankProfile(),
        kcbBankProfile(),
        genericBankProfile()
    ).sortedByDescending { it.priority }

    // -----------------------------------------------------------------------
    // M-PESA (Safaricom)
    // -----------------------------------------------------------------------

    private fun mpesaProfile(): SenderProfile {
        return SenderProfile(
            id = "mpesa",
            name = "M-PESA",
            senderAddresses = listOf("MPESA", "M-PESA", "Safaricom"),
            currency = "KES",
            priority = 10,
            patterns = listOf(
                // Send Money:
                // "RC12AB345 Confirmed. Ksh1,500.00 sent to John Doe 0712345678
                //  on 18/2/26 at 3:45 PM. New M-PESA balance is Ksh5,000.00."
                SmsPattern(
                    name = "send_money",
                    regex = """([A-Z0-9]+)\s+Confirmed\.\s*Ksh([\d,]+\.?\d*)\s+sent\s+to\s+(.+?)\s+(\d{10,})\s+on\s+(\d{1,2}/\d{1,2}/\d{2,4})\s+at\s+(\d{1,2}:\d{2}\s*[AaPp][Mm])""",
                    direction = "DEBIT",
                    captureGroups = CaptureGroups(
                        amount = 2,
                        reference = 1,
                        counterparty = 3,
                        accountHint = 4
                    )
                ),
                // Receive Money:
                // "RC12AB345 Confirmed. You have received Ksh2,850.00 from Acme Corp
                //  0712345678 on 18/2/26 at 10:30 AM."
                SmsPattern(
                    name = "receive_money",
                    regex = """([A-Z0-9]+)\s+Confirmed\.\s*You\s+have\s+received\s+Ksh([\d,]+\.?\d*)\s+from\s+(.+?)\s+(\d{10,})\s+on\s+(\d{1,2}/\d{1,2}/\d{2,4})\s+at\s+(\d{1,2}:\d{2}\s*[AaPp][Mm])""",
                    direction = "CREDIT",
                    captureGroups = CaptureGroups(
                        amount = 2,
                        reference = 1,
                        counterparty = 3,
                        accountHint = 4
                    )
                ),
                // Buy Goods:
                // "RC12AB345 Confirmed. Ksh87.50 paid to Whole Foods Market
                //  on 18/2/26 at 2:15 PM."
                SmsPattern(
                    name = "buy_goods",
                    regex = """([A-Z0-9]+)\s+Confirmed\.\s*Ksh([\d,]+\.?\d*)\s+paid\s+to\s+(.+?)\s+on\s+(\d{1,2}/\d{1,2}/\d{2,4})\s+at\s+(\d{1,2}:\d{2}\s*[AaPp][Mm])""",
                    direction = "DEBIT",
                    captureGroups = CaptureGroups(
                        amount = 2,
                        reference = 1,
                        counterparty = 3
                    )
                ),
                // Paybill:
                // "RC12AB345 Confirmed. Ksh1,250.00 sent to Oak Street Apartments
                //  for account 1234 on 18/2/26 at 9:00 AM."
                SmsPattern(
                    name = "paybill",
                    regex = """([A-Z0-9]+)\s+Confirmed\.\s*Ksh([\d,]+\.?\d*)\s+sent\s+to\s+(.+?)\s+for\s+account\s+(\S+)\s+on\s+(\d{1,2}/\d{1,2}/\d{2,4})\s+at\s+(\d{1,2}:\d{2}\s*[AaPp][Mm])""",
                    direction = "DEBIT",
                    captureGroups = CaptureGroups(
                        amount = 2,
                        reference = 1,
                        counterparty = 3,
                        accountHint = 4
                    )
                ),
                // Withdraw from Agent:
                // "RC12AB345 Confirmed. on 18/2/26 at 3:00 PM Withdraw Ksh5,000.00
                //  from 12345 - Agent Name."
                SmsPattern(
                    name = "withdraw",
                    regex = """([A-Z0-9]+)\s+Confirmed\.\s*on\s+(\d{1,2}/\d{1,2}/\d{2,4})\s+at\s+(\d{1,2}:\d{2}\s*[AaPp][Mm])\s+Withdraw\s+Ksh([\d,]+\.?\d*)\s+from\s+(.+)""",
                    direction = "DEBIT",
                    captureGroups = CaptureGroups(
                        amount = 4,
                        reference = 1,
                        counterparty = 5
                    )
                ),
                // Fallback: any "Confirmed" message with Ksh amount
                SmsPattern(
                    name = "generic_confirmed",
                    regex = """([A-Z0-9]+)\s+Confirmed\..*?Ksh([\d,]+\.?\d*)""",
                    direction = "DEBIT",
                    captureGroups = CaptureGroups(
                        amount = 2,
                        reference = 1
                    )
                )
            )
        )
    }

    // -----------------------------------------------------------------------
    // Equity Bank
    // -----------------------------------------------------------------------

    private fun equityBankProfile(): SenderProfile {
        return SenderProfile(
            id = "equity",
            name = "Equity Bank",
            senderAddresses = listOf("ABORTEQU", "Abortequ", "EquityBk", "ABORTEQ", "Equity"),
            currency = "KES",
            priority = 5,
            patterns = listOf(
                // Debit:
                // "You have made a payment of KES 5,000.00 to MERCHANT on 18-02-2026
                //  12:00. Ref: 123456. Balance: KES 50,000.00"
                SmsPattern(
                    name = "payment",
                    regex = """(?:payment|transfer|withdrawal)\s+of\s+KES\s*([\d,]+\.?\d*)\s+(?:to|from)\s+(.+?)\s+on\s+(\d{2}-\d{2}-\d{4}).*?Ref:\s*(\S+)""",
                    direction = "DEBIT",
                    captureGroups = CaptureGroups(
                        amount = 1,
                        counterparty = 2,
                        reference = 4
                    )
                ),
                // Credit:
                // "You have received KES 10,000.00 from NAME on 18-02-2026. Ref: 123456."
                SmsPattern(
                    name = "receive",
                    regex = """received\s+KES\s*([\d,]+\.?\d*)\s+from\s+(.+?)\s+on\s+(\d{2}-\d{2}-\d{4}).*?Ref:\s*(\S+)""",
                    direction = "CREDIT",
                    captureGroups = CaptureGroups(
                        amount = 1,
                        counterparty = 2,
                        reference = 4
                    )
                )
            )
        )
    }

    // -----------------------------------------------------------------------
    // KCB Bank
    // -----------------------------------------------------------------------

    private fun kcbBankProfile(): SenderProfile {
        return SenderProfile(
            id = "kcb",
            name = "KCB Bank",
            senderAddresses = listOf("KCB", "KCBBank", "KCBGroup"),
            currency = "KES",
            priority = 5,
            patterns = listOf(
                SmsPattern(
                    name = "debit",
                    regex = """(?:debited|withdrawn|paid)\s+KES\s*([\d,]+\.?\d*).*?(?:to|at)\s+(.+?)\.?\s*(?:Ref|TXN|Trans)[\s.:]*(\S+)""",
                    direction = "DEBIT",
                    captureGroups = CaptureGroups(
                        amount = 1,
                        counterparty = 2,
                        reference = 3
                    )
                ),
                SmsPattern(
                    name = "credit",
                    regex = """(?:credited|received|deposited)\s+KES\s*([\d,]+\.?\d*).*?(?:from|by)\s+(.+?)\.?\s*(?:Ref|TXN|Trans)[\s.:]*(\S+)""",
                    direction = "CREDIT",
                    captureGroups = CaptureGroups(
                        amount = 1,
                        counterparty = 2,
                        reference = 3
                    )
                )
            )
        )
    }

    // -----------------------------------------------------------------------
    // Generic Bank (content-only fallback, no sender address filter)
    // -----------------------------------------------------------------------

    private fun genericBankProfile(): SenderProfile {
        return SenderProfile(
            id = "generic_bank",
            name = "Generic Bank",
            senderAddresses = emptyList(),
            currency = "KES",
            priority = 0,
            patterns = listOf(
                SmsPattern(
                    name = "generic_debit",
                    regex = """(?:debited|paid|sent|withdrawn|transferred)\s+(?:KES|Ksh|KSh)\s*([\d,]+\.?\d*)""",
                    direction = "DEBIT",
                    captureGroups = CaptureGroups(amount = 1)
                ),
                SmsPattern(
                    name = "generic_credit",
                    regex = """(?:credited|received|deposited)\s+(?:KES|Ksh|KSh)\s*([\d,]+\.?\d*)""",
                    direction = "CREDIT",
                    captureGroups = CaptureGroups(amount = 1)
                )
            )
        )
    }
}
