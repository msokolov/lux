package lux.compiler;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import lux.Compiler;
import net.sf.saxon.Configuration;
import net.sf.saxon.s9api.Processor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EXPathSupport {
    
    public static void initializeEXPath(Processor p) {
        Logger log = LoggerFactory.getLogger(Compiler.class);
        // initialize the EXPath package manager
        Class<?> pkgInitializerClass;
        try {
            pkgInitializerClass = Class.forName("org.expath.pkg.saxon.PkgInitializer");
            Object pkgInitializer = null;
            try {
                pkgInitializer = pkgInitializerClass.newInstance();
            } catch (InstantiationException e) {
                log.error (e.getMessage());
                return;
            } catch (IllegalAccessException e) {
                log.error (e.getMessage());
                return;
            }
            Method initialize = pkgInitializerClass.getMethod("initialize", Configuration.class);
            initialize.invoke(pkgInitializer, p.getUnderlyingConfiguration());
        } catch (ClassNotFoundException e) {
            log.error("EXPath repository declared, but EXPath Saxon package support classes are not available");
        } catch (SecurityException e) {
            log.error (e.getMessage());
        } catch (NoSuchMethodException e) {
            log.error (e.getMessage());
        } catch (IllegalArgumentException e) {
            log.error (e.getMessage());
        } catch (IllegalAccessException e) {
            log.error (e.getMessage());
        } catch (InvocationTargetException e) {
            log.error (e.getMessage());
        }
    }
    
}
