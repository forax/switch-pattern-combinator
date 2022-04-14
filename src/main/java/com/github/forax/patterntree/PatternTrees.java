package com.github.forax.patterntree;

import com.github.forax.patterntree.Pattern.NullPattern;
import com.github.forax.patterntree.Pattern.ParenthesizedPattern;
import com.github.forax.patterntree.Pattern.RecordPattern;
import com.github.forax.patterntree.Pattern.TypePattern;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

import static java.util.Objects.requireNonNull;

public class PatternTrees {
  public static Node createTree(Class<?> targetType, List<Case> items) {
    items = new ArrayList<>(items);

    var root = new Node(targetType, null, null, false);
    for(var item: items) {
      root.insert(item.pattern(), null, null, null).setIndex(item.index());
    }
    return root;
  }

  public static final class Node {
    private static final int UNINITIALIZED = -2;

    private final Class<?> targetClass;
    private final RecordComponent component;
    private final Node componentSource;
    private final boolean binding;
    private final LinkedHashMap<Class<?>, Node> map = new LinkedHashMap<>();
    private boolean sealed;
    private int index = UNINITIALIZED;

    private Node(Class<?> targetClass, RecordComponent component, Node componentSource, boolean binding) {
      this.targetClass = targetClass;
      this.component = component;
      this.componentSource = componentSource;
      this.binding = binding;
    }

    public Node insert(Pattern pattern, Class<?> nextTargetType, RecordComponent nextComponent, Node nextSource) {
      requireNonNull(pattern);
      return switch (pattern) {
        case NullPattern nullPattern -> map.computeIfAbsent(null, __ -> new Node(nextTargetType, nextComponent, nextSource, false));
        case ParenthesizedPattern parenthesizedPattern -> insert(parenthesizedPattern.pattern(), nextTargetType, nextComponent, nextSource);
        case TypePattern typePattern -> map.computeIfAbsent(typePattern.type(), __ -> new Node(nextTargetType, nextComponent, nextSource, true));
        case RecordPattern recordPattern -> {
          var recordClass = recordPattern.type();
          if (!recordClass.isRecord()) {
            throw new IllegalStateException("not a record " + recordPattern.type());
          }
          var node = this;
          var recordComponents = recordClass.getRecordComponents();
          var firstParameterOp = recordComponents.length == 0? nextComponent: recordComponents[0];
          var firstParameterTargetType = recordComponents.length == 0? nextTargetType: recordComponents[0].getType();
          var firstSource = recordComponents.length == 0? nextSource: this;
          node = map.computeIfAbsent(recordClass, __ -> new Node(firstParameterTargetType, firstParameterOp, firstSource, recordPattern.identifier().isPresent()));

          var parameterPatterns = recordPattern.patterns();
          for (int i = 0; i < recordComponents.length; i++) {
            var parameterPattern = parameterPatterns.get(i);
            var parameterOp = i == recordComponents.length - 1 ? nextComponent : recordComponents[i + 1];
            var parameterTargetType = i == recordComponents.length - 1 ? nextTargetType : recordComponents[i + 1].getType();
            var parameterSource = i == recordComponents.length - 1 ? nextSource: this;
            node = node.insert(parameterPattern, parameterTargetType, parameterOp, parameterSource);
          }
          yield node;
        }
      };
    }

    public void seal() {
      if (sealed) {
        throw new IllegalStateException("sealed already set");
      }
      sealed = true;
    }

    public void setIndex(int index) {
      if (this.index != UNINITIALIZED) {
        throw new IllegalStateException("index already set");
      }
      this.index = index;
    }

    private static String simpleName(Class<?> clazz) {
      if (clazz == null) { // null pattern
        return "null";
      }
      var name = clazz.getName();
      var index = name.lastIndexOf('.');
      return name.substring(index + 1);
    }

    private void toBuilder(StringBuilder builder, int depth) {
      if (binding) {
        builder.append(" $");
      }
      if (targetClass == null) {
        builder.append(" ").append(index).append('\n');
        return;
      }
      builder.append(" (").append(simpleName(targetClass))
          .append(sealed ? "*":"")
          .append(component == null? "": " " + component.getName() + "()")
          .append(")\n");
      map.forEach((aClass, node) -> {
        var newBuilder = new StringBuilder();
        newBuilder.append(" ".repeat(depth));
        newBuilder.append("-").append(simpleName(aClass)).append("->");
        node.toBuilder(newBuilder, depth + 4);
        builder.append(newBuilder);
      });
    }

    @Override
    public String toString() {
      var builder = new StringBuilder();
      toBuilder(builder, 4);
      return builder.toString();
    }

    public Node find(Class<?> ... types) {
      var node = this;
      for(var type: types) {
        node = node.map.get(type);
        if (node == null) {
          throw new IllegalArgumentException("no node at " + Arrays.toString(types));
        }
      }
      return node;
    }

    public String toCode() {
      var builder = new StringBuilder();
      toCode(builder, 0, 0, new Scope(), List.of());
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
        var result = map.put(node, varnum);
      }

      public int get(Node node) {
        return map.get(node);
      }
    }

    private void toCode(StringBuilder builder, int depth, int varnum, Scope scope, List<String> bindings) {
      if (binding) {
        bindings = append(bindings, r(varnum));
      }

      // call
      if (index != UNINITIALIZED) {
        builder.append("""
          call %d(%s);
          return;
          """.formatted(index, String.join(", ", bindings)).indent(depth));
        return;
      }

      if (component != null) {
        var input = scope.get(componentSource);
        builder.append("""
            %s %s = %s.%s();
            """.formatted(simpleName(component.getType()), r(varnum + 1), r(input), component.getName()).indent(depth));
        varnum++;
      }

      var transitions = new ArrayList<>(map.entrySet());
      for (int i = 0; i < transitions.size(); i++) {
        var entry = transitions.get(i);
        var type = entry.getKey();
        var nextNode = entry.getValue();
        if (type == null) {
          if (nextNode.index == -1) {
            builder.append("""
                requireNonNull(%s);
                """.formatted(r(varnum)).indent(depth));
          } else {
            builder.append("""
                if %s == null {
                """.formatted(r(varnum)).indent(depth));
            nextNode.toCode(builder, depth + 2, varnum, scope, bindings);
            builder.append("}\n".indent(depth));
          }
          continue;
        }
        var typename = simpleName(type);

        if (i == transitions.size() - 1) { // last node
          if (type == targetClass) {
            // do nothing
            scope.set(this, varnum);
            nextNode.toCode(builder, depth, varnum, scope, bindings);
            continue;
          }
          if (sealed) {
            if (!map.containsKey(null)) {  // null is in the remainder
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
            builder.append("""
                %s %s = (%s) %s;    // catch(CCE) -> ICCE
                """.formatted(typename, r(varnum + 1), typename, r(varnum)).indent(depth));
            scope.set(this, varnum + 1);
            nextNode.toCode(builder, depth, varnum + 1, scope, bindings);
            continue;
          }
        }
        builder.append("""
            if %s instanceof %s {
              %s %s = (%s) %s;
            """.formatted(r(varnum), typename, typename, r(varnum + 1), typename, r(varnum)).indent(depth));
        scope.set(this, varnum + 1);
        nextNode.toCode(builder, depth + 2, varnum + 1, scope, bindings);
        builder.append("}\n".indent(depth));
      }
    }
  }
}
