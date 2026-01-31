package com.roshka.sifen.test.http;

import com.roshka.sifen.core.SifenConfig;
import com.roshka.sifen.internal.helpers.SSLContextHelper;
import org.junit.Test;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class ConsRUCTests {

    @Test
    public void testConsRUC_POST_SOAP12() throws Exception {
        String url = "https://sifen-test.set.gov.py/de/ws/consultas/consulta-ruc.wsdl";

        SifenConfig cfg = new SifenConfig();
        cfg.setUsarCertificadoCliente(Boolean.parseBoolean(System.getProperty("sifen.certificado_cliente.usar", "false")));
        String tipo = System.getProperty("sifen.certificado_cliente.tipo", "PFX");
        try { cfg.setTipoCertificadoCliente(SifenConfig.TipoCertificadoCliente.valueOf(tipo)); }
        catch (Exception ignored) { cfg.setTipoCertificadoCliente(SifenConfig.TipoCertificadoCliente.PFX); }
        cfg.setCertificadoCliente(System.getProperty("sifen.certificado_cliente.archivo"));
        cfg.setContrasenaCertificadoCliente(System.getProperty("sifen.certificado_cliente.contrasena"));

        SSLContext ctx = SSLContextHelper.getContextFromConfig(cfg);

        long now = System.currentTimeMillis() % 1000000000000000L;
        String dId = String.format("%015d", now);

        String xml =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
              + "<soap12:Envelope xmlns:soap12=\"http://www.w3.org/2003/05/soap-envelope\" xmlns:ns0=\"http://ekuatia.set.gov.py/sifen/xsd\">"
              + "  <soap12:Header/>"
              + "  <soap12:Body>"
              + "    <ns0:rEnviConsRUC>"
              + "      <ns0:dId>" + dId + "</ns0:dId>"
              + "      <ns0:dRUCCons>4554737</ns0:dRUCCons>"
              + "    </ns0:rEnviConsRUC>"
              + "  </soap12:Body>"
              + "</soap12:Envelope>";

        byte[] payload = xml.getBytes(StandardCharsets.UTF_8);

        String ts = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
        String reqPath = "backups/consruc_req_" + ts + ".xml";
        String respPath = "backups/consruc_resp_" + ts + ".xml";

        try (FileOutputStream fos = new FileOutputStream(reqPath)) {
            fos.write(payload);
        }

        HttpsURLConnection conn = (HttpsURLConnection) new URL(url).openConnection();
        conn.setSSLSocketFactory(ctx.getSocketFactory());
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setUseCaches(false);

        // CLAVE: evitar chunked -> enviar Content-Length real
        conn.setFixedLengthStreamingMode(payload.length);

        conn.setRequestProperty("Content-Type", "application/soap+xml; charset=utf-8; action=\"siConsRUC\"");
        conn.setRequestProperty("Accept", "application/soap+xml, text/xml, */*");
        conn.setRequestProperty("SOAPAction", "siConsRUC");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        conn.setRequestProperty("Connection", "close");
        conn.setRequestProperty("Content-Length", String.valueOf(payload.length));

        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload);
        }

        int code = conn.getResponseCode();
        System.out.println("REQ=" + reqPath + " (" + payload.length + " bytes)");
        System.out.println("HTTP CODE: " + code);
        System.out.println("Resp Content-Type: " + conn.getHeaderField("Content-Type"));

        InputStream is = (code >= 400) ? conn.getErrorStream() : conn.getInputStream();
        if (is != null) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);

            byte[] respBytes = baos.toByteArray();
            try (FileOutputStream fos = new FileOutputStream(respPath)) {
                fos.write(respBytes);
            }

            System.out.println("OUT=" + respPath + " (" + respBytes.length + " bytes)");
            System.out.println(new String(respBytes, StandardCharsets.UTF_8));
        } else {
            System.out.println("OUT=<null stream>");
        }
    }
}
