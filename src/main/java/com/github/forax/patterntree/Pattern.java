package com.github.forax.patterntree;

import java.util.List;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

// Kinds of Patterns (JLS 14.30.1)
//
// Pattern:
//    TypePattern
//    ParenthesizedPattern
//    RecordPattern
//TypePattern:
//    LocalVariableDeclaration
//
//ParenthesizedPattern:
//    ( Pattern )
//RecordPattern:
//    ReferenceType RecordStructurePattern [ Identifier ]
//RecordStructurePattern:
//    ( [ RecordComponentPatternList ] )
//RecordComponentPatternList :
//    Pattern { , Pattern }
public sealed interface Pattern {
  record NullPattern() implements Pattern {}

  record TypePattern(Class<?> type, String identifier) implements Pattern {
    public TypePattern {
      requireNonNull(type);
      requireNonNull(identifier);
    }
  }

  record ParenthesizedPattern(Pattern pattern) implements Pattern {
    public ParenthesizedPattern {
      requireNonNull(pattern);
    }
  }

  record RecordPattern(Class<?> type, List<Pattern> patterns, Optional<String> identifier) implements Pattern {
    public RecordPattern(Class<?> type, List<Pattern> patterns) {
      this(type, patterns, Optional.empty());
    }

    public RecordPattern {
      requireNonNull(type);
      patterns = List.copyOf(patterns);
      requireNonNull(identifier);
    }
  }
}
