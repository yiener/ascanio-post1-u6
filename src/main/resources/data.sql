-- Limpiar tablas antes de insertar para soportar reinicios de contexto en pruebas
DELETE FROM detalle_pedido;
DELETE FROM pedidos;
DELETE FROM facturas;
DELETE FROM inventario;
DELETE FROM productos;
DELETE FROM clientes;

-- Clientes de prueba
INSERT INTO clientes (id, nombre, email, tipo_cliente, nit) VALUES
(1, 'Carlos VIP', 'carlos.vip@correo.com', 'VIP', NULL),
(2, 'Maria Frecuente', 'maria.frecuente@correo.com', 'FRECUENTE', NULL),
(3, 'Juan Estandar', 'juan.estandar@correo.com', 'ESTANDAR', NULL),
(4, 'Pedro Moroso', 'pedro.moroso@correo.com', 'MOROSO', NULL),
(5, 'Tech Corp SAS', 'compras@techcorp.com', 'ESTANDAR', '900123456-7');

-- Productos de prueba
INSERT INTO productos (id, nombre, precio) VALUES
(101, 'Laptop Profesional', 1500000.0),
(102, 'Monitor 27 Pulgadas', 600000.0),
(103, 'Teclado Mecanico', 200000.0),
(104, 'Mouse Ergonomico', 50000.0),
(105, 'Cable USB-C', 10000.0);

-- Inventario inicial
INSERT INTO inventario (producto_id, stock) VALUES
(101, 10),
(102, 15),
(103, 30),
(104, 50),
(105, 500);

-- Factura pendiente para cliente moroso
INSERT INTO facturas (cliente_id, monto, pagada) VALUES
(4, 350000.0, false);

-- Historial de pedidos previos para cliente FRECUENTE (12 pedidos para activar escala > 10)
INSERT INTO pedidos (cliente_id, subtotal, descuento, impuesto, total, fecha, estado) VALUES
(2, 100000.0, 0.0, 19000.0, 119000.0, CURRENT_TIMESTAMP, 'CONFIRMADO'),
(2, 100000.0, 0.0, 19000.0, 119000.0, CURRENT_TIMESTAMP, 'CONFIRMADO'),
(2, 100000.0, 0.0, 19000.0, 119000.0, CURRENT_TIMESTAMP, 'CONFIRMADO'),
(2, 100000.0, 0.0, 19000.0, 119000.0, CURRENT_TIMESTAMP, 'CONFIRMADO'),
(2, 100000.0, 0.0, 19000.0, 119000.0, CURRENT_TIMESTAMP, 'CONFIRMADO'),
(2, 100000.0, 0.0, 19000.0, 119000.0, CURRENT_TIMESTAMP, 'CONFIRMADO'),
(2, 100000.0, 0.0, 19000.0, 119000.0, CURRENT_TIMESTAMP, 'CONFIRMADO'),
(2, 100000.0, 0.0, 19000.0, 119000.0, CURRENT_TIMESTAMP, 'CONFIRMADO'),
(2, 100000.0, 0.0, 19000.0, 119000.0, CURRENT_TIMESTAMP, 'CONFIRMADO'),
(2, 100000.0, 0.0, 19000.0, 119000.0, CURRENT_TIMESTAMP, 'CONFIRMADO'),
(2, 100000.0, 0.0, 19000.0, 119000.0, CURRENT_TIMESTAMP, 'CONFIRMADO'),
(2, 100000.0, 0.0, 19000.0, 119000.0, CURRENT_TIMESTAMP, 'CONFIRMADO');
