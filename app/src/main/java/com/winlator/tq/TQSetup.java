package com.winlator.tq;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import com.winlator.XServerDisplayActivity;
import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.container.Shortcut;
import com.winlator.core.Callback;
import com.winlator.core.FileUtils;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class TQSetup {
    public static final String CONTAINER_NAME = "Titan Quest AE";
    public static final String SHORTCUT_PRIMARY = "Titan Quest AE.desktop";
    public static final String SHORTCUT_DXVK = "Titan Quest AE - DXVK.desktop";
    public static final String DEFAULT_DATA_PATH = "/storage/emulated/0/TitanQuestAE";
    public static final String PREF_DATA_PATH = "tq_data_path";
    public static final String PREF_USE_DXVK = "tq_use_dxvk";

    private static final String DESKTOP_TEMPLATE =
            "[Desktop Entry]\n" +
            "Name=%s\n" +
            "Exec=wine start.exe D:\\TQ.exe\n" +
            "StartupWMClass=TQ.exe\n" +
            "\n" +
            "[Extra Data]\n" +
            "%s" +
            "forceFullscreen=1\n" +
            "controlsProfile=9901\n";

    private TQSetup() {}

    public static boolean isDataReady(String path) {
        if (path == null || path.isEmpty()) return false;
        File root = new File(path);
        return (new File(root, "TQ.exe").isFile() || new File(root, "tq.exe").isFile())
                && new File(root, "Engine.dll").isFile()
                && new File(root, "Game.dll").isFile()
                && (new File(root, "Database/database.arz").isFile()
                    || new File(root, "Database/Database.arz").isFile());
    }

    public static String detectDataPath(Context context) {
        String saved = getDataPath(context);
        if (isDataReady(saved)) return saved;

        File[] roots = new File[]{new File("/storage/emulated/0"), new File("/sdcard")};
        for (File root : roots) {
            File[] dirs = root.listFiles(File::isDirectory);
            if (dirs == null) continue;
            for (File dir : dirs) {
                String name = dir.getName().toLowerCase();
                if (name.contains("titanquest") || name.contains("tianquest")
                        || name.contains("titan quest")) {
                    if (isDataReady(dir.getAbsolutePath())) return dir.getAbsolutePath();
                }
            }
        }
        return saved;
    }

    public static String getDataPath(Context context) {
        return context.getSharedPreferences("tq", Context.MODE_PRIVATE)
                .getString(PREF_DATA_PATH, DEFAULT_DATA_PATH);
    }

    public static void setDataPath(Context context, String path) {
        context.getSharedPreferences("tq", Context.MODE_PRIVATE).edit()
                .putString(PREF_DATA_PATH, path).apply();
    }

    public static boolean useDxvk(Context context) {
        return context.getSharedPreferences("tq", Context.MODE_PRIVATE)
                .getBoolean(PREF_USE_DXVK, false);
    }

    public static void setUseDxvk(Context context, boolean value) {
        context.getSharedPreferences("tq", Context.MODE_PRIVATE).edit()
                .putBoolean(PREF_USE_DXVK, value).apply();
    }

    public static String detectFastCores() {
        File cpuDir = new File("/sys/devices/system/cpu");
        File[] entries = cpuDir.listFiles((dir, name) -> name.matches("cpu[0-9]+"));
        if (entries == null || entries.length == 0) return "4,5,6,7";

        List<long[]> cores = new ArrayList<>();
        for (File cpu : entries) {
            int index = Integer.parseInt(cpu.getName().substring(3));
            File freqFile = new File(cpu, "cpufreq/cpuinfo_max_freq");
            long freq = readLong(freqFile);
            if (freq > 0) cores.add(new long[]{index, freq});
        }
        if (cores.isEmpty()) return "4,5,6,7";

        cores.sort(Comparator.comparingLong((long[] a) -> a[1]).reversed());
        List<Integer> fastest = new ArrayList<>();
        for (int i = 0; i < Math.min(4, cores.size()); i++) fastest.add((int) cores.get(i)[0]);
        Collections.sort(fastest);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fastest.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(fastest.get(i));
        }
        return sb.toString();
    }

    private static long readLong(File file) {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(file));
            String line = reader.readLine();
            return line == null ? 0 : Long.parseLong(line.trim());
        }
        catch (Exception e) {
            return 0;
        }
        finally {
            if (reader != null) {
                try { reader.close(); } catch (Exception ignored) {}
            }
        }
    }

    public static JSONObject buildContainerData(String dataPath) {
        try {
            JSONObject data = new JSONObject();
            data.put("name", CONTAINER_NAME);
            data.put("screenSize", "1280x720");
            data.put("envVars",
                    "ZINK_DESCRIPTORS=lazy ZINK_DEBUG=compact MESA_SHADER_CACHE_DISABLE=false " +
                    "MESA_SHADER_CACHE_MAX_SIZE=512MB mesa_glthread=true WINEESYNC=1 TU_DEBUG=noconform " +
                    "MESA_EXTENSION_MAX_YEAR=2003 BOX64_DYNAREC=1 BOX64_DYNAREC_BIGBLOCK=1");
            data.put("cpuList", "0,1,2,3,4,5,6,7");
            data.put("cpuListWoW64", detectFastCores());
            data.put("graphicsDriver", "vortek,gladio");
            data.put("dxwrapper", "wined3d");
            data.put("dxwrapperConfig", "");
            data.put("graphicsDriverConfig", "");
            data.put("audioDriverConfig", "");
            data.put("audioDriver", "alsa");
            data.put("wincomponents",
                    "direct3d=1,directsound=1,directmusic=1,directshow=0,directplay=0," +
                    "xaudio=1,vcrun2005=0,vcrun2010=1,wmdecoder=1");
            data.put("drives", "D:" + dataPath + "E:/data/data/com.winlator/storage");
            data.put("hudMode", 0);
            data.put("startupSelection", 1);
            data.put("box64Preset", "PERFORMANCE");
            data.put("desktopTheme", "LIGHT,IMAGE,#0277bd");
            data.put("extraData", new JSONObject());
            return data;
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void prepareContainer(Activity activity, String dataPath, Callback<Container> callback) {
        ContainerManager manager = new ContainerManager(activity);
        for (Container container : manager.getContainers()) {
            if (CONTAINER_NAME.equals(container.getName())) {
                callback.call(container);
                return;
            }
        }
        manager.createContainerAsync(buildContainerData(dataPath), callback);
    }

    public static Shortcut ensureShortcut(Container container, boolean dxvk) {
        File desktopDir = new File(container.getUserDir(), "Desktop");
        if (!desktopDir.isDirectory()) desktopDir.mkdirs();

        String fileName = dxvk ? SHORTCUT_DXVK : SHORTCUT_PRIMARY;
        File file = new File(desktopDir, fileName);
        if (!file.isFile()) {
            String extras = dxvk
                    ? "graphicsDriver=vortek,zink\ndxwrapper=dxvk\n" +
                      "envVars=DXVK_HUD=0 MESA_EXTENSION_MAX_YEAR=2003 WINEESYNC=1\n" +
                      "box64Preset=STABILITY\n"
                    : "envVars=MESA_EXTENSION_MAX_YEAR=2003 WINEESYNC=1\n" +
                      "box64Preset=PERFORMANCE\n";
            FileUtils.writeString(file, String.format(DESKTOP_TEMPLATE,
                    dxvk ? "Titan Quest AE (DXVK)" : "Titan Quest AE", extras));
        }
        return new Shortcut(container, file);
    }

    public static void launch(Activity activity, Container container, boolean dxvk) {
        Shortcut shortcut = ensureShortcut(container, dxvk);
        Intent intent = new Intent(activity, XServerDisplayActivity.class);
        intent.putExtra("container_id", container.id);
        intent.putExtra("shortcut_path", shortcut.file.getPath());
        activity.startActivity(intent);
    }
}
