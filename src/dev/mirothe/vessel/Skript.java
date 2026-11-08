package dev.mirothe.vessel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

// writes a Skript next to the pack with commands to put players in the skies and place the objects
final class Skript {
    private static final String TEMPLATE = """
            # made by @@TOOL@@ for @@PACK@@
            # players need the @@PACK@@ resource pack loaded for any of this to show up
            #
            # /@@CMD@@ sky <name> [player|all]    puts someone inside a sky
            # /@@CMD@@ off [player|all]           takes the sky away again
            # /@@CMD@@ place <name> [size]        puts an object down in front of you
            # /@@CMD@@ size <size>                resizes the object nearest to you
            # /@@CMD@@ clear                      removes the objects near you
            # /@@CMD@@ overlay <name> [player|all] puts an overlay over someone's screen
            # /@@CMD@@ overlay off [player|all]    takes the overlay away and gives their offhand back
            # /@@CMD@@ give <name>                gives you the item itself, that's how materials are used
            # /@@CMD@@ list                       shows what's in the pack
            # the tab suggestions at the bottom need the SkBee addon, without it only that part fails to load

            options:
                p: &8[&f@@CMD@@&8]&7
                perm: @@CMD@@.admin

            function @@CMD@@Item(id: text) :: text:
            @@ITEMS@@
                return ""

            function @@CMD@@Kind(id: text) :: text:
            @@KINDS@@
                return "sky"

            # the overlay is an item in the offhand, the hand gets drawn over the world so it covers the whole screen
            function @@CMD@@Overlay(p: player, id: text):
                set {_u} to uuid of {_p}
                if {@@CMD@@ov::%{_u}%} isn't set:
                    set {@@CMD@@stash::%{_u}%} to offhand tool of {_p}
                make console execute command "item replace entity %{_p}% weapon.offhand with minecraft:paper[minecraft:item_model=""@@NS@@:%@@CMD@@Item({_id})%""]"
                set {@@CMD@@ov::%{_u}%} to {_id}

            function @@CMD@@OverlayOff(p: player):
                set {_u} to uuid of {_p}
                {@@CMD@@ov::%{_u}%} is set
                set offhand tool of {_p} to air
                if {@@CMD@@stash::%{_u}%} is set:
                    set offhand tool of {_p} to {@@CMD@@stash::%{_u}%}
                delete {@@CMD@@ov::%{_u}%}
                delete {@@CMD@@stash::%{_u}%}

            function @@CMD@@Kill(p: player):
                set {_u} to uuid of {_p}
                make console execute command "kill @e[type=minecraft:item_display,tag=@@CMD@@_sky_%{_u}%]"
                delete {-@@CMD@@ent::%{_u}%}

            function @@CMD@@Grab(p: player):
                set {_u} to uuid of {_p}
                loop entities in radius 8 of {_p}:
                    name of loop-entity is "@@CMD@@_sky_%{_u}%"
                    set {-@@CMD@@ent::%{_u}%} to loop-entity
                    exit loop

            # the sky is a huge cube turned inside out, so you see its inner faces from the middle
            function @@CMD@@Sky(p: player, id: text):
                @@CMD@@Kill({_p})
                set {_u} to uuid of {_p}
                set {_model} to "@@NS@@:%@@CMD@@Item({_id})%"
                make console execute command "execute at %{_p}% run summon minecraft:item_display ~ ~ ~ {CustomName:""@@CMD@@_sky_%{_u}%"",Tags:[""@@CMD@@_sky"",""@@CMD@@_sky_%{_u}%""],item:{id:""minecraft:paper"",count:1,components:{""minecraft:item_model"":""%{_model}%""}},transformation:{left_rotation:[0.19747f,0.19747f,0.19747f,0.93969f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[-500f,-500f,-500f]},brightness:{sky:15,block:15},view_range:16f,width:0f,height:0f,teleport_duration:5}"
                @@CMD@@Grab({_p})
                set {@@CMD@@on::%{_u}%} to {_id}
                # the cube starts turned 40 degrees on its diagonal and rotates straight, the shader reads that turn as its intro
                # the client has to see the small cube first or it skips straight to the end
                wait 2 ticks
                make console execute command "execute as @e[type=minecraft:item_display,tag=@@CMD@@_sky_%{_u}%] run data merge entity @s {transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[-500f,-500f,-500f]},start_interpolation:0,interpolation_duration:100}"

            every 5 ticks:
                loop all players:
                    set {_u} to uuid of loop-player
                    {@@CMD@@on::%{_u}%} is set
                    if {-@@CMD@@ent::%{_u}%} is not set:
                        @@CMD@@Grab(loop-player)
                    set {_e} to {-@@CMD@@ent::%{_u}%}
                    {_e} is set
                    if {_e} is alive:
                        set {_l} to location of loop-player
                        set yaw of {_l} to 0
                        set pitch of {_l} to 0
                        teleport {_e} to {_l}
                    else:
                        delete {-@@CMD@@ent::%{_u}%}

            # no permission line on purpose, Minecraft hides commands you lack the permission for,
            # so it's checked in the trigger instead and everyone can at least see it
            command /@@CMD@@ [<text>] [<text>] [<text>]:
                usage: /@@CMD@@ <sky|off|place|size|clear|overlay|give|list>
                trigger:
                    if sender is a player:
                        if player is not op:
                            if player doesn't have permission "{@perm}":
                                send "{@p} &cyou need {@perm} to use this"
                                stop
                    if arg-1 isn't set:
                        send "{@p} /@@CMD@@ sky <name> [player|all]"
                        send "{@p} /@@CMD@@ off [player|all]"
                        send "{@p} /@@CMD@@ place <name> [size]"
                        send "{@p} /@@CMD@@ size <size> &8- &7resizes the object nearest to you"
                        send "{@p} /@@CMD@@ overlay <name|off> [player|all]"
                        send "{@p} /@@CMD@@ clear &8| &7/@@CMD@@ give <name> &8| &7/@@CMD@@ list"
                        stop
                    if arg-1 is "list":
                        send "{@p} skies: &f@@SKYLIST@@"
                        send "{@p} objects: &f@@OBJECTLIST@@"
                        send "{@p} materials: &f@@MATERIALLIST@@"
                        send "{@p} overlays: &f@@OVERLAYLIST@@"
                        stop
                    if arg-1 is "off":
                        if arg-2 is "all":
                            set {_ps::*} to all players
                        else if arg-2 is set:
                            set {_t} to arg-2 parsed as player
                            if {_t} isn't set:
                                send "{@p} &ccant find %arg-2%"
                                stop
                            add {_t} to {_ps::*}
                        else if sender is a player:
                            add player to {_ps::*}
                        else:
                            send "{@p} &cyou need to put a player name"
                            stop
                        loop {_ps::*}:
                            @@CMD@@Kill(loop-value)
                            delete {@@CMD@@on::%uuid of loop-value%}
                        send "{@p} sky off for &f%size of {_ps::*}% &7player(s)"
                        stop
                    if arg-1 is "clear":
                        sender is a player
                        make console execute command "execute at %player% run kill @e[type=minecraft:item_display,tag=@@CMD@@_object,distance=..12]"
                        send "{@p} cleared the objects near you"
                        stop
                    if arg-1 is "size":
                        sender is a player
                        set {_size} to arg-2 parsed as number
                        if {_size} isn't set:
                            send "{@p} &c/@@CMD@@ size <size>, like 0.5 or 3"
                            stop
                        if {_size} is not between 0.1 and 20:
                            send "{@p} &csize goes from 0.1 to 20"
                            stop
                        # interpolation makes it grow or shrink over half a second instead of snapping
                        make console execute command "execute at %player% as @e[type=minecraft:item_display,tag=@@CMD@@_object,distance=..12,sort=nearest,limit=1] run data merge entity @s {transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[%{_size}%f,%{_size}%f,%{_size}%f]},start_interpolation:0,interpolation_duration:10}"
                        send "{@p} resized the nearest object to &f%{_size}%"
                        stop
                    if arg-2 isn't set:
                        send "{@p} &cwhich one? /@@CMD@@ list shows them"
                        stop
                    if arg-1 is "overlay":
                        if arg-3 is "all":
                            set {_ps::*} to all players
                        else if arg-3 is set:
                            set {_t} to arg-3 parsed as player
                            if {_t} isn't set:
                                send "{@p} &ccant find %arg-3%"
                                stop
                            add {_t} to {_ps::*}
                        else if sender is a player:
                            add player to {_ps::*}
                        else:
                            send "{@p} &cyou need to put a player name"
                            stop
                        if arg-2 is "off":
                            loop {_ps::*}:
                                @@CMD@@OverlayOff(loop-value)
                            send "{@p} overlay off for &f%size of {_ps::*}% &7player(s)"
                            stop
                        if @@CMD@@Kind(arg-2) is not "overlay":
                            send "{@p} &c%arg-2% isn't an overlay, /@@CMD@@ list shows them"
                            stop
                        loop {_ps::*}:
                            @@CMD@@Overlay(loop-value, arg-2)
                        send "{@p} &f%arg-2% &7on for &f%size of {_ps::*}% &7player(s)"
                        stop
                    if @@CMD@@Item(arg-2) is "":
                        send "{@p} &cthere's nothing called %arg-2%, try /@@CMD@@ list"
                        stop
                    if arg-1 is "give":
                        sender is a player
                        make console execute command "give %player% minecraft:paper[minecraft:item_model=""@@NS@@:%@@CMD@@Item(arg-2)%""]"
                        send "{@p} here's &f%arg-2%"
                        stop
                    if arg-1 is "sky":
                        if @@CMD@@Kind(arg-2) is not "sky":
                            send "{@p} &c%arg-2% isn't a sky, /@@CMD@@ list shows what each one is"
                            stop
                        if arg-3 is "all":
                            set {_ps::*} to all players
                        else if arg-3 is set:
                            set {_t} to arg-3 parsed as player
                            if {_t} isn't set:
                                send "{@p} &ccant find %arg-3%"
                                stop
                            add {_t} to {_ps::*}
                        else if sender is a player:
                            add player to {_ps::*}
                        else:
                            send "{@p} &cyou need to put a player name"
                            stop
                        loop {_ps::*}:
                            @@CMD@@Sky(loop-value, arg-2)
                        send "{@p} &f%arg-2% &7on for &f%size of {_ps::*}% &7player(s)"
                        stop
                    if arg-1 is "place":
                        sender is a player
                        if @@CMD@@Kind(arg-2) is not "object":
                            send "{@p} &c%arg-2% can't be placed, /@@CMD@@ list shows what each one is"
                            stop
                        set {_size} to 1
                        if arg-3 is set:
                            set {_size} to arg-3 parsed as number
                            if {_size} isn't set:
                                send "{@p} &cthe size has to be a number"
                                stop
                        if {_size} is not between 0.1 and 20:
                            send "{@p} &csize goes from 0.1 to 20"
                            stop
                        set {_l} to location 2.5 meters in front of eye location of player
                        make console execute command "summon minecraft:item_display %x-coord of {_l}% %y-coord of {_l}% %z-coord of {_l}% {Tags:[""@@CMD@@_object""],item:{id:""minecraft:paper"",count:1,components:{""minecraft:item_model"":""@@NS@@:%@@CMD@@Item(arg-2)%""}},transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[%{_size}%f,%{_size}%f,%{_size}%f]},brightness:{sky:15,block:15},view_range:4f}"
                        send "{@p} placed &f%arg-2%"
                        stop
                    send "{@p} &cdon't know %arg-1%, try /@@CMD@@"

            on join:
                wait a second
                player is online
                @@CMD@@Kill(player)
                set {_id} to {@@CMD@@on::%uuid of player%}
                {_id} is set
                @@CMD@@Sky(player, {_id})

            on quit:
                @@CMD@@Kill(player)

            on swap hand items:
                {@@CMD@@ov::%uuid of player%} is set
                cancel event

            on death of player:
                set {_u} to uuid of victim
                {@@CMD@@ov::%{_u}%} is set
                clear drops
                if {@@CMD@@stash::%{_u}%} is set:
                    drop {@@CMD@@stash::%{_u}%} at victim
                delete {@@CMD@@ov::%{_u}%}
                delete {@@CMD@@stash::%{_u}%}
            """;

    private static final String TABS = """
            # tab suggestions, this is the only part that needs SkBee
            on tab complete of "/@@CMD@@":
                set tab completions for position 1 to "sky", "off", "place", "size", "clear", "overlay", "give" and "list"
                if tab arg-1 is "sky":
            @@SKYTAB@@
                    set tab completions for position 3 to "all" and names of all players
                if tab arg-1 is "off":
                    set tab completions for position 2 to "all" and names of all players
                if tab arg-1 is "place":
            @@OBJECTTAB@@
                    set tab completions for position 3 to "0.5", "1", "2" and "4"
                if tab arg-1 is "size":
                    set tab completions for position 2 to "0.5", "1", "2", "4" and "8"
                if tab arg-1 is "overlay":
            @@OVERLAYTAB@@
                    set tab completions for position 3 to "all" and names of all players
                if tab arg-1 is "give":
            @@ALLTAB@@
            """;

    private Skript() {
    }

    // skript wants lists written as "a", "b" and "c"
    private static String quoted(List<String> names) {
        List<String> q = names.stream().map(n -> "\"" + n + "\"").toList();
        if (q.size() < 2) return String.join("", q);
        return String.join(", ", q.subList(0, q.size() - 1)) + " and " + q.get(q.size() - 1);
    }

    private static String completions(List<String> names) {
        if (names.isEmpty()) return "        stop\n";
        return "        set tab completions for position 2 to " + quoted(names) + "\n";
    }

    private static String command(Project project) {
        String cmd = project.namespace.toLowerCase().replaceAll("[^a-z0-9]", "");
        return cmd.isEmpty() ? "vessel" : cmd;
    }

    static Path write(Project project, Set<String> objects, Set<String> materials, Set<String> overlays, Path dir) throws IOException {
        String cmd = command(project);
        StringBuilder items = new StringBuilder();
        StringBuilder kinds = new StringBuilder();
        List<String> skies = new ArrayList<>();
        List<String> things = new ArrayList<>();
        List<String> mats = new ArrayList<>();
        List<String> overs = new ArrayList<>();
        for (Project.Effect e : project.effects) {
            items.append("    if {_id} is \"").append(e.id()).append("\":\n        return \"").append(e.item()).append("\"\n");
            String kind = objects.contains(e.id()) ? "object" : materials.contains(e.id()) ? "material" : overlays.contains(e.id()) ? "overlay" : "sky";
            if (!kind.equals("sky")) kinds.append("    if {_id} is \"").append(e.id()).append("\":\n        return \"").append(kind).append("\"\n");
            (switch (kind) {
                case "object" -> things;
                case "material" -> mats;
                case "overlay" -> overs;
                default -> skies;
            }).add(e.id());
        }
        String text = TEMPLATE
                .replace("@@ITEMS@@\n", items.toString())
                .replace("@@KINDS@@\n", kinds.toString())
                .replace("@@SKYLIST@@", list(skies))
                .replace("@@OBJECTLIST@@", list(things))
                .replace("@@MATERIALLIST@@", list(mats))
                .replace("@@OVERLAYLIST@@", list(overs))
                .replace("@@TOOL@@", Forge.TOOL)
                .replace("@@PACK@@", project.name)
                .replace("@@NS@@", project.namespace)
                .replace("@@CMD@@", cmd);
        String base = project.name.replaceAll("[^A-Za-z0-9 _-]", "");
        Path file = dir.resolve(base + ".sk");
        List<String> all = new ArrayList<>(skies);
        all.addAll(things);
        all.addAll(mats);
        all.addAll(overs);
        List<String> overlayTab = new ArrayList<>(overs);
        overlayTab.add("off");
        String tabs = TABS
                .replace("@@SKYTAB@@\n", completions(skies))
                .replace("@@OBJECTTAB@@\n", completions(things))
                .replace("@@OVERLAYTAB@@\n", completions(overlayTab))
                .replace("@@ALLTAB@@\n", completions(all))
                .replace("@@CMD@@", cmd);
        // the suggestions go last so the command already exists when SkBee hooks onto it
        Files.writeString(file, text + "\n" + tabs, StandardCharsets.UTF_8);
        Files.deleteIfExists(dir.resolve(base + " tabs.sk"));
        return file;
    }

    private static String list(List<String> names) {
        return names.isEmpty() ? "none" : String.join(", ", names);
    }
}
