package com.github.forax.patterntree;

import com.github.forax.patterntree.Pattern.ParenthesizedPattern;
import com.github.forax.patterntree.Pattern.RecordPattern;
import com.github.forax.patterntree.Pattern.TypePattern;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

public class PatternTrees {
  public static Node createTree(Class<?> targetType, List<Case> items) {
    items = new ArrayList<>(items);

    var root = new Node(targetType, null, null);
    for(var item: items) {
      var bindingNodes = new ArrayList<Node>();
      root.insert(item.pattern(), bindingNodes).setIndex(item.index(), bindingNodes);
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
    List<Node> bindingNodes;
    boolean total;
    boolean disallowNull;


    @Override
    public String toString() {
      return "Node{" +
          "targetClass=" + targetClass +
          ", map=" + map +
          ", component=" + component +
          //", componentSource=" + componentSource +
          ", componentNode=" + componentNode +
          ", index=" + index +
          //", bindingNodes=" + bindingNodes +
          ", total=" + total +
          ", disallowNull=" + disallowNull +
          '}';
    }

    private Node(Class<?> targetClass, RecordComponent component, Node componentSource) {
      this.targetClass = targetClass;
      this.component = component;
      this.componentSource = componentSource;
    }

    public Node insert(Pattern pattern, List<Node> bindingNodes) {
      requireNonNull(pattern);
      return switch (pattern) {
        case ParenthesizedPattern parenthesizedPattern -> insert(parenthesizedPattern.pattern(), bindingNodes);
        case TypePattern typePattern -> {
          var targetType = typePattern.type();
          var node = map.get(targetType);
          var type = (node != null && node.disallowNull)? null: targetType;
          yield map.computeIfAbsent(type, __ -> new Node(targetType, null, null))
              .addToBindingNodes(bindingNodes, !typePattern.identifier().equals("_"));
        }
        case RecordPattern recordPattern -> {
          var type = recordPattern.type();
          var components = type.getRecordComponents();
          var patterns = recordPattern.patterns();

          var first = map.computeIfAbsent(type, __ -> new Node(type, null, null));
          first.disallowNull = true;
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
            node = child.insert(componentPattern, bindingNodes);
          }

          first.addToBindingNodes(bindingNodes, recordPattern.identifier().filter(id -> !id.equals("_")).isPresent());
          yield node;
        }
      };
    }

    public Node addToBindingNodes(List<Node> bindingNodes, boolean isABinding) {
      if (isABinding) {
        bindingNodes.add(this);
      }
      return this;
    }

    public void setIndex(int index, List<Node> bindingNodes) {
      if (this.index != UNINITIALIZED) {
        throw new IllegalStateException("index already set");
      }
      this.index = index;
      this.bindingNodes = List.copyOf(bindingNodes);
    }

    public void setTotal() {
      if (total) {
        throw new IllegalStateException("total already set");
      }
      if (targetClass == null || !targetClass.isSealed()) {
        throw new IllegalStateException(targetClass + " can not be total");
      }
      total = true;
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
              throw new IllegalArgumentException("bad name for component '" + child.component.getName() + "' " + s);
            }
            yield child;
          }
          case null, Class<?> type -> {
            var child = node.map.get(type);
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
      if (clazz == null) {
        return "null";
      }
      var name = clazz.getName();
      var index = name.lastIndexOf('.');
      var index2 = name.lastIndexOf('$');
      return name.substring(Math.max(index, index2) + 1);
    }

    public String toCode() {
      var builder = new StringBuilder();
      toCode(builder, 0, 0, false, new Scope());
      return builder.toString();
    }

    private static String r(int varnum) {
      return "r" + varnum;
    }

    private static class Scope {
      private final HashMap<Node, Integer> map = new HashMap<>();

      public void set(Node node, int varnum) {
        map.put(node, varnum);
      }

      public int get(Node node) {
        var varnum = map.get(node);
        if (varnum == null) {
          throw new IllegalStateException("no varnum for node " + node);
        }
        return varnum;
      }
    }

    private void toCode(StringBuilder builder, int depth, int varnum, boolean notNull, Scope scope) {
      if (index != UNINITIALIZED) {
        scope.set(this, varnum);

        var bindingText = bindingNodes.stream().map(node -> r(scope.get(node))).collect(Collectors.joining(", "));
        builder.append("""
          return call %d(%s);
          """.formatted(index, bindingText).indent(depth));
        return;
      }

      if (componentSource != null) {
        var input = scope.get(componentSource);
        builder.append("""
            %s %s = %s.%s();
            """.formatted(simpleName(component.getType()), r(varnum + 1), r(input), component.getName()).indent(depth));
        notNull = false;
        varnum++;
      }

      var iterator = map.entrySet().iterator();
      while (iterator.hasNext()) {
        var entry = iterator.next();
        var type = entry.getKey();
        var nextNode = entry.getValue();

        var typename = simpleName(type);
        if (!iterator.hasNext()) { // last node
          if (type == targetClass || type == null) {
            // do nothing
            scope.set(this, varnum);
            nextNode.toCode(builder, depth, varnum, notNull, scope);
            continue;
          }
          if (total) {    // sealed and total
            if (!notNull) {  // null is in the remainder
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
            nextNode.toCode(builder, depth, varnum + 1, true, scope);
            continue;
          }
        }
        if (type == null) {
          var targetClassName = simpleName(nextNode.targetClass);
          builder.append("""
                if %s == null {
                  %s %s = (%s) %s;
                """.formatted(r(varnum), targetClassName, r(varnum + 1), targetClassName, r(varnum)).indent(depth));
          scope.set(this, varnum + 1);
          nextNode.toCode(builder, depth + 2, varnum + 1, false, scope);
          builder.append("}\n".indent(depth));
          continue;
        }
        if (type == targetClass) {
          builder.append("""
                if %s != null {
                """.formatted(r(varnum)));
          scope.set(this, varnum);
          nextNode.toCode(builder, depth + 2, varnum, true, scope);
          builder.append("}\n".indent(depth));
          continue;
        }
        builder.append("""
            if %s instanceof %s {
              %s %s = (%s) %s;
            """.formatted(r(varnum), typename, typename, r(varnum + 1), typename, r(varnum)).indent(depth));
        scope.set(this, varnum + 1);
        nextNode.toCode(builder, depth + 2, varnum + 1, true, scope);
        builder.append("}\n".indent(depth));
      }

      if (componentNode != null) {
        scope.set(this, varnum);
        componentNode.toCode(builder, depth, varnum, notNull, scope);
      }
    }
  }
}
