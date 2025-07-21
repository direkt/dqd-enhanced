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
package com.dremio.support.diagnostics.queriesjson;

import static com.dremio.support.diagnostics.shared.HtmlTableDataColumn.col;
import static java.util.Arrays.asList;

import com.dremio.support.diagnostics.queriesjson.html.ConcurrentQueueWriter;
import com.dremio.support.diagnostics.queriesjson.html.Dates;
import com.dremio.support.diagnostics.queriesjson.html.FailedQueriesWriter;
import com.dremio.support.diagnostics.queriesjson.html.MaxCPUTimeWriter;
import com.dremio.support.diagnostics.queriesjson.html.MaxMemoryQueriesWriter;
import com.dremio.support.diagnostics.queriesjson.html.MaxTimeWriter;
import com.dremio.support.diagnostics.queriesjson.html.MemoryAllocatedWriter;
import com.dremio.support.diagnostics.queriesjson.html.RequestByQueueWriter;
import com.dremio.support.diagnostics.queriesjson.html.RequestCounterWriter;
import com.dremio.support.diagnostics.queriesjson.html.SlowestMetadataRetrievalWriter;
import com.dremio.support.diagnostics.queriesjson.html.SlowestPlanningWriter;
import com.dremio.support.diagnostics.queriesjson.reporters.ConcurrentQueriesReporter;
import com.dremio.support.diagnostics.queriesjson.reporters.ConcurrentQueueReporter;
import com.dremio.support.diagnostics.queriesjson.reporters.ConcurrentSchemaOpsReporter;
import com.dremio.support.diagnostics.queriesjson.reporters.FailedQueriesReporter;
import com.dremio.support.diagnostics.queriesjson.reporters.MaxCPUQueriesReporter;
import com.dremio.support.diagnostics.queriesjson.reporters.MaxMemoryQueriesReporter;
import com.dremio.support.diagnostics.queriesjson.reporters.MaxTimeReporter;
import com.dremio.support.diagnostics.queriesjson.reporters.MemoryAllocatedReporter;
import com.dremio.support.diagnostics.queriesjson.reporters.RequestCounterReporter;
import com.dremio.support.diagnostics.queriesjson.reporters.RequestsByQueueReporter;
import com.dremio.support.diagnostics.queriesjson.reporters.SlowestMetadataQueriesReporter;
import com.dremio.support.diagnostics.queriesjson.reporters.SlowestPlanningQueriesReporter;
import com.dremio.support.diagnostics.queriesjson.reporters.StartFinishReporter;
import com.dremio.support.diagnostics.queriesjson.reporters.TotalQueriesReporter;
import com.dremio.support.diagnostics.shared.DQDVersion;
import com.dremio.support.diagnostics.shared.HtmlTableBuilder;
import com.dremio.support.diagnostics.shared.HtmlTableDataColumn;
import com.dremio.support.diagnostics.shared.Human;
import com.dremio.support.diagnostics.shared.JsLibraryTextProvider;
import com.dremio.support.diagnostics.shared.Report;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

public class QueriesJsonHtmlReport implements Report {
  private static final Logger LOGGER = Logger.getLogger(QueriesJsonHtmlReport.class.getName());
  private final JsLibraryTextProvider jsLibraryTextProvider = new JsLibraryTextProvider();
  private final Collection<Query> failedQueries;
  private final long problematicQueryLimit;
  private final Instant startFilter;
  private final Instant endFilter;
  private final Instant start;
  private final Instant end;
  private final long totalQueries;
  private final Map<String, Long> requestCounterMap;
  private final Map<String, Long> requestsByQueue;
  private final Map<Long, Double> memoryUsage;
  private final Collection<Query> slowestPlanning;
  private final Collection<Query> slowestMetadata;
  private final long bucketSize;
  private final Map<Long, Long> maxPending;
  private final Map<Long, Long> maxMetadata;
  private final Map<Long, Long> maxQueued;
  private final Map<Long, Long> maxPlanning;
  private final Map<Long, Long> totalQueryCounts;
  private final Map<Long, Long> schemaOpsCounts;
  private final Map<String, Map<Long, Long>> queueCounts;
  private final Collection<SearchedFile> filesSearched;

  private final Collection<Query> mostMemoryQueries;
  private final Collection<Query> mostCpuTimeQueries;
  private final Map<Long, Long> maxPool;

  public QueriesJsonHtmlReport(
      Collection<SearchedFile> filesSearched,
      final Instant startFilter,
      final Instant endFilter,
      long bucketSize,
      final ConcurrentQueriesReporter concurrentQueriesReporter,
      final ConcurrentQueueReporter concurrentQueueReporter,
      final ConcurrentSchemaOpsReporter concurrentSchemaOpsReporter,
      final MaxMemoryQueriesReporter maxMemoryQueriesReporter,
      final MaxCPUQueriesReporter maxCpuQueriesReporter,
      final MaxTimeReporter maxTimeReporter,
      final MemoryAllocatedReporter memoryAllocatedReporter,
      final RequestCounterReporter requestCounterReporter,
      final RequestsByQueueReporter requestsByQueueReporter,
      final SlowestMetadataQueriesReporter slowestMetadataQueriesReporter,
      final SlowestPlanningQueriesReporter slowestPlanningQueriesReporter,
      final StartFinishReporter startFinishReporter,
      final TotalQueriesReporter totalQueriesReporter,
      final FailedQueriesReporter failedQueriesReporter,
      final long problematicQueryLimit) {
    this(
        filesSearched,
        startFilter,
        endFilter,
        bucketSize,
        totalQueriesReporter.getCount(),
        slowestPlanningQueriesReporter.getQueries(),
        slowestMetadataQueriesReporter.getQueries(),
        maxMemoryQueriesReporter.getQueries(),
        maxCpuQueriesReporter.getQueries(),
        requestCounterReporter.getRequestCounterMap(),
        requestsByQueueReporter.getRequestsByQueue(),
        memoryAllocatedReporter.getMemoryCounter(),
        maxTimeReporter.getPending(),
        maxTimeReporter.getMetadata(),
        maxTimeReporter.getQueued(),
        maxTimeReporter.getPlanning(),
        maxTimeReporter.getMaxPool(),
        concurrentQueriesReporter.getCounts(),
        concurrentSchemaOpsReporter.getBuckets(),
        concurrentQueueReporter.getQueueBucketCounts(),
        Instant.ofEpochMilli(startFinishReporter.getStart()),
        Instant.ofEpochMilli(startFinishReporter.getFinish()),
        failedQueriesReporter.getFailedQueries(),
        problematicQueryLimit);
  }

  private String getFailedParses() {
    final StringBuilder sb = new StringBuilder();
    sb.append(
        """
    <h3 class="text-lg font-semibold text-gray-800 mb-4">Filters Applied</h3>
    <div class="overflow-x-auto mb-6 rounded-lg border border-gray-200">
      <table class="custom-table">
        <thead>
          <tr>
            <th>Filter Name</th>
            <th>Value</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td>Start Filter</td>
            <td>%s</td>
          </tr>
          <tr>
            <td>End Filter</td>
            <td>%s</td>
          </tr>
        </tbody>
      </table>
    </div>

"""
            .formatted(startFilter, endFilter));
    sb.append("<h3 class=\"text-lg font-semibold text-gray-800 mb-4\">Files Searched</h3>");
    sb.append(
        "<div class=\"overflow-x-auto rounded-lg border border-gray-200\"><table"
            + " class=\"custom-table\"><thead><tr><th>File</th><th>Queries Parsed</th><th>Queries"
            + " Filtered by Date</th><th>Error</th></tr></thead><tbody>");
    for (SearchedFile s : filesSearched) {
      sb.append("<tr><td>");
      sb.append(s.name());
      sb.append("</td><td>");
      sb.append(s.parsed());
      sb.append("</td><td>");
      sb.append(s.filtered());
      sb.append("</td><td class=\"tooltip-pr\">");
      sb.append(s.errorText());
      sb.append("</td></tr>");
    }
    sb.append("</tbody></table></div>");
    return sb.toString();
  }

  private String modernizeHtml(String html) {
    // Apply Tailwind CSS classes to common HTML elements

    // Handle table styling
    html =
        html
            // First update any existing table classes
            .replace("class=\"sortable\"", "class=\"custom-table sortable\"")
            .replace("class=\"htmlTable\"", "class=\"custom-table sortable\"")
            // Then handle tables without classes
            .replaceAll("<table(?![^>]*class)", "<table class=\"custom-table sortable\"")
            // Update table elements
            .replace(
                "<thead>",
                "<thead class=\"text-xs text-gray-700 uppercase bg-gray-100 sticky top-0\">")
            .replace("<tbody>", "<tbody class=\"bg-white divide-y divide-gray-200\">")
            .replace("<tr>", "<tr class=\"hover:bg-gray-50 transition-colors\">")
            .replace("<th>", "<th class=\"px-6 py-3 text-left font-medium whitespace-nowrap\">")
            .replace("<td>", "<td class=\"px-6 py-4 text-sm text-gray-900\">");

    // Wrap tables in overflow containers if not already wrapped
    if (!html.contains("overflow-x-auto")) {
      html =
          html.replaceAll(
              "(<table[^>]*>)",
              "<div class=\"overflow-x-auto mb-6 rounded-lg border border-gray-200\">$1");
      html = html.replace("</table>", "</table></div>");
    }

    // Style other elements
    html =
        html
            // Style captions
            .replace(
                "<caption>",
                "<caption class=\"text-xl font-bold text-left text-gray-800 mb-4 mt-6\">")
            // Style buttons
            .replace(
                "<button",
                "<button class=\"px-3 py-1 text-xs bg-gray-100 hover:bg-gray-200 rounded"
                    + " transition-colors\"")
            // Style headings
            .replace("<h1>", "<h1 class=\"text-2xl font-bold text-gray-900 mb-4\">")
            .replace("<h2>", "<h2 class=\"text-xl font-semibold text-gray-800 mb-3\">")
            .replace("<h3>", "<h3 class=\"text-lg font-medium text-gray-700 mb-2\">")
            .replace("<h4>", "<h4 class=\"text-base font-medium text-gray-600 mb-2\">")
            // Style paragraphs
            .replace("<p>", "<p class=\"text-gray-700 mb-2\">")
            // Style lists
            .replace("<ul>", "<ul class=\"list-disc list-inside mb-4 text-gray-700\">")
            .replace("<ol>", "<ol class=\"list-decimal list-inside mb-4 text-gray-700\">");

    return html;
  }

  public QueriesJsonHtmlReport(
      final Collection<SearchedFile> filesSearched,
      final Instant startFilter,
      final Instant endFilter,
      final long bucketSize,
      final long totalQueries,
      final Collection<Query> slowestPlanning,
      final Collection<Query> slowestMetadata,
      final Collection<Query> mostMemoryQueries,
      final Collection<Query> mostCpuTimeQueries,
      final Map<String, Long> requestCounterMap,
      final Map<String, Long> requestsByQueue,
      final Map<Long, Double> memoryUsage,
      final Map<Long, Long> maxPending,
      final Map<Long, Long> maxMetadata,
      final Map<Long, Long> maxQueued,
      final Map<Long, Long> maxPlanning,
      final Map<Long, Long> maxPool,
      final Map<Long, Long> totalQueryCounts,
      final Map<Long, Long> schemaOpsCounts,
      final Map<String, Map<Long, Long>> queueCounts,
      final Instant start,
      final Instant end,
      final Collection<Query> failedQueries,
      final long problematicQueryLimit) {
    this.filesSearched = filesSearched;
    this.startFilter = startFilter;
    this.endFilter = endFilter;
    this.bucketSize = bucketSize;
    this.totalQueries = totalQueries;
    this.mostMemoryQueries = mostMemoryQueries;
    this.mostCpuTimeQueries = mostCpuTimeQueries;
    this.maxPending = maxPending;
    this.maxMetadata = maxMetadata;
    this.maxQueued = maxQueued;
    this.maxPlanning = maxPlanning;
    this.maxPool = maxPool;
    this.start = start;
    this.end = end;
    this.slowestPlanning = slowestPlanning;
    this.slowestMetadata = slowestMetadata;
    this.requestsByQueue = requestsByQueue;
    this.requestCounterMap = requestCounterMap;
    this.memoryUsage = memoryUsage;
    this.totalQueryCounts = totalQueryCounts;
    this.schemaOpsCounts = schemaOpsCounts;
    this.queueCounts = queueCounts;
    this.failedQueries = failedQueries;
    this.problematicQueryLimit = problematicQueryLimit;
  }

  private String getQueriesJSONHtml() {
    long durationMillis = this.end.toEpochMilli() - this.start.toEpochMilli();
    if (durationMillis < this.bucketSize) {
      return """
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Queries.json Report - DQD Analysis</title>

  <!-- Tailwind CSS -->
  <script src="https://cdn.tailwindcss.com"></script>

  <!-- Font Awesome -->
  <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">

  <!-- Custom Tailwind config -->
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
            }
          }
        }
      }
    }
  </script>
</head>
<body class="bg-gray-50">
  <div class="min-h-screen flex items-center justify-center">
    <div class="bg-white rounded-lg shadow-sm p-8 max-w-lg">
      <div class="text-center">
        <i class="fas fa-exclamation-triangle text-yellow-500 text-5xl mb-4"></i>
        <h3 class="text-xl font-semibold text-gray-800 mb-4">Bucket size is too large</h3>
        <p class="text-gray-600">Selected bucket size of %d milliseconds is bigger than the range examined %d milliseconds.</p>
        <p class="text-gray-600 mt-2">Try again with a bucket size of 1 second.</p>
        <a href="/" class="mt-6 inline-block px-4 py-2 bg-primary-600 text-white rounded-md hover:bg-primary-700 transition-colors">
          <i class="fas fa-arrow-left mr-2"></i>Go Back
        </a>
      </div>
    </div>
  </div>
</body>
</html>
"""
          .formatted(this.bucketSize, durationMillis);
    }
    final String totalCountsJs =
        new ConcurrentQueueWriter(this.bucketSize)
            .generate(
                this.start.toEpochMilli(),
                this.end.toEpochMilli(),
                this.queueCounts,
                this.schemaOpsCounts,
                this.totalQueryCounts);
    final String maxValuesJs =
        new MaxTimeWriter(this.bucketSize)
            .generate(
                this.start.toEpochMilli(),
                this.end.toEpochMilli(),
                maxPending,
                maxMetadata,
                maxQueued,
                maxPlanning,
                maxPool);
    final String memoryAllocatedJs =
        new MemoryAllocatedWriter(this.bucketSize)
            .generate(this.start.toEpochMilli(), this.end.toEpochMilli(), this.memoryUsage);
    final String requestCounter =
        RequestCounterWriter.generate(this.totalQueries, this.requestCounterMap);
    final String requestQueueCounter =
        RequestByQueueWriter.generate(this.totalQueries, this.requestsByQueue);
    final String summaryText = generateSummary();
    final String slowestMetadataQueries =
        SlowestMetadataRetrievalWriter.generate(this.totalQueries, this.slowestMetadata);
    final String slowestPlanningQueries =
        SlowestPlanningWriter.generate(this.totalQueries, this.slowestPlanning);
    final String maxMemoryQueries =
        MaxMemoryQueriesWriter.generateMaxMemoryAllocated(mostMemoryQueries);
    final String maxCpuTime = MaxCPUTimeWriter.generate(mostCpuTimeQueries);
    final String failedQueries =
        FailedQueriesWriter.generateTable(this.failedQueries, this.problematicQueryLimit);
    final String failedParses = this.getFailedParses();
    return """
<!DOCTYPE html>
<html lang="en">
  <head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Queries.json Report - DQD Analysis</title>

    <!-- Tailwind CSS -->
    <script src="https://cdn.tailwindcss.com"></script>

    <!-- Font Awesome -->
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">

    <!-- Custom Tailwind config -->
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
              }
            }
          }
        }
      }
    </script>

    <style>
      /* Custom table styles */
      .custom-table {
        @apply w-full text-sm text-left text-gray-700 border-collapse;
      }

      .custom-table thead {
        @apply text-xs text-gray-700 uppercase bg-gray-100 border-b-2 border-gray-200;
      }

      .custom-table thead th {
        @apply px-6 py-3 font-semibold tracking-wider text-left;
      }

      .custom-table tbody {
        @apply bg-white divide-y divide-gray-200;
      }

      .custom-table tbody tr {
        @apply hover:bg-gray-50 transition-colors;
      }

      .custom-table tbody td {
        @apply px-6 py-4 whitespace-nowrap text-sm text-gray-900;
      }

      /* Table wrapper */
      .table-wrapper {
        @apply overflow-x-auto rounded-lg border border-gray-200;
      }

      /* Tooltip styles */
      .tooltip-pr {
        @apply overflow-hidden whitespace-nowrap text-ellipsis;
      }

      .tooltip-pr:hover {
        @apply cursor-pointer whitespace-normal;
        transition: height 0.2s ease-in-out;
      }

      /* Section styling */
      section {
        scroll-margin-block-start: 100px;
      }

      /* Sticky navigation */
      #navbar {
        @apply transition-all duration-300;
      }

      #navbar.sticky {
        @apply fixed top-0 w-full shadow-lg z-50;
      }

      .content {
        @apply transition-all duration-300;
      }

      #navbar.sticky + .content {
        @apply pt-24;
      }

      /* Active nav link */
      .nav-link.active-link {
        @apply bg-primary-100 text-primary-700;
      }

      /* Sortable table styles */
      .sortable th {
        @apply cursor-pointer select-none relative pr-8;
      }

      .sortable th:hover {
        @apply bg-gray-200;
      }

      .sortable th::after {
        @apply absolute right-2 text-gray-400 text-xs;
        content: " ↕";
      }

      .sortable th.sorted-asc::after {
        @apply text-primary-600;
        content: " ↑";
      }

      .sortable th.sorted-desc::after {
        @apply text-primary-600;
        content: " ↓";
      }

      /* Grid layouts */
      .summary-page {
        @apply grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6;
      }

      .content-page {
        @apply grid grid-cols-1 lg:grid-cols-2 gap-6;
      }

      /* Card styling */
      .card {
        @apply bg-white rounded-lg shadow-sm p-6;
      }

      /* Button styles */
      button {
        @apply px-3 py-1 text-xs bg-gray-100 hover:bg-gray-200 rounded transition-colors;
      }

      %s
    </style>

    <script>
      %s
    </script>
    <script>
      %s
    </script>
    <script>
      %s
    </script>
    <script>
      %s
    </script>
  </head>
  <body class="bg-gray-50">
    <!-- Navigation Bar -->
    <nav id="navbar" class="bg-white border-b border-gray-200">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="flex justify-between h-16">
          <div class="flex items-center">
            <div class="flex items-center space-x-3">
              <div class="w-10 h-10 bg-primary-600 rounded-lg flex items-center justify-center">
                <i class="fas fa-database text-white text-xl"></i>
              </div>
              <div>
                <h1 class="text-xl font-bold text-gray-800">Queries.json Report</h1>
              </div>
            </div>
          </div>
          <div class="flex items-center space-x-1 overflow-x-auto">
            <a href="/" class="text-gray-600 hover:text-primary-600 px-3 py-2 rounded-md text-sm font-medium transition-colors flex-shrink-0">
              <i class="fas fa-home mr-2"></i>Home
            </a>
            <a class="nav-link text-gray-600 hover:text-primary-600 px-3 py-2 rounded-md text-sm font-medium transition-colors" href="#summary-section">Summary</a>
            <a class="nav-link text-gray-600 hover:text-primary-600 px-3 py-2 rounded-md text-sm font-medium transition-colors" href="#outliers-section">Outliers</a>
            <a class="nav-link text-gray-600 hover:text-primary-600 px-3 py-2 rounded-md text-sm font-medium transition-colors" href="#usage-section">Usage</a>
            <a class="nav-link text-gray-600 hover:text-primary-600 px-3 py-2 rounded-md text-sm font-medium transition-colors" href="#failures-section">Failures</a>
            <a class="nav-link text-gray-600 hover:text-primary-600 px-3 py-2 rounded-md text-sm font-medium transition-colors" href="#report-section">Report Debugging</a>
          </div>
        </div>
      </div>
    </nav>

    <!-- Main Content -->
    <main class="content max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      <div class="mb-8">
        <h1 class="text-3xl font-bold text-gray-900 flex items-center">
          <i class="fas fa-chart-bar mr-3 text-primary-600"></i>
          Queries.json Analysis
        </h1>
      </div>

      <section id="summary-section" class="mb-12">
        <h2 class="text-2xl font-semibold text-gray-800 mb-6 flex items-center">
          <i class="fas fa-info-circle mr-2 text-primary-600"></i>
          Summary
        </h2>
        <div class="summary-page">
          <div class="card">%s</div>
          <div class="card">%s</div>
          <div class="card">%s</div>
        </div>
      </section>

      <section id="outliers-section" class="mb-12">
        <h2 class="text-2xl font-semibold text-gray-800 mb-6 flex items-center">
          <i class="fas fa-exclamation-triangle mr-2 text-yellow-600"></i>
          Outliers
        </h2>
        <div class="content-page">
          <div class="card">%s</div>
          <div class="card">%s</div>
          <div class="card">%s</div>
          <div class="card">%s</div>
        </div>
      </section>

      <section id="usage-section" class="mb-12">
        <h2 class="text-2xl font-semibold text-gray-800 mb-6 flex items-center">
          <i class="fas fa-chart-line mr-2 text-primary-600"></i>
          Usage
        </h2>
        <div class="space-y-6">
          <div class="card">%s</div>
          <div class="card">%s</div>
          <div class="card">%s</div>
        </div>
      </section>

      <section id="failures-section" class="mb-12">
        <h2 class="text-2xl font-semibold text-gray-800 mb-6 flex items-center">
          <i class="fas fa-times-circle mr-2 text-red-600"></i>
          Failures
        </h2>
        <div class="card">%s</div>
      </section>

      <section id="report-section" class="mb-12">
        <h2 class="text-2xl font-semibold text-gray-800 mb-6 flex items-center">
          <i class="fas fa-bug mr-2 text-purple-600"></i>
          Report Debugging
        </h2>
        <div class="card">%s</div>
      </section>
    </main>

    <script>
      // Sticky navigation
      window.onscroll = function() {stickNav()};

      var navbar = document.getElementById("navbar");
      var sticky = navbar.offsetTop;

      function stickNav() {
        if (window.pageYOffset >= sticky) {
          navbar.classList.add("sticky")
        } else {
          navbar.classList.remove("sticky");
        }
      }

      // Active navigation highlighting
      const sections = document.querySelectorAll('section');
      const links = document.querySelectorAll('a.nav-link');

      window.addEventListener('scroll', () => {
          let scrollPosition = window.scrollY + 100;
          sections.forEach(section => {
              if (scrollPosition >= section.offsetTop) {
                  links.forEach(link => {
                      link.classList.remove('active-link');
                      if (section.getAttribute('id') === link.getAttribute('href').substring(1)) {
                          link.classList.add('active-link');
                      }
                  });
              }
          });
      });
    </script>
  </body>
</html>
"""
        .formatted(
            jsLibraryTextProvider.getSortableCSSText(),
            jsLibraryTextProvider.getPlotlyJsText(),
            jsLibraryTextProvider.getCSVExportText(),
            jsLibraryTextProvider.getSortableText(),
            jsLibraryTextProvider.getFilterTableText(),
            modernizeHtml(summaryText),
            modernizeHtml(requestCounter),
            modernizeHtml(requestQueueCounter),
            modernizeHtml(slowestMetadataQueries),
            modernizeHtml(slowestPlanningQueries),
            modernizeHtml(maxCpuTime),
            modernizeHtml(maxMemoryQueries),
            modernizeHtml(totalCountsJs),
            modernizeHtml(maxValuesJs),
            modernizeHtml(memoryAllocatedJs),
            modernizeHtml(failedQueries),
            modernizeHtml(failedParses));
  }

  @Override
  public String getText() {
    if (this.totalQueries == 0) {
      var sb =
          new StringBuilder(
              """
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Queries.json Report - DQD Analysis</title>

  <!-- Tailwind CSS -->
  <script src="https://cdn.tailwindcss.com"></script>

  <!-- Font Awesome -->
  <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">

  <!-- Custom Tailwind config -->
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
            }
          }
        }
      }
    }
  </script>
</head>
<body class="bg-gray-50">
  <div class="min-h-screen flex items-center justify-center">
    <div class="bg-white rounded-lg shadow-sm p-8 max-w-2xl w-full">
      <div class="text-center mb-6">
        <i class="fas fa-info-circle text-blue-500 text-5xl mb-4"></i>
        <h1 class="text-2xl font-bold text-gray-800 mb-4">No Queries Found</h1>
        <p class="text-gray-600 mb-6">No queries matched the filter criteria in the uploaded file.</p>
      </div>

      <div class="bg-gray-50 rounded-lg p-6 mb-6">
        <h2 class="text-lg font-semibold text-gray-800 mb-4">Report Details</h2>
""");
      sb.append(modernizeHtml(this.getFailedParses()));
      sb.append(
          """
      </div>

      <div class="text-center">
        <a href="/" class="inline-block px-6 py-3 bg-primary-600 text-white rounded-md hover:bg-primary-700 transition-colors">
          <i class="fas fa-arrow-left mr-2"></i>Try Another File
        </a>
      </div>
    </div>
  </div>
</body>
</html>
""");
      return sb.toString();
    }
    return getQueriesJSONHtml();
  }

  private String generateSummary() {
    final StringBuilder builder = new StringBuilder();
    if (this.totalQueries == 0) {
      builder.append("<h2>Queries Summary</h2>");
      builder.append("<p>No Queries Found</p>");
      return builder.toString();
    }

    final Collection<Collection<HtmlTableDataColumn<String, Long>>> rows = new ArrayList<>();
    rows.add(asList(col("first query start"), col(Dates.format(this.start))));
    final long durationMillis = this.end.toEpochMilli() - this.start.toEpochMilli();
    final double durationSeconds = durationMillis / 1000.0;
    rows.add(asList(col("last query end"), col(Dates.format(this.end))));
    rows.add(asList(col("time span"), col(Human.getHumanDurationFromMillis(durationMillis))));
    rows.add(asList(col("total queries"), col(String.format("%,d", this.totalQueries))));
    rows.add(
        asList(
            col("average queries per second"),
            col(String.format("%.2f", (this.totalQueries / durationSeconds)))));
    rows.add(asList(col("dqd version"), col(DQDVersion.getVersion())));
    long failedFileCount = filesSearched.stream().filter(x -> !"".equals(x.errorText())).count();
    rows.add(
        asList(
            col("invalid/total files"),
            col(String.format(Locale.US, "%d/%d", failedFileCount, filesSearched.size()))));
    var htmlBuilder = new HtmlTableBuilder();
    builder.append(
        htmlBuilder.generateTable(
            "queriesSummary", "Queries Summary", asList("name", "value"), rows));
    return builder.toString();
  }

  @Override
  public String getTitle() {
    return "Queries.json Report";
  }
}
