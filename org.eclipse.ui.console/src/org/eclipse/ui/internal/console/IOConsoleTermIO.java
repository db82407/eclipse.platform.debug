/*******************************************************************************
 * Copyright (c) 2000, 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.ui.internal.console;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.ui.console.IOConsoleInputStream;

/**
 * TermIO supports non-canonical (raw) input, where characters are returned
 * without waiting for a newline. A number of ANSI-like escape sequences are
 * supported to configure the terminal: "ESC { ? n ; m", where ? indicates the
 * command and n and m are optional integer arguments, which default to 0.
 *
 * <ul>
 * <li>ESC { C 0 - Non-canonical "raw" mode. Input is available immediately,
 * without waiting for newline. Backspace and other keys have no special effect.
 * </li>
 * <li>ESC { C 1 - Canonical mode (default). Input is only available after
 * newline. Backspace and arrow keys allow editing before input is available.</li>
 * <li>ESC { E ? - Local echo. If ? is 0 or missing, characters are not echoed
 * as they are typed.</li>
 * <li>ESC { I - Identify terminal type. The following string will be read as if
 * typed on the keyboard: "TERM=eclipse". This allows applications to determine
 * whether TermIO is supported.</li>
 * </ul>
 */
public class IOConsoleTermIO {
	private IDocument document;
	private IOConsoleInputStream inputStream;

	private boolean fICanon = true;
	private int cursorOffset = -1;
	private StyledText fTextWidget;
	private IOConsoleViewer fViewer;

	public IOConsoleTermIO(IDocument document, IOConsoleInputStream inputStream) {
		this.document = document;
		this.inputStream = inputStream;
	}

	public boolean isCanonical() {
		return fICanon;
	}

	/*
	 * Provides ConsoleViewer so that cursor can be repositioned after output.
	 * Called by reflection from IOConsoleViewer. derek.baum@paremus.com
	 */
	public void setViewer(IOConsoleViewer viewer) {
		fViewer = viewer;
		fTextWidget = fViewer.getTextWidget();
		fTextWidget.addKeyListener(new InputKeyListener());
	}

	public void moveCursorToEnd() {
		if (fTextWidget != null) {
			fTextWidget.setCaretOffset(fTextWidget.getCharCount());
		}
	}

	/*
	 * interprets \b and \r in output as cursor motion. TODO: handle ANSI escape
	 * sequences for cursor movement and color.
	 */
	void filterOutput(StringBuffer buffer) throws BadLocationException {
		int end = document.getLength();
		int start = document.getLineOffset(document.getLineOfOffset(end));
		String text = buffer.toString();
		int textLen = text.length();

		if (cursorOffset < start || cursorOffset > end) {
			cursorOffset = end;
		}

		buffer.setLength(0);
		for (int index = 0; index < textLen; ++index) {
			char ch = text.charAt(index);
			if (Character.isISOControl(ch)) {
				switch (ch) {
					case '\b':
					case '\r':
						if (buffer.length() > 0) {
							int replace = Math.min(buffer.length(), document.getLength() - cursorOffset);
							document.replace(cursorOffset, replace, buffer.toString());
							cursorOffset += buffer.length();
							buffer.setLength(0);
						}

						if (ch == '\r') {
							cursorOffset = start;
						} else {
							cursorOffset--;
						}
						break;

					case '\t':
					case '\n':
						buffer.append(ch);
						break;

					case '\033':
						// TODO: handle ANSI escape sequences
						buffer.append("^["); //$NON-NLS-1$
						break;

					case '\007':
						// ignore ^G bell
						break;

					default:
						buffer.append('^');
						buffer.append((char) ('A' + ch - 1));
						break;
				}
			} else {
				buffer.append(ch);
			}
		}

		if (buffer.length() > 0) {
			int replace = Math.min(buffer.length(), document.getLength() - cursorOffset);
			document.replace(cursorOffset, replace, buffer.toString());
			cursorOffset += buffer.length();
		}

		if (fTextWidget != null) {
			fTextWidget.setCaretOffset(cursorOffset);
		}
	}

	/*
	 * sets icanon and echo from escape sequences in output.
	 */
	void stty(StringBuffer buffer) {
		int index;
		while ((index = buffer.indexOf("\033{")) != -1) { //$NON-NLS-1$
			int arg0 = 0;
			char ch = '\0';
			int start = index + 2;

			for (int end = start; end < buffer.length(); ++end) {
				ch = buffer.charAt(end);

				if (!Character.isDigit(ch)) {
					if (end > start) {
						arg0 = Integer.parseInt(buffer.substring(start, end));
					}

					if (ch == ';') { // get next arg
						start = end + 1;
					} else {
						buffer.replace(index, end + 1, ""); //$NON-NLS-1$
						break;
					}
				}
			}

			switch (ch) {
				case 'C':
					fICanon = (arg0 != 0);
					break;
				case 'E':
					fViewer.setEcho(arg0 != 0);
					break;
				case 'I':
					// identify terminal type
					inputStream.appendData("TERM=eclipse"); //$NON-NLS-1$
					break;
				default:
					// unknown escape sequence
			}
		}
	}

	/**
	 * sends control & cursor key sequences to InputStream in non-canonical
	 * mode.
	 */
	private class InputKeyListener implements KeyListener {
		@Override
		public void keyPressed(KeyEvent e) {
			if (fICanon) {
				return;
			}

			if ((e.stateMask & SWT.MODIFIER_MASK) == SWT.CTRL) {
				switch (e.keyCode) {
					case 'h':
					case 'i':
					case 'j':
					case 'm':
						break;

					case 'a':
					case 'b':
					case 'c':
					case 'd':
					case 'e':
					case 'f':
					case 'g':
					case 'k':
					case 'l':
					case 'n':
					case 'o':
					case 'p':
					case 'q':
					case 'r':
					case 's':
					case 't':
					case 'u':
					case 'v':
					case 'w':
					case 'x':
					case 'y':
					case 'z':
						byte[] b = { (byte) (e.keyCode - 'a' + 1) };
						inputStream.appendData(new String(b));
						break;
					case '[':
					case '\\':
					case ']':
						byte[] b2 = { (byte) (e.keyCode - 'A' + 1) };
						inputStream.appendData(new String(b2));
						break;

					default:
						break;
				}
			} else if ((e.stateMask & SWT.MODIFIER_MASK) == 0) {
				switch (e.keyCode) {
					case SWT.BS:
						inputStream.appendData("\b"); //$NON-NLS-1$
						break;
					case SWT.ESC:
						inputStream.appendData("\033"); //$NON-NLS-1$
						break;
					case SWT.DEL:
						inputStream.appendData("\033[3~"); //$NON-NLS-1$
						break;
					case SWT.ARROW_UP:
						inputStream.appendData("\033[A"); //$NON-NLS-1$
						break;
					case SWT.ARROW_DOWN:
						inputStream.appendData("\033[B"); //$NON-NLS-1$
						break;
					case SWT.ARROW_RIGHT:
						inputStream.appendData("\033[C"); //$NON-NLS-1$
						break;
					case SWT.ARROW_LEFT:
						inputStream.appendData("\033[D"); //$NON-NLS-1$
						break;

					default:
						break;
				}
			}
		}

		@Override
		public void keyReleased(KeyEvent e) {
		}
	}

}
