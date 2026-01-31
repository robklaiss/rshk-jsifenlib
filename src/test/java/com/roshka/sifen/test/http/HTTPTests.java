package com.roshka.sifen.test.http;

import com.roshka.sifen.core.SifenConfig;
import com.roshka.sifen.core.exceptions.SifenException;
import com.roshka.sifen.internal.helpers.HttpHelper;
import com.roshka.sifen.internal.helpers.SSLContextHelper;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

import org.junit.Test;

public class HTTPTests {

    @Test
    public void testWSDLConnection() throws SifenException {
        String url = "https://sifen-test.set.gov.py/de/ws/consultas/consulta-ruc";

        // IMPORTANT: cargar config desde -D... (System properties)
        SifenConfig cfg = new SifenConfig();

        // tomar -D... y aplicarlo a cfg (mTLS)
        String usar = System.getProperty("sifen.certificado_cliente.usar", "false");
        cfg.setUsarCertificadoCliente(Boolean.parseBoolean(usar));

        String tipo = System.getProperty("sifen.certificado_cliente.tipo", "PFX");
        try {
            cfg.setTipoCertificadoCliente(SifenConfig.TipoCertificadoCliente.valueOf(tipo));
        } catch (Exception ignored) {
            cfg.setTipoCertificadoCliente(SifenConfig.TipoCertificadoCliente.PFX);
        }

        String p12 = System.getProperty("sifen.certificado_cliente.archivo");
        String pass = System.getProperty("sifen.certificado_cliente.contrasena");
        cfg.setCertificadoCliente(p12);
        cfg.setContrasenaCertificadoCliente(pass);


        SSLContext ctx = SSLContextHelper.getContextFromConfig(cfg);
        SSLSocketFactory sf = (ctx != null) ? ctx.getSocketFactory() : null;

        HttpHelper.request(sf, url);
    }
}
