# CasinoSlots — Paper 1.21.1

Tragaperras 3D animadas (modelos + texturas incluidos), con moneda propia.

## Instalar
1. Compila: `mvn clean package` → `target/CasinoSlots-1.0.0.jar` → a la carpeta `plugins/`. (Java 21)
2. Sube `CasinoSlots-ResourcePack.zip` a un hosting con enlace directo (GitHub release, Dropbox con `dl=1`, etc.)
   y ponlo en `server.properties`:
       resource-pack=<enlace>
       resource-pack-sha1=aa5aaf74652f8d17c2db3b5eb140adf5c7182dc3
   (o en `plugins/CasinoSlots/config.yml` → `resource-pack.url` / `sha1`, y se envía al entrar).
3. Reinicia.

## Comandos (permiso `casino.admin`, por defecto OP)
- `/casino give coin [jugador] [cantidad]`
- `/casino give machine [jugador] [cantidad]`
- `/casino remove`  (la que mires o la más cercana)
- `/casino list` · `/casino reload` · `/casino pack [jugador]`
Alias: `/slots`, `/tragaperras`

## Jugar
Click derecho a la máquina con una moneda en la mano principal. Se gasta 1 moneda, la palanca baja
y los 3 rodillos giran. Si salen 3 iguales → UN premio aleatorio (config.yml → `rewards`).
`win-chance` controla la probabilidad (por defecto 8 %).

## Colocar / quitar
Click derecho en el suelo con el item "Tragaperras" (permiso `casino.place`, OP).
Quitar: `/casino remove`, o romperla en creativo siendo admin.
