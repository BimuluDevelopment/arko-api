package dk.arko.api.paper.command.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SubCommand {
    String name();
    String description() default "";
    boolean playerOnly() default false;
}
