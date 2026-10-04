package com.tienda.pedidos;

import com.tienda.pedidos.dto.ItemPedido;
import com.tienda.pedidos.dto.PedidoRequest;
import com.tienda.pedidos.dto.ResultadoPedido;
import com.tienda.pedidos.service.GestorPedidos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

import java.util.List;

@SpringBootApplication
public class PedidosServiceApplication {

    private static final Logger log = LoggerFactory.getLogger(PedidosServiceApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(PedidosServiceApplication.class, args);
    }

    @Bean
    @Profile("!test")
    public CommandLineRunner ejecucionDemostracion(GestorPedidos gestorPedidos) {
        return args -> {
            log.info("================================================================================");
            log.info("DEMOSTRACION INTERACTIVA: SISTEMA DE GESTION DE PEDIDOS REFACTORIZADO");
            log.info("================================================================================");

            // 1. Pedido VIP
            PedidoRequest reqVip = new PedidoRequest(1L, "carlos.vip@correo.com", List.of(new ItemPedido(101L, 1)));
            ResultadoPedido resVip = gestorPedidos.procesarPedido(reqVip);
            log.info(">>> Caso 1 [Cliente VIP]: {}", resVip);

            // 2. Pedido Frecuente
            PedidoRequest reqFrec = new PedidoRequest(2L, "maria.frecuente@correo.com", List.of(new ItemPedido(102L, 1)));
            ResultadoPedido resFrec = gestorPedidos.procesarPedido(reqFrec);
            log.info(">>> Caso 2 [Cliente Frecuente]: {}", resFrec);

            // 3. Pedido Corporativo con NIT
            PedidoRequest reqCorp = new PedidoRequest(5L, "compras@techcorp.com", List.of(new ItemPedido(103L, 2)));
            ResultadoPedido resCorp = gestorPedidos.procesarPedido(reqCorp);
            log.info(">>> Caso 3 [Cliente Corporativo NIT]: {}", resCorp);

            // 4. Pedido Volumen (> 20 unidades)
            PedidoRequest reqVol = new PedidoRequest(3L, "juan.estandar@correo.com", List.of(new ItemPedido(105L, 25)));
            ResultadoPedido resVol = gestorPedidos.procesarPedido(reqVol);
            log.info(">>> Caso 4 [Volumen > 20 unidades]: {}", resVol);

            // 5. Rechazo por Stock Insuficiente
            PedidoRequest reqStock = new PedidoRequest(1L, "carlos.vip@correo.com", List.of(new ItemPedido(101L, 99)));
            ResultadoPedido resStock = gestorPedidos.procesarPedido(reqStock);
            log.info(">>> Caso 5 [Rechazo Stock]: {}", resStock);

            // 6. Rechazo por Cliente Moroso
            PedidoRequest reqMora = new PedidoRequest(4L, "pedro.moroso@correo.com", List.of(new ItemPedido(104L, 1)));
            ResultadoPedido resMora = gestorPedidos.procesarPedido(reqMora);
            log.info(">>> Caso 6 [Validacion Morosidad]: {}", resMora);

            log.info("================================================================================");
            log.info("FIN DE LA DEMOSTRACION - SISTEMA OPERATIVO Y DISPONIBLE");
            log.info("================================================================================");
        };
    }
}
