import test from "node:test";
import assert from "node:assert/strict";
import {
  buildAlerts,
  categoryBreakdown,
  compareWithPreviousMonth,
  debtOverview,
  financialHealth,
  goalProgress,
  projectMonths,
  simulateDebtPayoff,
} from "../insights.js";
import { buildMonthOccurrences, calculateSummary, defaultItems } from "../finance.js";

test("proyecta doce meses arrastrando el saldo de cierre", () => {
  const months = projectMonths(defaultItems(), "2026-08", 14);
  assert.equal(months[0].opening, 0);
  assert.equal(months[0].closing, 1_595_000);
  assert.equal(months[1].opening, 1_595_000);
  assert.equal(months[1].closing, 1_482_000);
  // Julio de 2027 es la última cuota de los tres créditos.
  assert.equal(months[11].key, "2027-07");
  assert.equal(months[11].expenses, 363_000);
  // Después solo queda la suscripción mensual.
  assert.equal(months[12].expenses, 140_000);
  assert.equal(months[12].net, 110_000);
});

test("la proyección descuenta los gastos del día a día", () => {
  const spending = [
    { id: "a", date: "2026-08-05", amount: 100_000, category: "mercado" },
    { id: "b", date: "2026-09-05", amount: 50_000, category: "ocio" },
  ];
  const months = projectMonths(defaultItems(), "2026-08", 3, spending);
  assert.equal(months[0].closing, 1_495_000);
  assert.equal(months[1].closing, 1_495_000 + 250_000 - 363_000 - 50_000);
});

test("un ahorro de una sola vez solo aparta en su mes y deja libre el resto", () => {
  const savingsPlan = { amount: 800_000, frequency: "once", startMonth: "2026-08" };
  const months = projectMonths(defaultItems(), "2026-08", 3, [], { savingsPlan });
  assert.equal(months[0].saved, 800_000);
  assert.equal(months[0].savedTotal, 800_000);
  assert.equal(months[0].free, 795_000);
  // Septiembre y octubre no vuelven a apartar: el ahorro se queda quieto y todo lo demás es libre.
  assert.equal(months[1].saved, 0);
  assert.equal(months[1].savedTotal, 800_000);
  assert.equal(months[1].free, 682_000);
  assert.equal(months[2].savedTotal, 800_000);
  assert.equal(months[2].free, 569_000);
  assert.ok(months.every((month) => month.savingsShort === 0));
});

test("cuando el saldo cae por debajo de lo guardado se avisa del bocado, no de un faltante", () => {
  const savingsPlan = { amount: 800_000, frequency: "once", startMonth: "2026-08" };
  const months = projectMonths(defaultItems(), "2026-08", 12, [], { savingsPlan });
  // El saldo baja 113.000 al mes hasta quedar por debajo de los 800.000 apartados.
  const drop = months.find((month) => month.savingsDrop > 0);
  assert.equal(drop.key, "2027-04");
  assert.equal(drop.savingsDrop, 109_000);
  assert.equal(drop.savedTotal, drop.closing);
  assert.equal(drop.free, 0);
  // Ese mes no tocaba apartar nada, así que no hay meta incumplida.
  assert.ok(months.every((month) => month.savingsShort === 0));
});

test("un ahorro mensual se acumula hasta donde alcanza el saldo", () => {
  const savingsPlan = { amount: 800_000, frequency: "monthly", startMonth: "2026-08" };
  const months = projectMonths(defaultItems(), "2026-08", 3, [], { savingsPlan });
  assert.equal(months[0].savedTotal, 800_000);
  // El saldo no crece 800.000 al mes, así que el ahorro acumulado topa con el cierre.
  assert.equal(months[1].savedTotal, months[1].closing);
  assert.equal(months[1].saved, 682_000);
  assert.equal(months[1].savingsShort, 118_000);
  assert.equal(months[1].free, 0);
});

test("un ahorro trimestral solo aparta en los meses que le tocan", () => {
  const savingsPlan = { amount: 300_000, frequency: "quarterly", startMonth: "2026-08" };
  const months = projectMonths(defaultItems(), "2026-08", 5, [], { savingsPlan });
  assert.deepEqual(months.map((month) => month.saved), [300_000, 0, 0, 300_000, 0]);
  assert.equal(months.at(-1).savedTotal, 600_000);
});

test("un ahorro semanal aparta una vez por semana del mes", () => {
  const savingsPlan = { amount: 50_000, frequency: "weekly", startMonth: "2026-08" };
  const months = projectMonths(defaultItems(), "2026-08", 2, [], { savingsPlan });
  // Agosto de 2026 empieza en sábado: caben cinco sábados.
  assert.equal(months[0].monthlySavings, 250_000);
  assert.equal(months[0].saved, 250_000);
  assert.equal(months[1].monthlySavings, 200_000);
});

test("sin meta de ahorro la proyección deja todo el cierre libre", () => {
  const months = projectMonths(defaultItems(), "2026-08", 3);
  assert.equal(months[0].savedTotal, 0);
  assert.equal(months[0].free, months[0].closing);
  assert.equal(months[0].savingsShort, 0);
});

test("compara el mes visible con el anterior", () => {
  const comparison = compareWithPreviousMonth(defaultItems(), "2026-09");
  assert.equal(comparison.income.current, 250_000);
  assert.equal(comparison.income.previous, 1_958_000);
  assert.equal(comparison.expenses.diff, 0);
  assert.equal(comparison.free.current, 1_482_000);
});

test("agrupa los gastos por categoría sumando lo planeado y lo real", () => {
  const occurrences = buildMonthOccurrences(defaultItems(), "2026-08");
  const rows = categoryBreakdown(occurrences, [
    { id: "a", date: "2026-08-03", amount: 60_000, category: "mercado" },
    { id: "b", date: "2026-08-04", amount: 40_000, category: "mercado" },
  ]);
  const deudas = rows.find((row) => row.id === "deudas");
  const mercado = rows.find((row) => row.id === "mercado");
  assert.equal(deudas.planned, 223_000);
  assert.equal(mercado.real, 100_000);
  assert.equal(Math.round(rows.reduce((sum, row) => sum + row.share, 0)), 100);
});

test("resume las deudas activas del mes", () => {
  const debt = debtOverview(defaultItems(), "2026-08");
  assert.equal(debt.count, 3);
  assert.equal(debt.monthlyPayment, 223_000);
  assert.equal(debt.remainingBalance, 223_000 * 12);
  assert.equal(debt.freeMonth, "2027-07");
  assert.equal(Math.round(debt.progress), 0);
});

test("el resumen de deudas avanza con las cuotas ya pagadas", () => {
  const debt = debtOverview(defaultItems(), "2026-11");
  assert.equal(debt.credits[0].currentInstallment, 4);
  assert.equal(debt.remainingBalance, 223_000 * 9);
  assert.equal(Math.round(debt.progress), 25);
});

test("un abono extra adelanta la fecha de quedar libre de deudas", () => {
  const debt = debtOverview(defaultItems(), "2026-08");
  const baseline = simulateDebtPayoff(debt.credits, "2026-08", 0);
  const withExtra = simulateDebtPayoff(debt.credits, "2026-08", 200_000);
  assert.equal(baseline.months, 12);
  assert.equal(baseline.monthsSaved, 0);
  assert.ok(withExtra.months < baseline.months);
  assert.equal(withExtra.monthsSaved, baseline.months - withExtra.months);
  assert.equal(withExtra.payoff[0].name, "Crédito de $38.000");
});

test("la estrategia de mayor cuota ataca primero el crédito más grande", () => {
  const debt = debtOverview(defaultItems(), "2026-08");
  const simulation = simulateDebtPayoff(debt.credits, "2026-08", 300_000, "highest-payment");
  assert.equal(simulation.payoff[0].name, "Crédito de $140.000");
});

test("sin créditos la simulación no proyecta meses", () => {
  const simulation = simulateDebtPayoff([], "2026-08", 100_000);
  assert.deepEqual(simulation.payoff, []);
  assert.equal(simulation.months, 0);
});

test("estima cuándo se alcanza una meta de ahorro", () => {
  const goal = goalProgress({ name: "Viaje", target: 1_200_000, saved: 200_000, monthly: 250_000 }, "2026-08");
  assert.equal(goal.missing, 1_000_000);
  assert.equal(goal.monthsLeft, 4);
  assert.equal(goal.estimated, "2026-11");
  assert.equal(Math.round(goal.progress), 17);
});

test("avisa cuando una meta con fecha límite va atrasada", () => {
  const goal = goalProgress({ name: "Curso", target: 900_000, saved: 100_000, monthly: 100_000, deadline: "2026-11-01" }, "2026-08");
  assert.equal(goal.deadlineMonths, 4);
  assert.equal(goal.neededMonthly, 200_000);
  assert.equal(goal.onTrack, false);
});

test("califica la salud financiera entre 0 y 100", () => {
  const strong = financialHealth({
    income: 4_000_000,
    committed: 1_500_000,
    creditPayments: 300_000,
    openingBalance: 6_000_000,
    savingsTarget: 900_000,
    safeToSpend: 2_500_000,
  });
  const weak = financialHealth({
    income: 1_000_000,
    committed: 1_200_000,
    creditPayments: 600_000,
    openingBalance: 0,
    savingsTarget: 0,
    safeToSpend: -200_000,
  });
  assert.equal(strong.score, 100);
  assert.equal(strong.level, "Excelente");
  assert.ok(weak.score < 30);
  assert.equal(weak.level, "Frágil");
  assert.equal(weak.parts.length, 4);
});

test("las metas ya ahorradas cuentan como colchón de emergencia", () => {
  const base = { income: 2_000_000, committed: 1_000_000, creditPayments: 0, openingBalance: 0, savingsTarget: 0, safeToSpend: 500_000 };
  const withoutGoals = financialHealth(base);
  const withGoals = financialHealth({ ...base, goals: [{ saved: 3_000_000 }] });
  assert.equal(withoutGoals.parts.find((part) => part.id === "colchon").score, 0);
  assert.equal(withGoals.parts.find((part) => part.id === "colchon").score, 25);
});

test("los avisos priorizan los pagos vencidos y los meses en rojo", () => {
  const today = new Date(2026, 7, 25);
  const occurrences = buildMonthOccurrences(defaultItems(), "2026-08");
  const summary = calculateSummary(occurrences, today);
  const alerts = buildAlerts({
    occurrences,
    summary,
    projection: projectMonths(defaultItems(), "2026-08", 12),
    debt: debtOverview(defaultItems(), "2026-08"),
    visibleMonth: "2026-08",
    today,
  });
  assert.equal(alerts[0].level, "danger");
  assert.ok(alerts[0].title.includes("pagos sin marcar"));
  assert.ok(alerts.length <= 6);
});

test("avisa cuando la meta de ahorro no cabe en algún mes proyectado", () => {
  const today = new Date(2026, 7, 1);
  const alerts = buildAlerts({
    occurrences: [],
    summary: calculateSummary([], today, 500_000),
    projection: projectMonths(defaultItems(), "2026-08", 12, [], {
      savingsPlan: { amount: 800_000, frequency: "monthly", startMonth: "2026-08" },
    }),
    debt: { count: 0, credits: [] },
    visibleMonth: "2026-08",
    today,
    lastBackupAt: today.toISOString(),
  });
  const savingsAlert = alerts.find((alert) => alert.title.includes("meta de ahorro"));
  assert.ok(savingsAlert);
  assert.equal(savingsAlert.level, "warn");
  assert.equal(savingsAlert.action, "projection");
});

test("sin pendientes el aviso principal es tranquilizador", () => {
  const today = new Date(2026, 7, 1);
  const alerts = buildAlerts({
    occurrences: [],
    summary: calculateSummary([], today, 500_000),
    projection: projectMonths([], "2026-08", 12),
    debt: { count: 0, credits: [] },
    visibleMonth: "2026-08",
    today,
    lastBackupAt: today.toISOString(),
  });
  assert.equal(alerts[0].level, "good");
});
