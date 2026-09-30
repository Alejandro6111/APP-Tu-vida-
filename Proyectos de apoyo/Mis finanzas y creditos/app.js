import {
  CATEGORIES,
  FREQUENCIES,
  addMonths,
  buildMonthOccurrences,
  calculateOpeningBalance,
  calculateSummary,
  categoriesFor,
  creditEndMonth,
  dailyAllowance,
  dateKey,
  defaultCategory,
  defaultState,
  displayedAvailableMoney,
  migrateState,
  monthKey,
  monthsBetween,
  normalizeSettings,
  parseDate,
  projectedBalance,
  savingsForMonth,
  savingsOccurrences,
  savingsSlotsInMonth,
  spendingInMonth,
  spendingOnDate,
  startOfMonth,
  totalSpending,
} from "./finance.js";
import {
  buildAlerts,
  categoryBreakdown,
  compareWithPreviousMonth,
  debtOverview,
  financialHealth,
  goalProgress,
  projectMonths,
  simulateDebtPayoff,
} from "./insights.js";
import {
  dayNames,
  formatMoney,
  frequencyLabel,
  monthLabel,
  monthKeyLabel,
  monthNames,
  plural,
} from "./format.js";
import {
  renderAlerts,
  renderCalendar,
  renderCategories,
  renderComparison,
  renderDebts,
  renderFlow,
  renderGoals,
  renderHealth,
  renderHolidayNote,
  renderMonthSummaryLine,
  renderMovements,
  renderPayoffResult,
  renderProjection,
  renderProjectionNotes,
  renderSpendingLog,
} from "./views.js";

const STORAGE_KEY = "mi-plata-clara-v1";
const APP_VERSION = "2.2.0";

const $ = (selector) => document.querySelector(selector);
const $$ = (selector) => [...document.querySelectorAll(selector)];

let state = loadState();
let visibleMonth = startOfMonth(state.visibleMonth || "2026-08");
let activeFilter = "all";
let searchTerm = "";
let toastTimer;

function loadState() {
  try {
    const saved = JSON.parse(localStorage.getItem(STORAGE_KEY));
    const migrated = migrateState(saved);
    if (migrated) return migrated;
  } catch {
    // A damaged local copy should not prevent the app from opening.
  }
  return defaultState();
}

function saveState() {
  state.visibleMonth = monthKey(visibleMonth);
  localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
}

function options() {
  return { skipHolidays: state.settings.skipHolidays };
}

function currentOccurrences() {
  return buildMonthOccurrences(state.items, visibleMonth, options());
}

function showToast(message, action = null) {
  const toast = $("#toast");
  $("#toastMessage").textContent = message;
  const button = $("#toastAction");
  button.classList.toggle("hidden", !action);
  if (action) {
    button.textContent = action.label;
    button.onclick = () => {
      action.run();
      toast.classList.remove("visible");
    };
  } else {
    button.onclick = null;
  }
  toast.classList.add("visible");
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => toast.classList.remove("visible"), action ? 6000 : 2400);
}

function applyTheme() {
  const theme = state.theme === "dark" ? "dark" : "light";
  document.documentElement.dataset.theme = theme;
  const label = theme === "dark" ? "Activar modo claro" : "Activar modo oscuro";
  $("#themeButton").setAttribute("aria-label", label);
  $("#themeButton").title = label;
  document.querySelector('meta[name="theme-color"]').content = theme === "dark" ? "#000000" : "#f4f4f1";
}

function fillSelect(select, entries, selected) {
  select.innerHTML = entries.map(({ value, label }) => `<option value="${value}">${label}</option>`).join("");
  if (selected !== undefined) select.value = selected;
}

function categoryOptions(type) {
  return categoriesFor(type).map((category) => ({ value: category.id, label: `${category.icon} ${category.label}` }));
}

/* ------------------------------------------------------------------ render */

function render() {
  const today = new Date();
  const todayKey = dateKey(today);
  const occurrences = currentOccurrences();
  const monthSpending = spendingInMonth(state.spending, visibleMonth);
  const spentThisMonth = totalSpending(monthSpending);
  const spentToday = monthKey(visibleMonth) === monthKey(today) ? spendingOnDate(state.spending, todayKey) : 0;
  const openingBalance = calculateOpeningBalance(state.items, visibleMonth, state.spending, options());
  const summary = calculateSummary(occurrences, today, openingBalance, spentThisMonth);
  const savingsThisMonth = savingsForMonth(state.savings, visibleMonth, state.settings.skipHolidays);
  const allowance = dailyAllowance(summary.safeToSpend, visibleMonth, today, savingsThisMonth, state.pacing, spentToday);
  const displayedAvailable = displayedAvailableMoney(summary.safeToSpend, savingsThisMonth, state.deductSavingsFromAvailable);
  const availableBeforePayments = openingBalance + summary.income;
  const ratio = availableBeforePayments > 0
    ? Math.min(100, Math.round(((summary.committed + spentThisMonth) / availableBeforePayments) * 100))
    : summary.committed > 0 ? 100 : 0;

  $("#monthTitle").textContent = monthLabel(visibleMonth);
  $("#monthMeta").textContent = renderMonthSummaryLine(summary, occurrences);
  $("#safeToSpend").textContent = formatMoney(displayedAvailable);
  $("#safeToSpend").classList.toggle("negative", displayedAvailable < 0);
  $("#safeExplanation").textContent = displayedAvailable < 0
    ? `faltan ${formatMoney(Math.abs(displayedAvailable))} para cubrir el mes`
    : state.deductSavingsFromAvailable
      ? allowance.saved
        ? `ya descuenta ${formatMoney(allowance.saved)} que decidiste guardar`
        : "define una meta en “Quiero guardar” para separarla"
      : allowance.saved
        ? `todavía incluye ${formatMoney(allowance.saved)} que decidiste guardar`
        : "después de separar todos los compromisos del mes";

  const savingsDeductionButton = $("#savingsDeductionButton");
  savingsDeductionButton.classList.toggle("active", state.deductSavingsFromAvailable);
  savingsDeductionButton.setAttribute("aria-pressed", String(state.deductSavingsFromAvailable));
  savingsDeductionButton.innerHTML = state.deductSavingsFromAvailable
    ? '<span aria-hidden="true">↩</span> Volver a incluir el ahorro'
    : '<span aria-hidden="true">−</span> Descontar ahorro de este monto';

  $("#dailyAllowance").innerHTML = `${formatMoney(allowance.amount)} <small>/ día</small>`;
  $("#allowanceDays").textContent = `${formatMoney(allowance.usable)} ÷ ${allowance.days} ${plural(allowance.days, "día de uso", "días de uso")} restantes`;
  $("#weeklyAllowance").innerHTML = `${formatMoney(allowance.weeklyAmount)} <small>/ semana</small>`;
  $("#allowanceWeek").textContent = `${allowance.weeklyDays} ${plural(allowance.weeklyDays, "día de uso", "días de uso")} · ${dayNames[allowance.pacing.weekStart]} a ${dayNames[allowance.pacing.weekEnd]}`;

  renderSavingsControl(allowance);

  $("#commitmentRatio").textContent = `${ratio}%`;
  $("#commitmentMeter").style.width = `${ratio}%`;
  $("#meterIncome").textContent = `${formatMoney(availableBeforePayments)} disponibles`;

  const todayPill = $("#todayPill");
  todayPill.classList.toggle("over", allowance.isSpendingDay && allowance.todayLeft < 0);
  todayPill.classList.toggle("resting", !allowance.isSpendingDay);
  $("#todayBudget").textContent = allowance.isSpendingDay ? formatMoney(Math.max(0, allowance.todayLeft)) : formatMoney(0);
  $("#todayDetail").textContent = !allowance.isSpendingDay
    ? monthKey(visibleMonth) === monthKey(today) ? "hoy no está entre tus días de uso" : "estás viendo otro mes"
    : allowance.todayLeft < 0
      ? `te pasaste ${formatMoney(Math.abs(allowance.todayLeft))} del ritmo de hoy`
      : `de ${formatMoney(allowance.todayBudget)} · llevas ${formatMoney(spentToday)}`;

  $("#totalIncome").textContent = formatMoney(summary.income);
  $("#totalExpenses").textContent = formatMoney(summary.committed);
  $("#openingBalance").textContent = formatMoney(openingBalance);
  $("#monthSpending").textContent = formatMoney(spentThisMonth);
  $("#monthSpendingDetail").textContent = monthSpending.length
    ? `${monthSpending.length} ${plural(monthSpending.length, "registro", "registros")} anotados`
    : "lo que has anotado día a día";
  $("#openingExplanation").textContent = openingBalance
    ? `cierre proyectado de ${monthNames[addMonths(visibleMonth, -1).getMonth()]}`
    : "este es el punto de partida";
  $("#receivedIncome").textContent = summary.receivedIncome
    ? `${formatMoney(summary.receivedIncome)} recibido`
    : "Nada recibido aún";
  $("#pendingExpenses").textContent = summary.pendingExpenses
    ? `${formatMoney(summary.pendingExpenses)} pendiente`
    : "Todo está pagado";

  const projection = projectMonths(state.items, visibleMonth, 12, state.spending, {
    ...options(),
    savingsPlan: state.savings,
  });
  const debt = debtOverview(state.items, visibleMonth);
  const health = financialHealth({
    income: summary.income,
    committed: summary.committed,
    creditPayments: summary.creditPayments,
    openingBalance,
    savingsTarget: allowance.saved,
    safeToSpend: summary.safeToSpend,
    goals: state.goals,
  }, state.settings);

  $("#healthCard").innerHTML = renderHealth(health);
  $("#alertList").innerHTML = renderAlerts(buildAlerts({
    occurrences,
    summary,
    allowance,
    projection,
    debt,
    goals: state.goals,
    spending: state.spending,
    visibleMonth,
    today,
    lastBackupAt: state.lastBackupAt,
    settings: state.settings,
  }));
  $("#compareStrip").innerHTML = renderComparison(compareWithPreviousMonth(state.items, visibleMonth, state.spending, options()));
  renderProjectionPanel(projection);

  $("#monthCalendar").innerHTML = renderCalendar(occurrences, visibleMonth, monthSpending, todayKey);
  $("#monthCalendar").setAttribute("aria-label", `Calendario de ${monthLabel(visibleMonth)}`);
  $("#holidayNote").textContent = renderHolidayNote(visibleMonth);
  $("#flowTimeline").innerHTML = renderFlow(projectedBalance(occurrences, openingBalance), openingBalance);

  $("#categoryList").innerHTML = renderCategories(categoryBreakdown(occurrences, monthSpending));
  $("#spendingTotal").textContent = formatMoney(spentThisMonth);
  $("#spendingList").innerHTML = renderSpendingLog(monthSpending, allowance);

  $("#debtRemaining").textContent = formatMoney(debt.remainingBalance);
  $("#debtMonthly").textContent = formatMoney(debt.monthlyPayment);
  $("#debtFreeMonth").textContent = debt.freeMonth ? monthKeyLabel(debt.freeMonth) : "Sin deudas";
  $("#debtProgress").textContent = `${Math.round(debt.progress)}%`;
  $("#debtList").innerHTML = renderDebts(debt);
  renderPayoff(debt);

  $("#goalList").innerHTML = renderGoals(state.goals, visibleMonth);

  renderMovementList(occurrences);
  saveState();
}

/* -------------------------------------------------------------- proyección */

function renderProjectionPanel(projection) {
  // Sin ahorro apartado las dos vistas serían idénticas, así que "Solo lo libre" no aplica.
  const hasSavings = projection.some((month) => month.savedTotal > 0);
  const view = hasSavings && state.projectionView === "free" ? "free" : "total";

  $$("#projectionViewToggle .filter").forEach((button) => {
    const isFree = button.dataset.projectionView === "free";
    const active = button.dataset.projectionView === view;
    button.classList.toggle("active", active);
    button.setAttribute("aria-pressed", String(active));
    button.disabled = isFree && !hasSavings;
    button.title = isFree && !hasSavings ? "Define un ahorro en “Quiero guardar” para ver esta vista" : "";
  });
  $("#legendSaved").classList.toggle("hidden", view === "free");
  $("#projectionCopy").textContent = view === "free"
    ? "Lo que te quedaría libre cada mes después de apartar tu ahorro."
    : "Saldo con el que cerrarías cada mes si todo sigue como está registrado hoy, incluyendo lo que decides guardar.";

  $("#projectionChart").innerHTML = renderProjection(projection, monthKey(visibleMonth), view);
  $("#projectionNotes").innerHTML = renderProjectionNotes(projection, view);
}

/* ------------------------------------------------------------------ ahorro */

/** Veces que cae el ahorro en el mes visible: 0 cuando el plan no aplica ahí. */
function savingsSlotsHere() {
  return savingsOccurrences(state.savings, visibleMonth, state.settings.skipHolidays);
}

/**
 * Mover el deslizador solo cambia el monto. El plan se re-ancla al mes visible únicamente
 * cuando ahí no se estaba guardando nada, para no mover sin querer un plan que ya corre.
 */
function setSavingsAmount(amount) {
  const restart = savingsSlotsHere() === 0 || !state.savings.amount;
  state.savings = {
    ...state.savings,
    amount: Math.max(0, Number(amount) || 0),
    startMonth: restart ? monthKey(visibleMonth) : state.savings.startMonth,
  };
}

function savingsPlanNote(plan, slotsHere, monthTotal) {
  if (!plan.amount) return "Lo que guardas se resta antes de calcular los montos por día y por semana.";
  const cadence = frequencyLabel(plan.frequency).toLowerCase();
  const since = monthKeyLabel(plan.startMonth);
  const summary = plan.frequency === "once"
    ? `guardar ${formatMoney(plan.amount)} una sola vez, en ${since}`
    : `guardar ${formatMoney(plan.amount)} ${cadence} desde ${since}`;
  if (!slotsHere) {
    return `Tu plan es ${summary}. En este mes no apartas nada; mueve la barra si quieres guardar aquí también.`;
  }
  if (plan.frequency === "once") {
    return `Guardas ${formatMoney(plan.amount)} una sola vez, en ${since}. Los demás meses quedan libres.`;
  }
  if (slotsHere > 1) {
    return `Guardas ${formatMoney(plan.amount)} ${cadence} desde ${since}: ${slotsHere} veces en este mes, ${formatMoney(monthTotal)} en total.`;
  }
  return `Guardas ${formatMoney(plan.amount)} ${cadence} desde ${since}. Se resta antes de calcular los montos por día y por semana.`;
}

function renderSavingsControl(allowance) {
  const plan = state.savings;
  const slotsHere = savingsSlotsHere();
  const perSlot = slotsHere ? plan.amount : 0;
  // El tope del deslizador es por aporte, no por mes: con ahorro semanal caben varios en el mes.
  const slots = savingsSlotsInMonth(plan.frequency, visibleMonth, state.settings.skipHolidays);
  const slider = $("#savingsTarget");
  slider.max = Math.max(Math.round(allowance.base / slots), perSlot, 1);
  slider.value = perSlot;
  slider.setAttribute("aria-valuetext", `${formatMoney(perSlot)} ${frequencyLabel(plan.frequency).toLowerCase()}`);
  $("#savingsFrequency").value = plan.frequency;
  $("#savingsTargetValue").textContent = formatMoney(perSlot);
  $("#savingsAmount").textContent = formatMoney(allowance.saved);
  $("#usableAmount").textContent = formatMoney(allowance.usable);
  $("#savingsNote").textContent = savingsPlanNote(plan, slotsHere, allowance.target);
}

function renderPayoff(debt) {
  const extra = Number($("#extraPayment").value) || 0;
  const strategy = $("#payoffStrategy").value;
  const simulation = simulateDebtPayoff(debt.credits, visibleMonth, extra, strategy);
  $("#payoffResult").innerHTML = renderPayoffResult(simulation, debt);
}

function matchesFilter(item) {
  if (activeFilter === "credit") return item.kind === "credit";
  if (activeFilter === "income") return item.type === "income";
  if (activeFilter === "expense") return item.type === "expense" && item.kind !== "credit";
  if (activeFilter === "pending") return !item.paid;
  return true;
}

function matchesSearch(item) {
  if (!searchTerm) return true;
  return `${item.name} ${item.category}`.toLowerCase().includes(searchTerm);
}

function renderMovementList(occurrences = currentOccurrences()) {
  const visible = occurrences.filter((item) => matchesFilter(item) && matchesSearch(item));
  $("#movementList").innerHTML = renderMovements(visible, visibleMonth);
}

/* ------------------------------------------------------------- navigation */

function setVisibleMonth(offset) {
  visibleMonth = addMonths(visibleMonth, offset);
  render();
}

function goToSection(name) {
  const targets = {
    movements: "#movements",
    calendar: "#calendar",
    projection: "#projection",
    spending: "#spending",
    debts: "#debts",
    goals: "#goals",
    backup: "#insights",
  };
  if (name === "backup") return exportData();
  const target = $(targets[name] || "#insights");
  target?.scrollIntoView({ behavior: "smooth", block: "start" });
}

/* ------------------------------------------------------ movement dialog */

function openDialog(item = null) {
  const form = $("#movementForm");
  form.reset();
  $("#movementId").value = item?.id || "";
  $("#dialogEyebrow").textContent = item ? "Editar movimiento" : "Nuevo movimiento";
  $("#dialogTitle").textContent = item ? "Ajusta este movimiento" : "¿Qué quieres registrar?";
  $("#deleteButton").classList.toggle("hidden", !item);

  const formType = item?.kind === "credit" ? "credit" : item?.type || "expense";
  form.elements.type.value = formType;
  fillSelect($("#movementFrequency"), FREQUENCIES.map((frequency) => ({ value: frequency.id, label: frequency.label })), item?.frequency || "once");
  fillSelect($("#movementCategory"), categoryOptions(formType === "income" ? "income" : "expense"), item?.category || defaultCategory({ type: formType === "income" ? "income" : "expense", kind: formType }));
  $("#movementName").value = item?.name || "";
  $("#movementAmount").value = item?.amount || "";
  $("#movementStart").value = item?.startDate || dateKey(visibleMonth);
  $("#movementSchedule").value = item?.schedule || "day";
  $("#movementDay").value = item?.day || parseDate($("#movementStart").value).getDate();
  $("#movementEnd").value = item && item.kind !== "credit" && item.endDate ? String(item.endDate).slice(0, 7) : "";
  $("#movementInstallments").value = item?.installments || 12;
  const visibleOccurrence = item && currentOccurrences().find((occurrence) => occurrence.id === item.id);
  $("#movementCurrentInstallment").value = visibleOccurrence?.installment || item?.currentInstallment || 1;
  $("#movementCreditEnd").value = item?.kind === "credit" ? creditEndMonth(item) : monthKey(addMonths(visibleMonth, 11));
  $("#movementCreditEnd").min = monthKey(visibleMonth);
  $("#currentInstallmentLabel").textContent = `Cuota en ${monthNames[visibleMonth.getMonth()]} ${visibleMonth.getFullYear()}`;
  updateFormVisibility();
  $("#movementDialog").showModal();
  setTimeout(() => $("#movementName").focus(), 50);
}

function updateFormVisibility() {
  const form = $("#movementForm");
  const type = form.elements.type.value;
  const isCredit = type === "credit";
  const frequency = $("#movementFrequency").value;
  const isRecurring = frequency !== "once";
  const usesMonthDay = ["monthly", "bimonthly", "quarterly", "semiannual", "yearly"].includes(frequency);
  const fixedDay = $("#movementSchedule").value === "day";

  if ($("#movementCategory").dataset.scope !== type) {
    const scope = type === "income" ? "income" : "expense";
    const previous = $("#movementCategory").value;
    fillSelect($("#movementCategory"), categoryOptions(scope));
    $("#movementCategory").value = CATEGORIES.some((category) => category.id === previous && category.scope === scope)
      ? previous
      : defaultCategory({ type: scope, kind: type });
    $("#movementCategory").dataset.scope = type;
  }

  $("#installmentsField").classList.toggle("hidden", !isCredit);
  $("#currentInstallmentField").classList.toggle("hidden", !isCredit);
  $("#creditEndField").classList.toggle("hidden", !isCredit);
  $("#creditHint").classList.toggle("hidden", !isCredit);
  $("#endField").classList.toggle("hidden", isCredit || !isRecurring);
  $("#scheduleField").classList.toggle("hidden", !usesMonthDay);
  $("#dayField").classList.toggle("hidden", !usesMonthDay || !fixedDay);
  $("#movementInstallments").required = isCredit;
  $("#movementCurrentInstallment").required = isCredit;
  $("#movementCreditEnd").required = isCredit;
  $("#movementCurrentInstallment").max = $("#movementInstallments").value || 360;
  if (isCredit) {
    $("#movementFrequency").value = "monthly";
    if (!$("#movementCreditEnd").value) syncCreditEndFromInstallments();
  }
  updateMovementPreview();
}

function updateMovementPreview() {
  const form = $("#movementForm");
  const amount = Number($("#movementAmount").value) || 0;
  const frequency = $("#movementFrequency").value;
  const type = form.elements.type.value;
  if (!amount) {
    $("#movementPreview").textContent = "Escribe un valor para ver el impacto mensual.";
    return;
  }
  const perMonth = { weekly: amount * 52 / 12, biweekly: amount * 26 / 12, monthly: amount, bimonthly: amount / 2, quarterly: amount / 3, semiannual: amount / 6, yearly: amount / 12, once: 0 }[frequency] ?? 0;
  $("#movementPreview").textContent = frequency === "once"
    ? `Movimiento único de ${formatMoney(amount)}.`
    : `Equivale a ${formatMoney(perMonth)} al mes y ${formatMoney(perMonth * 12)} al año ${type === "income" ? "que entran" : "que salen"}.`;
}

function syncCreditEndFromInstallments() {
  const total = Math.max(1, Number($("#movementInstallments").value) || 1);
  const current = Math.min(total, Math.max(1, Number($("#movementCurrentInstallment").value) || 1));
  $("#movementCurrentInstallment").value = current;
  $("#movementCurrentInstallment").max = total;
  $("#movementCreditEnd").value = monthKey(addMonths(visibleMonth, total - current));
  $("#movementCreditEnd").setCustomValidity("");
}

function syncInstallmentsFromCreditEnd() {
  const endValue = $("#movementCreditEnd").value;
  if (!endValue) return;
  const distance = monthsBetween(startOfMonth(visibleMonth), startOfMonth(endValue));
  if (distance < 0) {
    $("#movementCreditEnd").setCustomValidity("La última cuota no puede ser anterior al mes visible.");
    return;
  }
  $("#movementCreditEnd").setCustomValidity("");
  const current = Math.max(1, Number($("#movementCurrentInstallment").value) || 1);
  $("#movementInstallments").value = current + distance;
  $("#movementCurrentInstallment").max = current + distance;
}

function submitMovement(event) {
  if (event.submitter?.value === "cancel") return;
  event.preventDefault();
  const form = event.currentTarget;
  if (!form.reportValidity()) return;
  const data = new FormData(form);
  const formType = data.get("type");
  const id = data.get("id") || `movement-${Date.now()}`;
  const existing = state.items.find((item) => item.id === id);
  const start = parseDate(data.get("startDate"));
  const frequency = formType === "credit" ? "monthly" : data.get("frequency");
  const usesMonthDay = ["monthly", "bimonthly", "quarterly", "semiannual", "yearly"].includes(frequency);
  const visibleInstallment = Number(data.get("currentInstallment"));
  const startingInstallment = Math.max(1, visibleInstallment - Math.max(0, monthsBetween(start, visibleMonth)));
  const plainEnd = data.get("endDate");

  const item = {
    id,
    name: data.get("name").trim(),
    type: formType === "income" ? "income" : "expense",
    kind: formType === "credit" ? "credit" : formType,
    amount: Number(data.get("amount")),
    frequency,
    schedule: usesMonthDay ? data.get("schedule") : "day",
    day: usesMonthDay ? Number(data.get("day") || start.getDate()) : start.getDate(),
    startDate: data.get("startDate"),
    category: data.get("category"),
    installments: formType === "credit" ? Number(data.get("installments")) : undefined,
    currentInstallment: formType === "credit" ? startingInstallment : undefined,
    endDate: formType === "credit"
      ? `${data.get("creditEnd")}-01`
      : plainEnd && frequency !== "once" ? `${plainEnd}-01` : undefined,
    payments: existing?.payments || {},
    color: formType === "income" ? "mint" : formType === "credit" ? "coral" : "violet",
  };

  state.items = existing
    ? state.items.map((candidate) => (candidate.id === id ? item : candidate))
    : [...state.items, item];
  $("#movementDialog").close();
  render();
  showToast(existing ? "Movimiento actualizado" : "Movimiento agregado");
}

function duplicateMovement(id) {
  const original = state.items.find((item) => item.id === id);
  if (!original) return;
  state.items = [...state.items, {
    ...original,
    id: `movement-${Date.now()}`,
    name: `${original.name} (copia)`,
    payments: {},
  }];
  render();
  showToast("Movimiento duplicado");
}

function togglePayment(id, paymentKey) {
  state.items = state.items.map((item) => {
    if (item.id !== id) return item;
    const payments = { ...item.payments };
    if (payments[paymentKey]) delete payments[paymentKey];
    else payments[paymentKey] = true;
    return { ...item, payments };
  });
  render();
}

function deleteMovement() {
  const id = $("#movementId").value;
  if (!id) return;
  const snapshot = state.items;
  state.items = state.items.filter((item) => item.id !== id);
  $("#movementDialog").close();
  render();
  showToast("Movimiento eliminado", {
    label: "Deshacer",
    run: () => {
      state.items = snapshot;
      render();
      showToast("Movimiento restaurado");
    },
  });
}

/* ------------------------------------------------------------- gastos */

function submitSpending(event) {
  event.preventDefault();
  const amount = Number($("#spendAmount").value);
  if (!amount || amount <= 0) return;
  const entry = {
    id: `spend-${Date.now()}`,
    date: $("#spendDate").value || dateKey(new Date()),
    amount,
    category: $("#spendCategory").value,
    note: $("#spendNote").value.trim(),
  };
  state.spending = [...state.spending, entry];
  $("#spendAmount").value = "";
  $("#spendNote").value = "";
  if (monthKey(parseDate(entry.date)) !== monthKey(visibleMonth)) visibleMonth = startOfMonth(entry.date);
  render();
  showToast(`Gasto de ${formatMoney(amount)} anotado`, {
    label: "Deshacer",
    run: () => {
      state.spending = state.spending.filter((candidate) => candidate.id !== entry.id);
      render();
    },
  });
  $("#spendAmount").focus();
}

function deleteSpending(id) {
  const snapshot = state.spending;
  state.spending = state.spending.filter((entry) => entry.id !== id);
  render();
  showToast("Gasto borrado", {
    label: "Deshacer",
    run: () => {
      state.spending = snapshot;
      render();
    },
  });
}

/* -------------------------------------------------------------- metas */

function openGoalDialog(goal = null) {
  const form = $("#goalForm");
  form.reset();
  $("#goalId").value = goal?.id || "";
  $("#goalEyebrow").textContent = goal ? "Editar meta" : "Nueva meta";
  $("#goalIcon").value = goal?.icon || "🎯";
  $("#goalName").value = goal?.name || "";
  $("#goalTarget").value = goal?.target || "";
  $("#goalSaved").value = goal?.saved || 0;
  $("#goalMonthly").value = goal?.monthly || 0;
  $("#goalDeadline").value = goal?.deadline ? String(goal.deadline).slice(0, 7) : "";
  $("#deleteGoalButton").classList.toggle("hidden", !goal);
  updateGoalPreview();
  $("#goalDialog").showModal();
  setTimeout(() => $("#goalName").focus(), 50);
}

function updateGoalPreview() {
  const draft = goalProgress({
    name: $("#goalName").value,
    target: Number($("#goalTarget").value) || 0,
    saved: Number($("#goalSaved").value) || 0,
    monthly: Number($("#goalMonthly").value) || 0,
    deadline: $("#goalDeadline").value,
  }, visibleMonth);
  if (!draft.target) {
    $("#goalPreview").textContent = "Escribe el monto que quieres alcanzar.";
    return;
  }
  if (draft.complete) {
    $("#goalPreview").textContent = "Ya tienes el monto completo. ¡Meta cumplida!";
    return;
  }
  $("#goalPreview").textContent = draft.monthly
    ? `Faltan ${formatMoney(draft.missing)}: a ${formatMoney(draft.monthly)} al mes la logras en ${monthKeyLabel(draft.estimated)}.`
      + (draft.neededMonthly ? ` Para tu fecha límite necesitarías ${formatMoney(draft.neededMonthly)} al mes.` : "")
    : `Faltan ${formatMoney(draft.missing)}. Define un aporte mensual para saber cuándo la alcanzas.`;
}

function submitGoal(event) {
  if (event.submitter?.value === "cancel") return;
  event.preventDefault();
  const form = event.currentTarget;
  if (!form.reportValidity()) return;
  const id = $("#goalId").value || `goal-${Date.now()}`;
  const goal = {
    id,
    icon: $("#goalIcon").value,
    name: $("#goalName").value.trim(),
    target: Number($("#goalTarget").value) || 0,
    saved: Number($("#goalSaved").value) || 0,
    monthly: Number($("#goalMonthly").value) || 0,
    deadline: $("#goalDeadline").value ? `${$("#goalDeadline").value}-01` : "",
  };
  const exists = state.goals.some((candidate) => candidate.id === id);
  state.goals = exists
    ? state.goals.map((candidate) => (candidate.id === id ? goal : candidate))
    : [...state.goals, goal];
  $("#goalDialog").close();
  render();
  showToast(exists ? "Meta actualizada" : "Meta creada");
}

function deleteGoal() {
  const id = $("#goalId").value;
  if (!id) return;
  const snapshot = state.goals;
  state.goals = state.goals.filter((goal) => goal.id !== id);
  $("#goalDialog").close();
  render();
  showToast("Meta eliminada", {
    label: "Deshacer",
    run: () => {
      state.goals = snapshot;
      render();
    },
  });
}

function fundGoal(id) {
  const goal = state.goals.find((candidate) => candidate.id === id);
  if (!goal) return;
  const suggested = goal.monthly || Math.max(0, goal.target - goal.saved);
  const answer = prompt(`¿Cuánto abonas a "${goal.name}"?`, String(Math.round(suggested)));
  if (answer === null) return;
  const amount = Number(answer.replace(/[^\d]/g, ""));
  if (!amount) return;
  state.goals = state.goals.map((candidate) => candidate.id === id
    ? { ...candidate, saved: Math.max(0, candidate.saved + amount) }
    : candidate);
  render();
  showToast(`${formatMoney(amount)} abonados a ${goal.name}`);
}

/* ----------------------------------------------------------- ajustes */

function openSettings() {
  $("#settingEmergency").value = state.settings.emergencyMonths;
  $("#settingSavingsRate").value = state.settings.savingsGoalRate;
  $("#settingHolidays").checked = state.settings.skipHolidays;
  $("#settingsDialog").showModal();
}

function submitSettings(event) {
  if (event.submitter?.value === "cancel") return;
  event.preventDefault();
  state.settings = normalizeSettings({
    emergencyMonths: Number($("#settingEmergency").value),
    savingsGoalRate: Number($("#settingSavingsRate").value),
    skipHolidays: $("#settingHolidays").checked,
  });
  $("#settingsDialog").close();
  render();
  showToast("Ajustes guardados");
}

/* ------------------------------------------------------ copias y datos */

function download(content, filename, type) {
  const blob = new Blob([content], { type });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}

function exportData() {
  state.lastBackupAt = new Date().toISOString();
  download(
    JSON.stringify({ version: APP_VERSION, exportedAt: state.lastBackupAt, ...state }, null, 2),
    `mi-plata-clara-${dateKey(new Date())}.json`,
    "application/json",
  );
  render();
  showToast("Copia guardada");
}

function exportCsv() {
  const rows = [["tipo", "fecha", "nombre", "categoria", "valor", "estado"]];
  const months = projectMonths(state.items, visibleMonth, 12, state.spending, options());
  months.forEach((month) => {
    buildMonthOccurrences(state.items, month.date, options()).forEach((item) => {
      rows.push([item.type === "income" ? "ingreso" : "gasto", item.dateKey, item.name, item.category, Math.round(item.amount), item.paid ? "listo" : "pendiente"]);
    });
  });
  state.spending.forEach((entry) => {
    rows.push(["gasto diario", entry.date, entry.note || "Sin nota", entry.category, Math.round(entry.amount), "listo"]);
  });
  const csv = rows.map((row) => row.map((cell) => `"${String(cell).replaceAll('"', '""')}"`).join(";")).join("\n");
  download(`﻿${csv}`, `mi-plata-clara-${dateKey(new Date())}.csv`, "text/csv;charset=utf-8");
  showToast("CSV exportado");
}

function importData(file) {
  const reader = new FileReader();
  reader.onload = () => {
    try {
      const parsed = JSON.parse(String(reader.result));
      const migrated = migrateState(parsed);
      if (!migrated) throw new Error("Archivo sin movimientos");
      const snapshot = state;
      state = migrated;
      visibleMonth = startOfMonth(state.visibleMonth || new Date());
      applyTheme();
      render();
      showToast("Copia restaurada", {
        label: "Deshacer",
        run: () => {
          state = snapshot;
          visibleMonth = startOfMonth(state.visibleMonth || new Date());
          applyTheme();
          render();
        },
      });
    } catch {
      showToast("No pude leer ese archivo");
    }
  };
  reader.readAsText(file);
}

/* ------------------------------------------------------------- pacing */

function pacingDraft() {
  return {
    spendingDays: $$('input[name="spendingDays"]:checked').map((input) => Number(input.value)),
    weekStart: Number($("#pacingWeekStart").value),
    weekEnd: Number($("#pacingWeekEnd").value),
  };
}

function updatePacingPreview() {
  const draft = pacingDraft();
  const firstDay = $('input[name="spendingDays"]');
  const hasDays = draft.spendingDays.length > 0;
  firstDay.setCustomValidity(hasDays ? "" : "Selecciona al menos un día de uso.");
  if (!hasDays) {
    $("#pacingPreviewDay").textContent = "$0";
    $("#pacingPreviewWeek").textContent = "$0";
    $("#pacingPreviewExplanation").textContent = "Selecciona al menos un día de uso.";
    return;
  }
  const occurrences = currentOccurrences();
  const spent = totalSpending(spendingInMonth(state.spending, visibleMonth));
  const openingBalance = calculateOpeningBalance(state.items, visibleMonth, state.spending, options());
  const summary = calculateSummary(occurrences, new Date(), openingBalance, spent);
  const savingsThisMonth = savingsForMonth(state.savings, visibleMonth, state.settings.skipHolidays);
  const preview = dailyAllowance(summary.safeToSpend, visibleMonth, new Date(), savingsThisMonth, draft);
  $("#pacingPreviewDay").textContent = formatMoney(preview.amount);
  $("#pacingPreviewWeek").textContent = formatMoney(preview.weeklyAmount);
  $("#pacingPreviewExplanation").textContent = `${preview.days} días de uso restantes · semana de ${dayNames[draft.weekStart]} a ${dayNames[draft.weekEnd]}`;
}

function openPacingDialog() {
  $$('input[name="spendingDays"]').forEach((input) => {
    input.checked = state.pacing.spendingDays.includes(Number(input.value));
  });
  $("#pacingWeekStart").value = state.pacing.weekStart;
  $("#pacingWeekEnd").value = state.pacing.weekEnd;
  updatePacingPreview();
  $("#pacingDialog").showModal();
}

function submitPacing(event) {
  if (event.submitter?.value === "cancel") return;
  event.preventDefault();
  const firstDay = $('input[name="spendingDays"]');
  updatePacingPreview();
  if (!firstDay.checkValidity()) {
    firstDay.reportValidity();
    return;
  }
  state.pacing = pacingDraft();
  $("#pacingDialog").close();
  render();
  showToast("Días de uso actualizados");
}

/* -------------------------------------------------------------- eventos */

$("#previousMonth").addEventListener("click", () => setVisibleMonth(-1));
$("#nextMonth").addEventListener("click", () => setVisibleMonth(1));
$("#todayButton").addEventListener("click", () => {
  visibleMonth = startOfMonth(new Date());
  render();
});
$("#addButton").addEventListener("click", () => openDialog());
$("#backupButton").addEventListener("click", exportData);
$("#exportCsvButton").addEventListener("click", exportCsv);
$("#importButton").addEventListener("click", () => $("#importInput").click());
$("#importInput").addEventListener("change", (event) => {
  const [file] = event.target.files;
  if (file) importData(file);
  event.target.value = "";
});
$("#settingsButton").addEventListener("click", openSettings);
$("#settingsForm").addEventListener("submit", submitSettings);
$("#themeButton").addEventListener("click", () => {
  state.theme = state.theme === "dark" ? "light" : "dark";
  applyTheme();
  saveState();
});
$("#savingsTarget").addEventListener("input", (event) => {
  setSavingsAmount(event.target.value);
  render();
});
$("#savingsFrequency").addEventListener("change", (event) => {
  // Cambiar la frecuencia siempre arranca el plan en el mes que estás viendo.
  state.savings = { ...state.savings, frequency: event.target.value, startMonth: monthKey(visibleMonth) };
  render();
  showToast(state.savings.amount
    ? `Ahora guardas ${formatMoney(state.savings.amount)} ${frequencyLabel(state.savings.frequency).toLowerCase()} desde ${monthKeyLabel(monthKey(visibleMonth))}`
    : `El ahorro será ${frequencyLabel(state.savings.frequency).toLowerCase()}`);
});
$("#savingsDeductionButton").addEventListener("click", () => {
  state.deductSavingsFromAvailable = !state.deductSavingsFromAvailable;
  render();
  showToast(state.deductSavingsFromAvailable
    ? "El ahorro ya se descuenta del disponible"
    : "El ahorro vuelve a incluirse en el disponible");
});
$("#projectionViewToggle").addEventListener("click", (event) => {
  const button = event.target.closest("button[data-projection-view]");
  if (!button || button.disabled) return;
  state.projectionView = button.dataset.projectionView;
  render();
});
$("#pacingButton").addEventListener("click", openPacingDialog);
$("#pacingForm").addEventListener("submit", submitPacing);
$("#pacingForm").addEventListener("input", updatePacingPreview);
$("#pacingForm").addEventListener("change", updatePacingPreview);
$("#deleteButton").addEventListener("click", deleteMovement);
$("#movementForm").addEventListener("submit", submitMovement);
$("#movementForm").addEventListener("change", updateFormVisibility);
$("#movementForm").addEventListener("input", updateMovementPreview);
$("#movementInstallments").addEventListener("input", syncCreditEndFromInstallments);
$("#movementCurrentInstallment").addEventListener("input", syncCreditEndFromInstallments);
$("#movementCreditEnd").addEventListener("input", syncInstallmentsFromCreditEnd);
$("#movementStart").addEventListener("change", (event) => {
  if ($("#movementFrequency").value === "once") $("#movementDay").value = parseDate(event.target.value).getDate();
});

$("#spendingForm").addEventListener("submit", submitSpending);
$("#quickSpendButton").addEventListener("click", () => {
  goToSection("spending");
  setTimeout(() => $("#spendAmount").focus(), 320);
});
$("#spendingList").addEventListener("click", (event) => {
  const button = event.target.closest("button[data-action='delete-spending']");
  if (button) deleteSpending(button.dataset.id);
});

$("#addGoalButton").addEventListener("click", () => openGoalDialog());
$("#goalForm").addEventListener("submit", submitGoal);
$("#goalForm").addEventListener("input", updateGoalPreview);
$("#deleteGoalButton").addEventListener("click", deleteGoal);
$("#goalList").addEventListener("click", (event) => {
  const button = event.target.closest("button[data-action]");
  if (!button) return;
  const goal = state.goals.find((candidate) => candidate.id === button.dataset.id);
  if (button.dataset.action === "edit-goal") openGoalDialog(goal);
  if (button.dataset.action === "fund-goal") fundGoal(button.dataset.id);
});
$("#syncSavingsButton").addEventListener("click", () => {
  const total = state.goals.reduce((sum, goal) => sum + (Number(goal.monthly) || 0), 0);
  if (!total) {
    showToast("Define un aporte mensual en tus metas");
    return;
  }
  // Los aportes de las metas son mensuales, así que el plan se vuelve mensual desde este mes.
  state.savings = { amount: total, frequency: "monthly", startMonth: monthKey(visibleMonth) };
  render();
  showToast(`Meta mensual de ahorro: ${formatMoney(total)}`);
});

$("#extraPayment").addEventListener("input", () => renderPayoff(debtOverview(state.items, visibleMonth)));
$("#payoffStrategy").addEventListener("change", () => renderPayoff(debtOverview(state.items, visibleMonth)));

$("#alertList").addEventListener("click", (event) => {
  const button = event.target.closest("button[data-goto]");
  if (button) goToSection(button.dataset.goto);
});
$("#movementList").addEventListener("click", (event) => {
  const button = event.target.closest("button[data-action]");
  if (!button) return;
  if (button.dataset.action === "pay") togglePayment(button.dataset.id, button.dataset.key);
  if (button.dataset.action === "edit") openDialog(state.items.find((item) => item.id === button.dataset.id));
  if (button.dataset.action === "duplicate") duplicateMovement(button.dataset.id);
});
$("#monthCalendar").addEventListener("click", (event) => {
  const button = event.target.closest("button[data-action='edit']");
  if (button) openDialog(state.items.find((item) => item.id === button.dataset.id));
});
$("#movementSearch").addEventListener("input", (event) => {
  searchTerm = event.target.value.trim().toLowerCase();
  renderMovementList();
});
$$(".filter").forEach((button) => button.addEventListener("click", () => {
  $$(".filter").forEach((candidate) => candidate.classList.remove("active"));
  button.classList.add("active");
  activeFilter = button.dataset.filter;
  renderMovementList();
}));
$("#resetButton").addEventListener("click", () => {
  if (!confirm("¿Restaurar los datos de ejemplo? Se reemplazarán tus movimientos actuales.")) return;
  const snapshot = state;
  const currentTheme = state.theme;
  state = { ...defaultState(), theme: currentTheme };
  visibleMonth = startOfMonth("2026-08");
  render();
  showToast("Datos de ejemplo restaurados", {
    label: "Deshacer",
    run: () => {
      state = snapshot;
      visibleMonth = startOfMonth(state.visibleMonth || "2026-08");
      render();
    },
  });
});

document.addEventListener("keydown", (event) => {
  if (event.metaKey || event.ctrlKey || event.altKey) return;
  const tag = event.target.tagName;
  const typing = tag === "INPUT" || tag === "TEXTAREA" || tag === "SELECT";
  if (document.querySelector("dialog[open]")) return;
  if (typing && event.key !== "Escape") return;
  const actions = {
    n: () => openDialog(),
    g: () => { goToSection("spending"); setTimeout(() => $("#spendAmount").focus(), 320); },
    t: () => { visibleMonth = startOfMonth(new Date()); render(); },
    ArrowLeft: () => setVisibleMonth(-1),
    ArrowRight: () => setVisibleMonth(1),
    "/": () => $("#movementSearch").focus(),
  };
  const action = actions[event.key] || actions[event.key.toLowerCase()];
  if (!action) return;
  event.preventDefault();
  action();
});

/* -------------------------------------------------------------- arranque */

fillSelect($("#spendCategory"), categoryOptions("expense"), "mercado");
fillSelect($("#savingsFrequency"), FREQUENCIES.map((frequency) => ({ value: frequency.id, label: frequency.label })), state.savings.frequency);
$("#spendDate").value = dateKey(new Date());
$("#movementCategory").dataset.scope = "expense";
applyTheme();
render();
