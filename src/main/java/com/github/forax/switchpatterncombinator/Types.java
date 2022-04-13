package com.github.forax.switchpatterncombinator;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.stream.IntStream;

public class Types {
  // placeholder for wildcard
  public static final Type W = new WildcardType() {
    @Override
    public Type[] getUpperBounds() {
      return new Type[0];
    }

    @Override
    public Type[] getLowerBounds() {
      return new Type[] { Object.class };
    }
  };

  public static Type type(Class<?> genericClass, Type... types) {
    var typeVariable = genericClass.getTypeParameters();
    if (typeVariable.length != types.length) {
      throw new IllegalArgumentException("wrong number of type arguments " + typeVariable.length + " " + types.length);
    }
    if (typeVariable.length == 0) {
      return genericClass;
    }
    return new ParameterizedType() {
      @Override
      public Type[] getActualTypeArguments() {
        return IntStream.range(0, types.length)
            .mapToObj(i -> {
              var type = types[i];
              if (type == W) {  // placeholder
                var variable = typeVariable[i];
                return new WildcardType() {
                  @Override
                  public Type[] getUpperBounds() {
                    return new Type[0];
                  }

                  @Override
                  public Type[] getLowerBounds() {
                    return variable.getBounds();
                  }
                };
              }
              return type;
            })
            .toArray(Type[]::new);
      }
      @Override
      public Type getRawType() {
        return genericClass;
      }
      @Override
      public Type getOwnerType() {
        return null;  // not supported for now
      }
    };
  }

  /*
  static Type wildCardify(Class<?> genericClass) {
    var typeVariable = genericClass.getTypeParameters();
    return new ParameterizedType() {
      @Override
      public Type[] getActualTypeArguments() {
        return Arrays.stream(typeVariable)
            .map(variable -> new WildcardType() {
              @Override
              public Type[] getUpperBounds() {
                return new Type[0];
              }

              @Override
              public Type[] getLowerBounds() {
                return variable.getBounds();
              }
            })
            .toArray(Type[]::new);
      }
      @Override
      public Type getRawType() {
        return genericClass;
      }
      @Override
      public Type getOwnerType() {
        return null;  // not supported for now
      }
    };
  }*/
}
