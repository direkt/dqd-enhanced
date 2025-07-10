/**
 * Copyright 2022 Dremio
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dremio.support.diagnostics.top;

import com.dremio.support.diagnostics.iostat.CPUStats;
import com.dremio.support.diagnostics.shared.DQDVersion;
import com.dremio.support.diagnostics.shared.HtmlTableBuilder;
import com.dremio.support.diagnostics.shared.HtmlTableDataColumn;
import com.dremio.support.diagnostics.shared.JsLibraryTextProvider;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.text.StringEscapeUtils;

public class TopExec {
  /**
   * A composite key class to uniquely identify threads by combining PID and command.
   * This helps address the issue of duplicate or recycled PIDs.
   */
  private static class ThreadKey {
    private final String pid;
    private final String command;

    public ThreadKey(String pid, String command) {
      this.pid = pid;
      this.command = command;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      ThreadKey threadKey = (ThreadKey) o;
      return pid.equals(threadKey.pid) && command.equals(threadKey.command);
    }

    @Override
    public int hashCode() {
      return Objects.hash(pid, command);
    }

    @Override
    public String toString() {
      return pid + ":" + command;
    }
  }

  public static void exec(final InputStream file, final OutputStream writer) throws IOException {
    try (InputStreamReader inputStreamReader = new InputStreamReader(file)) {
      try (BufferedReader bufferedReader = new BufferedReader(inputStreamReader)) {
        try (BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(writer)) {
          final List<LocalTime> times = new ArrayList<>();
          final Map<ThreadKey, List<ThreadUsage>> maps = new HashMap<>();
          final List<CPUStats> cpuStats = new ArrayList<>();
          final List<MemStats> memStats = new ArrayList<>();
          final List<SwapStats> swapStats = new ArrayList<>();
          final List<ThreadStats> threadStats = new ArrayList<>();
          final List<ParseError> parseErrors = new ArrayList<>();

          boolean startParsingThreads = false;
          String line = null;
          while ((line = bufferedReader.readLine()) != null) {
            if (line.startsWith("Threads")) {
              try {
                // Threads: 525 total,   1 running, 524 sleeping,   0 stopped,   0 zombie
                // Extract the values using regex
                final String[] parts = line.split(",\\s*");
                final int total =
                    Integer.parseInt(parts[0].split(":")[1].trim().split(" ")[0]); // Total threads
                final int running = Integer.parseInt(parts[1].split(" ")[0]); // Running threads
                final int sleeping = Integer.parseInt(parts[2].split(" ")[0]); // Sleeping threads
                final int stopped = Integer.parseInt(parts[3].split(" ")[0]); // Stopped threads
                final int zombie = Integer.parseInt(parts[4].split(" ")[0]); // Zombie threads

                // Create an instance of ThreadStats
                final ThreadStats stats =
                    new ThreadStats(total, running, sleeping, stopped, zombie);
                threadStats.add(stats);
              } catch (final Exception ex) {
                parseErrors.add(new ParseError(ex.getMessage(), "Thread Stats"));
              }
            }
            if (line.startsWith("MiB Mem ")) {
              try {
                final String[] parts = line.split(",\\s*");
                final String total = parts[0].split(":")[1].trim().split(" ")[0]; // Total memory
                final String free = parts[1].split(" ")[0]; // Free memory
                final String used = parts[2].split(" ")[0]; // Used memory
                final String buffCache = parts[3].split(" ")[0]; // Buff/cache memory
                memStats.add(
                    new MemStats(
                        Float.parseFloat(total),
                        Float.parseFloat(free),
                        Float.parseFloat(used),
                        Float.parseFloat(buffCache)));
              } catch (final Exception ex) {
                parseErrors.add(new ParseError(ex.getMessage(), "Memory"));
              }
              continue;
            }
            if (line.startsWith("MiB Swap:")) {
              try {
                final String[] parts = line.split(",\\s*");

                final String totalPart = parts[0].trim();
                final String freePart = parts[1].trim();
                final String usedAvailPart = parts[2].trim();

                // Extract the numbers
                final String total =
                    totalPart
                        .split(" ")[
                        totalPart.split(" ").length - 2]; // Get the value before "total"
                final String free = freePart.split(" ")[0]; // Get the value before "free"
                final String[] tokens = usedAvailPart.split(" ");

                final String used = tokens[0]; // Get the value before "used."
                String availMem = "0.0";
                for (int i = 0; i < tokens.length; i++) {
                  String t = tokens[i];
                  if (t.trim().equals("avail")) {
                    availMem = tokens[i - 1];
                  }
                }

                swapStats.add(
                    new SwapStats(
                        Float.parseFloat(total),
                        Float.parseFloat(free),
                        Float.parseFloat(used),
                        Float.parseFloat(availMem)));
              } catch (final Exception ex) {
                parseErrors.add(new ParseError(ex.getMessage(), "Swap"));
              }
              continue;
            }
            if (line.startsWith("top - ")) {
              // top - 12:02:04 up  3:07,  0 users,  load average: 3.18, 1.16, 0.41
              final String[] tokens = line.trim().split("\\s+");
              final LocalTime timeStamp = LocalTime.parse(tokens[2]);
              times.add(timeStamp);
              continue;
            }
            if (line.startsWith("%Cpu(s):")) {
              // %Cpu(s): 75.3 us,  3.2 sy,  0.0 ni, 20.4 id,  0.0 wa,  0.0 hi,  1.0 si,  0.0 st
              final String[] tokens = line.trim().split("\\s+");
              final float user = Float.parseFloat(tokens[1]);
              final float sys = Float.parseFloat(tokens[3]);
              final float nice = Float.parseFloat(tokens[5]);
              final float idle = Float.parseFloat(tokens[7]);
              final float iowait = Float.parseFloat(tokens[9]);
              final float steal = Float.parseFloat(tokens[15]);
              cpuStats.add(new CPUStats(user, nice, sys, iowait, steal, idle));
              continue;
            }
            if (line.contains("PID USER")) {
              startParsingThreads = true;
              continue;
            }
            if (startParsingThreads) {
              if (line.length() == 0) {
                startParsingThreads = false;
                continue;
              }
              //    996 dremio    20   0 7008232   3.4g  98412 S  82.2  21.9   1:36.72 C2
              // CompilerThre
              final String[] tokens = line.trim().split("\\s+");

              final String pid = tokens[0];
              final Double cpu = Double.parseDouble(tokens[8]);
              final StringBuilder command = new StringBuilder();

              // Combine the remaining parts for the command
              for (int i = 11; i < tokens.length; i++) {
                command.append(tokens[i]).append(" ");
              }

              final String commandStr = command.toString().trim();
              final ThreadUsage threadUsage = new ThreadUsage(pid, cpu, commandStr);
              final ThreadKey threadKey = new ThreadKey(pid, commandStr);

              if (maps.containsKey(threadKey)) {
                final List<ThreadUsage> usage = maps.get(threadKey);
                usage.add(threadUsage);
                maps.put(threadKey, usage);
              } else {
                final List<ThreadUsage> usage = new ArrayList<>();
                usage.add(threadUsage);
                maps.put(threadKey, usage);
              }
            }
          }
          final JsLibraryTextProvider jsLibraryTextProvider = new JsLibraryTextProvider();
          // now generate the report
          final String html =
              String.format(
                  Locale.US,
                  """
           <!DOCTYPE html>
 <html lang="en">
 <head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1"/>
  <title>Threaded Top Analysis - DQD</title>
  <meta name="description" content="Thread-level CPU and memory usage analysis">
  <meta name="author" content="dremio">

  <!-- Tailwind CSS -->
  <script src="https://cdn.tailwindcss.com"></script>

  <!-- Font Awesome for icons -->
  <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">

  <script>
    tailwind.config = {
      theme: {
        extend: {
          colors: {
            primary: {
              50: '#f0f9ff',
              100: '#e0f2fe',
              200: '#bae6fd',
              300: '#7dd3fc',
              400: '#38bdf8',
              500: '#0ea5e9',
              600: '#0284c7',
              700: '#0369a1',
              800: '#075985',
              900: '#0c4a6e',
            },
            secondary: {
              50: '#f8fafc',
              100: '#f1f5f9',
              200: '#e2e8f0',
              300: '#cbd5e1',
              400: '#94a3b8',
              500: '#64748b',
              600: '#475569',
              700: '#334155',
              800: '#1e293b',
              900: '#0f172a',
            },
            accent: {
              red: {
                50: '#fef2f2',
                100: '#fee2e2',
                200: '#fecaca',
                300: '#fca5a5',
                400: '#f87171',
                500: '#ef4444',
                600: '#dc2626',
                700: '#b91c1c',
                800: '#991b1b',
                900: '#7f1d1d',
              }
            }
          }
        }
      }
    }
  </script>

  <style>
     html {
      scroll-behavior: smooth;
    }
     table {
     table-layout:fixed; width: 100%%;
     }
     .chart-container {
       background: white;
       border-radius: 0.75rem;
       box-shadow: 0 1px 3px 0 rgba(0, 0, 0, 0.1);
       padding: 1.5rem;
       margin-bottom: 1.5rem;
     }
     .tooltip-pr {
       overflow: hidden;
       white-space: nowrap;
       text-overflow: ellipsis;
     }
     .tooltip-pr .tooltiptext-pr {
       color: black;
       hyphens: auto;
     }
     .tooltip-pr:hover {
       cursor: pointer;
       white-space: initial;
       transition: height 0.2s ease-in-out;
     }
     /* Plotly override styles */
     .js-plotly-plot .plotly .modebar {
       top: 10px !important;
       right: 10px !important;
     }
 </style>
  <style>
    %s
  </style>
  <script>
  %s
  </script>
 </head>
 <body class="bg-gray-50">
 <!-- Header with DQD branding -->
 <header class="bg-gradient-to-r from-accent-red-500 to-accent-red-600 shadow-lg sticky top-0 z-50">
   <div class="container mx-auto px-6">
     <div class="flex items-center justify-between h-16">
       <div class="flex items-center space-x-4">
         <div class="w-10 h-10 bg-white/20 backdrop-blur rounded-lg flex items-center justify-center">
           <i class="fas fa-stethoscope text-white text-xl"></i>
         </div>
         <div>
           <h1 class="text-white text-xl font-bold">DQD - Threaded Top Analysis</h1>
           <p class="text-accent-red-100 text-sm">Dremio Query Doctor</p>
         </div>
       </div>
       <nav class="hidden md:flex space-x-1">
         <a class="nav-link px-4 py-2 rounded-lg text-white/80 hover:text-white hover:bg-white/10 transition-colors" href="#cpu-section">
           <i class="fas fa-microchip mr-1"></i> CPU
         </a>
         <a class="nav-link px-4 py-2 rounded-lg text-white/80 hover:text-white hover:bg-white/10 transition-colors" href="#threads-section">
           <i class="fas fa-list mr-1"></i> Threads
         </a>
         <a class="nav-link px-4 py-2 rounded-lg text-white/80 hover:text-white hover:bg-white/10 transition-colors" href="#mem-section">
           <i class="fas fa-memory mr-1"></i> Memory
         </a>
         <a class="nav-link px-4 py-2 rounded-lg text-white/80 hover:text-white hover:bg-white/10 transition-colors" href="#swap-section">
           <i class="fas fa-hdd mr-1"></i> Swap
         </a>
         <a class="nav-link px-4 py-2 rounded-lg text-white/80 hover:text-white hover:bg-white/10 transition-colors" href="#thread-stats-section">
           <i class="fas fa-chart-bar mr-1"></i> Stats
         </a>
         <a class="nav-link px-4 py-2 rounded-lg text-white/80 hover:text-white hover:bg-white/10 transition-colors" href="#debugging-section">
           <i class="fas fa-bug mr-1"></i> Debug
         </a>
       </nav>
     </div>
   </div>
 </header>

 <!-- Mobile navigation -->
 <nav class="md:hidden bg-accent-red-600 border-t border-accent-red-700">
   <div class="grid grid-cols-3 gap-1 p-2">
     <a class="nav-link px-3 py-2 rounded text-center text-white/80 hover:text-white hover:bg-white/10 text-sm" href="#cpu-section">CPU</a>
     <a class="nav-link px-3 py-2 rounded text-center text-white/80 hover:text-white hover:bg-white/10 text-sm" href="#threads-section">Threads</a>
     <a class="nav-link px-3 py-2 rounded text-center text-white/80 hover:text-white hover:bg-white/10 text-sm" href="#mem-section">Memory</a>
     <a class="nav-link px-3 py-2 rounded text-center text-white/80 hover:text-white hover:bg-white/10 text-sm" href="#swap-section">Swap</a>
     <a class="nav-link px-3 py-2 rounded text-center text-white/80 hover:text-white hover:bg-white/10 text-sm" href="#thread-stats-section">Stats</a>
     <a class="nav-link px-3 py-2 rounded text-center text-white/80 hover:text-white hover:bg-white/10 text-sm" href="#debugging-section">Debug</a>
   </div>
 </nav>

 <main class="container mx-auto px-6 py-8">
   <!-- Info Banner -->
   <div class="bg-gradient-to-r from-blue-50 to-indigo-50 border border-blue-200 rounded-xl p-6 mb-8">
     <div class="flex items-start">
       <div class="flex-shrink-0">
         <div class="w-10 h-10 bg-blue-100 rounded-full flex items-center justify-center">
           <i class="fas fa-info-circle text-blue-600"></i>
         </div>
       </div>
       <div class="ml-4">
         <h3 class="text-base font-semibold text-blue-900 mb-2">Understanding This Report</h3>
         <p class="text-blue-800 mb-3">
           This analysis provides thread-level CPU and memory usage patterns from your system's top output.
         </p>
         <p class="text-sm text-blue-700">
           For additional context, consider reading
           <a href="https://www.redhat.com/sysadmin/interpret-top-output" target="_blank"
              class="text-blue-600 hover:text-blue-700 underline font-medium">
             this guide on interpreting top output
           </a>
         </p>
       </div>
     </div>
   </div>

   %s
 </main>

 <!-- Footer -->
 <footer class="bg-gray-800 text-white py-8 mt-12">
   <div class="container mx-auto px-6">
     <div class="flex flex-col md:flex-row justify-between items-center">
       <div class="flex items-center mb-4 md:mb-0">
         <div class="w-8 h-8 bg-accent-red-600 rounded-lg flex items-center justify-center mr-3">
           <i class="fas fa-stethoscope text-white"></i>
         </div>
         <div>
           <h3 class="font-bold">DQD - Dremio Query Doctor</h3>
           <p class="text-gray-400 text-sm">Version %s</p>
         </div>
       </div>
       <div class="text-gray-400 text-sm text-center md:text-right">
         <p>Analyze • Diagnose • Optimize</p>
         <p class="mt-1">Generated on %s</p>
       </div>
     </div>
   </div>
 </footer>

 <script>
    // Smooth scroll offset for sticky header
    document.querySelectorAll('a[href^="#"]').forEach(anchor => {
      anchor.addEventListener('click', function (e) {
        e.preventDefault();
        const target = document.querySelector(this.getAttribute('href'));
        if (target) {
          const offset = 80; // Header height
          const targetPosition = target.offsetTop - offset;
          window.scrollTo({
            top: targetPosition,
            behavior: 'smooth'
          });
        }
      });
    });

    // Active nav link highlighting
    const sections = document.querySelectorAll('section');
    const navLinks = document.querySelectorAll('.nav-link');

    window.addEventListener('scroll', () => {
      let current = '';
      sections.forEach(section => {
        const sectionTop = section.offsetTop - 100;
        if (window.scrollY >= sectionTop) {
          current = section.getAttribute('id');
        }
      });

      navLinks.forEach(link => {
        link.classList.remove('bg-white/20', 'text-white');
        if (link.getAttribute('href').substring(1) === current) {
          link.classList.add('bg-white/20', 'text-white');
        }
      });
    });
  </script>
 </body>
</html>
""",
                  jsLibraryTextProvider.getTableCSS(),
                  jsLibraryTextProvider.getPlotlyJsText(),
                  threadGraph(times, cpuStats, memStats, swapStats, threadStats, parseErrors, maps),
                  DQDVersion.getVersion(),
                  java.time.LocalDateTime.now()
                      .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
          writer.write(html.getBytes("UTF-8"));
        }
      }
    }
  }

  /**
   * @param times
   * @param cpuStats
   * @param memStats
   * @param swapStats
   * @param threadStats
   * @param parseErrors
   * @param map
   * @return
   */
  private static String threadGraph(
      final List<LocalTime> times,
      final List<CPUStats> cpuStats,
      final List<MemStats> memStats,
      final List<SwapStats> swapStats,
      final List<ThreadStats> threadStats,
      final List<ParseError> parseErrors,
      final Map<ThreadKey, List<ThreadUsage>> map) {

    final List<ThreadKey> keys =
        map.entrySet().stream()
            .sorted(
                (x, y) -> {
                  final Double sumx =
                      x.getValue().stream().map(c -> c.cpuUsage()).reduce(0.0, (a, b) -> a + b);
                  final Double sumy =
                      y.getValue().stream().map(c -> c.cpuUsage()).reduce(0.0, (a, b) -> a + b);
                  return sumy.compareTo(sumx);
                })
            .map(x -> x.getKey())
            .toList();
    List<String> threadTraces = new ArrayList<>();
    for (ThreadKey key : keys) {
      List<ThreadUsage> usage = map.get(key);
      if (usage.size() == 0) {
        continue;
      }
      String commandName = key.command;
      List<Float> data = usage.stream().map(x -> (float) x.cpuUsage()).toList();
      threadTraces.add(makeTrace(times, data, commandName));
    }

    List<Float> userList = new ArrayList<>();
    List<Float> idleList = new ArrayList<>();
    List<Float> stealList = new ArrayList<>();
    List<Float> sysList = new ArrayList<>();
    List<Float> iowaitList = new ArrayList<>();
    List<Float> niceList = new ArrayList<>();

    for (final CPUStats cpu : cpuStats) {
      userList.add(cpu.user());
      idleList.add(cpu.idle());
      stealList.add(cpu.steal());
      sysList.add(cpu.system());
      iowaitList.add(cpu.iowait());
      niceList.add(cpu.nice());
    }

    List<String> cpuTraces = new ArrayList<>();
    cpuTraces.add(makeTrace(times, userList, "user"));
    cpuTraces.add(makeTrace(times, sysList, "sys"));
    cpuTraces.add(makeTrace(times, iowaitList, "iowait"));
    cpuTraces.add(makeTrace(times, niceList, "nice"));
    cpuTraces.add(makeTrace(times, stealList, "steal"));
    cpuTraces.add(makeTrace(times, idleList, "idle"));

    List<Float> totalMemList = new ArrayList<>();
    List<Float> freeMemList = new ArrayList<>();
    List<Float> usedMemList = new ArrayList<>();
    List<Float> bufferMemList = new ArrayList<>();

    for (final MemStats mem : memStats) {
      totalMemList.add(mem.total());
      freeMemList.add(mem.free());
      usedMemList.add(mem.used());
      bufferMemList.add(mem.buffCache());
    }

    final List<String> memoryTraces = new ArrayList<>();
    memoryTraces.add(makeTrace(times, totalMemList, "total"));
    memoryTraces.add(makeTrace(times, freeMemList, "free"));
    memoryTraces.add(makeTrace(times, usedMemList, "used"));
    memoryTraces.add(makeTrace(times, bufferMemList, "buffer/page"));

    List<Float> totalSwapList = new ArrayList<>();
    List<Float> freeSwapList = new ArrayList<>();
    List<Float> usedSwapList = new ArrayList<>();
    List<Float> availSwapList = new ArrayList<>();

    for (final SwapStats swap : swapStats) {
      totalSwapList.add(swap.total());
      freeSwapList.add(swap.free());
      usedSwapList.add(swap.used());
      availSwapList.add(swap.avail());
    }

    final List<String> swapTraces = new ArrayList<>();
    swapTraces.add(makeTrace(times, totalSwapList, "total"));
    swapTraces.add(makeTrace(times, freeSwapList, "free"));
    swapTraces.add(makeTrace(times, usedSwapList, "used"));
    swapTraces.add(makeTrace(times, availSwapList, "avail"));

    List<Integer> totalThreadsList = new ArrayList<>();
    List<Integer> runningThreadsList = new ArrayList<>();
    List<Integer> sleepingThreadsList = new ArrayList<>();
    List<Integer> stoppedThreadsList = new ArrayList<>();
    List<Integer> zombieThreadsList = new ArrayList<>();

    for (final ThreadStats t : threadStats) {
      totalThreadsList.add(t.total());
      runningThreadsList.add(t.running());
      sleepingThreadsList.add(t.sleeping());
      stoppedThreadsList.add(t.stopped());
      zombieThreadsList.add(t.zombie());
    }

    final List<String> threadStatsTraces = new ArrayList<>();
    threadStatsTraces.add(makeTrace(times, totalThreadsList, "total"));
    threadStatsTraces.add(makeTrace(times, runningThreadsList, "running"));
    threadStatsTraces.add(makeTrace(times, sleepingThreadsList, "sleeping"));
    threadStatsTraces.add(makeTrace(times, stoppedThreadsList, "stopped"));
    threadStatsTraces.add(makeTrace(times, zombieThreadsList, "zombie"));

    final List<Collection<HtmlTableDataColumn<String, Integer>>> rows = new ArrayList<>();
    for (int i = 0; i < parseErrors.size(); i++) {
      final ParseError parseError = parseErrors.get(i);
      final String e = parseError.msg();
      final String c = parseError.category();
      HtmlTableDataColumn<String, Integer> numCol = HtmlTableDataColumn.col(String.valueOf(i), i);
      HtmlTableDataColumn<String, Integer> errCol = HtmlTableDataColumn.col(e);
      HtmlTableDataColumn<String, Integer> catCol = HtmlTableDataColumn.col(c);
      List<HtmlTableDataColumn<String, Integer>> row = Arrays.asList(numCol, errCol, catCol);
      rows.add(row);
    }
    final List<Collection<HtmlTableDataColumn<String, Integer>>> reportRows = new ArrayList<>();
    List<HtmlTableDataColumn<String, Integer>> row =
        Arrays.asList(
            HtmlTableDataColumn.col("report version"),
            HtmlTableDataColumn.col(DQDVersion.getVersion()));
    reportRows.add(row);
    return String.format(
        Locale.US,
        """
        <section id="cpu-section" class="mb-12">
         <div class="flex items-center mb-6">
           <div class="w-12 h-12 bg-red-100 rounded-xl flex items-center justify-center mr-4">
             <i class="fas fa-microchip text-red-600 text-xl"></i>
           </div>
           <h2 class="text-2xl font-bold text-gray-800">CPU Usage Analysis</h2>
         </div>
         <div class="chart-container">
           <div id="top-cpu-graph" style="width: 100%%; height: 400px;"></div>
         </div>
        </section>

        <section id="threads-section" class="mb-12">
         <div class="flex items-center mb-6">
           <div class="w-12 h-12 bg-purple-100 rounded-xl flex items-center justify-center mr-4">
             <i class="fas fa-threads text-purple-600 text-xl"></i>
           </div>
           <h2 class="text-2xl font-bold text-gray-800">Thread CPU Usage</h2>
         </div>
         <div class="chart-container">
           <div id="threads-usage-graph" style="width: 100%%; height: 600px;"></div>
         </div>
        </section>

        <section id="mem-section" class="mb-12">
         <div class="flex items-center mb-6">
           <div class="w-12 h-12 bg-blue-100 rounded-xl flex items-center justify-center mr-4">
             <i class="fas fa-memory text-blue-600 text-xl"></i>
           </div>
           <h2 class="text-2xl font-bold text-gray-800">Memory Usage</h2>
         </div>
         <div class="chart-container">
           <div id="top-mem-graph" style="width: 100%%; height: 400px;"></div>
         </div>
        </section>

        <section id="swap-section" class="mb-12">
         <div class="flex items-center mb-6">
           <div class="w-12 h-12 bg-orange-100 rounded-xl flex items-center justify-center mr-4">
             <i class="fas fa-hdd text-orange-600 text-xl"></i>
           </div>
           <h2 class="text-2xl font-bold text-gray-800">Swap Usage</h2>
         </div>
         <div class="chart-container">
           <div id="top-swap-graph" style="width: 100%%; height: 400px;"></div>
         </div>
        </section>

        <section id="thread-stats-section" class="mb-12">
         <div class="flex items-center mb-6">
           <div class="w-12 h-12 bg-green-100 rounded-xl flex items-center justify-center mr-4">
             <i class="fas fa-chart-bar text-green-600 text-xl"></i>
           </div>
           <h2 class="text-2xl font-bold text-gray-800">Thread Statistics</h2>
         </div>
         <div class="chart-container">
           <div id="top-threads-graph" style="width: 100%%; height: 400px;"></div>
         </div>
        </section>

        <section id="debugging-section" class="mb-12">
         <div class="flex items-center mb-6">
           <div class="w-12 h-12 bg-gray-100 rounded-xl flex items-center justify-center mr-4">
             <i class="fas fa-bug text-gray-600 text-xl"></i>
           </div>
           <h2 class="text-2xl font-bold text-gray-800">Report Debugging</h2>
         </div>

         <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
           <div class="bg-white rounded-xl shadow-sm p-6">
             <h3 class="text-lg font-semibold text-gray-800 mb-4 flex items-center">
               <i class="fas fa-chart-line text-gray-600 mr-2"></i> Report Statistics
             </h3>
             %s
           </div>

           <div class="bg-white rounded-xl shadow-sm p-6">
             <h3 class="text-lg font-semibold text-gray-800 mb-4 flex items-center">
               <i class="fas fa-exclamation-triangle text-yellow-600 mr-2"></i> Parse Errors
             </h3>
             %s
           </div>
         </div>
        </section>

        <script>
        // Configure Plotly layout defaults
        const layoutDefaults = {
          paper_bgcolor: 'rgba(0,0,0,0)',
          plot_bgcolor: 'rgba(0,0,0,0)',
          font: {
            family: 'system-ui, -apple-system, sans-serif',
            size: 12,
            color: '#374151'
          },
          margin: { l: 60, r: 30, t: 40, b: 60 },
          xaxis: {
            gridcolor: '#e5e7eb',
            zerolinecolor: '#e5e7eb'
          },
          yaxis: {
            gridcolor: '#e5e7eb',
            zerolinecolor: '#e5e7eb'
          }
        };

        Plotly.newPlot('top-cpu-graph', [ %s ], {
          ...layoutDefaults,
          title: {
            text: 'CPU Usage Over Time',
            font: { size: 16 }
          }
        });

        Plotly.newPlot('threads-usage-graph',[ %s ], {
          ...layoutDefaults,
          title: {
            text: 'Per Thread CPU Usage (Top 100 Threads)',
            font: { size: 16 }
          },
          height: 600
        });

        Plotly.newPlot('top-mem-graph',[ %s ], {
          ...layoutDefaults,
          title: {
            text: 'Memory Usage in MiB',
            font: { size: 16 }
          }
        });

        Plotly.newPlot('top-swap-graph',[ %s ], {
          ...layoutDefaults,
          title: {
            text: 'Swap Usage in MiB',
            font: { size: 16 }
          }
        });

        Plotly.newPlot('top-threads-graph',[ %s ], {
          ...layoutDefaults,
          title: {
            text: 'Thread State Distribution',
            font: { size: 16 }
          }
        });

        // Make charts responsive
        window.addEventListener('resize', () => {
          Plotly.Plots.resize('top-cpu-graph');
          Plotly.Plots.resize('threads-usage-graph');
          Plotly.Plots.resize('top-mem-graph');
          Plotly.Plots.resize('top-swap-graph');
          Plotly.Plots.resize('top-threads-graph');
        });
        </script>

        """,
        new HtmlTableBuilder()
            .generateTable(
                "reportStats", "report statistics", Arrays.asList("name", "value"), reportRows),
        new HtmlTableBuilder()
            .generateTable(
                "parsingErrors",
                "errors during parsing",
                Arrays.asList("#", "error", "category"),
                rows),
        String.join(",", cpuTraces),
        String.join(",", threadTraces),
        String.join(",", memoryTraces),
        String.join(",", swapTraces),
        String.join(",", threadStatsTraces));
  }

  static <T> String makeTrace(final List<LocalTime> times, final List<T> data, final String title) {
    return String.format(
        Locale.US,
        """
    {
      x: [%s],
      y: [%s],
      mode: 'lines',
      name: '%s'
}
""",
        String.join(",", times.stream().map(x -> String.format(Locale.US, "\"%s\"", x)).toList()),
        String.join(",", data.stream().map(x -> x.toString()).toList()),
        StringEscapeUtils.escapeEcmaScript(title));
  }
}
