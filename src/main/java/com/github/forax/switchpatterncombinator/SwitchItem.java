package com.github.forax.switchpatterncombinator;

import static java.util.Objects.requireNonNull;

public sealed interface SwitchItem {
  record CasePattern(Pattern pattern, int index) implements SwitchItem{
    public CasePattern {
      requireNonNull(pattern);
    }
  }

  //record CaseValue(Object value, int index) implements SwitchItem {}

  //record Default(int index) implements SwitchItem { }
}
