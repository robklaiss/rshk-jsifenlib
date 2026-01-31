package com.roshka.sifen.internal.request;

import com.roshka.sifen.core.SifenConfig;
import com.roshka.sifen.core.beans.DocumentoElectronico;
import com.roshka.sifen.core.beans.response.RespuestaRecepcionLoteDE;
import com.roshka.sifen.core.exceptions.SifenException;
import com.roshka.sifen.internal.Constants;
import com.roshka.sifen.internal.SOAPResponse;
import com.roshka.sifen.internal.ctx.GenerationCtx;
import com.roshka.sifen.internal.helpers.SoapHelper;
import com.roshka.sifen.internal.response.BaseResponse;
import com.roshka.sifen.internal.response.SifenObjectFactory;
import com.roshka.sifen.internal.util.ResponseUtil;
import com.roshka.sifen.internal.util.SifenExceptionUtil;
import com.roshka.sifen.internal.util.SifenUtil;
import org.w3c.dom.Node;

import javax.xml.namespace.QName;
import javax.xml.soap.*;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Clase encargada de la petición de Recepción de Lote de Documentos Electrónicos.
 */
public class ReqRecLoteDe extends BaseRequest {
    private List<DocumentoElectronico> DEList;
    private final static Logger logger = Logger.getLogger(ReqRecLoteDe.class.toString());
//    @Value("#{new Boolean('${useReceivedCDC}')}")
//    public Boolean useReceivedCDC;
//    private Boolean useReceivedCDC = true;

    public ReqRecLoteDe(long dId, SifenConfig sifenConfig) {
        super(dId, sifenConfig);
    }

    @Override
    SOAPMessage setupSoapMessage(GenerationCtx generationCtx) throws SifenException {
        try {
            SOAPMessage message = SoapHelper.createSoapMessage();
            SOAPBody soapBody = message.getSOAPBody();

            // Main Element
            SOAPBodyElement rEnvioLote = soapBody.addBodyElement(new QName(Constants.SIFEN_NS_URI, "rEnvioLote"));
            rEnvioLote.addChildElement("dId").setTextContent(String.valueOf(this.getdId()));
            SOAPElement xDE = rEnvioLote.addChildElement("xDE");

            SOAPMessage tmpMsg = SoapHelper.createSoapMessage();
              SOAPBody tmpBody = tmpMsg.getSOAPBody();
              SOAPBodyElement rLoteDE = tmpBody.addBodyElement(new QName(Constants.SIFEN_NS_URI, "rLoteDE"));

                // FIX 0160: declarar xsi + schemaLocation a nivel rLoteDE
                rLoteDE.addNamespaceDeclaration("xsi", "http://www.w3.org/2001/XMLSchema-instance");
                rLoteDE.setAttributeNS("http://www.w3.org/2001/XMLSchema-instance", "xsi:schemaLocation",
                        Constants.SIFEN_NS_URI + " rLoteDE_v150.xsd");


                // FIX: en envío por lote, dVerFor debe existir también a nivel rLoteDE
                rLoteDE.addChildElement("dVerFor").setTextContent("150");
            for (DocumentoElectronico DE : DEList) {
                DE.setupDE(generationCtx, rLoteDE, this.getSifenConfig());
            }
//            FIN CAMBIO

            // Obtenemos el XML
            final StringWriter sw = new StringWriter();
            try {
                Transformer transformer = TransformerFactory.newInstance().newTransformer();
                // Configurar sin indentación para preservar canonicalización XMLDSig
                transformer.setOutputProperty(OutputKeys.INDENT, "no");
                transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "0");
                transformer.transform(new DOMSource(rLoteDE), new StreamResult(sw));
            } catch (TransformerException e) {
                throw new RuntimeException(e);
            }


              // DEBUG_DUMP_RLOTEDE_BEGIN
              try {
                  java.nio.file.Path dir = java.nio.file.Paths.get("build", "tmp", "sifen");
                  java.nio.file.Files.createDirectories(dir);
                  String ts = java.time.LocalDateTime.now().format(
                          java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
                  );

                  String xmlRaw = sw.toString();
                  java.nio.file.Path outXml = dir.resolve("rLoteDE_raw_" + ts + ".xml");
                  java.nio.file.Files.write(outXml, xmlRaw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                  logger.warning("DEBUG: rLoteDE RAW guardado en: " + outXml.toAbsolutePath());

              } catch (Exception e) {
                  logger.warning("DEBUG: no pude guardar rLoteDE RAW: " + e.getMessage());
              }
              // DEBUG_DUMP_RLOTEDE_END
            // Comprimimos a un archivo zip
            byte[] zipFile = SifenUtil.compressXmlToZip(sw.toString());


              // DEBUG_DUMP_RLOTEDE_BEGIN
              try {
                  java.nio.file.Path dir = java.nio.file.Paths.get("build", "tmp", "sifen");
                  java.nio.file.Files.createDirectories(dir);
                  String ts = java.time.LocalDateTime.now().format(
                          java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
                  );
                  java.nio.file.Path outZip = dir.resolve("rLoteDE_zip_" + ts + ".zip");
                  java.nio.file.Files.write(outZip, zipFile);
                  logger.warning("DEBUG: rLoteDE ZIP guardado en: " + outZip.toAbsolutePath());
              } catch (Exception e) {
                  logger.warning("DEBUG: no pude guardar rLoteDE ZIP: " + e.getMessage());
              }
              // DEBUG_DUMP_RLOTEDE_END
            // Convertimos el zip a Base64
            String rLoteDEBase64 = new String(Base64.getEncoder().encode(zipFile), StandardCharsets.UTF_8);
            xDE.setTextContent(rLoteDEBase64);

            return message;
        } catch (SOAPException | IOException e) {
            throw SifenExceptionUtil.requestPreparationError("Ocurrió un error al preparar el cuerpo de la petición SOAP", e);
        }
    }

    @Override
    BaseResponse processResponse(SOAPResponse soapResponse) throws SifenException {
        Node rResEnviLoteDe = null;
        try {
            rResEnviLoteDe = ResponseUtil.getMainNode(soapResponse.getSoapResponse(), "rResEnviLoteDe");
        } catch (SifenException e) {
            logger.warning(e.getMessage());
        }

        RespuestaRecepcionLoteDE respuestaRecepcionLoteDE = new RespuestaRecepcionLoteDE();
        if (rResEnviLoteDe != null) {
            respuestaRecepcionLoteDE = SifenObjectFactory.getFromNode(rResEnviLoteDe, RespuestaRecepcionLoteDE.class);
        }

        respuestaRecepcionLoteDE.setCodigoEstado(soapResponse.getStatus());
        respuestaRecepcionLoteDE.setRespuestaBruta(new String(soapResponse.getRawData(), StandardCharsets.UTF_8));
        return respuestaRecepcionLoteDE;
    }

    public void setDEList(List<DocumentoElectronico> DEList) {
        this.DEList = DEList;
    }

}