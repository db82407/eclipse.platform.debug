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

import java.lang.reflect.Method;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.VerifyEvent;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsoleDocumentPartitioner;
import org.eclipse.ui.console.IOConsole;
import org.eclipse.ui.console.IOConsoleInputStream;
import org.eclipse.ui.console.TextConsole;
import org.eclipse.ui.console.TextConsoleViewer;

/**
 * Viewer used to display an IOConsole
 *
 * @since 3.1
 */
public class IOConsoleViewer extends TextConsoleViewer {
    /**
     * will always scroll with output if value is true.
     */
    private boolean fAutoScroll = true;

    private IDocumentListener fDocumentListener;

    // echo characters locally
    private boolean fEcho = true;

	// direct access to InputStream, needed when echo == false.
    private IOConsoleInputStream inputStream;

    public IOConsoleViewer(Composite parent, TextConsole console) {
        super(parent, console);
        if (!isReadOnly()) {
			try {
				inputStream = ((IOConsole) console).getInputStream();
			} catch (UnsupportedOperationException e) {
				// so why isn't it read-only?
			}
        }
    }

    public void setEcho(boolean echo) {
        fEcho = echo;
    }

    public boolean isAutoScroll() {
        return fAutoScroll;
    }

    public void setAutoScroll(boolean scroll) {
        fAutoScroll = scroll;
    }

    /*
     * (non-Javadoc)
     *
     * @see org.eclipse.jface.text.TextViewer#handleVerifyEvent(org.eclipse.swt.events.VerifyEvent)
     */
    @Override
	protected void handleVerifyEvent(VerifyEvent e) {
        IDocument doc = getDocument();
        String[] legalLineDelimiters = doc.getLegalLineDelimiters();
        String eventString = e.text;

		if (!fEcho && inputStream != null) {
			inputStream.appendData(eventString);
			e.doit = false;
			return;
		}

        try {
            IConsoleDocumentPartitioner partitioner = (IConsoleDocumentPartitioner) doc.getDocumentPartitioner();
            if (!partitioner.isReadOnly(e.start)) {
                boolean isCarriageReturn = false;
                for (int i = 0; i < legalLineDelimiters.length; i++) {
                    if (e.text.equals(legalLineDelimiters[i])) {
                        isCarriageReturn = true;
                        break;
                    }
                }

                if (!isCarriageReturn) {
                    super.handleVerifyEvent(e);
                    return;
                }
            }

            int length = doc.getLength();
            if (e.start == length) {
                super.handleVerifyEvent(e);
            } else {
                try {
                    doc.replace(length, 0, eventString);
                } catch (BadLocationException e1) {
                }
                e.doit = false;
            }
        } finally {
            StyledText text = (StyledText) e.widget;
            text.setCaretOffset(text.getCharCount());
        }
    }

    /**
     * makes the associated text widget uneditable.
     */
    public void setReadOnly() {
        ConsolePlugin.getStandardDisplay().asyncExec(new Runnable() {
            @Override
			public void run() {
                StyledText text = getTextWidget();
                if (text != null && !text.isDisposed()) {
                    text.setEditable(false);
                }
            }
        });
    }

    /**
     * @return <code>false</code> if text is editable
     */
    public boolean isReadOnly() {
        return !getTextWidget().getEditable();
    }

    /* (non-Javadoc)
     * @see org.eclipse.jface.text.ITextViewer#setDocument(org.eclipse.jface.text.IDocument)
     */
    @Override
	public void setDocument(IDocument document) {
        IDocument oldDocument= getDocument();

        super.setDocument(document);

        if (oldDocument != null) {
            oldDocument.removeDocumentListener(getDocumentListener());
        }
        if (document != null) {
            document.addDocumentListener(getDocumentListener());
            /*
			 * Set IOConsoleViewer on IOConsolePartitioner, so it can set cursor
			 * position. Called by reflection to avoid changing interface.
			 */
			Object partitioner = document.getDocumentPartitioner();
			try {
				Method method = partitioner.getClass().getMethod(
						"setViewer", new Class[] { IOConsoleViewer.class }); //$NON-NLS-1$
				method.invoke(partitioner, new Object[] { this });
			} catch (Exception e) {
				// ignore
			}
        }
    }

    private IDocumentListener getDocumentListener() {
        if (fDocumentListener == null) {
            fDocumentListener= new IDocumentListener() {
				@Override
				public void documentAboutToBeChanged(DocumentEvent event) {
                }

                @Override
				public void documentChanged(DocumentEvent event) {
                    if (fAutoScroll) {
                        revealEndOfDocument();
                    }
                }
            };
        }
        return fDocumentListener;
    }
}
