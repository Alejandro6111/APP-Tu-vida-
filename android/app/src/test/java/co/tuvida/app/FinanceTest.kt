package co.tuvida.app

import co.tuvida.app.data.*
import co.tuvida.app.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class FinanceTest {
    private val aug = YearMonth.of(2026, 8)
    private fun income(amount: Long = 1000000) = Movement(id = "salary", name = "Salario", amount = amount, type = "income", kind = "income", startDate = "2026-08-01", day = 1)
    private fun bill(amount: Long = 200000) = Movement(id = "rent", name = "Gasto", amount = amount, startDate = "2026-08-01", day = 10)
    @Test fun openingBalanceCarriesOnlyOnce() {
        val data = AppData(movements = listOf(income(1708000).copy(frequency = "once"), bill()))
        assertEquals(1508000, Finance.summary(data, aug).closing)
        assertEquals(1508000, Finance.summary(data, aug.plusMonths(1)).opening)
        assertEquals(1308000, Finance.summary(data, aug.plusMonths(1)).closing)
    }
    @Test fun recordedCashUsesOnlyMarkedPayments() {
        val data = AppData(movements = listOf(income(), bill().copy(payments = setOf("2026-08"))))
        val summary = Finance.summary(data, aug)
        assertEquals(800000, summary.closing); assertEquals(-200000, summary.cash)
    }
    @Test fun dailyExpensesReduceNextMonthsOpening() {
        val data = AppData(movements = listOf(income(), bill()), spending = listOf(Spending(date = "2026-08-20", amount = 50000)))
        assertEquals(750000, Finance.summary(data, aug).closing); assertEquals(750000, Finance.summary(data, aug.plusMonths(1)).opening)
    }
    @Test fun februaryClampsDay31() { assertEquals(listOf(LocalDate.of(2027, 2, 28)), Finance.dates(bill().copy(day = 31), YearMonth.of(2027, 2), true)) }
    @Test fun lastBusinessDayUsesColombianHoliday() { assertEquals(LocalDate.of(2026, 8, 31), Finance.lastBusinessDay(aug, true)); assertEquals(LocalDate.of(2026, 5, 29), Finance.lastBusinessDay(YearMonth.of(2026, 5), true)) }
    @Test fun christmas2026IsHoliday() { assertTrue(LocalDate.of(2026, 12, 25) in Finance.holidays(2026)) }
    @Test fun holyThursdayAndFriday() { assertTrue(LocalDate.of(2026, 4, 2) in Finance.holidays(2026)); assertTrue(LocalDate.of(2026, 4, 3) in Finance.holidays(2026)) }
    @Test fun emilianiMonday() { assertTrue(LocalDate.of(2026, 1, 12) in Finance.holidays(2026)); assertFalse(LocalDate.of(2026, 1, 6) in Finance.holidays(2026)) }
    @Test fun weeklyHasFiveOccurrences() { assertEquals(5, Finance.dates(bill().copy(frequency = "weekly"), aug, true).size) }
    @Test fun biweeklyKeepsFifteenDayAnchor() { assertEquals(listOf("2026-08-01", "2026-08-16", "2026-08-31"), Finance.dates(bill().copy(frequency = "biweekly"), aug, true).map { it.toString() }) }
    @Test fun quarterlyOnlyOnAnchorMonths() { val q = bill().copy(frequency = "quarterly"); assertEquals(0, Finance.dates(q, aug.plusMonths(1), true).size); assertEquals(1, Finance.dates(q, aug.plusMonths(3), true).size) }
    @Test fun endDateStopsRecurringMovement() { assertEquals(0, Finance.dates(bill().copy(endDate = "2026-09-05"), aug.plusMonths(1), true).size) }
    @Test fun installmentsStopAfterLastMonth() { val c = bill().copy(kind = "credit", installments = 3, currentInstallment = 2); assertEquals(1, Finance.dates(c, aug.plusMonths(1), true).size); assertEquals(0, Finance.dates(c, aug.plusMonths(2), true).size) }
    @Test fun differentWeeklyPaymentsAreIndependent() { val m = bill().copy(frequency = "weekly", payments = setOf("2026-08-08")); val data = AppData(movements = listOf(m)); assertEquals(1, Finance.occurrences(data, aug).count { it.paid }) }
    @Test fun savingsOnlyOnce() { val plan = Savings(100000, "once", "2026-08"); assertEquals(100000, Finance.plannedSavings(plan, aug)); assertEquals(0, Finance.plannedSavings(plan, aug.plusMonths(1))) }
    @Test fun weeklySavingsCountsOccurrences() { assertEquals(250000, Finance.plannedSavings(Savings(50000, "weekly", "2026-08"), aug)) }
    @Test fun savingsCannotExceedPositiveClosing() { val data = AppData(movements = listOf(income(100000)), savings = Savings(200000, "monthly", "2026-08")); assertEquals(100000, Finance.summary(data, aug).saving); assertEquals(0, Finance.summary(data, aug).free) }
    @Test fun deficitIsNotMadeIntoSavings() { val data = AppData(movements = listOf(bill()), savings = Savings(100000, "monthly", "2026-08")); assertEquals(0, Finance.summary(data, aug).saving); assertEquals(-200000, Finance.summary(data, aug).free) }
    @Test fun projectionHasTwelveMonthsAndCumulativeSavings() { val data = AppData(movements = listOf(income()), savings = Savings(100000, "monthly", "2026-08")); val p = Finance.projection(data, aug); assertEquals(12, p.size); assertEquals(1200000, p.last().savings); assertEquals(10800000, p.last().free) }
    @Test fun savingsDecreaseWhenCashFalls() { val data = AppData(movements = listOf(income(1000000).copy(frequency = "once"), bill(300000)), savings = Savings(500000, "monthly", "2026-08")); val p = Finance.projection(data, aug); assertTrue(p[2].savings <= p[2].closing.coerceAtLeast(0)) }
    @Test fun todaysSpendingIsDeductedFromOriginalBudget() { val day = LocalDate.of(2026, 8, 31); val data = AppData(movements = listOf(income(100000)), spending = listOf(Spending(date = day.toString(), amount = 20000))); assertEquals(80000, Finance.summary(data, aug, day).today) }
    @Test fun noDailyAllowanceOnUnselectedDay() { val data = AppData(movements = listOf(income()), financeSettings = FinanceSettings(spendingDays = setOf(1))); assertEquals(0, Finance.summary(data, aug, LocalDate.of(2026, 8, 2)).today) }
    @Test fun payoffWithExtraIsFaster() { val data = AppData(movements = listOf(bill(100000).copy(kind = "credit", installments = 12))); val p = Finance.simulate(data, aug, 100000, "snowball"); assertEquals(12, p.normalMonths); assertEquals(6, p.extraMonths) }
    @Test fun duplicateMarkedPaymentsDoNotDoubleCount() { val data = AppData(movements = listOf(bill().copy(kind = "credit", installments = 3, payments = setOf("2026-08")))); assertEquals(400000, Finance.debts(data, aug).single().remaining) }
    @Test fun categoryActualIncludesDiaryAndPaidMovement() { val data = AppData(movements = listOf(bill().copy(category = "mercado", payments = setOf("2026-08"))), spending = listOf(Spending(date = "2026-08-03", amount = 10000, category = "mercado"))); assertEquals(200000L to 210000L, Finance.categoryTotals(data, aug)["mercado"]) }
    @Test fun healthScoreIsBounded() { val data = AppData(movements = listOf(income(), bill()), savings = Savings(200000, "monthly", "2026-08")); assertTrue(Finance.healthScore(data, aug) in 0..100); assertEquals(0, Finance.healthScore(AppData(), aug)) }
    @Test fun arrearsProduceAdvice() { val data = AppData(movements = listOf(bill())); assertTrue(Finance.alerts(data, aug, LocalDate.of(2026, 8, 15)).any { it.title.contains("Pago pendiente") }) }
}
