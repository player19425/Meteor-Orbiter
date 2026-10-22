<div align="center">
  <img src="src/main/resources/assets/orbiter/icon.png" alt="Orbiter logo" width="20%"/>
  <h1>Meteor Orbiter</h1>
  <p>Un addon para <a href="https://meteorclient.com/">Meteor Client</a> que añade más de 60 módulos, comandos propios y elementos de HUD para anarquía, griefing y calidad de vida.</p>

  <a href="https://github.com/player19425/Meteor-Orbiter/releases"><img src="https://img.shields.io/badge/Version-1.0.6-orange" alt="Version"></a>
  <img src="https://img.shields.io/badge/Minecraft-26.3%20%7C%2026.2%20%7C%2026.1.2%20%7C%201.21.11-blue" alt="Versiones de Minecraft soportadas">
  <img src="https://img.shields.io/badge/Java-25%20%7C%2021-green" alt="Versión de Java">
  <a href="https://github.com/player19425/Meteor-Orbiter/releases"><img src="https://img.shields.io/github/downloads/player19425/Meteor-Orbiter/total?style=flat" alt="Descargas"></a>
  <a href="https://github.com/player19425/Meteor-Orbiter/stargazers"><img src="https://img.shields.io/github/stars/player19425/Meteor-Orbiter?style=flat&cacheSeconds=3600" alt="Estrellas"></a>
  <a href="https://github.com/player19425/Meteor-Orbiter/commits/26.2"><img src="https://img.shields.io/github/last-commit/player19425/Meteor-Orbiter/26.2?style=flat&cacheSeconds=3600" alt="Último commit"></a>
  <img src="https://img.shields.io/github/languages/code-size/player19425/Meteor-Orbiter?style=flat" alt="Tamaño del código">
  <a href="https://github.com/player19425/Meteor-Orbiter/issues"><img src="https://img.shields.io/github/issues/player19425/Meteor-Orbiter?style=flat" alt="Incidencias"></a>
  <a href="https://github.com/player19425/Meteor-Orbiter/blob/26.2/LICENSE"><img src="https://img.shields.io/github/license/player19425/Meteor-Orbiter?style=flat" alt="Licencia"></a>
</div>
<p align="center">
  <a href="README.md">English</a> &nbsp;&middot;&nbsp; <a href="README.es.md"><b>Español</b></a> &nbsp;&middot;&nbsp; <a href="README.zh.md">中文</a>
</p>

<hr />

# Acerca de

Orbiter es una colección de módulos y herramientas para [Meteor Client](https://meteorclient.com/), pensado originalmente para supervivencia, anarquía y creativo/OP.

Todo está organizado en cinco categorías dentro del juego:

- **Orbiter Survival** • módulos de uso general y contra los abuseos
- **Orbiter Vanilla** • módulos que solo usan mecánicas vanilla
- **Orbiter Creative/OP** • módulos que necesitan modo Creativo o permisos de operador
- **Orbiter Stupid** • módulos de broma / experimentales (actívalos en los ajustes de Meteor Client)
- **Orbiter W.I.P** • módulos en desarrollo, usables pero todavía con aristas

# Requisitos

| Minecraft | Meteor | Java | Plugin de Loom |
|---|---|---|---|
| 26.3 | 26.3 | 25+ | `net.fabricmc.fabric-loom` |
| 26.2 | 26.2 | 25+ | `net.fabricmc.fabric-loom` |
| 26.1.2 | 26.1.2 | 25+ | `net.fabricmc.fabric-loom` |
| 1.21.11 | 1.21.11 | 21+ | `net.fabricmc.fabric-loom` |

En todas las versiones hace falta [Fabric Loader](https://fabricmc.net/use/installer/) 0.19.3 o superior.

Cada versión publicada tiene su propio tag con el proyecto entero en la raíz, así que cualquiera de ellas se puede comprobar y compilar por separado. Esta rama está congelada, la que se sigue actualizando es la `26.3`.

# Compilar

```
gradlew build
```

El jar queda en `build/libs/meteor-orbiter-1.0.6-26.2.jar`.

| Comando | Qué hace |
|---|---|
| `gradlew build` | compila el jar de la versión de Minecraft de `version.properties` |
| `gradlew runClient` | arranca un cliente de desarrollo con el addon cargado |
| `gradlew printTarget` | muestra el par de Minecraft/Meteor al que apunta el proyecto |

# Instalación

1. Descarga la [release](https://github.com/player19425/Meteor-Orbiter/releases) que corresponda a tu versión de Minecraft.
2. mete el `.jar` en la carpeta `.minecraft/mods`. (depende del launcher)
3. Arranca el juego con Fabric y Meteor Client instalados.
4. Ya debería estar instalado correctamente.

Orbiter también se puede mantener actualizado solo: al entrar en un servidor o mundo comprueba si hay una release nueva y te enseña un popup dentro del juego con las opciones de actualizar / ignorar / ver el changelog, e incluso puede descargar e instalar el jar nuevo por ti si lo activas. Todo está en Meteor Config → Orbiter, y los dos interruptores son tuyos (el popup viene activado por defecto).

Cada versión de Minecraft tiene su propia release, así que el comprobador solo te ofrece el jar compilado para la versión que estás usando. Nunca te dará el jar de otro Minecraft.

# Módulos:

## Combate:

| Módulo | Descripción |
|---|---|
| **Aim Assist Plus** | Assistencia de puntería con predicción. Admite puntería silenciosa y ángulo basado en proyectiles. |
| **Anti Knockback** | Sin knockback. |
| **Auto Totem+** | Cambia totems dentro de una sesión de inventario real mientras te quedas quieto. |
| **Mace Assist** | Apunta y golpea automáticamente con la maza. |
| **No Friend Hit** | No golpea a los amigos de Meteor. |
| **Out Of Reach** | Pelea desde fuera del alcance de los jugadores. |

Todos los assists permiten elegir a qué tipos de entidad apuntar (jugadores, armor stands y más).

Los siguientes viven en la categoría Orbiter W.I.P:

| Módulo | Descripción |
|---|---|
| **Bow Assist** (W.I.P) | Apunta el arco automáticamente. |
| **Crossbow Assist** (W.I.P) | Apunta la ballesta automáticamente. |
| **Trident Assist** (W.I.P) | Apunta y lanza el tridente automáticamente. |
| **Spear Assist** (W.I.P) | Asistencia cuerpo a cuerpo. |
| **Shield Assist** (W.I.P) | Se bloquea con el escudo automáticamente. |
| **Precision Shot** (W.I.P) | Puntería silenciosa en la mira. |

## Movimiento:

| Módulo | Descripción |
|---|---|
| **Anti Push** | Para el empuje de fluidos y de entidades. |
| **Auto Clutch** | Clutch automático para evitar el daño de caída. |
| **Force Invisibility** | Invisibilidad del lado del servidor. |
| **Jump A** | Salta muros y alcanza bloques. |
| **Noclip** | Atraviesa bloques sin colisión ni gravedad. Anula los teletransportes correctivos que envía el servidor, así que la posición se mantiene al salir por el otro lado. Baja un poco cada pocos segundos para que el kick de flotación de vanilla nunca salte. |
| **Slime Jump** | Rebota más alto sobre slime. |

## Jugador:

| Módulo | Descripción |
|---|---|
| **Auto Craft Plus** | Fabrica objetos a máxima velocidad. |
| **Client Side Mine** | Rompe bloques al instante desde el cliente. |
| **Restock** | Rellena tu barra rápida automáticamente. |

## Mundo:

| Módulo | Descripción |
|---|---|
| **Auto Farming** | Cultiva y cría automáticamente. |
| **Bed Defender** | Construye un anillo protector alrededor de las camas. |
| **Command Block Placer** | Coloca bloques de comando. Creativo + OP. |
| **Control Player** | Gira a los jugadores a tu alrededor. OP. |
| **Death Override** | Quita la pantalla de muerte cuando se dispara un kill command block. |
| **Entity Spammer** | Genera y anima entidades. Requiere OP. |
| **Item Creator** | Crea objetos a medida. Solo Creativo. |
| **Item Generator** | Genera objetos con encantamientos aleatorios. Creativo. |
| **Operator Nuker** | Destruye bloques en masa con /fill. OP. |
| **RNG Spammer** | Genera loot tables alrededor de los jugadores. OP. |
| **TNT Rain** | Lluvia de TNT. OP. |
| **UUID Ban (W.I.P)** | Expulsa a un jugador por su UUID. OP. |
| **World Downloader** | Guarda el mundo del servidor en local. |
| **World Edit** | WorldEdit del lado del cliente. `.we <comando>` |
| **World Eraser** | Borra bloques en un radio. Pulsa dos veces para activarlo. OP. |

## Render:

| Módulo | Descripción |
|---|---|
| **Block Spam** | Que lluevan los bloques. Lluvia, animación o setblock alrededor de un objetivo. OP. |
| **Block Spoof** | Suplanta las texturas de los bloques en el cliente. |
| **Bossbar Flash** | Parpadea barras de jefe aleatorias. Requiere OP. |
| **Camera 360** | Rotación de cámara sin límites. |
| **Firework Show** | Espectáculos de cohetes con formas y colores. |
| **Particle Control** | Formas de partículas alrededor de un objetivo. |
| **Particle Spam** | Espamea partículas en un radio. Requiere OP. |
| **Playsound Spam** | Espamea todos los sonidos. Requiere OP. |
| **View Blocks** | ESP para bloques invisibles. |

## Varios:

| Módulo | Descripción |
|---|---|
| **Actions** | Disparadores y acciones reactivas. |
| **Anti Staff** | Detecta al staff y sale solo. |
| **Auto Find** | Escanea escondites y bases. |
| **Auto Login** | Responde solo a los prompts de login y registro, al chat y a los diálogos. |
| **Auto Shop** | Compra solo en las tiendas del servidor. |
| **Client Side Things** | Suplantaciones visuales locales. |
| **Enchantment Cracker** | Descifra la semilla oculta de encantamiento de las ofertas de la mesa y dice las tres filas exactas. |
| **Exploit Preventer** | Bloquea exploits habituales del servidor. |
| **I Sell Wand** | Vende solo con la varita de venta. |
| **Infini Reach** | Alcance infinito. |
| **Item Info** | Información extra en los tooltips. |
| **Item Stealer** | Roba objetos de las GUIs. |
| **Leave Message** | Envía un mensaje de chat antes de salir. |
| **Message Formatter** | Formatea el chat que sale. |
| **Peak Plugin Scanner** | Detecta los plugins del servidor. |
| **Ping Spoof** | Suplanta tu ping. |
| **Server Protect** | Bloquea paquetes de crasheo y objetos maliciosos. |
| **Spam Plus** | Spameo de chat con escaleras de letras. |

# Comandos:

| Comando | Descripción | Alias |
|---|---|---|
| `.autoshop` | Activa y controla el módulo AutoShop. | `autoshopdetect` |
| `.copy` | Copia al portapapeles las coordenadas, la posición de cámara, el objeto en la mano, el NBT, la mirada y más. | |
| `.copypos` | Copia al portapapeles la posición de tu cámara (funciona con Freecam). | `cpos` |
| `.escape` | Controla la lógica de escape de ForceInvisibility. | |
| `.enchantmentcracker` | Descifra la semilla de encantamiento, predice cada fila exacta y muestra todas las ofertas. Usa `status` o `reset` como subcomandos. | `enchantcracked`, `encc` |
| `.exportmodulelist` | Exporta al portapapeles los nombres de todos los módulos. | |
| `.fixdeath` | Corta los bucles de muerte falsa y resincroniza el estado cliente/servidor. | |
| `.ghostblock` | Bloques fantasma del lado del cliente que solo ves tú. | `gb` |
| `.givepresetitems` | Más de 200 presets solo para OP y command blocks. | `gpi` |
| `.hidekeybind` | Oculta los keybinds de Meteor en la pantalla de Controles. | |
| `.isellwand` | Controla el módulo ISellWand. | `sellwand` |
| `.itemcrash` | Carga el objeto en la mano con encantamientos, atributos y NBT extremos. | |
| `.itemstealer` | Guarda / carga / gestiona objetos clonados. | `is`, `steal` |
| `.multicommand` | Ejecuta comandos contra varios jugadores con selectores. | |
| `.nbt` | Copia al portapapeles el NBT completo del objeto en la mano. | |
| `.orbitergivepreset` | Da más de 120 objetos especiales con lore. | `ogp` |
| `.peakscan` | Escáner de plugins • detecta los plugins del servidor. | |
| `.setprefix` | Cambia el prefijo de los comandos de chat de Meteor. | |
| `.tntrain` | Lanza la lluvia de TNT con los parámetros que le pases. | |
| `.transfer` | Cambia de servidor sin desconectarte. | |
| `.uuidban` | Banea a un jugador invocando una entidad con su UUID. (W.I.P) | |
| `.verify-protect` | Prueba la detección de objetos de crasheo de ServerProtect contra cargas conocidas. | |
| `.we` | Un clon de worldedit pero que usa /fill. | `worldedit` |

# HUD:

| Elemento | Descripción |
|---|---|
| **Custom Text** | Muestra texto propio en el HUD con placeholders. |
| **Nearest Player** | Muestra el jugador más cercano y su distancia. |
| **Render Distance** | Muestra la distancia de render actual. |
| **Server TPS** | Muestra los ticks por segundo del servidor. |
| **Server Time** | Muestra el día y la hora dentro del juego. |
| **Server Version Note** | Avisa cuando el servidor corre otra versión (protocol bridge). |
| **Server Difficulty** | Muestra la dificultad del mundo. |
| **Server Protocol** | Muestra la versión del protocolo del servidor. |
| **Server Players** | Muestra cuántos jugadores hay conectados. |
| **Server Brand** | Muestra la marca del servidor. |
| **Server Real Version** | Muestra la versión real del servidor sacada de la lista de servidores. |
| **Server Version** | Muestra la versión del servidor. |
| **Server Real IP** | Muestra la IP real de la conexión. |
| **Server IP** | Muestra la IP del servidor (dominio). |
| **Server Plugins** | Muestra cuántos plugins se han detectado. |
| **Weapon Cooldown** | Muestra el enfriamiento actual del ataque, en segundos. |

# Compilar desde el código:

### Requisitos previos:
- [JDK](https://adoptium.net/temurin/releases) 25 o superior
- Gradlew

### Pasos:
```bash
git clone https://github.com/player19425/Meteor-Orbiter
cd Meteor-Orbiter

./gradlew build
```

El JAR compilado quedará en `build/libs/`.

# Seguridad

- Las funciones destructivas piden confirmación explícita y deberían probarse solo en servidores locales desechables con copia de seguridad.
- Los módulos que producen comandos usan detección de capacidades y colas acotadas cuando es posible. Que un comando esté disponible no prueba que tengas permiso.
- Cualquier daño causado por este addon de Meteor Client __no__ es responsabilidad de quien creó el mod, y la responsabilidad es solo de quien hizo la acción.
- La única forma oficial de descargar este mod es [desde su github.](https://github.com/player19425/Meteor-Orbiter/releases/latest) Descargarlo de cualquier otro sitio __no__ es recomendable.


# Créditos

- [Meteor Client](https://github.com/MeteorDevelopment/meteor-client) • el cliente sobre el que se construye este addon.
- Esto se hizo como proyecto personal pero me daba cosa guardarlo ♥️

# Licencia

Este proyecto está bajo la [Licencia MIT](LICENSE).
