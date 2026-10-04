# Post-contenido — Unidad 6: Diagnóstico y Refactorización de Antipatrones de Diseño: Sistema de Gestión de Pedidos

**Estudiante:** Yeiner Jesús Ascanio  
**Asignatura:** Patrones de Diseño de Software — Sexto Semestre  
**Institución:** Universidad de Santander (UDES)  
**Repositorio:** `ascanio-post1-u6`  
**Proyecto:** `pedidos-service`  

---

## 1. Descripción General del Proyecto

Este proyecto aborda la actividad práctica de post-contenido de la Unidad 6, centrada en el diagnóstico riguroso mediante evidencia empírica de código fuente y la refactorización guiada por patrones de diseño de software y principios SOLID.

El sistema simula el núcleo de procesamiento de pedidos de una plataforma de comercio electrónico. La actividad se divide en dos fases progresivas:
1. **Parte 1:** Diagnóstico y refactorización de un antipatrón combinado (**God Object** y **Spaghetti Code**) concentrado en la clase `GestorPedidos`, separando sus responsabilidades en capas cohesivas mediante los patrones **Chain of Responsibility** y **Strategy**, además de capas de persistencia y notificación dedicadas.
2. **Parte 2:** Diagnóstico y corrección del antipatrón **Golden Hammer** introducido tras un ciclo de crecimiento al agregar tres tipos de descuento promocional (`BLACK_FRIDAY`, `CORPORATIVO`, `VOLUMEN`) indebidamente como eslabones de la cadena de validación, reestructurándolos bajo el patrón **Strategy** y previniendo el antipatrón **Lava Flow** mediante la eliminación total de código muerto.

Tecnologías base: **Java 17 / 21**, **Spring Boot 3.2.5**, **Spring JDBC**, **H2 Database (en memoria)** y **JUnit 5**.

---

## 2. Decisiones de Diseño

### Parte 1 — Diagnóstico y Refactorización de `GestorPedidos`

#### A. Diagnóstico con Evidencia Concreta del Código Fuente
En la versión inicial de `GestorPedidos.java`, el método `procesarPedido(PedidoRequest request)` presentaba una concentración patológica de responsabilidades y un flujo de control enredado, constituyendo un **God Object** (*Clase Dios*) combinado con **Spaghetti Code**.

**Evidencia empírica identificada en el código:**

1. **Acaparamiento de 6 responsabilidades dispares en un solo método (Violación flagrante de SRP):**
   - *Validación de existencias e inventario (Líneas 32–49):* Itera sobre los ítems del pedido ejecutando consultas SQL crudas directamente contra la base de datos (`SELECT stock FROM inventario WHERE producto_id = ?`), mezclando lógica de validación con infraestructura de persistencia.
   - *Validación de cliente, morosidad y regla temporal de corte (Líneas 51–75):* Consulta la tabla `clientes`, verifica saldos insolutos en `facturas` (`SELECT SUM(monto) FROM facturas WHERE cliente_id = ? AND pagada = false`) y evalúa restricciones horarias diurnas/nocturnas (`LocalTime.now().isBefore(LocalTime.of(20, 0))`).
   - *Cálculo de subtotal con antipatrón N+1 (Líneas 77–85):* Ejecuta una consulta SQL individual por cada ítem (`SELECT precio FROM productos WHERE id = ?`) en medio del algoritmo de cálculo numérico.
   - *Cálculo de descuentos con anidamiento condicional profundo (Líneas 87–106):* Estructura `if/else` con hasta 3 niveles de anidamiento que mezcla decisiones por monto (clientes VIP: > 1M al 15%, > 500k al 10%, base 5%) con consultas históricas en caliente (`SELECT COUNT(*) FROM pedidos WHERE cliente_id = ?` para clientes FRECUENTE).
   - *Persistencia directa vía JDBC sin repositorio ni transacción explícita (Líneas 111–131):* Ejecuta sentencias SQL `INSERT INTO pedidos`, consulta la secuencia con `CALL IDENTITY()`, e inserta en bucle en `detalle_pedido` a la vez que actualiza con `UPDATE` el stock del inventario.
   - *Construcción y envío de notificaciones (Líneas 133–152):* Ensambla el texto de presentación mediante `StringBuilder` concatenando montos y porcentajes, interactuando directamente con `EmailService`.

2. **Múltiples Razones para Cambiar:**
   La clase `GestorPedidos` violaba el Principio de Responsabilidad Única al tener al menos 6 motivos de cambio no relacionados: modificación de políticas de stock, variación en criterios de morosidad, ajuste de precios o tarifas de IVA, cambios en promociones de clientes, modificaciones en el esquema relacional SQL o cambios en la plantilla del correo.

3. **Múltiples Niveles de Abstracción Entrelazados:**
   En una misma secuencia de líneas coexistían consultas SQL parametrizadas, llamadas a la API de reloj del sistema, aritmética financiera, manejo transaccional de inventario y formateo de cadenas de texto.

4. **Métodos Privados Accesorios (~340 líneas en total):**
   La clase acumuló métodos como `obtenerHistorialCliente`, `formatearFactura`, `calcularImpuestoRegional`, `reintentarNotificacion` y `purgarPedidosVencidos`, convirtiéndose en un vertedero de utilidades sin cohesión.

---

#### B. Patrones de Diseño Aplicados y Justificación

```
[Cliente] ---> [GestorPedidos (Orquestador)]
                     │
       ┌─────────────┼───────────────┬────────────────┐
       ▼             ▼               ▼                ▼
[Cadena Validac.] [Cálculo Subtotal] [Strategy Dto.] [Repositorio]
(Stock -> Cliente)                    (VIP/Frec/Est)  (Persistencia)
```

1. **Chain of Responsibility para Validaciones:**
   - **Elección:** Se implementó mediante la clase abstracta `ValidadorPedido` y los eslabones concretos `ValidadorStock` y `ValidadorCliente`, operando sobre un `ContextoPedido` compartido.
   - **Justificación:** Las validaciones de un pedido poseen una **dependencia estricta de orden y necesidad de cortocircuito (fail-fast)**. Si un producto carece de existencias (`ValidadorStock`), el flujo debe detenerse de inmediato sin desperdiciar recursos consultando clientes ni facturas de mora (`ValidadorCliente`).
   - **Alternativa Descartada:** Se evaluó utilizar una lista de predicados funcionales (`List<Predicate<ContextoPedido>>`) en un bucle simple. Se descartó porque evalúa todas las condiciones sin modelar de forma expresiva la delegación secuencial ni permitir que un eslabón corte selectivamente el paso al siguiente de forma tipada y extensible.

2. **Strategy para Cálculo de Descuentos por Tipo de Cliente:**
   - **Elección:** Se definió la interfaz `EstrategiaDescuento` con las implementaciones `DescuentoVip`, `DescuentoFrecuente` y `DescuentoEstandar`, administradas por el selector polimórfico `SelectorEstrategiaDescuento`.
   - **Justificación:** A diferencia de las validaciones, los descuentos por tipo de cliente **no dependen de un orden entre sí ni necesitan cortar el flujo**: siempre aplica exactamente una regla mutuamente excluyente según la categoría del cliente. Un mapa asociativo elimina los condicionales anidados y respeta el principio Open/Closed (OCP).
   - **Alternativa Descartada:** Se descartó incluir el cálculo de descuento como un eslabón adicional en la cadena de validación. La cadena tiene semántica de interrupción/rechazo, mientras que el descuento siempre debe ejecutarse para producir una tasa numérica; forzarlo en la cadena desvirtuaría su propósito funcional.

3. **Separación de Capas (Repository y Service):**
   - **`PedidoRepository`:** Centraliza las sentencias SQL de inserción y actualización de inventario bajo la anotación `@Repository`.
   - **`NotificacionPedidoService`:** Centraliza el formateo del mensaje y la delegación al servicio de correo bajo `@Service`.
   - **`GestorPedidos`:** Se transforma en un **orquestador delgado** de menos de 40 líneas que solo coordina el flujo.

---

### Parte 2 — El Sistema Crece: Diagnóstico y Corrección de `Golden Hammer`

#### A. Diagnóstico con Evidencia Concreta del Código Fuente
Tras el éxito de la Parte 1, se requirió agregar tres campañas comerciales:
- `BLACK_FRIDAY`: 25% fijo durante campaña activa.
- `CORPORATIVO`: 10% para clientes con NIT registrado.
- `VOLUMEN`: 12% si el pedido supera 20 unidades.

El desarrollador incurrió en el antipatrón **Golden Hammer** (*Martillo de Oro*): como *Chain of Responsibility* había funcionado muy bien en la Parte 1, resolvió las campañas agregando tres eslabones más a la cadena de validación (`PromocionBlackFriday`, `PromocionCorporativo`, `PromocionVolumen`).

**Evidencia empírica identificada en el código:**

1. **Ausencia Absoluta de Dependencia de Orden y de Corte Anticipado:**
   - En `ValidadorStock` y `ValidadorCliente`, el orden importaba críticamente y el método `contexto.rechazar(...)` interrumpía la cadena.
   - En las promociones, **ninguna clase rechaza el pedido jamás**. Solo aprovechan el paso por la cadena para modificar un estado.
   - El orden entre ellas es irrelevante: evaluar `PromocionVolumen` antes de `PromocionCorporativo` o viceversa produce exactamente el mismo resultado final.

2. **Violación Semántica del Contrato de `ValidadorPedido` (Violación de LSP):**
   - El contrato de `ValidadorPedido` establece que su rol es "decidir si el pedido continúa o se rechaza".
   - Convertir una campaña publicitaria en una subclase de un validador rompe la coherencia conceptual del dominio: una promoción comercial no valida nada; usar la cadena para esto es forzar el problema a la herramienta previa.

3. **Polución de Estado Mutable Compartido en `ContextoPedido`:**
   - Se introdujo artificialmente el campo mutable `private double descuentoCampana = 0;` con el método `aplicarDescuentoCampana(double valor)` para que los eslabones compitieran sobrescribiendo el valor.
   - Este acoplamiento es sumamente frágil: si la regla de negocio cambiase para permitir sumar descuentos acumulativos en lugar de seleccionar el máximo, la cadena generaría ambigüedades y sobreescrituras incontroladas.

---

#### B. Patrón de Diseño Aplicado y Erradicación de `Lava Flow`

```
                      [ContextoPedido]
                             │
            ┌────────────────┴────────────────┐
            ▼                                 ▼
[SelectorEstrategiaDescuento]      [Campañas Promocionales]
  ├── DescuentoVip                   ├── DescuentoBlackFriday
  ├── DescuentoFrecuente             ├── DescuentoCorporativo
  └── DescuentoEstandar              └── DescuentoVolumen
            │                                 │
            └────────────────┬────────────────┘
                             ▼
                [CalculadorDescuentoFinal]
                     Math.max(tipo, campana)
```

1. **Modelado como `EstrategiaDescuento` Puro:**
   - Las tres campañas se modelaron como estrategias (`DescuentoBlackFriday`, `DescuentoCorporativo`, `DescuentoVolumen`), ya que tienen la misma forma funcional: reciben el contexto y calculan una tasa numérica independiente.

2. **Compositor Desacoplado `CalculadorDescuentoFinal`:**
   - Se introdujo un componente `@Component` que evalúa polimórficamente la estrategia del tipo de cliente y las campañas activas mediante Streams funcionales:
     ```java
     double porTipoCliente = selectorPorCliente.seleccionar(contexto.getTipoCliente()).calcular(contexto);
     double porCampana = campanas.stream().mapToDouble(e -> e.calcular(contexto)).max().orElse(0.0);
     return Math.max(porTipoCliente, porCampana);
     ```
   - Elimina cualquier necesidad de mutar variables compartidas dentro de `ContextoPedido`.

3. **Prevención de Lava Flow (Eliminación Total de Código Muerto):**
   - Se **eliminaron completamente** del proyecto los archivos `PromocionBlackFriday.java`, `PromocionCorporativo.java` y `PromocionVolumen.java`, así como el campo `descuentoCampana` de `ContextoPedido`.
   - **Justificación:** No se dejó código comentado "por si acaso". Conservar código descartado como comentarios es la vía directa para engendrar el antipatrón **Lava Flow**, donde fragmentos fosilizados permanecen por miedo a eliminarlos. El historial de Git es el único lugar legítimo para preservar la memoria histórica.

---

## 3. Comparación Antes / Después de la Salida del Sistema

El comportamiento observable del sistema se mantuvo **100% equivalente y reproducible** a través de todas las fases de refactorización, verificado mediante la suite automatizada de pruebas unitarias y de integración:

| Escenario de Prueba | Entrada (Cliente / Ítems) | Salida GestorPedidos Original | Salida Versión Refactorizada (Final) | Estado y Verificación |
|---|---|---|---|---|
| **Ruta 1: Stock Insuficiente** | Cliente 1 (VIP), Producto 101 x 99 unid. (stock 10) | `Rechazado: Stock insuficiente: producto 101` | `Rechazado: Stock insuficiente: producto 101` | **Idéntico** (Corte en `ValidadorStock`) |
| **Ruta 2: Cliente no Registrado** | Cliente 9999 (Inexistente), Producto 103 x 1 unid. | `Rechazado: Cliente no registrado` | `Rechazado: Cliente no registrado` | **Idéntico** (Corte en `ValidadorCliente`) |
| **Ruta 3: Cliente Moroso con Deuda** | Cliente 4 (Moroso, deuda $350k), Producto 104 x 1 unid. | `Rechazado: Cliente con deuda pendiente: $350000.0` | `Rechazado: Cliente con deuda pendiente: $350000.0` | **Idéntico** (Corte en horario normal) |
| **Ruta 4: Descuento VIP** | Cliente 1 (VIP), Laptop $1,500,000 (Subtotal > 1M) | `Confirmado - Subtotal: $1,500,000 - Total: $1,338,750` | `Confirmado - Subtotal: $1,500,000 - Total: $1,338,750` | **Idéntico** (Max entre VIP 15% y BF 25% = 25%) |
| **Ruta 5: Campaña Black Friday** | Cliente 2 (Frecuente), Monitor $600,000 | `Confirmado - Subtotal: $600,000 - Total: $535,500` | `Confirmado - Subtotal: $600,000 - Total: $535,500` | **Idéntico** (Max entre Frec. 8% y BF 25% = 25%) |
| **Ruta 6: Campaña Corporativo** | Cliente 5 (NIT registrado), 2 Teclados $400,000 | `Confirmado - Subtotal: $400,000 - Total: $428,400` | `Confirmado - Subtotal: $400,000 - Total: $428,400` | **Idéntico** (Descuento 10% aplicado con BF inactivo) |
| **Ruta 7: Campaña Volumen** | Cliente 3 (Estándar), 25 Cables USB-C ($250,000) | `Confirmado - Subtotal: $250,000 - Total: $261,800` | `Confirmado - Subtotal: $250,000 - Total: $261,800` | **Idéntico** (Descuento 12% aplicado con BF inactivo) |

---

## 4. Estructura del Proyecto

```
ascanio-post1-u6/
├── pom.xml
├── README.md
├── .gitignore
└── src/
    ├── main/
    │   ├── java/com/tienda/pedidos/
    │   │   ├── PedidosServiceApplication.java      # Arranque y CommandLineRunner demostrativo
    │   │   ├── dto/
    │   │   │   ├── ItemPedido.java                 # DTO de ítem
    │   │   │   ├── PedidoRequest.java              # DTO de solicitud de pedido
    │   │   │   └── ResultadoPedido.java            # DTO de resultado (confirmado/rechazado)
    │   │   ├── validacion/
    │   │   │   ├── ContextoPedido.java             # Contexto limpio del pedido
    │   │   │   ├── ValidadorPedido.java            # Clase base abstracta Chain of Responsibility
    │   │   │   ├── ValidadorStock.java             # Eslabón 1: validación de stock
    │   │   │   └── ValidadorCliente.java           # Eslabón 2: validación de cliente y mora
    │   │   ├── descuento/
    │   │   │   ├── EstrategiaDescuento.java        # Interfaz Strategy
    │   │   │   ├── DescuentoVip.java               # Estrategia VIP (escalas por monto)
    │   │   │   ├── DescuentoFrecuente.java         # Estrategia Frecuente (por pedidos previos)
    │   │   │   ├── DescuentoEstandar.java          # Estrategia Estándar (0%)
    │   │   │   ├── DescuentoBlackFriday.java       # Estrategia Campaña Black Friday (25%)
    │   │   │   ├── DescuentoCorporativo.java       # Estrategia Campaña Corporativo NIT (10%)
    │   │   │   ├── DescuentoVolumen.java           # Estrategia Campaña Volumen > 20 unid. (12%)
    │   │   │   ├── SelectorEstrategiaDescuento.java# Selector de estrategia por tipo de cliente
    │   │   │   └── CalculadorDescuentoFinal.java   # Resolutor de descuento óptimo final
    │   │   └── service/
    │   │       ├── EmailService.java               # Interfaz de servicio de correo
    │   │       ├── EmailServiceImpl.java           # Implementación mock de correo
    │   │       ├── PedidoRepository.java           # Persistencia JDBC en pedidos y detalle
    │   │       ├── NotificacionPedidoService.java  # Servicio de formateo y notificación
    │   │       └── GestorPedidos.java              # Orquestador delgado de 4 capas
    │   └── resources/
    │       ├── application.properties              # Configuración Spring Boot y H2
    │       ├── schema.sql                          # Definición DDL de tablas relacionales
    │       └── data.sql                            # Datos semilla e inserciones idempotentes
    └── test/
        └── java/com/tienda/pedidos/
            ├── GestorPedidosTest.java              # Pruebas integrales de todas las rutas y campañas
            └── CampanasPromocionalesSinBlackFridayTest.java # Pruebas aisladas para Corporativo y Volumen
```

---

## 5. Cómo Ejecutar

### Prerrequisitos
- **Java JDK 17 o 21** configurado en el PATH (`java -version`).
- **Apache Maven 3.8+** instalado (`mvn -v`).

### Compilar y Ejecutar Pruebas Automatizadas
Para ejecutar la suite completa de pruebas unitarias y de integración que verifican todas las rutas de negocio:
```bash
mvn test
```

### Ejecutar la Aplicación y Demostración en Consola
Para iniciar la aplicación Spring Boot con la base de datos H2 en memoria y ejecutar automáticamente el `CommandLineRunner` que procesa los pedidos de prueba por consola:
```bash
mvn spring-boot:run
```

### Consola de Base de Datos H2
Con la aplicación en ejecución, la base de datos interactiva está disponible en:
- **URL:** `http://localhost:8080/h2-console`
- **JDBC URL:** `jdbc:h2:mem:pedidosdb`
- **Usuario:** `sa`
- **Contraseña:** *(vacía)*

---

## 6. Historial de Commits del Repositorio

El repositorio refleja fielmente cada etapa del diagnóstico y refactorización incremental mediante commits semánticos y descriptivos:

| # | Hash | Mensaje de Commit | Descripción del Cambio |
|---|---|---|---|
| **1** | `cb974ed` | `feat: implementar GestorPedidos con validacion, calculo, persistencia y notificacion en un solo metodo` | Implementación base con antipatrones God Object y Spaghetti Code y pruebas iniciales. |
| **2** | `f6a46a9` | `docs: documentar diagnostico de God Object y Spaghetti Code con evidencia del codigo` | Diagnóstico riguroso de la Parte 1 con evidencia de líneas de código y justificación de patrones. |
| **3** | `d23632b` | `refactor: extraer validaciones a Chain of Responsibility y descuentos a Strategy` | Creación de clases de validación (`ValidadorPedido`) y estrategias de descuento (`EstrategiaDescuento`). |
| **4** | `db01f1d` | `refactor: reducir GestorPedidos a orquestador delgado de las cuatro capas` | Extracción de `PedidoRepository`, `NotificacionPedidoService` y adelgazamiento de `GestorPedidos`. |
| **5** | `8796b29` | `feat: agregar 3 campanas de descuento como eslabones de la cadena de validacion` | Simulación del antipatrón Golden Hammer añadiendo promociones como eslabones y mutando el contexto. |
| **6** | `22b6355` | `docs: diagnosticar Golden Hammer en la reutilizacion de Chain of Responsibility para las campanas` | Diagnóstico de Golden Hammer evidenciando falta de orden y ausencia de corte en promociones. |
| **7** | `ad3d6d7` | `refactor: mover las 3 campanas de la cadena de validacion a EstrategiaDescuento` | Corrección migrando campañas a Strategy con `CalculadorDescuentoFinal` y eliminando handlers obsoletos. |
| **8** | `793d151` | `docs: completar README con decisiones de diseño de ambas partes y conclusiones` | Consolidación final de documentación, comparativas, guía de ejecución y conclusiones. |

---

## 7. Herramientas Utilizadas

- **Lenguaje:** Java 17 (ejecutado sobre JDK 21 LTS de Oracle).
- **Framework:** Spring Boot 3.2.5 (Spring Web, Spring JDBC, Spring Test).
- **Base de Datos:** H2 Database Engine 2.2.x en memoria.
- **Herramienta de Construcción:** Apache Maven 3.9.x.
- **Testing:** JUnit Jupiter 5.10.x, Assertions, SpringBootTest.
- **Control de Versiones:** Git 2.55.0, GitHub.
- **IDE:** Visual Studio Code / IntelliJ IDEA Community.

---

## 8. Conclusiones

La realización de esta práctica evidenció que la acumulación desordenada de responsabilidades en una sola clase (**God Object**) combinada con un flujo condicional profundo (**Spaghetti Code**) no solo degrada severamente la legibilidad, sino que destruye la mantenibilidad al hacer imposible modificar reglas comerciales aisladas sin comprometer la persistencia o las validaciones. Asimismo, la experiencia de la Parte 2 demostró el peligro del antipatrón **Golden Hammer**, donde el éxito previo de una solución técnica (*Chain of Responsibility*) condujo a su reutilización acrítica en un problema de naturaleza distinta que carecía de dependencias de orden y corte anticipado, forzando la mutación de estado compartido y distorsionando los contratos polimórficos. Finalmente, la erradicación total de los componentes descartados en lugar de conservarlos como código comentado reafirmó la importancia de prevenir el antipatrón **Lava Flow**, garantizando una base de código limpia, autoexplicativa y gobernada por el historial de versiones en Git.
