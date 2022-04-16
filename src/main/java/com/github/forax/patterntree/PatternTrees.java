package com.github.forax.patterntree;

import com.github.forax.patterntree.Pattern.NullPattern;
import com.github.forax.patterntree.Pattern.ParenthesizedPattern;
import com.github.forax.patterntree.Pattern.RecordPattern;
import com.github.forax.patterntree.Pattern.TypePattern;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

import static java.util.Objects.requireNonNull;

public class PatternTrees {
  public static Node createTree(Class<?> targetType, List<Case> items) {
    items = new ArrayList<>(items);

    var root = new Node(targetType, null, null);
    for(var item: items) {
      root.insert(item.pattern()).setIndex(item.index());
    }
    return root;
  }

  public static final class Node {
    static final int UNINITIALIZED = Integer.MIN_VALUE;

    final Class<?> targetClass;
    final LinkedHashMap<Class<?>, Node> map = new LinkedHashMap<>();

    final RecordComponent component;
    final Node componentSource;
    Node componentNode;

    int index = UNINITIALIZED;
    boolean typeBinding;
    boolean recordBinding;
    boolean partial;

    @Override
    public String toString() {
      return "Node{" +
          "targetClass=" + targetClass +
          ", map=" + map +
          ", component=" + component +
          ", componentSource=" + componentSource +
          //", componentNode=" + componentNode +
          ", index=" + index +
          ", typeBinding=" + typeBinding +
          ", recordBinding=" + recordBinding +
          '}';
    }

    private Node(Class<?> targetClass, RecordComponent component, Node componentSource) {
      this.targetClass = targetClass;
      this.component = component;
      this.componentSource = componentSource;
    }

    public Node insert(Pattern pattern) {
      requireNonNull(pattern);
      return switch (pattern) {
        case NullPattern nullPattern -> map.computeIfAbsent(null, __ -> new Node(null, null, null));
        case ParenthesizedPattern parenthesizedPattern -> insert(parenthesizedPattern.pattern());
        case TypePattern typePattern -> map.computeIfAbsent(typePattern.type(), __ -> new Node(typePattern.type(), null, null)).setTypeBinding(true);
        case RecordPattern recordPattern -> {
          var type = recordPattern.type();
          var components = type.getRecordComponents();
          var patterns = recordPattern.patterns();

          var first = map.computeIfAbsent(type, __ -> new Node(type, null, null)).setRecordBinding(recordPattern.identifier().isPresent());
          var node = first;

          for (int i = 0; i < components.length; i++) {
            var component = components[i];
            var componentPattern = patterns.get(i);
            Node child;
            if (node.componentNode == null) {
              child = new Node(component.getType(), component, first);
              node.componentNode = child;
            } else {
              child = node.componentNode;
            }
            node = child.insert(componentPattern);
          }
          yield node;
        }
      };
    }

    public Node setTypeBinding(boolean typeBinding) {
      this.typeBinding |= typeBinding;
      return this;
    }

    public Node setRecordBinding(boolean recordBinding) {
      this.recordBinding |= recordBinding;
      return this;
    }

    public void setIndex(int index) {
      if (this.index != UNINITIALIZED) {
        throw new IllegalStateException("index already set");
      }
      this.index = index;
    }

    public void setPartial() {
      if (partial) {
        throw new IllegalStateException("partial already set");
      }
      if (targetClass == null || !targetClass.isSealed()) {
        throw new IllegalStateException(targetClass + " can not be partial");
      }
      partial = true;
    }

    public Node find(Object... transitions) {
      var node = this;
      for(var transition: transitions) {
        node = switch (transition) {
          case String s -> {
            var child = node.componentNode;
            if (child == null) {
              throw new IllegalArgumentException("null child for " + s);
            }
            if (!child.component.getName().equals(s)) {
              throw new IllegalArgumentException("bad name for component " + child.component.getName() + " " + s);
            }
            yield child;
          }
          case Class<?> type -> {
            var child = map.get(type);
            if (child == null) {
              throw new IllegalArgumentException("null child for type " + type.getName());
            }
            yield child;
          }
          default -> throw new IllegalArgumentException("bad argument " + transition);
        };
      }
      return node;
    }

    private static String simpleName(Class<?> clazz) {
      if (clazz == null) { // null pattern
        return "null";
      }
      var name = clazz.getName();
      var index = name.lastIndexOf('.');
      var index2 = name.lastIndexOf('$');
      return name.substring(Math.max(index, index2) + 1);
    }

    public String toCode() {
      var builder = new StringBuilder();
      toCode(builder, 0, 0, map.containsKey(null), new Scope(), List.of());
      return builder.toString();
    }

    private static String r(int varnum) {
      return "r" + varnum;
    }

    private static List<String> append(List<String> bindings, String name) {
      var list = new ArrayList<>(bindings);
      list.add(name);
      return List.copyOf(list);
    }

    private static class Scope {
      private final HashMap<Node, Integer> map = new HashMap<>();

      public void set(Node node, int varnum) {
        map.put(node, varnum);
      }

      public int get(Node node) {
        return map.get(node);
      }
    }

    private void toCode(StringBuilder builder, int depth, int varnum, boolean notNull, Scope scope, List<String> bindings) {
      if (typeBinding && (!recordBinding && index == UNINITIALIZED)) {
        bindings = append(bindings, r(varnum));
      }

      var unprotectedAccess = index != UNINITIALIZED && !notNull && componentNode != null && componentNode.componentSource == this;
      if (unprotectedAccess) {
        builder.append("""
            if %s != null {
            """.formatted(r(varnum)).indent(depth));
        notNull = true;
        depth += 2;
      }

      if (componentSource != null) {
        var input = scope.get(componentSource);
        builder.append("""
            %s %s = %s.%s();
            """.formatted(simpleName(component.getType()), r(varnum + 1), r(input), component.getName()).indent(depth));
        notNull = false;
        varnum++;
      }

      var transitions = new ArrayList<>(map.entrySet());
      for (int i = 0; i < transitions.size(); i++) {
        var entry = transitions.get(i);
        var type = entry.getKey();
        var nextNode = entry.getValue();
        if (type == null) {
          builder.append("""
              if %s == null {
              """.formatted(r(varnum)).indent(depth));
          nextNode.toCode(builder, depth + 2, varnum, false, scope, bindings);
          builder.append("}\n".indent(depth));
          continue;
        }

        var sealed = targetClass.isSealed();
        var typename = simpleName(type);
        if (i == transitions.size() - 1) { // last node
          if (type == targetClass) {
            // do nothing
            scope.set(this, varnum);
            nextNode.toCode(builder, depth, varnum, notNull || map.containsKey(null), scope, bindings);
            continue;
          }
          if (sealed) {
            if (!notNull && !map.containsKey(null)) {  // null is in the remainder
              if (partial) {
                builder.append("""
                      if %s != null {  // sealed and partial
                      """.formatted(r(varnum)).indent(depth));
                depth += 2;
              } else {
                if (type.isRecord() && type.getRecordComponents().length != 0) {
                  builder.append("""
                      // implicit null check of %s
                      """.formatted(r(varnum)).indent(depth));
                } else {
                  builder.append("""
                      requireNonNull(%s);  // null is a remainder
                      """.formatted(r(varnum)).indent(depth));
                }
              }
            }
            builder.append("""
                %s %s = (%s) %s;    // catch(CCE) -> ICCE
                """.formatted(typename, r(varnum + 1), typename, r(varnum)).indent(depth));
            scope.set(this, varnum + 1);
            nextNode.toCode(builder, depth, varnum + 1, true, scope, bindings);

            if (!notNull && !map.containsKey(null) && partial) {
              depth -= 2;
              builder.append("}\n".indent(depth));
            }

            continue;
          }
        }
        builder.append("""
            if %s instanceof %s {
              %s %s = (%s) %s;
            """.formatted(r(varnum), typename, typename, r(varnum + 1), typename, r(varnum)).indent(depth));
        scope.set(this, varnum + 1);
        nextNode.toCode(builder, depth + 2, varnum + 1, true, scope, bindings);
        builder.append("}\n".indent(depth));
      }

      if (componentNode != null) {
        scope.set(this, varnum);
        componentNode.toCode(builder, depth, varnum, notNull || map.containsKey(null), scope, bindings);
      }

      if (unprotectedAccess) {
        depth -= 2;
        builder.append("}\n".indent(depth));
      }
      if (index != UNINITIALIZED) {
        if (typeBinding && !recordBinding) {
          bindings = append(bindings, r(varnum));
        }
        builder.append("""
          return call %d(%s);
          """.formatted(index, String.join(", ", bindings)).indent(depth));
      }
    }
  }
}
