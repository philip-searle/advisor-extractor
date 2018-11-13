package uk.me.philipsearle.advisor;

import java.util.List;

public interface AdvisorDocument {

  char getApplicationPrefix();

  int getMaxDisplayWidth();

  String getOriginalName();

  List<HelpTopic> getTopics();

  CaseInsensitiveHashMap<HelpTopic> getGlobalContextMap();

  HelpTopic lookupGlobalContextId(String contextId);

  HelpTopic lookupLocalContextId(Integer contextId);

}
