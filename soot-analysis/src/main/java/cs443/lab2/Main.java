package cs443.lab2;

import soot.*;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import soot.options.Options;
import soot.toolkits.graph.BriefUnitGraph;
import soot.toolkits.graph.UnitGraph;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * CS443 Lab 2 — Soot-based Android Static Analysis
 * 
 * This tool:
 *  1. Loads an Android APK using Soot
 *  2. Iterates through all application classes and methods
 *  3. Generates Control Flow Graph (CFG) .dot files for each method
 *  4. Scans for sensitive API usage against sensitive_apis.csv
 *  5. Outputs results in "API_name:frequency:residing functions" format
 */
public class Main {

    // ── Configuration (overridable via command-line args) ──────────────
    private static String apkPath;
    private static String androidPlatformsPath;
    private static String sensitiveApisCsvPath;
    private static String cfgOutputDir;
    private static String sensitiveOutputFile;

    // ── Data structures ───────────────────────────────────────────────
    // Key = "CallerClass.CallerMethod" (from CSV)
    // We store the full row info so we can report it later
    private static final Set<String> sensitiveApiSet = new HashSet<>();
    // Map: "ClassName.MethodName" -> list of functions that call it
    private static final Map<String, List<String>> sensitiveApiHits = new LinkedHashMap<>();
    // Map: "ClassName.MethodName" -> call count
    private static final Map<String, Integer> sensitiveApiFrequency = new LinkedHashMap<>();

    public static void main(String[] args) throws Exception {
        // ── Parse arguments ───────────────────────────────────────────
        parseArgs(args);

        System.out.println("╔══════════════════════════════════════════════════════╗");
        System.out.println("║   CS443 Lab 2 — Soot Android Static Analyzer        ║");
        System.out.println("╚══════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("[*] APK Path            : " + apkPath);
        System.out.println("[*] Android Platforms    : " + androidPlatformsPath);
        System.out.println("[*] Sensitive APIs CSV   : " + sensitiveApisCsvPath);
        System.out.println("[*] CFG Output Directory : " + cfgOutputDir);
        System.out.println("[*] Sensitive API Output : " + sensitiveOutputFile);
        System.out.println();

        // ── Step 1: Load sensitive APIs from CSV ──────────────────────
        loadSensitiveApis();

        // ── Step 2: Configure and initialize Soot ─────────────────────
        configureSoot();

        // ── Step 3: Run the analysis ──────────────────────────────────
        runAnalysis();

        System.out.println();
        System.out.println("[✓] Analysis complete!");
    }

    // ══════════════════════════════════════════════════════════════════
    //  ARGUMENT PARSING
    // ══════════════════════════════════════════════════════════════════

    private static void parseArgs(String[] args) {
        // Defaults — paths relative to the project root
        String projectRoot = System.getProperty("user.dir");
        // Default: look for APK one level up from the Maven project
        apkPath = projectRoot + "/../demo.apk";
        androidPlatformsPath = System.getenv("ANDROID_HOME") != null
                ? System.getenv("ANDROID_HOME") + "/platforms"
                : System.getProperty("user.home") + "/Library/Android/sdk/platforms";
        sensitiveApisCsvPath = projectRoot + "/../sensitive_apis.csv";
        cfgOutputDir = projectRoot + "/../cfg_output";
        sensitiveOutputFile = projectRoot + "/../sensitive_apis.txt";

        // Override via CLI flags
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--apk":
                    apkPath = args[++i];
                    break;
                case "--android-platforms":
                    androidPlatformsPath = args[++i];
                    break;
                case "--sensitive-apis":
                    sensitiveApisCsvPath = args[++i];
                    break;
                case "--cfg-output":
                    cfgOutputDir = args[++i];
                    break;
                case "--sensitive-output":
                    sensitiveOutputFile = args[++i];
                    break;
                case "--help":
                    printUsage();
                    System.exit(0);
                    break;
                default:
                    System.err.println("Unknown argument: " + args[i]);
                    printUsage();
                    System.exit(1);
            }
        }

        // Validate
        if (!Files.exists(Paths.get(apkPath))) {
            System.err.println("[!] APK not found: " + apkPath);
            System.err.println("    Use --apk <path> to specify the APK location.");
            System.exit(1);
        }
        if (!Files.exists(Paths.get(androidPlatformsPath))) {
            System.err.println("[!] Android platforms not found: " + androidPlatformsPath);
            System.err.println("    Use --android-platforms <path> or set ANDROID_HOME.");
            System.err.println("    Install via: sdkmanager 'platforms;android-34'");
            System.exit(1);
        }
        if (!Files.exists(Paths.get(sensitiveApisCsvPath))) {
            System.err.println("[!] Sensitive APIs CSV not found: " + sensitiveApisCsvPath);
            System.err.println("    Use --sensitive-apis <path> to specify.");
            System.exit(1);
        }
    }

    private static void printUsage() {
        System.out.println("Usage: java -jar soot-analysis.jar [OPTIONS]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --apk <path>               Path to the APK file");
        System.out.println("  --android-platforms <path>  Path to Android SDK platforms/");
        System.out.println("  --sensitive-apis <path>     Path to sensitive_apis.csv");
        System.out.println("  --cfg-output <dir>          Output directory for .dot files");
        System.out.println("  --sensitive-output <path>   Output file for sensitive API report");
        System.out.println("  --help                      Show this help");
    }

    // ══════════════════════════════════════════════════════════════════
    //  LOAD SENSITIVE APIS FROM CSV
    // ══════════════════════════════════════════════════════════════════

    private static void loadSensitiveApis() throws IOException, CsvException {
        System.out.println("[1] Loading sensitive APIs from CSV...");

        try (CSVReader reader = new CSVReader(new FileReader(sensitiveApisCsvPath))) {
            List<String[]> rows = reader.readAll();

            // Skip header row
            boolean first = true;
            for (String[] row : rows) {
                if (first) { first = false; continue; }
                if (row.length >= 2) {
                    // CSV columns: CallerClass, CallerMethod, Permission
                    String className = row[0].trim().replace("/", ".");
                    String methodName = row[1].trim();
                    String key = className + "." + methodName;
                    sensitiveApiSet.add(key);
                }
            }
        }

        System.out.println("    → Loaded " + sensitiveApiSet.size() + " unique sensitive API signatures");
    }

    // ══════════════════════════════════════════════════════════════════
    //  SOOT CONFIGURATION
    // ══════════════════════════════════════════════════════════════════

    private static void configureSoot() {
        System.out.println("[2] Configuring Soot...");

        // Reset Soot state (safe to call multiple times)
        G.reset();

        // Set Android mode
        Options.v().set_src_prec(Options.src_prec_apk);
        Options.v().set_process_dir(Collections.singletonList(apkPath));
        Options.v().set_android_jars(androidPlatformsPath);

        // Allow phantom references (libraries not present)
        Options.v().set_allow_phantom_refs(true);

        // Whole-program mode (needed for call graph, etc.)
        Options.v().set_whole_program(true);

        // Output format: none (we handle output ourselves)
        Options.v().set_output_format(Options.output_format_none);

        // Exclude certain packages to speed up analysis
        List<String> excludeList = new ArrayList<>();
        excludeList.add("java.*");
        excludeList.add("javax.*");
        excludeList.add("sun.*");
        excludeList.add("jdk.*");
        Options.v().set_exclude(excludeList);
        Options.v().set_no_bodies_for_excluded(true);

        // Load necessary classes
        Scene.v().loadNecessaryClasses();

        System.out.println("    → Soot initialized successfully");
        System.out.println("    → Application classes loaded: " + Scene.v().getApplicationClasses().size());
    }

    // ══════════════════════════════════════════════════════════════════
    //  MAIN ANALYSIS
    // ══════════════════════════════════════════════════════════════════

    private static void runAnalysis() throws IOException {
        System.out.println("[3] Running analysis...");

        // Create output directory for CFGs
        Path cfgDir = Paths.get(cfgOutputDir);
        Files.createDirectories(cfgDir);

        int classCount = 0;
        int methodCount = 0;
        int cfgCount = 0;

        // Iterate through all application classes
        for (SootClass sootClass : Scene.v().getApplicationClasses()) {
            classCount++;
            String className = sootClass.getName();

            // Skip Android framework / support library classes
            if (className.startsWith("android.") || className.startsWith("androidx.")
                    || className.startsWith("com.google.android")
                    || className.startsWith("kotlin.") || className.startsWith("kotlinx.")) {
                continue;
            }

            System.out.println("    → Analyzing class: " + className);

            for (SootMethod method : sootClass.getMethods()) {
                methodCount++;

                // Skip methods with no body (abstract, native, etc.)
                if (!method.isConcrete()) continue;

                try {
                    Body body = method.retrieveActiveBody();
                    String methodSig = className + "." + method.getName();

                    // ── Generate CFG ──────────────────────────────
                    UnitGraph cfg = new BriefUnitGraph(body);
                    String dotFileName = sanitizeFileName(className + "_" + method.getName()
                            + "_" + method.getParameterCount()) + ".dot";
                    Path dotFilePath = cfgDir.resolve(dotFileName);
                    exportCfgAsDot(cfg, method, dotFilePath);
                    cfgCount++;

                    // ── Scan for sensitive API calls ──────────────
                    scanForSensitiveApis(body, methodSig);

                } catch (Exception e) {
                    // Some methods may fail to retrieve body
                    System.err.println("      [WARN] Could not analyze: " 
                            + method.getSignature() + " — " + e.getMessage());
                }
            }
        }

        System.out.println();
        System.out.println("    ┌─────────────────────────────────────┐");
        System.out.println("    │  Summary                            │");
        System.out.println("    ├─────────────────────────────────────┤");
        System.out.printf( "    │  Classes analyzed : %-16d │%n", classCount);
        System.out.printf( "    │  Methods analyzed : %-16d │%n", methodCount);
        System.out.printf( "    │  CFGs generated   : %-16d │%n", cfgCount);
        System.out.printf( "    │  Sensitive hits   : %-16d │%n", sensitiveApiFrequency.size());
        System.out.println("    └─────────────────────────────────────┘");

        // ── Step 4: Write sensitive API report ────────────────────────
        writeSensitiveApiReport();
    }

    // ══════════════════════════════════════════════════════════════════
    //  CFG → DOT EXPORT
    // ══════════════════════════════════════════════════════════════════

    private static void exportCfgAsDot(UnitGraph cfg, SootMethod method, Path outputPath) 
            throws IOException {
        
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(outputPath))) {
            writer.println("digraph \"" + escapeLabel(method.getSignature()) + "\" {");
            writer.println("  rankdir=TB;");
            writer.println("  node [shape=box, style=\"rounded,filled\", fillcolor=\"#e8f4fd\", "
                    + "fontname=\"Courier New\", fontsize=10];");
            writer.println("  edge [color=\"#4a90d9\"];");
            writer.println();

            // Map each Unit to a unique node ID
            Map<Unit, String> unitToId = new LinkedHashMap<>();
            int nodeId = 0;
            for (Unit unit : cfg.getBody().getUnits()) {
                unitToId.put(unit, "n" + nodeId++);
            }

            // Write nodes
            for (Map.Entry<Unit, String> entry : unitToId.entrySet()) {
                Unit unit = entry.getKey();
                String id = entry.getValue();
                String label = escapeLabel(unit.toString());
                writer.println("  " + id + " [label=\"" + label + "\"];");
            }

            writer.println();

            // Write edges
            for (Unit unit : cfg.getBody().getUnits()) {
                String fromId = unitToId.get(unit);
                for (Unit succ : cfg.getSuccsOf(unit)) {
                    String toId = unitToId.get(succ);
                    if (toId != null) {
                        writer.println("  " + fromId + " -> " + toId + ";");
                    }
                }
            }

            writer.println("}");
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  SENSITIVE API SCANNING
    // ══════════════════════════════════════════════════════════════════

    private static void scanForSensitiveApis(Body body, String callerMethodSig) {
        for (Unit unit : body.getUnits()) {
            if (unit instanceof Stmt) {
                Stmt stmt = (Stmt) unit;
                if (stmt.containsInvokeExpr()) {
                    InvokeExpr invoke = stmt.getInvokeExpr();
                    SootMethod calledMethod = invoke.getMethod();

                    String calledClass = calledMethod.getDeclaringClass().getName();
                    String calledName = calledMethod.getName();
                    String key = calledClass + "." + calledName;

                    if (sensitiveApiSet.contains(key)) {
                        // Record the hit
                        sensitiveApiFrequency.merge(key, 1, Integer::sum);
                        sensitiveApiHits.computeIfAbsent(key, k -> new ArrayList<>())
                                .add(callerMethodSig);
                    }
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  OUTPUT: SENSITIVE API REPORT
    // ══════════════════════════════════════════════════════════════════

    private static void writeSensitiveApiReport() throws IOException {
        System.out.println("[4] Writing sensitive API report to: " + sensitiveOutputFile);

        try (PrintWriter writer = new PrintWriter(
                Files.newBufferedWriter(Paths.get(sensitiveOutputFile)))) {
            
            writer.println("# CS443 Lab 2 — Sensitive API Usage Report");
            writer.println("# Format: API_name : frequency : residing_functions");
            writer.println("# Generated: " + new Date());
            writer.println("# APK: " + apkPath);
            writer.println("#");

            if (sensitiveApiFrequency.isEmpty()) {
                writer.println("# No sensitive API calls detected.");
                System.out.println("    → No sensitive API calls detected in application code.");
            } else {
                // Sort by frequency descending
                List<Map.Entry<String, Integer>> sorted = sensitiveApiFrequency.entrySet()
                        .stream()
                        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                        .collect(Collectors.toList());

                for (Map.Entry<String, Integer> entry : sorted) {
                    String api = entry.getKey();
                    int freq = entry.getValue();
                    List<String> functions = sensitiveApiHits.getOrDefault(api, Collections.emptyList());

                    // Deduplicate function names
                    String funcList = functions.stream()
                            .distinct()
                            .collect(Collectors.joining(", "));

                    writer.println(api + " : " + freq + " : [" + funcList + "]");
                }

                System.out.println("    → Found " + sorted.size() + " unique sensitive APIs");
                System.out.println("    → Top 5:");
                sorted.stream().limit(5).forEach(e ->
                        System.out.println("       " + e.getKey() + " (called " + e.getValue() + " times)"));
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  UTILITY METHODS
    // ══════════════════════════════════════════════════════════════════

    /** Escape special characters for DOT labels */
    private static String escapeLabel(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("<", "\\<")
                .replace(">", "\\>");
    }

    /** Sanitize file names (replace problematic characters) */
    private static String sanitizeFileName(String name) {
        return name.replaceAll("[^a-zA-Z0-9._\\-]", "_");
    }
}
