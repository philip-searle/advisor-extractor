package uk.me.philipsearle.advisor;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.CodeSource;

public class HtmlTopicExtractor {

  public static void main(String[] args) throws URISyntaxException, IOException {
    if (args.length != 2) {
      System.err.printf("Usage:\n\n\tjava -jar %s [hlpfile] [outputdir]\n", getRunningJarFile());
      System.exit(1);
    }

    File inputFile = new File(args[0]);
    File outputDirectory = new File(args[1]);

    AdvisorDocumentLoader documentLoader = new AdvisorDocumentLoader(Charset.forName("CP850"));
    AdvisorDocument advisorDocument = documentLoader.loadDocument(inputFile);

    outputDocumentSummary(advisorDocument, new File(outputDirectory, "_SUMMARY.TXT"));
    for (HelpTopic topic : advisorDocument.getTopics()) {
      System.out.println("Writing topic " + topic.getLocalContextId());
      outputTopicHtml(advisorDocument, topic,
              new File(outputDirectory, "TOPIC_" + topic.getLocalContextId() + ".HTML"));
    }
  }

  private static String getRunningJarFile() throws URISyntaxException {
    CodeSource codeSource = HtmlTopicExtractor.class.getProtectionDomain().getCodeSource();
    File jarFile = new File(codeSource.getLocation().toURI().getPath());
    return jarFile.getParentFile().getPath();
  }

  private static void outputDocumentSummary(AdvisorDocument advisorDocument, File summaryFile) {
    try (PrintWriter out = new PrintWriter(summaryFile, StandardCharsets.UTF_8.name())) {
      out.printf("Original name        : %s\n", advisorDocument.getOriginalName());
      out.printf("Max display width    : %d characters\n", advisorDocument.getMaxDisplayWidth());
      out.printf("Application prefix   : '%c' (%d)\n", advisorDocument.getApplicationPrefix(),
              (int) advisorDocument.getApplicationPrefix());
      out.printf("Topic count          : %d\n\n", advisorDocument.getTopics().size());
      out.printf("Global context count : %d\n", advisorDocument.getGlobalContextMap().size());

      out.printf("Global context references:\n\n");
      out.printf("localId  globalContextId\n");
      advisorDocument.getGlobalContextMap().forEach((globalContextId, topic) -> {
        out.printf("%8d %s\n", topic.getLocalContextId(), globalContextId);
      });
    } catch (FileNotFoundException | UnsupportedEncodingException e) {
      throw new RuntimeException("Failed to write document summary", e);
    }
  }

  static void outputTopicHtml(AdvisorDocument document, HelpTopic topic, File topicFile) throws URISyntaxException {
    try (PrintWriter out = new PrintWriter(topicFile, StandardCharsets.UTF_8.name())) {
      out.println("<!doctype html>");
      out.println("<html>");
      out.println("<head>");
      out.println("<meta charset='utf8'>");
      out.print("<title>");
      out.printf("Topic %d - %s", topic.getLocalContextId(), document.getOriginalName());
      out.println("</title>");
      out.println("</head>");
      out.println("<body><pre>");
      for (HelpTopicLine line : topic.getText()) {
        out.println(line.getHtmlFormattedText(document.getGlobalContextMap()));
      }
      out.println("</pre></body>");
      out.println("</html>");
    } catch (FileNotFoundException | UnsupportedEncodingException e) {
      throw new RuntimeException("Failed to write topic " + topic.getLocalContextId(), e);
    }
  }
}
