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
package com.dremio.support.diagnostics.profilejson.singlefile;

import com.dremio.support.diagnostics.profilejson.Operator;
import com.dremio.support.diagnostics.profilejson.PhaseThread;
import com.dremio.support.diagnostics.profilejson.converttorel.ConvertToRelGraph;
import com.dremio.support.diagnostics.profilejson.converttorel.ConvertToRelGraphParser;
import com.dremio.support.diagnostics.profilejson.plan.PlanRelation;
import com.dremio.support.diagnostics.profilejson.plan.PlanRelationshipParser;
import com.dremio.support.diagnostics.profilejson.singlefile.reports.ProfileSummaryReport;
import com.dremio.support.diagnostics.profilejson.singlefile.reports.plots.OperatorDurationPlot;
import com.dremio.support.diagnostics.profilejson.singlefile.reports.plots.OperatorRecordsPlot;
import com.dremio.support.diagnostics.profilejson.singlefile.reports.plots.PhasesPlot;
import com.dremio.support.diagnostics.profilejson.singlefile.reports.plots.TimelinePlot;
import com.dremio.support.diagnostics.shared.Human;
import com.dremio.support.diagnostics.shared.JsLibraryTextProvider;
import com.dremio.support.diagnostics.shared.Report;
import com.dremio.support.diagnostics.shared.dto.profilejson.FragmentProfile;
import com.dremio.support.diagnostics.shared.dto.profilejson.MinorFragmentProfile;
import com.dremio.support.diagnostics.shared.dto.profilejson.OperatorProfile;
import com.dremio.support.diagnostics.shared.dto.profilejson.ProfileJSON;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class SingleProfileJsonHtmlReport implements Report {

  private final ProfileJSON parsed;
  private final boolean showConvertToRel;
  private final boolean showPlanDetails;
  private final GraphWriter sankeyWriter = new GraphWriter();
  private static final JsLibraryTextProvider jsLibProvider = new JsLibraryTextProvider();

  /**
   * Generates some graphs to display visual information not included in the summary
   *
   * @param showPlanDetails display all the plan details that are visible
   * @param showConvertToRel when true will display the convert to rel graph assuming there are not
   *     too many phases (default 100) that need to be displayed
   * @param parsed the ProfileJSON object full parsed
   */
  public SingleProfileJsonHtmlReport(
      final boolean showPlanDetails, final boolean showConvertToRel, final ProfileJSON parsed) {
    this.showPlanDetails = showPlanDetails;
    this.showConvertToRel = showConvertToRel;
    this.parsed = parsed;
  }

  /**
   * generates custom html based on the data inside the ProfileJson that was passed to the ctor
   *
   * @return html as a string ready to hand off the web server
   */
  @Override
  public String getText() {
    final List<PhaseThread> phaseThreads = getPhaseThreads();
    final long[] startTimes = new long[phaseThreads.size()];
    final long[] endTimes = new long[phaseThreads.size()];
    final String[] phaseThreadNames = new String[phaseThreads.size()];
    final String[] phaseThreadTextNames = new String[phaseThreads.size()];
    final long[] phaseProcessTimes = new long[phaseThreads.size()];
    for (int i = 0; i < phaseThreads.size(); i++) {
      final PhaseThread phaseThread = phaseThreads.get(i);
      startTimes[i] = phaseThread.getStartTime();
      endTimes[i] = phaseThread.getEndTime();
      phaseThreadNames[i] = String.format("%02d", phaseThread.getPhaseId());
      phaseThreadTextNames[i] =
          String.format(
              "%02d-%02d-XX - run %s, sleep %s, blocked { total %s, upstream %s, downstream %s,"
                  + " shared %s }",
              phaseThread.getPhaseId(),
              phaseThread.getThreadId(),
              Human.getHumanDurationFromMillis(phaseThread.getRunDuration()),
              Human.getHumanDurationFromMillis(phaseThread.getSleepingDuration()),
              Human.getHumanDurationFromMillis(phaseThread.getBlockedDuration()),
              Human.getHumanDurationFromMillis(phaseThread.getBlockedOnUpstreamDuration()),
              Human.getHumanDurationFromMillis(phaseThread.getBlockedOnDownstreamDuration()),
              Human.getHumanDurationFromMillis(phaseThread.getBlockedOnSharedResourceDuration()));
      phaseProcessTimes[i] = phaseThread.getTotalTimeMillis();
    }
    final List<String> scripts = new ArrayList<>();
    final List<String> htmlFragments = new ArrayList<>();
    final List<String> sections = new ArrayList<>();
    final List<String> titles = new ArrayList<>();
    final List<Operator> operators = new ArrayList<>();
    if (this.parsed != null) {
      final Collection<PlanRelation> planRelations =
          new PlanRelationshipParser().getPlanRelations(this.parsed);
      SummaryOut out =
          new ProfileSummaryReport()
              .generateSummary(this.showPlanDetails, this.parsed, planRelations);
      sections.addAll(out.sections());
      titles.addAll(out.titles());
      htmlFragments.add(modernizeHtml(out.htmlString()));
      final String plotlyJsText = jsLibProvider.getPlotlyJsText();
      scripts.add("<script>" + plotlyJsText + "</script>");
      final String mermaidJsText = jsLibProvider.getMermaidJsText();
      scripts.add("<script>" + mermaidJsText + "</script>");
      htmlFragments.add(
          """
          <section id="phases-section" class="bg-white rounded-lg shadow-sm p-6 mb-6">
          <h2 class="text-xl font-semibold text-gray-800 mb-4 flex items-center">
            <i class="fas fa-layer-group mr-2 text-primary-600"></i>
            Phases
          </h2>
          <div class="overflow-x-auto">
          %s
          </div>
          </section>
          """
              .formatted(
                  new PhasesPlot()
                      .generatePlot(phaseThreadNames, phaseProcessTimes, phaseThreadTextNames)));
      sections.add("phases-section");
      titles.add("Phases");
      htmlFragments.add(
          """
           <section id="timeline-section" class="bg-white rounded-lg shadow-sm p-6 mb-6">
            <h2 class="text-xl font-semibold text-gray-800 mb-4 flex items-center">
              <i class="fas fa-clock mr-2 text-primary-600"></i>
              Timeline
            </h2>
            <div class="overflow-x-auto">
           %s
            </div>
           </section>
          """
              .formatted(
                  new TimelinePlot()
                      .generatePlot(phaseThreadNames, startTimes, endTimes, phaseThreadTextNames)));
      sections.add("timeline-section");
      titles.add("Timeline");
      // graph out operators by process time
      if (this.parsed.getFragmentProfile() != null) {
        for (final FragmentProfile fragmentProfile : this.parsed.getFragmentProfile()) {
          if (fragmentProfile != null && fragmentProfile.getMinorFragmentProfile() != null) {
            final int phaseId = fragmentProfile.getMajorFragmentId();
            for (final MinorFragmentProfile minorProfile :
                fragmentProfile.getMinorFragmentProfile()) {
              if (minorProfile != null && minorProfile.getOperatorProfile() != null) {
                for (final OperatorProfile operatorProfile : minorProfile.getOperatorProfile()) {
                  final Operator operator =
                      Operator.createFromOperatorProfile(
                          operatorProfile, this.parsed.getOperatorTypeMetricsMap().getMetricsDef());
                  operator.setParentPhaseId(phaseId);
                  operator.setThreadId(minorProfile.getMinorFragmentId());
                  operators.add(operator);
                }
              }
            }
          }
        }
      }
      final String[] operatorNames = new String[operators.size()];
      final String[] operatorText = new String[operators.size()];
      final long[] operatorTimes = new long[operators.size()];
      final long[] operatorRecords = new long[operators.size()];
      for (int i = 0; i < operators.size(); i++) {
        final Operator operator = operators.get(i);
        // calculate relative id number to provide a clean layout with only phases
        // labeled using the
        // prefix feature
        operatorNames[i] = String.format("%02d", operator.getParentPhaseId());
        operatorText[i] =
            String.format(
                "%s { records: %s batches: %s setup: %s wait: %s process: %s }",
                String.format(
                    "%s %02d-%02d-%02d",
                    operator.getKind(),
                    operator.getParentPhaseId(),
                    operator.getThreadId(),
                    operator.getId()),
                operator.getRecords(),
                operator.getBatches(),
                Human.getHumanDurationFromMillis(operator.getSetupMillis()),
                Human.getHumanDurationFromMillis(operator.getWaitMillis()),
                Human.getHumanDurationFromMillis(operator.getProcessTimeMillis()));
        operatorTimes[i] = operator.getTotalTimeMillis();
        operatorRecords[i] = operator.getRecords();
      }

      htmlFragments.add(
          """
          <section id="op-duration-section" class="bg-white rounded-lg shadow-sm p-6 mb-6">
          <h2 class="text-xl font-semibold text-gray-800 mb-4 flex items-center">
            <i class="fas fa-hourglass-half mr-2 text-primary-600"></i>
            Duration Graph
          </h2>
          <div class="overflow-x-auto">
          %s
          </div>
          </section>
          """
              .formatted(
                  new OperatorDurationPlot()
                      .generatePlot(operatorNames, operatorTimes, operatorText)));
      sections.add("op-duration-section");
      titles.add("Duration Graph");

      htmlFragments.add(
          """
          <section id="op-records-section" class="bg-white rounded-lg shadow-sm p-6 mb-6">
          <h2 class="text-xl font-semibold text-gray-800 mb-4 flex items-center">
            <i class="fas fa-database mr-2 text-primary-600"></i>
            Records Graph
          </h2>
          <div class="overflow-x-auto">
          %s
          </div>
          </section>
          """
              .formatted(
                  new OperatorRecordsPlot()
                      .generatePlot(operatorNames, operatorRecords, operatorText)));
      sections.add("op-records-section");
      titles.add("Records Graph");
      final String convertToRel;
      if (showConvertToRel) {
        final ConvertToRelGraph c = new ConvertToRelGraphParser().parseConvertToRel(parsed);
        if (c != null) {
          sections.add("convert-to-rel-section");
          titles.add("Convert To Rel");
          convertToRel =
              """
              <section id="convert-to-rel-section" class="bg-white rounded-lg shadow-sm p-6 mb-6">
              <h2 class="text-xl font-semibold text-gray-800 mb-4 flex items-center">
                <i class="fas fa-project-diagram mr-2 text-primary-600"></i>
                Convert To Rel
              </h2>
              <div class="overflow-x-auto">
              %s
              </div>
              </section>
              """
                  .formatted(sankeyWriter.writeMermaid(c.getConvertToRelTree()));
        } else {
          convertToRel = "";
        }
      } else {
        convertToRel = "";
      }
      htmlFragments.add(convertToRel);
    } else {
      htmlFragments.add(
          """
<div class="bg-red-50 border border-red-200 rounded-lg p-4 mb-6">
  <h3 class="text-red-800 font-semibold">Too Many Phases: Disabled Graphs and Convert To Rel</h3>
</div>
""");
    }

    var sectionBuilder = new StringBuilder();
    for (int j = 0; j < titles.size(); j++) {
      final String title = titles.get(j);
      final String sectionName = sections.get(j);
      sectionBuilder.append(
          String.format(
              "<a class=\"nav-link text-gray-600 hover:text-primary-600 px-3 py-2 rounded-md"
                  + " text-sm font-medium transition-colors\" href=\"#%s\">%s</a>\n",
              sectionName, title));
    }

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

      /* Specific column alignments */
      .custom-table th:first-child,
      .custom-table td:first-child {
        @apply text-left;
      }

      .custom-table th:last-child,
      .custom-table td:last-child {
        @apply text-right;
      }

      /* Numeric columns - right align */
      .custom-table td[data-sort] {
        @apply text-right font-mono;
      }

      /* State Timings table specific styling */
      #stateTimingsTable td:nth-child(2),
      #stateTimingsTable td:nth-child(3),
      #stateTimingsTable th:nth-child(2),
      #stateTimingsTable th:nth-child(3) {
        @apply text-right;
      }

      /* Non Default Support Keys table */
      #nonDefaultSupportKeys td:nth-child(4),
      #nonDefaultSupportKeys th:nth-child(4) {
        @apply text-right;
      }

      /* Operators table styling */
      table[id*="operatorsTable"] td:nth-child(n+2),
      table[id*="operatorsTable"] th:nth-child(n+2) {
        @apply text-right;
      }

      /* Make tables responsive */
      .table-wrapper {
        @apply overflow-x-auto -mx-4 sm:-mx-6 lg:-mx-8;
      }

      .table-wrapper .custom-table {
        @apply min-w-full;
      }

      /* Caption styling */
      caption {
        @apply text-xl font-bold text-left text-gray-800 mb-4 mt-6;
      }

      /* Mermaid tooltip */
      .mermaidTooltip {
        @apply absolute text-center max-w-xs p-2 text-sm bg-yellow-50 border border-yellow-200 rounded shadow-lg pointer-events-none z-50;
      }

      /* Tooltip styles */
      .tooltip-pr {
        @apply overflow-hidden whitespace-nowrap text-ellipsis;
      }

      .tooltip-pr .tooltiptext-pr {
        @apply text-black;
        hyphens: auto;
      }

      .tooltip-pr:hover {
        @apply cursor-pointer whitespace-normal;
        transition: height 0.2s ease-in-out;
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

      /* Section scroll margin */
      section {
        scroll-margin-block-start: 110px;
        scroll-margin-block-end: 110px;
      }

      /* Section styling */
      section {
        @apply bg-white rounded-lg shadow-sm p-6 mb-6;
      }

      section h2 {
        @apply text-xl font-semibold text-gray-800 mb-4 flex items-center;
      }

      section h3 {
        @apply text-lg font-medium text-gray-700 mb-3;
      }

      /* Active nav link */
      .nav-link.active-link {
        @apply bg-primary-100 text-primary-700;
      }

      /* Button styles */
      button {
        @apply px-3 py-1 text-xs bg-gray-100 hover:bg-gray-200 rounded transition-colors;
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

      /* Ensure proper spacing */
      p {
        @apply mb-2 text-gray-700;
      }

      /* List styling */
      ul {
        @apply list-disc list-inside mb-4 text-gray-700;
      }

      ol {
        @apply list-decimal list-inside mb-4 text-gray-700;
      }

      /* Definition lists */
      dl {
        @apply mb-4;
      }

      dt {
        @apply font-semibold text-gray-800;
      }

      dd {
        @apply ml-4 mb-2 text-gray-700;
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
    <script>
    %s
    </script>
    %s
  </head>
  <body class="bg-gray-50">
    <!-- Navigation Bar -->
    <nav id="navbar" class="bg-white border-b border-gray-200">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="flex justify-between h-16">
          <div class="flex items-center">
            <div class="flex items-center space-x-3">
              <div class="w-10 h-10 bg-primary-600 rounded-lg flex items-center justify-center">
                <i class="fas fa-chart-line text-white text-xl"></i>
              </div>
              <div>
                <h1 class="text-xl font-bold text-gray-800">Detailed Profile Analysis</h1>
              </div>
            </div>
          </div>
          <div class="flex items-center space-x-1 overflow-x-auto">
            <a href="/" class="text-gray-600 hover:text-primary-600 px-3 py-2 rounded-md text-sm font-medium transition-colors flex-shrink-0">
              <i class="fas fa-home mr-2"></i>Home
            </a>
            %s
          </div>
        </div>
      </div>
    </nav>

    <!-- Main Content -->
    <main class="content max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      <div class="mb-8">
        <h1 class="text-3xl font-bold text-gray-900 flex items-center">
          <i class="fas fa-microscope mr-3 text-primary-600"></i>
          %s
        </h1>
      </div>
      %s
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
          let scrollPosition = window.scrollY + 140;
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
            this.getTitle(),
            jsLibProvider.getSortableCSSText(),
            jsLibProvider.getSortableText(),
            jsLibProvider.getCSVExportText(),
            jsLibProvider.getFilterTableText(),
            String.join("\n", scripts),
            sectionBuilder.toString(),
            this.getTitle(),
            String.join("\n", htmlFragments));
  }

  private List<PhaseThread> getPhaseThreads() {
    final List<PhaseThread> phaseThreads = new ArrayList<>();
    if (this.parsed != null && this.parsed.getFragmentProfile() != null) {
      for (final FragmentProfile phase : this.parsed.getFragmentProfile()) {
        if (phase != null && phase.getMinorFragmentProfile() != null) {
          final int phaseId = phase.getMajorFragmentId();
          for (final MinorFragmentProfile phaseThread : phase.getMinorFragmentProfile()) {
            final long threadId = phaseThread.getMinorFragmentId();
            final PhaseThread pt = getPhaseThread(phaseThread, phaseId, threadId);
            phaseThreads.add(pt);
          }
        }
      }
    }
    return phaseThreads;
  }

  private static PhaseThread getPhaseThread(
      MinorFragmentProfile phaseThread, int phaseId, long threadId) {
    final PhaseThread pt = new PhaseThread();
    pt.setPhaseId(phaseId);
    pt.setThreadId(threadId);
    pt.setRunDuration(phaseThread.getRunDuration());
    pt.setBlockedDuration(phaseThread.getBlockedDuration());
    pt.setBlockedOnUpstreamDuration(phaseThread.getBlockedOnUpstreamDuration());
    pt.setBlockedOnDownstreamDuration(phaseThread.getBlockedOnDownstreamDuration());
    pt.setBlockedOnSharedResourceDuration(phaseThread.getBlockedOnSharedResourceDuration());
    pt.setSleepingDuration(phaseThread.getSleepingDuration());
    pt.setTotalTimeMillis(phaseThread.getEndTime() - phaseThread.getStartTime());
    pt.setEndTime(phaseThread.getEndTime());
    pt.setStartTime(phaseThread.getEndTime() - pt.getRunDuration());
    return pt;
  }

  public ProfileJSON getParsed() {
    return parsed;
  }

  @Override
  public String getTitle() {
    return "Profile.json Analysis";
  }

  private String modernizeHtml(String html) {
    // Apply Tailwind CSS classes to common HTML elements

    // First wrap sections in cards
    html =
        html.replaceAll(
            "<section id=\"([^\"]+)\"[^>]*>",
            "<section id=\"$1\" class=\"bg-white rounded-lg shadow-sm p-6 mb-6\">");

    // Handle table styling - must be done carefully to avoid double-wrapping
    html =
        html
            // First update any existing table classes
            .replace("class=\"sortable\"", "class=\"custom-table sortable\"")
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

    // Wrap tables in overflow containers
    html =
        html.replaceAll(
            "(<table[^>]*>)",
            "<div class=\"overflow-x-auto mb-6 rounded-lg border border-gray-200\">$1");
    html = html.replace("</table>", "</table></div>");

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
            .replace("<ol>", "<ol class=\"list-decimal list-inside mb-4 text-gray-700\">")
            // Style grid layouts
            .replace(
                "class=\"summary-page\"",
                "class=\"grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6\"")
            .replace("class=\"content-page\"", "class=\"grid grid-cols-1 lg:grid-cols-2 gap-6\"");

    return html;
  }
}
