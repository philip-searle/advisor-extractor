package uk.me.philipsearle.advisor;

import java.util.List;

public class AdvisorDocumentImpl implements AdvisorDocument {

  /** The character with which inline application-specific commands are prefixed. */
  private final char applicationPrefix;

  /** The maximum length of all lines in this document in characters. */
  private final int maxDisplayWidth;

  /** The 8.3 original filename of this document. */
  private final String originalName;

  /** The decopressed help topics. Each topic's index is also it's local context ID for cross-references. */
  private final List<HelpTopic> topics;

  /** Maps global context IDs to help topic (potentially many per topic). */
  private final CaseInsensitiveHashMap<HelpTopic> globalContextMap;

  AdvisorDocumentImpl(char applicationPrefix, int maxDisplayWidth, String originalName, List<HelpTopic> topics,
          CaseInsensitiveHashMap<HelpTopic> globalContextMap) {
    this.applicationPrefix = applicationPrefix;
    this.maxDisplayWidth = maxDisplayWidth;
    this.originalName = originalName;
    this.topics = topics;
    this.globalContextMap = globalContextMap;
  }

  @Override
  public char getApplicationPrefix() {
    return applicationPrefix;
  }

  @Override
  public int getMaxDisplayWidth() {
    return maxDisplayWidth;
  }

  @Override
  public String getOriginalName() {
    return originalName;
  }

  @Override
  public List<HelpTopic> getTopics() {
    return topics;
  }

  @Override
  public CaseInsensitiveHashMap<HelpTopic> getGlobalContextMap() {
    return globalContextMap;
  }

  @Override
  public HelpTopic lookupGlobalContextId(String contextId) {
    return globalContextMap.get(contextId);
  }

  @Override
  public HelpTopic lookupLocalContextId(Integer contextId) {
    return topics.get(contextId);
  }
}


class HelpTopic {
  private final long localContextId;
  private final List<HelpTopicLine> text;

  HelpTopic(long localContextId, List<HelpTopicLine> text) {
    this.localContextId = localContextId;
    this.text = text;
  }

  public long getLocalContextId() {
    return localContextId;
  }

  public List<HelpTopicLine> getText() {
    return text;
  }
}
