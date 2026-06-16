# PlotropolisCrates

Spigot 1.21.x plugin (Java 21). Kein Vault.

## Wichtig: PlotropolisCoins Hook
Dieses Plugin nutzt Reflection. Passe in `config.yml` unter `economy.reflection.*` die Methodennamen an,
die dein PlotropolisCoins-Plugin anbietet.

Standard erwartet:
- has(UUID, long) -> boolean
- withdraw(UUID, long) -> boolean
- deposit(UUID, long) -> void

## Ordner
- `plugins/PlotropolisCrates/crates/*.yml` -> jede Crate eine Datei
- `plugins/PlotropolisCrates/players.yml` -> Crate-Anzahl & Stats
