# AzLink Forge 1.7.10

Рабочий модуль под Forge **10.13.4.1614** / MC **1.7.10**.

Сборка **изолирована** (RetroFuturaGradle конфликтует с ForgeGradle 6 / NeoForge в одном multi-project).

```bash
# из корня репозитория
./gradlew buildForge1710
# или все платформы
./gradlew buildAllPlatforms

# напрямую
./gradlew -p forge-1.7.10 build
```

Артефакт: `forge-1.7.10/build/libs/` (`reobfJar` / `AzLink-Forge-*-1.7.10*.jar`).
