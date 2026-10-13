# Robots Constructores y Mineros

Mod para **Minecraft Java 1.20.1** con **Forge 47.x** (Java 17).

## Cómo instalarlo (Windows 11)

1. Instala Forge 1.20.1 desde la página oficial: https://files.minecraftforge.net/net/minecraftforge/forge/index_1.20.1.html (botón *Installer*, después *Install client*).
2. Descarga `robotsconstructores-0.1.0.jar` desde la pestaña **Releases** de este repositorio.
3. Pulsa `Windows + R`, escribe `%appdata%\.minecraft\mods` y pulsa Enter. Si la carpeta `mods` no existe, créala.
4. Copia el `.jar` dentro de esa carpeta.
5. Abre el launcher de Minecraft, elige el perfil **forge 1.20.1** y pulsa Jugar.

## Cómo se usa

Los dos robots están en la pestaña creativa **Herramientas y utilidades** (y también en la de huevos de generación).

- **Clic derecho sobre el suelo** con el objeto del robot: colocas el robot.
- **Clic derecho sobre el robot**: abres su interfaz.
- **Mayúsculas + clic derecho con la mano vacía**: recoges el robot. Te da su inventario (lo que no te quepa cae al suelo).
- Si el robot muere, suelta todo su inventario.

### Robot Constructor MK-I
1. Mete al menos **15 bloques de piedra o de roca** en su inventario.
2. Mira hacia donde quieres la pared y pulsa **Iniciar**. Construye una pared de 5 × 3 dos bloques delante del robot, en la dirección en la que miras tú.
3. Coloca un bloque cada medio segundo y gasta los materiales de verdad.
4. Solo construye sobre aire o plantas que se pueden reemplazar (hierba, flores...). Si hay algo en medio, no empieza o se pausa.
5. Tiene botones para pausar, seguir y cancelar.

### Robot Minero MK-I
1. Elige el material con `<` y `>`: piedra, pizarra profunda, carbón, hierro, cobre, oro, redstone, lapislázuli, diamante, esmeralda, cuarzo del Nether, escombros ancestrales o **Muestra**.
   Con **Muestra** se mina el bloque que pongas en la ranura de muestra (sirve cualquier bloque).
2. Escribe la **cantidad** (de 1 a 1728) y pulsa **OK**.
3. Elige el **radio** (de 2 a 16 bloques) con `-` y `+`.
4. Pulsa **Iniciar**. El robot revisa la zona poco a poco (sin congelar el juego) y después rompe los bloques de verdad, uno cada medio segundo.

**Regla de conteo:** se cuentan los objetos reales que caen. Por ejemplo, para el diamante se cuentan diamantes; para la piedra, roca; para el hierro, hierro en bruto. Con la opción Muestra se cuentan todos los objetos que caen. El robot rompe el bloque con un pico de diamante interno **sin Fortuna**. Algunos minerales sueltan varias unidades (redstone, lapislázuli, cobre), así que el total puede pasarse un poco del objetivo, pero nunca empieza un bloque nuevo cuando ya ha llegado.

Cuándo se detiene:
- Si alcanza el objetivo, termina.
- Si se acaban los bloques de la zona, termina y te dice cuántas unidades ha conseguido.
- Si se le llena el inventario, se **pausa sin romper el bloque** (no se pierde nada). Vacíalo con **Recoger** y pulsa **Seguir**.

## Limitaciones de la versión 0.1.0 (honestas)
- **El minero no camina.** Se queda quieto y extrae a distancia los bloques que hay dentro de su radio. Para que se mueva por túneles hace falta añadir: búsqueda de rutas por túneles, excavar el camino, volver al punto de trabajo y descargar en cofres.
- **Todavía no hay cofres conectados.** Para sacar las cosas, usa el inventario del robot (con Mayúsculas + clic, o con el botón Recoger).
- Respeta las protecciones de otros mods (lanza el evento de romper bloque de Forge). Nunca toca bloques con contenido, como cofres u hornos, ni bloques irrompibles.
- **Multijugador:** todas las órdenes las comprueba y las ejecuta el servidor. Aun así, cualquier jugador puede abrir cualquier robot (no hay sistema de dueños con permisos), y no está probado en un servidor real.
- Los modelos y texturas son sencillos. Las texturas se generan con `tools/generate_textures.py`.

## Compilar tú mismo
GitHub lo compila solo con cada cambio (pestaña **Actions**). En tu PC, con Java 17 instalado: `gradlew build`. El archivo queda en `build/libs/`.
