package com.alipay.sofa.runtime.api.annotation;

import java.lang.annotation.*;

/**
 * Sofa Component Scan,scans the classpath for annotated components that will be auto-registered as
 * Spring beans,Sofa-provided {@link SofaService} and {@link SofaReference}
 *
 * @see SofaService
 * @see SofaReference
 *
 * @author renliangyu 2021/12/21
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SofaComponentScan {
    /**
     * Alias for the {@link #basePackages()} attribute. Allows for more concise annotation
     * declarations e.g.: {@code @SofaComponentScan("org.my.pkg")} instead of
     * {@code @SofaComponentScan(basePackages="org.my.pkg")}.
     *
     * @return the base packages to scan
     */
    String[] value() default {};

    /**
     * Base packages to scan for annotated @SofaService classes. {@link #value()} is an
     * alias for (and mutually exclusive with) this attribute.
     * <p>
     * Use {@link #basePackageClasses()} for a type-safe alternative to String-based
     * package names.
     *
     * @return the base packages to scan
     */
    String[] basePackages() default {};

    /**
     * Type-safe alternative to {@link #basePackages()} for specifying the packages to
     * scan for annotated @SofaService classes. The package of each class specified will be
     * scanned.
     *
     * @return classes from the base packages to scan
     */
    Class<?>[] basePackageClasses() default {};

}
