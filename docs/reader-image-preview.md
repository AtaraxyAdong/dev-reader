# Image preview (0.5.13)

Click a rendered question/answer image to open a modal overlay inside the reader. It initially
fits the available reader area without changing the image aspect ratio or upscaling small images.
The controls offer zoom out/in, fit, and original size. Original-size/zoomed images remain
scrollable in both directions, including narrow or short tool windows.

Close with the visible close button, by clicking the dark outer mask, or with Esc. Keyboard
Enter/Space also opens a focused image; Tab stays inside the modal and closing returns focus
without moving the reading position. Clicking an image inside a link previews the image;
ordinary text links retain their existing behavior. A failed source image's existing link opens
an internal error/retry preview rather than an external browser.

Only the current HTTP(S) image URL may be displayed. There is no new URL scheme, remote
script, CSP permission, JCEF navigation permission or Cookie access. Login and content-fetching
code is unchanged. Closing drops the image source and callbacks; document changes discard
preview state and late image responses cannot reopen it.

## Scope and provenance

Built on the user's uncommitted 0.5.12 workspace in E:\01-Java\mianshiya-plugin, branch
codex/sidebar-markdown-reader, base HEAD 65171ae. This task changes only the three reader
resources, adds browser regression tests, and increments pluginVersion to 0.5.13. It does not
commit, push, install the plugin or restart IDEA. Existing user work remains in place.

## Browser regression

src/test/js/reader-image-preview.test.cjs runs the production reader template, CSS and JavaScript
in real headless Chrome through Playwright. All HTTP responses are controlled fixtures; no
account or external content is used. Requires Node.js, Playwright, and an installed Chrome.
Set NODE_PATH to an existing Playwright module directory if it is not installed locally; optionally
set READER_TEST_BROWSER to a compatible Chrome/Chromium executable. Run:

```powershell
node --test src/test/js/reader-image-preview.test.cjs
```

Nine cases cover fit/aspect ratio, eight repeated open/close cycles, all close routes, keyboard
focus, linked images and ordinary links, original-size scrolling/zoom/resize at 280 x 180,
load failure followed by success, document changes/unsafe image URLs, and closing during a
pending image response while retaining the reading position. Eight initial feature tests failed
against the original resources before implementation. Browser regressions do not replace native
IDEA/JCEF acceptance.

## Build validation

The full check, verifyPlugin and buildPlugin tasks completed, with 78 Java tests, zero failures,
errors or skipped tests. The Gradle runPluginVerifier task failed while resolving verifierPath
because its download endpoint terminated a TLS handshake. Compatibility is checked separately
with the already cached JetBrains Plugin Verifier 1.411 and the existing local IC-252.28539.97.
The final compatibility and package results are appended after verification.

Final result: cached Plugin Verifier 1.411 completed successfully against IC-252.28539.97.
Compatible, with the same four existing deprecated/scheduled-removal API warnings as 0.5.12.
The separately rerun check/verifyPlugin/buildPlugin command succeeds without the failed
verifier-download step. All nine browser tests pass. Fixture screenshots were visually checked
at 360 x 600 and 280 x 180; controls stay available and images preserve aspect ratio.
No native IDEA/JCEF session, account content or user screenshot was used for this acceptance.
Distribution: build/distributions/Mianshiya-0.5.13.zip. Package resources match the worktree.
