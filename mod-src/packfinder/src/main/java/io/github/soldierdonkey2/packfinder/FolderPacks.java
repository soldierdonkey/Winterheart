package io.github.soldierdonkey2.packfinder;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.FolderRepositorySource;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

/** Every pack in one folder, as required packs with a fixed order among themselves. */
public class FolderPacks implements RepositorySource {
    private final Path folder;
    private final Path orderFile;
    private final PackType type;
    private final String idPrefix;
    /** Pack ids, lowest priority first, as of the last {@link #loadPacks}. */
    private List<String> ordered = List.of();

    FolderPacks(Path folder, Path orderFile, PackType type, String idPrefix) {
        this.folder = folder;
        this.orderFile = orderFile;
        this.type = type;
        this.idPrefix = idPrefix;
    }

    @Override
    public void loadPacks(Consumer<Pack> consumer) {
        Map<String, Pack> found = new TreeMap<>();
        try {
            Files.createDirectories(folder);
            FolderRepositorySource.discoverPacks(folder, false, (path, resources) -> {
                String name = path.getFileName().toString();
                Pack pack = Pack.readMetaAndCreate(idPrefix + name, Component.literal(name), true, resources, type,
                        Pack.Position.TOP, PackSource.DEFAULT);
                if (pack != null) {
                    found.put(name, pack);
                } else {
                    PackFinder.LOGGER.warn("[PackFinder] Skipping unreadable pack {}", path);
                }
            });
        } catch (IOException e) {
            PackFinder.LOGGER.error("[PackFinder] Can't list {}", folder, e);
        }

        // Listed packs go above unlisted ones, in the listed order.
        Map<String, Pack> listed = new LinkedHashMap<>();
        for (String name : readOrder()) {
            Pack pack = found.remove(name);
            if (pack != null) {
                listed.put(name, pack);
            } else if (!listed.containsKey(name)) {
                PackFinder.LOGGER.warn("[PackFinder] {} lists {}, which is not a pack in {}", orderFile, name, folder);
            }
        }
        List<Pack> packs = new ArrayList<>(found.values());
        packs.addAll(listed.values());
        ordered = packs.stream().map(Pack::getId).toList();
        packs.forEach(consumer);
        PackFinder.LOGGER.info("[PackFinder] Found {} packs in {}", packs.size(), folder);
    }

    private List<String> readOrder() {
        if (!Files.isRegularFile(orderFile)) {
            return List.of();
        }
        try (Reader reader = Files.newBufferedReader(orderFile)) {
            List<String> names = new ArrayList<>();
            for (JsonElement name : JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("loadOrder")) {
                names.add(name.getAsString());
            }
            return names;
        } catch (IOException | RuntimeException e) {
            PackFinder.LOGGER.error("[PackFinder] Can't read {}; loading alphabetically", orderFile, e);
            return List.of();
        }
    }

    /** Moves this folder's packs to the top of the selection, in order. */
    public List<Pack> reorder(List<Pack> selected) {
        Map<String, Pack> own = new LinkedHashMap<>();
        List<Pack> result = new ArrayList<>();
        for (Pack pack : selected) {
            if (ordered.contains(pack.getId())) {
                own.put(pack.getId(), pack);
            } else {
                result.add(pack);
            }
        }
        for (String id : ordered) {
            Pack pack = own.get(id);
            if (pack != null) {
                result.add(pack);
            }
        }
        return result;
    }
}
