# AzLink

AzLink is a Minecraft mod to link a server with [Azuriom](https://azuriom.com/).

Supported loaders:
* [Forge](https://minecraftforge.net/) (Minecraft **1.20.1**)
* [NeoForge](https://neoforged.net/) (Minecraft **1.21.1**)
* Forge 1.7.10 — **skeleton only** (see [`forge-1.7.10/`](forge-1.7.10/))

## Setup

Download the JAR for your loader, put it in the `mods` folder, and restart the server.

Then use `/azlink setup <url> <key>` with the values from the Azuriom admin panel (Servers section).

## Building

Java **21** JDK is recommended for the Gradle toolchain (modules target Java 8 / 17 / 21 as needed).

```sh
./gradlew build
```

Output JARs:
* `forge/build/libs/AzLink-Forge-*-1.20.1.jar`
* `neoforge/build/libs/AzLink-NeoForge-*-1.21.1.jar`
* `forge-1.7.10/build/libs/AzLink-Forge-*-1.7.10-skeleton.jar` (stubs only)
