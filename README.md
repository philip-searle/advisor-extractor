# advisor-extractor
Parser and data extractor for Microsoft Advisor .HLP files

The Microsoft Advisor file format was used in MS-DOS 5 to store the HELP, QBASIC, and EDIT on-line documentation.
It was also used in the Microsoft Programmer's Workbench product and (possibly) in Microsoft Bookshelf.
This project can parse and extract the hypertext content from these files.

# File Format
As far as I can tell, the Advisor file format was designed by Leo Notenboom while he was working for Microsoft.
United States patents [4,955,066](http://patft.uspto.gov/netacgi/nph-Parser?Sect2=PTO1&Sect2=HITOFF&p=1&u=%2Fnetahtml%2Fsearch-bool.html&r=1&f=G&l=50&d=PALL&RefSrch=yes&Query=PN%2F4955066) and [5,109,433](http://patft.uspto.gov/netacgi/nph-Parser?Sect2=PTO1&Sect2=HITOFF&p=1&u=%2Fnetahtml%2Fsearch-bool.html&r=1&f=G&l=50&d=PALL&RefSrch=yes&Query=PN%2F5109433) describe the file format in enough detail that a parser can be reimplemented.

## Overview
 - An Advisor file contains one or more *topics*.
 - Each topic is identified by a *local context ID*.  A local context ID is the index of the topic within the Advisor file.
 - Topics can optionally have *global context IDs* attached to them.  A global context ID is a string that can be used to reference the topic from outside the Advisor file.
 - A topic consists of one or more *lines of text*, expected to be displayed in a monospaced font.
 - A line of text can contain optional *markup* that indicates parts of the line should be displayed in *bold*, *italic*, or *underlined*.
 - Lines of text can also contain *hyperlinks* that redirect the user to another topic.  Hyperlinks link to other topics by either a local or global context ID.

Topic text can be stored in it's original form or with any of several forms of compression applied.
The possible compression options are:
 - Run-Length Encoding (RLE):
   This replaces repeated runs characters with a flag and repeat count.
   Repeated space characters are so common there is a special encoding that saves one extra byte compared to the usual RLE encoding.
 - Phrase encoding:
   This builds a dictionary of the 1024 most common words in the input text and replaces each word with a two-byte control code.
   The help file compiler Microsoft used had an additional option referred to as 'extended phrase compression':
   this is where entries from the phrase dictionary are also replaced within larger words,
   e.g. the word "the" would also be replaced inside "then" and "heather".
 - Huffman compression:
   This is a well-known compression technique where the most common input symbols are represented with much shorter output codes.
   Less frequently occurring symbols end up with longer symbols.

Since the Advisor file format was developed for MS-DOS applications, all data structures are little-endian with byte alignment.
The following data types are used in this document:

| Data Type | Size |
| --------- | ---- |
| BYTE      | 8 bits |
| WORD      | 16 bits |
| DWORD     | 32 bits |
| STR       | String: a sequence of BYTE values, terminated by an ASCII NUL character |

## File Header
The header is located at the start of the file and enables you to locate the data structures needed to interpret the rest of the file.

| Offset | Size  | Content | Description |
| ------ | ----- | ------- | ----------- |
| 0x0000 | WORD  | magic   | Arbitrary value that identifies this file as an Advisor document.  Always the value 0x4e4c ('LN' in ASCII, the initials of the file format's designer). |
| 0x0002 | WORD  | version | File format version.  Always 0x0002. |
| 0x0004 | WORD  | flags?  | I used to have details on the exact meaning of the bits in this field... it definitely stores the 'protected file' flag that indicates decompilers should not run on this file. |
| 0x0006 | BYTE  | applicationPrefix | Lines of topic text that start with this byte should be considered application-specific commands and not displayed to the user. |
| 0x0007 | BYTE  | unknown1 | No idea what this byte does.  Always 0x00? |
| 0x0008 | WORD  | topicCount | Number of topics stored in this file. |
| 0x000a | WORD  | globalContextCount | Number of global context IDs stored in this file. |
| 0x000c | WORD  | maxDisplayWidth | The display width (in characters) for which this file was authored. |
| 0x000e | WORD  | unknown2 | No idea what this word does.  Always 0x0000? |
| 0x0010 | BYTE[12] | originalName | The name of the file when it was generated, in 8.3 format.  Right-padded with NUL characters, but not NUL-terminated. |
| 0x001c | WORD  | unknown3 | No idea what this word does.  Always 0x0000? |
| 0x001e | WORD  | unknown4 | No idea what this word does.  Always 0x0000? |
| 0x0020 | WORD  | unknown5 | No idea what this word does.  Always 0x0000? |
| 0x0022 | DWORD | topicMapOffset | Offset from the start of the file to the topic map. |
| 0x0026 | DWORD | contextStringTableOffset | Offset from the start of the file to the context string table. |
| 0x002a | DWORD | contextMapOffset | Offset from the start of the file to the context map. |
| 0x002e | DWORD | keywordTableOffset | Offset from the start of the file to the keyword table.  Will be zero if phrase compression was not used. |
| 0x0032 | DWORD | huffmanOffset | Offset from the start of the file to the Huffman coding table.  Will be zero if Huffman coding was not used. |
| 0x0036 | DWORD | topicTextOffset | Offset from the start of the file to the topic text. |
| 0x003a | DWORD | unknown6 | No idea what this doubleword does.  Always zero? |
| 0x003e | DWORD | unknown7 | No idea what this doubleword does.  Always zero? |
| 0x0042 | DWORD | documentEndOffset | Offset from the start of the file to immediately after the end of file.  I believe this is used by the Microsoft Advisor library to support searching through multiple concatenated Advisor documents. |

## Keyword Table
The keyword table consists of 1024 counted strings.
A counted string has the structure:

| Content      | Name   | Description                    |
| ------------ | ------ | ------------------------------ |
| BYTE         | length | Length of the string in bytes. |
| BYTE[length] | data   | String content.                |

## Huffman table
If present, the Huffman table is an array of WORDs, terminated by a WORD with the value zero.

## Topic Map
The topic map stores the offset from the start of the file to the text for each topic.
It is an array of DWORDs, of length `header.topicCount`.

## Context Map and Context String Table
These two data structures allow you to convert a global context ID string to a local context ID.
The context string table is a list:

| Content | Description |
| ------- | ----------- |
| `STR[header.globalContextCount]` | Global context ID |

The context map is an array of WORDs, with each entry containing the local context ID that corresponding entry in the context string table maps to.

## Topic Text
Topic text is the most complex part of the Advisor format.
Once you've located the start of the topic text (using the topic map), you must decompress it.
Refer to `AdvisorDocumentLoader.extractTopicText()` and `HelpTopicLine.getHtmlFormattedText()` for the implementation.

1. The first two bytes of the topic text are a WORD storing the length of the topic text after all decompression steps have been applied.
2. If the `huffmanOffset` field in the file header is non-zero the remainder of the topic text is Huffman compressed and must be decoded first -- refer to `CompressedStreamIterator.java` for the exact algorithm.
3. Walk through the topic text one byte at a time:
   1. If the byte is less than 0x10 or greater than 0x1a then it is a literal and can be output as-is; otherwise it is a control code and is followed by a parameter byte.
   2. If the byte is between 0x10 and 0x13 then it is encoding a phrase from the keyword table: the lower two bits of the control code are combined with the parameter byte to form an index into the keyword table.
   3. If the byte is between 0x14 and 0x17 then it encodes a phrase from the keyword table as before, but with an implicit space character output afterwards.
   4. If the byte is 0x18 then it encodes a run of repeated space characters, with the run length taken from the parameter byte.
   5. If the byte is 0x19 then it encodes a run of repeated non-space characters.  The run length is taken from the parameter byte and the actual character to output is taken from the next byte in the topic text.
   6. Finally, if the byte is 0x1a then it encodes an escaped control code.  The byte to output is taken from the parameter byte.
4. Now you have the decoded text, you can extract the individual lines of text:
   1. Each line consists of a BYTE storing the line length, followed by an array of BYTEs containing the actual text.  Immediately after the last BYTE of text come the attributes.  These are stored similarly: a length BYTE followed by an array of attribute BYTEs.
   2. Skip the first byte of the attributes (I think it stores the default formatting style for the line).
   3. Each attribute consists of a style BYTE that encodes bold, italic, and underline in the lower three bits, followed by a length BYTE that indicates how many characters that style applies to.
   4. If the style BYTE is equal to 0xff then you have reached the end of the style attributes and the remaining bytes should be parsed as hyperlink data.
   5. Each hyperlink is stored as three BYTEs: the start and end indices of the characters within the line that should be turned into a link, and the link target. If the link target byte is zero then the next two BYTEs are actually a WORD that encodes the local context ID the link should point to. If it is non-zero then the link target byte is the first byte of a NUL-terminated string storing a global context ID.
