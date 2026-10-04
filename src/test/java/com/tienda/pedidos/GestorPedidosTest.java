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
        // Restaurar inventario para asegurar repetibilidad
        jdbcTemplate.update("UPDATE inventario SET stock = 10 WHERE producto_id = 101");
        jdbcTemplate.update("UPDATE inventario SET stock = 15 WHERE producto_id = 102");
        jdbcTemplate.update("UPDATE inventario SET stock = 30 WHERE producto_id = 103");
        jdbcTemplate.update("UPDATE inventario SET stock = 50 WHERE producto_id = 104");
        jdbcTemplate.update("UPDATE inventario SET stock = 500 WHERE producto_id = 105");
        // Asegurar factura de cliente moroso
        jdbcTemplate.update("DELETE FROM facturas WHERE cliente_id = 4");
        jdbcTemplate.update("INSERT INTO facturas (cliente_id, monto, pagada) VALUES (4, 350000.0, false)");
    }

    @Test
    @DisplayName("Ruta 1: Rechazo por stock insuficiente")
    void testStockInsuficiente() {
        // Producto 101 tiene stock 10, solicitamos 99
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
            // Horario normal diurno: rechazo por morosidad
            assertFalse(resultado.isConfirmado());
            assertTrue(resultado.getMotivoRechazo().contains("Cliente con deuda pendiente"));
        } else {
            // Fuera de horario de corte: excepcion permitida
            assertTrue(resultado.isConfirmado());
        }
    }

    @Test
    @DisplayName("Ruta 4: Descuento VIP con subtotal > $1,000,000 (15%)")
    void testDescuentoClienteVip() {
        // Laptop: 1,500,000 -> subtotal = 1,500,000 (> 1,000,000 -> 15% desc)
        // Descuento = 225,000. Base = 1,275,000. Impuesto (19%) = 242,250. Total = 1,517,250.
        PedidoRequest request = new PedidoRequest(1L, "carlos.vip@correo.com",
                List.of(new ItemPedido(101L, 1)));

        ResultadoPedido resultado = gestorPedidos.procesarPedido(request);

        assertTrue(resultado.isConfirmado());
        assertNotNull(resultado.getPedidoId());
        assertEquals(1517250.0, resultado.getTotal(), 0.01);
    }

    @Test
    @DisplayName("Ruta 5: Descuento FRECUENTE con mas de 10 pedidos previos (8%)")
    void testDescuentoClienteFrecuente() {
        // Monitor: 600,000 -> subtotal = 600,000.
        // Cliente 2 tiene 12 pedidos previos en data.sql (> 10 -> 8% desc)
        // Descuento = 48,000. Base = 552,000. Impuesto (19%) = 104,880. Total = 656,880.
        PedidoRequest request = new PedidoRequest(2L, "maria.frecuente@correo.com",
                List.of(new ItemPedido(102L, 1)));

        ResultadoPedido resultado = gestorPedidos.procesarPedido(request);

        assertTrue(resultado.isConfirmado());
        assertNotNull(resultado.getPedidoId());
        assertEquals(656880.0, resultado.getTotal(), 0.01);
    }

    @Test
    @DisplayName("Ruta 6: Cliente ESTANDAR sin descuento (0%)")
    void testClienteEstandarSinDescuento() {
        // Teclado: 200,000 -> subtotal = 200,000.
        // Descuento = 0. Impuesto (19%) = 38,000. Total = 238,000.
        PedidoRequest request = new PedidoRequest(3L, "juan.estandar@correo.com",
                List.of(new ItemPedido(103L, 1)));

        ResultadoPedido resultado = gestorPedidos.procesarPedido(request);

        assertTrue(resultado.isConfirmado());
        assertNotNull(resultado.getPedidoId());
        assertEquals(238000.0, resultado.getTotal(), 0.01);
    }
}
