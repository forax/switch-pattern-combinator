package com.github.forax.patterntree;

import com.github.forax.patterntree.Main.I.A;
import com.github.forax.patterntree.Main.I.B;

import com.github.forax.patterntree.Pattern.NullPattern;
import com.github.forax.patterntree.Pattern.RecordPattern;
import com.github.forax.patterntree.Pattern.TypePattern;
import com.github.forax.patterntree.SwitchItem.CasePattern;

import java.util.List;

public class Main {
  record Box(Object value) {}
  sealed interface I {
    record A(int x, double y) implements I {}
    record B() implements I {}
  }

  public static void main(String[] args) {
    // Object o = ...
    // switch(o) {
    //   case Box(A(int a, int b)) -> 1
    //   case Box(B()) -> 2
    //   default -> 3
    // }
    var root = PatternTrees.createTree(Object.class, List.of(
        new CasePattern(new NullPattern(), -1),
        new CasePattern(new RecordPattern(Box.class, List.of(new RecordPattern(A.class, List.of(new TypePattern(int.class, "a"), new TypePattern(double.class, "b"))))), 1),
        new CasePattern(new RecordPattern(Box.class, List.of(new RecordPattern(B.class, List.of()))), 2),
        new CasePattern(new TypePattern(Object.class, "_"), 3)
        )
    );
    System.out.println(root);
    System.out.println(root.toCode());

    // I i = ...
    // switch(i) {
    //   case A(int a, int b) -> 1
    //   case B() -> 2
    // }
    var root2 = PatternTrees.createTree(I.class, List.of(
            //new CasePattern(new NullPattern(), -1),
            new CasePattern(new RecordPattern(A.class, List.of(new TypePattern(int.class, "a"), new TypePattern(double.class, "b"))), 1),
            new CasePattern(new RecordPattern(B.class, List.of()), 2)
        )
    );
    root2.seal();
    System.out.println(root2);
    System.out.println(root2.toCode());
  }
}
