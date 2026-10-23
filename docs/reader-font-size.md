# Reader font size (0.5.11)

The reader gear menu contains a Reading font size submenu showing the current pixel size. Increase/decrease changes by one pixel within 10–28px; reset follows the IDEA UI default. This applies to rendered question text, headings, code and tables, plus reader loading/error messages. Native IDEA chrome and question-list tables retain IDE fonts.

ReaderAppearanceSettings is an application-level persistent service using mianshiya-reader.xml, separate from account credentials. A disposable application message-bus subscription refreshes all existing reader panels. Newly opened documents read the same setting; the ready callback applies the latest value if it changed while loading. The existing CSS theme/reflow path updates content without another question request and retains the reading anchor.

Validation: 78 existing tests passed (11 suites, zero failures/errors). verifyPlugin/buildPlugin/runPluginVerifier succeeded for IC-252.28539.97 (Community 2025.2.6.3); the same four deprecated API usage warnings remain. Native IDEA verified menu labels, immediate 12px -> 13px resizing and line reflow. After closing IDEA, the standalone appearance file contains fontSize=13. Distribution: build/distributions/Mianshiya-0.5.11.zip.
After restarting the same native IDEA sandbox, the menu correctly displayed the saved 13px value.
