package dev.zm.pvprooms.managers;

import dev.zm.pvprooms.ZMPvPRooms;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LangMigrator {

    private static final Pattern TOP_KEY = Pattern.compile("^(\\S[^:]*):(.*)$");
    private static final Pattern SUB_KEY = Pattern.compile("^  (\\S[^:]*):(.*)$");
    private static final Pattern SUB_COMMENT = Pattern.compile("^  #.*");

    private final ZMPvPRooms plugin;

    public LangMigrator(ZMPvPRooms plugin) {
        this.plugin = plugin;
    }

    public int migrate(File userFile, String resourcePath) {
        if (!userFile.exists()) {
            return 0;
        }

        List<String> defaultLines = readResource(resourcePath);
        if (defaultLines.isEmpty()) {
            return 0;
        }

        List<String> userLines;
        try {
            userLines = Files.readAllLines(userFile.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().warning("No se pudo leer " + userFile.getName() + " para migrar: " + e.getMessage());
            return 0;
        }

        Map<String, Section> defaultSections = parse(defaultLines);
        Map<String, Section> userSections = parse(userLines);

        List<String> result = new ArrayList<>(userLines);
        int added = 0;

        for (Map.Entry<String, Section> entry : defaultSections.entrySet()) {
            String topKey = entry.getKey();
            Section defSection = entry.getValue();
            Section userSection = userSections.get(topKey);

            if (userSection == null) {
                if (!result.isEmpty() && !result.get(result.size() - 1).isEmpty()) {
                    result.add("");
                }
                result.addAll(defSection.fullBlock);
                added++;
                continue;
            }

            if (defSection.children.isEmpty()) {
                continue;
            }

            List<String> toInsert = new ArrayList<>();
            for (Map.Entry<String, List<String>> child : defSection.children.entrySet()) {
                if (!userSection.children.containsKey(child.getKey())) {
                    toInsert.addAll(child.getValue());
                    added++;
                }
            }

            if (!toInsert.isEmpty()) {
                int insertAt = findSectionEnd(result, topKey);
                result.addAll(insertAt, toInsert);
            }
        }

        if (added > 0) {
            try {
                Files.write(userFile.toPath(), result, StandardCharsets.UTF_8);
                plugin.getLogger().info("Se agregaron " + added + " mensaje(s) nuevo(s) a " + userFile.getName() + ".");
            } catch (IOException e) {
                plugin.getLogger().warning("No se pudo guardar " + userFile.getName() + " migrado: " + e.getMessage());
            }
        }

        return added;
    }

    private int findSectionEnd(List<String> lines, String topKey) {
        int sectionLine = -1;
        for (int i = 0; i < lines.size(); i++) {
            Matcher m = TOP_KEY.matcher(lines.get(i));
            if (m.matches() && m.group(1).equals(topKey)) {
                sectionLine = i;
                break;
            }
        }
        if (sectionLine == -1) {
            return lines.size();
        }
        for (int i = sectionLine + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (!line.isEmpty() && !Character.isWhitespace(line.charAt(0))) {
                return i;
            }
        }
        return lines.size();
    }

    private Map<String, Section> parse(List<String> lines) {
        Map<String, Section> sections = new LinkedHashMap<>();

        int i = 0;
        while (i < lines.size()) {
            String line = lines.get(i);

            if (line.isEmpty() || line.startsWith("#")) {
                i++;
                continue;
            }

            Matcher top = TOP_KEY.matcher(line);
            if (top.matches()) {
                String key = top.group(1);
                String value = top.group(2).trim();

                int start = i;
                int end = i + 1;
                while (end < lines.size()) {
                    String l = lines.get(end);
                    if (!l.isEmpty() && !Character.isWhitespace(l.charAt(0))) {
                        break;
                    }
                    end++;
                }

                List<String> fullBlock = new ArrayList<>(lines.subList(start, end));
                Section section = new Section(fullBlock);

                if (value.isEmpty()) {
                    section.children = parseChildren(fullBlock.subList(1, fullBlock.size()));
                }

                sections.put(key, section);
                i = end;
                continue;
            }

            i++;
        }

        return sections;
    }

    private Map<String, List<String>> parseChildren(List<String> lines) {
        Map<String, List<String>> children = new LinkedHashMap<>();
        List<String> pendingComments = new ArrayList<>();

        int i = 0;
        while (i < lines.size()) {
            String line = lines.get(i);

            if (line.isEmpty()) {
                pendingComments.clear();
                i++;
                continue;
            }
            if (SUB_COMMENT.matcher(line).matches()) {
                pendingComments.add(line);
                i++;
                continue;
            }

            Matcher sub = SUB_KEY.matcher(line);
            if (sub.matches()) {
                String key = sub.group(1);
                int start = i;
                int end = i + 1;
                while (end < lines.size()) {
                    String l = lines.get(end);
                    if (l.isEmpty() || SUB_COMMENT.matcher(l).matches() || SUB_KEY.matcher(l).matches()) {
                        break;
                    }
                    if (!l.startsWith("  ")) {
                        break;
                    }
                    end++;
                }
                List<String> block = new ArrayList<>(pendingComments);
                block.addAll(lines.subList(start, end));
                children.put(key, block);
                pendingComments = new ArrayList<>();
                i = end;
                continue;
            }

            pendingComments.clear();
            i++;
        }

        return children;
    }

    private List<String> readResource(String resourcePath) {
        try (InputStream in = plugin.getResource(resourcePath)) {
            if (in == null) {
                return Collections.emptyList();
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                List<String> lines = new ArrayList<>();
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }
                return lines;
            }
        } catch (IOException e) {
            plugin.getLogger().warning("No se pudo leer el recurso " + resourcePath + ": " + e.getMessage());
            return Collections.emptyList();
        }
    }

    private static class Section {
        final List<String> fullBlock;
        Map<String, List<String>> children = Collections.emptyMap();

        Section(List<String> fullBlock) {
            this.fullBlock = fullBlock;
        }
    }
}