package org.ebookdroid.fb2droid.codec;

import org.ebookdroid.utils.StringUtils;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class FB2BinaryHandler extends DefaultHandler {

    private static final Pattern notesPattern = Pattern.compile("n([0-9]+)");
    private final StringBuilder tmpBinaryContents = new StringBuilder(64 * 1024);

    private final FB2Document document;

    private String tmpBinaryName = null;
    private boolean parsingNotes = false;
    private boolean parsingNotesP = false;
    private boolean parsingBinary = false;
    private boolean inTitle = false;
    private String noteName = null;
    private int noteId = -1;
    private boolean noteFirstWord = true;
    private ArrayList<FB2Line> noteLines = null;

    private boolean bold = false;
    private boolean italic = false;

    public FB2BinaryHandler(final FB2Document fb2Document) {
        this.document = fb2Document;
    }

    @Override
    public void startElement(final String uri, final String localName, final String qName, final Attributes attributes)
            throws SAXException {
        if ("binary".equalsIgnoreCase(qName)) {
            tmpBinaryName = attributes.getValue("id");
            tmpBinaryContents.setLength(0);
            parsingBinary = true;
        } else if ("body".equalsIgnoreCase(qName)) {
            if ("notes".equals(attributes.getValue("name"))) {
                parsingNotes = true;
            }
        } else if ("title".equalsIgnoreCase(qName)) {
            inTitle = true;
        } else if ("p".equalsIgnoreCase(qName)) {
            if (parsingNotes && !inTitle) {
                parsingNotesP = true;
            }
        } else if ("strong".equals(qName)) {
            FB2Document.FOOTNOTETEXTPAINT.setFakeBoldText(true);
            bold = true;
        } else if ("emphasis".equals(qName)) {
            FB2Document.FOOTNOTETEXTPAINT.setTypeface(FB2Document.ITALIC_TF);
            italic = true;
        } else if ("section".equalsIgnoreCase(qName)) {
            if (parsingNotes) {
                noteName = attributes.getValue("id");
                if (noteName != null) {
                    String n = getNoteId();
                    noteLines = new ArrayList<FB2Line>();
                    final FB2Line lastLine = FB2Line.getLastLine(noteLines);
                    lastLine.append(new FB2TextElement(n.toCharArray(), 0, n.length(), FB2Document.FOOTNOTE_SIZE,
                            (int) FB2Document.FOOTNOTETEXTPAINT.measureText(n), false, false));
                    lastLine.append(new FB2LineWhiteSpace((int) FB2Document.FOOTNOTETEXTPAINT.measureText(" "),
                            FB2Document.FOOTNOTE_SIZE, false));
                }
            }
        }
    }

    @Override
    public void endElement(final String uri, final String localName, final String qName) throws SAXException {
        if ("binary".equalsIgnoreCase(qName)) {
            document.addImage(tmpBinaryName, tmpBinaryContents.toString());
            tmpBinaryName = null;
            tmpBinaryContents.setLength(0);
            parsingBinary = false;
        } else if ("body".equalsIgnoreCase(qName)) {
            parsingNotes = false;
        } else if ("title".equalsIgnoreCase(qName)) {
            inTitle = false;
        } else if ("section".equalsIgnoreCase(qName)) {
            if (parsingNotes) {
                document.addNote(noteName, noteLines);
                noteLines = null;
                noteId = -1;
                noteFirstWord = true;
            }
        } else if ("p".equalsIgnoreCase(qName)) {
            if (parsingNotesP) {
                parsingNotesP = false;
                final FB2Line line = FB2Line.getLastLine(noteLines);
                line.append(new FB2LineWhiteSpace(FB2Page.PAGE_WIDTH - line.getWidth() - 2 * FB2Page.MARGIN_X,
                        (int) FB2Document.FOOTNOTETEXTPAINT.getTextSize(), false));
                for (final FB2Line l : noteLines) {
                    l.applyJustification(JustificationMode.Justify);
                }
            }
        } else if ("strong".equals(qName)) {
            FB2Document.FOOTNOTETEXTPAINT.setFakeBoldText(false);
            bold = false;
        } else if ("emphasis".equals(qName)) {
            FB2Document.FOOTNOTETEXTPAINT.setTypeface(FB2Document.NORMAL_TF);
            italic = false;
        }
    }

    private static int[] starts = new int[10000];
    private static int[] lengths = new int[10000];

    @Override
    public void characters(final char[] ch, final int start, final int length) throws SAXException {
        if (parsingBinary) {
            tmpBinaryContents.append(ch, start, length);
        }
        if (parsingNotesP && noteLines != null) {
            final int space = (int) FB2Document.FOOTNOTETEXTPAINT.measureText(" ");
            final int count = StringUtils.split(ch, start, length, starts, lengths);

            if (count > 0) {
                final char[] dst = new char[length];
                System.arraycopy(ch, start, dst, 0, length);

                for (int i = 0; i < count; i++) {
                    final int st = starts[i];
                    final int len = lengths[i];
                    if (noteFirstWord) {
                        noteFirstWord = false;
                        int id = -2;
                        try {
                            id = Integer.parseInt(new String(ch, st, len));
                        } catch (final Exception e) {
                            id = -2;
                        }
                        if (id == noteId) {
                            continue;
                        }
                    }
                    final FB2TextElement te = new FB2TextElement(dst, st - start, len,
                            (int) FB2Document.FOOTNOTETEXTPAINT.getTextSize(),
                            (int) FB2Document.FOOTNOTETEXTPAINT.measureText(ch, st, len), bold, italic);
                    FB2Line line = FB2Line.getLastLine(noteLines);
                    if (line.getWidth() + 2 * FB2Page.MARGIN_X + space + te.getWidth() < FB2Page.PAGE_WIDTH) {
                        if (line.hasNonWhiteSpaces()) {
                            line.append(new FB2LineWhiteSpace(space, (int) FB2Document.FOOTNOTETEXTPAINT.getTextSize(),
                                    true));
                        }
                    } else {
                        line = new FB2Line();
                        noteLines.add(line);
                    }
                    line.append(te);
                }
            }
        }
    }

    private String getNoteId() {
        final Matcher matcher = notesPattern.matcher(noteName);
        String n = noteName;
        if (matcher.matches()) {
            noteId = Integer.parseInt(matcher.group(1));
            n = "" + noteId + ")";
        }
        return n;
    }

}
