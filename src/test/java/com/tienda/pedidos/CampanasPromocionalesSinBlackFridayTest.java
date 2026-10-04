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
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = "promo.black-friday.activa=false")
public class CampanasPromocionalesSinBlackFridayTest {

    @Autowired
    private GestorPedidos gestorPedidos;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void restaurarEstado() {
        jdbcTemplate.update("UPDATE inventario SET stock = 30 WHERE producto_id = 103");
        jdbcTemplate.update("UPDATE inventario SET stock = 500 WHERE producto_id = 105");
    }

    @Test
    @DisplayName("Campana Corporativo aislada (10%) sin Black Friday")
    void testCampanaCorporativoAislada() {
        // Cliente 5 tiene NIT '900123456-7'. Black Friday inactivo.
        // 2 Teclados: subtotal = 400,000. Descuento 10% = 40,000. Base = 360,000. IVA (19%) = 68,400. Total = 428,400.
        PedidoRequest request = new PedidoRequest(5L, "compras@techcorp.com",
                List.of(new ItemPedido(103L, 2)));

        ResultadoPedido resultado = gestorPedidos.procesarPedido(request);

        assertTrue(resultado.isConfirmado());
        assertEquals(428400.0, resultado.getTotal(), 0.01);
    }

    @Test
    @DisplayName("Campana Volumen aislada (12%) sin Black Friday")
    void testCampanaVolumenAislada() {
        // 25 cables USB-C a $10,000 = 250,000. Unidades: 25 > 20.
        // Descuento 12% = 30,000. Base = 220,000. IVA (19%) = 41,800. Total = 261,800.
        PedidoRequest request = new PedidoRequest(3L, "juan.estandar@correo.com",
                List.of(new ItemPedido(105L, 25)));

        ResultadoPedido resultado = gestorPedidos.procesarPedido(request);

        assertTrue(resultado.isConfirmado());
        assertEquals(261800.0, resultado.getTotal(), 0.01);
    }
}
