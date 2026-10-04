package com.tienda.pedidos.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class EmailServiceImpl implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailServiceImpl.class);

    @Override
    public void enviar(String destinatario, String asunto, String cuerpo) {
        log.info("Simulacion de envio de email a: {}\nAsunto: {}\nCuerpo:\n{}", destinatario, asunto, cuerpo);
    }
}
