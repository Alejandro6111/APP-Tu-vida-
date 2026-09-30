// Festivos de Colombia, incluida la Ley Emiliani que traslada varias fechas al lunes siguiente.
const cache = new Map();

function key(date) {
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${date.getFullYear()}-${month}-${day}`;
}

export function easterSunday(year) {
  const a = year % 19;
  const b = Math.floor(year / 100);
  const c = year % 100;
  const d = Math.floor(b / 4);
  const e = b % 4;
  const f = Math.floor((b + 8) / 25);
  const g = Math.floor((b - f + 1) / 3);
  const h = (19 * a + b - d - g + 15) % 30;
  const i = Math.floor(c / 4);
  const k = c % 4;
  const l = (32 + 2 * e + 2 * i - h - k) % 7;
  const m = Math.floor((a + 11 * h + 22 * l) / 451);
  const month = Math.floor((h + l - 7 * m + 114) / 31);
  const day = ((h + l - 7 * m + 114) % 31) + 1;
  return new Date(year, month - 1, day);
}

function movedToMonday(date) {
  const result = new Date(date);
  result.setDate(result.getDate() + ((8 - result.getDay()) % 7));
  return result;
}

export function colombianHolidays(year) {
  if (cache.has(year)) return cache.get(year);
  const easter = easterSunday(year);
  const afterEaster = (offset) => {
    const date = new Date(easter);
    date.setDate(date.getDate() + offset);
    return date;
  };

  const holidays = [
    ["Año Nuevo", new Date(year, 0, 1)],
    ["Reyes Magos", movedToMonday(new Date(year, 0, 6))],
    ["Día de San José", movedToMonday(new Date(year, 2, 19))],
    ["Jueves Santo", afterEaster(-3)],
    ["Viernes Santo", afterEaster(-2)],
    ["Día del Trabajo", new Date(year, 4, 1)],
    ["Ascensión del Señor", afterEaster(43)],
    ["Corpus Christi", afterEaster(64)],
    ["Sagrado Corazón", afterEaster(71)],
    ["San Pedro y San Pablo", movedToMonday(new Date(year, 5, 29))],
    ["Grito de Independencia", new Date(year, 6, 20)],
    ["Batalla de Boyacá", new Date(year, 7, 7)],
    ["Asunción de la Virgen", movedToMonday(new Date(year, 7, 15))],
    ["Día de la Raza", movedToMonday(new Date(year, 9, 12))],
    ["Todos los Santos", movedToMonday(new Date(year, 10, 1))],
    ["Independencia de Cartagena", movedToMonday(new Date(year, 10, 11))],
    ["Inmaculada Concepción", new Date(year, 11, 8)],
    ["Navidad", new Date(year, 11, 25)],
  ];

  const map = new Map(holidays.map(([name, date]) => [key(date), name]));
  cache.set(year, map);
  return map;
}

export function holidayName(date) {
  return colombianHolidays(date.getFullYear()).get(key(date)) || null;
}

export function isHoliday(date) {
  return Boolean(holidayName(date));
}

export function isWeekend(date) {
  return date.getDay() === 0 || date.getDay() === 6;
}

export function isBusinessDay(date) {
  return !isWeekend(date) && !isHoliday(date);
}

export function holidaysInMonth(year, monthIndex) {
  return [...colombianHolidays(year)]
    .map(([value, name]) => ({ dateKey: value, name, date: new Date(Number(value.slice(0, 4)), Number(value.slice(5, 7)) - 1, Number(value.slice(8, 10))) }))
    .filter((holiday) => holiday.date.getMonth() === monthIndex)
    .sort((a, b) => a.date - b.date);
}
