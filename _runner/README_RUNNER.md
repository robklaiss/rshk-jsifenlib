# RSHK-JSIFENLIB Runner E2E

Mini-proyecto runner para pruebas end-to-end de la librería SIFEN.

## Requisitos

- Java 8+
- La librería principal compilada (`../build/libs/rshk-jsifenlib-0.2.4.jar`)

## Configuración

1. Editar `conf/sifen.properties` con tus datos:
   - Certificado PFX y contraseña
   - CSC y CSC ID
   - RUC a consultar
   - (Opcional) Path a XML de DE para enviar

2. Ver `conf/sifen.properties.example` como referencia.

## Compilar y Ejecutar

### Paso 1: Compilar la librería principal (desde raíz del repo)

```bash
cd /Users/robinklaiss/Dev/rshk-jsifenlib
./gradlew clean build -x test
```

### Paso 2: Ejecutar el runner

```bash
cd /Users/robinklaiss/Dev/rshk-jsifenlib/_runner
../gradlew clean build -x test && java -jar build/libs/runner-1.0.0.jar
```

O en un solo comando desde la raíz:

```bash
cd /Users/robinklaiss/Dev/rshk-jsifenlib && ./gradlew clean build -x test && cd _runner && ../gradlew clean build -x test && java -jar build/libs/runner-1.0.0.jar
```

## Output

Los resultados se guardan en:
```
artifacts/run_YYYYMMDD_HHMMSS/
├── runner.log              # Log completo
├── sifen.properties        # Config SANITIZADA (sin secretos)
├── consulta_ruc.json       # Resultado consultaRUC
├── consulta_ruc_request.xml
├── consulta_ruc_response.xml
├── de_input.xml            # XML DE de entrada (si aplica)
├── de_sent.xml             # XML enviado (SANITIZADO)
├── de_result.json          # Resultado recepcionDE
├── soap_request.xml        # Request SOAP (SANITIZADO)
├── soap_response.xml       # Response SOAP (SANITIZADO)
└── result.json             # Resultado final
```

## Códigos de Respuesta Esperados

### consultaRUC
- `0502`: RUC encontrado (éxito)
- `0501`: RUC no encontrado

### recepcionDE
- `0260`: DE aprobado
- `0261`: DE aprobado con observación
- `03xx`: Errores de validación

## Notas de Seguridad

- Los archivos en `artifacts/` son SANITIZADOS (sin contraseñas, certificados, CSC completo)
- El archivo `conf/sifen.properties` contiene secretos - NO commitear
- Agregar `_runner/conf/sifen.properties` a `.gitignore`
