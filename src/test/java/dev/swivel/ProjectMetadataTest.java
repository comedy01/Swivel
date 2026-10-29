package dev.swivel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ProjectMetadataTest {
    private static JsonObject resourceJson(String name) throws IOException {
        InputStream in = ProjectMetadataTest.class.getClassLoader().getResourceAsStream(name);
        assertNotNull(in, name + " must be on the classpath");
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static boolean has(String name) {
        return ProjectMetadataTest.class.getClassLoader().getResource(name) != null;
    }

    private static String resourceText(String name) throws IOException {
        try (InputStream in = ProjectMetadataTest.class.getClassLoader().getResourceAsStream(name)) {
            assertNotNull(in, name + " must be on the classpath");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void modJson() throws IOException {
        assumeTrue(has("fabric.mod.json"));
        JsonObject mod = resourceJson("fabric.mod.json");
        assertEquals("swivel", mod.get("id").getAsString());
        assertEquals("Swivel", mod.get("name").getAsString());
        assertEquals("client", mod.get("environment").getAsString());
        assertFalse(mod.get("version").getAsString().contains("$"), "version placeholder must be expanded");

        JsonObject entrypoints = mod.getAsJsonObject("entrypoints");
        assertEquals("dev.swivel.fabric.SwivelFabric", entrypoints.getAsJsonArray("client").get(0).getAsString());
        for (String key : entrypoints.keySet()) {
            for (JsonElement entry : entrypoints.getAsJsonArray(key)) {
                assertClassExists(entry.getAsString());
            }
        }

        JsonObject depends = mod.getAsJsonObject("depends");
        assertFalse(depends.has("modmenu"), "Mod Menu must stay optional");
        assertTrue(mod.getAsJsonObject("suggests").has("modmenu"));
    }

    @Test
    void neoforgeMetadata() throws IOException {
        assumeTrue(has("META-INF/neoforge.mods.toml"));
        String toml = resourceText("META-INF/neoforge.mods.toml");
        assertTrue(toml.contains("modId=\"swivel\""), toml);
        assertTrue(toml.contains("displayName=\"Swivel\""), toml);
        assertTrue(toml.contains("config=\"swivel.mixins.json\""), toml);
        assertFalse(toml.contains("${"), "placeholders must be expanded");
        assertClassExists("dev.swivel.neoforge.SwivelNeoForge");
    }

    @Test
    void forgeMetadata() throws IOException {
        assumeTrue(has("META-INF/mods.toml"));
        String toml = resourceText("META-INF/mods.toml");
        assertTrue(toml.contains("modId=\"swivel\""), toml);
        assertTrue(toml.contains("displayName=\"Swivel\""), toml);
        assertTrue(toml.contains("modId=\"forge\""), toml);
        assertFalse(toml.contains("${"), "placeholders must be expanded");
        assertEquals("swivel.refmap.json", resourceJson("swivel.mixins.json").get("refmap").getAsString());
        assertEquals(15, resourceJson("pack.mcmeta").getAsJsonObject("pack").get("pack_format").getAsInt());
        assertClassExists("dev.swivel.forge.SwivelForge");
    }

    @Test
    void mixinsExist() throws IOException {
        JsonObject mixins = resourceJson("swivel.mixins.json");
        assertEquals("dev.swivel.mixin", mixins.get("package").getAsString());
        JsonArray client = mixins.getAsJsonArray("client");
        assertEquals(9, client.size());
        for (JsonElement entry : client) {
            assertClassExists("dev.swivel.mixin." + entry.getAsString());
        }
        assertFalse(mixins.has("mixins"), "all mixins are client-only");
        assertFalse(mixins.has("server"));
    }

    @Test
    void everySettingIsTranslated() throws IOException {
        JsonObject lang = resourceJson("assets/swivel/lang/en_us.json");
        String[] options = {"enabled", "turn_speed", "distance", "raise_camera", "height", "strafe", "swivel_running",
                "smooth_collisions", "crosshair", "align_on_exit", "fade_player", "aim_camera", "spyglass", "free_look_crosshair",
                "keys", "reset"};
        for (String option : options) {
            assertTrue(lang.has("swivel.options." + option), option);
            assertTrue(lang.has("swivel.options." + option + ".tooltip"), option + " tooltip");
        }
        for (String key : new String[] {"swivel.options.title", "swivel.options.section.third_person",
                "swivel.options.section.free_look", "swivel.options.section.free_look.unbound",
                "key.category.swivel.main", "key.swivel.free_look", "key.swivel.toggle", "key.swivel.recenter",
                "key.swivel.zoom_in", "key.swivel.zoom_out", "key.swivel.swap_shoulder", "swivel.options.section.aiming",
                "swivel.options.aim_camera.right_shoulder", "swivel.options.aim_camera.left_shoulder",
                "swivel.options.aim_camera.centered", "swivel.options.aim_camera.off", "swivel.message.aim_camera",
                "swivel.message.distance",
                "swivel.message.on", "swivel.message.off"}) {
            assertTrue(lang.has(key), key);
        }
    }

    @Test
    void iconsExist() {
        for (String size : new String[] {"16", "32", "128"}) {
            assertTrue(has("assets/swivel/icon" + size + ".png"), "icon" + size);
        }
    }

    @Test
    void noOtherProjectReferences() throws IOException {
        String[] forbidden = {"limn", "lidless", "reticle", "loupe", "easycrosshair", "easy crosshair",
                "better third person", "better-third-person", "shoulder surfing"};
        try (Stream<Path> files = Stream.of(Path.of("src/main"), Path.of("src/loader"), Path.of("src/compat"),
                        Path.of("build.gradle"), Path.of("gradle.properties"), Path.of("settings.gradle"), Path.of("README.md"))
                .filter(Files::exists)
                .flatMap(ProjectMetadataTest::walk)) {
            files.filter(Files::isRegularFile)
                    .filter(file -> !file.toString().endsWith(".png"))
                    .forEach(file -> {
                        String text;
                        try {
                            text = Files.readString(file, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
                        } catch (IOException e) {
                            throw new IllegalStateException(file.toString(), e);
                        }
                        for (String token : forbidden) {
                            assertFalse(text.contains(token), file + " mentions \"" + token + "\"");
                        }
                    });
        }
    }

    private static void assertClassExists(String className) {
        String path = className.replace('.', '/') + ".class";
        assertNotNull(ProjectMetadataTest.class.getClassLoader().getResource(path), path + " must be compiled");
    }

    private static Stream<Path> walk(Path root) {
        try {
            return Files.walk(root);
        } catch (IOException e) {
            throw new IllegalStateException(root.toString(), e);
        }
    }
}
