package dk.arko.api.paper.command.annotation;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Command {
    String name();
    String description() default "";
    String[] aliases() default {};
    String permission() default "";
    boolean playerOnly() default false;
}
