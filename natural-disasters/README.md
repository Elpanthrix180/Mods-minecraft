# Panthrix's Natural Disasters

Mod para **Minecraft Java 1.20.1** con **Forge 47.x** (Java 17).
Mod ID: `panthrixsnaturaldisasters` · Paquete Java: `com.panthrix.naturaldisasters`

Añade catástrofes naturales aleatorias que cambian el clima, afectan a las criaturas y, si lo permites, modifican el terreno.

> **Estado:** los 12 fenómenos están programados y el mod **compila** (GitHub Actions).
> Las pruebas dentro del juego (lista de la sección 8) **las tiene que hacer el usuario**: no se han podido ejecutar desde el entorno de desarrollo.

---

## 1. Instalación (un jugador, Windows 11)

1. Instala **Java 17** si no lo tienes: https://adoptium.net/temurin/releases/?version=17 (Windows x64, `.msi`).
2. Instala **Forge 1.20.1**: https://files.minecraftforge.net/net/minecraftforge/forge/index_1.20.1.html → *Installer* → *Install client*.
3. Descarga `panthrixs-natural-disasters-0.1.0.jar` de la pestaña **Releases** del repositorio
   (versión «Panthrix's Natural Disasters (…)»).
4. Pulsa `Windows + R`, escribe `%appdata%\.minecraft\mods` y pulsa Enter (crea la carpeta `mods` si no existe).
5. Copia el JAR dentro. Abre el launcher, elige el perfil **forge 1.20.1** y pulsa Jugar.
6. En el menú principal → **Mods** debe aparecer *Panthrix's Natural Disasters*.

## 2. Instalación en un servidor Forge 1.20.1

1. Descarga el instalador de Forge 1.20.1 (el mismo enlace) y elige **Install server** en una carpeta vacía.
2. Ejecuta `run.bat` una vez, acepta el EULA (`eula.txt` → `eula=true`) y vuelve a ejecutarlo.
3. Copia el JAR en la carpeta `mods` del servidor y reinícialo.
4. **Recomendado:** que los jugadores también instalen el mod. Si no lo tienen, pueden entrar, pero no verán la vibración de cámara.
5. Los comandos de administración necesitan ser operador: en la consola del servidor escribe `op TuNombre`.
6. La configuración del servidor está en `config/panthrixsnaturaldisasters-common.toml` dentro de la carpeta del servidor.

## 3. Copias de seguridad (¡hazlas antes de probar la destrucción!)

- **Un jugador:** cierra Minecraft. Copia la carpeta del mundo desde `%appdata%\.minecraft\saves\NombreDelMundo` a otro sitio (por ejemplo el Escritorio).
  Para restaurar, borra la carpeta del mundo y vuelve a pegar la copia.
- **Servidor:** detén el servidor (`stop`) y copia la carpeta `world`.
- El estado del mod se guarda dentro del mundo, en `data/panthrixsnaturaldisasters_state.dat` (calendario y evento activo) y `data/panthrixsnaturaldisasters_volcanoes.dat` (volcanes conocidos).

## 4. Comandos

| Comando | Quién | Qué hace |
|---|---|---|
| `/pnd help` | todos | Lista de comandos y fenómenos |
| `/pnd status` | todos | Sistema activado o no, catástrofe activa, fase y segundos restantes |
| `/pnd next` | todos | Cuándo llega la próxima catástrofe |
| `/pnd start <fenómeno>` | admin | Inicia un fenómeno (empieza por el aviso) |
| `/pnd stop` | admin | Detiene la catástrofe al instante y deshace lo temporal |
| `/pnd enable` / `/pnd disable` | admin | Activa o desactiva las catástrofes aleatorias |
| `/pnd config` | admin | Muestra la configuración actual |
| `/pnd volcano` | admin | Volcanes conocidos y el más cercano |
| `/pnd volcano create` | admin | Crea un volcán pequeño donde estás (modifica el terreno) |

Fenómenos: `tornado`, `blizzard`, `earthquake`, `flood`, `volcano`, `thunderstorm`, `landslide`, `hailstorm`, `drought`, `meteor_shower`, `black_hole`, `ground_cracks`.

## 5. Cómo funciona el director

- Cada **5–20 días de Minecraft** (1 día = 24.000 ticks = 20 minutos reales) elige un fenómeno al azar según los pesos.
- No repite el último si hay otros activados. Nunca hay dos a la vez.
- Fases: **Calma → Aviso → Preparación → Activo → Finalización → Recuperación → Calma**.
- Ocurre cerca de un jugador al azar que esté en el mundo normal (no en el Nether ni el End).
- Todo se guarda en el mundo: al reiniciar, el calendario y el evento continúan donde estaban.
- Si un fenómeno da un error, se cancela, se escribe en el registro (`logs/latest.log`) y se programa el siguiente.

## 6. Guía de configuración (frecuencia e intensidad)

Archivo: `.minecraft\config\panthrixsnaturaldisasters-common.toml` (se crea la primera vez que arrancas el juego).
Ábrelo con el Bloc de notas **con el juego cerrado**, cambia los valores y guarda.

**Frecuencia**
- `[calendario] diasMinimos` / `diasMaximos`: días entre catástrofes. Ejemplo: `1` y `2` para probar mucho.
- `[fenomenos.<nombre>] peso`: probabilidad relativa. `0` = nunca al azar. Con `activado = false` también se desactiva.
- `[fenomenos.<nombre>] duracionSegundos`: duración de la fase activa.
- `[volcan] rareza`: volcanes naturales (más alto = menos volcanes).

**Intensidad y destrucción**
- `[general] intensidad`: `SOFT` (suave), `MODERATE` (moderada), `EXTREME` (extrema).
- `[general] destruccion`:
  - `OFF` (por defecto): **ningún bloque se modifica**. Solo hay partículas, sonidos, clima, empujones y daño.
  - `PARTIAL`: grietas, deslizamientos, agua de inundación, tornado que arranca tierra, etc.
  - `INTENSE`: además zanjas, cuevas nuevas, troncos arrancados y cristales rotos. **Puede afectar al rendimiento.**
- `[general] maxBloquesPorEvento`: límite total de bloques que un evento puede tocar (por defecto 1000).
- `[general] radio`: tamaño de la zona afectada.
- `[general] danoJugadores` / `danoCriaturas`: si los fenómenos hacen daño.
- `[terremoto] profundidadGrietas`, `longitudGrietas`, `anchoGrietas`, `numeroGrietas`, `bloquesPorTick`.
- `[inundacion] alturaMaxima`, `radioInundacion`, `aguaTemporal`.
- `[meteoritos] meteoritosPorMinuto`, `radioCrater`, `mineralesEnMeteoritos`, `maxBloques`, `cieloNaranja`, `radioBola` (tamaño de la bola de roca; más grande = más lag).
- `[agrietamientos] numeroGrietas`, `longitud`, `profundidad`, `ancho`, `velocidad`, `ramificaciones`, `lavaEnFondo`, `maxBloques`.
- `[agujeroNegro] radioAtraccion`, `radioAbsorcion`, `altura`, `destruyeObjetos`, `maxBloques`.
- Meteoritos y agujero negro usan su propio `maxBloques` (40000 y 30000) en lugar de `maxBloquesPorEvento`, porque mueven mucho terreno. Bájalo si el juego va lento.
- Los fenómenos más extremos salen menos por defecto: meteoritos peso 6, agujero negro peso 3 (el resto 10).
- Cada sección tiene más opciones, explicadas en los comentarios del propio archivo.

**Protección**
- `[proteccion] bloquesProtegidos`: lista de bloques que nunca se tocan (por ejemplo `"minecraft:glass"`).
- `[proteccion] zonasProtegidas`: zonas sin destrucción, con el formato `"x1,z1,x2,z2"`. Ejemplo: `["-200,-200,200,200"]` protege tu base.
- Siempre protegidos: bloques con inventario o datos (cofres, hornos, tolvas, carteles, máquinas de otros mods) y bloques irrompibles (roca madre…).

## 7. Funciones implementadas

| Fenómeno | Siempre | Con destrucción PARTIAL/INTENSE |
|---|---|---|
| Ventisca | nieve, viento, lentitud, congelación al aire libre | capas de nieve temporales (opción `acumularNieve`) |
| Tormenta eléctrica | lluvia, truenos, rayos frecuentes (máx. 300) con daño | incendios por rayos (opción `incendiosPorRayos`) |
| Granizada | granizo, sonidos, daño sin casco | retrasa cultivos; rompe cristales (INTENSE + opción) |
| Sequía | sin lluvia, polvo, cultivos más lentos, lluvia final | seca la tierra de cultivo (opción) |
| Tornado | embudo móvil, viento, atrae, levanta y daña | arranca tierra, arena, hojas y plantas; troncos (INTENSE + opción) |
| Inundación | lluvia intensa, corrientes | el agua sube en zonas bajas y se retira al terminar |
| Terremoto | vibración de cámara, retumbos, polvo, sacudidas | grietas en V, derrumbes; zanjas y cuevas (INTENSE); daño a edificios (opción) |
| Volcán | generación natural, humo, lava, ceniza, calor, temblores | rocas volcánicas, coladas de lava temporales, crea un volcán si no hay |
| Deslizamiento | retumbo, polvo, empujones ladera abajo | la superficie de la ladera baja como bloques que caen |
| Lluvia de meteoritos | el cielo se pone naranja; bolas gigantes de magma brillante (4–9 bloques) envueltas en fuego que caen del cielo y aceleran; impactos con varias explosiones, onda de choque y temblor fuerte | cráteres gigantes (7–20 bloques de radio) que se excavan poco a poco, con paredes de obsidiana, magma y piedra negra, lago de lava en el fondo y fuego en el borde |
| Agujero negro | columna oscura vertical con disco de luz vertical y chorros; crece poco a poco y atrae cada vez más fuerte | se traga todo lo que no esté protegido —terreno, árboles, agua y casas— en un radio que crece hasta `radioAbsorcion` (20) |
| Agrietamiento del terreno | crujidos, polvo y vapor; las grietas se ven avanzar desde el centro, serpentean y se ramifican; temblor ligero | las grietas se abren de verdad (anchas y profundas en el centro, finas en la punta); con INTENSE, lava en el fondo |

Rendimiento: toda la lógica funciona en el servidor y en tandas pequeñas. Nunca se cargan chunks a propósito, y hay límites de bloques por tick y por evento.

## 8. Lista de pruebas (hazlas en un mundo de prueba)

1. [ ] Forge muestra el mod en **Mods** y el mundo se abre.
2. [ ] `/pnd help`, `/pnd status` y `/pnd next` responden.
3. [ ] Un jugador sin permisos de operador **no** puede usar `/pnd start`.
4. [ ] `/pnd next`, cierra el mundo, vuelve a entrar y repite `/pnd next`: el tiempo sigue, no se reinicia.
5. [ ] Con `diasMinimos = 1` y `diasMaximos = 1`, espera un día de Minecraft: llega una catástrofe sola, con aviso.
6. [ ] Con `destruccion = "OFF"`, prueba los 9 fenómenos con `/pnd start …`: **ningún bloque cambia**.
7. [ ] Con `PARTIAL`: hay grietas, deslizamientos y agua, y se respeta `maxBloquesPorEvento`.
8. [ ] Con `INTENSE`: zanjas y cuevas, y el juego sigue fluido.
9. [ ] Un cofre cerca de un tornado o terremoto **no** se rompe.
10. [ ] `/pnd stop` a mitad de una inundación: el agua se retira.
11. [ ] Cierra el juego durante un terremoto y vuelve a entrar: continúa sin repetirse.
12. [ ] `logs/latest.log` no muestra errores con `panthrixsnaturaldisasters`.
13. [ ] En un servidor con 2 jugadores: los avisos llegan a ambos.

Si una prueba falla, copia el error o el `latest.log` y compártelo para corregirlo.

## 9. Limitaciones conocidas

- El **clima** (lluvia o cielo despejado) es global en el mundo normal: Minecraft no permite clima por zonas. Los efectos y daños sí se limitan a la zona.
- No hay niebla real en la ventisca: la visibilidad se reduce con partículas.
- La **vibración de cámara** solo la ven los jugadores que tienen el mod instalado.
- No se detectan las protecciones de otros mods (claims, etc.). Para esos sitios usa `zonasProtegidas`.
- `eventosSimultaneos` existe en la configuración, pero de momento siempre hay como máximo una catástrofe.
- **Volcanes naturales:** solo aparecen en chunks nuevos. Son pequeños (unos 20 bloques) y pueden quedar junto a una aldea generada después.
- `/pnd volcano create` coloca miles de bloques de golpe y puede dar un tirón breve.
- **Inundación:** al retirar el agua también se quitan las fuentes que el juego crea junto a ella. Si el mundo se cierra justo en ese momento, puede quedar algún bloque de agua que no se retire.
- Las grietas, cuevas y deslizamientos son **permanentes**.
- Las zonas no cargadas se saltan: un trozo de grieta o de nieve allí no se hace o no se retira.
- Los bloques absorbidos por el agujero negro (incluidas casas) desaparecen para siempre. Cofres, hornos, máquinas y zonas protegidas se salvan, así que pueden quedar flotando.
- El cielo naranja de los meteoritos tiñe la niebla y el horizonte (Minecraft no permite cambiar todo el cielo sin modificar sus gráficos) y solo lo ven los jugadores con el mod instalado.
- Los bloques absorbidos por el agujero negro desaparecen para siempre (es un fenómeno destructivo; por eso solo actúa con destrucción PARTIAL/INTENSE).
- La caída después de que el tornado te levante hace daño normal (si `danoJugadores = false`, no te levanta).

## 10. Compilar desde el código (opcional)

En la carpeta `natural-disasters`: `gradlew build` → `build\libs\panthrixs-natural-disasters-0.1.0.jar`.
Para un Minecraft de prueba: `gradlew runClient`. Cada `push` a GitHub compila y publica el JAR en **Releases**.
