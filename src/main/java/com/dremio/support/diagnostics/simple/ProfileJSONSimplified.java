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
package com.dremio.support.diagnostics.simple;

import static com.dremio.support.diagnostics.shared.HtmlTableDataColumn.col;
import static com.dremio.support.diagnostics.shared.Human.getHumanBytes1024;
import static com.dremio.support.diagnostics.shared.Human.getHumanDurationFromNanos;
import static com.dremio.support.diagnostics.shared.Human.getHumanNumber;

import com.dremio.support.diagnostics.profilejson.CoreOperatorType;
import com.dremio.support.diagnostics.profilejson.QueryState;
import com.dremio.support.diagnostics.profilejson.plan.PlanRelation;
import com.dremio.support.diagnostics.profilejson.plan.PlanRelationshipParser;
import com.dremio.support.diagnostics.profilejson.singlefile.reports.summary.FindingsReport;
import com.dremio.support.diagnostics.repro.ArgSetup;
import com.dremio.support.diagnostics.shared.HtmlTableBuilder;
import com.dremio.support.diagnostics.shared.HtmlTableDataColumn;
import com.dremio.support.diagnostics.shared.Human;
import com.dremio.support.diagnostics.shared.JsLibraryTextProvider;
import com.dremio.support.diagnostics.shared.PathAndStream;
import com.dremio.support.diagnostics.shared.ProfileProvider;
import com.dremio.support.diagnostics.shared.UsageEntry;
import com.dremio.support.diagnostics.shared.UsageLogger;
import com.dremio.support.diagnostics.shared.dto.profilejson.FragmentProfile;
import com.dremio.support.diagnostics.shared.dto.profilejson.InputProfile;
import com.dremio.support.diagnostics.shared.dto.profilejson.MinorFragmentProfile;
import com.dremio.support.diagnostics.shared.dto.profilejson.OperatorProfile;
import com.dremio.support.diagnostics.shared.dto.profilejson.ProfileJSON;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;
import picocli.CommandLine;

public class ProfileJSONSimplified {
  public record Summary(
      String dremioVersion,
      long startEpochMillis,
      long endEpochMillis,
      int totalPhases,
      long totalOperators,
      String user,
      String queryPhase,
      Collection<String> findings,
      Collection<OperatorRow> operatorRows) {}

  public record OperatorRow(
      String hostName,
      long batches,
      long records,
      long sizeBytes,
      long operatorId,
      long phaseId,
      long threadId,
      long processNanos,
      long setupNanos,
      long waitNanos,
      long totalDurationNanos,
      long peakMemoryAllocatedBytes,
      CoreOperatorType coreOperatorType,
      String hostname) {

    public String name() {
      return String.format(
          "%s %02d-%02d-%02d", coreOperatorType(), phaseId(), threadId(), operatorId());
    }

    public double processSeconds() {
      if (processNanos == 0) {
        return 0;
      }
      return processNanos / 1000000000.0;
    }

    public double waitSeconds() {
      if (waitNanos == 0) {
        return 0;
      }
      return waitNanos / 1000000000.0;
    }

    public double setupSeconds() {
      if (setupNanos == 0) {
        return 0.0;
      }
      return setupNanos / 1000000000.0;
    }

    public double totalDurationSeconds() {
      if (totalDurationNanos == 0) {
        return 0.0;
      }
      return totalDurationNanos / 1000000000.0;
    }
  }

  public static class Summarize {

    public Summary singleProfile(ProfileJSON parsedProfileJSON) {
      String dremioVersion = "unknown";
      String user = "unknown user";
      String queryPhase = "UNKNOWN PHASE";
      long start = 0;
      long end = 0;
      int totalPhases = 0;
      List<OperatorRow> rows = new ArrayList<>();
      if (parsedProfileJSON == null) {
        return new Summary(
            dremioVersion, start, end, totalPhases, 0, user, queryPhase, new ArrayList<>(), rows);
      }
      final Collection<PlanRelation> planRelations =
          new PlanRelationshipParser().getPlanRelations(parsedProfileJSON);

      var findings = FindingsReport.searchForFindings(parsedProfileJSON, planRelations);
      if (parsedProfileJSON.getFragmentProfile() != null) {
        start = parsedProfileJSON.getStart();
        end = parsedProfileJSON.getEnd();
        if (parsedProfileJSON.getUser() != null && !parsedProfileJSON.getUser().isEmpty()) {
          user = parsedProfileJSON.getUser();
        }
        if (parsedProfileJSON.getState() < QueryState.values().length) {
          queryPhase = QueryState.values()[parsedProfileJSON.getState()].name();
        }
        if (parsedProfileJSON.getDremioVersion() != null
            && !parsedProfileJSON.getDremioVersion().isEmpty()) {
          dremioVersion = parsedProfileJSON.getDremioVersion();
        }
        for (final FragmentProfile fragmentProfile : parsedProfileJSON.getFragmentProfile()) {
          if (fragmentProfile != null) {
            final int phaseId = fragmentProfile.getMajorFragmentId();
            totalPhases++;
            for (final MinorFragmentProfile minorFragmentProfile :
                fragmentProfile.getMinorFragmentProfile()) {
              if (minorFragmentProfile != null
                  && minorFragmentProfile.getOperatorProfile() != null) {
                final long threadId = minorFragmentProfile.getMinorFragmentId();
                String hostName = "";
                if (minorFragmentProfile.getEndpoint() != null
                    && minorFragmentProfile.getEndpoint().getAddress() != null) {
                  hostName = minorFragmentProfile.getEndpoint().getAddress();
                }
                for (final OperatorProfile operatorProfile :
                    minorFragmentProfile.getOperatorProfile()) {
                  long batches = 0;
                  long records = 0;
                  long size = 0;
                  if (operatorProfile.getInputProfile() != null) {
                    for (final InputProfile inputProfile : operatorProfile.getInputProfile()) {
                      batches += inputProfile.getBatches();
                      records += inputProfile.getRecords();
                      size += inputProfile.getSize();
                    }
                  }
                  final int operatorTypeId = operatorProfile.getOperatorType();
                  final long operatorId = operatorProfile.getOperatorId();
                  final long processNanos = operatorProfile.getProcessNanos();
                  final long setupNanos = operatorProfile.getSetupNanos();
                  final long waitNanos = operatorProfile.getWaitNanos();
                  final long totalDurationNanos = processNanos + setupNanos + waitNanos;
                  final long peakLocalMemoryAllocated =
                      operatorProfile.getPeakLocalMemoryAllocated();
                  final CoreOperatorType[] coreOperatorTypes = CoreOperatorType.values();
                  CoreOperatorType operatorType = null;
                  if ((long) operatorTypeId < coreOperatorTypes.length) {
                    operatorType = coreOperatorTypes[operatorTypeId];
                  }
                  final OperatorRow row =
                      new OperatorRow(
                          hostName,
                          batches,
                          records,
                          size,
                          operatorId,
                          phaseId,
                          threadId,
                          processNanos,
                          setupNanos,
                          waitNanos,
                          totalDurationNanos,
                          peakLocalMemoryAllocated,
                          operatorType,
                          hostName);
                  rows.add(row);
                }
              }
            }
          }
        }
      }
      return new Summary(
          dremioVersion,
          start,
          end,
          totalPhases,
          rows.size(),
          user,
          queryPhase,
          findings,
          rows.stream()
              .sorted((x1, x2) -> Long.compare(x2.totalDurationNanos(), x1.totalDurationNanos()))
              .toList());
    }

    public SummaryCompare compareProfiles(ProfileJSON profile1, ProfileJSON profile2) {
      Summary summary1 = singleProfile(profile1);
      Summary summary2 = singleProfile(profile2);
      return new SummaryCompare(summary1, summary2);
    }
  }

  public record SummaryCompare(Summary summary1, Summary summary2) {}

  public static class ProfileHTTPEndpoint implements Handler {

    private static final Logger logger = Logger.getLogger(ProfileHTTPEndpoint.class.getName());
    private final UsageLogger usageLogger;

    public ProfileHTTPEndpoint(UsageLogger usageLogger) {
      this.usageLogger = usageLogger;
    }

    @Override
    public void handle(@NotNull Context ctx) throws Exception {
      var start = Instant.now();
      try (InputStream is = ctx.uploadedFiles().get(0).content()) {
        ProfileProvider profileProvider =
            ArgSetup.getProfileProvider(
                new PathAndStream(Paths.get(ctx.uploadedFiles().get(0).filename()), is));
        ProfileJSON p = profileProvider.getProfile();
        final Summary summary = new Summarize().singleProfile(p);
        final int unlimitedRows = -1;
        final String text = new HTMLReport().singleProfile(summary, unlimitedRows);
        ctx.html(text);
      } catch (Exception e) {
        logger.log(Level.SEVERE, "error reading uploaded file", e);
        ctx.html("<html><body>" + e.getMessage() + "</body>");
      } finally {
        logger.info("profile analysis report generated");
        var end = Instant.now();
        usageLogger.LogUsage(
            new UsageEntry(
                start.getEpochSecond(), end.getEpochSecond(), "profile-json-simple", ctx.ip()));
      }
    }
  }

  public static class HTMLReport {
    public String profileCompare(
        final Summary summary1, final Summary summary2, Integer limitOperatorRows) {
      String fragment1 = summaryFragment(summary1, 1, limitOperatorRows);
      String fragment2 = summaryFragment(summary2, 2, limitOperatorRows);

      return createHTMLPage(
          "Profile JSON Comparison",
          """
          <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
            <div>
              <h2 class="text-2xl font-semibold text-gray-800 mb-4 flex items-center">
                <i class="fas fa-file-alt mr-2 text-primary-600"></i>
                Profile 1
              </h2>
              %s
            </div>
            <div>
              <h2 class="text-2xl font-semibold text-gray-800 mb-4 flex items-center">
                <i class="fas fa-file-alt mr-2 text-primary-600"></i>
                Profile 2
              </h2>
              %s
            </div>
          </div>
          """
              .formatted(fragment1, fragment2));
    }

    public String summaryFragment(final Summary summary, int id, int limitOperatorRows) {
      var duration =
          Human.getHumanDurationFromMillis(summary.endEpochMillis() - summary.startEpochMillis());
      var formatter = DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneId.of("UTC"));
      var startTimeHuman = formatter.format(Instant.ofEpochMilli(summary.startEpochMillis()));
      var endTimeHuman = formatter.format(Instant.ofEpochMilli(summary.endEpochMillis()));

      // Build Query Summary Card
      StringBuilder querySummaryHtml = new StringBuilder();
      querySummaryHtml.append(
          """
          <div class="bg-white rounded-lg shadow-sm p-6 mb-6">
            <h2 class="text-xl font-semibold text-gray-800 mb-4 flex items-center">
              <i class="fas fa-info-circle mr-2 text-primary-600"></i>
              Query Summary
            </h2>
            <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div class="flex justify-between py-2 border-b border-gray-100">
                <span class="text-gray-600 font-medium">Start Time</span>
                <span class="text-gray-900">%s</span>
              </div>
              <div class="flex justify-between py-2 border-b border-gray-100">
                <span class="text-gray-600 font-medium">End Time</span>
                <span class="text-gray-900">%s</span>
              </div>
              <div class="flex justify-between py-2 border-b border-gray-100">
                <span class="text-gray-600 font-medium">Duration</span>
                <span class="text-gray-900 font-semibold">%s</span>
              </div>
              <div class="flex justify-between py-2 border-b border-gray-100">
                <span class="text-gray-600 font-medium">Phase</span>
                <span class="text-gray-900">
                  <span class="px-2 py-1 text-xs rounded-full %s">%s</span>
                </span>
              </div>
              <div class="flex justify-between py-2 border-b border-gray-100">
                <span class="text-gray-600 font-medium">Total Phases</span>
                <span class="text-gray-900">%d</span>
              </div>
              <div class="flex justify-between py-2 border-b border-gray-100">
                <span class="text-gray-600 font-medium">Total Operators</span>
                <span class="text-gray-900">%d</span>
              </div>
              <div class="flex justify-between py-2 border-b border-gray-100">
                <span class="text-gray-600 font-medium">Dremio Version</span>
                <span class="text-gray-900">%s</span>
              </div>
              <div class="flex justify-between py-2 border-b border-gray-100">
                <span class="text-gray-600 font-medium">User</span>
                <span class="text-gray-900">%s</span>
              </div>
            </div>
          </div>
          """
              .formatted(
                  startTimeHuman,
                  endTimeHuman,
                  duration,
                  getPhaseColorClass(summary.queryPhase()),
                  summary.queryPhase(),
                  summary.totalPhases(),
                  summary.totalOperators(),
                  summary.dremioVersion(),
                  summary.user()));

      // Build Findings Card
      StringBuilder findingsHtml = new StringBuilder();
      findingsHtml.append(
          """
          <div class="bg-white rounded-lg shadow-sm p-6 mb-6">
            <h2 class="text-xl font-semibold text-gray-800 mb-4 flex items-center">
              <i class="fas fa-exclamation-triangle mr-2 text-yellow-600"></i>
              Findings
            </h2>
          """);

      if (summary.findings().isEmpty()) {
        findingsHtml.append(
            """
            <p class="text-gray-500 italic">No findings detected</p>
            """);
      } else {
        findingsHtml.append(
            """
            <div class="space-y-2">
            """);
        int counter = 0;
        for (var finding : summary.findings()) {
          counter++;
          findingsHtml.append(
              """
<div class="flex items-start p-3 bg-yellow-50 border border-yellow-200 rounded-lg">
  <span class="flex-shrink-0 w-6 h-6 bg-yellow-200 text-yellow-800 rounded-full flex items-center justify-center text-xs font-semibold mr-3">%d</span>
  <span class="text-gray-700">%s</span>
</div>
"""
                  .formatted(counter, finding));
        }
        findingsHtml.append("</div>");
      }
      findingsHtml.append("</div>");

      // Build Operators Table Card
      var builder = new HtmlTableBuilder();
      final Collection<Collection<HtmlTableDataColumn<String, Number>>> operatorTableRows =
          new ArrayList<>();
      var operatorRowsStream = summary.operatorRows().stream();
      if (limitOperatorRows > 0) {
        operatorRowsStream = operatorRowsStream.limit(limitOperatorRows);
      }
      operatorRowsStream
          .map(
              x ->
                  Arrays.<HtmlTableDataColumn<String, Number>>asList(
                      HtmlTableDataColumn.col(x.name()),
                      col(getHumanDurationFromNanos(x.processNanos()), x.processNanos()),
                      col(getHumanDurationFromNanos(x.waitNanos()), x.waitNanos()),
                      col(getHumanDurationFromNanos(x.setupNanos()), x.setupNanos()),
                      col(
                          getHumanDurationFromNanos(x.totalDurationNanos()),
                          x.totalDurationNanos()),
                      col(getHumanBytes1024(x.sizeBytes()), x.sizeBytes()),
                      col(getHumanNumber(x.batches()), x.batches()),
                      col(getHumanNumber(x.records()), x.records()),
                      col(
                          getHumanNumber(x.records() / x.totalDurationSeconds()),
                          ((double) Math.round((x.records() * 100.0) / x.totalDurationSeconds())
                              / 100.0)),
                      col(
                          getHumanBytes1024(x.peakMemoryAllocatedBytes()),
                          x.peakMemoryAllocatedBytes()),
                      HtmlTableDataColumn.col(x.hostname())))
          .forEach(operatorTableRows::add);

      String operatorTableHtml =
          builder.generateTable(
              "operatorsTable" + id,
              "Operators",
              Arrays.asList(
                  "Name",
                  "Process",
                  "Wait",
                  "Setup",
                  "Total",
                  "Size Processed",
                  "Batches",
                  "Records",
                  "Records/Sec",
                  "Peak RAM Allocated",
                  "Node"),
              operatorTableRows);

      // Wrap the operators table in a card
      String operatorsCardHtml =
          """
          <div class="bg-white rounded-lg shadow-sm p-6">
            <h2 class="text-xl font-semibold text-gray-800 mb-4 flex items-center">
              <i class="fas fa-cogs mr-2 text-primary-600"></i>
              Operators
              %s
            </h2>
            <div class="overflow-x-auto">
              %s
            </div>
          </div>
          """
              .formatted(
                  limitOperatorRows > 0
                      ? "<span class=\"text-sm text-gray-500 ml-2\">(showing top "
                          + limitOperatorRows
                          + " slowest)</span>"
                      : "",
                  modernizeTable(operatorTableHtml));

      return querySummaryHtml.toString() + findingsHtml.toString() + operatorsCardHtml;
    }

    private String getPhaseColorClass(String phase) {
      return switch (phase) {
        case "COMPLETED" -> "bg-green-100 text-green-800";
        case "FAILED" -> "bg-red-100 text-red-800";
        case "CANCELLED" -> "bg-yellow-100 text-yellow-800";
        case "RUNNING" -> "bg-blue-100 text-blue-800";
        default -> "bg-gray-100 text-gray-800";
      };
    }

    private String modernizeTable(String oldTableHtml) {
      // Transform the old table HTML to use modern styling classes
      return oldTableHtml
          .replace("<table", "<table class=\"custom-table sortable\"")
          .replace("class=\"htmlTable\"", "class=\"custom-table sortable\"")
          .replace("<button class=\"export\"", "<button class=\"export-btn\"");
    }

    private String createHTMLPage(String title, String content) {
      var provider = new JsLibraryTextProvider();
      return """
<!DOCTYPE html>
<html lang="en">
  <head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>%s - DQD Analysis</title>

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
        @apply w-full text-sm text-left text-gray-700;
      }
      .custom-table thead {
        @apply text-xs text-gray-700 uppercase bg-gray-50 sticky top-0;
      }
      .custom-table tbody tr {
        @apply bg-white border-b hover:bg-gray-50 transition-colors;
      }
      .custom-table th {
        @apply px-6 py-3 font-medium;
      }
      .custom-table td {
        @apply px-6 py-4;
      }

      /* Table-specific styling for better alignment */
      table {
        table-layout: fixed;
        border-collapse: collapse;
      }

      /* Column-specific alignments and widths */
      th:nth-child(1), td:nth-child(1) { /* Name column */
        @apply text-left;
        width: 20%%;
        min-width: 200px;
      }

      th:nth-child(2), td:nth-child(2), /* Process */
      th:nth-child(3), td:nth-child(3), /* Wait */
      th:nth-child(4), td:nth-child(4), /* Setup */
      th:nth-child(5), td:nth-child(5) { /* Total */
        @apply text-right;
        width: 8%%;
        min-width: 80px;
      }

      th:nth-child(6), td:nth-child(6) { /* Size Processed */
        @apply text-right;
        width: 10%%;
        min-width: 100px;
      }

      th:nth-child(7), td:nth-child(7), /* Batches */
      th:nth-child(8), td:nth-child(8), /* Records */
      th:nth-child(9), td:nth-child(9) { /* Records/Sec */
        @apply text-right;
        width: 8%%;
        min-width: 80px;
      }

      th:nth-child(10), td:nth-child(10) { /* Peak RAM */
        @apply text-right;
        width: 10%%;
        min-width: 100px;
      }

      th:nth-child(11), td:nth-child(11) { /* Node */
        @apply text-left;
        width: 18%%;
        min-width: 180px;
        word-break: break-all;
      }

      /* Numeric data styling */
      td[data-sort] {
        font-family: 'Courier New', monospace;
        @apply text-gray-800;
      }

      /* Alternating row colors for better readability */
      tbody tr:nth-child(even) {
        @apply bg-gray-50;
      }

      tbody tr:nth-child(even):hover {
        @apply bg-gray-100;
      }

      /* Sortable table header styles */
      .sortable th {
        @apply cursor-pointer select-none;
      }
      .sortable th:hover {
        @apply bg-gray-100;
      }
      .sortable th::after {
        content: " ↕";
        @apply text-gray-400 text-xs;
      }
      .sortable th.sorted-asc::after {
        content: " ↑";
        @apply text-primary-600;
      }
      .sortable th.sorted-desc::after {
        content: " ↓";
        @apply text-primary-600;
      }

      /* Export button styles */
      .export-btn {
        @apply px-3 py-1 text-xs bg-gray-100 hover:bg-gray-200 rounded transition-colors;
      }

      /* Ensure table container allows horizontal scroll on small screens */
      .table-container {
        @apply overflow-x-auto;
      }

      /* Add some visual separation between columns */
      th, td {
        border-right: 1px solid #e5e7eb;
      }

      th:last-child, td:last-child {
        border-right: none;
      }

      /* Header styling */
      thead th {
        @apply bg-gray-100 font-semibold;
        white-space: nowrap;
      }
    </style>

    <!-- Include the necessary JavaScript -->
    <script>%s</script>
    <script>%s</script>
    <script>%s</script>
  </head>
  <body class="bg-gray-50">
    <!-- Navigation Bar -->
    <nav class="bg-white shadow-sm border-b border-gray-200">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="flex justify-between h-16">
          <div class="flex items-center">
            <div class="flex items-center space-x-3">
              <div class="w-10 h-10 bg-primary-600 rounded-lg flex items-center justify-center">
                <i class="fas fa-stethoscope text-white text-xl"></i>
              </div>
              <div>
                <h1 class="text-xl font-bold text-gray-800">DQD Analysis</h1>
              </div>
            </div>
          </div>
          <div class="flex items-center space-x-4">
            <a href="/" class="text-gray-600 hover:text-primary-600 transition-colors">
              <i class="fas fa-home mr-2"></i>Home
            </a>
          </div>
        </div>
      </div>
    </nav>

    <!-- Main Content -->
    <main class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      <div class="mb-8">
        <h1 class="text-3xl font-bold text-gray-900 flex items-center">
          <i class="fas fa-chart-line mr-3 text-primary-600"></i>
          %s
        </h1>
      </div>
      %s
    </main>
  </body>
</html>
"""
          .formatted(
              title,
              provider.getCSVExportText(),
              provider.getSortableText(),
              provider.getFilterTableText(),
              title,
              content);
    }

    public String singleProfile(final Summary summary, int limitOperatorRows) {
      return createHTMLPage("Profile JSON Summary", summaryFragment(summary, 0, limitOperatorRows));
    }
  }

  public record CliArgs(File path, File comparePath, int limitOperatorRows) {}

  @CommandLine.Command(
      name = "summarize-profile-json",
      mixinStandardHelpOptions = true,
      description = "A simplified profile-json command that only includes a brief summary")
  public static class Cli implements Callable<Integer> {

    @CommandLine.Parameters(index = "0", description = "The file whose checksum to calculate.")
    private File profile;

    @CommandLine.Option(
        names = {"-c", "--compare"},
        description = "A second profile to compare against")
    private File profileToCompare;

    @CommandLine.Option(
        names = {"-l", "--limit-operator-rows"},
        defaultValue = "-1",
        description = "optional limit on the rows in the operators table(s)")
    private Integer limitOperatorRows;

    public String execute(CliArgs args) {
      if (args.comparePath() == null) {
        try (final InputStream stream = Files.newInputStream(args.path().toPath())) {
          var profile =
              ArgSetup.getProfileProvider(new PathAndStream(args.path().toPath(), stream));
          final Summarize summarize = new Summarize();
          try {
            final Summary summary = summarize.singleProfile(profile.getProfile());
            var reporter = new ProfileJSONSimplified.HTMLReport();
            return reporter.singleProfile(summary, args.limitOperatorRows);
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      } else {
        try (final InputStream stream = Files.newInputStream(args.path().toPath())) {
          try (final InputStream compareStream =
              Files.newInputStream(args.comparePath().toPath())) {
            var profile =
                ArgSetup.getProfileProvider(new PathAndStream(args.path().toPath(), stream));
            var compareProfile =
                ArgSetup.getProfileProvider(
                    new PathAndStream(args.comparePath().toPath(), compareStream));
            final Summarize summarize = new Summarize();
            try {
              SummaryCompare compare =
                  summarize.compareProfiles(profile.getProfile(), compareProfile.getProfile());

              var reporter = new ProfileJSONSimplified.HTMLReport();

              return reporter.profileCompare(
                  compare.summary1(), compare.summary2(), args.limitOperatorRows());
            } catch (IOException e) {
              throw new RuntimeException(e);
            }
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    }

    @Override
    public Integer call() throws Exception {
      final var args = new CliArgs(profile, profileToCompare, limitOperatorRows);
      String outputText = execute(args);
      System.out.println(outputText);
      return 0;
    }
  }
}
