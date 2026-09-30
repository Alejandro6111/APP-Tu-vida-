# Mi Plata Clara

**Versión 2.0.0**

Aplicación web local para organizar ingresos, gastos, suscripciones, créditos, metas y gastos del día a día sin depender de cuentas mentales. Su cifra principal es el dinero que realmente puede usarse después de separar todos los compromisos del mes.

## Qué incluye

### El mes de un vistazo

- Resumen mensual de ingresos, compromisos, saldo registrado y dinero libre.
- Meta fija de ahorro configurable y botón para descontarla del monto principal.
- Cuánto puedes usar por día y por semana, con días de uso y semana personalizados.
- **Para hoy**: el presupuesto del día, que baja en tiempo real con cada gasto anotado.
- Comparación automática con el mes anterior en ingresos, gastos y dinero libre.
- Calendario mensual con festivos colombianos señalados y línea de tiempo del saldo proyectado.
- Arrastre automático del saldo restante de un mes al siguiente.

### Diagnóstico y ayudas

- **Salud financiera (0 a 100)** con cuatro medidas: ahorro del mes, carga de cuotas, colchón de emergencia y margen del mes. Cada una viene con un consejo concreto.
- **Avisos** que priorizan lo urgente: pagos vencidos sin marcar, pagos de la semana, meses que cerrarían en rojo, cuotas que pesan demasiado, créditos que terminan, metas atrasadas, gastos sin registrar y copias de seguridad vencidas. Cada aviso lleva a la sección correspondiente.
- **Proyección de 12 meses** en gráfico con dos vistas intercambiables: **Saldo total** muestra el cierre de cada mes con el ahorro acumulado marcado en verde dentro de la barra, y **Solo lo libre** muestra únicamente lo que te quedaría después de apartarlo. Avisa de los meses que cerrarían en negativo.

### Deudas

- Resumen real: cuánto falta por pagar, cuánto pesan las cuotas al mes, en qué mes quedas libre y qué porcentaje llevas pagado.
- Barra de avance por crédito con la cuota actual, el valor mensual y el mes final.
- **Simulador de abonos**: cuánto adelantarías tu fecha de libertad abonando un monto extra cada mes, empezando por la deuda más pequeña (bola de nieve) o por la cuota más alta. El cálculo no incluye intereses: asume que el abono baja directamente el saldo.

### Metas de ahorro

- Metas con nombre, ícono, monto objetivo, aporte mensual y fecha límite opcional.
- Fecha estimada de cumplimiento y aviso cuando el aporte no alcanza para la fecha límite.
- Botón **Abonar** para sumar dinero a una meta y opción de convertir la suma de los aportes en la meta mensual de ahorro.
- Lo ya ahorrado en metas cuenta como colchón de emergencia en la salud financiera.

### Día a día

- **Diario de gastos**: anota en dos segundos lo que gastas, con categoría y nota. Reduce el dinero libre, el presupuesto del día y el saldo que pasa al mes siguiente.
- **Categorías** con ícono y color: 13 de gasto y 4 de ingreso, con el reparto del mes entre lo planeado y lo realmente gastado.
- Los gastos del día también se ven en el calendario.

### Movimientos

- Ingresos y gastos únicos o recurrentes: semanal, cada 15 días, mensual, bimestral, trimestral, semestral o anual.
- Ingresos configurables para el último día hábil de cada mes, con festivos colombianos incluidos.
- Créditos con valor fijo, total de cuotas, cuota actual y mes de última cuota sincronizados entre sí.
- Marcación individual de pagos y cobros, duplicado de movimientos, búsqueda, filtros (incluido “pendientes”) y vista previa del impacto mensual y anual al crearlos.
- Fecha de finalización opcional para cualquier gasto o ingreso recurrente.

### Datos y comodidad

- Copia de seguridad en JSON y **restauración** desde un archivo, además de exportación a CSV de 12 meses.
- Deshacer disponible al eliminar movimientos, gastos, metas o al restaurar datos.
- Ajustes propios: meses de colchón ideal, porcentaje de ahorro ideal y si se tienen en cuenta los festivos.
- Atajos de teclado: `N` nuevo movimiento, `G` anotar gasto, `T` volver al mes actual, `←` `→` cambiar de mes, `/` buscar.
- Modo oscuro persistente y diseño adaptado a móvil.
- Persistencia local en el navegador, sin enviar información financiera a servidores.

## Datos iniciales

La primera apertura carga el contexto financiero suministrado:

| Movimiento | Valor | Fecha |
| --- | ---: | --- |
| Saldo inicial de agosto | $1.708.000 | Una sola vez, el 1 de agosto de 2026 |
| Ingreso adicional | $250.000 | Último día hábil |
| Crédito | $140.000 | Día 2 |
| Crédito | $38.000 | Día 22 |
| Crédito | $45.000 | Día 23 |
| Suscripciones | $140.000 | Día 20, editable |

Los créditos se crean inicialmente con 12 cuotas porque no se indicó su duración real. En **Editar cuota** se puede corregir el valor, el total de cuotas y la cuota correspondiente al mes visible; la app dejará de proyectarlo al terminar la última.

Los $1.708.000 son el capital inicial disponible en agosto de 2026. No se vuelven a registrar como ingreso, pero el dinero restante al cerrar agosto pasa a ser el saldo inicial de septiembre. Ese proceso continúa mes a mes. El único ingreso mensual precargado es el de $250.000 del último día hábil.

## Ejecutar

Se requiere Node.js 18 o superior.

La forma más rápida en Windows es hacer doble clic en **`Abrir Mi Plata Clara.bat`**. El lanzador inicia la app en segundo plano y abre automáticamente el navegador. Si la app ya está activa, simplemente vuelve a abrirla sin crear otro servidor.

También se puede iniciar manualmente desde una terminal:

```powershell
npm start
```

Después abre [http://localhost:4173](http://localhost:4173).

## Pruebas

```powershell
npm test
```

Las 71 pruebas cubren el último día hábil y los festivos colombianos, las frecuencias semanal, quincenal y de varios meses, la proyección a 12 meses, el fin de las cuotas, los pagos marcados, la separación del ahorro, los ritmos diario y semanal, el presupuesto del día, el efecto de los gastos diarios sobre el saldo, el resumen y la simulación de deudas, las metas, la salud financiera, los avisos y las migraciones de datos.

También se verificaron en un navegador real la carga inicial, el registro de un gasto, la creación de metas, el simulador de abonos, los movimientos semanales, los ajustes, el modo oscuro, la ausencia de errores de consola y la presentación móvil.

## Estructura

| Archivo | Contenido |
| --- | --- |
| `finance.js` | Cálculos base: fechas, ocurrencias, resumen del mes, ahorro, ritmos y migraciones. |
| `holidays.js` | Festivos de Colombia con Ley Emiliani y fechas derivadas de la pascua. |
| `insights.js` | Proyección, comparación de meses, categorías, deudas, metas, salud financiera y avisos. |
| `views.js` | Generación del HTML de cada sección. |
| `format.js` | Formatos de dinero, fechas y textos. |
| `app.js` | Estado, eventos y orquestación de la interfaz. |

## Privacidad y respaldo

Los datos se guardan únicamente en `localStorage` dentro del navegador actual. El botón **Copia** descarga un archivo JSON y **Restaurar copia** lo vuelve a cargar. Borrar los datos del navegador también borra la información de la app, por lo que conviene crear copias periódicas; la app avisa cuando la última copia tiene más de 30 días.

## Notas de cálculo

- **Saldo que venía** = cierre proyectado del mes anterior, ya descontados los gastos diarios de ese mes.
- **Puedes usar con tranquilidad** = saldo que venía + ingresos previstos − todos los gastos y cuotas previstos − gastos del día a día anotados. Cuando se activa **Descontar ahorro de este monto**, también resta el ahorro protegido.
- **Saldo registrado** = saldo que venía + ingresos marcados como recibidos − egresos marcados como pagados − gastos del día a día.
- **Quiero guardar** = monto por aporte más una frecuencia (**una sola vez**, cada semana, cada 15 días, cada mes, cada 2, 3, 6 meses o cada año). El plan arranca en el mes donde lo defines: si eliges *una sola vez* en agosto, solo se aparta en agosto y los demás meses quedan libres. En los meses en que el plan no aplica, el deslizador aparece en cero y moverlo ahí arranca un plan nuevo desde ese mes; cambiar la frecuencia siempre reancla el plan al mes que estás viendo.
- **Ahorro del mes** = monto por aporte × las veces que cae en ese mes (un ahorro semanal de $50.000 son $250.000 en un mes de cinco semanas). Es lo que se protege antes de calcular el uso diario y semanal; si el dinero libre del mes es menor, se protege solo lo disponible sin perder el plan definido.
- **Dinero para usar** = dinero libre − ahorro del mes.
- **Ahorro en la proyección** = la proyección de 12 meses aparta el ahorro solo en los meses que la frecuencia indica, y lo dibuja en verde dentro de cada barra. El ahorro acumulado nunca supera el saldo de cierre de ese mes: si no alcanza para el aporte completo, aparta lo que se pueda; si el saldo baja por debajo de lo ya guardado, avisa cuánto tendrías que sacar del ahorro.
- **Saldo total / Solo lo libre** = las dos vistas de la proyección. *Saldo total* dibuja el cierre completo; *Solo lo libre* dibuja el cierre menos el ahorro acumulado, y las notas de abajo cambian con la vista. La opción queda guardada. Sin un ahorro definido las dos vistas serían idénticas, así que *Solo lo libre* aparece desactivada.
- **Puedes usar por día** = dinero para usar ÷ días de uso restantes del mes.
- **Puedes usar por semana** = monto diario × días de uso incluidos en la semana personalizada.
- **Para hoy** = ritmo del día menos lo que ya anotaste hoy. Si hoy no es un día de uso, no hay presupuesto.
- **Salud financiera** = ahorro del mes (30) + carga de cuotas (25) + colchón de emergencia (25) + margen del mes (20). El colchón suma el saldo que venía y lo ya guardado en metas.
- **Quedas libre** = mes de la última cuota del crédito más largo. El simulador reparte tu abono extra sobre la deuda elegida y va liberando cuotas a medida que terminan.
- **Último día hábil** = último día del mes que no sea sábado, domingo ni festivo colombiano (se puede desactivar en Ajustes).
- Los montos diario y semanal son referencias, no pagos ni límites obligatorios.

## Cambios de versión

### 2.2.0

- La **proyección de 12 meses** gana un interruptor **Saldo total / Solo lo libre**: la segunda vista dibuja el dinero que quedaría disponible después de apartar el ahorro, en vez del saldo completo.
- El texto de la sección, la leyenda, los rótulos de cada barra y las notas del pie se adaptan a la vista elegida, que queda guardada entre sesiones.
- Nuevo archivo de pruebas `test/views.test.js` para el gráfico y sus notas.

### 2.1.0

- **Quiero guardar** deja de ser un monto que se repetía en todos los meses y pasa a ser un **plan con frecuencia**: una sola vez, cada semana, cada 15 días, cada mes, cada 2, 3, 6 meses o cada año, arrancando en el mes donde lo defines.
- El plan de ahorro se **sincroniza con la proyección de 12 meses**: cada barra muestra en verde el ahorro acumulado y en azul el saldo libre, apartando solo en los meses que la frecuencia indica.
- Nuevos avisos cuando el aporte no cabe en algún mes proyectado o cuando el saldo obligaría a sacar plata de lo ya guardado.
- El deslizador limita el monto **por aporte** según las veces que cabe en el mes, y una nota explica el plan activo.
- La medida **Ahorro del mes** de la salud financiera usa el ahorro que realmente cabe en el mes, no la meta sin recortar.
- Los datos guardados suben a la versión 8: el monto suelto anterior se convierte en un plan mensual desde el último mes visible.

### 2.0.0

- Añade el **diario de gastos** del día a día, con categorías, presupuesto **Para hoy** y efecto real sobre el dinero libre y el saldo de los meses siguientes.
- Incorpora el índice de **salud financiera** con cuatro medidas y consejos.
- Añade el panel de **avisos** que prioriza pagos vencidos, semanas cargadas, meses en rojo, metas atrasadas y copias vencidas.
- Añade la **proyección de 12 meses** en gráfico y la comparación con el mes anterior.
- Añade el **plan para salir de deudas** con simulador de abonos extra (bola de nieve o mayor cuota).
- Añade **metas de ahorro** con aporte mensual, fecha estimada, abonos y sincronización con la meta mensual.
- Añade **categorías** para ingresos y gastos, con el reparto del mes.
- Añade los **festivos de Colombia** (incluida la Ley Emiliani) al cálculo del último día hábil y al calendario.
- Añade frecuencias semanal, quincenal, bimestral, trimestral, semestral y anual, y fecha de fin para recurrentes.
- Añade búsqueda, filtro de pendientes, duplicado de movimientos y vista previa del impacto mensual y anual.
- Añade restauración de copias JSON, exportación CSV, deshacer, ajustes personales y atajos de teclado.
- Reorganiza el código en `holidays.js`, `insights.js`, `views.js` y `format.js`.

### 1.6.1

- Añade un lanzador `.bat` para abrir la app con doble clic en Windows.
- Inicia el servidor local de forma oculta, abre el navegador automáticamente y evita procesos duplicados.
- Muestra una indicación clara si Node.js no está instalado.

### 1.6.0

- Aclara que **Quiero guardar** se descuenta antes de calcular cuánto puede usarse por día y por semana.
- Añade un botón para descontar el ahorro del monto principal y una acción inversa para volver a incluirlo.
- Conserva la preferencia en el navegador y en las copias de seguridad JSON.

### 1.5.0

- Convierte el modo oscuro en un tema negro real, con fondo `#000000` y superficies de alto contraste.
- Renueva la identidad visual con un monograma SVG propio inspirado en el flujo del dinero.
- Refuerza los estados de foco, hover y movimiento reducido.

### 1.4.1

- Convierte **Quiero guardar** en una meta monetaria fija que no cambia al navegar entre meses.

### 1.4.0

- Añade el cálculo **Puedes usar por semana** junto al ritmo diario y los días de uso configurables.

### 1.3.1

- Añade el campo **Última cuota** para elegir el mes en que termina un crédito.

### 1.3.0

- Añade un porcentaje de ahorro sobre el dinero libre y el modo oscuro.

### 1.2.1

- Reorganiza el calendario y el recorrido del dinero en dos columnas.

### 1.2.0

- Añade la vista de calendario mensual y la edición de cuotas desde la lista.

### 1.1.0

- Conserva el cierre proyectado de cada mes como saldo inicial del siguiente.

### 1.0.1

- Corrige la base de $1.708.000 para que ocurra solamente en agosto de 2026.
