package uk.me.philipsearle.advisor;

import java.util.List;

/**
 * A class that ierates over a Huffman-compressed bitstream.
 * It can also operate in a no-op mode when the huffman table is null or empty.
 */
class CompresedTopicIterator {
  private List<Integer> huffmanTable;
  private byte[] bitstream;
  private int byteOffset;
  private int byteMask;

  public CompresedTopicIterator(List<Integer> huffmanTable, byte[] bitstream, int initalOffset) {
    this.huffmanTable = huffmanTable;
    this.bitstream = bitstream;

    // Subtract one from the initial offset because the first bit
    // read will cause byteMask to wrap and advance the offset
    this.byteOffset = initalOffset - 1;
    this.byteMask = 1;
  }

  public int nextByte() {
    if (huffmanTable == null || huffmanTable.isEmpty()) {
      return bitstream[byteOffset++];
    }

    int huffmanIndex = 0;
    while (true) {
      int huffmanEntry = huffmanTable.get(huffmanIndex);
      if ((huffmanEntry & 0x8000) != 0) {
        // Reached leaf node
        return huffmanEntry & 0xff;
      }

      // Advance to next bit
      byteMask >>>= 1;
      if ((byteMask & 0xff) == 0) {
        byteMask = 0x80;
        byteOffset++;
      }

      // Non-leaf node, huffman table entry is index of entry to use for 0-bit
      // If a 1-bit then use next entry
      int compressedByte = bitstream[byteOffset] & 0xff;
      if ((compressedByte & byteMask) == 0) {
        // shift by 1 to convert byte offset to array index
        huffmanIndex = huffmanEntry >> 1;
      } else {
        huffmanIndex++;
      }
    }
  }
}