# Panthrix's Natural Disasters

Mod para **Minecraft Java 1.20.1** + **Forge 47.x** (Java 17). Mod ID: `panthrixsnaturaldisasters`.

Estado: **Fase 5**. Sistema central, ventisca, tormenta electrica, granizada, sequia, tornados, inundaciones, terremotos (grietas, zanjas, derrumbes, cuevas, vibracion de camara) y proteccion de bloques.

## Compilar en Windows 11
1. Instala **JDK 17** (gratis): https://adoptium.net/temurin/releases/?version=17 → Windows x64 → `.msi`. Durante la instalacion marca *Set JAVA_HOME*.
2. Abre la carpeta `natural-disasters`, haz clic en la barra de direcciones, escribe `cmd` y pulsa Enter.
3. Escribe `gradlew build` y pulsa Enter (la primera vez tarda varios minutos).
4. El JAR aparece en `natural-disasters\build\libs\panthrixs-natural-disasters-0.1.0.jar`.
5. Para probar en un Minecraft de desarrollo: `gradlew runClient`.

Tambien se compila solo en GitHub (pestaña **Actions** / **Releases**).
