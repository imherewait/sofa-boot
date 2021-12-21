package com.alipay.sofa.runtime.api.annotation;

import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;

/**
 * Enables Sofa components as Spring Beans
 *
 * @author renliangyu 2021/12/21
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
@SofaComponentScan
public @interface EnableSofa {
    /**
     * Base packages to scan for annotated @SofaService classes.
     * <p>
     * Use {@link #scanBasePackageClasses()} for a type-safe alternative to String-based
     * package names.
     *
     * @return the base packages to scan
     * @see SofaComponentScan#basePackages()
     */
    @AliasFor(annotation = SofaComponentScan.class, attribute = "basePackages")
    String[] scanBasePackages() default {};

    /**
     * Type-safe alternative to {@link #scanBasePackages()} for specifying the packages to
     * scan for annotated @SofaService classes. The package of each class specified will be
     * scanned.
     *
     * @return classes from the base packages to scan
     * @see SofaComponentScan#basePackageClasses
     */
    @AliasFor(annotation = SofaComponentScan.class, attribute = "basePackageClasses")
    Class<?>[] scanBasePackageClasses() default {};

}
