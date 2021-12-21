package com.alipay.sofa.runtime.spring;

import com.alipay.sofa.runtime.api.annotation.EnableSofa;
import com.alipay.sofa.runtime.api.annotation.SofaComponentScan;
import com.alipay.sofa.runtime.api.annotation.SofaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.SingletonBeanRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.AnnotationBeanNameGenerator;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.TypeFilter;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.*;

import static java.util.Arrays.asList;
import static org.springframework.context.annotation.AnnotationConfigUtils.CONFIGURATION_BEAN_NAME_GENERATOR;

/**
 * Sofa {@link com.alipay.sofa.runtime.api.annotation.SofaComponentScan} Bean Registrar
 *
 * @author renliangyu 2021/12/21
 */
public class SofaComponentScanRegistrar implements ImportBeanDefinitionRegistrar, EnvironmentAware, ResourceLoaderAware, ApplicationContextAware {

    private Logger logger = LoggerFactory.getLogger(SofaComponentScanRegistrar.class);

    private final static List<Class<? extends Annotation>> serviceAnnotationTypes = asList(
            SofaService.class
    );

    private Environment environment;

    private ResourceLoader resourceLoader;

    private SofaServicePackagesHolder sofaServicePackagesHolder;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.sofaServicePackagesHolder = applicationContext.getBean(SofaServicePackagesHolder.BEAN_NAME, SofaServicePackagesHolder.class);
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
       this.resourceLoader = resourceLoader;
    }


    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {

        Set<String> packagesToScan = getPackagesToScan(importingClassMetadata);

       scanServiceBeans(packagesToScan,registry);
    }


    private Set<String> getPackagesToScan(AnnotationMetadata metadata) {
        // get from @SofaComponentScan
        Set<String> packagesToScan = getPackagesToScanImpl(metadata, SofaComponentScan.class, "basePackages", "basePackageClasses");

        // get from @EnableSofa
        if (packagesToScan.isEmpty()) {
            packagesToScan = getPackagesToScanImpl(metadata, EnableSofa.class, "scanBasePackages", "scanBasePackageClasses");
        }

        if (packagesToScan.isEmpty()) {
            return Collections.singleton(ClassUtils.getPackageName(metadata.getClassName()));
        }
        return packagesToScan;
    }

    private Set<String> getPackagesToScanImpl(AnnotationMetadata metadata, Class annotationClass, String basePackagesName, String basePackageClassesName) {

        AnnotationAttributes attributes = AnnotationAttributes.fromMap(
                metadata.getAnnotationAttributes(annotationClass.getName()));
        if (attributes == null) {
            return Collections.emptySet();
        }

        Set<String> packagesToScan = new LinkedHashSet<>();
        // basePackages
        String[] basePackages = attributes.getStringArray(basePackagesName);
        packagesToScan.addAll(Arrays.asList(basePackages));
        // basePackageClasses
        Class<?>[] basePackageClasses = attributes.getClassArray(basePackageClassesName);
        for (Class<?> basePackageClass : basePackageClasses) {
            packagesToScan.add(ClassUtils.getPackageName(basePackageClass));
        }
        // value
        if (attributes.containsKey("value")) {
            String[] value = attributes.getStringArray("value");
            packagesToScan.addAll(Arrays.asList(value));
        }
        return packagesToScan;
    }

    /**
     * Scan whose classes was annotated {@link com.alipay.sofa.runtime.api.annotation.SofaService}
     * after registry SofaServiceDefinition by {@link ServiceBeanFactoryPostProcessor}
     * @param packagesToScan The base packages to scan
     * @param registry       {@link BeanDefinitionRegistry}
     */
    private void scanServiceBeans(Set<String> packagesToScan, BeanDefinitionRegistry registry) {

        if (CollectionUtils.isEmpty(packagesToScan)) {
            if (logger.isWarnEnabled()) {
                logger.warn("packagesToScan is empty , SofaServiceBean registry will be ignored!");
            }
            return;
        }

        SofaClassPathBeanDefinitionScanner scanner =
                new SofaClassPathBeanDefinitionScanner(registry, environment, resourceLoader);

        BeanNameGenerator beanNameGenerator = resolveBeanNameGenerator(registry);
        scanner.setBeanNameGenerator(beanNameGenerator);
        for (Class<? extends Annotation> annotationType : serviceAnnotationTypes) {
            scanner.addIncludeFilter(new AnnotationTypeFilter(annotationType));
        }

        //avoid duplicated scan class
        ScanExcludeFilter scanExcludeFilter = new ScanExcludeFilter();
        scanner.addExcludeFilter(scanExcludeFilter);

        for (String packageToScan : packagesToScan) {

            // avoid duplicated scans package and sub package,scanner中findCandidateComponents已经做了判断了，为什么这里还做一次？todo
            if (sofaServicePackagesHolder.isPackageScanned(packageToScan)) {
                if (logger.isInfoEnabled()) {
                    logger.info("Ignore package who has already bean scanned: " + packageToScan);
                }
                continue;
            }

            // Registers @SofaService Bean, after registry SofaServiceDefinition by
            scanner.scan(packageToScan);

            // Finds all BeanDefinitionHolders of @SofaService whether @ComponentScan scans or not for debug info
            Set<BeanDefinitionHolder> beanDefinitionHolders =
                    findServiceBeanDefinitionHolders(scanner, packageToScan, registry, beanNameGenerator);

            if (!CollectionUtils.isEmpty(beanDefinitionHolders)) {
                if (logger.isInfoEnabled()) {
                    List<String> serviceClasses = new ArrayList<>(beanDefinitionHolders.size());
                    for (BeanDefinitionHolder beanDefinitionHolder : beanDefinitionHolders) {
                        serviceClasses.add(beanDefinitionHolder.getBeanDefinition().getBeanClassName());
                    }
                    logger.info("Found " + beanDefinitionHolders.size() + " classes annotated by  @SofaService under package [" + packageToScan + "]: " + serviceClasses);
                }

                for (BeanDefinitionHolder beanDefinitionHolder : beanDefinitionHolders) {
                    sofaServicePackagesHolder.addScannedClass(beanDefinitionHolder.getBeanDefinition().getBeanClassName());
                }
            } else {
                if (logger.isWarnEnabled()) {
                    logger.warn("No class annotated by @SofaService was found under package ["
                            + packageToScan + "], ignore re-scanned classes: " + scanExcludeFilter.getExcludedCount());
                }
            }

            sofaServicePackagesHolder.addScannedPackage(packageToScan);
        }
    }

    /**
     * 这个有什么用 todo?
     * It'd better to use BeanNameGenerator instance that should reference
     * {@link org.springframework.context.annotation.ConfigurationClassPostProcessor},
     * thus it maybe a potential problem on bean name generation.
     *
     * @param registry {@link BeanDefinitionRegistry}
     * @return {@link BeanNameGenerator} instance
     * @see SingletonBeanRegistry
     * @see org.springframework.context.annotation.AnnotationConfigUtils#CONFIGURATION_BEAN_NAME_GENERATOR
     * @see org.springframework.context.annotation.ConfigurationClassPostProcessor#processConfigBeanDefinitions
     * @since 2.5.8
     */
    private BeanNameGenerator resolveBeanNameGenerator(BeanDefinitionRegistry registry) {

        BeanNameGenerator beanNameGenerator = null;

        if (registry instanceof SingletonBeanRegistry) {
            SingletonBeanRegistry singletonBeanRegistry = SingletonBeanRegistry.class.cast(registry);
            beanNameGenerator = (BeanNameGenerator) singletonBeanRegistry.getSingleton(CONFIGURATION_BEAN_NAME_GENERATOR);
        }

        if (beanNameGenerator == null) {

            if (logger.isInfoEnabled()) {

                logger.info("BeanNameGenerator bean can't be found in BeanFactory with name ["
                        + CONFIGURATION_BEAN_NAME_GENERATOR + "]");
                logger.info("BeanNameGenerator will be a instance of " +
                        AnnotationBeanNameGenerator.class.getName() +
                        " , it maybe a potential problem on bean name generation.");
            }

            beanNameGenerator = new AnnotationBeanNameGenerator();

        }

        return beanNameGenerator;

    }


    private class ScanExcludeFilter implements TypeFilter {

        private int excludedCount;

        @Override
        public boolean match(MetadataReader metadataReader, MetadataReaderFactory metadataReaderFactory) throws IOException {
            String className = metadataReader.getClassMetadata().getClassName();
            //servicePackagesHolder 初始化 todo
            boolean excluded = sofaServicePackagesHolder.isClassScanned(className);
            if (excluded) {
                excludedCount++;
            }
            return excluded;
        }

        public int getExcludedCount() {
            return excludedCount;
        }
    }

    /**
     * Finds a {@link Set} of {@link BeanDefinitionHolder BeanDefinitionHolders} whose bean type annotated
     * {@link SofaService} Annotation.
     *
     * @param scanner       {@link ClassPathBeanDefinitionScanner}
     * @param packageToScan pachage to scan
     * @param registry      {@link BeanDefinitionRegistry}
     * @return non-null
     * @since 2.5.8
     */
    private Set<BeanDefinitionHolder> findServiceBeanDefinitionHolders(
            ClassPathBeanDefinitionScanner scanner, String packageToScan, BeanDefinitionRegistry registry,
            BeanNameGenerator beanNameGenerator) {

        Set<BeanDefinition> beanDefinitions = scanner.findCandidateComponents(packageToScan);

        Set<BeanDefinitionHolder> beanDefinitionHolders = new LinkedHashSet<>(beanDefinitions.size());

        for (BeanDefinition beanDefinition : beanDefinitions) {

            String beanName = beanNameGenerator.generateBeanName(beanDefinition, registry);
            BeanDefinitionHolder beanDefinitionHolder = new BeanDefinitionHolder(beanDefinition, beanName);
            beanDefinitionHolders.add(beanDefinitionHolder);

        }
        return beanDefinitionHolders;

    }

}
