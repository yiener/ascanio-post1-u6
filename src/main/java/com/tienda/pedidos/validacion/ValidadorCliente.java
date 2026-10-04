package com.tienda.pedidos.validacion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalTime;

// Eslabon 2: existencia y mora del cliente (depende de que el eslabon anterior haya pasado)
@Component
public class ValidadorCliente extends ValidadorPedido {

    private static final Logger log = LoggerFactory.getLogger(ValidadorCliente.class);
    private final JdbcTemplate jdbcTemplate;

    public ValidadorCliente(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    protected void ejecutar(ContextoPedido contexto) {
        Long clienteId = contexto.getRequest().getClienteId();
        String tipo = null;
        try {
            tipo = jdbcTemplate.queryForObject(
                    "SELECT tipo_cliente FROM clientes WHERE id = ?",
                    String.class, clienteId);
        } catch (EmptyResultDataAccessException e) {
            tipo = null;
        }

        if (tipo == null) {
            log.warn("Cliente no encontrado: {}", clienteId);
            contexto.rechazar("Cliente no registrado");
            return;
        }

        contexto.setTipoCliente(tipo);

        if (tipo.equals("MOROSO")) {
            Double deuda = jdbcTemplate.queryForObject(
                    "SELECT SUM(monto) FROM facturas WHERE cliente_id = ? AND pagada = false",
                    Double.class, clienteId);
            boolean fueraDeHorarioDeCorte = !LocalTime.now().isBefore(LocalTime.of(20, 0));
            if (deuda != null && deuda > 0 && !fueraDeHorarioDeCorte) {
                log.warn("Cliente moroso con deuda pendiente: {}", deuda);
                contexto.rechazar("Cliente con deuda pendiente: $" + deuda);
            } else if (deuda != null && deuda > 0 && fueraDeHorarioDeCorte) {
                log.info("Cliente moroso fuera del horario de corte; se permite el pedido excepcionalmente");
            }
        }
    }
}
