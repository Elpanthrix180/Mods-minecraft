# Panthrix's Robots

Mod para **Minecraft Java 1.20.1** con **Forge 47.x** (Java 17).

## Cómo instalarlo (Windows 11)

1. Instala Forge 1.20.1 desde la página oficial: https://files.minecraftforge.net/net/minecraftforge/forge/index_1.20.1.html (botón *Installer*, después *Install client*).
2. Descarga `panthrixs-robots-0.1.0.jar` desde la pestaña **Releases** de este repositorio.
3. Pulsa `Windows + R`, escribe `%appdata%\.minecraft\mods` y pulsa Enter. Si la carpeta `mods` no existe, créala.
4. Copia el `.jar` dentro de esa carpeta.
5. Abre el launcher de Minecraft, elige el perfil **forge 1.20.1** y pulsa Jugar.

## Recetas (mesa de crafteo)

**Varita de Robots** (palo abajo a la izquierda, en diagonal):
```
 .  E  A        A = fragmento de amatista
 .  R  E        E = perla de ender
 S  .  .        R = redstone,  S = palo
```
**Robot Constructor:**
```
 I A I          I = lingote de hierro, A = fragmento de amatista
 R P R          R = redstone, P = pistón
 I B I          B = ladrillos
```
**Robot Minero:** igual, pero con un **pico de hierro** en lugar de los ladrillos.

## Lava y agua
El minero puede cavar junto a la **lava** y el **agua**, y también a través de ellas. Antes de cavar, **las tapa** con roca, tierra, rocanegra, piedra infernal, etc. de su inventario, como haría un jugador con cuidado. Así no se derraman por los túneles ni queman lo que lleva.
- Necesita bloques de relleno en el inventario. Normalmente ya los tiene de cavar. Si no tiene, te avisa y deja esa parte.
- Atraviesa los lagos de lava tapándolos casilla a casilla.
- Lo único que no puede tapar es el agua que está dentro de otro bloque (por ejemplo, una escalera con agua). Esa parte la salta.

## Construir en agua y lava
El constructor puede colocar bloques **dentro del agua y de la lava**: el bloque ocupa el sitio del líquido. Sirve para muros en el mar, casas sobre la lava, etc.
- Los **caminos** que cruzan agua o lava hacen un **puente** sobre la superficie.
- Las puertas, escaleras y escaleras de mano que coloca dentro del agua quedan **mojadas** (con agua dentro), como en el juego normal.
- Los robots **no se ahogan** bajo el agua. Recuerda que fuera del Nether la lava sí les hace daño: deja el robot en un sitio seco y que construya a distancia.

## En el Nether
Los robots son **ignífugos en el Nether**: allí el fuego y la lava no les hacen daño. En el mundo normal y en el End sí les afectan. 

## Mando de Robots
Receta:
```
 .  A  .        A = fragmento de amatista
 I  R  I        I = lingote de hierro, R = redstone
 I  E  I        E = perla de ender
```
- **Clic derecho:** trae hasta ti **todos tus robots que no están trabajando** (los que han terminado o no tienen trabajo).
- Funciona aunque estén **lejos** o en **otra dimensión**. Si están en una zona que no está cargada, el mando la carga unos segundos, trae el robot y la vuelve a liberar.
- Los robots que están **trabajando o en pausa se quedan** donde están, para no estropear su trabajo.
- Solo trae **tus** robots: los que colocaste tú.

## Varita de Robots
- **Clic derecho sobre un robot:** lo guarda dentro de la varita, con todo su inventario y su configuración. Caben hasta 8. (Si está trabajando o en pausa, primero cancela o deja que termine.)
- **Clic derecho sobre un bloque:** saca el robot elegido y lo pone ahí.
- **Mayús + clic derecho al aire:** elige otro de los robots guardados.
- Pasa el ratón por encima de la varita para ver qué robots lleva. La varita brilla cuando tiene robots y no se quema en la lava.

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

**Casa con techo en pico:** la misma casa, pero con un tejado a dos aguas hecho de escaleras (con alero de 1 bloque) y fachadas triangulares.
- 114 bloques de pared, 72 escaleras de cualquier tipo para el tejado (si no tienes, usa bloques de pared), 5 de cristal y 1 puerta (opcional).

**Torre 5×5:** tiene paredes de 10 bloques de alto, puerta, 8 ventanas, escalera de mano por dentro, plataforma arriba con un hueco para subir y almenas.
- 182 bloques de pared (los mismos materiales que la casa), 8 de cristal, 1 puerta y 10 escaleras de mano. La puerta y las escaleras son opcionales.

**Muralla:** muro de 2 bloques de grosor y 5 de alto que va de lado a lado delante del robot.
- Elige el **largo** (de 4 a 64) con `-` y `+`.
- Tiene almenas en la cara exterior y una escalera de mano en el centro de la cara interior para subir arriba.
- Material: el mismo que la casa (tablones, roca, piedra, ladrillos...). Con largo 16 son 168 bloques más 5 escaleras de mano (opcionales).
- Necesita terreno llano: si hay algo en medio, te dice dónde.

**Plano (estructura .nbt):** copia cualquier construcción.
1. Haz o busca la construcción y guárdala con un **bloque de estructuras** (`/give @s structure_block`, modo *Guardar*, ponle un nombre, por ejemplo `casa1`, y pulsa *GUARDAR*). También puedes copiar archivos `.nbt` en la carpeta `.minecraft\robotsconstructores\planos\`.
2. En el robot elige **Plano**, escribe el nombre (`casa1`) y pulsa **OK**.
3. Mete los materiales. Si falta algo, el robot te dice en el chat exactamente qué y cuánto.
4. Ponte detrás del robot mirando hacia donde quieres la construcción y pulsa **Iniciar**. La copia a 2 bloques delante, girada hacia donde miras, de abajo arriba y con cada bloque en su orientación.
- Tamaño máximo: 64 bloques de lado. No copia lo que hay dentro de los cofres, ni el agua o la lava.

**Imagen (pixel art .png):** convierte una imagen en un mural.
1. Copia la imagen en `.minecraft\robotsconstructores\imagenes\` (la carpeta se crea sola al abrir el juego con el mod).
2. En el robot elige **Imagen**, escribe el nombre (por ejemplo `logo` o `logo.png`) y pulsa **OK**.
3. Cada píxel se convierte en el **hormigón** del color más parecido (16 colores). Si no tienes hormigón de un color, usa la **lana** del mismo color. Las partes transparentes se quedan vacías. Si la imagen es mayor de 64×64, se reduce.
4. Construye el mural de pie, a 2 bloques delante del robot. El robot te dice cuántos bloques de cada color necesita.

**Caminos de cualquier tipo:**
- **Camino (bloque de muestra):** pon en la ranura de muestra (a la derecha, debajo del nombre del plano) el bloque que quieras: grava, piedra lisa, tablones, ladrillos, losas, cualquiera. Mete en el inventario bloques iguales.
- **Senda de tierra:** convierte el césped y la tierra en senda, como una pala. No gasta nada.
- Elige el **largo** (de 4 a 64) y el **ancho** (1, 3 o 5). El camino empieza justo delante del robot y va hacia donde miras.
- Sigue la forma del terreno: sube y baja hasta 4–5 bloques. Solo cambia suelo natural (césped, tierra, arena, grava, piedra...). Lo que quita lo guarda en su inventario, así que no se pierde nada. Si encuentra un hueco sin suelo, hace un puente a la altura del robot.

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
5. **Modo Tiras (strip mining):** pulsa el botón **Zona** para que cambie a **Tiras**. Ahora el robot no busca en una zona, sino que:
   - Cava un **túnel principal** de 2 bloques de alto hacia donde miras tú al pulsar Iniciar. El largo es el valor de **Túnel** (de 8 a 64; se cambia con `-` y `+`).
   - Cada 3 bloques abre **galerías** de 8 bloques a izquierda y derecha.
   - Si ve el material elegido en las paredes, el suelo o el techo, lo saca y **sigue la veta**.
   - Tapa los agujeros del suelo con roca o tierra. Si le das **antorchas**, pone una cada 8 bloques del túnel.
   - Si una galería llega al agua o a la lava, la deja y pasa a la siguiente.
   - Termina al llegar a la cantidad pedida o al acabar el túnel, y vuelve a su puesto (guarda en el cofre si está activado).
   - Consejo: colócalo en la capa del mineral que buscas (por ejemplo, para diamantes, en Y = -58).
6. **Guardar en cofres:** pon un cofre o un barril a 4 bloques o menos del sitio donde colocaste el robot, y pulsa el botón **Cofre: No** para que cambie a **Cofre: Sí**.
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
