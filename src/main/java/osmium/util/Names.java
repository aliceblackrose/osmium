package osmium.util;

import java.util.Locale;

public final class Names {
  private static final String DEFAULT_NAME = "unnamed";

  private Names() {}

  public static String stem(String name) {
    int separatorIndex = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
    String fileName = separatorIndex >= 0 ? name.substring(separatorIndex + 1) : name;

    int extensionIndex = fileName.lastIndexOf('.');
    if (extensionIndex > 0) {
      return fileName.substring(0, extensionIndex);
    }

    return fileName;
  }

  public static String key(String input) {
    if (input == null || input.isBlank()) {
      return DEFAULT_NAME;
    }

    String normalized = normalizeKey(input.toLowerCase(Locale.ROOT));
    String trimmed = trimKeyBoundaries(normalized);

    if (trimmed.isBlank()) {
      return DEFAULT_NAME;
    }

    return trimmed;
  }

  public static String namespace(String input) {
    return key(input).replace('/', '_');
  }

  private static String normalizeKey(String input) {
    StringBuilder normalized = new StringBuilder(input.length());
    boolean previousWasUnderscore = false;

    for (int index = 0; index < input.length(); index++) {
      char character = input.charAt(index);

      if (isAllowedKeyCharacter(character)) {
        if (character == '_' && previousWasUnderscore) {
          continue;
        }

        normalized.append(character);
        previousWasUnderscore = character == '_';
        continue;
      }

      if (!previousWasUnderscore) {
        normalized.append('_');
        previousWasUnderscore = true;
      }
    }

    return normalized.toString();
  }

  private static boolean isAllowedKeyCharacter(char character) {
    return (character >= 'a' && character <= 'z')
        || (character >= '0' && character <= '9')
        || character == '_'
        || character == '.'
        || character == '/'
        || character == '-';
  }

  private static String trimKeyBoundaries(String key) {
    int start = 0;
    int end = key.length();

    while (start < end && isTrimmedBoundaryCharacter(key.charAt(start))) {
      start++;
    }

    while (end > start && isTrimmedBoundaryCharacter(key.charAt(end - 1))) {
      end--;
    }

    return key.substring(start, end);
  }

  private static boolean isTrimmedBoundaryCharacter(char character) {
    return character == '_' || character == '.' || character == '/' || character == '-';
  }
}
