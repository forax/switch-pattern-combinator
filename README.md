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
requireNonNull(r0);
if o instanceof Box r1 {
  Object r2 = box.v();
  if r2 instanceof A r3 {
    int r4 = r3.z();
    return call1(...)
  }
  if v instanceof B r3 {
    return call2(...);
  }
}
return call3();
```


## Case 2

```java
I i = ...
switch(i) {
  case A(int z) -> 1
  case B() -> 2
}
```

```         
(Main$I* i)
    -null-> -1
    -Main$I$A-> (int x())
        -int-> (double y())
            -double-> 1
    -Main$I$B-> 2
```

```java
requireNonNull(r0);
if v instanceof A r1 {
  int r2 = r1.x();
  int r3 = r1.y();
  return call1(...)
}
B r1 = (B) r0;        // catch(CCE) -> ICCE
return call_2(...);;
```




