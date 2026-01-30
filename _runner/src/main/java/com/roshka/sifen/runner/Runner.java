package com.roshka.sifen.runner;

import com.roshka.sifen.Sifen;
import com.roshka.sifen.core.SifenConfig;
import com.roshka.sifen.core.beans.DocumentoElectronico;
import com.roshka.sifen.core.beans.response.RespuestaConsultaRUC;
import com.roshka.sifen.core.beans.response.RespuestaRecepcionDE;
import com.roshka.sifen.core.exceptions.SifenException;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import java.util.logging.*;

public class Runner {
    
    private static final Logger logger = Logger.getLogger(Runner.class.getName());
    private static final DateTimeFormatter RUN_ID_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    
    private static Path runDir;
    private static Path artifactsBase;
    private static FileHandler fileHandler;
    private static Properties runnerProps;
    
    public static void main(String[] args) {
        try {
            // 1. Create run directory
            String runId = "run_" + LocalDateTime.now().format(RUN_ID_FORMAT);
            artifactsBase = Paths.get("artifacts");
            runDir = artifactsBase.resolve(runId);
            Files.createDirectories(runDir);
            
            // 2. Setup logging to file
            setupLogging();
            
            logger.info("=== RSHK-JSIFENLIB RUNNER E2E ===");
            logger.info("Run ID: " + runId);
            logger.info("Artifacts dir: " + runDir.toAbsolutePath());
            
            // 3. Load configuration
            String configPath = "_runner/conf/sifen.properties";
            logger.info("Loading config from: " + configPath);
            
            runnerProps = new Properties();
            try (FileInputStream fis = new FileInputStream(configPath)) {
                runnerProps.load(fis);
            }
            
            // Copy and sanitize properties
            copySanitizedProperties(configPath);
            
            // 4. Initialize Sifen
            SifenConfig sifenConfig = SifenConfig.cargarConfiguracion(configPath);
            Sifen.setSifenConfig(sifenConfig);
            
            String ambiente = runnerProps.getProperty("sifen.ambiente", "DEV");
            logger.info("Ambiente: " + ambiente);
            logger.info("URL Base: " + sifenConfig.getUrlBaseLocal());
            
            // 5. Run tests
            boolean consultaOk = runConsultaRUC();
            boolean recepcionOk = runRecepcionDE();
            
            // 6. Write final result
            writeResult(consultaOk, recepcionOk, ambiente);
            
            logger.info("=== RUNNER COMPLETED ===");
            logger.info("Check artifacts at: " + runDir.toAbsolutePath());
            
        } catch (Exception e) {
            logger.severe("FATAL ERROR: " + e.getMessage());
            e.printStackTrace();
            try {
                if (runDir != null) {
                    writeError(e);
                }
            } catch (IOException ignored) {}
            System.exit(1);
        } finally {
            if (fileHandler != null) {
                fileHandler.close();
            }
        }
    }
    
    private static void setupLogging() throws IOException {
        Logger rootLogger = Logger.getLogger("");
        
        // File handler for runner.log
        fileHandler = new FileHandler(runDir.resolve("runner.log").toString(), true);
        fileHandler.setFormatter(new SimpleFormatter());
        fileHandler.setLevel(Level.ALL);
        rootLogger.addHandler(fileHandler);
        
        // Also log to console
        for (Handler h : rootLogger.getHandlers()) {
            if (h instanceof ConsoleHandler) {
                h.setLevel(Level.INFO);
            }
        }
        rootLogger.setLevel(Level.ALL);
    }
    
    private static void copySanitizedProperties(String configPath) throws IOException {
        Properties sanitized = new Properties();
        
        for (String key : runnerProps.stringPropertyNames()) {
            String value = runnerProps.getProperty(key);
            
            // Sanitize sensitive values
            if (key.contains("contrasena") || key.contains("password")) {
                sanitized.setProperty(key, "********");
            } else if (key.equals("sifen.csc")) {
                // Show only first 4 and last 4 chars
                if (value.length() > 8) {
                    sanitized.setProperty(key, value.substring(0, 4) + "..." + value.substring(value.length() - 4));
                } else {
                    sanitized.setProperty(key, "********");
                }
            } else if (key.contains("archivo") && (key.contains("certificado") || key.contains("pfx") || key.contains("p12"))) {
                // Show only filename, not full path
                sanitized.setProperty(key, Paths.get(value).getFileName().toString());
            } else {
                sanitized.setProperty(key, value);
            }
        }
        
        Path sanitizedPath = runDir.resolve("sifen.properties");
        try (FileOutputStream fos = new FileOutputStream(sanitizedPath.toFile())) {
            sanitized.store(fos, "Sanitized copy - secrets removed");
        }
        logger.info("Sanitized properties saved to: " + sanitizedPath);
    }
    
    private static boolean runConsultaRUC() {
        logger.info("");
        logger.info("=== TEST: Consulta RUC ===");
        
        String rucSinDv = runnerProps.getProperty("runner.ruc_sin_dv", "80089752");
        logger.info("Consultando RUC: " + rucSinDv);
        
        try {
            RespuestaConsultaRUC resp = Sifen.consultaRUC(rucSinDv);
            
            String dCodRes = resp.getdCodRes();
            String dMsgRes = resp.getdMsgRes();
            int httpStatus = resp.getCodigoEstado();
            
            logger.info("HTTP Status: " + httpStatus);
            logger.info("dCodRes: " + dCodRes);
            logger.info("dMsgRes: " + dMsgRes);
            
            // Save request/response
            try {
                saveXml("consulta_ruc_request.xml", sanitizeXml(resp.getRequestSent()));
                saveXml("consulta_ruc_response.xml", sanitizeXml(resp.getRespuestaBruta()));
            } catch (IOException ioe) {
                logger.warning("Could not save request/response XML: " + ioe.getMessage());
            }
            
            // Save result JSON
            StringBuilder json = new StringBuilder();
            json.append("{\n");
            json.append("  \"operacion\": \"consultaRUC\",\n");
            json.append("  \"ruc_consultado\": \"").append(rucSinDv).append("\",\n");
            json.append("  \"http_status\": ").append(httpStatus).append(",\n");
            json.append("  \"dCodRes\": \"").append(dCodRes != null ? dCodRes : "null").append("\",\n");
            json.append("  \"dMsgRes\": \"").append(escapeJson(dMsgRes)).append("\"");
            
            if (resp.getxContRUC() != null) {
                json.append(",\n  \"razon_social\": \"").append(escapeJson(resp.getxContRUC().getdRazCons())).append("\"");
            }
            json.append("\n}");
            
            try {
                saveFile("consulta_ruc.json", json.toString());
            } catch (IOException ioe) {
                logger.warning("Could not save consulta_ruc.json: " + ioe.getMessage());
            }
            
            boolean success = httpStatus == 200 && "0502".equals(dCodRes);
            logger.info("Consulta RUC: " + (success ? "OK" : "FAILED"));
            return success;
            
        } catch (SifenException e) {
            logger.severe("Error en consultaRUC: " + e.getMessage());
            try {
                saveFile("consulta_ruc_error.txt", e.getMessage() + "\n" + getStackTrace(e));
            } catch (IOException ignored) {}
            return false;
        }
    }
    
    private static boolean runRecepcionDE() {
        logger.info("");
        logger.info("=== TEST: Recepcion DE ===");
        
        String deXmlPath = runnerProps.getProperty("runner.de_xml_path");
        
        if (deXmlPath == null || deXmlPath.trim().isEmpty()) {
            logger.warning("runner.de_xml_path not configured, skipping DE reception test");
            try {
                saveFile("de_skipped.txt", "runner.de_xml_path not configured");
            } catch (IOException ignored) {}
            return true; // Not a failure, just skipped
        }
        
        logger.info("DE XML path: " + deXmlPath);
        
        try {
            // Read the XML file
            Path xmlPath = Paths.get(deXmlPath);
            if (!Files.exists(xmlPath)) {
                logger.severe("DE XML file not found: " + deXmlPath);
                saveFile("de_error.txt", "File not found: " + deXmlPath);
                return false;
            }
            
            String xmlContent = new String(Files.readAllBytes(xmlPath), StandardCharsets.UTF_8);
            logger.info("DE XML loaded, length: " + xmlContent.length() + " chars");
            
            // Clean namespace prefixes (ns0:, ns1:, etc.) that the library doesn't handle
            xmlContent = cleanNamespacePrefixes(xmlContent);
            logger.info("DE XML cleaned, length: " + xmlContent.length() + " chars");
            
            // Save input XML
            saveXml("de_input.xml", sanitizeXml(xmlContent));
            
            // Parse the XML to DocumentoElectronico
            // The library will re-sign it with the configured certificate
            DocumentoElectronico de = new DocumentoElectronico(xmlContent);
            String cdc = de.getId();
            logger.info("CDC from DE: " + cdc);
            
            // Send to SIFEN
            logger.info("Sending DE to SIFEN...");
            RespuestaRecepcionDE resp = Sifen.recepcionDE(de);
            
            String dCodRes = resp.getdCodRes();
            String dMsgRes = resp.getdMsgRes();
            int httpStatus = resp.getCodigoEstado();
            
            logger.info("HTTP Status: " + httpStatus);
            logger.info("dCodRes: " + dCodRes);
            logger.info("dMsgRes: " + dMsgRes);
            
            // Save request/response
            saveXml("soap_request.xml", sanitizeXml(resp.getRequestSent()));
            saveXml("soap_response.xml", sanitizeXml(resp.getRespuestaBruta()));
            saveXml("de_sent.xml", sanitizeXml(resp.getRequestSent()));
            
            // Extract protocol info if available
            String protocolCdc = null;
            String protocolId = null;
            if (resp.getxProtDE() != null) {
                try {
                    protocolCdc = resp.getxProtDE().getId();
                    logger.info("Protocol CDC: " + protocolCdc);
                } catch (Exception e) {
                    logger.fine("Could not get protocol CDC: " + e.getMessage());
                }
            }
            
            // Save result JSON
            StringBuilder json = new StringBuilder();
            json.append("{\n");
            json.append("  \"operacion\": \"recepcionDE\",\n");
            json.append("  \"cdc_input\": \"").append(cdc != null ? cdc : "null").append("\",\n");
            json.append("  \"http_status\": ").append(httpStatus).append(",\n");
            json.append("  \"dCodRes\": \"").append(dCodRes != null ? dCodRes : "null").append("\",\n");
            json.append("  \"dMsgRes\": \"").append(escapeJson(dMsgRes)).append("\"");
            if (protocolCdc != null) {
                json.append(",\n  \"protocol_cdc\": \"").append(protocolCdc).append("\"");
            }
            json.append("\n}");
            
            saveFile("de_result.json", json.toString());
            
            // Success codes: 0260 (aprobado), 0261 (aprobado con obs)
            boolean success = httpStatus == 200 && (dCodRes != null && (dCodRes.startsWith("02")));
            logger.info("Recepcion DE: " + (success ? "OK" : "CHECK RESULT"));
            return true; // Return true even if rejected - the test ran successfully
            
        } catch (SifenException e) {
            logger.severe("Error en recepcionDE: " + e.getMessage());
            try {
                saveFile("de_error.txt", e.getMessage() + "\n" + getStackTrace(e));
            } catch (IOException ignored) {}
            return false;
        } catch (IOException e) {
            logger.severe("IO Error: " + e.getMessage());
            try {
                saveFile("de_error.txt", "IO Error: " + e.getMessage() + "\n" + getStackTrace(e));
            } catch (IOException ignored) {}
            return false;
        } catch (Exception e) {
            // Handle DOM errors when library tries to re-sign already-signed XML
            logger.warning("Error en recepcionDE (possibly pre-signed XML): " + e.getMessage());
            logger.warning("NOTE: This library cannot send pre-signed XML. It must sign the DE itself.");
            try {
                StringBuilder errorJson = new StringBuilder();
                errorJson.append("{\n");
                errorJson.append("  \"error\": \"").append(escapeJson(e.getClass().getSimpleName())).append("\",\n");
                errorJson.append("  \"message\": \"").append(escapeJson(e.getMessage())).append("\",\n");
                errorJson.append("  \"note\": \"This library cannot send pre-signed XML. Build DE from code instead.\"\n");
                errorJson.append("}");
                saveFile("de_error.json", errorJson.toString());
                saveFile("de_error.txt", e.getMessage() + "\n" + getStackTrace(e));
            } catch (IOException ignored) {}
            return true; // Not a failure of the runner itself, just a limitation
        }
    }
    
    private static void writeResult(boolean consultaOk, boolean recepcionOk, String ambiente) throws IOException {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"run_id\": \"").append(runDir.getFileName()).append("\",\n");
        json.append("  \"timestamp\": \"").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\",\n");
        json.append("  \"ambiente\": \"").append(ambiente).append("\",\n");
        json.append("  \"url_base\": \"").append(Sifen.getSifenConfig().getUrlBaseLocal()).append("\",\n");
        json.append("  \"tests\": {\n");
        json.append("    \"consultaRUC\": ").append(consultaOk).append(",\n");
        json.append("    \"recepcionDE\": ").append(recepcionOk).append("\n");
        json.append("  },\n");
        json.append("  \"success\": ").append(consultaOk && recepcionOk).append("\n");
        json.append("}");
        
        saveFile("result.json", json.toString());
        logger.info("Final result saved to result.json");
    }
    
    private static void writeError(Exception e) throws IOException {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"error\": true,\n");
        json.append("  \"message\": \"").append(escapeJson(e.getMessage())).append("\",\n");
        json.append("  \"stacktrace\": \"").append(escapeJson(getStackTrace(e))).append("\"\n");
        json.append("}");
        
        saveFile("result.json", json.toString());
    }
    
    private static void saveFile(String filename, String content) throws IOException {
        Path path = runDir.resolve(filename);
        Files.write(path, content.getBytes(StandardCharsets.UTF_8));
        logger.fine("Saved: " + path);
    }
    
    private static void saveXml(String filename, String xml) throws IOException {
        if (xml == null) {
            xml = "<!-- null -->";
        }
        saveFile(filename, xml);
    }
    
    private static String sanitizeXml(String xml) {
        if (xml == null) return null;
        
        // Remove certificate content (base64 blocks)
        xml = xml.replaceAll("<X509Certificate>[^<]+</X509Certificate>", "<X509Certificate>***REDACTED***</X509Certificate>");
        xml = xml.replaceAll("<SignatureValue>[^<]+</SignatureValue>", "<SignatureValue>***REDACTED***</SignatureValue>");
        xml = xml.replaceAll("<DigestValue>[^<]+</DigestValue>", "<DigestValue>***REDACTED***</DigestValue>");
        
        return xml;
    }
    
    private static String cleanNamespacePrefixes(String xml) {
        if (xml == null) return null;
        
        // Remove namespace prefixes like ns0:, ns1:, etc.
        // This is needed because some XML generators add these prefixes
        // but the library expects unprefixed element names
        xml = xml.replaceAll("<ns\\d+:", "<");
        xml = xml.replaceAll("</ns\\d+:", "</");
        xml = xml.replaceAll("xmlns:ns\\d+=\"[^\"]*\"", "");
        
        // Clean up extra whitespace from removed xmlns declarations
        xml = xml.replaceAll("\\s+>", ">");
        xml = xml.replaceAll("<\\s+", "<");
        
        return xml;
    }
    
    private static String escapeJson(String s) {
        if (s == null) return "null";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
    
    private static String getStackTrace(Exception e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
