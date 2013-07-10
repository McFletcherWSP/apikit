package org.mule.tools.apikit.output.scopes;

import org.jdom2.Element;
import org.mule.tools.apikit.misc.APIKitTools;
import org.mule.tools.apikit.model.API;

import static org.mule.tools.apikit.output.MuleConfigGenerator.HTTP_NAMESPACE;
import static org.mule.tools.apikit.output.MuleConfigGenerator.XMLNS_NAMESPACE;

public class FlowScope implements Scope {

    private final Element main;

    public FlowScope(Element mule, String exceptionStrategyRef, API api, String yamlFileName) {
        main = new Element("flow", XMLNS_NAMESPACE.getNamespace());

        main.setAttribute("name", "main");

        Element httpInboundEndpoint = new Element("inbound-endpoint", HTTP_NAMESPACE.getNamespace());
        httpInboundEndpoint.setAttribute("address", api.getBaseUri());
        Element objectToStringTransformer = new Element("object-to-string-transformer",
                XMLNS_NAMESPACE.getNamespace());
        httpInboundEndpoint.addContent(objectToStringTransformer);

        main.addContent(httpInboundEndpoint);

        Element restProcessor = new Element("rest-processor", APIKitTools.API_KIT_NAMESPACE.getNamespace());
        restProcessor.setAttribute("config", yamlFileName);

        main.addContent("\n        ");
        main.addContent(restProcessor);

        Element exceptionStrategy = new Element("exception-strategy", XMLNS_NAMESPACE.getNamespace());
        exceptionStrategy.setAttribute("ref", exceptionStrategyRef);

        main.addContent("\n        ");
        main.addContent(exceptionStrategy);

        mule.addContent("\n    ");
        mule.addContent("\n    ");
        mule.addContent(main);
        mule.addContent("\n    ");

    }

    @Override
    public Element generate() {
        return main;
    }
}
