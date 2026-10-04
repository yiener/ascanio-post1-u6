package com.tienda.pedidos.service;

import com.tienda.pedidos.descuento.CalculadorDescuentoFinal;
import com.tienda.pedidos.dto.ItemPedido;
import com.tienda.pedidos.dto.PedidoRequest;
import com.tienda.pedidos.dto.ResultadoPedido;
import com.tienda.pedidos.validacion.ContextoPedido;
import com.tienda.pedidos.validacion.ValidadorCliente;
import com.tienda.pedidos.validacion.ValidadorPedido;
import com.tienda.pedidos.validacion.ValidadorStock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class GestorPedidos {

    private static final Logger log = LoggerFactory.getLogger(GestorPedidos.class);

    private final ValidadorPedido primerValidador;
    private final CalculadorDescuentoFinal calculadorDescuento;
    private final PedidoRepository repository;
    private final NotificacionPedidoService notificacion;
    private final JdbcTemplate jdbcTemplate;

    public GestorPedidos(ValidadorStock stock,
                         ValidadorCliente cliente,
                         CalculadorDescuentoFinal calculadorDescuento,
                         PedidoRepository repository,
                         NotificacionPedidoService notificacion,
                         JdbcTemplate jdbcTemplate) {
        stock.encadenar(cliente);
        this.primerValidador = stock;
        this.calculadorDescuento = calculadorDescuento;
        this.repository = repository;
        this.notificacion = notificacion;
        this.jdbcTemplate = jdbcTemplate;
    }

    public ResultadoPedido procesarPedido(PedidoRequest request) {
        log.info("Iniciando procesamiento de pedido para cliente {}", request.getClienteId());

        ContextoPedido contexto = new ContextoPedido(request);
        primerValidador.validar(contexto);

        if (contexto.isRechazado()) {
            return ResultadoPedido.rechazado(contexto.getMotivoRechazo());
        }

        double subtotal = calcularSubtotal(request);
        contexto.setSubtotal(subtotal);

        double descuento = calculadorDescuento.calcular(contexto);
        double impuesto = (subtotal - subtotal * descuento) * 0.19;
        double total = subtotal - (subtotal * descuento) + impuesto;

        Long pedidoId = repository.guardar(contexto, descuento, impuesto, total);
        notificacion.notificarConfirmacion(contexto, pedidoId, descuento, impuesto, total);

        log.info("Pedido {} confirmado con descuento {}%. Total: {}", pedidoId, (int) (descuento * 100), total);
        return ResultadoPedido.confirmado(pedidoId, total);
    }

    private double calcularSubtotal(PedidoRequest request) {
        double subtotal = 0;
        for (ItemPedido item : request.getItems()) {
            Double precioUnitario = jdbcTemplate.queryForObject(
                    "SELECT precio FROM productos WHERE id = ?",
                    Double.class, item.getProductoId());
            subtotal += (precioUnitario != null ? precioUnitario : 0.0) * item.getCantidad();
        }
        return subtotal;
    }
}
