package uk.me.philipsearle.advisor;

import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

public class CaseInsensitiveHashMap<V> {
  private final HashMap<String, V> wrapped = new HashMap<>();

  public V get(String key) {
    return wrapped.get(key.toLowerCase(Locale.UK));
  }

  public V put(String key, V value) {
    return wrapped.put(key.toLowerCase(Locale.UK), value);
  }

  void forEach(BiConsumer<String, ? super V> action) {
    Objects.requireNonNull(action);
    for (Map.Entry<String, V> entry : wrapped.entrySet()) {
      String k;
      V v;
      try {
        k = entry.getKey();
        v = entry.getValue();
      } catch (IllegalStateException ise) {
        // this usually means the entry is no longer in the map.
        throw new ConcurrentModificationException(ise);
      }
      action.accept(k, v);
    }
  }

  public int size() {
    return wrapped.size();
  }
}
