package com.linkedpipes.etl.component.api.impl;

import com.linkedpipes.etl.component.api.Component;
import com.linkedpipes.etl.component.api.impl.rdf.RdfReader;
import com.linkedpipes.etl.component.api.service.*;
import com.linkedpipes.etl.executor.api.v1.RdfException;
import com.linkedpipes.etl.executor.api.v1.component.SequentialComponent;
import com.linkedpipes.etl.executor.api.v1.dataunit.DataUnit;
import com.linkedpipes.etl.executor.api.v1.exception.LpException;
import com.linkedpipes.etl.executor.api.v1.rdf.SparqlSelect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.Map;

/**
 * @author Škoda Petr
 */
final class SimpleComponentImpl implements SequentialComponent {

    private static final Logger LOG
            = LoggerFactory.getLogger(SimpleComponentImpl.class);

    /**
     * Instance of a DPU code to execute.
     */
    private final Component.Sequential component;

    /**
     * Bundle information about the DPU.
     */
    private final BundleInformation info;

    /**
     * RDF configuration about this DPU.
     */
    private final ComponentConfiguration configuration;

    /**
     * Reference to the definition class.
     */
    private final SparqlSelect definition;

    /**
     * Main definition graph.
     */
    private final String definitionGraph;

    /**
     * After execution object if used.
     */
    private AfterExecutionImpl afterExecution = null;

    private final com.linkedpipes.etl.executor.api.v1.component.Component.Context
            context;

    SimpleComponentImpl(Component.Sequential dpu, BundleInformation info,
            ComponentConfiguration configuration, SparqlSelect definition,
            String graph,
            com.linkedpipes.etl.executor.api.v1.component.Component.Context context) {
        this.component = dpu;
        this.info = info;
        this.configuration = configuration;
        this.definition = definition;
        this.definitionGraph = graph;
        this.context = context;
    }

    /**
     * Bind given data units to the port of the {@link #component}.
     *
     * @param dataunits
     */
    protected void bindPorts(Map<String, DataUnit> dataunits)
            throws RdfException {
        for (Field field : component.getClass().getFields()) {
            final Component.InputPort input
                    = field.getAnnotation(Component.InputPort.class);
            final Component.OutputPort output
                    = field.getAnnotation(Component.OutputPort.class);
            if (input != null) {
                bindPort(dataunits, field, input.id(), input.optional());
            }
            if (output != null) {
                bindPort(dataunits, field, output.id(), false);
            }
        }
    }

    protected void bindPort(Map<String, DataUnit> dataUnits, Field field,
            String id, boolean optional) throws RdfException {
        // Search for data unit.
        DataUnit dataUnit = null;
        for (DataUnit item : dataUnits.values()) {
            if (id.equals(item.getBinding())) {
                dataUnit = item;
            }
        }
        if (dataUnit == null) {
            if (!optional) {
                LOG.info("Missing data unit, expected: {}", id);
                for (DataUnit item : dataUnits.values()) {
                    LOG.info("\tFound: {}", item.getBinding());
                }
                // If it's not optional then fail for missing data unit.
                throw RdfException.problemWithDataUnit(id);
            }
        } else if (field.getType().isAssignableFrom(dataUnit.getClass())) {
            try {
                field.set(component, dataUnit);
            } catch (IllegalAccessException | IllegalArgumentException ex) {
                throw RdfException.problemWithDataUnit(id, ex);
            }
        } else {
            // Type miss match!
            LOG.error("Not assignable data units ({}): {} -> {}", id,
                    dataUnit.getClass().getSimpleName(),
                    field.getType().getSimpleName());
            //
            throw RdfException.problemWithDataUnit(id);
        }
    }

    protected void injectObjects()
            throws RdfException {
        for (Field field : component.getClass().getFields()) {
            if (field.getAnnotation(Component.Inject.class) == null) {
                // No annotation.
                continue;
            }
            // Create extension instance.
            Object object;
            if (field.getType() == ProgressReport.class) {
                object = new ProgressReportImpl(context,
                        configuration.getResourceIri());
            } else if (field.getType() == DefinitionReader.class) {
                object = new DefinitionReaderImpl(definition, definitionGraph,
                        configuration.getResourceIri());
            } else if (field.getType() == AfterExecution.class) {
                afterExecution = new AfterExecutionImpl();
                object = afterExecution;
            } else if (field.getType() == WorkingDirectory.class) {
                object = new WorkingDirectory(
                        configuration.getWorkingDirectory().getPath());
            } else if (field.getType() == ExceptionFactory.class) {
                object = new ExceptionFactoryImpl(
                        configuration.getResourceIri());
            } else {
                throw RdfException.initializationFailed(
                        "Can't initialize extension!");
            }
            // ...
            try {
                field.set(component, object);
            } catch (IllegalAccessException | IllegalArgumentException ex) {
                throw RdfException.initializationFailed(
                        "Can't set extension!", ex);
            }
        }
    }

    /**
     * Load configuration to all annotated fields in DPU.
     *
     * @param runtimeConfig
     */
    protected void loadConfigurations(SparqlSelect runtimeConfig)
            throws RdfException {
        final ConfigurationController configController
                = new ConfigurationController(definition);
        for (Field field : component.getClass().getFields()) {
            if (field.getAnnotation(Component.Configuration.class) != null) {
                loadConfigurationForField(field, runtimeConfig,
                        configController);
            }
        }
    }

    /**
     * If this function fail or recoverable-error then only the whole
     * function can be re-executed.
     *
     * @param field
     * @param runtimeConfig
     */
    protected void loadConfigurationForField(Field field,
            SparqlSelect runtimeConfig,
            ConfigurationController configController)
            throws RdfException {
        // Create configuration object.
        final Object fieldValue;
        try {
            fieldValue = field.getType().newInstance();
        } catch (IllegalAccessException | InstantiationException ex) {
            throw RdfException.initializationFailed(
                    "Can't create configuration class for field: {}",
                    field.getName(), ex);
        }
        // Load configurations from definitions.
        for (ComponentConfiguration.Configuration configRef
                : configuration.getConfigurations()) {
            // Use current graph if no graph is given.
            final String graph = configRef.getConfigurationGraph() == null
                    ? definitionGraph : configRef.getConfigurationGraph();
            final String uri = configRef.getConfigurationIri();
            try {
                if (uri == null) {
                    RdfReader.addToObject(fieldValue, definition, graph,
                            configController);
                } else {
                    RdfReader.addToObject(fieldValue, definition, graph,
                            uri, configController);
                }
            } catch (RdfException ex) {
                throw RdfException.failure(
                        "Can't load configuration from definition.", ex);
            }
        }
        // Load runtime configuration.
        try {
            if (runtimeConfig != null) {
                RdfReader.addToObject(fieldValue, runtimeConfig, null,
                        configController);
            }
        } catch (RdfException ex) {
            throw RdfException.failure("Can't load runtime configuration.", ex);
        }
        // Set value.
        try {
            field.set(component, fieldValue);
        } catch (IllegalAccessException | IllegalArgumentException ex) {
            throw RdfException.initializationFailed(
                    "Can't set configuration object for field: {}",
                    field.getName(), ex);
        }
    }

    /**
     * Search for configuration field. If it's found then try to convert
     * it into {@link SparqlSelect} and return it.
     *
     * Must be called after all the data units are bound.
     *
     * @return Null if there is no configuration data unit.
     */
    private SparqlSelect getConfigurationDataUnit() throws RdfException {
        for (Field field : component.getClass().getFields()) {
            final Component.ContainsConfiguration config
                    =
                    field.getAnnotation(Component.ContainsConfiguration.class);
            if (config != null) {
                final Object value;
                try {
                    value = field.get(component);
                } catch (Exception ex) {
                    throw RdfException.initializationFailed(
                            "Can't read field.", ex);
                }
                final SparqlSelect sparqlSelect;
                if (value instanceof SparqlSelect) {
                    sparqlSelect = (SparqlSelect) value;
                } else {
                    sparqlSelect = null;
                }
                if (sparqlSelect == null) {
                    throw RdfException.initializationFailed(
                            "Can not use data unit ({}) "
                                    + "as a configuration source.",
                            field.getName());
                }
                return sparqlSelect;
            }
        }
        // No configuration data unit is presented.
        return null;
    }

    @Override
    public void initialize(Map<String, DataUnit> dataUnits)
            throws RdfException {
        bindPorts(dataUnits);
        injectObjects();
        // Must be called after bindPorts.
        loadConfigurations(getConfigurationDataUnit());
    }

    @Override
    public void execute() throws RdfException {
        try {
            component.execute();
        } catch (LpException ex) {
            throw RdfException.rethrow(ex);
        } catch (Throwable ex) {
            throw RdfException.failure("Component failure on Throwable.", ex);
        } finally {
            if (afterExecution != null) {
                afterExecution.postExecution();
            }
        }
    }

    @Override
    public String getHeader(String key) {
        switch (key) {
            case Headers.LOG_PACKAGES:
                // Construct list of packages.
                final StringBuilder logList = new StringBuilder(64);
                final Iterator<String> iter = info.getPackages().iterator();
                logList.append(iter.next());
                while (iter.hasNext()) {
                    logList.append(",");
                    logList.append(iter.next());
                }
                LOG.debug("Log packages: {}", logList);
                return logList.toString();
            default:
                return null;
        }
    }

}
