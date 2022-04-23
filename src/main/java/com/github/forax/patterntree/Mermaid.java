package com.github.forax.patterntree;

import com.github.forax.patterntree.PatternTrees.Node;

import java.util.HashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;

// Generates diagram using mermaid-js spec https://mermaid-js.github.io
//   Live editor: https://mermaid-js.github.io/mermaid-live-editor
public class Mermaid {
  public static String toMermaidJS(Node root) {
     var builder = new StringBuilder();
     builder.append("flowchart LR\n");
     toMermaidJS(root, builder, new Env());
     return builder.toString();
  }

  private static final class Env {
    private int id;
    private final HashMap<Node, Integer> idMap = new HashMap<>();

    public int id(Node node) {
      return idMap.computeIfAbsent(node, __ -> id++);
    }
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

  private static int distance(Node start, Node end) {
    if (start == end) {
      return 0;
    }
    for(var n: start.map.values()) {
      var d = distance(n, end);
      if (d != -1) {
        return 1 + d;
      }
    }
    var n = start.componentNode;
    if (n != null) {
      var d = distance(n, end);
      if (d != -1) {
        return 1 + d;
      }
    }
    return -1;
  }

  private static void toMermaidJS(Node node, StringBuilder builder, Env env) {
    var id = env.id(node);

    var style = Stream.of("")
        .<String>mapMulti((__, consumer) -> {
          //if (node.typeBinding || node.recordBinding) {
          //  consumer.accept("stroke-dasharray: 5 5");
          //}
          if (node.total) {
            consumer.accept("stroke-width: 4px");
          }
        })
        .collect(joining(","));
    if (!style.isEmpty()) {
      builder.append("""
            style id%d %s
          """.formatted(id, style));
    }

    var text = Stream.of("")
        .<String>mapMulti((__, consumer) -> {
          if (node.targetClass != null) {
            consumer.accept(simpleName(node.targetClass));
          }
          if (node.index != Node.UNINITIALIZED) {
            var bindings = node.bindingNodes.stream().map(n -> "" + distance(n, node)).collect(joining(","));
            consumer.accept(node.index + "(" + bindings + ')');
          }

        })
        .collect(joining(", "));

    builder.append("""
              id%d("%s")
            """.formatted(id, text));

    node.map.forEach((type, nextNode) -> {
      var nextId = env.id(nextNode);
      var label = simpleName(type);
      builder.append("""
            id%d-- %s --oid%d
          """.formatted(id, label, nextId));
    });

    if (node.componentNode != null) {
      var nextId = env.id(node.componentNode);
      var label = "(" + distance(node, node.componentNode.componentSource) + ")." + node.componentNode.component.getName();
      builder.append("""
            id%d-- \"%s\" -->id%d
          """.formatted(id, label, nextId));
    }

    node.map.values().forEach(n -> toMermaidJS(n, builder, env));
    if (node.componentNode != null) {
      toMermaidJS(node.componentNode, builder, env);
    }
  }
}
