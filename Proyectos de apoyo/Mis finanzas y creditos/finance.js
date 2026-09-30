import { isBusinessDay } from "./holidays.js";

export const DATA_VERSION = 8;
export const DEFAULT_PACING = Object.freeze({
  spendingDays: [0, 1, 2, 3, 4, 5, 6],
  weekStart: 1,
  weekEnd: 0,
});

export const DEFAULT_SETTINGS = Object.freeze({
  skipHolidays: true,
  emergencyMonths: 3,
  savingsGoalRate: 20,
});

export const COP = new Intl.NumberFormat("es-CO", {
  style: "currency",
  currency: "COP",
  maximumFractionDigits: 0,
});

export const FREQUENCIES = Object.freeze([
  { id: "once", label: "Solo una vez", months: 0 },
  { id: "weekly", label: "Cada semana", months: 0 },
  { id: "biweekly", label: "Cada 15 días", months: 0 },
  { id: "monthly", label: "Cada mes", months: 1 },
  { id: "bimonthly", label: "Cada 2 meses", months: 2 },
  { id: "quarterly", label: "Cada 3 meses", months: 3 },
  { id: "semiannual", label: "Cada 6 meses", months: 6 },
  { id: "yearly", label: "Cada año", months: 12 },
]);

const MONTH_INTERVALS = Object.fromEntries(FREQUENCIES.filter((item) => item.months).map((item) => [item.id, item.months]));
const REPEATING_MONTHLY = new Set(Object.keys(MONTH_INTERVALS));

export const CATEGORIES = Object.freeze([
  { id: "vivienda", label: "Vivienda", icon: "🏠", scope: "expense", color: "#4a6cf0" },
  { id: "mercado", label: "Mercado", icon: "🛒", scope: "expense", color: "#0f9d76" },
  { id: "transporte", label: "Transporte", icon: "🚌", scope: "expense", color: "#e0842c" },
  { id: "servicios", label: "Servicios", icon: "💡", scope: "expense", color: "#d0b021" },
  { id: "salud", label: "Salud", icon: "🩺", scope: "expense", color: "#e05c8a" },
  { id: "educacion", label: "Educación", icon: "🎓", scope: "expense", color: "#7551b6" },
  { id: "ocio", label: "Ocio", icon: "🎉", scope: "expense", color: "#ef6a5b" },
  { id: "suscripciones", label: "Suscripciones", icon: "📺", scope: "expense", color: "#9b59d0" },
  { id: "deudas", label: "Créditos y deudas", icon: "🏦", scope: "expense", color: "#c64d43" },
  { id: "familia", label: "Familia", icon: "🧡", scope: "expense", color: "#d4694f" },
  { id: "personal", label: "Personal", icon: "👕", scope: "expense", color: "#3fa3c9" },
  { id: "ahorro", label: "Ahorro", icon: "🪙", scope: "expense", color: "#12a37c" },
  { id: "otros", label: "Otros gastos", icon: "🔖", scope: "expense", color: "#7c8497" },
  { id: "salario", label: "Salario", icon: "💼", scope: "income", color: "#0f9d76" },
  { id: "extra", label: "Ingreso extra", icon: "✨", scope: "income", color: "#4a6cf0" },
  { id: "ventas", label: "Ventas o negocio", icon: "🧾", scope: "income", color: "#7551b6" },
  { id: "otros-ingreso", label: "Otros ingresos", icon: "💰", scope: "income", color: "#7c8497" },
]);

const CATEGORY_MAP = new Map(CATEGORIES.map((category) => [category.id, category]));

export function categoriesFor(type) {
  return CATEGORIES.filter((category) => category.scope === (type === "income" ? "income" : "expense"));
}

export function categoryInfo(id, type = "expense") {
  return CATEGORY_MAP.get(id) || (type === "income"
    ? CATEGORY_MAP.get("otros-ingreso")
    : CATEGORY_MAP.get("otros"));
}

export function defaultCategory(item) {
  if (item.type === "income") return "otros-ingreso";
  if (item.kind === "credit") return "deudas";
  return "otros";
}

export function dateKey(date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

export function parseDate(value) {
  const [year, month, day] = String(value).split("-").map(Number);
  return new Date(year, month - 1, day);
}

export function monthKey(date) {
  return dateKey(date).slice(0, 7);
}

export function startOfMonth(value) {
  const date = typeof value === "string" ? parseDate(`${value.slice(0, 7)}-01`) : value;
  return new Date(date.getFullYear(), date.getMonth(), 1);
}

export function endOfMonth(value) {
  const start = startOfMonth(value);
  return new Date(start.getFullYear(), start.getMonth() + 1, 0);
}

export function addMonths(value, amount) {
  const start = startOfMonth(value);
  return new Date(start.getFullYear(), start.getMonth() + amount, 1);
}

export function startOfDay(date = new Date()) {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate());
}

export function daysBetween(from, to) {
  return Math.round((startOfDay(to) - startOfDay(from)) / 86_400_000);
}

export function sameMonth(a, b) {
  return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth();
}

export function lastBusinessDay(year, monthIndex, skipHolidays = true) {
  const date = new Date(year, monthIndex + 1, 0);
  while (skipHolidays ? !isBusinessDay(date) : date.getDay() === 0 || date.getDay() === 6) {
    date.setDate(date.getDate() - 1);
  }
  return date;
}

export function occurrenceDate(item, monthDate, skipHolidays = true) {
  const year = monthDate.getFullYear();
  const month = monthDate.getMonth();
  if (item.schedule === "last-business-day") {
    return lastBusinessDay(year, month, skipHolidays);
  }
  const lastDay = new Date(year, month + 1, 0).getDate();
  return new Date(year, month, Math.min(Number(item.day) || 1, lastDay));
}

export function occurrenceDates(item, monthValue, skipHolidays = true) {
  const monthDate = startOfMonth(monthValue);
  const start = parseDate(item.startDate);
  const frequency = item.frequency || "once";

  if (frequency === "once") {
    return sameMonth(start, monthDate) ? [start] : [];
  }

  if (frequency === "weekly" || frequency === "biweekly") {
    const step = frequency === "weekly" ? 7 : 14;
    const monthStart = monthDate;
    const monthEnd = endOfMonth(monthDate);
    const cursor = new Date(start);
    if (cursor < monthStart) {
      const jumps = Math.floor(daysBetween(cursor, monthStart) / step);
      cursor.setDate(cursor.getDate() + jumps * step);
      while (cursor < monthStart) cursor.setDate(cursor.getDate() + step);
    }
    const dates = [];
    while (cursor <= monthEnd) {
      dates.push(new Date(cursor));
      cursor.setDate(cursor.getDate() + step);
    }
    return dates;
  }

  const interval = MONTH_INTERVALS[frequency] || 1;
  const distance = monthsBetween(startOfMonth(start), monthDate);
  if (distance < 0 || distance % interval !== 0) return [];
  return [occurrenceDate(item, monthDate, skipHolidays)];
}

export function monthsBetween(from, to) {
  return (to.getFullYear() - from.getFullYear()) * 12 + to.getMonth() - from.getMonth();
}

export function creditEndMonth(item) {
  if (item.endDate) return monthKey(startOfMonth(item.endDate));
  const start = startOfMonth(item.startDate);
  const startingInstallment = Math.max(1, Number(item.currentInstallment) || 1);
  const totalInstallments = Math.max(startingInstallment, Number(item.installments) || startingInstallment);
  return monthKey(new Date(start.getFullYear(), start.getMonth() + totalInstallments - startingInstallment, 1));
}

export function installmentsThroughEnd(currentInstallment, currentMonth, endMonth) {
  const current = Math.max(1, Number(currentInstallment) || 1);
  const distance = monthsBetween(startOfMonth(currentMonth), startOfMonth(endMonth));
  return current + Math.max(0, distance);
}

export function isActiveInMonth(item, monthDate) {
  const start = parseDate(item.startDate);
  if (item.frequency === "once") return sameMonth(start, monthDate);
  const monthDistance = monthsBetween(start, monthDate);
  if (monthDistance < 0) return false;
  if (item.kind === "credit" && Number(item.installments) > 0) {
    const startingInstallment = Math.max(1, Number(item.currentInstallment) || 1);
    const withinInstallments = startingInstallment + monthDistance <= Number(item.installments);
    const withinEndMonth = !item.endDate || monthsBetween(monthDate, startOfMonth(item.endDate)) >= 0;
    return withinInstallments && withinEndMonth;
  }
  if (item.endDate) {
    const end = parseDate(item.endDate);
    return monthsBetween(monthDate, end) >= 0;
  }
  return true;
}

export function installmentNumber(item, monthDate) {
  if (item.kind !== "credit") return null;
  return Math.max(1, Number(item.currentInstallment) || 1)
    + monthsBetween(parseDate(item.startDate), monthDate);
}

export function buildMonthOccurrences(items, monthValue, options = {}) {
  const skipHolidays = options.skipHolidays !== false;
  const monthDate = startOfMonth(monthValue);
  const occurrences = [];

  for (const item of items) {
    if (!isActiveInMonth(item, monthDate)) continue;
    for (const date of occurrenceDates(item, monthDate, skipHolidays)) {
      const paymentKey = `${item.id}:${dateKey(date)}`;
      occurrences.push({
        ...item,
        date,
        dateKey: dateKey(date),
        paymentKey,
        paid: Boolean(item.payments?.[paymentKey]),
        installment: installmentNumber(item, monthDate),
        category: item.category || defaultCategory(item),
      });
    }
  }

  return occurrences.sort((a, b) => a.date - b.date || (a.type === "income" ? -1 : 1));
}

export function spendingInMonth(spending = [], monthValue) {
  const prefix = monthKey(startOfMonth(monthValue));
  return spending
    .filter((entry) => String(entry.date).startsWith(prefix))
    .sort((a, b) => (a.date < b.date ? 1 : a.date > b.date ? -1 : 0));
}

export function totalSpending(entries = []) {
  return entries.reduce((sum, entry) => sum + Math.max(0, Number(entry.amount) || 0), 0);
}

export function spendingOnDate(spending = [], date) {
  const key = typeof date === "string" ? date : dateKey(date);
  return totalSpending(spending.filter((entry) => entry.date === key));
}

export function calculateSummary(occurrences, today = new Date(), openingBalance = 0, variableSpent = 0) {
  const income = occurrences
    .filter((item) => item.type === "income")
    .reduce((sum, item) => sum + Number(item.amount), 0);
  const committed = occurrences
    .filter((item) => item.type === "expense")
    .reduce((sum, item) => sum + Number(item.amount), 0);
  const paidExpenses = occurrences
    .filter((item) => item.type === "expense" && item.paid)
    .reduce((sum, item) => sum + Number(item.amount), 0);
  const receivedIncome = occurrences
    .filter((item) => item.type === "income" && item.paid)
    .reduce((sum, item) => sum + Number(item.amount), 0);
  const creditPayments = occurrences
    .filter((item) => item.type === "expense" && item.kind === "credit")
    .reduce((sum, item) => sum + Number(item.amount), 0);
  const spent = Math.max(0, Number(variableSpent) || 0);
  const pendingExpenses = committed - paidExpenses;
  const currentCash = openingBalance + receivedIncome - paidExpenses - spent;
  const safeToSpend = openingBalance + income - committed - spent;
  const reference = startOfDay(today);
  const upcoming = occurrences
    .filter((item) => item.type === "expense" && !item.paid && item.date >= reference)
    .reduce((sum, item) => sum + Number(item.amount), 0);
  const overdue = occurrences
    .filter((item) => item.type === "expense" && !item.paid && item.date < reference)
    .reduce((sum, item) => sum + Number(item.amount), 0);

  return {
    openingBalance,
    income,
    committed,
    creditPayments,
    paidExpenses,
    receivedIncome,
    pendingExpenses,
    variableSpent: spent,
    currentCash,
    safeToSpend,
    upcoming,
    overdue,
  };
}

/* ------------------------------------------------------------------ ahorro */

export const DEFAULT_SAVINGS_PLAN = Object.freeze({ amount: 0, frequency: "monthly", startMonth: "" });

const FREQUENCY_IDS = new Set(FREQUENCIES.map((frequency) => frequency.id));

export function normalizeSavingsPlan(plan = {}, fallbackMonth = "") {
  const startMonth = /^\d{4}-\d{2}$/.test(String(plan?.startMonth))
    ? String(plan.startMonth)
    : /^\d{4}-\d{2}/.test(String(fallbackMonth)) ? String(fallbackMonth).slice(0, 7) : "";
  return {
    amount: Math.max(0, Number(plan?.amount) || 0),
    frequency: FREQUENCY_IDS.has(plan?.frequency) ? plan.frequency : DEFAULT_SAVINGS_PLAN.frequency,
    startMonth,
  };
}

/** El plan de ahorro reutiliza el mismo calendario que los movimientos recurrentes. */
function savingsItem(plan) {
  return { startDate: `${plan.startMonth}-01`, frequency: plan.frequency, schedule: "day", day: 1 };
}

/** Cuántas veces cae el ahorro dentro de un mes: 0 si el plan todavía no aplica ahí. */
export function savingsOccurrences(plan, monthValue, skipHolidays = true) {
  const normalized = normalizeSavingsPlan(plan);
  if (!normalized.startMonth) return 0;
  const monthDate = startOfMonth(monthValue);
  const item = savingsItem(normalized);
  if (!isActiveInMonth(item, monthDate)) return 0;
  return occurrenceDates(item, monthDate, skipHolidays).length;
}

/** Lo que se apartaría en ese mes concreto según la frecuencia elegida. */
export function savingsForMonth(plan, monthValue, skipHolidays = true) {
  const normalized = normalizeSavingsPlan(plan);
  if (!normalized.amount) return 0;
  return normalized.amount * savingsOccurrences(normalized, monthValue, skipHolidays);
}

/** Veces que cabría el ahorro en un mes si el plan arrancara justo ahí. Nunca menos de una. */
export function savingsSlotsInMonth(frequency, monthValue, skipHolidays = true) {
  const monthDate = startOfMonth(monthValue);
  const plan = { amount: 1, frequency, startMonth: monthKey(monthDate) };
  return Math.max(1, savingsOccurrences(plan, monthDate, skipHolidays));
}

export function splitAvailableMoney(safeToSpend, savingsTarget = 0) {
  const base = Math.max(0, Number(safeToSpend) || 0);
  const target = Math.max(0, Number(savingsTarget) || 0);
  const saved = Math.min(base, target);
  const rate = base ? saved / base * 100 : 0;
  return { base, target, rate, saved, usable: base - saved };
}

export function displayedAvailableMoney(safeToSpend, savingsTarget = 0, deductSavings = false) {
  const available = Number(safeToSpend) || 0;
  if (!deductSavings) return available;
  return available - splitAvailableMoney(available, savingsTarget).saved;
}

export function normalizePacingPreferences(preferences = {}) {
  const requestedDays = Array.isArray(preferences.spendingDays)
    ? preferences.spendingDays.map(Number).filter((day) => Number.isInteger(day) && day >= 0 && day <= 6)
    : DEFAULT_PACING.spendingDays;
  const spendingDays = [...new Set(requestedDays)].sort((a, b) => a - b);
  const weekStart = Number.isInteger(Number(preferences.weekStart))
    ? Math.min(6, Math.max(0, Number(preferences.weekStart)))
    : DEFAULT_PACING.weekStart;
  const weekEnd = Number.isInteger(Number(preferences.weekEnd))
    ? Math.min(6, Math.max(0, Number(preferences.weekEnd)))
    : DEFAULT_PACING.weekEnd;
  return {
    spendingDays: spendingDays.length ? spendingDays : [...DEFAULT_PACING.spendingDays],
    weekStart,
    weekEnd,
  };
}

export function weekdaysInRange(weekStart, weekEnd) {
  const days = [];
  let cursor = weekStart;
  while (days.length < 7) {
    days.push(cursor);
    if (cursor === weekEnd) break;
    cursor = (cursor + 1) % 7;
  }
  return days;
}

function countSelectedDates(from, to, selectedDays) {
  let count = 0;
  const cursor = new Date(from.getFullYear(), from.getMonth(), from.getDate());
  while (cursor <= to) {
    if (selectedDays.includes(cursor.getDay())) count += 1;
    cursor.setDate(cursor.getDate() + 1);
  }
  return count;
}

export function dailyAllowance(safeToSpend, monthValue, today = new Date(), savingsTarget = 0, preferences = {}, spentToday = 0) {
  const monthStart = startOfMonth(monthValue);
  const monthEnd = endOfMonth(monthValue);
  const withinMonth = today >= monthStart && today <= monthEnd;
  const start = withinMonth ? startOfDay(today) : monthStart;
  const pacing = normalizePacingPreferences(preferences);
  const days = countSelectedDates(start, monthEnd, pacing.spendingDays);
  const allocation = splitAvailableMoney(safeToSpend, savingsTarget);
  const amount = days ? allocation.usable / days : 0;
  const weekDays = weekdaysInRange(pacing.weekStart, pacing.weekEnd)
    .filter((day) => pacing.spendingDays.includes(day));
  const alreadySpent = Math.max(0, Number(spentToday) || 0);
  const isSpendingDay = withinMonth && pacing.spendingDays.includes(today.getDay());
  const todayBudget = isSpendingDay && days ? (allocation.usable + alreadySpent) / days : 0;

  return {
    days,
    amount,
    weeklyDays: weekDays.length,
    weeklyAmount: Math.min(allocation.usable, amount * weekDays.length),
    pacing,
    isSpendingDay,
    spentToday: alreadySpent,
    todayBudget,
    todayLeft: todayBudget - alreadySpent,
    ...allocation,
  };
}

export function projectedBalance(occurrences, openingBalance = 0) {
  let balance = openingBalance;
  return occurrences.map((item) => {
    balance += item.type === "income" ? Number(item.amount) : -Number(item.amount);
    return { ...item, balance };
  });
}

export function firstTrackedMonth(items) {
  const validStarts = items
    .map((item) => item.startDate)
    .filter(Boolean)
    .map((value) => startOfMonth(value));
  if (!validStarts.length) return null;
  return new Date(Math.min(...validStarts.map((date) => date.getTime())));
}

export function calculateOpeningBalance(items, monthValue, spending = [], options = {}) {
  const targetMonth = startOfMonth(monthValue);
  let cursor = firstTrackedMonth(items);
  if (!cursor) return 0;
  let balance = 0;

  while (cursor < targetMonth) {
    const occurrences = buildMonthOccurrences(items, cursor, options);
    balance = projectedBalance(occurrences, balance).at(-1)?.balance ?? balance;
    balance -= totalSpending(spendingInMonth(spending, cursor));
    cursor = new Date(cursor.getFullYear(), cursor.getMonth() + 1, 1);
  }

  return balance;
}

export function normalizeSpending(entries = []) {
  return entries
    .filter((entry) => entry && entry.date && Number(entry.amount) > 0)
    .map((entry) => ({
      id: entry.id || `spend-${Math.random().toString(36).slice(2, 10)}`,
      date: String(entry.date).slice(0, 10),
      amount: Math.max(0, Number(entry.amount) || 0),
      category: CATEGORY_MAP.has(entry.category) ? entry.category : "otros",
      note: String(entry.note || "").slice(0, 80),
    }));
}

export function normalizeGoals(goals = []) {
  return goals
    .filter((goal) => goal && goal.name)
    .map((goal) => ({
      id: goal.id || `goal-${Math.random().toString(36).slice(2, 10)}`,
      name: String(goal.name).slice(0, 60),
      target: Math.max(0, Number(goal.target) || 0),
      saved: Math.max(0, Number(goal.saved) || 0),
      monthly: Math.max(0, Number(goal.monthly) || 0),
      deadline: goal.deadline || "",
      icon: goal.icon || "🎯",
    }));
}

export function normalizeSettings(settings = {}) {
  return {
    skipHolidays: settings.skipHolidays !== false,
    emergencyMonths: Math.min(12, Math.max(1, Number(settings.emergencyMonths) || DEFAULT_SETTINGS.emergencyMonths)),
    savingsGoalRate: Math.min(80, Math.max(0, Number(settings.savingsGoalRate) ?? DEFAULT_SETTINGS.savingsGoalRate)),
  };
}

export function migrateState(savedState) {
  if (!savedState?.items) return null;
  const previousVersion = Number(savedState.dataVersion) || 1;
  const { savingsRate: _legacySavingsRate, savingsTarget: _legacyTarget, ...currentState } = savedState;
  let items = savedState.items;

  if (previousVersion < 2) {
    items = items.map((item) => item.id === "base-1708"
      ? {
          ...item,
          name: "Saldo inicial de agosto",
          frequency: "once",
          schedule: "day",
          day: 1,
          startDate: "2026-08-01",
        }
      : item);
  }

  const anchorMonth = monthKey(startOfMonth(savedState.visibleMonth || items[0]?.startDate || new Date()));
  let savingsTarget = Math.max(0, Number(savedState.savingsTarget) || 0);
  if (previousVersion < 5 && !Object.hasOwn(savedState, "savingsTarget")) {
    const visibleMonth = startOfMonth(savedState.visibleMonth || items[0]?.startDate || new Date());
    const openingBalance = calculateOpeningBalance(items, visibleMonth);
    const summary = calculateSummary(buildMonthOccurrences(items, visibleMonth), new Date(), openingBalance);
    const legacyRate = Math.min(100, Math.max(0, Number(savedState.savingsRate) || 0));
    savingsTarget = Math.max(0, summary.safeToSpend) * legacyRate / 100;
  }

  if (previousVersion < 7) {
    items = items.map((item) => ({ ...item, category: item.category || defaultCategory(item) }));
  }

  // Hasta la versión 7 el ahorro era un monto suelto que se repetía en todos los meses.
  const savings = savedState.savings
    ? normalizeSavingsPlan(savedState.savings, anchorMonth)
    : normalizeSavingsPlan({ amount: savingsTarget, frequency: "monthly", startMonth: anchorMonth });

  return {
    ...currentState,
    items,
    savings,
    deductSavingsFromAvailable: Boolean(savedState.deductSavingsFromAvailable),
    pacing: normalizePacingPreferences(savedState.pacing),
    spending: normalizeSpending(savedState.spending),
    goals: normalizeGoals(savedState.goals),
    settings: normalizeSettings(savedState.settings),
    lastBackupAt: savedState.lastBackupAt || null,
    theme: savedState.theme === "dark" ? "dark" : "light",
    projectionView: savedState.projectionView === "free" ? "free" : "total",
    dataVersion: DATA_VERSION,
  };
}

export function defaultItems() {
  return [
    {
      id: "base-1708",
      name: "Saldo inicial de agosto",
      type: "income",
      kind: "income",
      amount: 1_708_000,
      frequency: "once",
      schedule: "day",
      day: 1,
      startDate: "2026-08-01",
      payments: {},
      category: "otros-ingreso",
      color: "blue",
    },
    {
      id: "extra-250",
      name: "Ingreso fin de mes",
      type: "income",
      kind: "income",
      amount: 250_000,
      frequency: "monthly",
      schedule: "last-business-day",
      day: 1,
      startDate: "2026-08-01",
      payments: {},
      category: "salario",
      color: "mint",
    },
    {
      id: "credito-140",
      name: "Crédito de $140.000",
      type: "expense",
      kind: "credit",
      amount: 140_000,
      frequency: "monthly",
      schedule: "day",
      day: 2,
      startDate: "2026-08-01",
      installments: 12,
      currentInstallment: 1,
      payments: {},
      category: "deudas",
      color: "coral",
    },
    {
      id: "credito-38",
      name: "Crédito de $38.000",
      type: "expense",
      kind: "credit",
      amount: 38_000,
      frequency: "monthly",
      schedule: "day",
      day: 22,
      startDate: "2026-08-01",
      installments: 12,
      currentInstallment: 1,
      payments: {},
      category: "deudas",
      color: "coral",
    },
    {
      id: "credito-45",
      name: "Crédito de $45.000",
      type: "expense",
      kind: "credit",
      amount: 45_000,
      frequency: "monthly",
      schedule: "day",
      day: 23,
      startDate: "2026-08-01",
      installments: 12,
      currentInstallment: 1,
      payments: {},
      category: "deudas",
      color: "coral",
    },
    {
      id: "suscripciones-140",
      name: "Suscripciones",
      type: "expense",
      kind: "expense",
      amount: 140_000,
      frequency: "monthly",
      schedule: "day",
      day: 20,
      startDate: "2026-08-01",
      payments: {},
      category: "suscripciones",
      color: "violet",
    },
  ];
}

export function defaultState() {
  return {
    items: defaultItems(),
    spending: [],
    goals: [],
    visibleMonth: "2026-08",
    savings: { ...DEFAULT_SAVINGS_PLAN, startMonth: "2026-08" },
    deductSavingsFromAvailable: false,
    pacing: { ...DEFAULT_PACING, spendingDays: [...DEFAULT_PACING.spendingDays] },
    settings: { ...DEFAULT_SETTINGS },
    lastBackupAt: null,
    theme: "light",
    projectionView: "total",
    dataVersion: DATA_VERSION,
  };
}

export { REPEATING_MONTHLY };
