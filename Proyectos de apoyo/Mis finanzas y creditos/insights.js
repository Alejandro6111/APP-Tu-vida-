import {
  addMonths,
  buildMonthOccurrences,
  calculateOpeningBalance,
  calculateSummary,
  categoryInfo,
  creditEndMonth,
  daysBetween,
  endOfMonth,
  firstTrackedMonth,
  isActiveInMonth,
  monthKey,
  monthsBetween,
  parseDate,
  savingsForMonth,
  spendingInMonth,
  startOfDay,
  startOfMonth,
  totalSpending,
} from "./finance.js";

const clamp = (value, min = 0, max = 1) => Math.min(max, Math.max(min, value));

/**
 * Proyecta el saldo de cierre de varios meses seguidos sin recalcular el histórico cada vez.
 * Con `options.savingsPlan` aparta lo que el usuario quiere guardar solo en los meses en que
 * su frecuencia lo indica: el ahorro acumulado nunca puede pasarse del saldo real de ese mes.
 */
export function projectMonths(items, startMonthValue, count = 12, spending = [], options = {}) {
  const start = startOfMonth(startMonthValue);
  const skipHolidays = options.skipHolidays !== false;
  let balance = calculateOpeningBalance(items, start, spending, options);
  let savedTotal = 0;
  const months = [];

  for (let index = 0; index < count; index += 1) {
    const monthDate = addMonths(start, index);
    const monthlySavings = savingsForMonth(options.savingsPlan, monthDate, skipHolidays);
    const occurrences = buildMonthOccurrences(items, monthDate, options);
    const income = occurrences.filter((item) => item.type === "income").reduce((sum, item) => sum + Number(item.amount), 0);
    const expenses = occurrences.filter((item) => item.type === "expense").reduce((sum, item) => sum + Number(item.amount), 0);
    const spent = totalSpending(spendingInMonth(spending, monthDate));
    const closing = balance + income - expenses - spent;
    const previousSaved = savedTotal;
    savedTotal = Math.max(0, Math.min(previousSaved + monthlySavings, closing));
    const saved = savedTotal - previousSaved;
    months.push({
      key: monthKey(monthDate),
      date: monthDate,
      opening: balance,
      income,
      expenses: expenses + spent,
      planned: expenses,
      spent,
      net: income - expenses - spent,
      closing,
      monthlySavings,
      saved,
      savedTotal,
      // Lo que no se alcanza a apartar de lo previsto, y lo que habría que sacar de lo ya guardado.
      savingsShort: Math.max(0, monthlySavings - Math.max(0, saved)),
      savingsDrop: Math.max(0, previousSaved - savedTotal),
      free: closing - savedTotal,
    });
    balance = closing;
  }

  return months;
}

export function compareWithPreviousMonth(items, monthValue, spending = [], options = {}) {
  const current = startOfMonth(monthValue);
  const previous = addMonths(current, -1);
  const build = (monthDate) => {
    const occurrences = buildMonthOccurrences(items, monthDate, options);
    const opening = calculateOpeningBalance(items, monthDate, spending, options);
    return calculateSummary(occurrences, endOfMonth(monthDate), opening, totalSpending(spendingInMonth(spending, monthDate)));
  };
  const now = build(current);
  const before = build(previous);
  const delta = (a, b) => ({ current: a, previous: b, diff: a - b, rate: b ? (a - b) / Math.abs(b) * 100 : null });

  return {
    month: monthKey(current),
    previousMonth: monthKey(previous),
    income: delta(now.income, before.income),
    expenses: delta(now.committed + now.variableSpent, before.committed + before.variableSpent),
    free: delta(now.safeToSpend, before.safeToSpend),
  };
}

export function categoryBreakdown(occurrences, spendingEntries = [], type = "expense") {
  const totals = new Map();
  const add = (categoryId, amount, bucket) => {
    const info = categoryInfo(categoryId, type);
    const current = totals.get(info.id) || { ...info, planned: 0, real: 0, total: 0, count: 0 };
    current[bucket] += amount;
    current.total += amount;
    current.count += 1;
    totals.set(info.id, current);
  };

  occurrences
    .filter((item) => item.type === type)
    .forEach((item) => add(item.category, Number(item.amount) || 0, "planned"));

  if (type === "expense") {
    spendingEntries.forEach((entry) => add(entry.category, Number(entry.amount) || 0, "real"));
  }

  const rows = [...totals.values()].sort((a, b) => b.total - a.total);
  const sum = rows.reduce((total, row) => total + row.total, 0);
  return rows.map((row) => ({ ...row, share: sum ? row.total / sum * 100 : 0 }));
}

export function debtOverview(items, monthValue) {
  const monthDate = startOfMonth(monthValue);
  const credits = items
    .filter((item) => item.kind === "credit" && isActiveInMonth(item, monthDate))
    .map((item) => {
      const endMonth = creditEndMonth(item);
      const remainingMonths = Math.max(0, monthsBetween(monthDate, startOfMonth(endMonth)) + 1);
      const total = Math.max(1, Number(item.installments) || 1);
      const currentInstallment = Math.max(1, Number(item.currentInstallment) || 1)
        + monthsBetween(parseDate(item.startDate), monthDate);
      const paidInstallments = Math.max(0, currentInstallment - 1);
      const installment = Number(item.amount) || 0;
      return {
        id: item.id,
        name: item.name,
        installment,
        total,
        currentInstallment,
        paidInstallments,
        remainingMonths,
        remainingBalance: installment * remainingMonths,
        paidBalance: installment * paidInstallments,
        originalBalance: installment * total,
        endMonth,
        progress: total ? Math.min(100, paidInstallments / total * 100) : 0,
      };
    })
    .sort((a, b) => a.remainingBalance - b.remainingBalance);

  const remainingBalance = credits.reduce((sum, credit) => sum + credit.remainingBalance, 0);
  const monthlyPayment = credits.reduce((sum, credit) => sum + credit.installment, 0);
  const paidBalance = credits.reduce((sum, credit) => sum + credit.paidBalance, 0);
  const originalBalance = credits.reduce((sum, credit) => sum + credit.originalBalance, 0);
  const longest = credits.reduce((max, credit) => Math.max(max, credit.remainingMonths), 0);

  return {
    credits,
    count: credits.length,
    remainingBalance,
    paidBalance,
    originalBalance,
    monthlyPayment,
    monthsToFreedom: longest,
    freeMonth: longest ? monthKey(addMonths(monthDate, longest - 1)) : null,
    progress: originalBalance ? paidBalance / originalBalance * 100 : 0,
  };
}

/**
 * Simula el pago de las deudas mes a mes. No incluye intereses: asume que cada
 * abono adicional reduce directamente el saldo pendiente.
 */
export function simulateDebtPayoff(credits, monthValue, extra = 0, strategy = "snowball") {
  const monthDate = startOfMonth(monthValue);
  const additional = Math.max(0, Number(extra) || 0);
  const debts = credits
    .filter((credit) => credit.remainingBalance > 0)
    .map((credit) => ({ ...credit, balance: credit.remainingBalance }));

  if (!debts.length) {
    return { months: 0, monthsSaved: 0, baselineMonths: 0, payoff: [], freeMonth: null, extra: additional, strategy };
  }

  const baselineMonths = debts.reduce((max, debt) => Math.max(max, debt.remainingMonths), 0);
  const budget = debts.reduce((sum, debt) => sum + debt.installment, 0) + additional;
  const order = (list) => [...list].sort((a, b) => strategy === "highest-payment"
    ? b.installment - a.installment || a.balance - b.balance
    : a.balance - b.balance || b.installment - a.installment);

  let active = debts;
  const payoff = [];
  let month = 0;

  while (active.length && month < 600) {
    month += 1;
    let leftover = budget;
    for (const debt of active) {
      const payment = Math.min(debt.balance, debt.installment);
      debt.balance -= payment;
      leftover -= payment;
    }
    for (const debt of order(active.filter((candidate) => candidate.balance > 0))) {
      if (leftover <= 0) break;
      const payment = Math.min(debt.balance, leftover);
      debt.balance -= payment;
      leftover -= payment;
    }
    for (const debt of active) {
      if (debt.balance <= 0.5) {
        payoff.push({
          id: debt.id,
          name: debt.name,
          month: monthKey(addMonths(monthDate, month - 1)),
          monthsAhead: Math.max(0, debt.remainingMonths - month),
        });
      }
    }
    active = active.filter((debt) => debt.balance > 0.5);
  }

  return {
    months: month,
    baselineMonths,
    monthsSaved: Math.max(0, baselineMonths - month),
    payoff,
    freeMonth: monthKey(addMonths(monthDate, Math.max(0, month - 1))),
    extra: additional,
    strategy,
  };
}

export function goalProgress(goal, monthValue = new Date()) {
  const target = Math.max(0, Number(goal.target) || 0);
  const saved = Math.max(0, Number(goal.saved) || 0);
  const monthly = Math.max(0, Number(goal.monthly) || 0);
  const missing = Math.max(0, target - saved);
  const monthsLeft = monthly ? Math.ceil(missing / monthly) : null;
  const estimated = monthsLeft === null ? null : monthKey(addMonths(monthValue, Math.max(0, monthsLeft - 1)));
  const deadlineMonths = goal.deadline ? monthsBetween(startOfMonth(monthValue), startOfMonth(goal.deadline)) + 1 : null;
  const neededMonthly = deadlineMonths && deadlineMonths > 0 ? missing / deadlineMonths : null;

  return {
    ...goal,
    target,
    saved,
    monthly,
    missing,
    monthsLeft,
    estimated,
    deadlineMonths,
    neededMonthly,
    complete: target > 0 && saved >= target,
    onTrack: neededMonthly === null ? null : monthly >= neededMonthly,
    progress: target ? Math.min(100, saved / target * 100) : 0,
  };
}

export function financialHealth({ income = 0, committed = 0, creditPayments = 0, openingBalance = 0, savingsTarget = 0, safeToSpend = 0, goals = [] }, settings = {}) {
  const emergencyMonths = Math.max(1, Number(settings.emergencyMonths) || 3);
  const savingsGoalRate = Math.max(1, Number(settings.savingsGoalRate) || 20) / 100;
  const monthlyCost = committed || 1;
  const savedTotal = goals.reduce((sum, goal) => sum + Math.max(0, Number(goal.saved) || 0), 0);
  const cushion = savedTotal + Math.max(0, openingBalance);

  const parts = [];

  if (income > 0) {
    const rate = savingsTarget / income;
    parts.push({
      id: "ahorro",
      label: "Ahorro del mes",
      max: 30,
      score: Math.round(clamp(rate / savingsGoalRate) * 30),
      value: `${Math.round(rate * 100)}%`,
      detail: `Estás separando el ${Math.round(rate * 100)}% de tus ingresos.`,
      tip: rate >= savingsGoalRate
        ? "Vas muy bien: mantén este ritmo y llévalo a una meta concreta."
        : `Subir el ahorro al ${Math.round(savingsGoalRate * 100)}% te daría más margen.`,
    });
  } else {
    parts.push({
      id: "ahorro",
      label: "Ahorro del mes",
      max: 30,
      score: 0,
      value: "—",
      detail: "Aún no hay ingresos registrados en este mes.",
      tip: "Registra tus ingresos para medir tu capacidad de ahorro.",
    });
  }

  const debtRatio = income > 0 ? creditPayments / income : creditPayments > 0 ? 1 : 0;
  parts.push({
    id: "deuda",
    label: "Carga de cuotas",
    max: 25,
    score: Math.round(clamp((0.45 - debtRatio) / 0.35) * 25),
    value: `${Math.round(debtRatio * 100)}%`,
    detail: `Tus cuotas se llevan el ${Math.round(debtRatio * 100)}% de lo que entra.`,
    tip: debtRatio <= 0.2
      ? "Tu nivel de deuda es cómodo."
      : debtRatio <= 0.35
        ? "Evita nuevos créditos hasta bajar de 20%."
        : "Prioriza terminar la deuda más pequeña para liberar cuota.",
  });

  const cushionMonths = cushion / monthlyCost;
  parts.push({
    id: "colchon",
    label: "Colchón de emergencia",
    max: 25,
    score: Math.round(clamp(cushionMonths / emergencyMonths) * 25),
    value: `${cushionMonths.toFixed(1)} meses`,
    detail: `Con lo que tienes cubrirías ${cushionMonths.toFixed(1)} meses de compromisos.`,
    tip: cushionMonths >= emergencyMonths
      ? "Tienes un colchón sólido para imprevistos."
      : `Apunta a ${emergencyMonths} meses de gastos guardados.`,
  });

  const margin = income > 0 ? safeToSpend / income : 0;
  parts.push({
    id: "margen",
    label: "Margen del mes",
    max: 20,
    score: Math.round(clamp(margin / 0.3) * 20),
    value: `${Math.round(margin * 100)}%`,
    detail: safeToSpend >= 0
      ? `Te queda libre el ${Math.round(margin * 100)}% de lo que entra.`
      : "Este mes cierra en rojo.",
    tip: safeToSpend < 0
      ? "Revisa qué gasto puedes mover al próximo mes."
      : margin >= 0.3
        ? "Buen margen: parte de eso puede volverse ahorro."
        : "Un margen del 30% te deja respirar ante imprevistos.",
  });

  const score = parts.reduce((sum, part) => sum + part.score, 0);
  const level = score >= 80 ? "Excelente" : score >= 60 ? "Sólida" : score >= 40 ? "En camino" : "Frágil";
  const headline = score >= 80
    ? "Tus finanzas están en muy buen punto."
    : score >= 60
      ? "Vas bien, con espacio para afinar."
      : score >= 40
        ? "Hay bases, falta reforzar el colchón."
        : "Conviene ordenar deudas y ahorro.";

  return { score, level, headline, parts };
}

export function buildAlerts({
  occurrences = [],
  summary,
  allowance,
  projection = [],
  debt,
  goals = [],
  spending = [],
  visibleMonth,
  today = new Date(),
  lastBackupAt = null,
  settings = {},
} = {}) {
  const alerts = [];
  const reference = startOfDay(today);
  const inVisibleMonth = startOfMonth(visibleMonth).getTime() === startOfMonth(reference).getTime();

  const overdue = occurrences.filter((item) => item.type === "expense" && !item.paid && item.date < reference);
  if (inVisibleMonth && overdue.length) {
    alerts.push({
      level: "danger",
      icon: "⏰",
      title: `${overdue.length} pago${overdue.length > 1 ? "s" : ""} sin marcar`,
      detail: `${overdue.map((item) => item.name).slice(0, 3).join(", ")} ya pasó de fecha. Márcalo si ya lo hiciste.`,
      action: "movements",
    });
  }

  const soon = occurrences.filter((item) => item.type === "expense" && !item.paid
    && item.date >= reference && daysBetween(reference, item.date) <= 7);
  if (inVisibleMonth && soon.length) {
    const total = soon.reduce((sum, item) => sum + Number(item.amount), 0);
    alerts.push({
      level: "warn",
      icon: "📅",
      title: `Esta semana salen ${soon.length} pago${soon.length > 1 ? "s" : ""}`,
      detail: `Suman ${Math.round(total).toLocaleString("es-CO")} pesos. El próximo es ${soon[0].name} el ${soon[0].date.getDate()}.`,
      action: "calendar",
    });
  }

  if (summary && summary.safeToSpend < 0) {
    alerts.push({
      level: "danger",
      icon: "🚨",
      title: "Este mes no alcanza",
      detail: `Faltan ${Math.abs(Math.round(summary.safeToSpend)).toLocaleString("es-CO")} pesos para cubrir todo lo previsto.`,
      action: "movements",
    });
  }

  const firstNegative = projection.find((month) => month.closing < 0);
  if (firstNegative && (!summary || summary.safeToSpend >= 0)) {
    alerts.push({
      level: "warn",
      icon: "📉",
      title: "Un mes futuro cierra en rojo",
      detail: `Según lo registrado, ${firstNegative.key} terminaría con saldo negativo.`,
      action: "projection",
    });
  }

  const firstShort = projection.find((month) => month.savingsShort > 0);
  const firstDrop = projection.find((month) => month.savingsDrop > 0);
  if (firstShort && !firstNegative) {
    alerts.push({
      level: "warn",
      icon: "🪙",
      title: "Tu meta de ahorro no cabe todos los meses",
      detail: `En ${firstShort.key} solo podrías guardar ${Math.round(Math.max(0, firstShort.saved)).toLocaleString("es-CO")} de los ${Math.round(firstShort.monthlySavings).toLocaleString("es-CO")} que te propusiste.`,
      action: "projection",
    });
  } else if (firstDrop && !firstNegative) {
    alerts.push({
      level: "warn",
      icon: "🪙",
      title: "Tendrías que tocar lo que ya guardaste",
      detail: `En ${firstDrop.key} el saldo baja y sacarías ${Math.round(firstDrop.savingsDrop).toLocaleString("es-CO")} de tu ahorro.`,
      action: "projection",
    });
  }

  if (allowance?.isSpendingDay && allowance.todayLeft < 0) {
    alerts.push({
      level: "warn",
      icon: "💸",
      title: "Hoy te pasaste del ritmo",
      detail: `Gastaste ${Math.round(allowance.spentToday).toLocaleString("es-CO")} y tu ritmo del día era ${Math.round(allowance.todayBudget).toLocaleString("es-CO")}.`,
      action: "spending",
    });
  }

  if (debt?.count && summary?.income > 0 && debt.monthlyPayment / summary.income > 0.35) {
    alerts.push({
      level: "warn",
      icon: "🏦",
      title: "Tus cuotas pesan mucho",
      detail: `Se llevan el ${Math.round(debt.monthlyPayment / summary.income * 100)}% de tus ingresos. Evita nuevos créditos.`,
      action: "debts",
    });
  }

  const endingCredits = debt?.credits.filter((credit) => credit.remainingMonths === 1) || [];
  endingCredits.forEach((credit) => {
    alerts.push({
      level: "good",
      icon: "🎉",
      title: `${credit.name} termina este mes`,
      detail: `Después de esta cuota liberas ${Math.round(credit.installment).toLocaleString("es-CO")} pesos cada mes.`,
      action: "debts",
    });
  });

  const behindGoals = goals.map((goal) => goalProgress(goal, visibleMonth))
    .filter((goal) => goal.onTrack === false && !goal.complete);
  if (behindGoals.length) {
    alerts.push({
      level: "warn",
      icon: "🎯",
      title: `${behindGoals.length} meta${behindGoals.length > 1 ? "s" : ""} va${behindGoals.length > 1 ? "n" : ""} atrasada${behindGoals.length > 1 ? "s" : ""}`,
      detail: `Para "${behindGoals[0].name}" necesitarías ${Math.round(behindGoals[0].neededMonthly).toLocaleString("es-CO")} al mes.`,
      action: "goals",
    });
  }

  if (inVisibleMonth) {
    const lastEntry = [...spending].sort((a, b) => (a.date < b.date ? 1 : -1))[0];
    const silence = lastEntry ? daysBetween(parseDate(lastEntry.date), reference) : null;
    if (!lastEntry) {
      alerts.push({
        level: "info",
        icon: "📝",
        title: "Aún no registras gastos del día a día",
        detail: "Anotar lo que gastas es lo que hace que el ritmo diario funcione.",
        action: "spending",
      });
    } else if (silence >= 4) {
      alerts.push({
        level: "info",
        icon: "📝",
        title: `Llevas ${silence} días sin anotar gastos`,
        detail: "Registra lo del día para que el ritmo diario siga siendo real.",
        action: "spending",
      });
    }
  }

  const backupAge = lastBackupAt ? daysBetween(new Date(lastBackupAt), reference) : null;
  if (backupAge === null || backupAge > 30) {
    alerts.push({
      level: "info",
      icon: "💾",
      title: backupAge === null ? "No has guardado una copia" : `Tu última copia tiene ${backupAge} días`,
      detail: "Los datos viven solo en este navegador. Descarga una copia de seguridad.",
      action: "backup",
    });
  }

  if (!alerts.some((alert) => alert.level === "danger" || alert.level === "warn")) {
    alerts.push({
      level: "good",
      icon: "✅",
      title: "Todo en orden por ahora",
      detail: "No hay pagos vencidos ni meses en rojo a la vista.",
    });
  }

  const weight = { danger: 0, warn: 1, good: 2, info: 3 };
  return alerts.sort((a, b) => weight[a.level] - weight[b.level]).slice(0, 6);
}

export function monthlyAverages(items, monthValue, months = 6, spending = [], options = {}) {
  const end = startOfMonth(monthValue);
  const first = firstTrackedMonth(items) || end;
  const list = [];
  for (let index = months; index >= 1; index -= 1) {
    const monthDate = addMonths(end, -index);
    if (monthDate < first) continue;
    const occurrences = buildMonthOccurrences(items, monthDate, options);
    const expenses = occurrences.filter((item) => item.type === "expense").reduce((sum, item) => sum + Number(item.amount), 0);
    const income = occurrences.filter((item) => item.type === "income").reduce((sum, item) => sum + Number(item.amount), 0);
    list.push({ income, expenses: expenses + totalSpending(spendingInMonth(spending, monthDate)) });
  }
  if (!list.length) return { months: 0, income: 0, expenses: 0 };
  return {
    months: list.length,
    income: list.reduce((sum, row) => sum + row.income, 0) / list.length,
    expenses: list.reduce((sum, row) => sum + row.expenses, 0) / list.length,
  };
}
