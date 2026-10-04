package com.tienda.pedidos.validacion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

// Eslabon 1: existencia de stock (un solo motivo de rechazo, una sola responsabilidad)
@Component
public class ValidadorStock extends ValidadorPedido {

    private static final Logger log = LoggerFactory.getLogger(ValidadorStock.class);
    private final JdbcTemplate jdbcTemplate;

    public ValidadorStock(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    protected void ejecutar(ContextoPedido contexto) {
        if (contexto.getRequest().getItems() == null || contexto.getRequest().getItems().isEmpty()) {
            log.warn("Pedido rechazado: sin items para cliente {}", contexto.getRequest().getClienteId());
            contexto.rechazar("El pedido no contiene items");
            return;
        }

        for (var item : contexto.getRequest().getItems()) {
            Integer stock = null;
            try {
                stock = jdbcTemplate.queryForObject(
                        "SELECT stock FROM inventario WHERE producto_id = ?",
                        Integer.class, item.getProductoId());
            } catch (EmptyResultDataAccessException e) {
                stock = null;
            }

            if (stock == null || stock < item.getCantidad()) {
                log.warn("Stock insuficiente para producto {}", item.getProductoId());
                contexto.rechazar("Stock insuficiente: producto " + item.getProductoId());
                return;
            }
        }
    }
}
