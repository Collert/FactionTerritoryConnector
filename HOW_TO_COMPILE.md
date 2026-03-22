# How to Compile This Mod

Because this VS Code environment is running inside a Flatpak container, the standard `java` and `JAVA_HOME` variables are not always configured globally. 

If any future AI agent needs to compile this mod, they should execute the Gradle wrapper by pointing `JAVA_HOME` specifically to the Java 17 runtime bundled with your CurseForge installation.

## Compilation Command

Run the following command in the bash terminal at the root of the workspace (`/home/collert/Documents/FactionTerritoryConnector`):

```bash
export JAVA_HOME=/home/collert/Documents/curseforge/minecraft/Install/runtime/java-runtime-gamma/linux/java-runtime-gamma && ./gradlew build
```

### Steps the Agent Should Follow:
1. Open the terminal.
2. Run the above command. (It enforces Java 17, which Minecraft 1.20.1 requires).
3. Wait for the `BUILD SUCCESSFUL` message.
4. Retrieve the fully compiled mod file from `build/libs/factionterritoryconnector-1.0.0.jar`.

## Where is the compiled jar?
Once compiled, the resulting `.jar` file will always be placed here:
`workspace_root/build/libs/factionterritoryconnector-1.0.0.jar`

You (the user) can simply drag and drop that `.jar` file into your CurseForge `mods` folder to test the new changes.
