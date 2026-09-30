import test from "node:test";
import assert from "node:assert/strict";
import { colombianHolidays, easterSunday, holidayName, isBusinessDay } from "../holidays.js";
import { dateKey, lastBusinessDay } from "../finance.js";

test("calcula el domingo de pascua", () => {
  assert.equal(dateKey(easterSunday(2026)), "2026-04-05");
  assert.equal(dateKey(easterSunday(2027)), "2027-03-28");
  assert.equal(dateKey(easterSunday(2025)), "2025-04-20");
});

test("aplica la Ley Emiliani trasladando festivos al lunes", () => {
  const holidays = colombianHolidays(2026);
  assert.equal(holidays.get("2026-01-12"), "Reyes Magos");
  assert.equal(holidays.get("2026-03-23"), "Día de San José");
  assert.equal(holidays.get("2026-11-16"), "Independencia de Cartagena");
});

test("conserva los festivos de fecha fija", () => {
  const holidays = colombianHolidays(2026);
  assert.equal(holidays.get("2026-01-01"), "Año Nuevo");
  assert.equal(holidays.get("2026-05-01"), "Día del Trabajo");
  assert.equal(holidays.get("2026-07-20"), "Grito de Independencia");
  assert.equal(holidays.get("2026-12-25"), "Navidad");
});

test("incluye los festivos que dependen de la pascua", () => {
  assert.equal(holidayName(new Date(2026, 3, 2)), "Jueves Santo");
  assert.equal(holidayName(new Date(2026, 3, 3)), "Viernes Santo");
  assert.equal(holidayName(new Date(2026, 4, 18)), "Ascensión del Señor");
  assert.equal(holidayName(new Date(2026, 5, 8)), "Corpus Christi");
  assert.equal(holidayName(new Date(2026, 5, 15)), "Sagrado Corazón");
});

test("un festivo no es día hábil", () => {
  assert.equal(isBusinessDay(new Date(2026, 0, 1)), false);
  assert.equal(isBusinessDay(new Date(2026, 0, 2)), true);
  assert.equal(isBusinessDay(new Date(2026, 0, 3)), false);
});

test("el último día hábil se corre cuando cae en festivo", () => {
  // Corpus Christi de 2027 cae el lunes 31 de mayo, último día del mes.
  assert.equal(holidayName(new Date(2027, 4, 31)), "Corpus Christi");
  assert.equal(dateKey(lastBusinessDay(2027, 4)), "2027-05-28");
  // Sin tener en cuenta festivos solo se evitan los fines de semana.
  assert.equal(dateKey(lastBusinessDay(2027, 4, false)), "2027-05-31");
  // Noviembre de 2026 termina en lunes hábil.
  assert.equal(dateKey(lastBusinessDay(2026, 10)), "2026-11-30");
});
