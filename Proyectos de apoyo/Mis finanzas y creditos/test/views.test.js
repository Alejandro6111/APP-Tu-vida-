import test from "node:test";
import assert from "node:assert/strict";
import { renderProjection, renderProjectionNotes } from "../views.js";
import { projectMonths } from "../insights.js";
import { defaultItems } from "../finance.js";

const savingsPlan = { amount: 800_000, frequency: "once", startMonth: "2026-08" };
const withSavings = () => projectMonths(defaultItems(), "2026-08", 3, [], { savingsPlan });

test("la vista de saldo total dibuja el ahorro dentro de la barra", () => {
  const chart = renderProjection(withSavings(), "2026-08");
  assert.match(chart, /cierra en \$1\.595\.000 · \$800\.000 guardados · \$795\.000 libres/);
  assert.equal(chart.match(/proj-saved/g).length, 3);
  assert.match(chart, /aria-label="Saldo proyectado/);
});

test("la vista de solo lo libre dibuja el dinero libre sin bloque de ahorro", () => {
  const chart = renderProjection(withSavings(), "2026-08", "free");
  assert.equal(chart.includes("proj-saved"), false);
  assert.match(chart, /\$795\.000 libres · \$800\.000 apartados · cierra en \$1\.595\.000/);
  assert.match(chart, /aria-label="Dinero libre proyectado/);
  // La barra de agosto ya no vale 1.595.000 sino los 795.000 que quedan libres.
  assert.match(chart, /class="proj-value"[^>]*>\$795k</);
  assert.equal(chart.includes(">$1,6M<"), false);
});

test("sin ahorro las dos vistas dibujan las mismas barras", () => {
  const months = projectMonths(defaultItems(), "2026-08", 3);
  const bars = (chart) => chart.match(/<rect[^>]*>|class="proj-value"[^>]*>[^<]*/g);
  assert.deepEqual(bars(renderProjection(months, "2026-08", "free")), bars(renderProjection(months, "2026-08")));
  // Sin nada apartado el tooltip de la vista libre no menciona el ahorro.
  assert.match(renderProjection(months, "2026-08", "free"), /ago 2026: \$1\.595\.000 libres</);
});

test("las notas cambian de idioma según la vista", () => {
  const months = withSavings();
  assert.match(renderProjectionNotes(months), /En oct 2026 tendrías \$1\.369\.000 si todo sigue igual/);
  assert.match(renderProjectionNotes(months), /El mejor cierre sería ago 2026 con \$1\.595\.000/);
  const free = renderProjectionNotes(months, "free");
  assert.match(free, /En oct 2026 te quedarían \$569\.000 libres si todo sigue igual/);
  assert.match(free, /El mes con más dinero libre sería ago 2026 con \$795\.000/);
});

test("una proyección vacía no rompe el gráfico ni las notas", () => {
  assert.match(renderProjection([], "2026-08"), /Sin datos para proyectar/);
  assert.equal(renderProjectionNotes([]), "");
});
