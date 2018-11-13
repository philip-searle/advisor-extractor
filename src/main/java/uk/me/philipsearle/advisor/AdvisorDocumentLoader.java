package uk.me.philipsearle.advisor;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mindprod.ledatastream.LERandomAccessFile;

public class AdvisorDocumentLoader {
  /**
   * Magic number: 'LN', creator's initials (reversed here because Java is big-endian).
   */
  private static final int ADVISOR_FILE_MAGIC = ('N' << 8) | 'L';

  /** Version two is the only known one. */
  private static final int ADVISOR_FILE_VERSION = 2;

  /**
   * The character with which inline application-specific commands are prefixed.
   */
  private char applicationPrefix = '\0';

  /** The maximum length of all lines in this document in characters. */
  private int maxDisplayWidth;

  /** The 8.3 original filename of this document. */
  private String originalName;

  private Map<HelpTopic, List<String>> reverseGlobalContextMap;

  private Map<Long, HelpTopic> localContextMap;

  private List<Integer> huffmanTable;

  private List<byte[]> keywordTable;

  /** The character set to use when decoding text from the file. */
  private final Charset charset;

  private long topicMapOffset;

  private long contextStringTableOffset;

  private long contextMapOffset;

  private long keywordTableOffset;

  private long huffmanOffset;

  private long topicTextOffset;

  private long documentEndOffset;

  private long keywordTableEndoFfset;

  private int topicCount;

  private int globalContextCount;

  public AdvisorDocumentLoader(Charset charset) {
    this.charset = charset;
  }

  public AdvisorDocument loadDocument(File document) throws IOException {
    try (LERandomAccessFile file = new LERandomAccessFile(document, "r")) {
      parseHeader(file);

      keywordTable = parseKeywordTable(file);
      huffmanTable = parseHuffmanTable(file);
      List<HelpTopic> topics = parseTopics(file);
      reverseGlobalContextMap = parseContextMap(file, topics);

      CaseInsensitiveHashMap<HelpTopic> globalContextMap = new CaseInsensitiveHashMap<HelpTopic>();
      for (HelpTopic topic : topics) {
        reverseGlobalContextMap.getOrDefault(topic, Collections.emptyList())
                .forEach(globalContextId -> globalContextMap.put(globalContextId, topic));
      }
      return new AdvisorDocumentImpl(applicationPrefix, maxDisplayWidth, originalName, topics, globalContextMap);
    }
  }

  private void parseHeader(LERandomAccessFile file) throws IOException {
    int magic = file.readUnsignedShort();
    if (magic != ADVISOR_FILE_MAGIC) {
      throw new BadAdvisorFileException("Incorrect magic number: " + magic + ", expected " + ADVISOR_FILE_MAGIC);
    }

    int version = file.readUnsignedShort();
    if (version != ADVISOR_FILE_VERSION) {
      throw new BadAdvisorFileException("Incorrect file version: " + version + ", expected " + ADVISOR_FILE_VERSION);
    }

    skipUnknownShort(file, "flags");

    applicationPrefix = (char) file.readUnsignedByte();

    int unknown1 = file.readUnsignedByte();
    if (unknown1 != 0) {
      throw new IllegalStateException("Not yet implemented: unknown1");
    }

    topicCount = file.readUnsignedShort();
    globalContextCount = file.readUnsignedShort();
    maxDisplayWidth = file.readUnsignedShort();

    skipUnknownShort(file, "unknown2");

    byte[] originalNameBytes = new byte[12];
    file.readFully(originalNameBytes);
    originalName = new String(originalNameBytes, charset).trim();

    skipUnknownShort(file, "unknown3");
    skipUnknownShort(file, "unknown4");
    skipUnknownShort(file, "unknown5");

    topicMapOffset = file.readUnsignedInt();
    contextStringTableOffset = file.readUnsignedInt();
    contextMapOffset = file.readUnsignedInt();
    keywordTableOffset = file.readUnsignedInt();
    huffmanOffset = file.readUnsignedInt();
    topicTextOffset = file.readUnsignedInt();

    skipUnknownInt(file, "unknown6");
    skipUnknownInt(file, "unknown7");

    documentEndOffset = file.readUnsignedInt();
    keywordTableEndoFfset = huffmanOffset == 0 ? topicTextOffset : huffmanOffset;
  }

  private List<byte[]> parseKeywordTable(LERandomAccessFile file) throws IOException {
    if (keywordTableOffset == 0) {
      return Collections.emptyList();
    }

    List<byte[]> keywords = new ArrayList<>();
    file.seek(keywordTableOffset);
    while (file.getFilePointer() < keywordTableEndoFfset) {
      int keywordLength = file.readUnsignedByte();
      keywords.add(readCountedString(file, keywordLength));
    }
    return keywords;
  }

  private List<HelpTopic> parseTopics(LERandomAccessFile file) throws IOException {
    // Plus one for the EOF offset
    long topicOffsets[] = new long[topicCount + 1];
    topicOffsets[topicCount] = documentEndOffset;

    file.seek(topicMapOffset);
    for (int i = 0; i < topicCount; i++) {
      topicOffsets[i] = file.readUnsignedInt();
    }

    List<byte[]> compressedTopics = new ArrayList<>(topicCount);
    for (int i = 0; i < topicCount; i++) {
      file.seek(topicOffsets[i]);
      // The length of the compressed topic text must be calculated using the start of the next topic
      // (for the last topic we use the EOF, which is included at the end of the topicOffsets array)
      byte[] compressedTopicText = new byte[(int) (topicOffsets[i + 1] - topicOffsets[i])];
      file.readFully(compressedTopicText);
      compressedTopics.add(compressedTopicText);
    }

    List<HelpTopic> topics = new ArrayList<>();
    for (int i = 0; i < compressedTopics.size(); i++) {
      topics.add(new HelpTopic(topicOffsets[i], extractTopicText(decompress(compressedTopics.get(i)))));
    }
    return topics;
  }

  private Map<HelpTopic, List<String>> parseContextMap(LERandomAccessFile file, List<HelpTopic> topics)
          throws IOException {
    int[] topicIndexes = new int[globalContextCount];
    file.seek(contextMapOffset);
    for (int i = 0; i < globalContextCount; i++) {
      topicIndexes[i] = file.readUnsignedShort();
    }

    Map<HelpTopic, List<String>> reverseContextMap = new HashMap<>();
    file.seek(contextStringTableOffset);
    for (int i = 0; i < globalContextCount; i++) {
      String context = readNulTerminatedString(file);
      reverseContextMap.computeIfAbsent(topics.get(topicIndexes[i]), v -> new ArrayList<>()).add(context);
    }
    return reverseContextMap;
  }

  private List<Integer> parseHuffmanTable(LERandomAccessFile file) throws IOException {
    if (huffmanOffset == 0) {
      return null;
    }

    List<Integer> table = new ArrayList<>();
    int value;
    file.seek(huffmanOffset);
    do {
      value = file.readUnsignedShort();
      table.add(value);
    } while (value != 0);

    return table;
  }

  // TODO: This should decode characters using the correct Charset
  private String readNulTerminatedString(LERandomAccessFile file) throws IOException {
    CharsetDecoder decoder = charset.newDecoder();
    ByteBuffer in = ByteBuffer.allocate(1);
    CharBuffer out = CharBuffer.allocate(1);

    StringBuilder string = new StringBuilder();
    while (true) {
      int b = file.readUnsignedByte();
      if (b == 0) {
        return string.toString();
      }

      in.put(0, (byte) b);
      in.position(0);
      CoderResult result = decoder.decode(in, out, false);
      if (result.isError()) {
        string.appendCodePoint(0xfffd);
      } else if (out.position() > 0) {
        string.append(out.array(), 0, out.position());
        out.position(0);
        // string.appendCodePoint(b);
      }
    }
  }

  private byte[] readCountedString(LERandomAccessFile file, int length) throws IOException {
    byte[] buffer = new byte[length];
    for (int i = 0; i < length; i++) {
      buffer[i] = (byte) file.readUnsignedByte();
    }
    return buffer;
  }

  private void skipUnknownShort(LERandomAccessFile file, String name) throws IOException, IllegalStateException {
    int unknown = file.readUnsignedShort();
    if (unknown != 0) {
      throw new IllegalStateException("Not yet implemented: " + name);
    }
  }

  private void skipUnknownInt(LERandomAccessFile file, String name) throws IOException, IllegalStateException {
    long unknown = file.readUnsignedInt();
    if (unknown != 0) {
      throw new IllegalStateException("Not yet implemented: " + name);
    }
  }

  private byte[] decompress(byte[] compressedTopic) {
    int charCount = (compressedTopic[0] & 0xff) | ((compressedTopic[1] & 0xff) << 8);
    byte[] buffer = new byte[charCount];
    int bufferIndex = 0;

    CompresedTopicIterator it = new CompresedTopicIterator(huffmanTable, compressedTopic, 2);
    while (bufferIndex < charCount) {
      int c = it.nextByte();

      if (c < 0x10 || c > 0x1a) {
        buffer[bufferIndex++] = (byte) c;
        continue;
      }

      int command = c - 0x10;
      int parameter = it.nextByte();
      switch (command) {
        case 0:
        case 1:
        case 2:
        case 3:
        case 4:
        case 5:
        case 6:
        case 7: {
          // keyword from table
          int tableIndex = command & 0x03;
          byte[] keyword = keywordTable.get(parameter + 256 * tableIndex);
          for (byte element : keyword) {
            buffer[bufferIndex++] = element;
          }

          if (command >= 4) {
            // keyword from table (with space)
            buffer[bufferIndex++] = (byte) ' ';
          }
          break;
        }
        case 8: {
          // RLE (spaces)
          for (int count = 0; count < parameter; count++) {
            buffer[bufferIndex++] = (byte) ' ';
          }
          break;
        }
        case 9: {
          // RLE (arbitrary char)
          int repeatChar = parameter;
          parameter = it.nextByte();
          for (int count = 0; count < parameter; count++) {
            buffer[bufferIndex++] = (byte) repeatChar;
          }
          break;
        }
        case 10: {
          // Output literal
          buffer[bufferIndex++] = (byte) parameter;
          break;
        }
        default:
          throw new IllegalStateException("Unsupported compression command: " + command);
      }
    }

    return buffer;
  }

  private List<HelpTopicLine> extractTopicText(byte[] decompressedTopic) {
    List<HelpTopicLine> topicText = new ArrayList<>();

    int index = 0;
    while (index < decompressedTopic.length) {
      int lineLength = decompressedTopic[index++] - 1;
      StringBuilder text = new StringBuilder();

      ByteBuffer textSpan = ByteBuffer.wrap(decompressedTopic, index, lineLength);
      text.append(charset.decode(textSpan));
      index += lineLength;

      int attributesLength = (decompressedTopic[index++] & 0xff) - 1;
      byte[] attributes = new byte[attributesLength];
      for (int attributesIndex = 0; attributesIndex < attributesLength; attributesIndex++) {
        attributes[attributesIndex] = decompressedTopic[index++];
      }

      topicText.add(new HelpTopicLine(text.toString(), attributes));
    }

    return topicText;
  }
}
