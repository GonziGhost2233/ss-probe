import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;

public class Probe {
    static StringBuilder out = new StringBuilder();

    static void p(String s) { out.append(s).append("\n"); System.out.println(s); }

    static String read(String path, int max) {
        try (InputStream in = new FileInputStream(path)) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0 && bo.size() < max) bo.write(buf, 0, n);
            return bo.toString("UTF-8").replace('\0', ' ').trim();
        } catch (Throwable t) {
            return "<error: " + t.getClass().getSimpleName() + ": " + t.getMessage() + ">";
        }
    }

    static void list(String path) {
        File d = new File(path);
        String[] names = d.list();
        p(path + " -> existe=" + d.exists() + " legible=" + d.canRead()
            + " items=" + (names == null ? "no se pudo listar" : String.valueOf(names.length)));
        if (names != null) {
            Arrays.sort(names);
            for (int i = 0; i < Math.min(names.length, 15); i++) p("    " + names[i]);
        }
    }

    static boolean tryWrite(String dir, String text) {
        try {
            File f = new File(dir, "probe_report.txt");
            try (FileWriter w = new FileWriter(f)) { w.write(text); }
            p("REPORTE GUARDADO EN: " + f.getAbsolutePath());
            return true;
        } catch (Throwable t) {
            p("No pude escribir en " + dir + ": " + t.getMessage());
            return false;
        }
    }

    public static void main(String[] args) {
        p("=== 1. ENTORNO ===");
        for (String k : new String[]{"java.version", "java.vendor", "os.name", "os.arch",
                "user.name", "user.dir", "user.home", "java.io.tmpdir"})
            p(k + " = " + System.getProperty(k));

        p("\n=== 2. ESTE PROCESO ===");
        p("cmdline: " + read("/proc/self/cmdline", 2000));
        try { p("pid: " + new File("/proc/self").getCanonicalFile().getName()); }
        catch (Throwable t) { p("pid: error " + t.getMessage()); }

        p("\n=== 3. PROCESOS VISIBLES ===");
        List<String> pids = new ArrayList<String>();
        File[] procs = new File("/proc").listFiles();
        if (procs != null)
            for (File f : procs) if (f.getName().matches("\\d+")) pids.add(f.getName());
        Collections.sort(pids, (a, b) -> Integer.compare(Integer.parseInt(a), Integer.parseInt(b)));
        p("cantidad de procesos que puedo ver: " + pids.size());
        for (int i = 0; i < Math.min(pids.size(), 40); i++) {
            String c = read("/proc/" + pids.get(i) + "/cmdline", 150);
            p("  " + pids.get(i) + ": " + c);
        }

        p("\n=== 4. ARCHIVOS ABIERTOS (.jar / deleted) ===");
        for (String pid : pids) {
            String[] fds = new File("/proc/" + pid + "/fd").list();
            if (fds == null) continue;
            int shown = 0;
            for (String fd : fds) {
                try {
                    String t = Files.readSymbolicLink(Paths.get("/proc/" + pid + "/fd/" + fd)).toString();
                    if ((t.contains(".jar") || t.contains("(deleted)")) && shown < 15) {
                        p("  pid " + pid + " fd " + fd + " -> " + t);
                        shown++;
                    }
                } catch (Throwable ignored) { }
            }
            p("  pid " + pid + ": fds legibles=" + fds.length);
        }

        p("\n=== 5. CARPETAS ===");
        for (String d : new String[]{System.getProperty("user.dir"), System.getProperty("user.home"),
                "/storage/emulated/0", "/storage/emulated/0/Android/data",
                "/storage/emulated/0/games", "/storage/emulated/0/Download"})
            if (d != null) list(d);

        p("\n=== 6. INTERNET (Modrinth) ===");
        try {
            HttpURLConnection c = (HttpURLConnection) new URL("https://api.modrinth.com/v2/").openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            c.setRequestProperty("User-Agent", "SSProbe/0.1");
            p("respuesta HTTP: " + c.getResponseCode());
        } catch (Throwable t) {
            p("sin internet o bloqueado: " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }

        p("\n=== 7. DONDE PUEDO ESCRIBIR ===");
        String text = out.toString();
        boolean ok = tryWrite(System.getProperty("user.dir"), text);
        if (!ok) ok = tryWrite("/storage/emulated/0/Download", text);
        if (!ok) tryWrite(System.getProperty("java.io.tmpdir"), text);
        p("=== FIN ===");
    }
}
