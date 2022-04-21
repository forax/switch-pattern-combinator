package com.github.forax.patterntree;

import com.github.forax.patterntree.Pattern.RecordPattern;
import com.github.forax.patterntree.Pattern.TypePattern;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class PatternTreesTest {

  @Nested
  class Simple {
    record Foo(int x) {}

    @Test
    public void createTree() {
      // Object o = ...
      // switch(o) {
      //   case Foo(int x) -> 1
      //   case Object o3 -> 2
      // }
      var root = PatternTrees.createTree(Object.class, List.of(
              new Case(new RecordPattern(Foo.class, new TypePattern(int.class, "x")), 1),
              new Case(new TypePattern(Object.class, "o3"), 2)
          )
      );

      System.out.println(Mermaid.toMermaidJS(root));

      assertEquals("""
        if r0 instanceof Foo {
          Foo r1 = (Foo) r0;
          int r2 = r1.x();
          return call 1(r2);
        }
        return call 2(r0);
        """, root.toCode());
    }
  }

  @Nested
  class Example {
    record Foo(I i, I i2) {}
    sealed interface I {
      record A(int x) implements I {}
      record B(String s) implements I {}
    }

    @Test
    public void createTree() {
      // Foo foo = ...
      // switch(foo) {
      //   case Foo(A a, A(int x)) -> 1
      //   case Foo(A a, A a2) -> 2
      //   case Foo(A a, B(String s)) -> 3
      //   case Foo(B b, I i) -> 4
      // }
      var root = PatternTrees.createTree(Foo.class, List.of(
              new Case(new RecordPattern(Foo.class, new TypePattern(I.A.class, "a"), new RecordPattern(I.A.class, new TypePattern(int.class, "x"))), 1),
              new Case(new RecordPattern(Foo.class, new TypePattern(I.A.class, "a"), new TypePattern(I.A.class, "a2")), 2),
              new Case(new RecordPattern(Foo.class, new TypePattern(I.A.class, "a"), new RecordPattern(I.B.class, new TypePattern(String.class, "s"))), 3),
              new Case(new RecordPattern(Foo.class, new TypePattern(I.B.class, "b"), new TypePattern(I.class, "i")), 4)
          )
      );
      root.find(Foo.class, "i").setTotal();
      root.find(Foo.class, "i", I.A.class, "i2").setTotal();

      System.out.println(Mermaid.toMermaidJS(root));

      assertEquals("""
          I r1 = r0.i();
          if r1 instanceof A {
            A r2 = (A) r1;
            I r3 = r0.i2();
            if r3 instanceof A {
              A r4 = (A) r3;
              int r5 = r4.x();
              return call 1(r2, r5);
            }
            if r3 == null {
              A r4 = (A) r3;
              return call 2(r2, r4);
            }
            // implicit null check of r3
            B r4 = (B) r3;    // catch(CCE) -> ICCE
            String r5 = r4.s();
            return call 3(r2, r5);
          }
          // implicit null check of r1
          B r2 = (B) r1;    // catch(CCE) -> ICCE
          I r3 = r0.i2();
          return call 4(r2, r3);
          """, root.toCode());
    }
  }

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
              new Case(new RecordPattern(Foo.class, new RecordPattern(Bar.class, List.of(new TypePattern(int.class, "x"))), new TypePattern(Integer.class, "i")), 1),
              new Case(new RecordPattern(Foo.class, new RecordPattern(Bar.class, List.of(new TypePattern(int.class, "y"))), new TypePattern(Object.class, "o2")), 2),
              new Case(new TypePattern(Object.class, "o3"), 3)
          )
      );

      assertEquals("""
        if r0 instanceof Foo {
          Foo r1 = (Foo) r0;
          Object r2 = r1.o();
          if r2 instanceof Bar {
            Bar r3 = (Bar) r2;
            int r4 = r3.x();
            Object r5 = r1.o2();
            if r5 instanceof Integer {
              Integer r6 = (Integer) r5;
              return call 1(r4, r6);
            }
            return call 2(r4, r5);
          }
        }
        return call 3(r0);
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
              new Case(new RecordPattern(I.A.class, new TypePattern(int.class, "a"), new TypePattern(double.class, "b")), 1),
              new Case(new RecordPattern(I.B.class), 2),
              new Case(new TypePattern(I.C.class, "c"), 3)
          )
      );
      root.setTotal();

      System.out.println(Mermaid.toMermaidJS(root));

      assertEquals("""
        if r0 instanceof A {
          A r1 = (A) r0;
          int r2 = r1.x();
          double r3 = r1.y();
          return call 1(r2, r3);
        }
        if r0 instanceof B {
          B r1 = (B) r0;
          return call 2();
        }
        requireNonNull(r0);  // null is a remainder
        C r1 = (C) r0;    // catch(CCE) -> ICCE
        return call 3(r1);
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
              new Case(new RecordPattern(I.B.class), 2),
              new Case(new RecordPattern(I.A.class, new TypePattern(int.class, "a"), new TypePattern(double.class, "b")), 3)
          )
      );
      root.setTotal();

      System.out.println(Mermaid.toMermaidJS(root));

      assertEquals("""
        if r0 instanceof C {
          C r1 = (C) r0;
          return call 1(r1);
        }
        if r0 instanceof B {
          B r1 = (B) r0;
          return call 2();
        }
        // implicit null check of r0
        A r1 = (A) r0;    // catch(CCE) -> ICCE
        int r2 = r1.x();
        double r3 = r1.y();
        return call 3(r2, r3);
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
          new Case(new RecordPattern(Foo.class, new TypePattern(I.A.class, "a"), new TypePattern(I.A.class, "a2")), 1),
          new Case(new RecordPattern(Foo.class, new TypePattern(I.A.class, "a"), new TypePattern(I.B.class, "b")), 2),
          new Case(new RecordPattern(Foo.class, new TypePattern(I.B.class, "b"), new TypePattern(I.A.class, "a")), 3),
          new Case(new RecordPattern(Foo.class, new TypePattern(I.B.class, "b"), new TypePattern(I.B.class, "b2")), 4)
          )
      );
      root.find(Foo.class, "i1").setTotal();
      root.find(Foo.class, "i1", I.A.class, "i2").setTotal();
      root.find(Foo.class, "i1", I.B.class, "i2").setTotal();

      System.out.println(root);

      System.out.println(Mermaid.toMermaidJS(root));

      assertEquals("""
        I r1 = r0.i1();
        if r1 instanceof A {
          A r2 = (A) r1;
          I r3 = r0.i2();
          if r3 instanceof A {
            A r4 = (A) r3;
            return call 1(r2, r4);
          }
          requireNonNull(r3);  // null is a remainder
          B r4 = (B) r3;    // catch(CCE) -> ICCE
          return call 2(r2, r4);
        }
        requireNonNull(r1);  // null is a remainder
        B r2 = (B) r1;    // catch(CCE) -> ICCE
        I r3 = r0.i2();
        if r3 instanceof A {
          A r4 = (A) r3;
          return call 3(r2, r4);
        }
        requireNonNull(r3);  // null is a remainder
        B r4 = (B) r3;    // catch(CCE) -> ICCE
        return call 4(r2, r4);
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
          new Case(new RecordPattern(Foo.class, new RecordPattern(I.A.class, new TypePattern(int.class, "x")), new RecordPattern(I.A.class, new TypePattern(int.class, "x2"))), 1),
          new Case(new RecordPattern(Foo.class, new RecordPattern(I.A.class, new TypePattern(int.class, "x")), new RecordPattern(I.B.class, new TypePattern(int.class, "y"))), 2),
          new Case(new RecordPattern(Foo.class, new RecordPattern(I.B.class, new TypePattern(int.class, "y")), new RecordPattern(I.A.class, new TypePattern(int.class, "x"))), 3),
          new Case(new RecordPattern(Foo.class, new RecordPattern(I.B.class, new TypePattern(int.class, "y")), new RecordPattern(I.B.class, new TypePattern(int.class, "y2"))), 4)
          )
      );
      root.find(Foo.class, "i1").setTotal();
      root.find(Foo.class, "i1", I.A.class, "x", int.class, "i2").setTotal();
      root.find(Foo.class, "i1", I.B.class, "y", int.class, "i2").setTotal();

      System.out.println(Mermaid.toMermaidJS(root));

      assertEquals("""
        I r1 = r0.i1();
        if r1 instanceof A {
          A r2 = (A) r1;
          int r3 = r2.x();
          I r4 = r0.i2();
          if r4 instanceof A {
            A r5 = (A) r4;
            int r6 = r5.x();
            return call 1(r3, r6);
          }
          // implicit null check of r4
          B r5 = (B) r4;    // catch(CCE) -> ICCE
          int r6 = r5.y();
          return call 2(r3, r6);
        }
        // implicit null check of r1
        B r2 = (B) r1;    // catch(CCE) -> ICCE
        int r3 = r2.y();
        I r4 = r0.i2();
        if r4 instanceof A {
          A r5 = (A) r4;
          int r6 = r5.x();
          return call 3(r3, r6);
        }
        // implicit null check of r4
        B r5 = (B) r4;    // catch(CCE) -> ICCE
        int r6 = r5.y();
        return call 4(r3, r6);
        """, root.toCode());
    }
  }

  @Nested
  class ShareTypePatternAndRecordPatternOnTheSameNode {
    record Foo(Object o) {}

    @Test
    public void createTree() {
      // Foo foo = ...
      // switch(foo) {
      //   case Foo(String s) -> 1
      //   case Foo foo2 -> 2
      // }
      var root = PatternTrees.createTree(Foo.class, List.of(
              new Case(new RecordPattern(Foo.class, new TypePattern(String.class, "s")), 1),
              new Case(new TypePattern(Foo.class, "foo2"), 2)
          )
      );

      System.out.println(Mermaid.toMermaidJS(root));

      assertEquals("""
        if r0 != null {
          Object r1 = r0.o();
          if r1 instanceof String {
            String r2 = (String) r1;
            return call 1(r2);
          }
        }
        return call 2(r0);
        """, root.toCode());
    }
  }

  @Nested
  class ShareTypePatternAndRecordPatternOnTheSameNode2 {
    record Foo(Object o) {}

    @Test
    public void createTree() {
      // Object o = ...
      // switch(o) {
      //   case Foo(String s) -> 1
      //   case Foo foo -> 2
      //   case Object o2 -> 3
      // }
      var root = PatternTrees.createTree(Object.class, List.of(
              new Case(new RecordPattern(Foo.class, new TypePattern(String.class, "s")), 1),
              new Case(new TypePattern(Foo.class, "foo"), 2),
              new Case(new TypePattern(Object.class, "o2"), 3)
          )
      );

      System.out.println(Mermaid.toMermaidJS(root));

      assertEquals("""
        if r0 instanceof Foo {
          Foo r1 = (Foo) r0;
          Object r2 = r1.o();
          if r2 instanceof String {
            String r3 = (String) r2;
            return call 1(r3);
          }
        }
        if r0 == null {
          Foo r1 = (Foo) r0;
          return call 2(r1);
        }
        return call 3(r0);
        """, root.toCode());
    }
  }

  @Nested
  class SealedAndNull {
    record Foo(I i) {}
    sealed interface I {
      final class A implements I {}
    }

    @Test
    public void createTree() {
      // Object o = ...
      // switch(o) {
      //   case Foo(A a) -> 2
      //   case Object o2 -> 3
      // }
      var root = PatternTrees.createTree(Object.class, List.of(
              new Case(new RecordPattern(Foo.class, new TypePattern(I.A.class, "a")), 1),
              new Case(new TypePattern(Object.class, "o2"), 2)
          )
      );

      System.out.println(Mermaid.toMermaidJS(root));

      assertEquals("""
        if r0 instanceof Foo {
          Foo r1 = (Foo) r0;
          I r2 = r1.i();
          if r2 instanceof A {
            A r3 = (A) r2;
            return call 1(r3);
          }
        }
        return call 2(r0);
        """, root.toCode());
    }
  }
}