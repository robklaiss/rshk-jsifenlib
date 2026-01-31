package com.roshka.sifen.internal.helpers;

import com.roshka.sifen.core.SifenConfig;
import com.roshka.sifen.core.exceptions.SifenException;
import com.roshka.sifen.internal.SOAPResponse;
import com.roshka.sifen.internal.util.SifenExceptionUtil;
import com.roshka.sifen.internal.util.SifenUtil;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.xml.soap.MessageFactory;
import javax.xml.soap.SOAPConstants;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.logging.Logger;

/**
 * Helper encargado de manejar las peticiones SOAP.
 */
public class SoapHelper {
    private final static Logger logger = Logger.getLogger(SoapHelper.class.toString());

    

    /**
     * SOAP 1.1 message (usa text/xml + SOAPAction)
     */
    public static SOAPMessage createSoapMessage11() throws SOAPException {
        MessageFactory mf11 = MessageFactory.newInstance(SOAPConstants.SOAP_1_1_PROTOCOL);
        return mf11.createMessage();
    }
private static void setupHttpURLConnectionProperties(HttpsURLConnection httpsConnection, SifenConfig sifenConfig) {
        httpsConnection.setConnectTimeout(sifenConfig.getHttpConnectTimeout());
        httpsConnection.setReadTimeout(sifenConfig.getHttpReadTimeout());
    }

    private static boolean isConsultaRuc(String urlString) {
        if (urlString == null) return false;
        String u = urlString.toLowerCase();
        return u.contains("consulta-ruc.wsdl") || u.contains("consulta-ruc");
    }

    private static void setupHttpURLConnectionHeaders(HttpsURLConnection httpsConnection, SifenConfig sifenConfig, String urlString) {
        // DEBUG: confirmar routing ConsRUC
        try {
            boolean __isConsRuc = isConsultaRuc(urlString);
            logger.warning("DEBUG: setupHttpURLConnectionHeaders url=" + urlString + " isConsultaRuc=" + __isConsRuc);
        } catch (Exception __e) {
            logger.warning("DEBUG: setupHttpURLConnectionHeaders error evaluando isConsultaRuc: " + __e.getMessage());
        }        // FIX ConsRUC (SIFEN TEST/PROD): ruteo correcto -> SOAP 1.2 Content-Type con action="siConsRUC"
        if (isConsultaRuc(urlString)) {
            httpsConnection.setRequestProperty("Accept", "application/soap+xml, text/xml, */*");
            httpsConnection.setRequestProperty("Content-Type", "application/soap+xml; charset=utf-8; action=\"siConsRUC\"");
            // No setear SOAPAction (eso es SOAP 1.1). En SOAP 1.2 va dentro de Content-Type.
            return;
        }

        httpsConnection.setRequestProperty("User-Agent", sifenConfig.getUserAgent());

        // --- consulta-ruc: casos especiales ---
        if (isConsultaRuc(urlString) && urlString != null && urlString.contains("sifen-test.set.gov.py")) {
            // SIFEN TEST: reproducir request OK (application/xml + Accept tipo navegador + sin SOAPAction)
            httpsConnection.setRequestProperty("User-Agent", "rshk-jsifenlib/0.2.4 (LVEA)");
            httpsConnection.setRequestProperty("Content-Type", "application/xml; charset=utf-8");
            httpsConnection.setRequestProperty("Accept", "text/html, image/gif, image/jpeg, */*; q=0.2");
            httpsConnection.setRequestProperty("Connection", "keep-alive");
            logger.info("HTTP Headers (consulta-ruc TEST) - Content-Type: application/xml; charset=utf-8 ; no SOAPAction");
            return; // IMPORTANT: no pisar con defaults
        } else if (isConsultaRuc(urlString)) {
            // Fuera de TEST: SOAP 1.2 con action y SOAPAction (como venías usando)
            String contentType = "application/soap+xml; charset=utf-8; action=\"siConsRUC\"";
            httpsConnection.setRequestProperty("SOAPAction", "siConsRUC");
            httpsConnection.setRequestProperty("Content-Type", contentType);
            httpsConnection.setRequestProperty("Accept", "application/soap+xml, text/xml, */*");
            logger.info("HTTP Headers (consulta-ruc) - Content-Type: " + contentType + " ; SOAPAction=siConsRUC");
            return; // IMPORTANT: no pisar con defaults
        }

        // --- recibe-lote (async): SOAP 1.2 requiere action=siRecepLoteDE ---
        boolean isRecibeLote = (urlString != null && urlString.contains("/async/recibe-lote"));
        if (isRecibeLote) {
            String contentType = "application/soap+xml; charset=utf-8; action=\"siRecepLoteDE\"";
            httpsConnection.setRequestProperty("Content-Type", contentType);
            httpsConnection.setRequestProperty("Accept", "application/soap+xml, text/xml, */*");
            logger.info("HTTP Headers (recibe-lote) - Content-Type: " + contentType);
            return;
        }

        // Default SOAP 1.2
                // --- consulta-lote: endpoint quisquilloso (forzar close + action) ---
        if (urlString.contains(sifenConfig.getPathConsultaLote()) || urlString.contains("/de/ws/consultas/consulta-lote")) {
            // SOAP 1.2: algunos servers cortan si no viene action en Content-Type
            String contentType = "application/soap+xml; charset=utf-8; action=\"siConsLoteDE\"";
            httpsConnection.setRequestProperty("Content-Type", contentType);
            httpsConnection.setRequestProperty("Accept", "application/soap+xml, text/xml, */*");
            httpsConnection.setRequestProperty("Connection", "close");
            // SOAPAction header vacío (soap12:operation soapAction="") – ayuda a ciertos gateways
            httpsConnection.setRequestProperty("SOAPAction", "");
            logger.info("HTTP Headers (consulta-lote) - Content-Type: " + contentType + " ; Connection=close ; SOAPAction=");
            return;
        }

        String contentType = "application/soap+xml; charset=utf-8";
        httpsConnection.setRequestProperty("Content-Type", contentType);
        httpsConnection.setRequestProperty("Accept", "application/soap+xml, text/xml, */*");
        logger.info("HTTP Headers - Content-Type: " + contentType);
    }

    public static SOAPMessage createSoapMessage() throws SOAPException {
        MessageFactory mf12 = MessageFactory.newInstance(SOAPConstants.SOAP_1_2_PROTOCOL);
        return mf12.createMessage();
    }

public static SOAPMessage createSoapMessage(String urlString) throws SOAPException {
        if (isConsultaRuc(urlString)) {
            MessageFactory mf11 = MessageFactory.newInstance(SOAPConstants.SOAP_1_1_PROTOCOL);
            return mf11.createMessage();
        }
        MessageFactory mf12 = MessageFactory.newInstance(SOAPConstants.SOAP_1_2_PROTOCOL);
        return mf12.createMessage();
    }

    public static SOAPMessage parseSoapMessage(InputStream is)
            throws SOAPException, IOException {
        MessageFactory mf12 = MessageFactory.newInstance(SOAPConstants.SOAP_1_2_PROTOCOL);
        return mf12.createMessage(null, is);
    }


    /**
     * Parser tolerante: detecta SOAP 1.2 vs 1.1 mirando el namespace del Envelope.
     */
    private static SOAPMessage parseSoapMessageAuto(byte[] data) throws SOAPException, IOException {
        String xml = new String(data, java.nio.charset.StandardCharsets.UTF_8);
        MessageFactory mf;
        if (xml.contains("http://www.w3.org/2003/05/soap-envelope")) {
            mf = MessageFactory.newInstance(SOAPConstants.SOAP_1_2_PROTOCOL);
        } else if (xml.contains("http://schemas.xmlsoap.org/soap/envelope/")) {
            mf = MessageFactory.newInstance(SOAPConstants.SOAP_1_1_PROTOCOL);
        } else {
            // fallback: intentar 1.2 primero, luego 1.1
            try {
                mf = MessageFactory.newInstance(SOAPConstants.SOAP_1_2_PROTOCOL);
                return mf.createMessage(null, new java.io.ByteArrayInputStream(data));
            } catch (SOAPException _e) {
                mf = MessageFactory.newInstance(SOAPConstants.SOAP_1_1_PROTOCOL);
            }
        }
        return mf.createMessage(null, new java.io.ByteArrayInputStream(data));
    }
public static SOAPMessage parseSoapMessage(InputStream is, String urlString)
            throws SOAPException, IOException {
        // Preferimos protocolo esperado por endpoint
        try {
            if (isConsultaRuc(urlString)) {
                MessageFactory mf11 = MessageFactory.newInstance(SOAPConstants.SOAP_1_1_PROTOCOL);
                return mf11.createMessage(null, is);
            }
            MessageFactory mf12 = MessageFactory.newInstance(SOAPConstants.SOAP_1_2_PROTOCOL);
            return mf12.createMessage(null, is);
        } catch (SOAPException e) {
            // Fallback: intentar el otro protocolo
            MessageFactory mf11 = MessageFactory.newInstance(SOAPConstants.SOAP_1_1_PROTOCOL);
            return mf11.createMessage(null, is);
        }
    }

    public static SOAPResponse makeSoapRequest(SifenConfig sifenConfig, String urlString, SOAPMessage soapMessage) throws SifenException {
        SOAPResponse soapResponse = new SOAPResponse();
        HttpsURLConnection httpsConnection = null;
        try {
            URL url = new URL(urlString);
            httpsConnection = (HttpsURLConnection) url.openConnection();
            if (url.getProtocol().equalsIgnoreCase("https")) {
                SSLContext sslContext = SSLContextHelper.getContextFromConfig(sifenConfig);
                httpsConnection.setSSLSocketFactory(sslContext.getSocketFactory());
            } else if (!url.getProtocol().equalsIgnoreCase("http")) {
                throw SifenExceptionUtil.invalidSOAPRequest("El protocolo " + url.getProtocol() + " es inválido");
            }

            httpsConnection.setRequestMethod("POST");
            httpsConnection.setDoOutput(true);
            setupHttpURLConnectionProperties(httpsConnection, sifenConfig);
            setupHttpURLConnectionHeaders(httpsConnection, sifenConfig, urlString);


            // Conexión
            logger.info("Conectando a: " + url);
            // Petición
            logger.info("Enviando mensaje SOAP");

                        // --- DEBUG + FIX: evitar chunked y dumpear request real ---
            byte[] __payload;
            try (java.io.ByteArrayOutputStream __baos = new java.io.ByteArrayOutputStream()) {
                soapMessage.writeTo(__baos);
                __payload = __baos.toByteArray();
            }

            // Dump del request (útil para 0160/connection reset)
            try {
                java.nio.file.Path __dir = java.nio.file.Paths.get("build", "tmp", "sifen");
                java.nio.file.Files.createDirectories(__dir);
                String __ts = java.time.LocalDateTime.now().format(
                        java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
                );
                java.nio.file.Path __out = __dir.resolve("soap_request_" + __ts + ".xml");
                java.nio.file.Files.write(__out, __payload);
                logger.warning("DEBUG: SOAP request dump: " + __out.toAbsolutePath());
                logger.warning("DEBUG: SOAP url=" + urlString + " bytes=" + __payload.length);
            } catch (Exception __e) {
                logger.warning("DEBUG: no pude dumpear SOAP request: " + __e.getMessage());
            }

            // Importante: fixed-length para evitar Transfer-Encoding: chunked (a veces corta el server)
            httpsConnection.setFixedLengthStreamingMode(__payload.length);

            try (java.io.OutputStream __os = httpsConnection.getOutputStream()) {
                __os.write(__payload);
                __os.flush();
            }
// Respuesta
            soapResponse.setStatus(httpsConnection.getResponseCode());
            InputStream inputStream;
            if (soapResponse.isRequestSuccessful()) {
                inputStream = httpsConnection.getInputStream();
            } else {
                inputStream = httpsConnection.getErrorStream();
            }

            byte[] readData = SifenUtil.getByteArrayFromInputStream(inputStream);

            // --- DEBUG: guardar SIEMPRE el response (200 y errores) ---
            try {
                java.nio.file.Path __dir = java.nio.file.Paths.get("build", "tmp", "sifen");
                java.nio.file.Files.createDirectories(__dir);
                String __ts = java.time.LocalDateTime.now().format(
                        java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
                );
                java.nio.file.Path __out = __dir.resolve("soap_response_" + soapResponse.getStatus() + "_" + __ts + ".xml");
                java.nio.file.Files.write(__out, readData);
                logger.warning("DEBUG: SOAP response dump: " + __out.toAbsolutePath());
            } catch (Exception __e) {
                logger.warning("DEBUG: no pude dumpear SOAP response: " + __e.getMessage());
            }
// DEBUG_DUMP_SOAP_ERROR_BEGIN
        if (!soapResponse.isRequestSuccessful()) {
            try {
                java.nio.file.Path dir = java.nio.file.Paths.get("build", "tmp", "sifen");
                java.nio.file.Files.createDirectories(dir);
                String ts = java.time.LocalDateTime.now().format(
                        java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
                );
                java.nio.file.Path out = dir.resolve("soap_error_" + soapResponse.getStatus() + "_" + ts + ".xml");
                java.nio.file.Files.write(out, readData);

                String body = new String(readData, java.nio.charset.StandardCharsets.UTF_8);
                logger.warning("SOAP HTTP " + soapResponse.getStatus() + " — response guardada en: " + out.toAbsolutePath());
                logger.warning("SOAP ERROR BODY (primeros 2000 chars):\n" +
                        (body.length() > 2000 ? body.substring(0, 2000) + "…" : body)
                );
            } catch (Exception e) {
                logger.warning("No pude guardar/loguear el SOAP error body: " + e.getMessage());
            }
        }
        // DEBUG_DUMP_SOAP_ERROR_END

            SOAPMessage successSoapMessage = SoapHelper.parseSoapMessageAuto(readData);
soapResponse.setSoapResponse(successSoapMessage);
            soapResponse.setRawData(readData);

            return soapResponse;
        } catch (MalformedURLException e) {
            throw SifenExceptionUtil.invalidSOAPRequest("El URL " + urlString + " es inválido: " + e.getLocalizedMessage(), e);
        } catch (IOException e) {
            throw SifenExceptionUtil.invalidSOAPRequest("Excepción de entrada/salida al realizar llamada SOAP: " + e.getLocalizedMessage(), e);
        } catch (SOAPException e) {
            throw SifenExceptionUtil.invalidSOAPRequest("Excepción de mensajería SOAP: " + e.getLocalizedMessage(), e);
        } finally {
            if (httpsConnection != null)
                httpsConnection.disconnect();
        }
    }
}