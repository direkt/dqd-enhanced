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
package com.dremio.support.diagnostics.shared;

import java.util.Collection;

public class HtmlTableBuilder {
  public <D, S> String generateTable(
      final String tableID,
      final String caption,
      final Collection<String> headers,
      final Collection<Collection<HtmlTableDataColumn<D, S>>> rows) {
    StringBuilder builder = new StringBuilder();

    builder.append("<div class=\"overflow-x-auto\">\n");
    builder.append("<div class=\"mb-4 flex justify-between items-center\">\n");
    builder.append("<h3 class=\"text-lg font-semibold text-gray-700 capitalize\">");
    builder.append(caption);
    builder.append("</h3>\n");
    if (rows != null && !rows.isEmpty()) {
      builder.append("<div class=\"flex items-center space-x-4\">\n");
      builder.append("<button onClick=\"exportAsCSV('");
      builder.append(tableID);
      builder.append(
          "')\" class=\"px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg"
              + " hover:bg-blue-700 transition-colors\">");
      builder.append("<i class=\"fas fa-download mr-2\"></i>Export CSV</button>\n");
      builder.append("</div>\n");
    }
    builder.append("</div>\n");

    final var inputID = tableID + "Input";
    final var spanID = tableID + "Span";
    builder.append("<div class=\"mb-4 flex items-center space-x-4\">\n");
    builder.append("<label class=\"text-sm font-medium text-gray-700\">Filter: </label>");
    builder.append("<input id=\"");
    builder.append(inputID);
    builder.append("\" type=\"text\" onkeyup=\"filterTable('");
    builder.append(tableID);
    builder.append("', '");
    builder.append(inputID);
    builder.append("', '");
    builder.append(spanID);
    builder.append(
        "')\" class=\"px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500"
            + " focus:border-blue-500\" placeholder=\"Type to search...\" />");
    builder.append("<span id=\"");
    builder.append(spanID);
    builder.append("\" class=\"text-sm text-gray-600\">");
    String name = "row";
    int rowCount = 0;
    if (rows != null) {
      rowCount = rows.size();
    }
    if (rowCount != 1) {
      name += "s";
    }
    String rowTitle = " %d %s shown".formatted(rowCount, name);
    builder.append(rowTitle);
    builder.append("</span>");
    builder.append("</div>\n");

    builder.append(
        "<table class=\"sortable comparison-table w-full text-sm text-left text-gray-700 rounded-lg"
            + " overflow-hidden\" id=\"");
    builder.append(tableID);
    builder.append("\">\n");
    builder.append("<thead class=\"text-xs text-gray-700 uppercase bg-gray-100\">\n");
    builder.append("<tr>\n");
    for (final String header : headers) {
      builder.append("<th class=\"px-6 py-3 font-medium\">");
      builder.append(header);
      builder.append("</th>\n");
    }
    builder.append("</tr>\n");
    builder.append("</thead>\n");
    builder.append("<tbody class=\"bg-white divide-y divide-gray-200\">\n");
    if (rows != null) {
      for (final Collection<HtmlTableDataColumn<D, S>> detail : rows) {
        builder.append("<tr class=\"hover:bg-gray-50 transition-colors\">");
        for (final HtmlTableDataColumn<D, S> column : detail) {
          String classes = "px-6 py-4 whitespace-nowrap";
          if (column.limitText()) {
            classes += " tooltip-pr relative";
          }
          if (column.sortableData() != null) {
            // the data-sort attribute works with https://tofsjonas.github.io/sortable/ to use a
            // machine parseable sort column
            builder.append("<td data-sort=\"");
            builder.append(column.sortableData());
            builder.append("\" class=\"%s\">".formatted(classes));
          } else {
            builder.append("<td class=\"%s\">".formatted(classes));
          }
          if (column.limitText()) {
            builder.append(
                "<span class=\"tooltiptext-pr absolute z-10 w-max max-w-xs px-3 py-2 text-sm"
                    + " text-white bg-gray-900 rounded-lg shadow-lg opacity-0 invisible"
                    + " group-hover:opacity-100 group-hover:visible transition-opacity duration-300"
                    + " bottom-full left-1/2 transform -translate-x-1/2 mb-2\">");
            builder.append(column.data());
            builder.append("</span>");
            builder.append("<span class=\"truncate block max-w-xs\">");
            builder.append(column.data());
            builder.append("</span>");
          } else {
            builder.append(column.data());
          }
          builder.append("</td>\n");
        }
        builder.append("</tr>\n");
      }
    }
    builder.append("</tbody>\n");
    builder.append("</table>\n");
    builder.append("</div>\n");
    return builder.toString();
  }
}
