package uk.me.philipsearle.advisor;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A single line of text from a help topic. Attribute format is:
 * <ul>
 * <li>byte - default attributes for line???</li>
 * <li>repeat:
 * <ul>
 * <li>byte - text attribute</li>
 * <li>byte - length of text run with this attribute</li>
 * </ul>
 * A text attribute of 0xff indicates start of xref data:
 * <ul>
 * <li>repeat:
 * <ul>
 * <li>byte - ordinal of xref start in line</li>
 * <li>byte - index of xref end in line?</li>
 * <li>if next byte zero then following word is local context ref otherwise byte is first char of nul-terminated global
 * context ref</li>
 * </ul>
 * </ul>
 * </ul>
 */
class HelpTopicLine {
  public enum TextAttributes {
    BOLD, ITALIC, UNDERLINE
  }

  public class TextRun {
    public final int start, end;
    public final String text;
    public final Set<TextAttributes> attributes;
    public Optional<Integer> localContextLink;
    public Optional<String> globalContextLink;

    public TextRun(int start, int end, String text, Set<TextAttributes> attributes) {
      this.start = start;
      this.end = end;
      this.text = text;
      this.attributes = attributes;
      this.localContextLink = Optional.empty();
      this.globalContextLink = Optional.empty();
    }
  }

  private final String text;
  private final byte[] attributes;

  public HelpTopicLine(String text, byte[] attributes) {
    this.text = text;
    this.attributes = attributes;
  }

  public String getText() {
    return text;
  }

  public byte[] getAttributes() {
    return attributes;
  }

  public int getFirstAttributeByte() {
    return attributes[0] & 0xff;
  }

  public List<TextRun> getFormattedText() {
    List<TextRun> textRuns = new ArrayList<>();
    int index = 0;
    int textIndex = 0;

    // Skip the first byte (is it default attributes for the line?)
    index++;

    // Handle text styling attributes
    while (index < attributes.length) {
      int style = attributes[index++] & 0xff;

      if (style == 0xff) {
        // Found start of xref data
        break;
      }

      StringBuilder formattedText = new StringBuilder();
      EnumSet<TextAttributes> textAttributes = EnumSet.noneOf(TextAttributes.class);
      if ((style & 0x01) != 0) {
        textAttributes.add(TextAttributes.BOLD);
      }
      if ((style & 0x02) != 0) {
        textAttributes.add(TextAttributes.ITALIC);
      }
      if ((style & 0x04) != 0) {
        textAttributes.add(TextAttributes.UNDERLINE);
      }

      int runStart = textIndex;
      int runLength = attributes[index++];
      while (runLength-- > 0) {
        formattedText.append(text.charAt(textIndex++));
      }

      textRuns.add(new TextRun(runStart, textIndex, formattedText.toString(), textAttributes));
    }

    // Handle trailing unstyled text
    int runStart = textIndex;
    StringBuilder formattedText = new StringBuilder();
    while (textIndex < text.length()) {
      formattedText.append(text.charAt(textIndex++));
    }
    textRuns.add(new TextRun(runStart, textIndex, formattedText.toString(), EnumSet.noneOf(TextAttributes.class)));

    // while (index < attributes.length) {
    // int xrefStart = attributes[index++] & 0xff;
    // int xrefEnd = attributes[index++] & 0xff;
    // int xref = attributes[index++] & 0xff;
    //
    //
    // for (int i = 0; i < textRuns.size(); i++) {
    // TextRun run = textRuns.get(i);
    // if (run.start > xrefEnd) {
    // break;
    // }
    // if (run.end < xrefStart) {
    // continue;
    // }
    // }
    // html.append("<!-- xref @ ");
    // html.append(xrefStart);
    // html.append(':');
    // html.append(xrefEnd);
    //
    // if (xref != 0) {
    // html.append(" to global ");
    // do {
    // html.append((char) xref);
    // xref = attributes[index++] & 0xff;
    // } while (xref != 0);
    // } else {
    // html.append(" to local ");
    // xref = (attributes[index++] & 0xff);
    // xref |= (attributes[index++] & 0xff) << 8;
    // html.append(xref);
    // }
    // html.append(" -->");
    // }
    return textRuns;
  }

  private char safeCharAt(CharSequence cs, int index) {
    try {
      return cs.charAt(index);
    } catch (StringIndexOutOfBoundsException e) {
      System.err.println("Warning: bad string index " + index + " for string of length " + cs.length());
      return '\ufffd';
    }
  }

  public String getHtmlFormattedText(CaseInsensitiveHashMap<HelpTopic> globalContextMap) throws URISyntaxException {
    StringBuilder html = new StringBuilder();
    int index = 0;
    int textIndex = 0;

    int shift = 0;
    int[] charShifts = new int[80 * 4];// bodge size, should be large enough

    // Skip the first byte (is it default attributes for the line?)
    index++;

    // Handle text styling attributes
    while (index < attributes.length) {
      int style = attributes[index++] & 0xff;

      if (style == 0xff) {
        // Found start of xref data
        break;
      }

      if ((style & 0x01) != 0) {
        html.append("<b>");
        shift += 3;
      }
      if ((style & 0x02) != 0) {
        html.append("<i>");
        shift += 3;
      }
      if ((style & 0x04) != 0) {
        html.append("<u>");
        shift += 3;
      }

      int runLength = attributes[index++];
      while (runLength-- > 0) {
        charShifts[textIndex] = shift;
        html.append(safeCharAt(text, textIndex++));
      }

      if ((style & 0x04) != 0) {
        html.append("</u>");
        shift += 4;
      }
      if ((style & 0x02) != 0) {
        html.append("</i>");
        shift += 4;
      }
      if ((style & 0x01) != 0) {
        html.append("</b>");
        shift += 4;
      }
    }

    // Handle trailing unstyled text
    while (textIndex < text.length()) {
      charShifts[textIndex] = shift;
      html.append(safeCharAt(text, textIndex++));
    }

    while (index < attributes.length) {
      int xrefStart = attributes[index++] & 0xff;
      int xrefEnd = attributes[index++] & 0xff;
      int xref = attributes[index++] & 0xff;

      html.append("<!-- xref @ ");
      html.append(xrefStart);
      html.append(':');
      html.append(xrefEnd);

      String target = "";
      if (xref != 0) {
        html.append(" to global ");
        do {
          html.append((char) xref);
          target += (char) xref;
          xref = attributes[index++] & 0xff;
        } while (xref != 0);
        HelpTopic destinationTopic = globalContextMap.get(target);
        if (destinationTopic != null) {
          target = new URI(null, null, "TOPIC_" + destinationTopic.getLocalContextId() + ".HTML", target).toString();
        } else {
          System.err.println("Link references missing global context ID " + target);
          target = new URI(null, null, null, target).toString();
          html.append(" (missing!)");
        }
      } else {
        html.append(" to local ");
        xref = (attributes[index++] & 0xff);
        xref |= (attributes[index++] & 0xff) << 8;
        html.append(xref);
        target = new URI(null, null, "TOPIC_" + xref + ".HTML", Integer.toString(xref)).toString();
      }

      String linkStart = "<a href='" + target + "'>";
      String linkEnd = "</a>";
      html.insert(xrefStart + charShifts[xrefStart] - 1, linkStart);
      increaseShifts(charShifts, xrefStart, linkStart.length());
      html.insert(xrefEnd + charShifts[xrefEnd], linkEnd);
      increaseShifts(charShifts, xrefEnd, linkEnd.length());

      html.append(" -->");
    }

    return html.toString();
  }

  private void increaseShifts(int[] charShifts, int offset, int increase) {
    for (int i = offset; i < charShifts.length; i++) {
      charShifts[i] += increase;
    }
  }
}
