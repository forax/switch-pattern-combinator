package com.github.forax.patterntree;

import com.github.forax.patterntree.Pattern.NullPattern;
import com.github.forax.patterntree.Pattern.RecordPattern;
import com.github.forax.patterntree.Pattern.TypePattern;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class PatternTreesTest {

  @Nested
  class ShareALot {
    record Foo(Object o, Object o2) {}
    record Bar(int x) {}

    @Test
    public void createTree() {
      // Object o = ...
      // switch(o) {
      //   case Foo(Bar(int x), Integer i) -> 1
      //   case Foo(Bar(int y), Object o2) -> 2
      //   case Object o3 -> 3
      // }
      var root = PatternTrees.createTree(Object.class, List.of(
              new Case(new RecordPattern(Foo.class, List.of(new RecordPattern(Bar.class, List.of(new TypePattern(int.class, "x"))), new TypePattern(Integer.class, "i"))), 1),
              new Case(new RecordPattern(Foo.class, List.of(new RecordPattern(Bar.class, List.of(new TypePattern(int.class, "y"))), new TypePattern(Object.class, "o2"))), 2),
              new Case(new TypePattern(Object.class, "o3"), 3)
          )
      );
      assertEquals("""
        if r0 instanceof PatternTreesTest$ShareALot$Foo {
          PatternTreesTest$ShareALot$Foo r1 = (PatternTreesTest$ShareALot$Foo) r0;
          Object r2 = r1.o();
          if r2 instanceof PatternTreesTest$ShareALot$Bar {
            PatternTreesTest$ShareALot$Bar r3 = (PatternTreesTest$ShareALot$Bar) r2;
            int r4 = r3.x();
            Object r5 = r1.o2();
            if r5 instanceof Integer {
              Integer r6 = (Integer) r5;
              call 1(r4, r6);
              return;
            }
            call 2(r4, r5);
            return;
          }
        }
        call 3(r0);
        return;
        """, root.toCode());
    }
  }

  @Nested
  class SealedHierarchy {
    sealed interface I {
      record A(int x, double y) implements I {}
      record B() implements I {}
      final class C implements I {}
    }

    @Test
    public void createTree() {
      // I i = ...
      // switch(i) {
      //   case A(int a, double b) -> 1
      //   case B() -> 2
      //   case C c -> 3
      // }
      var root = PatternTrees.createTree(I.class, List.of(
              new Case(new RecordPattern(I.A.class, List.of(new TypePattern(int.class, "a"), new TypePattern(double.class, "b"))), 1),
              new Case(new RecordPattern(I.B.class, List.of()), 2),
              new Case(new TypePattern(I.C.class, "c"), 3)
          )
      );
      assertEquals("""
        if r0 instanceof PatternTreesTest$SealedHierarchy$I$A {
          PatternTreesTest$SealedHierarchy$I$A r1 = (PatternTreesTest$SealedHierarchy$I$A) r0;
          int r2 = r1.x();
          double r3 = r1.y();
          call 1(r2, r3);
          return;
        }
        if r0 instanceof PatternTreesTest$SealedHierarchy$I$B {
          PatternTreesTest$SealedHierarchy$I$B r1 = (PatternTreesTest$SealedHierarchy$I$B) r0;
          call 2();
          return;
        }
        requireNonNull(r0);  // null is a remainder
        PatternTreesTest$SealedHierarchy$I$C r1 = (PatternTreesTest$SealedHierarchy$I$C) r0;    // catch(CCE) -> ICCE
        call 3(r1);
        return;
        """, root.toCode());
    }
  }

  @Nested
  class SealedHierarchyRecordAtTheEnd {
    sealed interface I {
      record A(int x, double y) implements I {}
      record B() implements I {}
      final class C implements I {}
    }

    @Test
    public void createTree() {
      // I i = ...
      // switch(i) {
      //   case C c -> 1
      //   case B() -> 2
      //   case A(int a, double b)  -> 3
      // }
      var root = PatternTrees.createTree(I.class, List.of(
              new Case(new TypePattern(I.C.class, "c"), 1),
              new Case(new RecordPattern(I.B.class, List.of()), 2),
              new Case(new RecordPattern(I.A.class, List.of(new TypePattern(int.class, "a"), new TypePattern(double.class, "b"))), 3)
          )
      );
      assertEquals("""
        if r0 instanceof PatternTreesTest$SealedHierarchyRecordAtTheEnd$I$C {
          PatternTreesTest$SealedHierarchyRecordAtTheEnd$I$C r1 = (PatternTreesTest$SealedHierarchyRecordAtTheEnd$I$C) r0;
          call 1(r1);
          return;
        }
        if r0 instanceof PatternTreesTest$SealedHierarchyRecordAtTheEnd$I$B {
          PatternTreesTest$SealedHierarchyRecordAtTheEnd$I$B r1 = (PatternTreesTest$SealedHierarchyRecordAtTheEnd$I$B) r0;
          call 2();
          return;
        }
        // implicit null check of r0
        PatternTreesTest$SealedHierarchyRecordAtTheEnd$I$A r1 = (PatternTreesTest$SealedHierarchyRecordAtTheEnd$I$A) r0;    // catch(CCE) -> ICCE
        int r2 = r1.x();
        double r3 = r1.y();
        call 3(r2, r3);
        return;
        """, root.toCode());
    }
  }

  @Nested
  class SealedAllCombinations {
    sealed interface I {
      final class A implements I {}
      final class B implements I {}
    }

    record Foo(I i1, I i2) {}

    @Test
    public void createTree() {
      // Foo foo = ...
      // switch(foo) {
      //   case Foo(A a, A a2) -> 1
      //   case Foo(A a, B b) -> 2
      //   case Foo(B b, A a) -> 3
      //   case Foo(B b, B b2) -> 4
      // }
      var root = PatternTrees.createTree(Foo.class, List.of(
          new Case(new RecordPattern(Foo.class, List.of(new TypePattern(I.A.class, "a"), new TypePattern(I.A.class, "a2"))), 1),
          new Case(new RecordPattern(Foo.class, List.of(new TypePattern(I.A.class, "a"), new TypePattern(I.B.class, "b"))), 2),
          new Case(new RecordPattern(Foo.class, List.of(new TypePattern(I.B.class, "b"), new TypePattern(I.A.class, "a"))), 3),
          new Case(new RecordPattern(Foo.class, List.of(new TypePattern(I.B.class, "b"), new TypePattern(I.B.class, "b2"))), 4)
          )
      );

      assertEquals("""
        PatternTreesTest$SealedAllCombinations$I r1 = r0.i1();
        if r1 instanceof PatternTreesTest$SealedAllCombinations$I$A {
          PatternTreesTest$SealedAllCombinations$I$A r2 = (PatternTreesTest$SealedAllCombinations$I$A) r1;
          PatternTreesTest$SealedAllCombinations$I r3 = r0.i2();
          if r3 instanceof PatternTreesTest$SealedAllCombinations$I$A {
            PatternTreesTest$SealedAllCombinations$I$A r4 = (PatternTreesTest$SealedAllCombinations$I$A) r3;
            call 1(r2, r4);
            return;
          }
          requireNonNull(r3);  // null is a remainder
          PatternTreesTest$SealedAllCombinations$I$B r4 = (PatternTreesTest$SealedAllCombinations$I$B) r3;    // catch(CCE) -> ICCE
          call 2(r2, r4);
          return;
        }
        requireNonNull(r1);  // null is a remainder
        PatternTreesTest$SealedAllCombinations$I$B r2 = (PatternTreesTest$SealedAllCombinations$I$B) r1;    // catch(CCE) -> ICCE
        PatternTreesTest$SealedAllCombinations$I r3 = r0.i2();
        if r3 instanceof PatternTreesTest$SealedAllCombinations$I$A {
          PatternTreesTest$SealedAllCombinations$I$A r4 = (PatternTreesTest$SealedAllCombinations$I$A) r3;
          call 3(r2, r4);
          return;
        }
        requireNonNull(r3);  // null is a remainder
        PatternTreesTest$SealedAllCombinations$I$B r4 = (PatternTreesTest$SealedAllCombinations$I$B) r3;    // catch(CCE) -> ICCE
        call 4(r2, r4);
        return;
        """, root.toCode());
    }
  }

  @Nested
  class SealedRecordAllCombinations {
    sealed interface I {
      record A(int x) implements I {}
      record B(int y) implements I {}
    }

    record Foo(I i1, I i2) {}

    @Test
    public void createTree() {
      // Foo foo = ...
      // switch(foo) {
      //   case Foo(A(int x), A(int x2))) -> 1
      //   case Foo(A(int x), B(int y)) -> 2
      //   case Foo(B(int y), A(int x)) -> 3
      //   case Foo(B(int y), B(int y2)) -> 4
      // }
      var root = PatternTrees.createTree(Foo.class, List.of(
          new Case(new RecordPattern(Foo.class, List.of(new RecordPattern(I.A.class, List.of(new TypePattern(int.class, "x"))), new RecordPattern(I.A.class, List.of(new TypePattern(int.class, "x2"))))), 1),
          new Case(new RecordPattern(Foo.class, List.of(new RecordPattern(I.A.class, List.of(new TypePattern(int.class, "x"))), new RecordPattern(I.B.class, List.of(new TypePattern(int.class, "y"))))), 2),
          new Case(new RecordPattern(Foo.class, List.of(new RecordPattern(I.B.class, List.of(new TypePattern(int.class, "y"))), new RecordPattern(I.A.class, List.of(new TypePattern(int.class, "x"))))), 3),
          new Case(new RecordPattern(Foo.class, List.of(new RecordPattern(I.B.class, List.of(new TypePattern(int.class, "y"))), new RecordPattern(I.B.class, List.of(new TypePattern(int.class, "y2"))))), 4)
          )
      );

      System.out.println(Mermaid.toMermaidJS(root));

      assertEquals("""
        PatternTreesTest$SealedRecordAllCombinations$I r1 = r0.i1();
        if r1 instanceof PatternTreesTest$SealedRecordAllCombinations$I$A {
          PatternTreesTest$SealedRecordAllCombinations$I$A r2 = (PatternTreesTest$SealedRecordAllCombinations$I$A) r1;
          int r3 = r2.x();
          PatternTreesTest$SealedRecordAllCombinations$I r4 = r0.i2();
          if r4 instanceof PatternTreesTest$SealedRecordAllCombinations$I$A {
            PatternTreesTest$SealedRecordAllCombinations$I$A r5 = (PatternTreesTest$SealedRecordAllCombinations$I$A) r4;
            int r6 = r5.x();
            call 1(r3, r6);
            return;
          }
          // implicit null check of r4
          PatternTreesTest$SealedRecordAllCombinations$I$B r5 = (PatternTreesTest$SealedRecordAllCombinations$I$B) r4;    // catch(CCE) -> ICCE
          int r6 = r5.y();
          call 2(r3, r6);
          return;
        }
        // implicit null check of r1
        PatternTreesTest$SealedRecordAllCombinations$I$B r2 = (PatternTreesTest$SealedRecordAllCombinations$I$B) r1;    // catch(CCE) -> ICCE
        int r3 = r2.y();
        PatternTreesTest$SealedRecordAllCombinations$I r4 = r0.i2();
        if r4 instanceof PatternTreesTest$SealedRecordAllCombinations$I$A {
          PatternTreesTest$SealedRecordAllCombinations$I$A r5 = (PatternTreesTest$SealedRecordAllCombinations$I$A) r4;
          int r6 = r5.x();
          call 3(r3, r6);
          return;
        }
        // implicit null check of r4
        PatternTreesTest$SealedRecordAllCombinations$I$B r5 = (PatternTreesTest$SealedRecordAllCombinations$I$B) r4;    // catch(CCE) -> ICCE
        int r6 = r5.y();
        call 4(r3, r6);
        return;
        """, root.toCode());
    }
  }

  @Nested
  class CaseNull {
    @Test
    public void createTree() {
      // Object o = ...
      // switch(o) {
      //   case null -> 1
      //   case String s -> 2
      //   case Object o2 -> 3
      // }
      var root = PatternTrees.createTree(Object.class, List.of(
          new Case(new NullPattern(), 1),
          new Case(new TypePattern(String.class, "s"), 2),
          new Case(new TypePattern(Object.class, "o2"), 3)
          )
      );

      assertEquals("""
        if r0 == null {
          call 1();
          return;
        }
        if r0 instanceof String {
          String r1 = (String) r0;
          call 2(r1);
          return;
        }
        call 3(r0);
        return;
        """, root.toCode());
    }
  }

  @Nested
  class RecordWithNull {
    record Foo(Object o) {}

    @Test
    public void createTree() {
      // Object o = ...
      // switch(o) {
      //   case Foo(null) -> 1
      //   case Foo(String s) -> 2
      //   case Foo(Object o2) -> 3
      //   case Object o3 -> 4
      // }
      var root = PatternTrees.createTree(Object.class, List.of(
              new Case(new RecordPattern(Foo.class, List.of(new NullPattern())), 1),
              new Case(new RecordPattern(Foo.class, List.of(new TypePattern(String.class, "s"))), 2),
              new Case(new RecordPattern(Foo.class, List.of(new TypePattern(Object.class, "o2"))), 3),
              new Case(new TypePattern(Object.class, "o3"), 4)
          )
      );

      assertEquals("""
        if r0 instanceof PatternTreesTest$RecordWithNull$Foo {
          PatternTreesTest$RecordWithNull$Foo r1 = (PatternTreesTest$RecordWithNull$Foo) r0;
          Object r2 = r1.o();
          if r2 == null {
            call 1();
            return;
          }
          if r2 instanceof String {
            String r3 = (String) r2;
            call 2(r3);
            return;
          }
          call 3(r2);
          return;
        }
        call 4(r0);
        return;
        """, root.toCode());
    }
  }
}