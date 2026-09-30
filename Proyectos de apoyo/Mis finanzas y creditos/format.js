import { COP, FREQUENCIES, daysBetween, parseDate, startOfDay } from "./finance.js";

export const monthNames = ["enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"];
export const shortMonths = ["ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic"];
export const dayNames = ["domingo", "lunes", "martes", "miércoles", "jueves", "viernes", "sábado"];

const frequencyLabels = new Map(FREQUENCIES.map((frequency) => [frequency.id, frequency.label]));

export function formatMoney(amount) {
  return COP.format(Math.round(amount)).replace(/\s/g, " ");
}

export function compactMoney(amount) {
  return `${amount < 0 ? "−" : ""}$${Math.abs(Math.round(amount)).toLocaleString("es-CO")}`;
}

export function shortMoney(amount) {
  const value = Math.abs(Math.round(amount));
  const sign = amount < 0 ? "−" : "";
  if (value >= 1_000_000) return `${sign}$${(value / 1_000_000).toFixed(value >= 10_000_000 ? 0 : 1).replace(".", ",")}M`;
  if (value >= 1_000) return `${sign}$${Math.round(value / 1_000)}k`;
  return `${sign}$${value}`;
}

export function titleCase(value) {
  return value.charAt(0).toUpperCase() + value.slice(1);
}

export function monthLabel(date) {
  return `${titleCase(monthNames[date.getMonth()])} ${date.getFullYear()}`;
}

export function monthKeyLabel(key) {
  if (!key) return "—";
  const [year, month] = key.split("-").map(Number);
  return `${shortMonths[month - 1]} ${year}`;
}

export function frequencyLabel(id) {
  return frequencyLabels.get(id) || "Solo una vez";
}

export function dayLabel(value) {
  const date = typeof value === "string" ? parseDate(value) : value;
  const distance = daysBetween(date, startOfDay());
  if (distance === 0) return "Hoy";
  if (distance === 1) return "Ayer";
  if (distance === -1) return "Mañana";
  return `${titleCase(dayNames[date.getDay()])} ${date.getDate()} ${shortMonths[date.getMonth()]}`;
}

export function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

export function plural(count, singular, pluralWord) {
  return count === 1 ? singular : pluralWord;
}

export function signedPercent(rate) {
  if (rate === null || !Number.isFinite(rate)) return "";
  const rounded = Math.round(rate);
  return `${rounded > 0 ? "+" : ""}${rounded}%`;
}
