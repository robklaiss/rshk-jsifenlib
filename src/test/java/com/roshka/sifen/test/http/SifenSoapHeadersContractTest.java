package com.roshka.sifen.test.http;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

/**
 * Anti-regresión (compatible con este repo):
 * Verifica que SoapHelper siga forzando SOAP 1.2 con action correcto por endpoint,
 * para evitar ruteo equivocado (y rechazos tipo rRetEnviDe/0160).
 *
 * Nota: es un "source contract test": no pega a SIFEN, valida que el código mantenga las reglas.
 */
public class SifenSoapHeadersContractTest {

    @Test
    public void soapHelperMustKeepConsRucActionRouting() throws Exception {
        String src = readSoapHelperSource();

        assertTrue("SoapHelper debe detectar consulta-ruc",
                src.contains("consulta-ruc"));

        assertTrue("SoapHelper debe mantener action=siConsRUC para ConsRUC (Content-Type SOAP 1.2)",
                src.contains("action=\\\"siConsRUC\\\"") || src.contains("action=\"siConsRUC\""));
    }

    @Test
    public void soapHelperMustKeepRecibeLoteActionRouting() throws Exception {
        String src = readSoapHelperSource();

        assertTrue("SoapHelper debe detectar recibe-lote async",
                src.contains("/async/recibe-lote"));

        assertTrue("SoapHelper debe mantener action=siRecepLoteDE para recibe-lote (Content-Type SOAP 1.2)",
                src.contains("action=\\\"siRecepLoteDE\\\"") || src.contains("action=\"siRecepLoteDE\""));
    }

    private static String readSoapHelperSource() throws Exception {
        Path p = Path.of("src/main/java/com/roshka/sifen/internal/helpers/SoapHelper.java");
        assertTrue("No encuentro SoapHelper.java en la ruta esperada: " + p, Files.exists(p));
        return Files.readString(p, StandardCharsets.UTF_8);
    }
}
