package io.quarkiverse.cxf.transport;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.cxf.Bus;
import org.apache.cxf.BusException;
import org.apache.cxf.BusFactory;
import org.apache.cxf.common.classloader.ClassLoaderUtils;
import org.apache.cxf.common.util.StringUtils;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.feature.Feature;
import org.apache.cxf.interceptor.Interceptor;
import org.apache.cxf.jaxws.JaxWsServerFactoryBean;
import org.apache.cxf.message.Message;
import org.apache.cxf.resource.ResourceManager;
import org.apache.cxf.transport.DestinationFactoryManager;
import org.apache.cxf.transport.http.AbstractHTTPDestination;
import org.apache.cxf.transport.http.DestinationRegistry;
import org.apache.cxf.transport.servlet.BaseUrlHelper;
import org.jboss.logging.Logger;

import io.quarkiverse.cxf.CXFServletInfo;
import io.quarkiverse.cxf.CXFServletInfos;
import io.quarkiverse.cxf.QuarkusJaxWsServiceFactoryBean;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;

public class CxfHandler implements Handler<RoutingContext> {
    private static final Logger LOGGER = Logger.getLogger(CxfHandler.class);
    private static final String ALLOWED_METHODS = "POST, GET, PUT, DELETE, HEAD, OPTIONS, TRACE";
    private static final String QUERY_PARAM_FORMAT = "format";
    private Bus bus;
    private ClassLoader loader;
    private DestinationRegistry destinationRegistry;
    private boolean loadBus;
    protected String serviceListRelativePath = "/services";

    private static final Map<String, String> RESPONSE_HEADERS = new HashMap<>();

    static {
        RESPONSE_HEADERS.put("Access-Control-Allow-Origin", "*");
        RESPONSE_HEADERS.put("Access-Control-Allow-Credentials", "true");
        RESPONSE_HEADERS.put("Access-Control-Allow-Methods", ALLOWED_METHODS);
        RESPONSE_HEADERS.put("Access-Control-Allow-Headers", "Content-Type, Authorization");
        RESPONSE_HEADERS.put("Access-Control-Max-Age", "86400");
    }

    public CxfHandler() {
        loadBus = false;
    }

    public CxfHandler(CXFServletInfos cxfServletInfos) {
        if (cxfServletInfos == null || cxfServletInfos.getInfos() == null || cxfServletInfos.getInfos().isEmpty()) {
            LOGGER.warn("no info transmit to servlet");
            return;
        }
        Bus bus = getBus();
        BusFactory.setDefaultBus(bus);
        for (CXFServletInfo servletInfo : cxfServletInfos.getInfos()) {
            JaxWsServerFactoryBean factory = new JaxWsServerFactoryBean(
                    new QuarkusJaxWsServiceFactoryBean(cxfServletInfos.getWrappersclasses()));
            factory.setBus(bus);
            Object instanceService = getInstance(servletInfo.getClassName());
            if (instanceService != null) {
                Class<?> seiClass = null;
                if (servletInfo.getSei() != null) {
                    seiClass = loadClass(servletInfo.getSei());
                    factory.setServiceClass(seiClass);
                }
                if (seiClass == null) {
                    LOGGER.warn("sei not found: " + servletInfo.getSei());
                }
                factory.setAddress(servletInfo.getPath());
                factory.setServiceBean(instanceService);
                if (servletInfo.getWsdlPath() != null) {
                    factory.setWsdlLocation(servletInfo.getWsdlPath());
                }
                if (!servletInfo.getFeatures().isEmpty()) {
                    List<Feature> features = new ArrayList<>();
                    for (String feature : servletInfo.getFeatures()) {
                        Feature instanceFeature = (Feature) getInstance(feature);
                        features.add(instanceFeature);
                    }
                    factory.setFeatures(features);
                }
                if (servletInfo.getSOAPBinding() != null) {
                    factory.setBindingId(servletInfo.getSOAPBinding());
                }

                Server server = factory.create();
                for (String className : servletInfo.getInFaultInterceptors()) {
                    Interceptor<? extends Message> interceptor = (Interceptor<? extends Message>) getInstance(className);
                    server.getEndpoint().getInFaultInterceptors().add(interceptor);
                }
                for (String className : servletInfo.getInInterceptors()) {
                    Interceptor<? extends Message> interceptor = (Interceptor<? extends Message>) getInstance(className);
                    server.getEndpoint().getInInterceptors().add(interceptor);
                }
                for (String className : servletInfo.getOutFaultInterceptors()) {
                    Interceptor<? extends Message> interceptor = (Interceptor<? extends Message>) getInstance(className);
                    server.getEndpoint().getOutFaultInterceptors().add(interceptor);
                }
                for (String className : servletInfo.getOutInterceptors()) {
                    Interceptor<? extends Message> interceptor = (Interceptor<? extends Message>) getInstance(className);
                    server.getEndpoint().getOutInterceptors().add(interceptor);
                }

                LOGGER.info(servletInfo.toString() + " available.");
            } else {
                LOGGER.error("Cannot initialize " + servletInfo.toString());
            }
        }
    }

    private Class<?> loadClass(String className) {
        try {
            return Thread.currentThread().getContextClassLoader().loadClass(className);
        } catch (ClassNotFoundException e) {
            //silent fail
        }
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            LOGGER.warn("failed to load class " + className);
            return null;
        }
    }

    private Object getInstance(String className) {
        Class<?> classObj = loadClass(className);
        try {
            return classObj.getConstructor().newInstance();
        } catch (Exception e) {
            return null;
        }
    }

    protected DestinationRegistry getDestinationRegistryFromBusOrDefault() {
        DestinationFactoryManager dfm = this.bus.getExtension(DestinationFactoryManager.class);
        VertxDestinationFactory soapDF = new VertxDestinationFactory();
        dfm.registerDestinationFactory("http://cxf.apache.org/transports/quarkus", soapDF);
        try {
            VertxDestinationFactory df = (VertxDestinationFactory) dfm
                    .getDestinationFactory("http://cxf.apache.org/transports/quarkus");
            return df.getRegistry();
        } catch (BusException ex) {
            //ignored
        }
        return null;
    }

    public Bus getBus() {
        return this.bus;
    }

    public void init() {
        if (this.bus == null && this.loadBus) {
            this.bus = BusFactory.getDefaultBus();
        }
        if (this.bus != null) {
            this.loader = this.bus.getExtension(ClassLoader.class);
            if (this.destinationRegistry == null) {
                this.destinationRegistry = this.getDestinationRegistryFromBusOrDefault();
            }
        }
    }

    @Override
    public void handle(RoutingContext event) {
        ClassLoaderUtils.ClassLoaderHolder origLoader = null;
        Bus origBus = null;
        try {
            if (this.loader != null) {
                origLoader = ClassLoaderUtils.setThreadContextClassloader(this.loader);
            }

            if (this.bus != null) {
                origBus = BusFactory.getAndSetThreadDefaultBus(this.bus);
            }

            process(event);
        } finally {
            if (origBus != this.bus) {
                BusFactory.setThreadDefaultBus(origBus);
            }

            if (origLoader != null) {
                origLoader.reset();
            }

        }
    }

    protected void generateNotFound(HttpServerRequest request, HttpServerResponse res) {
        res.setStatusCode(404);
        res.headers().add("Content-Type", "text/html");
        res.end("<html><body>No service was found.</body></html>");
    }

    protected void updateDestination(HttpServerRequest request, AbstractHTTPDestination d) {
        String base = getBaseURL(request);
        String ad = d.getEndpointInfo().getAddress();
        if (ad == null && d.getAddress() != null && d.getAddress().getAddress() != null) {
            ad = d.getAddress().getAddress().getValue();
            if (ad == null) {
                ad = "/";
            }
        }

        if (ad != null && !ad.startsWith("http")) {
            BaseUrlHelper.setAddress(d, base + ad);
        }

    }

    private String getBaseURL(HttpServerRequest request) {
        String reqPrefix = request.uri();
        String pathInfo = request.path();
        if (!"/".equals(pathInfo) || reqPrefix.contains(";")) {
            StringBuilder sb = new StringBuilder();
            URI uri = URI.create(reqPrefix);
            sb.append(uri.getScheme()).append("://").append(uri.getRawAuthority());
            String contextPath = request.path();
            if (contextPath != null) {
                sb.append(contextPath);
            }
            reqPrefix = sb.toString();
        }

        return reqPrefix;
    }

    private void process(RoutingContext event) {
        HttpServerRequest request = event.request();
        HttpServerResponse res = event.response();
        String pathInfo = request.path() == null ? "" : request.path();
        AbstractHTTPDestination d = this.destinationRegistry.getDestinationForPath(pathInfo, true);
        if (d == null) {
            if ((request.uri().endsWith(this.serviceListRelativePath)
                    || request.uri().endsWith(this.serviceListRelativePath + "/")
                    || StringUtils.isEmpty(pathInfo)
                    || "/".equals(pathInfo))) {

                //TODO list of services (ServiceListGeneratorServlet)
            } else {
                d = this.destinationRegistry.checkRestfulRequest(pathInfo);
                if (d == null || d.getMessageObserver() == null) {
                    LOGGER.warn("Can't find the request for " + request.uri() + "'s Observer ");
                    this.generateNotFound(request, res);
                    return;
                }
            }
        }

        if (d != null && d.getMessageObserver() != null) {
            Bus bus = d.getBus();
            ClassLoaderUtils.ClassLoaderHolder orig = null;

            try {
                if (bus != null) {
                    ClassLoader loader = bus.getExtension(ClassLoader.class);
                    if (loader == null) {
                        ResourceManager manager = bus.getExtension(ResourceManager.class);
                        if (manager != null) {
                            loader = manager.resolveResource("", ClassLoader.class);
                        }
                    }

                    if (loader != null) {
                        orig = ClassLoaderUtils.setThreadContextClassloader(loader);
                    }
                }

                this.updateDestination(request, d);
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Service http request on thread: " + Thread.currentThread());
                }

                try {
                    //todo call undertowDestination special invoke
                    if (d instanceof VertxDestination) {
                        try {
                            ((VertxDestination) d).invoke(event);
                        } catch (IOException e) {
                            LOGGER.warn("failed to handler request on vertx " + e.toString());
                        }
                    }
                } finally {
                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("Finished servicing http request on thread: " + Thread.currentThread());
                    }

                }
            } finally {
                if (orig != null) {
                    orig.reset();
                }

            }
        }

    }
}
