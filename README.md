# TraceRF

**Encontrá la señal.**

TraceRF es una aplicación Android para búsqueda RF en vivo. Usa las APIs estándar del teléfono para observar Wi‑Fi y Bluetooth Low Energy, clasificar emisiones y ayudar a seguir tendencias de intensidad sin convertir RSSI en una distancia o dirección falsa.

## Diferencial

TraceRF no está pensado como inventario ni como registrador. Está diseñado como buscador de proximidad:

- referencia temporal del entorno;
- filtro **Solo nuevos**;
- diferencia de RSSI contra la referencia;
- seguimiento de una señal con tendencia, vibración y sonido;
- modo domicilio;
- modo vehículo con captura por sectores;
- cámaras / IoT;
- trackers / beacons;
- drones / Remote ID BLE;
- desconocidos;
- modo experto con UUID, manufacturer data y datos crudos cuando Android los expone.

## Privacidad

- Sin cuenta.
- Sin backend.
- Sin nube.
- Sin base histórica de señales.
- Sin guardado de causas, domicilios o ubicaciones.
- Cada búsqueda vive en memoria y **Finalizar y borrar sesión** descarta los datos temporales.

## Límites

TraceRF solo puede mostrar emisiones que el hardware y Android permitan observar. No detecta dispositivos apagados, objetos sin radio, clientes Wi‑Fi invisibles al escaneo normal ni fuentes que utilicen protocolos/frecuencias fuera del alcance del teléfono. Una clasificación o un RSSI no identifican al propietario ni prueban una ubicación exacta.

TraceRF no intenta emparejarse, autenticarse, interceptar contenido, desautenticar, interferir ni conectarse a los equipos observados.

## Compilación

El repositorio incluye un workflow **Build TraceRF APK**. Cada push a `main` compila `app-debug.apk` y lo publica como artifact `TraceRF-v1.0.0-debug`.

## Requisitos

- Android 10 o superior (`minSdk 29`).
- Bluetooth LE para detección BLE.
- Wi‑Fi para escaneo de puntos de acceso.
- Permisos de Android para Bluetooth y escaneo Wi‑Fi/ubicación según la versión del sistema.

## Licencia

MIT.
