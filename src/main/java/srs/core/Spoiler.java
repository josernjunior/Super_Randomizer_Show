package srs.core;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects a human readable record of every randomization decision and writes it next to the mod config.
 */
public final class Spoiler {

    private static final Map<String, List<String>> sections = new LinkedHashMap<>();

    private Spoiler() {
    }

    public static synchronized List<String> section(String title) {
        List<String> lines = new ArrayList<>();
        sections.put(title, lines);
        return lines;
    }

    public static synchronized void add(String title, String line) {
        sections.computeIfAbsent(title, k -> new ArrayList<>()).add(line);
    }

    public static synchronized int sectionCount() {
        return sections.size();
    }

    public static File getFile() {
        return new File(SRS.configDir(), SRS.modID() + "-spoiler.txt");
    }

    public static synchronized File write() {
        File file = getFile();
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            try (PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
                out.println("Super Randomizer Show - spoiler");
                out.println("Seed: " + SRS.seed());
                out.println("Gerado em: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
                out.println("Configuracao usada:");
                for (String line : SRS.settings().toScript().split("\n")) {
                    out.println("    " + line);
                }
                for (Map.Entry<String, List<String>> e : sections.entrySet()) {
                    out.println();
                    out.println("==== " + e.getKey() + " (" + e.getValue().size() + ") ====");
                    for (String line : e.getValue()) {
                        out.println(line);
                    }
                }
            }
            SRS.log("Spoiler written to " + file.getAbsolutePath());
        } catch (Exception e) {
            SRS.warn("Could not write spoiler file: " + e);
        }
        return file;
    }

}
