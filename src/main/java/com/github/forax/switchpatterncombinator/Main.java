package com.github.forax.switchpatterncombinator;

import com.github.forax.switchpatterncombinator.Main.I.A;
import com.github.forax.switchpatterncombinator.Main.I.B;

import com.github.forax.switchpatterncombinator.Pattern.NullPattern;
import com.github.forax.switchpatterncombinator.Pattern.RecordPattern;
import com.github.forax.switchpatterncombinator.Pattern.TypePattern;
import com.github.forax.switchpatterncombinator.SwitchItem.CasePattern;

import java.util.List;

import static com.github.forax.switchpatterncombinator.Types.*;

public class Main {
  record Box<T>(Object v) {}
  sealed interface I {
    record A(int x, double y) implements I {}
    record B() implements I {}
  }

  public static void main(String[] args) {
    // Object o = ...
    // switch(o) {
    //   case Box<?>(A(int a, int b)) -> 1
    //   case Box<?>(B()) -> 2
    //   default -> 3
    // }
    SwitchPatternCombinators.switchPatterns("o", Object.class, List.of(
        new CasePattern(new NullPattern(), -1),
        new CasePattern(new RecordPattern(type(Box.class, W), List.of(new RecordPattern(A.class, List.of(new TypePattern(int.class, "a"), new TypePattern(double.class, "b"))))), 1),
        new CasePattern(new RecordPattern(type(Box.class, W), List.of(new RecordPattern(B.class, List.of()))), 2),
        new CasePattern(new TypePattern(Object.class, "_"), 3)
        )
    );
  }
}
