package pers.brian.springframework.context.reader;

import lombok.extern.slf4j.Slf4j;
import pers.brian.springframework.beans.BeanDefinition;
import pers.brian.springframework.beans.BeanDefinitionHolder;
import pers.brian.springframework.beans.GenericBeanDefinition;
import pers.brian.springframework.beans.annotation.Component;
import pers.brian.springframework.beans.annotation.Lazy;
import pers.brian.springframework.beans.annotation.Scope;
import pers.brian.springframework.core.utils.ClassLoaderUtils;
import pers.brian.springframework.core.utils.StringUtils;
import pers.brian.springframework.beans.support.BeanDefinitionRegistry;

import java.beans.Introspector;
import java.io.File;
import java.net.URL;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author BrianHu
 * @create 2022-01-11 14:44
 **/
@Slf4j
public class ClassPathBeanDefinitionScanner {

    private final BeanDefinitionRegistry bdRegistry;

    public ClassPathBeanDefinitionScanner(BeanDefinitionRegistry bdRegistry) {
        this.bdRegistry = bdRegistry;
    }

    public void scan(String... scanPaths) {
        log.info("BeanDefinition扫描开始");
        for (String scanPath : scanPaths) {
            log.info("开始扫描{}路径", scanPath);
            doScan(scanPath);
        }
    }

    private void doScan(String scanPath) {
        Set<BeanDefinition> bdSet = findBeanDefinition(scanPath);

        for (BeanDefinition bd : bdSet) {
            GenericBeanDefinition gbd = (GenericBeanDefinition) bd;
            Class<?> beanClass = gbd.getBeanClass();

            // 解析Scope
            if (beanClass.isAssignableFrom(Scope.class)) {
                gbd.setScope(beanClass.getAnnotation(Scope.class).value());
            } else {
                gbd.setScope(BeanDefinition.SCOPE_SINGLETON);
            }

            // 生成beanName
            String beanName;
            String nameVal = beanClass.getAnnotation(Component.class).value();
            if (StringUtils.isNotEmpty(nameVal)) {
                beanName = nameVal;
            } else {
                beanName = Introspector.decapitalize(beanClass.getSimpleName());
            }

            // 给注解赋初值
            gbd.setLazyInit(false);

            // 解析@Lazy、@Primary、@DependsOn、@Role、@Description
            if (beanClass.isAssignableFrom(Lazy.class)) {
                gbd.setLazyInit(true);
            }

            BeanDefinitionHolder bdHolder = new BeanDefinitionHolder(beanName, gbd);
            registerBeanDefinition(bdHolder);
        }

    }

    /**
     * 读取scanPath下的所有文件
     *
     * @param scanPath 扫描路径
     * @return 扫描路径下的所有问题
     */
    private File[] loadScanFiles(String scanPath) {
        scanPath = scanPath.replace(".", "/");
        // TODO: 2022/1/12 Spring使用使用的是ResourcePatternResolver
        ClassLoader classLoader = ClassLoaderUtils.getSystemClassLoader();
        URL resource = classLoader.getResource(scanPath);
        if (resource == null) {
            log.error("包扫描路径{}不存在", scanPath);
            return null;
        }
        File scanDir = new File(resource.getFile());
        if (!scanDir.isDirectory()) {
            log.error("包扫描路径{}不正确", scanPath);
            return null;
        }
        File[] scanFiles = scanDir.listFiles();
        if (scanFiles == null || scanFiles.length == 0) {
            log.info("包扫描路径{}下为空", scanPath);
            return null;
        }
        return scanFiles;
    }

    /**
     * 扫描beanDefinition
     *
     * @param scanPath 扫描路径
     * @return 所有扫描路径下的beanDefinition
     */
    private Set<BeanDefinition> findBeanDefinition(String scanPath) {
        Set<BeanDefinition> bdSet = new LinkedHashSet<>();

        // 加载扫描路径下的所有文件
        File[] scanFiles = loadScanFiles(scanPath);
        ClassLoader classLoader = ClassLoaderUtils.getSystemClassLoader();
        if (scanFiles != null) {
            for (File scanFile : scanFiles) {
                String absolutePath = scanFile.getAbsolutePath();
                if (!absolutePath.endsWith(".class")) {
                    continue;
                }
                String loadPath = absolutePath.substring(absolutePath.indexOf("target\\classes\\") + 15, absolutePath.length() - 6);
                loadPath = loadPath.replace("\\", ".");
                Class<?> clazz = null;
                try {
                    clazz = classLoader.loadClass(loadPath);
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
                if (clazz != null && clazz.isAnnotationPresent(Component.class)) {
                    GenericBeanDefinition bd = new GenericBeanDefinition();
                    bd.setBeanClass(clazz);
                    bdSet.add(bd);
                }
            }
        }
        return bdSet;
    }

    public void registerBeanDefinition(BeanDefinitionHolder bdHolder) {
        bdRegistry.registerBeanDefinition(bdHolder.getBeanName(), bdHolder.getBeanDefinition());
    }
}
