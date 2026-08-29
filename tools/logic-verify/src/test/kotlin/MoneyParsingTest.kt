import com.dadsvictory.domain.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MoneyParsingTest {

    @Test
    fun `plain numbers`() {
        assertEquals(500, Money.parseToMinor("5"))
        assertEquals(1_050, Money.parseToMinor("10.50"))
        assertEquals(1_050, Money.parseToMinor("10.5"))
        assertEquals(0, Money.parseToMinor("0"))
    }

    @Test
    fun `a currency symbol or spaces are ignored`() {
        assertEquals(1_050, Money.parseToMinor("£10.50"))
        assertEquals(1_050, Money.parseToMinor("$10.50"))
        assertEquals(1_050, Money.parseToMinor(" 10.50 "))
        assertEquals(1_050, Money.parseToMinor("£ 10.50"))
    }

    @Test
    fun `thousands separators are stripped`() {
        assertEquals(123_456, Money.parseToMinor("1,234.56"))
        assertEquals(123_400, Money.parseToMinor("1,234"))
        assertEquals(100_000_000, Money.parseToMinor("1,000,000"))
    }

    @Test
    fun `a comma decimal mark works, as used across much of Europe`() {
        assertEquals(1_050, Money.parseToMinor("10,50"))
        assertEquals(123_456, Money.parseToMinor("1.234,56"))
        assertEquals(550, Money.parseToMinor("5,5"))
    }

    @Test
    fun `more than two decimal places are truncated rather than rejected`() {
        assertEquals(1_099, Money.parseToMinor("10.999"))
    }

    @Test
    fun `a leading decimal point is understood`() {
        assertEquals(50, Money.parseToMinor(".50"))
    }

    @Test
    fun `nonsense returns null instead of a wrong number`() {
        assertNull(Money.parseToMinor(""))
        assertNull(Money.parseToMinor("   "))
        assertNull(Money.parseToMinor("abc"))
        assertNull(Money.parseToMinor("£"))
        assertNull(Money.parseToMinor("1.2.3"))
    }

    @Test
    fun `an absurdly long number is refused rather than overflowing`() {
        assertNull(Money.parseToMinor("9999999999999999999999"))
    }

    @Test
    fun `parsing and formatting round trip`() {
        for (text in listOf("5", "10.50", "1,234.56", "0.99", "100")) {
            val minor = Money.parseToMinor(text)!!
            assertEquals(minor, Money.parseToMinor(Money.toEditableText(minor)))
        }
    }

    @Test
    fun `editable text drops trailing pence when they are zero`() {
        assertEquals("10", Money.toEditableText(1_000))
        assertEquals("10.50", Money.toEditableText(1_050))
        assertEquals("0.05", Money.toEditableText(5))
    }
}
