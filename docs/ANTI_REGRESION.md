# ANTI-REGRESIÓN (SIFEN) — rshk-jsifenlib

## 2026-01-30 — ConsRUC: 0160 por `Transfer-Encoding: chunked`
**Síntoma**
- Respuesta con `dCodRes=0160` / `XML Mal Formado` aunque el XML parezca correcto.

**Causa real**
- El servidor puede rechazar requests SOAP cuando el cliente envía el body con **Transfer-Encoding: chunked** (sin Content-Length).

**Regla**
- Para ConsRUC: **NO usar chunked**.
- Enviar siempre con **Content-Length** / fixed-length.

**Implementación (Java)**
- Construir `byte[] body = xml.getBytes(StandardCharsets.UTF_8);`
- Antes de `getOutputStream()`:
  - `conn.setFixedLengthStreamingMode(body.length);`
- Luego escribir `body` al stream.

**Notas**
- El WSDL en test puede requerir mTLS para descargarse; sin cert puede redirigir/bloquear.
- En WSDL la operación puede venir con `soapAction=""` (no depender de SOAPAction).

## Paths (repo)
- Repo root: /Users/robinklaiss/Dev/rshk-jsifenlib
- Docs: /Users/robinklaiss/Dev/rshk-jsifenlib/docs

## Regla clave
Antes de cambiar lógica SOAP/headers:
1) Reproducir con test de bajo nivel (src/test/java/com/roshka/sifen/test/http)
2) Guardar request/response en backups/
3) Confirmar nodo esperado:
   - ConsRUC: rResEnviConsRUC
   - Recepción DE: rRetEnviDe (o equivalente)
   - Recepción lote: rResEnviLoteDe (y capturar dNroLote)
4) Solo luego refactorizar a helpers compartidos.

## Evidencia requerida
- backups/*req*.xml
- backups/*resp*.xml
- logs con HTTP code + Content-Type
MD