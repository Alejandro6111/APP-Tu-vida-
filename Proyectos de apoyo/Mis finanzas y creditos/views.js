import { categoryInfo, dateKey, monthsBetween, parseDate, startOfMonth } from "./finance.js";
import { goalProgress } from "./insights.js";
import { holidayName, holidaysInMonth } from "./holidays.js";
import {
  compactMoney,
  dayLabel,
  escapeHtml,
  formatMoney,
  frequencyLabel,
  monthKeyLabel,
  plural,
  shortMonths,
  shortMoney,
  signedPercent,
} from "./format.js";

const emptyState = (title, copy) => `<div class="empty-state"><strong>${title}</strong>${copy}</div>`;
const emptyRow = (title, copy) => `<li class="empty-state"><strong>${title}</strong>${copy}</li>`;

export function renderHealth(health) {
  const circumference = 2 * Math.PI * 52;
  const offset = circumference * (1 - health.score / 100);
  const tone = health.score >= 80 ? "excellent" : health.score >= 60 ? "solid" : health.score >= 40 ? "fair" : "weak";

  return `
    <div class="health-top">
      <div class="health-gauge ${tone}">
        <svg viewBox="0 0 120 120" role="img" aria-label="Salud financiera ${health.score} de 100">
          <circle class="gauge-track" cx="60" cy="60" r="52" />
          <circle class="gauge-value" cx="60" cy="60" r="52"
            stroke-dasharray="${circumference.toFixed(1)}" stroke-dashoffset="${offset.toFixed(1)}" />
        </svg>
        <div class="gauge-center"><strong>${health.score}</strong><span>/ 100</span></div>
      </div>
      <div class="health-copy">
        <p class="health-level ${tone}">${health.level}</p>
        <p class="health-headline">${escapeHtml(health.headline)}</p>
      </div>
    </div>
    <ul class="health-parts">
      ${health.parts.map((part) => `
        <li>
          <div class="health-part-head">
            <span>${escapeHtml(part.label)}</span>
            <b>${escapeHtml(part.value)}</b>
          </div>
          <div class="health-bar"><span style="width:${Math.round(part.score / part.max * 100)}%"></span></div>
          <p>${escapeHtml(part.tip)}</p>
        </li>
      `).join("")}
    </ul>
  `;
}

const actionLabels = {
  movements: "Ver pagos",
  calendar: "Ver calendario",
  projection: "Ver proyección",
  spending: "Anotar gasto",
  debts: "Ver deudas",
  goals: "Ver metas",
  backup: "Guardar copia",
};

export function renderAlerts(alerts) {
  return alerts.map((alert) => `
    <article class="alert ${alert.level}">
      <span class="alert-icon" aria-hidden="true">${alert.icon}</span>
      <div>
        <strong>${escapeHtml(alert.title)}</strong>
        <p>${escapeHtml(alert.detail)}</p>
      </div>
      ${alert.action ? `<button class="alert-link" type="button" data-goto="${alert.action}">${actionLabels[alert.action] || "Ver"}</button>` : ""}
    </article>
  `).join("");
}

/**
 * `view` decide qué representa cada barra: "total" dibuja el saldo de cierre con el ahorro
 * marcado dentro, y "free" dibuja solo el dinero que quedaría libre después de apartarlo.
 */
export function renderProjection(months, visibleMonthKey, view = "total") {
  if (!months.length) return emptyState("Sin datos para proyectar", "Agrega ingresos o gastos recurrentes.");
  const freeView = view === "free";
  const valueOf = (month) => (freeView ? month.free : month.closing);
  const width = 760;
  const height = 210;
  const padding = { top: 18, bottom: 34, left: 6, right: 6 };
  const usable = width - padding.left - padding.right;
  const slot = usable / months.length;
  const barWidth = Math.min(46, slot * 0.62);
  const max = Math.max(...months.map((month) => Math.abs(valueOf(month))), 1);
  const zone = height - padding.top - padding.bottom;
  const hasNegative = months.some((month) => valueOf(month) < 0);
  const zeroY = hasNegative ? padding.top + zone * 0.62 : padding.top + zone;
  const scale = (value) => Math.abs(value) / max * (value >= 0 ? zeroY - padding.top : height - padding.bottom - zeroY);

  const bars = months.map((month, index) => {
    const value = valueOf(month);
    const x = padding.left + slot * index + (slot - barWidth) / 2;
    const size = Math.max(2, scale(value));
    const y = value >= 0 ? zeroY - size : zeroY;
    const current = month.key === visibleMonthKey;
    const savedTotal = Math.max(0, month.savedTotal || 0);
    const savedHeight = !freeView && month.closing > 0 ? Math.min(size, size * savedTotal / month.closing) : 0;
    const savedBar = savedHeight > 0.5
      ? `<rect class="proj-saved" x="${x.toFixed(1)}" y="${(zeroY - savedHeight).toFixed(1)}" width="${barWidth.toFixed(1)}" height="${savedHeight.toFixed(1)}" rx="5" />`
      : "";
    const tooltip = freeView
      ? savedTotal > 0
        ? `${monthKeyLabel(month.key)}: ${compactMoney(month.free)} libres · ${compactMoney(savedTotal)} apartados · cierra en ${compactMoney(month.closing)}`
        : `${monthKeyLabel(month.key)}: ${compactMoney(month.free)} libres`
      : savedTotal > 0
        ? `${monthKeyLabel(month.key)}: cierra en ${compactMoney(month.closing)} · ${compactMoney(savedTotal)} guardados · ${compactMoney(month.free)} libres`
        : `${monthKeyLabel(month.key)}: cierra en ${compactMoney(month.closing)}`;
    return `
      <g class="proj-bar ${value < 0 ? "negative" : "positive"} ${current ? "current" : ""}">
        <title>${tooltip}</title>
        <rect x="${x.toFixed(1)}" y="${y.toFixed(1)}" width="${barWidth.toFixed(1)}" height="${size.toFixed(1)}" rx="5" />
        ${savedBar}
        <text class="proj-value" x="${(x + barWidth / 2).toFixed(1)}" y="${(value >= 0 ? y - 5 : y + size + 12).toFixed(1)}">${shortMoney(value)}</text>
        <text class="proj-label" x="${(x + barWidth / 2).toFixed(1)}" y="${height - 12}">${shortMonths[month.date.getMonth()]}</text>
      </g>
    `;
  }).join("");

  return `
    <svg class="projection-chart" viewBox="0 0 ${width} ${height}" preserveAspectRatio="xMidYMid meet" role="img"
      aria-label="${freeView ? "Dinero libre" : "Saldo"} proyectado de los próximos ${months.length} meses">
      <line class="proj-zero" x1="0" x2="${width}" y1="${zeroY}" y2="${zeroY}" />
      ${bars}
    </svg>
  `;
}

export function renderProjectionNotes(months, view = "total") {
  if (!months.length) return "";
  const freeView = view === "free";
  const valueOf = (month) => (freeView ? month.free : month.closing);
  const negative = months.filter((month) => month.closing < 0);
  const best = months.reduce((max, month) => (valueOf(month) > valueOf(max) ? month : max), months[0]);
  const last = months.at(-1);
  const savesSomething = months.some((month) => month.monthlySavings > 0);
  const notes = [];
  if (negative.length) {
    notes.push(`⚠️ ${negative.length} ${plural(negative.length, "mes cerraría", "meses cerrarían")} en rojo. El primero es ${monthKeyLabel(negative[0].key)}.`);
  } else {
    notes.push(`✅ Ningún mes proyectado cierra en negativo.`);
  }
  notes.push(freeView
    ? `En ${monthKeyLabel(last.key)} te quedarían ${compactMoney(last.free)} libres si todo sigue igual.`
    : `En ${monthKeyLabel(last.key)} tendrías ${compactMoney(last.closing)} si todo sigue igual.`);
  if (savesSomething) {
    notes.push(`🪙 Con tu plan de ahorro, en ${monthKeyLabel(last.key)} llevarías ${compactMoney(last.savedTotal)} apartados y ${compactMoney(last.free)} libres.`);
    const short = months.find((month) => month.savingsShort > 0);
    if (short) {
      notes.push(`⚠️ En ${monthKeyLabel(short.key)} el saldo no da para guardar todo: apartarías ${compactMoney(Math.max(0, short.saved))} de los ${compactMoney(short.monthlySavings)} previstos.`);
    }
    const drop = months.find((month) => month.savingsDrop > 0);
    if (drop) {
      notes.push(`⚠️ En ${monthKeyLabel(drop.key)} tendrías que echar mano de ${compactMoney(drop.savingsDrop)} de lo que ya tenías guardado.`);
    }
  }
  notes.push(freeView
    ? `El mes con más dinero libre sería ${monthKeyLabel(best.key)} con ${compactMoney(best.free)}.`
    : `El mejor cierre sería ${monthKeyLabel(best.key)} con ${compactMoney(best.closing)}.`);
  return notes.map((note) => `<li>${escapeHtml(note)}</li>`).join("");
}

export function renderComparison(comparison) {
  const previous = monthKeyLabel(comparison.previousMonth);
  const row = (label, data, invert = false) => {
    const better = invert ? data.diff < 0 : data.diff > 0;
    const tone = data.diff === 0 ? "flat" : better ? "up" : "down";
    const detail = data.previous === 0
      ? `sin registro en ${previous}`
      : data.diff === 0
        ? `igual que en ${previous}`
        : `${compactMoney(data.diff)} ${signedPercent(data.rate)} vs ${previous}`;
    return `
      <div class="compare-item ${data.previous === 0 ? "flat" : tone}">
        <span>${label}</span>
        <strong>${compactMoney(data.current)}</strong>
        <small>${detail}</small>
      </div>
    `;
  };
  return row("Ingresos", comparison.income)
    + row("Gastos", comparison.expenses, true)
    + row("Dinero libre", comparison.free);
}

export function renderCategories(rows) {
  if (!rows.length) return emptyRow("Todavía sin categorías", "Asigna una categoría a tus movimientos para ver en qué se va tu plata.");
  const top = rows.slice(0, 8);
  return top.map((row) => `
    <li class="category-row">
      <span class="category-icon" style="--tint:${row.color}">${row.icon}</span>
      <div class="category-main">
        <div class="category-head">
          <strong>${escapeHtml(row.label)}</strong>
          <b>${formatMoney(row.total)}</b>
        </div>
        <div class="category-bar"><span style="width:${row.share.toFixed(1)}%;background:${row.color}"></span></div>
        <small>${row.share.toFixed(0)}% del mes${row.real ? ` · ${formatMoney(row.real)} del día a día` : ""}</small>
      </div>
    </li>
  `).join("");
}

export function renderSpendingLog(entries, allowance) {
  if (!entries.length) {
    return emptyRow("Sin gastos anotados este mes", "Anota lo que gastas día a día y el ritmo diario se ajustará solo.");
  }
  const grouped = entries.reduce((days, entry) => {
    (days[entry.date] ||= []).push(entry);
    return days;
  }, {});

  return Object.entries(grouped).map(([date, dayEntries]) => {
    const total = dayEntries.reduce((sum, entry) => sum + entry.amount, 0);
    const overBudget = allowance?.todayBudget > 0 && date === dateKey(new Date()) && total > allowance.todayBudget;
    return `
      <li class="spend-day">
        <div class="spend-day-head">
          <strong>${dayLabel(date)}</strong>
          <b class="${overBudget ? "over" : ""}">${formatMoney(total)}</b>
        </div>
        <ul class="spend-entries">
          ${dayEntries.map((entry) => {
            const info = categoryInfo(entry.category);
            return `
              <li>
                <span class="category-icon small" style="--tint:${info.color}">${info.icon}</span>
                <span class="spend-note">${escapeHtml(entry.note || info.label)}</span>
                <b>${formatMoney(entry.amount)}</b>
                <button class="icon-link" type="button" data-action="delete-spending" data-id="${entry.id}" aria-label="Borrar gasto">×</button>
              </li>
            `;
          }).join("")}
        </ul>
      </li>
    `;
  }).join("");
}

export function renderDebts(debt) {
  if (!debt.count) return emptyRow("Sin créditos activos", "Cuando registres un crédito verás aquí cuánto falta y cuándo terminas.");
  return debt.credits.map((credit) => `
    <li class="debt-row">
      <div class="debt-head">
        <strong>${escapeHtml(credit.name)}</strong>
        <b>${formatMoney(credit.remainingBalance)}</b>
      </div>
      <div class="debt-bar"><span style="width:${credit.progress.toFixed(1)}%"></span></div>
      <div class="debt-meta">
        <span>Cuota ${credit.currentInstallment} de ${credit.total} · ${formatMoney(credit.installment)} al mes</span>
        <span>${credit.remainingMonths} ${plural(credit.remainingMonths, "mes", "meses")} · termina ${monthKeyLabel(credit.endMonth)}</span>
      </div>
    </li>
  `).join("");
}

export function renderPayoffResult(simulation, debt) {
  if (!debt.count) return "";
  if (!simulation.extra) {
    return `Sin abonos extra terminas de pagar en <b>${monthKeyLabel(debt.freeMonth)}</b>. Prueba cuánto adelantarías con un abono mensual.`;
  }
  const saved = simulation.monthsSaved;
  const first = simulation.payoff[0];
  return `Con <b>${formatMoney(simulation.extra)}</b> extra al mes quedarías libre en <b>${monthKeyLabel(simulation.freeMonth)}</b>`
    + (saved ? `, <b>${saved} ${plural(saved, "mes", "meses")} antes</b>.` : ".")
    + (first ? ` Lo primero en salir sería ${escapeHtml(first.name)} (${monthKeyLabel(first.month)}).` : "");
}

export function renderGoals(goals, visibleMonth) {
  if (!goals.length) {
    return emptyRow("Aún no tienes metas", "Una meta con nombre y monto hace que ahorrar sea mucho más fácil.");
  }
  return goals.map((goal) => {
    const progress = goalProgress(goal, visibleMonth);
    return `
      <li class="goal-card ${progress.complete ? "complete" : ""}">
        <div class="goal-head">
          <span class="goal-icon" aria-hidden="true">${escapeHtml(progress.icon)}</span>
          <div>
            <strong>${escapeHtml(progress.name)}</strong>
            <small>${formatMoney(progress.saved)} de ${formatMoney(progress.target)}</small>
          </div>
          <b>${Math.round(progress.progress)}%</b>
        </div>
        <div class="goal-bar"><span style="width:${progress.progress.toFixed(1)}%"></span></div>
        <p class="goal-note">
          ${progress.complete
            ? "🎉 Meta cumplida. Puedes cerrarla o subir el monto."
            : progress.monthly
              ? `Faltan ${formatMoney(progress.missing)} · a ${formatMoney(progress.monthly)} al mes la logras en ${monthKeyLabel(progress.estimated)}.`
              : `Faltan ${formatMoney(progress.missing)} · define un aporte mensual para estimar la fecha.`}
          ${progress.onTrack === false ? `<em>Para tu fecha límite necesitarías ${formatMoney(progress.neededMonthly)} al mes.</em>` : ""}
        </p>
        <div class="goal-actions">
          <button class="mini-button" type="button" data-action="fund-goal" data-id="${progress.id}">Abonar</button>
          <button class="mini-button" type="button" data-action="edit-goal" data-id="${progress.id}">Editar</button>
        </div>
      </li>
    `;
  }).join("");
}

export function renderCalendar(occurrences, visibleMonth, spending = [], todayKey) {
  const year = visibleMonth.getFullYear();
  const month = visibleMonth.getMonth();
  const daysInMonth = new Date(year, month + 1, 0).getDate();
  const leadingDays = (new Date(year, month, 1).getDay() + 6) % 7;
  const itemsByDay = occurrences.reduce((days, item) => {
    (days[item.date.getDate()] ||= []).push(item);
    return days;
  }, {});
  const spendingByDay = spending.reduce((days, entry) => {
    const day = Number(entry.date.slice(8, 10));
    days[day] = (days[day] || 0) + entry.amount;
    return days;
  }, {});

  const cells = [];
  for (let index = 0; index < leadingDays; index += 1) {
    cells.push('<div class="calendar-day outside" role="gridcell" aria-hidden="true"></div>');
  }

  for (let day = 1; day <= daysInMonth; day += 1) {
    const dayItems = itemsByDay[day] || [];
    const fullDate = new Date(year, month, day);
    const isToday = dateKey(fullDate) === todayKey;
    const festivo = holidayName(fullDate);
    const spent = spendingByDay[day];
    const accessibleDate = fullDate.toLocaleDateString("es-CO", { weekday: "long", day: "numeric", month: "long" });
    cells.push(`
      <div class="calendar-day ${isToday ? "today" : ""} ${festivo ? "holiday" : ""}" role="gridcell" aria-label="${accessibleDate}${festivo ? `, ${festivo}` : ""}">
        <div class="calendar-date">
          <span>${day}</span>
          ${isToday ? "<small>Hoy</small>" : festivo ? `<small class="holiday-tag" title="${escapeHtml(festivo)}">Festivo</small>` : ""}
        </div>
        <div class="calendar-events">
          ${dayItems.map((item) => `
            <button class="calendar-event ${item.type} ${item.paid ? "paid" : ""}" data-action="edit" data-id="${item.id}" type="button" title="Editar ${escapeHtml(item.name)}">
              <span>${escapeHtml(item.name)}</span>
              <strong>${item.type === "income" ? "+" : "−"}${formatMoney(item.amount)}</strong>
              ${item.kind === "credit" ? `<small>Cuota ${item.installment}/${item.installments}</small>` : ""}
            </button>
          `).join("")}
          ${spent ? `<div class="calendar-spent" title="Gastos del día a día">🧾 ${formatMoney(spent)}</div>` : ""}
        </div>
      </div>
    `);
  }

  while (cells.length % 7) {
    cells.push('<div class="calendar-day outside" role="gridcell" aria-hidden="true"></div>');
  }
  return cells.join("");
}

export function renderHolidayNote(visibleMonth) {
  const list = holidaysInMonth(visibleMonth.getFullYear(), visibleMonth.getMonth());
  if (!list.length) return "Este mes no tiene festivos en Colombia.";
  return `Festivos: ${list.map((holiday) => `${holiday.date.getDate()} ${shortMonths[holiday.date.getMonth()]} · ${holiday.name}`).join(" — ")}`;
}

export function renderFlow(projected, openingBalance) {
  if (!projected.length) {
    return emptyState("Este mes está en blanco", "Agrega un ingreso o un gasto para ver su recorrido.");
  }
  const carryItem = openingBalance ? `
    <article class="flow-item carry">
      <div class="flow-date">Inicio</div>
      <div class="flow-node"></div>
      <div class="flow-name">Saldo que venía</div>
      <strong class="flow-amount">${formatMoney(openingBalance)}</strong>
      <div class="flow-balance">antes de movimientos<strong>${compactMoney(openingBalance)}</strong></div>
    </article>
  ` : "";

  return carryItem + projected.map((item) => `
    <article class="flow-item ${item.type} ${item.balance < 0 ? "negative" : ""}">
      <div class="flow-date">${item.date.getDate()} ${shortMonths[item.date.getMonth()]}</div>
      <div class="flow-node"></div>
      <div class="flow-name" title="${escapeHtml(item.name)}">${escapeHtml(item.name)}</div>
      <strong class="flow-amount">${item.type === "income" ? "+" : "−"}${formatMoney(item.amount)}</strong>
      <div class="flow-balance">saldo proyectado<strong>${compactMoney(item.balance)}</strong></div>
    </article>
  `).join("");
}

export function renderMovements(occurrences, visibleMonth) {
  if (!occurrences.length) {
    return emptyState("No hay movimientos aquí", "Cambia el filtro, borra la búsqueda o agrega uno nuevo.");
  }
  const monthStart = startOfMonth(visibleMonth);

  return occurrences.map((item) => {
    const info = categoryInfo(item.category, item.type);
    const detail = item.kind === "credit"
      ? `Cuota ${item.installment} de ${item.installments} · termina ${monthKeyLabel(creditEnd(item))}`
      : frequencyLabel(item.frequency);
    const schedule = item.schedule === "last-business-day" ? "Último día hábil" : `Día ${item.date.getDate()}`;
    const paidLabel = item.type === "income"
      ? (item.paid ? "Recibido ✓" : "Marcar recibido")
      : (item.paid ? "Pagado ✓" : "Marcar pagado");
    const startsLater = monthsBetween(monthStart, parseDate(item.startDate)) > 0;

    return `
      <article class="movement-row ${item.paid ? "is-paid" : ""}">
        <div class="date-tile"><strong>${item.date.getDate()}</strong><span>${shortMonths[item.date.getMonth()]}</span></div>
        <div class="movement-main">
          <strong>${escapeHtml(item.name)}</strong>
          <span><i class="category-dot" style="background:${info.color}"></i>${info.label} · ${detail}</span>
        </div>
        <div class="movement-meta">
          <strong>${schedule}</strong>
          <span>${startsLater ? "Empieza más adelante" : frequencyLabel(item.frequency)}</span>
        </div>
        <strong class="movement-amount ${item.type}">${item.type === "income" ? "+" : "−"}${formatMoney(item.amount)}</strong>
        <div class="row-actions">
          <button class="pay-button ${item.paid ? "paid" : ""}" data-action="pay" data-id="${item.id}" data-key="${item.paymentKey}" type="button">${paidLabel}</button>
          <button class="edit-button" data-action="edit" data-id="${item.id}" type="button">${item.kind === "credit" ? "Editar cuota" : "Editar"}</button>
          <button class="edit-button" data-action="duplicate" data-id="${item.id}" type="button" title="Duplicar movimiento">Duplicar</button>
        </div>
      </article>
    `;
  }).join("");
}

function creditEnd(item) {
  if (item.endDate) return String(item.endDate).slice(0, 7);
  const start = startOfMonth(item.startDate);
  const startingInstallment = Math.max(1, Number(item.currentInstallment) || 1);
  const total = Math.max(startingInstallment, Number(item.installments) || startingInstallment);
  const end = new Date(start.getFullYear(), start.getMonth() + total - startingInstallment, 1);
  return `${end.getFullYear()}-${String(end.getMonth() + 1).padStart(2, "0")}`;
}

export function renderMonthSummaryLine(summary, occurrences) {
  const incomes = occurrences.filter((item) => item.type === "income").length;
  const expenses = occurrences.filter((item) => item.type === "expense").length;
  return `${occurrences.length} ${plural(occurrences.length, "movimiento", "movimientos")} · ${incomes} ${plural(incomes, "ingreso", "ingresos")} · ${expenses} ${plural(expenses, "pago", "pagos")}`;
}
