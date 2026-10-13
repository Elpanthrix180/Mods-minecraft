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
Elige el plano con `<` y `>`. Debajo verás qué materiales necesita.

**Pared de piedra 5×3:** necesita 15 bloques de piedra o de roca.

**Casa 7×7:** tiene paredes de 4 bloques de alto, techo plano, 4 ventanas y una puerta en el centro de la fachada. El suelo es el propio terreno.
- Paredes y techo: tablones de cualquier madera, roca, piedra, ladrillos de piedra, ladrillos o pizarra. Puede mezclarlos.
- Ventanas: cristal. Si no tiene, pone material de pared.
- Puerta: cualquier puerta de madera (opcional; si no tiene, deja el hueco).

Cómo construir:
1. Mete los materiales en el inventario del robot.
2. Ponte detrás del robot, mirando hacia donde quieres la construcción, y pulsa **Iniciar**. Construye a 2 bloques delante del robot.
3. Pone un bloque cada medio segundo, de abajo arriba, y gasta los materiales de verdad.
4. Solo construye sobre aire o plantas que se pueden reemplazar. Si hay algo en medio, no empieza y te dice dónde está el estorbo.
5. Puedes pausar, seguir y cancelar. Si guardas y sales a mitad, al volver sigue donde iba.

### Robot Minero MK-I
1. Elige el material con `<` y `>`: piedra, pizarra profunda, carbón, hierro, cobre, oro, redstone, lapislázuli, diamante, esmeralda, cuarzo del Nether, escombros ancestrales o **Muestra**.
   Con **Muestra** se mina el bloque que pongas en la ranura de muestra (sirve cualquier bloque).
2. Escribe la **cantidad** (de 1 a 1728) y pulsa **OK**.
3. Elige el **radio** (de 2 a 16 bloques) con `-` y `+`.
4. Pulsa **Iniciar**. El robot revisa la zona poco a poco (sin congelar el juego) y después **camina hasta el bloque más cercano** y lo rompe de verdad.
   - Si el bloque está enterrado, cava un **túnel en escalera** de 2 bloques de alto hasta él. Lo que suelta al cavar también se guarda, pero no cuenta para el objetivo.
   - No cava junto al agua ni a la lava. Si no puede llegar a un bloque en 2 minutos, lo salta y te avisa.
   - Al terminar, o si se llena, **vuelve a su punto de trabajo**.
5. **Guardar en cofres:** pon un cofre o un barril a 4 bloques o menos del sitio donde colocaste el robot, y pulsa el botón **Cofre: No** para que cambie a **Cofre: Sí**.
   Cada vez que el robot vuelva a su puesto, guardará todo su almacén en el cofre. Si había vuelto porque estaba lleno, sigue minando él solo. Si el cofre se llena, se queda en pausa y te avisa.

**Regla de conteo:** se cuentan los objetos reales que caen. Por ejemplo, para el diamante se cuentan diamantes; para la piedra, roca; para el hierro, hierro en bruto. Con la opción Muestra se cuentan todos los objetos que caen. El robot rompe el bloque con un pico de diamante interno **sin Fortuna**. Algunos minerales sueltan varias unidades (redstone, lapislázuli, cobre), así que el total puede pasarse un poco del objetivo, pero nunca empieza un bloque nuevo cuando ya ha llegado.

Cuándo se detiene:
- Si alcanza el objetivo, termina.
- Si se acaban los bloques de la zona, termina y te dice cuántas unidades ha conseguido.
- Si se le llena el inventario, **no rompe el bloque** (no se pierde nada), vuelve a su punto de trabajo y se pausa. Vacíalo con **Recoger** y pulsa **Seguir**.

## Limitaciones de la versión 0.1.0 (honestas)
- Para **trepar** en vertical hace una torre: salta y pone un bloque bajo sus pies, igual que un jugador. Usa roca, tierra, pizarra, etc. de su inventario, pero nunca el material que estás pidiendo. Si no tiene, te avisa.
- Si se queda atascado al volver (por ejemplo en un agujero), cava o trepa hasta su puesto. Tiene 3 minutos; si no lo consigue, se queda donde está.
- No recibe daño por caídas, por asfixia ni por ahogarse; sí recibe daño de la lava y de los monstruos.
- Respeta las protecciones de otros mods (lanza el evento de romper bloque de Forge). Nunca toca bloques con contenido, como cofres u hornos, ni bloques irrompibles.
- **Multijugador:** todas las órdenes las comprueba y las ejecuta el servidor. Aun así, cualquier jugador puede abrir cualquier robot (no hay sistema de dueños con permisos), y no está probado en un servidor real.
- Los modelos y texturas son sencillos. Las texturas se generan con `tools/generate_textures.py`.

## Compilar tú mismo
GitHub lo compila solo con cada cambio (pestaña **Actions**). En tu PC, con Java 17 instalado: `gradlew build`. El archivo queda en `build/libs/`.
