# Screen-centered image preview (0.5.14)

The user's 0.5.13 feedback requests the screen center rather than the sidebar center.
Image clicks now send a token-validated image message to the existing host bridge. The host
opens an IDE-owned, resizable, modeless DialogWrapper containing a dedicated restricted JCEF
viewer. The reader no longer contains an inline preview modal. Linked images and keyboard
activation use the same host path; ordinary text links retain their previous behavior.

## Placement and controls

The preview chooses the monitor with the largest usable-area intersection with the IDEA main
frame, including monitors with negative coordinates. If the frame is off-screen it chooses the
nearest monitor. Screen insets exclude taskbars/docks. Initial dimensions are 85% of that work
area, capped at 1200 x 900, and centered within it. Small work areas clamp the window size so
it stays on-screen. The main IDEA frame is preferred over a floating ToolWindow as the owner
reference. User resizing remains available and images refit when fit mode is active.

The separate viewer retains proportional fit, original size, zoom, bidirectional scrolling,
visible close controls and Esc. The native frame's close control and native Close action work
even if JCEF fails to initialize. DOM close events notify the host through a per-window token.
No external browser opens for images.

## Ownership and security

ImagePreviewController owns at most one window. Opening another image releases the previous
one. Showing reader status, loading another question, clearing the session and reader/project
disposal close the current window. JCEF browser/query objects are registered under the dialog
Disposable and the dialog under the reader Disposable. A delayed focus-return callback checks
reader/project lifetime and the original document token.

The native viewer uses the existing HTTP(S)-only image permission boundary, rejects userinfo,
and preserves the same Content-Security-Policy. It permits only its unique local main document;
subframe/remote navigation is blocked, and image requests cannot send or save browser Cookies.
Image URL and alt text are JSON encoded. No content-fetching or login implementation changes.

## Verification scope

- Java geometry/ownership tests cover multiple monitors, negative origins, taskbar insets,
  spanning/off-screen IDEA frames, small screens, repeated replacement/close, failed window
  display, project disposal, and unsafe/credential URLs. Preview-document tests cover hostile
  alt text and local-file rejection.
- Nine real headless Chrome tests exercise the production reader bridge and separate viewer
  resources: image/link/keyboard dispatch, fit, zoom, original-size scrolling, close notification,
  failed requests, pending-request close, pagehide cleanup and repeated open/close.
- These browser tests do not instantiate a native IDEA window. Actual multi-monitor placement,
  mixed-DPI native behavior and JCEF/OS window management still need native manual acceptance.

Built on the current user-modified workspace, branch codex/sidebar-markdown-reader, base HEAD
65171ae. No commit, push, user plugin installation or application restart is performed.
Final test/build/compatibility/package evidence is recorded below after execution.
