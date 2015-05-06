# eclipse.platform.debug
Eclipse Platform project repository (eclipse.platform.debug)

This repository contains a patch to the Eclipse console (org.eclipse.ui.console)
which supports non-canonical (raw) input, where characters are returned without waiting for a newline.
This allows applications using jline-like editing to work in the Eclipse console.

This patch also corrects the placement of the cursor in the console to just beyond that last output.
Without this patch, the cursor is only moved to correct position after a character is typed.

