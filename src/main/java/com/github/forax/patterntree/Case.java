package com.github.forax.patterntree;

import static java.util.Objects.requireNonNull;

public record Case(Pattern pattern, int index) {
    public Case {
      requireNonNull(pattern);
    }
}