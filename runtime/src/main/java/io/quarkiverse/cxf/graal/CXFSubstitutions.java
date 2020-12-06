package io.quarkiverse.cxf.graal;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URLStreamHandlerFactory;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.namespace.QName;

import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.common.util.ReflectionInvokationHandler;
import org.apache.cxf.common.util.ReflectionUtil;
import org.apache.cxf.databinding.WrapperHelper;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.util.VMError;

import io.quarkiverse.cxf.CXFException;

@TargetClass(className = "org.apache.cxf.wsdl.JAXBExtensionHelper")
final class Target_org_apache_cxf_wsdl_JAXBExtensionHelper {
    @Alias
    private static Logger LOG = null;

    @Substitute()
    private static Class<?> createExtensionClass(Class<?> cls, QName qname, ClassLoader loader) {
        try {
            LOG.info("extensibility class substitute: " + cls.getName());
            Class<?> clz = Class.forName("io.quarkiverse.cxf." + cls.getSimpleName() + "Extensibility");
            return clz;
        } catch (ClassNotFoundException e) {
            LOG.warning("extensibility class to create: " + cls.getName());
            throw new UnsupportedOperationException(
                    cls.getName() + " extensibility not implemented yet for GraalVM native images", e);
            // TODO CORBA support : org.apache.cxf.wsdl.http.OperationType and org.apache.cxf.wsdl.http.BindingType
        }
    }
}

@TargetClass(className = "org.apache.cxf.jaxb.JAXBContextInitializer")
final class Target_org_apache_cxf_jaxb_JAXBContextInitializer {
    @Alias
    private static Logger LOG = null;

    @Substitute()
    private Object createFactory(Class<?> cls, Constructor<?> contructor) {
        try {
            LOG.info("substitute  JAXBContextInitializer.createFactory class for : " + cls.getSimpleName());
            Class<?> factoryClass = Class.forName("io.quarkiverse.cxf." + cls.getSimpleName() + "Factory");
            try {
                return factoryClass.getConstructor().newInstance();
            } catch (Exception e) {
                LOG.warning("factory class not created for " + cls.getSimpleName());
            }
            return null;
        } catch (ClassNotFoundException e) {
            LOG.warning("factory class to create : " + cls.getSimpleName());
            throw new UnsupportedOperationException(cls.getName() + " factory not implemented yet for GraalVM native images",
                    e);
        }
    }
}

@TargetClass(className = "org.apache.cxf.jaxb.WrapperHelperCompiler")
final class Target_org_apache_cxf_jaxb_WrapperHelperCompiler {
    @Alias
    Class<?> wrapperType;

    @Alias
    private String computeSignature() {
        return null;
    }

    @Substitute()
    public WrapperHelper compile() {
        Logger LOG = LogUtils.getL7dLogger(Target_org_apache_cxf_jaxb_WrapperHelperCompiler.class);
        LOG.info("compileWrapperHelper substitution");
        int count = 1;
        String newClassName = wrapperType.getName() + "_WrapperTypeHelper" + count;
        newClassName = newClassName.replaceAll("\\$", ".");
        newClassName = newClassName.replace('/', '.');
        Class<?> cls = null;
        try {
            cls = Thread.currentThread().getContextClassLoader().loadClass(newClassName);
        } catch (ClassNotFoundException e) {
            LOG.warning("Wrapper helper class not found : " + e.toString());
        }
        while (cls != null) {
            try {
                WrapperHelper helper = WrapperHelper.class.cast(cls.newInstance());
                if (!helper.getSignature().equals(computeSignature())) {
                    LOG.warning("signature of helper : " + helper.getSignature()
                            + " is not equal to : " + computeSignature());
                    count++;
                    newClassName = wrapperType.getName() + "_WrapperTypeHelper" + count;
                    newClassName = newClassName.replaceAll("\\$", ".");
                    newClassName = newClassName.replace('/', '.');
                    try {
                        cls = Thread.currentThread().getContextClassLoader().loadClass(newClassName);
                    } catch (ClassNotFoundException e) {
                        LOG.warning("Wrapper helper class not found : " + e.toString());
                        break;
                    }
                } else {
                    return helper;
                }
            } catch (Exception e) {
                return null;
            }
        }

        WrapperHelper helper = null;
        try {
            if (cls != null) {
                helper = WrapperHelper.class.cast(cls.getConstructor().newInstance());
                return helper;
            }
        } catch (Exception e) {
            LOG.warning("Wrapper helper class not created : " + e.toString());
        }
        throw new UnsupportedOperationException(cls.getName() + " wrapperHelper not implemented yet for GraalVM native images");
    }

}

@TargetClass(className = "org.apache.cxf.endpoint.dynamic.TypeClassInitializer$ExceptionCreator")
final class Target_org_apache_cxf_endpoint_dynamic_TypeClassInitializer$ExceptionCreator {

    @Substitute
    public Class<?> createExceptionClass(Class<?> bean) throws ClassNotFoundException {
        Logger LOG = LogUtils.getL7dLogger(org.apache.cxf.endpoint.dynamic.TypeClassInitializer.class);
        LOG.info("Substitute TypeClassInitializer$ExceptionCreator.createExceptionClass");
        //TODO not sure if I use CXFException or generated one. I have both system in place. but I use CXFEx currently.
        String newClassName = CXFException.class.getSimpleName();

        try {
            Class<?> clz = Class.forName("io.quarkiverse.cxf." + newClassName);
            return clz;
        } catch (ClassNotFoundException e) {
            try {
                Class<?> clz = Class.forName("io.quarkiverse.cxf.CXFException");
                return clz;
            } catch (ClassNotFoundException ex) {
                throw new UnsupportedOperationException(
                        newClassName + " exception not implemented yet for GraalVM native images", ex);
            }
        }
    }
}

@TargetClass(className = "org.apache.cxf.common.jaxb.JAXBUtils")
final class Target_org_apache_cxf_common_jaxb_JAXBUtils {
    @Alias
    private static Logger LOG = null;

    @Substitute
    private static synchronized Object createNamespaceWrapper(Class<?> mcls, Map<String, String> map) {
        LOG.info("Substitute JAXBUtils.createNamespaceWrapper");
        Class<?> NamespaceWrapperClass = null;
        Throwable t = null;
        try {
            NamespaceWrapperClass = Class.forName("org.apache.cxf.jaxb.EclipseNamespaceMapper");
        } catch (ClassNotFoundException e) {
            // ignore
            t = e;
        }
        if (NamespaceWrapperClass == null) {
            try {
                NamespaceWrapperClass = Class.forName("org.apache.cxf.jaxb.NamespaceMapper");
            } catch (ClassNotFoundException e) {
                // ignore
                t = e;
            }
        }
        if (NamespaceWrapperClass == null) {
            try {
                NamespaceWrapperClass = Class.forName("org.apache.cxf.jaxb.NamespaceMapperRI");
            } catch (ClassNotFoundException e) {
                // ignore
                t = e;
            }
        }
        if (NamespaceWrapperClass == null && (!mcls.getName().contains(".internal.") && mcls.getName().contains("com.sun"))) {
            try {
                NamespaceWrapperClass = Class.forName("org.apache.cxf.common.jaxb.NamespaceMapper");
            } catch (Throwable ex2) {
                // ignore
                t = ex2;
            }
        }
        if (NamespaceWrapperClass != null) {
            try {
                return NamespaceWrapperClass.getConstructor(Map.class).newInstance(map);
            } catch (Exception e) {
                // ignore
                t = e;
            }
        }
        LOG.log(Level.INFO, "Could not create a NamespaceMapper compatible with Marshaller class " + mcls.getName(), t);
        return null;
    }
}

@TargetClass(className = "org.apache.cxf.common.util.ReflectionInvokationHandler")
final class Target_org_apache_cxf_common_util_ReflectionInvokationHandler {
    @Alias
    private Object target;

    @Alias
    private Class<?>[] getParameterTypes(Method method, Object[] args) {
        return null;
    }

    @Substitute
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        //add this to handle args null bug
        if (args == null)
            args = new Object[0];
        ReflectionInvokationHandler.WrapReturn wr = (ReflectionInvokationHandler.WrapReturn) method
                .getAnnotation(ReflectionInvokationHandler.WrapReturn.class);
        Class<?> targetClass = this.target.getClass();
        Class[] parameterTypes = this.getParameterTypes(method, args);

        int i;
        int x;
        try {
            Method m;
            try {
                m = targetClass.getMethod(method.getName(), parameterTypes);
            } catch (NoSuchMethodException var20) {
                boolean[] optionals = new boolean[method.getParameterTypes().length];
                i = 0;
                int optionalNumber = 0;
                Annotation[][] var25 = method.getParameterAnnotations();
                x = var25.length;

                int argI;
                for (argI = 0; argI < x; ++argI) {
                    Annotation[] a = var25[argI];
                    optionals[i] = false;
                    Annotation[] var16 = a;
                    int var17 = a.length;

                    for (int var18 = 0; var18 < var17; ++var18) {
                        Annotation potential = var16[var18];
                        if (ReflectionInvokationHandler.Optional.class.equals(potential.annotationType())) {
                            optionals[i] = true;
                            ++optionalNumber;
                            break;
                        }
                    }

                    ++i;
                }

                Class<?>[] newParams = new Class[args.length - optionalNumber];
                Object[] newArgs = new Object[args.length - optionalNumber];
                argI = 0;

                for (int j = 0; j < parameterTypes.length; ++j) {
                    if (!optionals[j]) {
                        newArgs[argI] = args[j];
                        newParams[argI] = parameterTypes[j];
                        ++argI;
                    }
                }

                m = targetClass.getMethod(method.getName(), newParams);
                args = newArgs;
            }

            ReflectionUtil.setAccessible(m);
            return wrapReturn(wr, m.invoke(this.target, args));
        } catch (InvocationTargetException var21) {
            throw var21.getCause();
        } catch (NoSuchMethodException var22) {
            Method[] var8 = targetClass.getMethods();
            int var9 = var8.length;

            for (i = 0; i < var9; ++i) {
                Method m2 = var8[i];
                if (m2.getName().equals(method.getName())
                        && m2.getParameterTypes().length == method.getParameterTypes().length) {
                    boolean found = true;

                    for (x = 0; x < m2.getParameterTypes().length; ++x) {
                        if (args[x] != null && !m2.getParameterTypes()[x].isInstance(args[x])) {
                            found = false;
                        }
                    }

                    if (found) {
                        ReflectionUtil.setAccessible(m2);
                        return wrapReturn(wr, m2.invoke(this.target, args));
                    }
                }
            }

            throw var22;
        }
    }

    @Alias
    private static Object wrapReturn(ReflectionInvokationHandler.WrapReturn wr, Object t) {
        return null;
    }
}

//copy fix from graal 20.3 until quarkus move to 20.3
@TargetClass(java.net.URL.class)
final class Target_java_net_URL {
    @Substitute
    @SuppressWarnings("unused")
    public static void setURLStreamHandlerFactory(URLStreamHandlerFactory fac) {
        VMError.unsupportedFeature("Setting a custom URLStreamHandlerFactory.");
    }
}

public class CXFSubstitutions {
}
