# Third party

Hogyoku Vessel's own code is under the MIT license in [LICENSE](LICENSE). These parts come from elsewhere and keep their own terms.

| What | Where | License |
|---|---|---|
| [FlatLaf](https://github.com/JFormDesigner/FlatLaf) | `lib/flatlaf-*.jar` | Apache 2.0 |
| [LWJGL](https://www.lwjgl.org) | `lib/lwjgl-*.jar` | BSD 3-Clause |
| [lwjgl3-awt](https://github.com/LWJGLX/lwjgl3-awt) | `lib/lwjgl3-awt-*.jar` | MIT |
| [glslang](https://github.com/KhronosGroup/glslang) | `resources/tools/glslang.exe` | BSD 3-Clause and others, see the glslang repo |
| [JetBrains Mono](https://www.jetbrains.com/lp/mono/) | `resources/fonts/` | SIL Open Font License 1.1, see `resources/fonts/OFL.txt` |

## Minecraft shaders

The files in `resources/tiers/` and `resources/stubs/` are Minecraft's own core shaders and shader includes, edited so an effect can be dropped into them. They belong to Mojang and are **not** covered by this project's MIT license. They're included because a resource pack that replaces a core shader has to ship the whole shader. Minecraft is a trademark of Mojang Studios, and this project isn't affiliated with or endorsed by Mojang or Microsoft.
