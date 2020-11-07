package io.quarkiverse.cxf.transport;

import java.io.IOException;
import java.util.logging.Logger;

import org.apache.cxf.Bus;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.transport.http.DestinationRegistry;
import org.apache.cxf.transport.http_jaxws_spi.JAXWSHttpSpiDestination;
import org.apache.cxf.ws.addressing.EndpointReferenceType;

import io.vertx.ext.web.RoutingContext;

public class VertxDestination extends JAXWSHttpSpiDestination {

    public VertxDestination(EndpointInfo endpointInfo, Bus bus, DestinationRegistry destinationRegistry) throws IOException {
        super(bus, destinationRegistry, endpointInfo);
    }

    @Override
    protected Logger getLogger() {
        return null;
    }

    @Override
    public EndpointReferenceType getAddress() {
        return super.getAddress();
    }

    public void invoke(RoutingContext context) throws IOException {
        VertxHttpServletRequest req = new VertxHttpServletRequest(context);
        VertxHttpServletResponse resp = new VertxHttpServletResponse(context);
        doService(req, resp);

    }
}
