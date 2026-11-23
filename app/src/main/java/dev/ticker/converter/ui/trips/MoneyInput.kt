package dev.ticker.converter.ui.trips

import java.math.BigDecimal

/** Typed money: either `.` or `,` is the decimal mark, spaces are ignored. Null for anything that is not above zero. */
fun parseAmount(text: String): BigDecimal? =
    text.filterNot { it.isWhitespace() }.replace(',', '.').toBigDecimalOrNull()?.takeIf { it.signum() > 0 }
