package uk.me.philipsearle.advisor;

import java.io.IOException;

public class BadAdvisorFileException extends IOException {
  private static final long serialVersionUID = 431322795100849596L;

  public BadAdvisorFileException(String message) {
    super(message);
  }
}
