import test from "node:test";
import assert from "node:assert/strict";
import {
  buildMonthOccurrences,
  calculateOpeningBalance,
  calculateSummary,
  categoryInfo,
  creditEndMonth,
  DEFAULT_PACING,
  dailyAllowance,
  defaultCategory,
  defaultItems,
  displayedAvailableMoney,
  lastBusinessDay,
  installmentsThroughEnd,
  migrateState,
  normalizePacingPreferences,
  normalizeSpending,
  normalizeSavingsPlan,
  occurrenceDates,
  savingsForMonth,
  savingsOccurrences,
  savingsSlotsInMonth,
  spendingInMonth,
  spendingOnDate,
  splitAvailableMoney,
  totalSpending,
  weekdaysInRange,
} from "../finance.js";

test("calcula el último día hábil del mes", () => {
  assert.equal(lastBusinessDay(2026, 7).toISOString().slice(0, 10), "2026-08-31");
  assert.equal(lastBusinessDay(2026, 9).toISOString().slice(0, 10), "2026-10-30");
  assert.equal(lastBusinessDay(2026, 10).toISOString().slice(0, 10), "2026-11-30");
});

test("proyecta los datos iniciales de agosto", () => {
  const occurrences = buildMonthOccurrences(defaultItems(), "2026-08");
  const summary = calculateSummary(occurrences, new Date(2026, 7, 1));
  assert.equal(occurrences.length, 6);
  assert.equal(summary.income, 1_958_000);
  assert.equal(summary.committed, 363_000);
  assert.equal(summary.safeToSpend, 1_595_000);
});

test("la base de agosto no vuelve a recibirse en meses posteriores", () => {
  const occurrences = buildMonthOccurrences(defaultItems(), "2026-09");
  const openingBalance = calculateOpeningBalance(defaultItems(), "2026-09");
  const summary = calculateSummary(occurrences, new Date(2026, 8, 1), openingBalance);
  assert.equal(occurrences.some((item) => item.id === "base-1708"), false);
  assert.equal(openingBalance, 1_595_000);
  assert.equal(summary.income, 250_000);
  assert.equal(summary.committed, 363_000);
  assert.equal(summary.safeToSpend, 1_482_000);
});

test("arrastra el cierre acumulado a cada mes siguiente", () => {
  const items = defaultItems();
  const septemberOpening = calculateOpeningBalance(items, "2026-09");
  const octoberOpening = calculateOpeningBalance(items, "2026-10");
  const october = calculateSummary(
    buildMonthOccurrences(items, "2026-10"),
    new Date(2026, 9, 1),
    octoberOpening,
  );
  assert.equal(septemberOpening, 1_595_000);
  assert.equal(octoberOpening, 1_482_000);
  assert.equal(october.safeToSpend, 1_369_000);
});

test("migra la base mensual guardada por la versión anterior", () => {
  const previousState = {
    items: [{ ...defaultItems()[0], name: "Base mensual", frequency: "monthly" }],
    visibleMonth: "2026-09",
  };
  const migrated = migrateState(previousState);
  assert.equal(migrated.dataVersion, 8);
  assert.deepEqual(migrated.pacing, DEFAULT_PACING);
  assert.equal(migrated.items[0].name, "Saldo inicial de agosto");
  assert.equal(migrated.items[0].frequency, "once");
});

test("un crédito desaparece después de su última cuota", () => {
  const item = {
    ...defaultItems().find((candidate) => candidate.kind === "credit"),
    installments: 2,
  };
  assert.equal(buildMonthOccurrences([item], "2026-08").length, 1);
  assert.equal(buildMonthOccurrences([item], "2026-09")[0].installment, 2);
  assert.equal(buildMonthOccurrences([item], "2026-10").length, 0);
});

test("un crédito puede empezar en una cuota que ya está en curso", () => {
  const item = {
    ...defaultItems().find((candidate) => candidate.kind === "credit"),
    installments: 8,
    currentInstallment: 5,
  };
  assert.equal(buildMonthOccurrences([item], "2026-08")[0].installment, 5);
  assert.equal(buildMonthOccurrences([item], "2026-11")[0].installment, 8);
  assert.equal(buildMonthOccurrences([item], "2026-12").length, 0);
});

test("calcula el mes de la última cuota de créditos existentes", () => {
  const item = {
    ...defaultItems().find((candidate) => candidate.kind === "credit"),
    installments: 12,
    currentInstallment: 1,
  };
  assert.equal(creditEndMonth(item), "2027-07");
  item.currentInstallment = 5;
  assert.equal(creditEndMonth(item), "2027-03");
});

test("calcula el total de cuotas al elegir el mes final", () => {
  assert.equal(installmentsThroughEnd(5, "2026-08", "2026-11"), 8);
  assert.equal(installmentsThroughEnd(1, "2026-08", "2027-07"), 12);
});

test("un crédito con fecha final explícita desaparece al mes siguiente", () => {
  const item = {
    ...defaultItems().find((candidate) => candidate.kind === "credit"),
    installments: 12,
    endDate: "2026-09-01",
  };
  assert.equal(buildMonthOccurrences([item], "2026-09").length, 1);
  assert.equal(buildMonthOccurrences([item], "2026-10").length, 0);
});

test("los pagos marcados ajustan el saldo registrado sin alterar la proyección libre", () => {
  const items = defaultItems();
  const occurrences = buildMonthOccurrences(items, "2026-08");
  occurrences[0].paid = true;
  occurrences[1].paid = true;
  const summary = calculateSummary(occurrences);
  assert.equal(summary.safeToSpend, 1_595_000);
  assert.equal(summary.currentCash, occurrences[0].amount - occurrences[1].amount);
});

test("el saldo registrado también parte del cierre anterior", () => {
  const occurrences = buildMonthOccurrences(defaultItems(), "2026-09");
  occurrences.find((item) => item.id === "extra-250").paid = true;
  const summary = calculateSummary(occurrences, new Date(), 1_595_000);
  assert.equal(summary.currentCash, 1_845_000);
  assert.equal(summary.safeToSpend, 1_482_000);
});

test("divide el dinero libre entre los días restantes", () => {
  const allowance = dailyAllowance(310_000, "2026-08", new Date(2026, 7, 21));
  assert.equal(allowance.days, 11);
  assert.equal(allowance.amount, 310_000 / 11);
});

test("separa una meta fija de ahorro y divide solamente el dinero que queda para usar", () => {
  const allowance = dailyAllowance(310_000, "2026-08", new Date(2026, 7, 21), 62_000);
  assert.equal(allowance.target, 62_000);
  assert.equal(allowance.saved, 62_000);
  assert.equal(allowance.usable, 248_000);
  assert.equal(allowance.amount, 248_000 / 11);
});

test("conserva la misma meta de ahorro aunque cambie el dinero libre del mes", () => {
  const august = splitAvailableMoney(1_595_000, 963_300);
  const september = splitAvailableMoney(1_482_000, 963_300);
  const october = splitAvailableMoney(1_369_000, 963_300);
  assert.equal(august.target, 963_300);
  assert.equal(september.target, 963_300);
  assert.equal(october.target, 963_300);
  assert.equal(august.saved, 963_300);
  assert.equal(september.saved, 963_300);
  assert.equal(october.saved, 963_300);
});

test("mantiene la meta fija y limita el ahorro real al dinero disponible", () => {
  assert.deepEqual(splitAvailableMoney(100_000, -10), {
    base: 100_000,
    target: 0,
    rate: 0,
    saved: 0,
    usable: 100_000,
  });
  assert.deepEqual(splitAvailableMoney(100_000, 120_000), {
    base: 100_000,
    target: 120_000,
    rate: 100,
    saved: 100_000,
    usable: 0,
  });
  assert.equal(splitAvailableMoney(-50_000, 20_000).saved, 0);
});

test("descuenta el ahorro del monto principal solamente cuando se activa", () => {
  assert.equal(displayedAvailableMoney(500_000, 120_000, false), 500_000);
  assert.equal(displayedAvailableMoney(500_000, 120_000, true), 380_000);
  assert.equal(displayedAvailableMoney(100_000, 120_000, true), 0);
  assert.equal(displayedAvailableMoney(-50_000, 20_000, true), -50_000);
});

test("un plan de ahorro de una sola vez solo cuenta en su mes", () => {
  const plan = { amount: 800_000, frequency: "once", startMonth: "2026-08" };
  assert.equal(savingsForMonth(plan, "2026-08"), 800_000);
  assert.equal(savingsForMonth(plan, "2026-09"), 0);
  assert.equal(savingsForMonth(plan, "2027-08"), 0);
  assert.equal(savingsForMonth(plan, "2026-07"), 0);
});

test("el plan de ahorro respeta cada frecuencia", () => {
  const plan = (frequency) => ({ amount: 100_000, frequency, startMonth: "2026-08" });
  assert.equal(savingsForMonth(plan("monthly"), "2026-11"), 100_000);
  assert.equal(savingsForMonth(plan("bimonthly"), "2026-09"), 0);
  assert.equal(savingsForMonth(plan("bimonthly"), "2026-10"), 100_000);
  assert.equal(savingsForMonth(plan("semiannual"), "2027-02"), 100_000);
  assert.equal(savingsForMonth(plan("yearly"), "2027-08"), 100_000);
  assert.equal(savingsForMonth(plan("yearly"), "2027-07"), 0);
  // Agosto de 2026 empieza en sábado: cinco semanas y tres quincenas.
  assert.equal(savingsOccurrences(plan("weekly"), "2026-08"), 5);
  assert.equal(savingsOccurrences(plan("biweekly"), "2026-08"), 3);
  assert.equal(savingsForMonth(plan("weekly"), "2026-09"), 400_000);
});

test("el tope por aporte se reparte entre las veces que cabe el ahorro en el mes", () => {
  assert.equal(savingsSlotsInMonth("monthly", "2026-08"), 1);
  assert.equal(savingsSlotsInMonth("once", "2026-08"), 1);
  assert.equal(savingsSlotsInMonth("weekly", "2026-08"), 5);
  assert.equal(savingsSlotsInMonth("biweekly", "2026-08"), 3);
  // Aunque el plan no aplique en ese mes, el tope nunca baja de un aporte.
  assert.equal(savingsSlotsInMonth("yearly", "2026-09"), 1);
});

test("normaliza planes de ahorro dañados o incompletos", () => {
  assert.deepEqual(normalizeSavingsPlan({}, "2026-08"), { amount: 0, frequency: "monthly", startMonth: "2026-08" });
  assert.deepEqual(normalizeSavingsPlan({ amount: -5, frequency: "inventada", startMonth: "roto" }, "2026-08-01"),
    { amount: 0, frequency: "monthly", startMonth: "2026-08" });
  assert.equal(savingsForMonth({ amount: 100_000, frequency: "monthly", startMonth: "" }, "2026-08"), 0);
});

test("migra el porcentaje anterior a una meta fija según el último mes visible", () => {
  const migrated = migrateState({
    items: defaultItems(),
    visibleMonth: "2026-09",
    savingsRate: 65,
    dataVersion: 4,
  });
  assert.equal(migrated.dataVersion, 8);
  assert.equal(migrated.savings.amount, 963_300);
  assert.equal(migrated.savings.frequency, "monthly");
  assert.equal(migrated.savings.startMonth, "2026-09");
  assert.equal(migrated.deductSavingsFromAvailable, false);
});

test("el ahorro suelto anterior se vuelve un plan mensual anclado al mes visible", () => {
  const migrated = migrateState({
    items: defaultItems(),
    visibleMonth: "2026-10",
    savingsTarget: 500_000,
    dataVersion: 7,
  });
  assert.deepEqual(migrated.savings, { amount: 500_000, frequency: "monthly", startMonth: "2026-10" });
  assert.equal(migrated.savingsTarget, undefined);
});

test("conserva el plan de ahorro con frecuencia propia al migrar", () => {
  const migrated = migrateState({
    items: defaultItems(),
    savings: { amount: 800_000, frequency: "once", startMonth: "2026-08" },
    dataVersion: 8,
  });
  assert.deepEqual(migrated.savings, { amount: 800_000, frequency: "once", startMonth: "2026-08" });
});

test("conserva la preferencia de descontar el ahorro al migrar", () => {
  const migrated = migrateState({
    items: defaultItems(),
    savingsTarget: 200_000,
    deductSavingsFromAvailable: true,
    dataVersion: 5,
  });
  assert.equal(migrated.dataVersion, 8);
  assert.equal(migrated.deductSavingsFromAvailable, true);
});

test("divide el dinero solamente entre los días de uso restantes", () => {
  const allowance = dailyAllowance(
    220_000,
    "2026-08",
    new Date(2026, 7, 21),
    0,
    { spendingDays: [1, 2, 3, 4, 5], weekStart: 1, weekEnd: 5 },
  );
  assert.equal(allowance.days, 7);
  assert.equal(allowance.amount, 220_000 / 7);
  assert.equal(allowance.weeklyDays, 5);
  assert.equal(allowance.weeklyAmount, (220_000 / 7) * 5);
});

test("permite una semana personalizada que cruza el domingo", () => {
  assert.deepEqual(weekdaysInRange(5, 1), [5, 6, 0, 1]);
  const allowance = dailyAllowance(
    100_000,
    "2026-08",
    new Date(2026, 7, 1),
    0,
    { spendingDays: [0, 1, 6], weekStart: 5, weekEnd: 1 },
  );
  assert.equal(allowance.weeklyDays, 3);
  assert.equal(allowance.weeklyAmount, allowance.amount * 3);
});

test("normaliza preferencias de ritmo dañadas o vacías", () => {
  assert.deepEqual(normalizePacingPreferences({ spendingDays: [], weekStart: -4, weekEnd: 12 }), {
    spendingDays: [...DEFAULT_PACING.spendingDays],
    weekStart: 0,
    weekEnd: 6,
  });
});

test("el ritmo semanal nunca supera el dinero restante del mes", () => {
  const allowance = dailyAllowance(100_000, "2026-08", new Date(2026, 7, 31));
  assert.equal(allowance.days, 1);
  assert.equal(allowance.weeklyDays, 7);
  assert.equal(allowance.weeklyAmount, 100_000);
});

test("un movimiento semanal aparece varias veces en el mismo mes", () => {
  const item = {
    id: "semanal",
    name: "Mercado semanal",
    type: "expense",
    kind: "expense",
    amount: 90_000,
    frequency: "weekly",
    startDate: "2026-08-03",
    payments: {},
  };
  const dates = occurrenceDates(item, "2026-08").map((date) => date.getDate());
  assert.deepEqual(dates, [3, 10, 17, 24, 31]);
  assert.deepEqual(occurrenceDates(item, "2026-09").map((date) => date.getDate()), [7, 14, 21, 28]);
  const summary = calculateSummary(buildMonthOccurrences([item], "2026-08"));
  assert.equal(summary.committed, 450_000);
});

test("un movimiento quincenal salta catorce días desde su fecha inicial", () => {
  const item = { id: "q", name: "Quincena", type: "income", kind: "income", amount: 1_000, frequency: "biweekly", startDate: "2026-08-05", payments: {} };
  assert.deepEqual(occurrenceDates(item, "2026-08").map((date) => date.getDate()), [5, 19]);
  assert.deepEqual(occurrenceDates(item, "2026-09").map((date) => date.getDate()), [2, 16, 30]);
  assert.deepEqual(occurrenceDates(item, "2026-07"), []);
});

test("las frecuencias de varios meses solo caen en su mes correspondiente", () => {
  const item = { id: "t", name: "Seguro", type: "expense", kind: "expense", amount: 300_000, frequency: "quarterly", schedule: "day", day: 10, startDate: "2026-08-10", payments: {} };
  assert.equal(occurrenceDates(item, "2026-08").length, 1);
  assert.equal(occurrenceDates(item, "2026-09").length, 0);
  assert.equal(occurrenceDates(item, "2026-11").length, 1);
  const yearly = { ...item, frequency: "yearly" };
  assert.equal(occurrenceDates(yearly, "2027-08").length, 1);
  assert.equal(occurrenceDates(yearly, "2027-09").length, 0);
});

test("los gastos del día a día reducen el dinero libre y el saldo que pasa al mes siguiente", () => {
  const spending = [
    { id: "1", date: "2026-08-04", amount: 120_000, category: "mercado" },
    { id: "2", date: "2026-08-09", amount: 30_000, category: "ocio" },
    { id: "3", date: "2026-09-01", amount: 15_000, category: "transporte" },
  ];
  const august = spendingInMonth(spending, "2026-08");
  assert.equal(august.length, 2);
  assert.equal(totalSpending(august), 150_000);
  assert.equal(spendingOnDate(spending, "2026-08-04"), 120_000);

  const summary = calculateSummary(buildMonthOccurrences(defaultItems(), "2026-08"), new Date(2026, 7, 10), 0, 150_000);
  assert.equal(summary.variableSpent, 150_000);
  assert.equal(summary.safeToSpend, 1_445_000);
  assert.equal(calculateOpeningBalance(defaultItems(), "2026-09", spending), 1_445_000);
});

test("el presupuesto de hoy descuenta lo que ya se gastó en el día", () => {
  const allowance = dailyAllowance(220_000, "2026-08", new Date(2026, 7, 21), 0, {}, 20_000);
  assert.equal(allowance.days, 11);
  assert.equal(allowance.isSpendingDay, true);
  assert.equal(allowance.todayBudget, 240_000 / 11);
  assert.equal(allowance.todayLeft, 240_000 / 11 - 20_000);
  assert.equal(allowance.amount, 220_000 / 11);
});

test("en un día que no es de uso no hay presupuesto diario", () => {
  const allowance = dailyAllowance(200_000, "2026-08", new Date(2026, 7, 22), 0, { spendingDays: [1, 2, 3, 4, 5] });
  assert.equal(allowance.isSpendingDay, false);
  assert.equal(allowance.todayBudget, 0);
});

test("limpia los gastos guardados que llegan dañados", () => {
  const entries = normalizeSpending([
    { date: "2026-08-04", amount: 50_000, category: "inventada", note: "café" },
    { date: "2026-08-05", amount: -10 },
    { amount: 1_000 },
  ]);
  assert.equal(entries.length, 1);
  assert.equal(entries[0].category, "otros");
  assert.ok(entries[0].id);
});

test("asigna una categoría por defecto según el tipo de movimiento", () => {
  assert.equal(defaultCategory({ type: "expense", kind: "credit" }), "deudas");
  assert.equal(defaultCategory({ type: "income" }), "otros-ingreso");
  assert.equal(categoryInfo("mercado").label, "Mercado");
  assert.equal(categoryInfo("no-existe", "income").id, "otros-ingreso");
});

test("la migración agrega categorías, gastos y ajustes a los datos anteriores", () => {
  const migrated = migrateState({ items: defaultItems().map(({ category, ...item }) => item), dataVersion: 6 });
  assert.equal(migrated.items.find((item) => item.kind === "credit").category, "deudas");
  assert.deepEqual(migrated.spending, []);
  assert.deepEqual(migrated.goals, []);
  assert.equal(migrated.settings.skipHolidays, true);
  assert.equal(migrated.settings.emergencyMonths, 3);
});
