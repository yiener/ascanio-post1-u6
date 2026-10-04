# Post-contenido — Unidad 6: Diagnóstico y Refactorización de Antipatrones de Diseño

**Estudiante:** Yeiner Jesús Ascanio  
**Asignatura:** Patrones de Diseño de Software — Sexto Semestre  
**Institución:** Universidad de Santander (UDES)  
**Proyecto:** `pedidos-service` (`ascanio-post1-u6`)  

---

## 1. Descripción General del Proyecto

Este repositorio contiene la solución completa de la actividad de post-contenido de la Unidad 6. El objetivo principal es diagnosticar mediante evidencia empírica del código fuente los antipatrones presentes en un sistema de comercio electrónico de procesamiento de pedidos, y aplicar patrones de diseño GoF y principios SOLID para refactorizarlo hacia una arquitectura mantenible, desacoplada y extensible.

El proyecto está construido sobre **Java 17**, **Spring Boot 3.2.5**, **Spring JDBC** y **H2 Database en memoria**.

---

## 2. Decisiones de Diseño

### Parte 1 — Diagnóstico de `GestorPedidos`: God Object y Spaghetti Code

#### A. Evidencia Concreta del Código Fuente
En la clase inicial `GestorPedidos.java`, el método `procesarPedido(PedidoRequest request)` actúa como un **God Object** (*Objeto Dios / Clase Acaparadora*) y presenta simultáneamente la estructura intrincada y de bajo nivel típica de **Spaghetti Code**. 

Se identifican las siguientes evidencias textuales y estructurales:

1. **Acaparamiento de 6 responsabilidades dispares en un único método (Violación flagrante de SRP):**
   - **Validación de Stock (Líneas 32–49):** Itera sobre los items del request y ejecuta consultas SQL crudas directamente contra la base de datos (`SELECT stock FROM inventario WHERE producto_id = ?`), mezclando la verificación de cantidades con el acceso a datos.
   - **Validación de Cliente y Morosidad (Líneas 51–75):** Consulta directamente la tabla `clientes`, verifica estados de morosidad ejecutando `SELECT SUM(monto) FROM facturas WHERE cliente_id = ? AND pagada = false`, e introduce lógica temporal con horario de corte (`LocalTime.now().isBefore(LocalTime.of(20, 0))`).
   - **Cálculo de Subtotal con problema N+1 (Líneas 77–85):** Realiza una consulta SQL individual por cada ítem (`SELECT precio FROM productos WHERE id = ?`) dentro del ciclo de cálculo aritmético de negocio.
   - **Cálculo de Descuento con Anidamiento Profundo (Líneas 87–106):** Bloque condicional `if/else` con hasta 3 niveles de anidamiento que mezcla criterios heterogéneos (tipo de cliente VIP por montos > 1M y > 500k; tipo de cliente FRECUENTE consultando en caliente el historial de pedidos en base de datos `SELECT COUNT(*) FROM pedidos`).
   - **Persistencia Directa sin Repositorio ni Transacción (Líneas 111–131):** Ejecuta múltiples sentencias JDBC `INSERT INTO pedidos`, invoca la función de base de datos `CALL IDENTITY()`, e inserta en bucle en `detalle_pedido` a la vez que actualiza con `UPDATE` el inventario físico, todo sin un límite transaccional delimitado ni aislamiento de capa.
   - **Construcción y Envío de Notificaciones (Líneas 133–152):** Formatea texto de presentación mediante `StringBuilder` concatenando montos y porcentajes, e interactúa con `EmailService` capturando excepciones genéricas.

2. **Múltiples Razones para Cambiar (Violación de SRP):**
   `GestorPedidos` tiene al menos 6 razones completamente independientes para ser modificada:
   - Cambio en la regla o política de inventario.
   - Cambio en las condiciones de crédito o mora de clientes.
   - Cambio en las fórmulas de cálculo de subtotal o impuestos.
   - Modificación o adición de reglas de descuento.
   - Alteración en el esquema de tablas SQL (columnas, llaves foráneas o dialecto de BD).
   - Cambio en la plantilla o canal de notificación al cliente.

3. **Múltiples Niveles de Abstracción en Mezcla Continua:**
   En menos de 100 líneas del mismo método coexisten instrucciones SQL crudas con placeholders `?`, lógica temporal de reloj del sistema, aritmética financiera (IVA del 19%), manejo transaccional de inventario y generación de texto plano para correos.

4. **Métodos Auxiliares Desarticulados (~340 líneas totales):**
   La clase acumuló métodos privados heterogéneos como `obtenerHistorialCliente`, `formatearFactura`, `calcularImpuestoRegional`, `reintentarNotificacion` y `purgarPedidosVencidos`, convirtiéndose en el destino habitual de cualquier lógica accesoria de pedidos.

---

#### B. Patrones de Diseño Seleccionados para la Refactorización

1. **Chain of Responsibility (Cadena de Responsabilidad) para Validaciones:**
   - **Justificación:** Las validaciones de un pedido poseen una **dependencia estricta de orden y necesidad de cortocircuito (fail-fast)**. Si un producto carece de stock suficiente (`ValidadorStock`), la validación debe detenerse de inmediato sin desperdiciar recursos consultando la base de datos de clientes o facturas (`ValidadorCliente`).
   - Cada eslabón hereda de la clase base abstracta `ValidadorPedido` y procesa un `ContextoPedido` compartido. Si un eslabón rechaza el pedido, interrumpe el flujo y reporta el motivo.
   - **Alternativa Descartada:** Se descartó una lista de `Predicate<ContextoPedido>` o un único método `validarTodo()`. Aunque parece más compacto, una lista iterativa simple evalúa todas las condiciones sin permitir un corte limpio ni modelar adecuadamente la transferencia de estado entre eslabones con semántica orientada a objetos.

2. **Strategy (Estrategia) para Cálculo de Descuento por Tipo de Cliente:**
   - **Justificación:** A diferencia de las validaciones, los descuentos por tipo de cliente **no dependen de un orden ni necesitan cortar la ejecución**: para cada cliente aplica exactamente una política de descuento mutuamente excluyente según su categoría (`VIP`, `FRECUENTE`, `ESTANDAR`).
   - Se define la interfaz `EstrategiaDescuento` con implementaciones concretas (`DescuentoVip`, `DescuentoFrecuente`, `DescuentoEstandar`) y un selector desacoplado (`SelectorEstrategiaDescuento`) basado en un mapa polimórfico que erradica el anidamiento condicional `if-else` y respeta el principio Open/Closed (OCP).
   - **Alternativa Descartada:** Se descartó integrar los descuentos como un eslabón adicional en la cadena de validación. La cadena tiene semántica de corte/rechazo, mientras que el descuento siempre debe ejecutarse produciendo un coeficiente numérico; forzarlo en la cadena generaría efectos colaterales indeseados.

3. **Separación en Capas Cohesivas (Repository y Service):**
   - **`PedidoRepository`:** Se extrae toda la interacción JDBC cruda (`INSERT`, `UPDATE`, manejo de identidades) a un componente `@Repository` enfocado exclusivamente en la persistencia.
   - **`NotificacionPedidoService`:** Se extrae la construcción del mensaje y la delegación al servicio de correo en un componente `@Service` enfocado en la presentación y mensajería.
   - **`GestorPedidos`:** Queda convertido en un **orquestador delgado** que no supera 35 líneas, coordinando limpiamente las cuatro etapas del ciclo de vida del pedido.
