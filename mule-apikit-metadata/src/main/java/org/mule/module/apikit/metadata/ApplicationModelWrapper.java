/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.module.apikit.metadata;

import org.mule.module.apikit.metadata.interfaces.Notifier;
import org.mule.module.apikit.metadata.model.ApikitConfig;
import org.mule.module.apikit.metadata.model.Flow;
import org.mule.module.apikit.metadata.model.FlowMapping;
import org.mule.module.apikit.metadata.model.RamlCoordinate;
import org.mule.module.apikit.metadata.raml.RamlCoordsSimpleFactory;
import org.mule.module.apikit.metadata.raml.RamlHandler;
import org.mule.raml.interfaces.model.IRaml;
import org.mule.runtime.config.spring.api.dsl.model.ApplicationModel;
import org.mule.runtime.config.spring.api.dsl.model.ComponentModel;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

public class ApplicationModelWrapper {

  private final static String PARAMETER_NAME = "name";
  private final static String PARAMETER_RAML = "raml";
  private final static String PARAMETER_RESOURCE = "resource";
  private final static String PARAMETER_ACTION = "action";
  private final static String PARAMETER_CONTENT_TYPE = "content-type";
  private final static String PARAMETER_FLOW_REF = "flow-ref";

  private ApplicationModel applicationModel;
  private RamlHandler ramlHandler;
  private Notifier notifier;

  private Map<String, ApikitConfig> apikitConfigMap;
  private Map<String, RamlCoordinate> metadataFlows;

  public ApplicationModelWrapper(ApplicationModel applicationModel, RamlHandler ramlHandler, Notifier notifier) {
    this.applicationModel = applicationModel;
    this.ramlHandler = ramlHandler;
    this.notifier = notifier;
    initialize();
  }

  private void initialize() {
    findApikitConfigs();
    findAndProcessFlows();
  }

  private void findAndProcessFlows() {
    // Finding all valid flows
    final List<Flow> flows = findFlows();

    // Creating a Coords Factory, giving the list of all valid config names
    final RamlCoordsSimpleFactory coordsFactory = new RamlCoordsSimpleFactory(getConfigNames());
    final Map<String, RamlCoordinate> conventionCoordinates = createCoordinatesForConventionFlows(flows, coordsFactory);
    final Map<String, RamlCoordinate> flowMappingCoordinates = createCoordinatesForMappingFlows(flows, coordsFactory);

    // Merging both results
    metadataFlows = Stream.of(conventionCoordinates, flowMappingCoordinates)
        .flatMap(map -> map.entrySet().stream())
        .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private void findApikitConfigs() {

    apikitConfigMap = applicationModel.getRootComponentModel().getInnerComponents()
        .stream()
        .filter((element) -> ApikitElementIdentifiers.isApikitConfig(element.getIdentifier()))
        .map(this::createApikitConfig)
        .collect(toMap(ApikitConfig::getName, config -> config));
  }

  private Set<String> getConfigNames() {
    return apikitConfigMap.keySet();
  }

  private Map<String, RamlCoordinate> createCoordinatesForMappingFlows(List<Flow> flows, RamlCoordsSimpleFactory coordsFactory) {
    return apikitConfigMap.values()
        .stream()
        .flatMap(apikitConfig -> apikitConfig.getFlowMappings().stream())
        .map(flowMapping -> {
          String flowRefName = flowMapping.getFlowRef();

          for (Flow f : flows) {
            if (f.getName().equals(flowRefName)) {
              return new AbstractMap.SimpleEntry<>(
                                                   flowRefName,
                                                   coordsFactory.createFromFlowMapping(flowMapping));
            }
          }

          return null;
        })
        .filter(Objects::nonNull)
        .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private Map<String, RamlCoordinate> createCoordinatesForConventionFlows(final List<Flow> flows,
                                                                          final RamlCoordsSimpleFactory coordsFactory) {
    return flows
        .stream()
        .map(flow -> {

          String flowName = flow.getName();

          return new AbstractMap.SimpleEntry<>(flowName, coordsFactory.createFromFlowName(flowName));
        })
        .filter(entry -> entry.getValue() != null)
        .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
  }


  private ApikitConfig createApikitConfig(ComponentModel unwrappedApikitConfig) {
    final Map<String, String> parameters = unwrappedApikitConfig.getParameters();
    final String configName = parameters.get(PARAMETER_NAME);
    final String configRaml = parameters.get(PARAMETER_RAML);

    final List<FlowMapping> flowMappings = unwrappedApikitConfig.getInnerComponents()
        .stream()
        .filter(config -> ApikitElementIdentifiers.isFlowMappings(config.getIdentifier()))
        .flatMap(flowMappingsElement -> flowMappingsElement.getInnerComponents().stream())
        .filter(flowMapping -> ApikitElementIdentifiers.isFlowMapping(flowMapping.getIdentifier()))
        .map(unwrappedFlowMapping -> createFlowMapping(configName, unwrappedFlowMapping))
        .collect(toList());

    final RamlHandlerSupplier ramlSupplier = RamlHandlerSupplier.create(configRaml, ramlHandler);

    return new ApikitConfig(configName, configRaml, flowMappings, ramlSupplier);
  }

  private static class RamlHandlerSupplier implements Supplier<Optional<IRaml>> {

    private String configRaml;
    private RamlHandler handler;

    private RamlHandlerSupplier(String configRaml, RamlHandler handler) {
      this.configRaml = configRaml;
      this.handler = handler;
    }

    private static RamlHandlerSupplier create(String configRaml, RamlHandler handler) {
      return new RamlHandlerSupplier(configRaml, handler);
    }

    @Override
    public Optional<IRaml> get() {
      return handler.getRamlApi(configRaml);
    }
  }

  private FlowMapping createFlowMapping(String configName, ComponentModel unwrappedFlowMapping) {
    Map<String, String> flowMappingParameters = unwrappedFlowMapping.getParameters();

    String resource = flowMappingParameters.get(PARAMETER_RESOURCE);
    String action = flowMappingParameters.get(PARAMETER_ACTION);
    String contentType = flowMappingParameters.get(PARAMETER_CONTENT_TYPE);
    String flowRef = flowMappingParameters.get(PARAMETER_FLOW_REF);

    return new FlowMapping(configName, resource, action, contentType, flowRef);
  }

  public List<Flow> findFlows() {

    return applicationModel.getRootComponentModel().getInnerComponents()
        .stream()
        .filter(element -> ApikitElementIdentifiers.isFlow(element.getIdentifier()))
        .map(this::createFlow)
        .collect(toList());
  }

  private Flow createFlow(ComponentModel componentModel) {
    Map<String, String> parameters = componentModel.getParameters();
    String flowName = parameters.get(PARAMETER_NAME);
    return new Flow(flowName);
  }


  public RamlCoordinate getRamlCoordinatesForFlow(String flowName) {
    return metadataFlows.get(flowName);
  }

  public ApikitConfig getApikitConfigWithName(String apikitConfigName) {
    ApikitConfig config = apikitConfigMap.get(apikitConfigName);

    // If the flow is not explicitly naming the config it belongs, we assume there is only one API
    if (config == null) {
      config = apikitConfigMap.values().iterator().next();
    }

    return config;
  }
}