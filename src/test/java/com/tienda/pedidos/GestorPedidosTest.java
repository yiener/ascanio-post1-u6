package com.tienda.pedidos;

import com.tienda.pedidos.dto.ItemPedido;
import com.tienda.pedidos.dto.PedidoRequest;
import com.tienda.pedidos.dto.ResultadoPedido;
import com.tienda.pedidos.service.GestorPedidos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class GestorPedidosTest {

    @Autowired
    private GestorPedidos gestorPedidos;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void restaurarEstado() {
        jdbcTemplate.update("UPDATE inventario SET stock = 10 WHERE producto_id = 101");
        jdbcTemplate.update("UPDATE inventario SET stock = 15 WHERE producto_id = 102");
        jdbcTemplate.update("UPDATE inventario SET stock = 30 WHERE producto_id = 103");
        jdbcTemplate.update("UPDATE inventario SET stock = 50 WHERE producto_id = 104");
        jdbcTemplate.update("UPDATE inventario SET stock = 500 WHERE producto_id = 105");
        jdbcTemplate.update("DELETE FROM facturas WHERE cliente_id = 4");
        jdbcTemplate.update("INSERT INTO facturas (cliente_id, monto, pagada) VALUES (4, 350000.0, false)");
    }

    @Test
    @DisplayName("Ruta 1: Rechazo por stock insuficiente")
    void testStockInsuficiente() {
        PedidoRequest request = new PedidoRequest(1L, "carlos.vip@correo.com",
                List.of(new ItemPedido(101L, 99)));

        ResultadoPedido resultado = gestorPedidos.procesarPedido(request);

        assertFalse(resultado.isConfirmado());
        assertNotNull(resultado.getMotivoRechazo());
        assertTrue(resultado.getMotivoRechazo().contains("Stock insuficiente"));
    }

    @Test
    @DisplayName("Ruta 2: Rechazo por cliente no registrado")
    void testClienteNoRegistrado() {
        PedidoRequest request = new PedidoRequest(9999L, "desconocido@correo.com",
                List.of(new ItemPedido(103L, 1)));

        ResultadoPedido resultado = gestorPedidos.procesarPedido(request);

        assertFalse(resultado.isConfirmado());
        assertEquals("Cliente no registrado", resultado.getMotivoRechazo());
    }

    @Test
    @DisplayName("Ruta 3: Validacion de cliente moroso con deuda pendiente")
    void testClienteMorosoConDeuda() {
        PedidoRequest request = new PedidoRequest(4L, "pedro.moroso@correo.com",
                List.of(new ItemPedido(103L, 1)));

        ResultadoPedido resultado = gestorPedidos.procesarPedido(request);

        LocalTime ahora = LocalTime.now();
        if (ahora.isBefore(LocalTime.of(20, 0))) {
            assertFalse(resultado.isConfirmado());
            assertTrue(resultado.getMotivoRechazo().contains("Cliente con deuda pendiente"));
        } else {
            assertTrue(resultado.isConfirmado());
        }
    }

    @Test
    @DisplayName("Ruta 4: Descuento VIP con subtotal > $1,000,000 frente a campana Black Friday")
    void testDescuentoClienteVip() {
        // Laptop: 1,500,000 -> subtotal = 1,500,000.
        // Descuento VIP: 15%. Campana Black Friday activa: 25%.
        // Con la integracion de campanas, aplica el mayor: 25% (0.25).
        // Descuento = 375,000. Base = 1,125,000. Impuesto (19%) = 213,750. Total = 1,338,750.
        PedidoRequest request = new PedidoRequest(1L, "carlos.vip@correo.com",
                List.of(new ItemPedido(101L, 1)));

        ResultadoPedido resultado = gestorPedidos.procesarPedido(request);

        assertTrue(resultado.isConfirmado());
        assertNotNull(resultado.getPedidoId());
        assertEquals(1338750.0, resultado.getTotal(), 0.01);
    }

    @Test
    @DisplayName("Ruta 5: Campana Black Friday activa (25%) aplicada a cliente frecuente")
    void testCampanaBlackFriday() {
        // Monitor: 600,000 -> subtotal = 600,000.
        // Descuento FRECUENTE: 8%. Black Friday: 25%. Aplica el mayor: 25%.
        // Total = 600,000 * 0.75 * 1.19 = 535,500.
        PedidoRequest request = new PedidoRequest(2L, "maria.frecuente@correo.com",
                List.of(new ItemPedido(102L, 1)));

        ResultadoPedido resultado = gestorPedidos.procesarPedido(request);

        assertTrue(resultado.isConfirmado());
        assertEquals(535500.0, resultado.getTotal(), 0.01);
    }

    @Test
    @DisplayName("Ruta 6: Campana Corporativo (10%) para cliente con NIT")
    void testCampanaCorporativo() {
        // Cliente 5 tiene NIT '900123456-7'.
        // Cuando Black Friday esta activo (25%), 25% > 10%, por lo que gana 25%.
        PedidoRequest request = new PedidoRequest(5L, "compras@techcorp.com",
                List.of(new ItemPedido(103L, 2)));

        ResultadoPedido resultado = gestorPedidos.procesarPedido(request);

        assertTrue(resultado.isConfirmado());
        // Subtotal = 400,000. Descuento 25% = 300,000 * 1.19 = 357,000
        assertEquals(357000.0, resultado.getTotal(), 0.01);
    }

    @Test
    @DisplayName("Ruta 7: Campana Volumen (> 20 unidades)")
    void testCampanaVolumen() {
        // 25 cables USB-C (producto 105 a $10,000 c/u) -> 25 unidades > 20.
        // Subtotal = 250,000. Black Friday (25%) supera a volumen (12%).
        // 250,000 * 0.75 * 1.19 = 223,125.0
        PedidoRequest request = new PedidoRequest(3L, "juan.estandar@correo.com",
                List.of(new ItemPedido(105L, 25)));

        ResultadoPedido resultado = gestorPedidos.procesarPedido(request);

        assertTrue(resultado.isConfirmado());
        assertEquals(223125.0, resultado.getTotal(), 0.01);
    }
}
