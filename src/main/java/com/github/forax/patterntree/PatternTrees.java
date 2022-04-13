package com.github.forax.patterntree;

import com.github.forax.patterntree.Pattern.NullPattern;
import com.github.forax.patterntree.Pattern.ParenthesizedPattern;
import com.github.forax.patterntree.Pattern.RecordPattern;
import com.github.forax.patterntree.Pattern.TypePattern;
import com.github.forax.patterntree.SwitchItem.CasePattern;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

import static java.util.Objects.requireNonNull;

public class PatternTrees {
  public static Node createTree(String name, Class<?> targetType, List<SwitchItem> items) {
    items = new ArrayList<>(items);

    var root = new Node(targetType, name);
    for(var item: items) {
      switch (item) {
        case CasePattern casePattern -> root.insert(casePattern.pattern(), null, null).setIndex(casePattern.index());
        default -> throw new AssertionError();
      }
    }
    return root;
  }

  public static final class Node {
    private final Class<?> targetClass;
    private final String op;
    private final LinkedHashMap<Class<?>, Node> map = new LinkedHashMap<>();
    private boolean exhaustive;
    private int index = -1;

    private Node(Class<?> targetClass, String op) {
      this.targetClass = targetClass;
      this.op = op;
    }

    public Node insert(Pattern pattern, Class<?> nextTargetType, String nextOp) {
      requireNonNull(pattern);
      return switch (pattern) {
        case NullPattern nullPattern -> map.computeIfAbsent(null, __ -> new Node(nextTargetType, nextOp));
        case ParenthesizedPattern parenthesizedPattern -> insert(parenthesizedPattern.pattern(), nextTargetType, nextOp);
        case TypePattern typePattern -> map.computeIfAbsent(typePattern.type(), __ -> new Node(nextTargetType, nextOp));
        case RecordPattern recordPattern -> {
          var recordClass = recordPattern.type();
          if (!recordClass.isRecord()) {
            throw new IllegalStateException("not a record " + recordPattern.type());
          }
          var node = this;
          var recordComponents = recordClass.getRecordComponents();
          var firstParameterOp = recordComponents.length == 0? nextOp: recordComponents[0].getName()+"()";
          var firstParameterTargetType = recordComponents.length == 0? nextTargetType: recordComponents[0].getType();
          node = map.computeIfAbsent(recordClass, __ -> new Node(firstParameterTargetType, firstParameterOp));

          var parameterPatterns = recordPattern.patterns();
          for (int i = 0; i < recordComponents.length; i++) {
            var parameterPattern = parameterPatterns.get(i);
            var parameterOp = i == recordComponents.length - 1 ? nextOp : recordComponents[i + 1].getName()+"()";
            var parameterTargetType = i == recordComponents.length - 1 ? nextTargetType : recordComponents[i + 1].getType();
            node = node.insert(parameterPattern, parameterTargetType, parameterOp);
          }
          yield node;
        }
      };
    }

    public void setExhaustive() {
      if (exhaustive) {
        throw new IllegalStateException("exhaustive already set");
      }
      exhaustive = true;
    }

    public void setIndex(int index) {
      if (this.index != -1) {
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
      if (targetClass == null) {
        builder.append(" ").append(index).append('\n');
        return;
      }
      builder.append(" (").append(simpleName(targetClass))
          .append(exhaustive? "*":"")
          .append(' ').append(op).append(")\n");
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
  }
}
