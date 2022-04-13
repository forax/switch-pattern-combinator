package com.github.forax.switchpatterncombinator;

import com.github.forax.switchpatterncombinator.Pattern.NullPattern;
import com.github.forax.switchpatterncombinator.Pattern.ParenthesizedPattern;
import com.github.forax.switchpatterncombinator.Pattern.RecordPattern;
import com.github.forax.switchpatterncombinator.Pattern.TypePattern;
import com.github.forax.switchpatterncombinator.SwitchItem.CasePattern;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

public class SwitchPatternCombinators {
  public static void switchPatterns(String name, Type targetType, List<SwitchItem> items) {
    items = new ArrayList<>(items);

    var root = new Node(erase(targetType), name);
    for(var item: items) {
      switch (item) {
        case CasePattern casePattern -> root.insert(casePattern.pattern(), null, null).setIndex(casePattern.index());
        default -> throw new AssertionError();
      }
    }

    // print the whole tree
    System.out.println(root);
  }

  private static Class<?> erase(Type type) {
    return switch (type) {
      case null -> null;
      case Class<?> clazz -> clazz;
      case ParameterizedType parameterizedType -> erase(parameterizedType.getRawType());
      default -> throw new AssertionError("unknown type " + type);
    };
  }

  private static final class Node {
    private final Class<?> targetClass;
    private final String op;
    private final LinkedHashMap<Class<?>, Node> map = new LinkedHashMap<>();
    private int index = -1;

    public Node(Class<?> targetClass, String op) {
      this.targetClass = targetClass;
      this.op = op;
    }

    public Node insert(Pattern pattern, Type nextTargetType, String nextOp) {
      requireNonNull(pattern);
      return switch (pattern) {
        case NullPattern nullPattern -> map.computeIfAbsent(null, __ -> new Node(erase(nextTargetType), nextOp));
        case ParenthesizedPattern parenthesizedPattern -> insert(parenthesizedPattern.pattern(), nextTargetType, nextOp);
        case TypePattern typePattern -> map.computeIfAbsent(erase(typePattern.type()), __ -> new Node(erase(nextTargetType), nextOp));
        case RecordPattern recordPattern -> {
          var recordClass = erase(recordPattern.type());
          if (!recordClass.isRecord()) {
            throw new IllegalStateException("not a record " + recordPattern.type());
          }
          var node = this;
          var recordComponents = recordClass.getRecordComponents();
          var firstParameterOp = recordComponents.length == 0? nextOp: recordComponents[0].getName()+"()";
          var firstParameterTargetType = recordComponents.length == 0? nextTargetType: recordComponents[0].getGenericType();
          node = map.computeIfAbsent(recordClass, __ -> new Node(erase(firstParameterTargetType), firstParameterOp));

          var parameterPatterns = recordPattern.patterns();
          for (int i = 0; i < recordComponents.length; i++) {
            var parameterPattern = parameterPatterns.get(i);
            var parameterOp = i == recordComponents.length - 1 ? nextOp : recordComponents[i + 1].getName()+"()";
            var parameterTargetType = i == recordComponents.length - 1 ? nextTargetType : recordComponents[i + 1].getGenericType();
            node = node.insert(parameterPattern, parameterTargetType, parameterOp);
          }
          yield node;
        }
      };
    }

    public void setIndex(int index) {
      if (this.index != -1) {
        throw new IllegalStateException("index set twice");
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
      builder.append(" (").append(targetClass.getName()).append(' ').append(op).append(")\n");
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
  }
}
