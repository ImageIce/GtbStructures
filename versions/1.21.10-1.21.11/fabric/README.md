# GtbStructures 1.21.10 Fabric

This folder is the Fabric implementation for Minecraft 1.21.10-1.21.11. See the [parent GTBS layout and build matrix](../../../README.md) before adding another version or loader target.

The port keeps GTBS-specific behavior from the Forge 1.8.9 mod while using Fabric-specific adapters for current Minecraft APIs.

Verification:

```powershell
.\gradlew.bat --no-daemon clean build
```
