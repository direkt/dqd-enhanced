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
package com.dremio.support.diagnostics.iostat;

import static com.dremio.support.diagnostics.shared.HtmlTableDataColumn.col;

import com.dremio.support.diagnostics.shared.DQDVersion;
import com.dremio.support.diagnostics.shared.HtmlTableBuilder;
import com.dremio.support.diagnostics.shared.HtmlTableDataColumn;
import com.dremio.support.diagnostics.shared.JsLibraryTextProvider;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class IOStatReporter {
  private final JsLibraryTextProvider jsLibraryTextProvider = new JsLibraryTextProvider();

  public void write(final ReportStats reportStats, final OutputStream streamWriter)
      throws UnsupportedEncodingException, IOException {
    final Summary summary = this.summaryStats(reportStats);
    try (BufferedOutputStream output = new BufferedOutputStream(streamWriter)) {
      final String template =
          String.format(
              Locale.US,
              """
 <!DOCTYPE html>
 <html lang="en">
 <head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1"/>
  <title>IOStat Analysis - DQD</title>
  <meta name="description" content="Disk I/O and system performance analysis">
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
              orange: {
                50: '#fff7ed',
                100: '#ffedd5',
                200: '#fed7aa',
                300: '#fdba74',
                400: '#fb923c',
                500: '#f97316',
                600: '#ea580c',
                700: '#c2410c',
                800: '#9a3412',
                900: '#7c2d12',
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
   <script>
  %s
  </script>
  <style>
    %s
  </style>
  <script>
  %s
  </script>
     <script>
  %s
  </script>
 </head>
 <body class="bg-gray-50">
 <!-- Header with DQD branding -->
 <header class="bg-gradient-to-r from-accent-orange-500 to-accent-orange-600 shadow-lg sticky top-0 z-50">
   <div class="container mx-auto px-6">
     <div class="flex items-center justify-between h-16">
       <div class="flex items-center space-x-4">
         <div class="w-10 h-10 bg-white/20 backdrop-blur rounded-lg flex items-center justify-center">
           <i class="fas fa-stethoscope text-white text-xl"></i>
         </div>
         <div>
           <h1 class="text-white text-xl font-bold">DQD - IOStat Analysis</h1>
           <p class="text-accent-orange-100 text-sm">Dremio Query Doctor</p>
         </div>
       </div>
       <nav class="hidden md:flex space-x-1">
         <a class="nav-link px-4 py-2 rounded-lg text-white/80 hover:text-white hover:bg-white/10 transition-colors" href="#summary-section">
           <i class="fas fa-chart-pie mr-1"></i> Summary
         </a>
         <a class="nav-link px-4 py-2 rounded-lg text-white/80 hover:text-white hover:bg-white/10 transition-colors" href="#cpu-section">
           <i class="fas fa-microchip mr-1"></i> CPU
         </a>
         <a class="nav-link px-4 py-2 rounded-lg text-white/80 hover:text-white hover:bg-white/10 transition-colors" href="#disk-queue-section">
           <i class="fas fa-list mr-1"></i> Queue
         </a>
         <a class="nav-link px-4 py-2 rounded-lg text-white/80 hover:text-white hover:bg-white/10 transition-colors" href="#disk-await-section">
           <i class="fas fa-clock mr-1"></i> Await
         </a>
         <a class="nav-link px-4 py-2 rounded-lg text-white/80 hover:text-white hover:bg-white/10 transition-colors" href="#disk-rw-section">
           <i class="fas fa-exchange-alt mr-1"></i> Throughput
         </a>
         <a class="nav-link px-4 py-2 rounded-lg text-white/80 hover:text-white hover:bg-white/10 transition-colors" href="#disk-iops-section">
           <i class="fas fa-tachometer-alt mr-1"></i> IOPS
         </a>
         <a class="nav-link px-4 py-2 rounded-lg text-white/80 hover:text-white hover:bg-white/10 transition-colors" href="#disk-util-section">
           <i class="fas fa-percentage mr-1"></i> Utilization
         </a>
       </nav>
     </div>
   </div>
 </header>

 <!-- Mobile navigation -->
 <nav class="md:hidden bg-accent-orange-600 border-t border-accent-orange-700">
   <div class="grid grid-cols-4 gap-1 p-2">
     <a class="nav-link px-2 py-2 rounded text-center text-white/80 hover:text-white hover:bg-white/10 text-xs" href="#summary-section">Summary</a>
     <a class="nav-link px-2 py-2 rounded text-center text-white/80 hover:text-white hover:bg-white/10 text-xs" href="#cpu-section">CPU</a>
     <a class="nav-link px-2 py-2 rounded text-center text-white/80 hover:text-white hover:bg-white/10 text-xs" href="#disk-queue-section">Queue</a>
     <a class="nav-link px-2 py-2 rounded text-center text-white/80 hover:text-white hover:bg-white/10 text-xs" href="#disk-await-section">Await</a>
     <a class="nav-link px-2 py-2 rounded text-center text-white/80 hover:text-white hover:bg-white/10 text-xs" href="#disk-rw-section">Throughput</a>
     <a class="nav-link px-2 py-2 rounded text-center text-white/80 hover:text-white hover:bg-white/10 text-xs" href="#disk-iops-section">IOPS</a>
     <a class="nav-link px-2 py-2 rounded text-center text-white/80 hover:text-white hover:bg-white/10 text-xs" href="#disk-util-section">Utilization</a>
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
           This analysis provides disk I/O and system performance metrics from your iostat output.
         </p>
         <p class="text-sm text-blue-700">
           For additional context on interpreting iostat data, consider reading
           <a href="https://www.redhat.com/sysadmin/linux-iostat-command" target="_blank"
              class="text-blue-600 hover:text-blue-700 underline font-medium">
             this guide on iostat analysis
           </a>
         </p>
       </div>
     </div>
   </div>
   <section id="summary-section" class="mb-12">
     <div class="flex items-center mb-6">
       <div class="w-12 h-12 bg-accent-orange-100 rounded-xl flex items-center justify-center mr-4">
         <i class="fas fa-chart-pie text-accent-orange-600 text-xl"></i>
       </div>
       <h2 class="text-2xl font-bold text-gray-800">Analysis Summary</h2>
     </div>
     <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
       <div class="bg-white rounded-xl shadow-sm p-6">
         <h3 class="text-lg font-semibold text-gray-800 mb-4 flex items-center">
           <i class="fas fa-chart-line text-accent-orange-600 mr-2"></i> Important Data Points
         </h3>
         %s
       </div>
       <div class="bg-white rounded-xl shadow-sm p-6">
         <h3 class="text-lg font-semibold text-gray-800 mb-4 flex items-center">
           <i class="fas fa-lightbulb text-yellow-600 mr-2"></i> Recommendations
         </h3>
         %s
       </div>
     </div>
   </section>

   %s
 </main>

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

 <!-- Footer -->
 <footer class="bg-gray-800 text-white py-8 mt-12">
   <div class="container mx-auto px-6">
     <div class="flex flex-col md:flex-row justify-between items-center">
       <div class="flex items-center mb-4 md:mb-0">
         <div class="w-8 h-8 bg-accent-orange-600 rounded-lg flex items-center justify-center mr-3">
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

 </body>
</html>
""",
              jsLibraryTextProvider.getTableCSS(),
              jsLibraryTextProvider.getPlotlyJsText(),
              jsLibraryTextProvider.getCSVExportText(),
              jsLibraryTextProvider.getSortableCSSText(),
              jsLibraryTextProvider.getSortableText(),
              jsLibraryTextProvider.getFilterTableText(),
              this.summaryText(summary),
              this.recommendations(summary),
              this.detailGraph(reportStats),
              DQDVersion.getVersion(),
              java.time.LocalDateTime.now()
                  .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
      output.write(template.getBytes("UTF-8"));
    }
  }

  Summary summaryStats(final ReportStats reportStats) {
    final long totalRecords = reportStats.cpuStats().size();
    final double percentageTimeOver50;
    if (reportStats.numberOfTimesOver50PerCpu() > 0) {
      percentageTimeOver50 =
          ((double) reportStats.numberOfTimesOver50PerCpu() / (double) totalRecords) * 100.0;
    } else {
      percentageTimeOver50 = 0.0;
    }
    final double percentageTimeOver90;
    if (reportStats.numberOfTimesOver90PerCpu() > 0) {
      percentageTimeOver90 =
          ((double) reportStats.numberOfTimesOver90PerCpu() / (double) totalRecords) * 100.0;
    } else {
      percentageTimeOver90 = 0.0;
    }
    final double percentageIOWaitTimeOver5;
    if (reportStats.ioBottleneckCount() > 0) {
      percentageIOWaitTimeOver5 =
          ((double) reportStats.ioBottleneckCount() / (double) totalRecords) * 100.0;
    } else {
      percentageIOWaitTimeOver5 = 0.0;
    }
    List<String> disks = new ArrayList<>();
    for (String key : reportStats.queueMap().keySet()) {
      disks.add(key);
    }
    Collections.sort(disks);
    final Map<String, Double> diskPer = new HashMap<>();
    for (final String deviceName : disks) {
      Long timesQueued = reportStats.queueMap().get(deviceName);
      final double percentageQueued;
      if (reportStats.ioBottleneckCount() > 0) {
        percentageQueued = ((double) timesQueued / (double) totalRecords) * 100.0;
      } else {
        percentageQueued = 0.0;
      }
      diskPer.put(deviceName, percentageQueued);
    }
    return new Summary(
        percentageTimeOver50,
        percentageTimeOver90,
        percentageIOWaitTimeOver5,
        disks,
        diskPer,
        totalRecords);
  }

  String summaryText(final Summary summary) {
    final HtmlTableBuilder builder = new HtmlTableBuilder();
    final Collection<Collection<HtmlTableDataColumn<String, Long>>> rows = new ArrayList<>();
    rows.add(
        Arrays.asList(
            col("number measurements"),
            col(String.format(Locale.US, "%,d", summary.totalRecords()))));
    rows.add(
        Arrays.asList(
            col(
                "%time over 50% user+system+steal+nice cpu usage (for systems with 2 threads per"
                    + " core)"),
            col(String.format(Locale.US, "%.2f%%", summary.percOver50()))));
    rows.add(
        Arrays.asList(
            col(
                "%time over 90% user+system+steal+nice cpu usage (for systems with 1 thread per"
                    + " core)"),
            col(String.format(Locale.US, "%.2f%%", summary.percOver90()))));
    rows.add(
        Arrays.asList(
            col("%time over iowait% is over 5%"),
            col(String.format(Locale.US, "%.2f%%", summary.percIOWaitOver5()))));
    for (final String deviceName : summary.diskNames()) {
      Double percentageQueued = summary.queuePerc().get(deviceName);
      rows.add(
          Arrays.asList(
              col(String.format("%%time %s had queue depth over 1.0", deviceName)),
              col(String.format(Locale.US, "%.2f%%", percentageQueued))));
    }
    return builder.generateTable(
        "summaryStatsTable", "Recommendations", Arrays.asList("name", "value"), rows);
  }

  String recommendations(final Summary summary) {
    final HtmlTableBuilder builder = new HtmlTableBuilder();
    final Collection<Collection<HtmlTableDataColumn<String, String>>> rows = new ArrayList<>();
    int counter = 0;
    if (summary.percIOWaitOver5() > 10.0) {
      List<String> problemDisks = new ArrayList<>();
      for (String disk : summary.diskNames()) {
        Double per = summary.queuePerc().get(disk);
        if (per > 10.0) {
          problemDisks.add(disk);
        }
      }
      if (problemDisks.size() > 0) {
        counter++;
        var rec =
            col(
                "Increase iops and throughput capacity on the following disks: %s",
                String.join(",", problemDisks));
        rows.add(Arrays.asList(col(String.valueOf(counter)), rec));
      } else {
        counter++;
        var rec =
            col(
                "The cpu is often waiting on the IO layer, this could be network. None of the disks"
                    + " are substantially saturated.",
                String.join(",", problemDisks));
        rows.add(Arrays.asList(col(String.valueOf(counter)), rec));
      }
    }
    if (summary.percOver50() > 10.0) {
      counter++;
      final HtmlTableDataColumn<String, String> rec =
          col(
              "For systems with 2 threads per core (look for lscpu output in the os_info.txt file"
                  + " of DDC to determine this), the CPU utilization is too high. Increase CPU"
                  + " count or reduce workload.");
      rows.add(Arrays.asList(col(String.valueOf(counter)), rec));
    }
    if (summary.percOver90() > 10.0) {
      counter++;
      final HtmlTableDataColumn<String, String> rec =
          col(
              "For systems with 1 thread per core (look for lscpu output in the os_info.txt file of"
                  + " DDC to determine this), the CPU utilization is too high. Increase CPU count"
                  + " or reduce workload.");
      rows.add(Arrays.asList(col(String.valueOf(counter)), rec));
    }
    return builder.generateTable(
        "summaryStatsTable", "Important Data Points", Arrays.asList("#", "recommendation"), rows);
  }

  String detailGraph(final ReportStats reportStats) {
    List<Double> userList = new ArrayList<>();
    List<Double> idleList = new ArrayList<>();
    List<Double> stealList = new ArrayList<>();
    List<Double> sysList = new ArrayList<>();
    List<Double> iowaitList = new ArrayList<>();
    List<Double> niceList = new ArrayList<>();

    for (final CPUStats cpu : reportStats.cpuStats()) {
      userList.add((double) cpu.user());
      idleList.add((double) cpu.idle());
      stealList.add((double) cpu.steal());
      sysList.add((double) cpu.system());
      iowaitList.add((double) cpu.iowait());
      niceList.add((double) cpu.nice());
    }
    List<LocalDateTime> times = reportStats.times();
    List<String> cpuTraces = new ArrayList<>();
    cpuTraces.add(makeTrace(times, userList, "user%"));
    cpuTraces.add(makeTrace(times, sysList, "sys%"));
    cpuTraces.add(makeTrace(times, iowaitList, "iowait%"));
    cpuTraces.add(makeTrace(times, niceList, "nice%"));
    cpuTraces.add(makeTrace(times, stealList, "steal%"));
    cpuTraces.add(makeTrace(times, idleList, "idle%"));

    List<String> diskQueueTraces = new ArrayList<>();
    List<String> disks = new ArrayList<>();
    for (final String d : reportStats.diskMap().keySet()) {
      disks.add(d);
    }
    Collections.sort(disks);
    for (final String d : disks) {
      List<Double> data =
          reportStats.diskMap().get(d).stream().map(x -> x.averageQueueSize()).toList();
      diskQueueTraces.add(makeTrace(times, data, d));
    }

    List<String> diskAwaitTraces = new ArrayList<>();

    for (final String d : disks) {
      List<Double> writeData =
          reportStats.diskMap().get(d).stream().map(x -> x.writeAverageWaitMillis()).toList();
      diskAwaitTraces.add(makeTrace(times, writeData, d + " write"));
      List<Double> readData =
          reportStats.diskMap().get(d).stream().map(x -> x.readAverageWaitMillis()).toList();
      diskAwaitTraces.add(makeTrace(times, readData, d + " read"));
    }

    List<String> diskRWTraces = new ArrayList<>();

    for (final String d : disks) {
      List<Double> writeData =
          reportStats.diskMap().get(d).stream().map(x -> x.writesKBPerSecond()).toList();
      diskRWTraces.add(makeTrace(times, writeData, d + " write"));
      List<Double> readData =
          reportStats.diskMap().get(d).stream().map(x -> x.readsKBPerSecond()).toList();
      diskRWTraces.add(makeTrace(times, readData, d + " read"));
    }

    List<String> diskIOPSTraces = new ArrayList<>();

    for (final String d : disks) {
      List<Double> writeData =
          reportStats.diskMap().get(d).stream().map(x -> x.writesPerSecond()).toList();
      diskIOPSTraces.add(makeTrace(times, writeData, d + " write"));
      List<Double> readData =
          reportStats.diskMap().get(d).stream().map(x -> x.readsPerSecond()).toList();
      diskIOPSTraces.add(makeTrace(times, readData, d + " read"));
    }

    List<String> diskUtilTraces = new ArrayList<>();

    for (final String d : disks) {
      List<Double> data =
          reportStats.diskMap().get(d).stream().map(x -> x.utilizationPercentage()).toList();
      diskUtilTraces.add(makeTrace(times, data, d));
    }

    return String.format(
        Locale.US,
        """
        <section id="cpu-section" class="mb-12">
          <div class="flex items-center mb-6">
            <div class="w-12 h-12 bg-orange-100 rounded-xl flex items-center justify-center mr-4">
              <i class="fas fa-microchip text-orange-600 text-xl"></i>
            </div>
            <h2 class="text-2xl font-bold text-gray-800">CPU Usage Analysis</h2>
          </div>
          <div class="chart-container">
            <div id="cpu-usage-graph" style="width: 100%%; height: 400px;"></div>
          </div>
        </section>

        <section id="disk-queue-section" class="mb-12">
          <div class="flex items-center mb-6">
            <div class="w-12 h-12 bg-blue-100 rounded-xl flex items-center justify-center mr-4">
              <i class="fas fa-list text-blue-600 text-xl"></i>
            </div>
            <h2 class="text-2xl font-bold text-gray-800">Disk Queue Analysis</h2>
          </div>
          <div class="chart-container">
            <div id="disk-queue-graph" style="width: 100%%; height: 400px;"></div>
          </div>
        </section>

        <section id="disk-await-section" class="mb-12">
          <div class="flex items-center mb-6">
            <div class="w-12 h-12 bg-purple-100 rounded-xl flex items-center justify-center mr-4">
              <i class="fas fa-clock text-purple-600 text-xl"></i>
            </div>
            <h2 class="text-2xl font-bold text-gray-800">Disk Await Time</h2>
          </div>
          <div class="chart-container">
            <div id="disk-await-graph" style="width: 100%%; height: 400px;"></div>
          </div>
        </section>

        <section id="disk-rw-section" class="mb-12">
          <div class="flex items-center mb-6">
            <div class="w-12 h-12 bg-green-100 rounded-xl flex items-center justify-center mr-4">
              <i class="fas fa-exchange-alt text-green-600 text-xl"></i>
            </div>
            <h2 class="text-2xl font-bold text-gray-800">Disk Throughput</h2>
          </div>
          <div class="chart-container">
            <div id="disk-io-graph" style="width: 100%%; height: 400px;"></div>
          </div>
        </section>

        <section id="disk-iops-section" class="mb-12">
          <div class="flex items-center mb-6">
            <div class="w-12 h-12 bg-red-100 rounded-xl flex items-center justify-center mr-4">
              <i class="fas fa-tachometer-alt text-red-600 text-xl"></i>
            </div>
            <h2 class="text-2xl font-bold text-gray-800">Disk IOPS</h2>
          </div>
          <div class="chart-container">
            <div id="disk-iops-graph" style="width: 100%%; height: 400px;"></div>
          </div>
        </section>

        <section id="disk-util-section" class="mb-12">
          <div class="flex items-center mb-6">
            <div class="w-12 h-12 bg-yellow-100 rounded-xl flex items-center justify-center mr-4">
              <i class="fas fa-percentage text-yellow-600 text-xl"></i>
            </div>
            <h2 class="text-2xl font-bold text-gray-800">Disk Utilization</h2>
          </div>
          <div class="chart-container">
            <div id="disk-util-graph" style="width: 100%%; height: 400px;"></div>
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

        Plotly.newPlot('cpu-usage-graph',[ %s ], {
          ...layoutDefaults,
          title: {
            text: 'CPU Usage Over Time (%%)',
            font: { size: 16 }
          }
        });

        Plotly.newPlot('disk-queue-graph',[ %s ], {
          ...layoutDefaults,
          title: {
            text: 'Average Disk Queue Size Over Time',
            font: { size: 16 }
          }
        });

        Plotly.newPlot('disk-await-graph',[ %s ], {
          ...layoutDefaults,
          title: {
            text: 'Disk Await Time (milliseconds)',
            font: { size: 16 }
          }
        });

        Plotly.newPlot('disk-io-graph',[ %s ], {
          ...layoutDefaults,
          title: {
            text: 'Disk Read/Write Throughput (KB/s)',
            font: { size: 16 }
          }
        });

        Plotly.newPlot('disk-iops-graph',[ %s ], {
          ...layoutDefaults,
          title: {
            text: 'Disk IOPS (Operations Per Second)',
            font: { size: 16 }
          }
        });

        Plotly.newPlot('disk-util-graph',[ %s ], {
          ...layoutDefaults,
          title: {
            text: 'Disk Utilization (%%)',
            font: { size: 16 }
          }
        });

        // Make charts responsive
        window.addEventListener('resize', () => {
          Plotly.Plots.resize('cpu-usage-graph');
          Plotly.Plots.resize('disk-queue-graph');
          Plotly.Plots.resize('disk-await-graph');
          Plotly.Plots.resize('disk-io-graph');
          Plotly.Plots.resize('disk-iops-graph');
          Plotly.Plots.resize('disk-util-graph');
        });
        </script>
        """,
        String.join(",", cpuTraces),
        String.join(",", diskQueueTraces),
        String.join(",", diskAwaitTraces),
        String.join(",", diskRWTraces),
        String.join(",", diskIOPSTraces),
        String.join(",", diskUtilTraces));
  }

  String makeTrace(List<LocalDateTime> times, List<Double> data, String title) {
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
        String.join(
            ",",
            times.stream().map(x -> String.format(Locale.US, "\"%s\"", x.toString())).toList()),
        String.join(",", data.stream().map(x -> x.toString()).toList()),
        title);
  }
}
