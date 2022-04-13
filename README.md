# switch-pattern-combinator
A prototype showing how to generte the bytecode for a switch on patterns

```java
record Box<T>(Object v) {}
sealed interface I {
  record A(int x) implements I {}
  record B() implements I {}
}
```

## case 1

```java
Object o = ...
switch(o) {
  case Box<?>(A(int z)) -> 1
  case Box<?>(B()) -> 2
  default -> 3
}
```

```         
(java.lang.Object o)
    -null-> -1
    -Main$Box-> (java.lang.Object v())
        -Main$I$A-> (int x())
            -int-> (double y())
                -double-> 1
        -Main$I$B-> 2
    -Object-> 3
```

```java
requireNonNull(o);
if o instanceof Box box {
  Object _1 = box.v();
  if v instanceof A _2 {
    int _2 = _3.z();
    return call1(...)
  }
  if v instanceof B _2 {
    return call2(...);
  }
}
return call3();
```


## Case 2

```java
Box<I> box = ...
switch(box) {
  case Box<I>(A(int z)) -> 1
  case Box<I>(B()) -> 2
}
```

```         
-Box b-> (I b.v()) -A a-> (int a.z() *) -int-> 1
                   -B b-> -> 2
```

```java
requireNonNull(o);
if o instanceof Box box {
  I _1 = (I) box.v();     // CCE
  if v instanceof A _2 {
    int z = _2.z();
    return call1(...)
  }
  requireNonNull(v);    // remainder
  B _2 = (B) _1;        // catch(CCE) -> ICCE
  return call_2(...);
}
return call_3();
```




